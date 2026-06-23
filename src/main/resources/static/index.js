const { createApp, ref, nextTick } = Vue;

// ─── G6 自定义 DOM 节点注册 ──────────────────────────────────────────────────
const createNodeHtml = (cfg) => {
    const dotIdx = cfg.className ? cfg.className.lastIndexOf('.') : -1;
    const shortName = dotIdx !== -1 ? cfg.className.substring(dotIdx + 1) : cfg.className;
    
    let headerText = '⚡ Service 方法';
    if (cfg.isRoot) {
        headerText = '🎯 入口方法';
    } else if (cfg.isMapper) {
        headerText = '💾 Mapper 接口';
    }

    let cardClass = 'mindmap-card';
    if (cfg.isMapper) {
        cardClass += ' is-mapper';
    } else if (cfg.isRoot) {
        cardClass += ' is-root';
    } else {
        cardClass += ' is-service';
    }

    let remarksHtml = '';
    if (cfg.remarks) {
        remarksHtml = `
            <div class="card-row">
                <span class="row-label">简介</span>
                <span class="row-badge" title="${cfg.remarks}">${cfg.remarks}</span>
            </div>
        `;
    }

    let sqlHtml = '';
    if (cfg.isMapper && cfg.dbOperations && cfg.dbOperations.length > 0) {
        let opHtmls = '';
        cfg.dbOperations.forEach(op => {
            const opTypeClass = op.operationType ? op.operationType.toLowerCase() : 'sql';
            opHtmls += `
                <div class="sql-box">
                    <span class="sql-type-tag ${opTypeClass}">${op.operationType || 'SQL'}</span>
                    <code class="sql-code">${op.sql || '[' + (op.operationType || 'SQL') + '] ' + (op.tableName || '')}</code>
                </div>
            `;
        });
        sqlHtml = `
            <div class="card-sql-container">
                <div class="sql-title">SQL 语句</div>
                ${opHtmls}
            </div>
        `;
    }

    const hasBody = cfg.isMapper && cfg.dbOperations && cfg.dbOperations.length > 0;
    const bodyHtml = hasBody ? `<div class="card-body">${sqlHtml}</div>` : '';

    let btnHtml = '';
    if (!cfg.isMapper) {
        const btnText = cfg.collapsed ? '+' : '−';
        btnHtml = `<div class="card-expand-btn" onclick="window.handleNodeExpandClick('${cfg.id}', event)">${btnText}</div>`;
    }
    return `
        <div class="mindmap-card-wrapper" onmousedown="window.handleNodeDragStart('${cfg.id}', event)">
            <div class="${cardClass}">
                <div class="card-delete-btn" onclick="window.handleNodeDeleteClick('${cfg.id}', event)">✖</div>
                <div class="card-header-type">
                    <span class="header-indicator"></span>
                    <span class="header-text">${headerText}</span>
                </div>
                ${remarksHtml}
                <div class="card-row">
                    <span class="row-label">类名</span>
                    <span class="row-badge" title="${cfg.className || ''}">${shortName || ''}</span>
                </div>
                <div class="card-row">
                    <span class="row-label">方法名</span>
                    <span class="row-badge">${cfg.methodName || ''}</span>
                </div>
                ${bodyHtml}
            </div>
            ${btnHtml}
        </div>
    `;
};

G6.registerNode('custom-node', {
    draw(cfg, group) {
        const hasBody = cfg.isMapper && cfg.dbOperations && cfg.dbOperations.length > 0;
        const height = hasBody ? 240 : (cfg.remarks ? 145 : 120);
        cfg.size = [300, height];
        
        return group.addShape('dom', {
            attrs: {
                x: -150,
                y: -height / 2,
                width: 300,
                height: height,
                html: createNodeHtml(cfg)
            },
            name: 'dom-node-keyshape',
            draggable: true
        });
    },
    getAnchorPoints(cfg) {
        const anchors = [];
        // 左侧边缘 (x = 0)，索引为 0 到 20
        for (let i = 0; i <= 20; i++) {
            anchors.push([0, i / 20]);
        }
        // 右侧边缘 (x = 1)，索引为 21 到 41
        for (let i = 0; i <= 20; i++) {
            anchors.push([1, i / 20]);
        }
        return anchors;
    }
});

