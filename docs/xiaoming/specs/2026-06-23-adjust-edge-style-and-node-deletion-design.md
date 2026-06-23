# G6 连线样式调整与节点删除功能 设计文档

本文档描述了将调用链关系图连线样式变更为圆角折线（polyline），并新增节点删除功能（支持级联删除）的设计方案。

## 1. 连线样式调整

### 1.1 G6 Graph 配置变更
修改 `src/main/resources/static/index.js` 中的 `initGraph` 配置，将 `defaultEdge` 变更为 `polyline` 类型：

```javascript
defaultEdge: {
    type: 'polyline',
    style: {
        radius: 10,   // 圆角半径设置为 10px
        offset: 20,   // 拐弯最小距离
        stroke: '#818cf8',
        lineWidth: 2,
        endArrow: {
            path: G6.Arrow.triangle(8, 10, 0),
            fill: '#818cf8'
        }
    }
}
```

---

## 2. 节点删除功能

### 2.1 界面变更 (HTML & CSS)
在节点卡片的右上角增加删除按钮，允许用户快捷删除节点。

#### 2.1.1 DOM 结构修改
在 `src/main/resources/static/index.js` 的 `createNodeHtml` 中，为 `.mindmap-card` 结构的最外层（内部首个子元素）增加删除按钮：
```html
<div class="card-delete-btn" onclick="window.handleNodeDeleteClick('${cfg.id}', event)">✖</div>
```

#### 2.1.2 样式表新增 (src/main/resources/static/index.css)
为 `.card-delete-btn` 添加精美的圆圈悬浮变红样式：
```css
.card-delete-btn {
    position: absolute;
    top: 6px;
    right: 8px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: transparent;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    color: #94a3b8;
    cursor: pointer;
    z-index: 10;
    transition: all 0.2s ease-in-out;
}
.card-delete-btn:hover {
    background: #fee2e2;
    color: #ef4444;
}
```

### 2.2 事件处理逻辑 (JS)

#### 2.2.1 全局函数注册 (src/main/resources/static/index.js)
```javascript
window.handleNodeDeleteClick = (nodeId, event) => {
    event.stopPropagation();
    if (window.vueAppInstance && window.vueAppInstance.deleteNode) {
        window.vueAppInstance.deleteNode(nodeId);
    }
};
```

#### 2.2.2 Vue 实例方法定义 (src/main/resources/static/index.js)
在 Vue setup 的 `setupInstance` 中暴露出 `deleteNode` 方法：
```javascript
const deleteNode = (nodeId) => {
    const nodeIndex = nodes.findIndex(n => n.id === nodeId);
    if (nodeIndex === -1) return;
    const node = nodes[nodeIndex];

    // 若为根节点，重置画布
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

    // 级联删除算法（找出所有因该节点删除而失去所有父节点的子孙节点）
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

    // 删除关联的所有边
    for (let i = edges.length - 1; i >= 0; i--) {
        const edge = edges[i];
        if (nodesToDelete.has(edge.source) || nodesToDelete.has(edge.target)) {
            edges.splice(i, 1);
        }
    }

    // 从 nodes 列表中移除
    for (const idToDelete of nodesToDelete) {
        const idx = nodes.findIndex(n => n.id === idToDelete);
        if (idx !== -1) {
            nodes.splice(idx, 1);
        }
    }

    updateGraph();
    ElementPlus.ElMessage.success('节点已删除');
};
```

---

## 3. 验证方案

### 3.1 编译与启动测试
1. 执行 `mvn clean compile` 确认编译无误。
2. 执行 `mvn spring-boot:run` 启动服务。

### 3.2 功能性验证
1. 打开 `http://localhost:8080`，载入默认入口方法。
2. 展开节点，检查连线是否全部更新为圆角折线样式。
3. 点击卡片右上角的 `✖` 按钮，校验：
   * 普通叶子节点删除：仅删除叶子节点及相连的连线，其他节点无影响。
   * 中间节点删除：会将其后续的子节点全部递归级联删除（如果这些子节点没有其他父节点连接）。
   * 根节点删除：整个画布重置，展示初始表单卡片。
