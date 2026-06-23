# G6 连线样式调整与节点删除功能 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 将调用链关系图的连线样式变更为圆角折线样式，并在每个节点卡片右上角增加支持级联删除的节点删除按钮。

**架构：** 在 G6 图初始化中配置 `defaultEdge` 为 `polyline` 并附带圆角参数；在节点 HTML 中增加 `card-delete-btn` 并在 `index.js` 中开发相应的级联删除逻辑以递归删除无前置依赖的子孙节点。

**技术栈：** HTML5, Vue 3, AntV G6 (v4.8.x), Vanilla CSS

---

### Task 1: 调整 G6 连线为圆角折线样式

**文件：**
- 修改：`src/main/resources/static/index.js:205-215`

- [ ] **步骤 1：修改 `initGraph` 的 `defaultEdge` 样式配置**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 中，定位到 `defaultEdge` 的定义并将其修改为如下内容：

  ```javascript
                  defaultEdge: {
                      type: 'polyline',
                      style: {
                          radius: 10,
                          offset: 20,
                          stroke: '#818cf8',
                          lineWidth: 2,
                          endArrow: {
                              path: G6.Arrow.triangle(8, 10, 0),
                              fill: '#818cf8'
                          }
                      }
                  },
  ```

---

### Task 2: 节点卡片 UI 变更（删除按钮 HTML 与 CSS）

**文件：**
- 修改：`src/main/resources/static/index.js:63-70`
- 修改：`src/main/resources/static/index.css` (文件尾部追加)

- [ ] **步骤 1：在 `createNodeHtml` 中注入删除按钮的 HTML 结构**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 的 `createNodeHtml` 函数中，为 `.mindmap-card` 最外层结构内首个子元素添加 `card-delete-btn`：

  ```javascript
      return `
          <div class="mindmap-card-wrapper" onmousedown="window.handleNodeDragStart('${cfg.id}', event)">
              <div class="${cardClass}">
                  <div class="card-delete-btn" onclick="window.handleNodeDeleteClick('${cfg.id}', event)">✖</div>
                  <div class="card-header-type">
                      <span class="header-indicator"></span>
                      <span class="header-text">${headerText}</span>
                  </div>
  ```

- [ ] **步骤 2：在样式表末尾追加删除按钮的 CSS 样式**

  在 [index.css](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.css) 尾部追加如下样式定义：

  ```css
  /* 节点删除按钮样式 */
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

---

### Task 3: 节点删除业务逻辑与事件注册

**文件：**
- 修改：`src/main/resources/static/index.js` (注册全局方法、定义并导出 `deleteNode`)

- [ ] **步骤 1：注册全局删除事件点击回调函数**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 的 `window.handleNodeExpandClick` 下方添加如下注册函数：

  ```javascript
  window.handleNodeDeleteClick = (nodeId, event) => {
      event.stopPropagation();
      if (window.vueAppInstance && window.vueAppInstance.deleteNode) {
          window.vueAppInstance.deleteNode(nodeId);
      }
  };
  ```

- [ ] **步骤 2：在 Vue Setup 中定义级联删除逻辑 `deleteNode`**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 的 `collapseNodeChildren` 函数下方定义 `deleteNode` 逻辑：

  ```javascript
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
  ```

- [ ] **步骤 3：在 `setupInstance` 中公开 `deleteNode` 方法**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 的 `setupInstance` 对象定义中补充 `deleteNode`：

  ```javascript
          const setupInstance = {
              entry,
              initTree,
              leftCardCollapsed,
              zoomPercent,
              dragMode,
              toggleNode,
              deleteNode,
              getGraphInstance: () => graphInstance,
  ```

---

### Task 4: 构建、启动并验证功能

**文件：**
- 修改：无

- [ ] **步骤 1：编译项目确保正常启动**

  在终端运行后端构建命令：
  ```bash
  mvn clean compile
  ```
  预期结果：构建成功，无任何编译错误。

- [ ] **步骤 2：启动应用**

  运行 Spring Boot 启动命令：
  ```bash
  mvn spring-boot:run
  ```

- [ ] **步骤 3：功能性验证**

  打开浏览器访问 `http://localhost:8080`，载入默认入口方法：
  1. 确认所有节点连线呈现圆角折线。
  2. 展开多层节点，点击普通子节点的右上角 `✖` 按钮，验证被删除的节点及其无其他父依赖的子节点全部正常删除。
  3. 点击根节点的右上角 `✖` 按钮，确认画布被重置回输入载入状态。