const parsePathPoints = (path) => {
    if (Array.isArray(path)) {
        path = path.map(seg => seg.join(' ')).join(' ');
    }
    let points = [];
    if (typeof path === 'string') {
        const commands = path.match(/[a-zA-Z][^a-zA-Z]*/g) || [];
        commands.forEach(cmd => {
            const type = cmd[0];
            const args = cmd.substring(1).trim().split(/[\s,]+/).map(parseFloat).filter(n => !isNaN(n));
            if (type === 'M' || type === 'L') {
                if (args.length >= 2) {
                    points.push({ x: args[0], y: args[1] });
                }
            } else if (type === 'Q') {
                if (args.length >= 4) {
                    if (points.length > 0) {
                        points[points.length - 1] = { x: args[0], y: args[1] };
                    }
                    points.push({ x: args[2], y: args[3] });
                }
            } else if (type === 'A') {
                if (args.length >= 7) {
                    if (points.length > 0) {
                        let isIncomingVertical = true;
                        if (points.length >= 2) {
                            const pLast = points[points.length - 1];
                            const pPrev = points[points.length - 2];
                            isIncomingVertical = Math.abs(pLast.x - pPrev.x) < 2;
                        }
                        const pLast = points[points.length - 1];
                        let corner;
                        if (isIncomingVertical) {
                            corner = { x: pLast.x, y: args[6] };
                        } else {
                            corner = { x: args[5], y: pLast.y };
                        }
                        points[points.length - 1] = corner;
                    }
                    points.push({ x: args[5], y: args[6] });
                }
            }
        });

        // 过滤掉距离过近的控制点（弧线的起点和终点通常相距 < 15px），只留下主干拐点
        const filtered = [];
        points.forEach(p => {
            if (filtered.length === 0) {
                filtered.push(p);
            } else {
                const last = filtered[filtered.length - 1];
                const dist = Math.sqrt((p.x - last.x) ** 2 + (p.y - last.y) ** 2);
                if (dist >= 15) {
                    filtered.push(p);
                }
            }
        });
        points = filtered;
    }
    return points;
};

const drawHandles = (cfg, group, keyShape) => {
    const path = keyShape.attr('path');
    if (!path || path.length < 2) return;

    const points = parsePathPoints(path);
    const handleCount = Math.min(points.length - 1, 3);

    // 1. 获取并更新/创建手柄
    for (let i = 0; i < 3; i++) {
        const handleName = `edge-handle-${i}`;
        const children = group.get('children') || [];
        let handle = children.find(child => child && child.get('name') === handleName);

        if (i < handleCount) {
            const pStart = points[i];
            const pEnd = points[i + 1];

            const midX = (pStart.x + pEnd.x) / 2;
            const midY = (pStart.y + pEnd.y) / 2;

            const isHorizontal = Math.abs(pStart.y - pEnd.y) < 2;

            const w = isHorizontal ? 16 : 6;
            const h = isHorizontal ? 6 : 16;

            const attrs = {
                x: midX - w / 2,
                y: midY - h / 2,
                width: w,
                height: h,
                radius: 3,
                fill: '#3b82f6',
                cursor: isHorizontal ? 'ns-resize' : 'ew-resize',
                opacity: 0.95
            };

            if (handle) {
                handle.attr(attrs);
                handle.show();
            } else {
                group.addShape('rect', {
                    attrs: attrs,
                    name: handleName,
                    draggable: true
                });
            }
        } else {
            if (handle) {
                handle.hide();
            }
        }
    }
};

G6.registerEdge('custom-polyline', {
    afterDraw(cfg, group) {
        const keyShape = group.get('children')[0];
        drawHandles(cfg, group, keyShape);
    },
    afterUpdate(cfg, item) {
        const group = item.getContainer();
        const keyShape = item.getKeyShape();
        drawHandles(cfg, group, keyShape);
    }
}, 'polyline');

window.handleNodeExpandClick = (nodeId, event) => {
    event.stopPropagation();
    if (window.vueAppInstance && window.vueAppInstance.toggleNode) {
        window.vueAppInstance.toggleNode(nodeId);
    }
};

window.handleNodeDeleteClick = (nodeId, event) => {
    event.stopPropagation();
    if (window.vueAppInstance && window.vueAppInstance.deleteNode) {
        window.vueAppInstance.deleteNode(nodeId);
    }
};

