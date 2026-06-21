import MindMap from 'https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.min.js';

const { createApp, ref, onMounted, nextTick } = Vue;

createApp({
    setup() {
        const entry = ref({
            className: 'com.omp.marketing.intf.web.ShortLinkController',
            methodName: 'detail'
        });

        const selectedNode = ref(null);
        const leftCardCollapsed = ref(false);
        const rightDrawerCollapsed = ref(false);
        const zoomPercent = ref(100);
        const dragMode = ref(false);

        let mindMapInstance = null;
        let rawTreeData = null; 

        // 转换后端的数据结构到 simple-mind-map 规格
        const transformNode = (backendNode) => {
            if (!backendNode) return null;
            const text = backendNode.isMapper ? `💾 ${backendNode.label}` : backendNode.label;
            const simpleNode = {
                data: {
                    text: text,
                    id: backendNode.id,
                    label: backendNode.label,
                    className: backendNode.className,
                    methodName: backendNode.methodName,
                    isMapper: backendNode.isMapper,
                    dbOperations: backendNode.dbOperations || []
                },
                children: []
            };
            if (backendNode.children) {
                simpleNode.children = backendNode.children.map(c => transformNode(c));
            }
            return simpleNode;
        };

        // 寻找对应 id 的节点
        const findNodeInTree = (node, id) => {
            if (node.id === id) return node;
            if (node.children) {
                for (const child of node.children) {
                    const found = findNodeInTree(child, id);
                    if (found) return found;
                }
            }
            return null;
        };

        // 选中及展开子节点
        const selectNode = async (nodeData) => {
            selectedNode.value = nodeData;
            rightDrawerCollapsed.value = false;

            if (!nodeData.children && !nodeData.isMapper) {
                try {
                    const res = await fetch('/api/tree/expand', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            className: nodeData.className,
                            methodName: nodeData.methodName
                        })
                    });
                    if (res.ok) {
                        const children = await res.json();
                        if (children && children.length) {
                            const rawNode = findNodeInTree(rawTreeData, nodeData.id);
                            if (rawNode) {
                                rawNode.children = children;
                                nodeData.children = children; // 同步模板绑定

                                const mapData = transformNode(rawTreeData);
                                mindMapInstance.setData(mapData);
                            }
                        }
                    }
                } catch (e) {
                    ElementPlus.ElMessage.error('展开子节点失败：' + e.message);
                }
            }
        };

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

                const mapData = transformNode(rawTreeData);
                if (mindMapInstance) {
                    mindMapInstance.setData(mapData);
                } else {
                    mindMapInstance = new MindMap({
                        el: document.getElementById('mindMapContainer'),
                        data: mapData,
                        layout: 'logicalStructure', // 逻辑结构图 (向右)
                        theme: 'classic',
                        readonly: true
                    });

                    mindMapInstance.on('node_active', (nodeInstance) => {
                        if (nodeInstance) {
                            const data = nodeInstance.getData().data;
                            selectNode(data);
                        }
                    });

                    mindMapInstance.on('scale_change', (scale) => {
                        zoomPercent.value = Math.round(scale * 100);
                    });
                }

                await selectNode(rawTreeData);
                leftCardCollapsed.value = true;
            } catch (e) {
                ElementPlus.ElMessage.error('无法初始化调用链：' + e.message);
            }
        };

        const selectNodeFromList = (child) => {
            // 钻取分析列表点击
            selectNode(child);
            
            if (mindMapInstance && mindMapInstance.renderer && mindMapInstance.renderer.root) {
                const findAndActive = (nodeInst) => {
                    if (nodeInst.getData().data.id === child.id) {
                        nodeInst.active();
                        mindMapInstance.execCommand('GO_TARGET_NODE', nodeInst);
                        return true;
                    }
                    if (nodeInst.children) {
                        for (const c of nodeInst.children) {
                            if (findAndActive(c)) return true;
                        }
                    }
                    return false;
                };
                findAndActive(mindMapInstance.renderer.root);
            }
        };

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
            if (mindMapInstance) {
                mindMapInstance.reset();
            }
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
                ElementPlus.ElMessage.success('已复制：' + window.location.href);
            }
        };

        const exitApp = () => {
            ElementPlus.ElMessageBox.confirm('是否重置分析画布？', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }).then(() => {
                if (mindMapInstance) {
                    mindMapInstance.setData({ data: { text: 'Empty' }, children: [] });
                }
                selectedNode.value = null;
                leftCardCollapsed.value = false;
                ElementPlus.ElMessage.success('画布已重置');
            });
        };

        const showHelp = () => {
            ElementPlus.ElMessageBox.alert(
                '1. 点击左下角 🎯 按钮展开或重载方法分析入口<br/>2. 点击树图中的节点，右侧将以抽屉展示对物理表的 CRUD 操作<br/>3. 点击右侧子调用列表中“钻取分析”，画布将自动聚焦到对应子节点并高亮显示',
                '使用指南',
                { dangerouslyUseHTMLString: true }
              );
          };

          return {
              entry,
              selectedNode,
              initTree,
              leftCardCollapsed,
              rightDrawerCollapsed,
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
              showHelp,
              selectNodeFromList
          };
      }
  }).use(ElementPlus).mount('#app');
