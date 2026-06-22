import MindMap from 'https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.min.js';

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

            const text = node.isMapper ? `💾 ${node.label}` : (node.label || node.text || '?');
            const isLoaded = loadedSet.has(node.id);
            const hasApiChildren = node.children && node.children.length > 0;
            const hasSql = node.isMapper && node.dbOperations && node.dbOperations.length > 0;

            let children = [];

            if (hasApiChildren) {
                // 已通过 API 加载的子节点，递归转换
                children = node.children.map(c => transformNode(c));
            } else if (hasSql) {
                // Mapper 节点：直接使用 SQL 作为叶子节点
                children = node.dbOperations.map((op, idx) => ({
                    data: {
                        text: op.sql || `[${op.operationType || 'SQL'}] ${op.tableName || ''}`,
                        id: `${node.id}__sql__${idx}`,
                        _isSql: true,
                        expand: false
                    },
                    children: []
                }));
            } else if (!node.isMapper && !isLoaded) {
                // 未加载的普通节点：添加占位子节点使其显示 + 按钮
                children = [{
                    data: {
                        text: '···',
                        id: `${node.id}__ph__`,
                        _isPlaceholder: true
                    },
                    children: []
                }];
            }
            // 已加载但无子节点（空方法）：children = []，不显示 + 按钮

            const expand = (node.expand !== undefined) ? !!node.expand : false;

            return {
                data: {
                    text,
                    id: node.id,
                    expand
                },
                children
            };
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
                        // 始终显示展开/收起 + 按钮，无需 hover
                        alwaysShowExpandBtn: true
                    });

                    // 监听 expand_btn_click（点击 + 按钮）
                    mindMapInstance.on('expand_btn_click', (nodeInstance) => {
                        console.log('[Event] expand_btn_click fired');
                        handleNodeClick(nodeInstance);
                    });

                    // 同时监听 node_click（点击节点本体），作为备用触发方式
                    mindMapInstance.on('node_click', (nodeInstance) => {
                        console.log('[Event] node_click fired');
                        handleNodeClick(nodeInstance);
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