window.handleNodeDragStart = (nodeId, event) => {
    // 仅支持鼠标左键拖动
    if (event.button !== 0) return;
    
    // 排除折叠展开按钮和 SQL 容器等有自己滚动/交互的区域
    if (event.target.closest('.card-expand-btn') || event.target.closest('.card-sql-container')) {
        return;
    }

    event.preventDefault();
    event.stopPropagation(); // 阻止事件冒泡以避免触发画布 drag-canvas

    const graph = window.vueAppInstance.getGraphInstance();
    const nodeItem = graph.findById(nodeId);
    if (!nodeItem) return;

    const model = nodeItem.getModel();
    const scale = graph.getZoom();

    const startMouseX = event.clientX;
    const startMouseY = event.clientY;
    
    const startNodeX = model.x || 0;
    const startNodeY = model.y || 0;

    const handleMouseMove = (moveEvent) => {
        const dx = (moveEvent.clientX - startMouseX) / scale;
        const dy = (moveEvent.clientY - startMouseY) / scale;

        graph.updateItem(nodeItem, {
            x: startNodeX + dx,
            y: startNodeY + dy
        });
    };

    const handleMouseUp = () => {
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
};

// ─── Vue App 实例化 ────────────────────────────────────────────────────────
createApp({
    setup() {
        const entry = ref({
            className: 'com.omp.finance.intf.app.FinanceController',
            methodName: 'saveInvoiceWithoutTitle'
        });

        const leftCardCollapsed = ref(false);
        const zoomPercent = ref(100);
        const dragMode = ref(true);

        let graphInstance = null;
        const nodes = [];
        const edges = [];

        const updateGraph = () => {
            if (graphInstance) {
                const clonedNodes = nodes.map(n => ({ ...n }));
                const clonedEdges = edges.map(e => ({ ...e }));
                graphInstance.changeData({ nodes: clonedNodes, edges: clonedEdges });
                graphInstance.layout();
            }
        };

        const initGraph = () => {
            if (graphInstance) return;
            
            graphInstance = new G6.Graph({
                container: 'mindMapContainer',
                width: window.innerWidth,
                height: window.innerHeight,
                renderer: 'svg', // 必须使用 SVG 渲染以支持 DOM 节点
                layout: {
                    type: 'dagre',
                    rankdir: 'LR',      // 从左到右布局
                    nodesep: 45,        // 节点垂直间距
                    ranksep: 40,        // 节点水平间距
                    controlPoints: false
                },
                defaultNode: {
                    type: 'custom-node',
                    size: [300, 120]
                },
                defaultEdge: {
                    type: 'custom-polyline',
                    style: {
                        radius: 10,
                        offset: 20,
                        stroke: '#000000',
                        lineWidth: 2,
                        endArrow: {
                            path: G6.Arrow.triangle(8, 10, 0),
                            fill: '#000000',
                            stroke: '#000000',
                            lineWidth: 1
                        }
                    }
                },
                modes: {
                    default: ['drag-canvas', 'zoom-canvas']
                }
            });

            graphInstance.on('viewportchange', () => {
                const scale = graphInstance.getZoom();
                zoomPercent.value = Math.round(scale * 100);
            });

            graphInstance.on('edge:mousedown', (e) => {
                e.stopPropagation();
                const target = e.target;
                if (!target) return;
                const name = target.get('name');
                if (!name || !name.startsWith('edge-handle-')) return;

                const index = parseInt(name.split('-')[2]);
                const edgeItem = e.item;
                if (!edgeItem) return;

                const model = edgeItem.getModel();
                const keyShape = edgeItem.getContainer().get('children')[0];
                const path = keyShape.attr('path');
                if (!path || path.length < 2) return;

                const points = parsePathPoints(path);

                let cps = model.controlPoints;
                if (!cps || cps.length === 0) {
                    if (points.length >= 4) {
                        cps = [
                            { x: points[1].x, y: points[1].y },
                            { x: points[2].x, y: points[2].y }
                        ];
                    } else {
                        cps = [];
                    }
                } else {
                    cps = cps.map(p => ({ x: p.x, y: p.y }));
                }

                const handleMouseMove = (moveEvent) => {
                    const point = graphInstance.getPointByClient(moveEvent.clientX, moveEvent.clientY);

                    if (cps.length < 2 && points.length >= 4) {
                        cps = [
                            { x: points[1].x, y: points[1].y },
                            { x: points[2].x, y: points[2].y }
                        ];
                    }

                    if (cps.length >= 2) {
                        if (index === 0) {
                            cps[0].y = point.y;
                            const sourceModel = edgeItem.getSource().getModel();
                            const sourceHeight = sourceModel.size[1];
                            const sourceRatio = (point.y - sourceModel.y + sourceHeight / 2) / sourceHeight;
                            const clampedRatio = Math.max(0, Math.min(1, sourceRatio));
                            const newSourceAnchor = 21 + Math.round(clampedRatio * 20);

                            graphInstance.updateItem(edgeItem, {
                                controlPoints: cps,
                                sourceAnchor: newSourceAnchor
                            });
                        } else if (index === 1) {
                            cps[0].x = point.x;
                            cps[1].x = point.x;
                            graphInstance.updateItem(edgeItem, {
                                controlPoints: cps
                            });
                        } else if (index === 2) {
                            cps[1].y = point.y;
                            const targetModel = edgeItem.getTarget().getModel();
                            const targetHeight = targetModel.size[1];
                            const targetRatio = (point.y - targetModel.y + targetHeight / 2) / targetHeight;
                            const clampedRatio = Math.max(0, Math.min(1, targetRatio));
                            const newTargetAnchor = Math.round(clampedRatio * 20);

                            graphInstance.updateItem(edgeItem, {
                                controlPoints: cps,
                                targetAnchor: newTargetAnchor
                            });
                        }
                    }
                };

                const handleMouseUp = () => {
                    document.removeEventListener('mousemove', handleMouseMove);
                    document.removeEventListener('mouseup', handleMouseUp);
                };

                document.addEventListener('mousemove', handleMouseMove);
                document.addEventListener('mouseup', handleMouseUp);
            });

            graphInstance.on('edge:dblclick', (e) => {
                e.stopPropagation();
                const edgeItem = e.item;
                if (edgeItem) {
                    graphInstance.updateItem(edgeItem, {
                        controlPoints: null,
                        sourceAnchor: 31,
                        targetAnchor: 10
                    });
                }
            });

            window.addEventListener('resize', () => {
                if (graphInstance) {
                    graphInstance.changeSize(window.innerWidth, window.innerHeight);
                }
            });
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
                const rootData = await res.json();

                nodes.length = 0;
                edges.length = 0;

                const hasBody = rootData.isMapper && rootData.dbOperations && rootData.dbOperations.length > 0;
                const height = hasBody ? 240 : (rootData.remarks ? 145 : 120);

                nodes.push({
                    id: rootData.id,
                    className: rootData.className,
                    methodName: rootData.methodName,
                    isMapper: rootData.isMapper,
                    dbOperations: rootData.dbOperations,
                    remarks: rootData.remarks,
                    isRoot: true,
                    collapsed: true,
                    size: [300, height]
                });

                initGraph();
                updateGraph();

                leftCardCollapsed.value = true;
                ElementPlus.ElMessage.success('调用链加载成功，点击 + 按钮展开节点');
            } catch (e) {
                console.error('[Init] error:', e);
                ElementPlus.ElMessage.error('无法初始化调用链：' + e.message);
            }
        };

        const expandNodeChildren = async (parentNode) => {
            try {
                const res = await fetch('/api/tree/expand', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        className: parentNode.className,
                        methodName: parentNode.methodName
                    })
                });
                if (!res.ok) {
                    ElementPlus.ElMessage.error(`加载失败 HTTP ${res.status}`);
                    return;
                }
                const apiChildren = await res.json();
                if (!apiChildren || apiChildren.length === 0) return;

                apiChildren.forEach(child => {
                    let existingNode = nodes.find(n => n.id === child.id);
                    if (!existingNode) {
                        const hasBody = child.isMapper && child.dbOperations && child.dbOperations.length > 0;
                        const height = hasBody ? 240 : (child.remarks ? 145 : 120);
                        nodes.push({
                            id: child.id,
                            className: child.className,
                            methodName: child.methodName,
                            isMapper: child.isMapper,
                            dbOperations: child.dbOperations,
                            remarks: child.remarks,
                            collapsed: true,
                            size: [300, height]
                        });
                    }
                    
                    const edgeExists = edges.some(e => e.source === parentNode.id && e.target === child.id);
                    if (!edgeExists) {
                        edges.push({
                            source: parentNode.id,
                            target: child.id,
                            sourceAnchor: 31,
                            targetAnchor: 10
                        });
                    }
                });
            } catch (e) {
                console.error('[Expand] error:', e);
                ElementPlus.ElMessage.error('加载子节点失败：' + e.message);
            }
        };

        const collapseNodeChildren = (parentId) => {
            const outgoingEdges = edges.filter(e => e.source === parentId);
            
            outgoingEdges.forEach(edge => {
                const childId = edge.target;
                const otherParents = edges.filter(e => e.target === childId && e.source !== parentId);
                
                if (otherParents.length === 0) {
                    collapseNodeChildren(childId);
                    
                    const nodeIdx = nodes.findIndex(n => n.id === childId);
                    if (nodeIdx !== -1) {
                        nodes.splice(nodeIdx, 1);
                    }
                }
            });

            for (let i = edges.length - 1; i >= 0; i--) {
                if (edges[i].source === parentId) {
                    edges.splice(i, 1);
                }
            }
        };

        const deleteNode = (nodeId) => {
            const nodeIndex = nodes.findIndex(n => n.id === nodeId);
            if (nodeIndex === -1) return;
            const node = nodes[nodeIndex];

            // 若为根节点，清空整个画布
            if (node.isRoot) {
                nodes.length = 0;
                edges.length = 0;
                if (graphInstance) {
                    graphInstance.clear();
                }
                leftCardCollapsed.value = false;
                ElementPlus.ElMessage.success('已删除根节点，画布已重置');
                return;
            }

            // 级联删除算法：递归收集所有孤立子节点
            const nodesToDelete = new Set([nodeId]);
            let foundNew = true;
            while (foundNew) {
                foundNew = false;
                for (const edge of edges) {
                    if (nodesToDelete.has(edge.source) && !nodesToDelete.has(edge.target)) {
                        const targetParents = edges.filter(e => e.target === edge.target);
                        const allParentsDeleted = targetParents.every(e => nodesToDelete.has(e.source));
                        if (allParentsDeleted) {
                            nodesToDelete.add(edge.target);
                            foundNew = true;
                        }
                    }
                }
            }

            // 移除关联的边
            for (let i = edges.length - 1; i >= 0; i--) {
                const edge = edges[i];
                if (nodesToDelete.has(edge.source) || nodesToDelete.has(edge.target)) {
                    edges.splice(i, 1);
                }
            }

            // 从节点列表中清除
            for (const idToDelete of nodesToDelete) {
                const idx = nodes.findIndex(n => n.id === idToDelete);
                if (idx !== -1) {
                    nodes.splice(idx, 1);
                }
            }

            updateGraph();
            ElementPlus.ElMessage.success('节点已删除');
        };

        const toggleNode = async (nodeId) => {
            const node = nodes.find(n => n.id === nodeId);
            if (!node) return;

            if (!node.collapsed) {
                node.collapsed = true;
                collapseNodeChildren(nodeId);
                updateGraph();
            } else {
                node.collapsed = false;
                await expandNodeChildren(node);
                updateGraph();
            }
        };

        const setupInstance = {
            entry,
            initTree,
            leftCardCollapsed,
            zoomPercent,
            dragMode,
            toggleNode,
            deleteNode,
            getGraphInstance: () => graphInstance,
            zoomIn: () => {
                if (graphInstance) {
                    const zoom = graphInstance.getZoom();
                    graphInstance.zoomTo(zoom + 0.1);
                }
            },
            zoomOut: () => {
                if (graphInstance) {
                    const zoom = graphInstance.getZoom();
                    graphInstance.zoomTo(Math.max(0.1, zoom - 0.1));
                }
            },
            zoomReset: () => {
                if (graphInstance) {
                    graphInstance.zoomTo(1.0);
                    graphInstance.fitView(20);
                }
            },
            toggleDragMode: () => {
                ElementPlus.ElMessage.info('拖拽缩放已在画布中默认开启。');
            },
            undo: () => {},
            redo: () => {},
            shareLink: () => {
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(window.location.href);
                    ElementPlus.ElMessage.success('已复制页面链接，可直接分享！');
                } else {
                    ElementPlus.ElMessage.info('链接：' + window.location.href);
                }
            },
            exitApp: () => {
                ElementPlus.ElMessageBox.confirm('是否重置分析画布？', '提示', {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }).then(() => {
                    nodes.length = 0;
                    edges.length = 0;
                    if (graphInstance) {
                        graphInstance.clear();
                    }
                    leftCardCollapsed.value = false;
                    ElementPlus.ElMessage.success('画布已重置');
                });
            },
            showHelp: () => {
                ElementPlus.ElMessageBox.alert(
                    '1. 在左下角 🎯 输入类名 and 方法名，点击「加载调用链」<br/>' +
                    '2. 点击节点的 <b>+</b> 按钮，动态加载子方法调用，关系线会自动收敛到共享节点上<br/>' +
                    '3. 再次点击节点的 <b>−</b> 按钮可收起折叠节点',
                    '使用指南',
                    { dangerouslyUseHTMLString: true }
                );
            }
        };

        window.vueAppInstance = setupInstance;
        return setupInstance;
    }
}).use(ElementPlus).mount('#app');
