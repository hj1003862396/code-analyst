# 展示 SQL 语句 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在思维导图的 Mapper 节点下直接展开并显示对应的最终 SQL 语句，并在用户点击方法节点的 `+` 号时动态懒加载子方法调用，同时移除页面右侧多余的节点详细分析窗口。

**架构：**
1. 移除 `index.html` 中的右侧悬浮分析抽屉元素，实现全屏无打扰的思维导图展示。
2. 重构 `index.js` 的 `transformNode` 逻辑，非 Mapper 节点在未加载时加入一个“加载中...”占位子节点。当点击该节点的 `+` 号时，动态发起 `/api/tree/expand` 获取下级调用，并替换占位节点。
3. Mapper 节点的子节点直接设为对应的 SQL 语句，点击其 `+` 号直接展开。

**技术栈：** HTML5, Vue 3 (Element Plus), Vanilla CSS, simple-mind-map

---

### Task 1: 清理前端 HTML 布局，移除右侧详情抽屉

**文件：**
- 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：在 `index.html` 中移除右侧抽屉及悬浮气泡**

定位并移除 `src/main/resources/static/index.html` 中的下述结构（约 117 行至 203 行）：
```html
    <!-- 6. 右侧悬浮卡片：节点详细分析 -->
    <div class="right-detail-drawer" :class="{ collapsed: rightDrawerCollapsed }">
        ... (包含数据库操作汇总及子方法下钻列表的完整内容) ...
    </div>

    <!-- 右侧收起后的悬浮气泡 -->
    <div v-if="rightDrawerCollapsed" class="trigger-btn right-trigger" @click="rightDrawerCollapsed = false" title="展开详细分析">
        📝
    </div>
```

---

### Task 2: 实施思维导图懒加载交互与 SQL 节点化转换

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：移除 `index.js` 中与右侧抽屉相关的 Vue 响应式变量与冗余方法**

在 `index.js` 中移除 `selectedNode`, `rightDrawerCollapsed`, `selectNodeFromList` 的定义与 setup 函数中的返回。

- [ ] **步骤 2：重构 `transformNode` 函数以支持“加载中”占位符与 SQL 子节点渲染**

替换 `transformNode` 为：
```javascript
        const transformNode = (backendNode) => {
            if (!backendNode) return null;
            
            let text = backendNode.label;
            if (backendNode.isMapper) {
                text = `💾 ${backendNode.label}`;
            }
            
            let children = [];
            let hasActualChildren = backendNode.children && backendNode.children.length > 0;
            
            if (hasActualChildren) {
                children = backendNode.children.map(c => transformNode(c));
            } else if (backendNode.isMapper && backendNode.dbOperations && backendNode.dbOperations.length > 0) {
                // Mapper 节点的子节点即为 SQL 节点
                children = backendNode.dbOperations.map((op, idx) => {
                    return {
                        data: {
                            text: op.sql || `【${op.operationType}】${op.tableName}`,
                            id: `${backendNode.id}_sql_${idx}`,
                            isSql: true,
                            expand: false
                        },
                        children: []
                    };
                });
            } else if (!backendNode.isMapper) {
                // 未加载子方法的 Service/Controller 节点，放置“加载中...”占位符
                children = [{
                    data: {
                        text: '加载中...',
                        id: backendNode.id + '_placeholder',
                        isPlaceholder: true,
                        expand: true
                    },
                    children: []
                }];
            }

            // 确定展开状态，优先保留已记录的用户展开状态
            let isExpanded = false;
            if (backendNode.expand !== undefined) {
                isExpanded = backendNode.expand;
            } else {
                isExpanded = hasActualChildren;
            }

            return {
                data: {
                    text: text,
                    id: backendNode.id,
                    label: backendNode.label,
                    className: backendNode.className,
                    methodName: backendNode.methodName,
                    isMapper: backendNode.isMapper,
                    expand: isExpanded
                },
                children: children
            };
        };
```

