import MindMap from './lib/simple-mind-map/simpleMindMap.esm.min.js';

const { createApp, ref, nextTick } = Vue;

createApp({
    setup() {
        const entry = ref({
            className: 'com.omp.marketing.intf.web.ShortLinkController',
            methodName: 'detail'
        });

        const leftCardCollapsed = ref(false);
        const zoomPercent = ref(100);
        const dragMode = ref(false);

        let mindMapInstance = null;
        let rawTreeData = null;

        // Track which node IDs have already been loaded via API
        const loadedSet = new Set();

        // ─── 数据转换 ──────────────────────────────────────────────────────────────
        /**
         * 将后端节点格式转换为 simple-mind-map 所需格式。
         * 规则：
         *  - Mapper 节点 → 子节点直接使用 dbOperations 中的 SQL 语句
         *  - 未加载的普通节点 → 添加一个"待加载"占位子节点，以便显示 + 按钮
         *  - 已加载的普通节点 → 递归转换其 children
         */
        const transformNode = (node) => {
            if (!node) return null;

            const isLoaded = loadedSet.has(node.id);
            const isExpanded = !!node.expand;

            let children = [];

            // 仅在已加载且处于展开状态时，才递归返回子节点
            if (isLoaded && isExpanded && node.children && node.children.length > 0) {
                children = node.children.map(c => transformNode(c));
            }

            return {
                data: {
                    text: node.methodName || node.label || '?',
                    id: node.id,
                    className: node.className,
                    methodName: node.methodName,
                    isMapper: node.isMapper,
                    dbOperations: node.dbOperations,
                    remarks: node.remarks,
                    label: node.label
                },
                children
            };
        };

        const createCustomNodeDom = (node) => {
            const data = node.nodeData.data;
            if (!data) return null;

            // 外部包装容器，为右侧绝对定位的 + - 按钮预留空间，防止被 SVG foreignObject 剪裁
            const wrapper = document.createElement('div');
            wrapper.className = 'mindmap-card-wrapper';

            // 卡片主容器
            const div = document.createElement('div');
            div.className = 'mindmap-card';
            if (data.isMapper) {
                div.classList.add('is-mapper');
            } else if (rawTreeData && data.id === rawTreeData.id) {
                div.classList.add('is-root');
            } else {
                div.classList.add('is-service');
            }

            // 1. 头部标题区：指示器与类型文本
            const headerType = document.createElement('div');
            headerType.className = 'card-header-type';
            
            const indicator = document.createElement('span');
            indicator.className = 'header-indicator';
            headerType.appendChild(indicator);

            const headerText = document.createElement('span');
            headerText.className = 'header-text';
            if (rawTreeData && data.id === rawTreeData.id) {
                headerText.innerText = '🎯 入口方法';
            } else if (data.isMapper) {
                headerText.innerText = '💾 Mapper 接口';
            } else {
                headerText.innerText = '⚡ Service 方法';
            }
            headerType.appendChild(headerText);
            div.appendChild(headerType);

            // 2. 第一行：类名
            if (data.className) {
                const row = document.createElement('div');
                row.className = 'card-row';
                
                const label = document.createElement('span');
                label.className = 'row-label';
                label.innerText = '类名';
                row.appendChild(label);

                const dotIdx = data.className.lastIndexOf('.');
                const shortName = dotIdx !== -1 ? data.className.substring(dotIdx + 1) : data.className;
                
                const badge = document.createElement('span');
                badge.className = 'row-badge';
                badge.innerText = shortName;
                badge.setAttribute('title', data.className); // 悬浮显示完整类名
                row.appendChild(badge);
                
                div.appendChild(row);
            }

            // 3. 第二行：方法名
            if (data.methodName || data.text) {
                const row = document.createElement('div');
                row.className = 'card-row';
                
                const label = document.createElement('span');
                label.className = 'row-label';
                label.innerText = '方法名';
                row.appendChild(label);

                const badge = document.createElement('span');
                badge.className = 'row-badge';
                badge.innerText = data.methodName || data.text || '';
                row.appendChild(badge);

                div.appendChild(row);
            }

            // 4. 主体内容区（备注 & SQL 语句）
            const body = document.createElement('div');
            body.className = 'card-body';

            // 4.1 方法简介
            if (data.remarks) {
                const remarksContainer = document.createElement('div');
                remarksContainer.className = 'card-remarks-container';
                
                const remarksTitle = document.createElement('div');
                remarksTitle.className = 'remarks-title';
                remarksTitle.innerText = '方法简介';
                remarksContainer.appendChild(remarksTitle);

                const remarksContent = document.createElement('div');
                remarksContent.className = 'remarks-content';
                remarksContent.innerText = data.remarks;
                remarksContainer.appendChild(remarksContent);

                body.appendChild(remarksContainer);
            }

            // 4.2 SQL 语句 (仅 Mapper 且有数据时展示)
            if (data.isMapper && data.dbOperations && data.dbOperations.length > 0) {
                const sqlContainer = document.createElement('div');
                sqlContainer.className = 'card-sql-container';

                const sqlTitle = document.createElement('div');
                sqlTitle.className = 'sql-title';
                sqlTitle.innerText = 'SQL 语句';
                sqlContainer.appendChild(sqlTitle);

                data.dbOperations.forEach(op => {
                    const sqlBox = document.createElement('div');
                    sqlBox.className = 'sql-box';

                    if (op.operationType) {
                        const tag = document.createElement('span');
                        tag.className = `sql-type-tag ${op.operationType.toLowerCase()}`;
                        tag.innerText = op.operationType;
                        sqlBox.appendChild(tag);
                    }

                    const code = document.createElement('code');
                    code.className = 'sql-code';
                    code.innerText = op.sql || `[${op.operationType || 'SQL'}] ${op.tableName || ''}`;
                    sqlBox.appendChild(code);

                    sqlContainer.appendChild(sqlBox);
                });

                body.appendChild(sqlContainer);
            }

            // 仅在有备注或有 SQL 时才渲染 body
            const hasBody = data.remarks || (data.isMapper && data.dbOperations && data.dbOperations.length > 0);
            if (hasBody) {
                div.appendChild(body);
            }

            wrapper.appendChild(div);

            // 5. 自定义贴边折叠/展开按钮 (挂载到 wrapper 容器上)
            const isLoaded = loadedSet.has(data.id);
            const rawNode = findNode(rawTreeData, data.id);
            
            let showBtn = false;
            let btnText = '+';
            
            if (rawNode && !rawNode.isMapper) {
                if (!isLoaded) {
                    showBtn = true;
                    btnText = '+';
                } else if (rawNode.children && rawNode.children.length > 0) {
                    showBtn = true;
                    btnText = rawNode.expand ? '−' : '+';
                }
            }

            if (showBtn) {
                const btn = document.createElement('div');
                btn.className = 'card-expand-btn';
                btn.innerText = btnText;
                
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (!isLoaded) {
                        loadChildren(rawNode);
                    } else {
                        rawNode.expand = !rawNode.expand;
                        rerender();
                    }
                });
                
                wrapper.appendChild(btn);
            }

            return wrapper;
        };

        // ─── 工具函数 ──────────────────────────────────────────────────────────────

        /** 在 rawTreeData 树中通过 id 查找节点 */
        const findNode = (node, id) => {
            if (!node) return null;
            if (node.id === id) return node;
            if (node.children) {
                for (const c of node.children) {
                    const found = findNode(c, id);
                    if (found) return found;
                }
            }
            return null;
        };

        /** 将 rawTreeData 重新渲染到 MindMap */
        const rerender = () => {
            if (!mindMapInstance || !rawTreeData) return;
            mindMapInstance.setData(transformNode(rawTreeData));
        };

        // ─── 懒加载逻辑 ───────────────────────────────────────────────────────────

        /** 通过 API 加载节点子方法，并展开该节点 */
        const loadChildren = async (rawNode) => {
            if (!rawNode || rawNode.isMapper) return;
            if (loadedSet.has(rawNode.id)) {
                // 已加载：切换展开/收起
                rawNode.expand = !rawNode.expand;
                rerender();
                return;
            }

            console.log(`[LazyLoad] 加载节点: ${rawNode.label} (${rawNode.className}#${rawNode.methodName})`);
            try {
                const res = await fetch('/api/tree/expand', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        className: rawNode.className,
                        methodName: rawNode.methodName
                    })
                });
                if (!res.ok) {
                    ElementPlus.ElMessage.error(`加载失败 HTTP ${res.status}`);
                    return;
                }
                const apiChildren = await res.json();
                console.log(`[LazyLoad] 获取到 ${(apiChildren || []).length} 个子节点`);
                rawNode.children = apiChildren || [];
                rawNode.expand = true;
                loadedSet.add(rawNode.id);
                rerender();
            } catch (e) {
                console.error('[LazyLoad] 请求异常:', e);
                ElementPlus.ElMessage.error('加载子节点失败：' + e.message);
            }
        };

        // ─── 节点交互处理 ─────────────────────────────────────────────────────────

        /**
         * 处理节点上的 + / - 按钮点击，或节点本身点击。
         * 统一通过 rawTreeData 判断节点状态，避免依赖 nodeInstance 内部结构。
         */
        const handleNodeClick = (nodeInstance) => {
            // 兼容两种 getData 调用方式
            const nodeData = nodeInstance.getData();
            const nodeId = (nodeData && nodeData.data && nodeData.data.id)
                ? nodeData.data.id
                : nodeInstance.getData('id');

            if (!nodeId || !rawTreeData) return;

            // 跳过占位符节点和 SQL 叶子节点
            if (nodeId.endsWith('__ph__') || nodeId.includes('__sql__')) return;

            console.log(`[Click] 节点 id=${nodeId}`);

            const rawNode = findNode(rawTreeData, nodeId);
            if (!rawNode) {
                console.warn(`[Click] 未找到 rawNode, id=${nodeId}`);
                return;
            }

            if (rawNode.isMapper) {
                // Mapper 节点：切换展开/收起（SQL 子节点已在 transformNode 中准备好）
                rawNode.expand = !rawNode.expand;
                rerender();
                return;
            }

            // 普通方法节点：懒加载或切换展开
            loadChildren(rawNode);
        };

        // ─── 初始化 ───────────────────────────────────────────────────────────────

        const initTree = async () => {
            try {
                const res = await fetch('/api/tree/initialize', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(entry.value)
                });
                if (!res.ok) {
                    ElementPlus.ElMessage.error('初始化失败，请检查服务');
                    return;
                }
                rawTreeData = await res.json();

                // 根节点默认收起，点击 + 才加载下一级
                rawTreeData.expand = false;
                loadedSet.clear();

                console.log('[Init] rawTreeData:', rawTreeData);

                const mapData = transformNode(rawTreeData);

                if (mindMapInstance) {
                    mindMapInstance.setData(mapData);
                } else {
                    mindMapInstance = new MindMap({
                        el: document.getElementById('mindMapContainer'),
                        data: mapData,
                        layout: 'logicalStructure',
                        theme: 'classic',
                        readonly: true,
                        alwaysShowExpandBtn: false, // 禁用默认的展开按钮
                        hoverRectColor: 'transparent', // 隐藏悬浮和激活选中时的外围蓝色矩形框
                        isUseCustomNodeContent: true,
                        customCreateNodeContent: (node) => {
                            return createCustomNodeDom(node);
                        },
                        themeConfig: {
                            paddingX: 0,
                            paddingY: 0,
                            lineColor: '#818cf8',
                            lineWidth: 2,
                            root: {
                                fillColor: 'transparent',
                                borderColor: 'transparent',
                                borderWidth: 0,
                                active: {
                                    fillColor: 'transparent',
                                    borderColor: 'transparent'
                                },
                                hover: {
                                    fillColor: 'transparent',
                                    borderColor: 'transparent',
                                    borderWidth: 0
                                }
                            },
                            second: {
                                fillColor: 'transparent',
                                borderColor: 'transparent',
                                borderWidth: 0,
                                active: {
                                    fillColor: 'transparent',
                                    borderColor: 'transparent'
                                },
                                hover: {
                                    fillColor: 'transparent',
                                    borderColor: 'transparent',
                                    borderWidth: 0
                                }
                            },
                            node: {
                                fillColor: 'transparent',
                                borderColor: 'transparent',
                                borderWidth: 0,
                                active: {
                                    fillColor: 'transparent',
                                    borderColor: 'transparent'
                                },
                                hover: {
                                    fillColor: 'transparent',
                                    borderColor: 'transparent',
                                    borderWidth: 0
                                }
                            }
                        }
                    });

                    mindMapInstance.on('scale_change', (scale) => {
                        zoomPercent.value = Math.round(scale * 100);
                    });
                }

                leftCardCollapsed.value = true;
                ElementPlus.ElMessage.success('调用链加载成功，点击 + 按钮展开节点');
            } catch (e) {
                console.error('[Init] error:', e);
                ElementPlus.ElMessage.error('无法初始化调用链：' + e.message);
            }
        };

        // ─── 工具栏操作 ───────────────────────────────────────────────────────────

        const zoomIn = () => {
            if (mindMapInstance) {
                let scale = mindMapInstance.view.scale + 0.1;
                if (scale > 3) scale = 3;
                mindMapInstance.view.setScale(scale);
            }
        };

        const zoomOut = () => {
            if (mindMapInstance) {
                let scale = mindMapInstance.view.scale - 0.1;
                if (scale < 0.2) scale = 0.2;
                mindMapInstance.view.setScale(scale);
            }
        };

        const zoomReset = () => {
            if (mindMapInstance) mindMapInstance.reset();
        };

        const toggleDragMode = () => {
            dragMode.value = !dragMode.value;
            ElementPlus.ElMessage.info(dragMode.value ? '已开启拖拽' : '已关闭拖拽');
        };

        const undo = () => {
            if (mindMapInstance) mindMapInstance.execCommand('BACK');
        };

        const redo = () => {
            if (mindMapInstance) mindMapInstance.execCommand('FORWARD');
        };

        const shareLink = () => {
            if (navigator.clipboard) {
                navigator.clipboard.writeText(window.location.href);
                ElementPlus.ElMessage.success('已复制页面链接，可直接分享！');
            } else {
                ElementPlus.ElMessage.info('链接：' + window.location.href);
            }
        };

        const exitApp = () => {
            ElementPlus.ElMessageBox.confirm('是否重置分析画布？', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                rawTreeData = null;
                loadedSet.clear();
                if (mindMapInstance) {
                    mindMapInstance.setData({ data: { text: '请从左下角重新加载分析入口' }, children: [] });
                }
                leftCardCollapsed.value = false;
                ElementPlus.ElMessage.success('画布已重置');
            });
        };

        const showHelp = () => {
            ElementPlus.ElMessageBox.alert(
                '1. 在左下角 🎯 输入类名和方法名，点击「加载调用链」<br/>' +
                '2. 点击节点的 <b>+</b> 按钮，动态加载子方法调用（每次点击时请求后端）<br/>' +
                '3. Mapper 节点点击 + 直接展开对应 SQL 语句<br/>' +
                '4. 再次点击节点可折叠/展开',
                '使用指南',
                { dangerouslyUseHTMLString: true }
            );
        };

        return {
            entry,
            initTree,
            leftCardCollapsed,
            zoomPercent,
            dragMode,
            zoomIn,
            zoomOut,
            zoomReset,
            toggleDragMode,
            undo,
            redo,
            shareLink,
            exitApp,
            showHelp
        };
    }
}).use(ElementPlus).mount('#app');
