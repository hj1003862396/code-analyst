# 脑图卡片与 SQL 展现设计规范 (重构版 - 第二版)

## 1. 功能需求
- 过滤无关方法调用（主要是 getter/setter 以及 Stream 集合操作等）。
- 普通方法节点与 Mapper 节点采用 Dify 样式的卡片展现（SQL 直接合并在 Mapper 卡片内）。
- 根方法节点在加载时默认收起。
- **展开/折叠按钮紧贴在卡片本体右侧边缘**，而不是在连接线末端或拉出一根线。
- **只能通过点击加减号 `+` / `-` 按钮进行折叠和展开**，避免点击卡片本体误触。

## 2. 视觉与信息重构规范

### 2.1 卡片信息展现
- **仅展示方法名、备注说明（如有）、SQL 语句（仅 Mapper 且如有）**。
- 彻底移除源码预览（Source Code）和类名文字，保持节点精简美观。

### 2.2 视觉美化
- 在 MindMap 实例化时，将 `themeConfig` 中的 `paddingX` 和 `paddingY` 设为 `0`，且将所有节点（`root`, `second`, `node`）的 `fillColor`、`borderColor` 设为 `transparent`，`borderWidth` 设为 `0`。
- **隐藏默认按钮**：通过 CSS 将 `.smm-expand-btn { display: none !important; }` 隐藏。
- **自定义贴边按钮**：在自定义卡片 DOM 的右侧边缘，绝对定位一个圆圈 `.card-expand-btn`（半径 10px，位于 `right: -10px`）。
- CSS 效果：
  - **根节点**：翡翠绿（左侧边框：`#10b981`）。
  - **Service/Controller 节点**：星空靛蓝（左侧边框：`#6366f1`）。
  - **Mapper 节点**：珊瑚暖橙（左侧边框：`#f97316`，背景偏米白）。
  - **Hover 动效**：悬浮时卡片向上微移 2px，阴影加深。
  - **SQL 代码框**：深色底（`#1e293b`），浅青色高亮代码字体。

---

## 3. 交互控制与数据转换

### 3.1 数据转换控制展开收起 (`index.js` -> `transformNode`)
- 在 `transformNode` 数据转换中，根据 `node.expand`（是否展开）和 `isLoaded`（是否已加载）状态来决定返回的 `children`：
  - 若节点**未加载**或**未展开**：直接返回 `children = []`。这样脑图完全不会绘制多余的子连线，也不会展示任何 `...` 占位框。
  - 若节点**已加载**且**已展开**：返回 `node.children.map(c => transformNode(c))`，从而展示下级调用链。

### 3.2 贴边按钮点击响应
- 自定义卡片中的 `.card-expand-btn` 绑定点击事件：
  - 调用 `e.stopPropagation()` 阻止事件冒泡。
  - 若节点**未加载**：发起 lazy-load 请求获取子节点，获取成功后将子节点保存，设 `expand = true` 并重新渲染脑图。
  - 若节点**已加载**：切换 `expand` 状态，并重新渲染脑图。