- [ ] **步骤 3：在 `index.js` 中增加 `syncExpandState`, `activeNodeById`, `loadAndExpandNode` 等交互同步方法**

在 setup 函数中声明下述函数：
```javascript
        // 同步 MindMap 当前各个节点的展开/收起状态到 rawTreeData 中
        const syncExpandState = () => {
            if (!mindMapInstance || !rawTreeData) return;
            const currentData = mindMapInstance.getData();
            
            const traverseAndSync = (mindMapNode) => {
                if (!mindMapNode) return;
                const id = mindMapNode.data.id;
                const expand = mindMapNode.data.expand;
                
                const rawNode = findNodeInTree(rawTreeData, id);
                if (rawNode) {
                    rawNode.expand = expand;
                }
                
                if (mindMapNode.children) {
                    mindMapNode.children.forEach(traverseAndSync);
                }
            };
            traverseAndSync(currentData);
        };

        // 重新聚焦与激活指定 ID 的节点
        const activeNodeById = (id) => {
            if (mindMapInstance && mindMapInstance.renderer && mindMapInstance.renderer.root) {
                const findAndActive = (nodeInst) => {
                    if (nodeInst.getData().data.id === id) {
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

        // 异步加载子方法调用
        const loadAndExpandNode = async (nodeData) => {
            if (nodeData.isMapper) return;
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
                    
                    const rawNode = findNodeInTree(rawTreeData, nodeData.id);
                    if (rawNode) {
                        rawNode.children = children || [];
                        rawNode.expand = true; // 设置展开状态为真
                        
                        const mapData = transformNode(rawTreeData);
                        mindMapInstance.setData(mapData);
                        
                        nextTick(() => {
                            activeNodeById(nodeData.id);
                        });
                    }
                }
            } catch (e) {
                ElementPlus.ElMessage.error('加载子节点失败：' + e.message);
            }
        };
```

- [ ] **步骤 4：更新 `initTree` 方法以适配懒加载模式与按钮点击监听**

重构 `initTree` 方法，当获取到初始化树节点后，不再自动调用 `selectNode(rawTreeData)`。另外，在初始化 `MindMap` 时注册 `expand_btn_click` 监听器：
```javascript
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
                // 确保根节点最初是收起状态
                mapData.data.expand = false;

                if (mindMapInstance) {
                    mindMapInstance.setData(mapData);
                } else {
                    mindMapInstance = new MindMap({
                        el: document.getElementById('mindMapContainer'),
                        data: mapData,
                        layout: 'logicalStructure',
                        theme: 'classic',
                        readonly: true
                    });

                    mindMapInstance.on('expand_btn_click', (nodeInstance) => {
                        syncExpandState();
                        const data = nodeInstance.getData().data;
                        const children = nodeInstance.getData().children;
                        if (children && children.length === 1 && children[0].data.isPlaceholder) {
                            loadAndExpandNode(data);
                        }
                    });

                    mindMapInstance.on('scale_change', (scale) => {
                        zoomPercent.value = Math.round(scale * 100);
                    });
                }

                leftCardCollapsed.value = true;
            } catch (e) {
                ElementPlus.ElMessage.error('无法初始化调用链：' + e.message);
            }
        };
```

---

### Task 3: 运行验证

- [ ] **步骤 1：使用 Maven 启动 Spring Boot 服务并验证编译通过**

运行：`mvn clean spring-boot:run` 或通过 IDE 启动服务。

- [ ] **步骤 2：打开浏览器访问页面进行手势交互与 SQL 节点化测试**

访问 `http://localhost:8080/index.html`，执行：
1. 首节点以收起形态展示并有 `+` 号。
2. 点击 `+` 号能够动态调起 `/api/tree/expand` 加载下层节点，原节点高亮保持并聚焦。
3. 点击 Mapper 节点的 `+` 号时，直接在本地显示具体的物理 SQL 语句节点。
4. 页面右侧无任何侧边栏/抽屉遮挡，体验清爽。
