# 2026-06-23 关系图（DAG）调用链可视化重构设计规格文档

本文档定义了将系统前端的可视化展现形式由传统的“树状脑图（Mind Map）”重构为“有向无环图/关系图（DAG）”的具体实现规格。

---

## 1. 变更背景与目标

目前，当前端加载和展开方法调用链时，画布使用的是 `simple-mind-map`（思维导图）库：
1. **树状结构的限制**：思维导图库在底层强制要求树形结构，每个节点有且仅能有一个父节点。
2. **多对一调用冲突**：在真实的 Java 代码中，一个公共方法（例如 `saveInvoice`）通常会被多个不同的上级方法（例如不同的 Service 实现或 Controller 端点）共同调用。
3. **节点冲突与联动问题**：由于每个方法的 ID 使用的是 `className#methodName`，在多处被调用时，思维导图会因为节点 ID 冲突而发生渲染混乱，或者导致折叠/展开状态互相联动，无法满足独立展示和独立操作的要求。

**目标**：
* 移除 `simple-mind-map` 脑图库，引入 **AntV G6 (v4.8.x)** 可视化图引擎。
* 支持多对一方法调用（多个父节点连线指向同一个共享子节点）。
* 保证方法节点依然采用原有的 HTML 自定义卡片样式（包含指示器、类名、方法名、备注及 SQL 代码块）。
* 实现流畅的懒加载（点击 `+` 展开、点击 `−` 折叠）和 DAG 图自适应重绘。

---

## 2. 模块级设计与改动细节

### 前端模块

#### 2.1 依赖库变更 [index.html](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.html)
* **移除**旧有的脑图 CSS 与 JS 依赖：
  - 移除 `<link href="./lib/simple-mind-map/simpleMindMap.css" rel="stylesheet">`
  - 移除 `simple-mind-map` 相关的 JS 引入。
* **引入** AntV G6 JS：
  - 添加 `<script src="https://unpkg.com/@antv/g6@4.8.24/dist/g6.min.js"></script>`

#### 2.2 核心画布渲染逻辑 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js)
* **自定义节点注册**：
  使用 `G6.registerNode` 注册自定义类型 `'custom-node'`。
  - 在 `draw` 方法中返回一个 `'dom'` 类型的 shape。
  - 在 HTML 属性中拼接节点渲染模板，模板结构与之前的卡片布局保持一致。
  - 自定义卡片中的 `+` 和 `−` 折叠按钮绑定原生的原生点击事件（使用 `node.collapsed` 状态判断）。
* **Graph 初始化**：
  ```javascript
  const graph = new G6.Graph({
      container: 'mindMapContainer', // 保持原有容器 ID
      width: window.innerWidth,
      height: window.innerHeight,
      renderer: 'svg', // 必须使用 SVG 渲染模式以支持 DOM 节点
      layout: {
          type: 'dagre',
          rankdir: 'LR',      // 自左向右的分层布局，契合代码调用走向
          nodesep: 40,        // 节点垂直间距
          ranksep: 60,        // 层级水平间距
          controlPoints: true // 开启折线/平滑曲线
      },
      defaultNode: {
          type: 'custom-node',
          size: [280, 120] // 定义卡片的基础高宽，防止 SVG foreignObject 遮挡
      },
      defaultEdge: {
          type: 'cubic-horizontal', // 水平三阶贝塞尔曲线连线，平滑优雅
          style: {
              stroke: '#818cf8',     // 连线颜色与原先主题保持一致
              lineWidth: 2,
              endArrow: {
                  path: G6.Arrow.triangle(8, 10, 0), // 尾部终点箭头，直观指示调用方向
                  fill: '#818cf8'
              }
          }
      },
      modes: {
          default: ['drag-canvas', 'zoom-canvas'] // 支持拖拽和滚轮缩放画布
      }
  });
  ```
* **状态维护与数据更新**：
  - 在 Setup 中维护响应式数组 `nodes` 和 `edges`。
  - 根节点初始化：`/api/tree/initialize` 成功后，将根节点推入 `nodes` 并渲染。
* **懒加载与更新逻辑 (Lazy Load & ChangeData)**：
  - 当点击节点卡片边缘的 `+` 按钮时，读取该节点的 `className` 与 `methodName` 发起后端异步请求 `/api/tree/expand`。
  - 获取到子方法列表后：
    1. 遍历每个子节点，如 `nodes` 中不存在该子节点（以 `id = className#methodName` 作唯一键），则将其添加到 `nodes` 中，并默认设置 `collapsed = true`；
    2. 创建一条从当前节点到该子节点的边：`{ source: parentId, target: childId }`。如 `edges` 中已存在，则跳过，避免重复连线；
    3. 将当前父节点的 `collapsed` 更新为 `false`；
    4. 最终调用 `graph.changeData({ nodes, edges })`，让 G6 自适应重新排列布局并画线。
  - 当点击 `−` 折叠按钮时：
    1. 将当前节点的 `collapsed` 标记为 `true`；
    2. 递归收集该节点下的所有子节点。检查 these 子节点是否还有**其他活跃/未折叠的父节点**连接。若无其他父节点，则将该子节点从 `nodes` 列表和 `edges` 列表中过滤清除；
    3. 调用 `graph.changeData({ nodes, edges })` 重新生成布局。

#### 2.3 节点样式配合 [index.css](file:///Users/hanjie/IdeaProjects/code-analysis/src/index.css)
* 清理与旧有思维导图相关的样式库覆写。
* 编写适用于 G6 SVG 外层容器的卡片定位与缩放样式，确保 custom HTML 节点内部的内容居中对齐、折叠按钮贴边渲染，且不会出现滚动条截断现象。

---

## 3. 验证计划

### 自动验证
* 在终端执行 `mvn test`，确认后端分析组件和用例全部通过，保持无回归错误。

### 手动验证
1. 启动应用访问 `http://localhost:8080`，确认根节点 `InvoicingApplyJob.process` 或 `FinanceController.saveInvoiceWithoutTitle` 能够正确且居中加载。
2. 连续展开子方法，直至显示出 `InvoiceRepositoryImpl.saveInvoice`。
3. 验证当该 `saveInvoice` 被两个不同的前置方法共同调用时，画布上仅存在**一个** `saveInvoice` 节点，并且存在两条指向它的边。
4. 点击该共享节点上的 `+` 按钮，验证它能够正确向外扩展它的后续 Mapper 调用与 SQL 执行列表，且关系线清晰无偏倚。
