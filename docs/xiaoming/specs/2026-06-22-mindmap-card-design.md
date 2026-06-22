# 脑图卡片与 SQL 展现设计规范 (重构版 - 第三版)

## 1. 功能需求
- 过滤无关方法调用（主要是 getter/setter 以及 Stream 集合操作等）。
- 普通方法节点与 Mapper 节点采用精致的**明亮渐变微光毛玻璃**卡片展现（SQL 直接合并在 Mapper 卡片内）。
- 根方法节点在加载时默认收起。
- **展开/折叠按钮紧贴在卡片本体右侧边缘**，而不是在连接线末端或拉出一根线。
- **只能通过点击加减号 `+` / `−` 按钮进行折叠和展开**，避免点击卡片本体误触。
- **重新引入类简称**，并可在悬浮时查看完整类路径，防止因只显示方法名而失去上下文。
- **将说明注释改为“方法简介”区域**，以更直观的方式展现方法的作用。

## 2. 视觉与信息重构规范

### 2.1 卡片层级与排版 (方案 A 流式版式 - 严格的垂直单行序列)
卡片内容采用**严格的单行自上而下垂直排列布局**，每一部分均独占一行：

- **第一行 (Row 1)**: 类简称（如 `ShortLinkController`），灰色小字（`11px`），通过 `title` 属性关联完整包类名，鼠标悬停时可查看。
- **第二行 (Row 2)**: 图标（⚡/💾）+ 方法名（如 `detail`），使用加粗的主色字体（`15px`），作为主视觉焦点。
- **第三行 (Row 3)**: **方法简介**: 若节点存在 Javadoc 注释，独占一行展示，字体为半透明灰（`12px`），前缀展示为 `方法简介：`。
- **第四行 (Row 4)**: **SQL 语句 (仅 Mapper 且如有)**: 独占一行展示，采用磨砂深色底盒（`background: rgba(15, 23, 42, 0.9)`，`backdrop-filter: blur(8px)`），搭配天蓝色（`#38bdf8`）的 SQL 代码字体，以保证极高的对比度和现代感。

### 2.2 视觉美化与毛玻璃效果
- **毛玻璃卡片样式**:
  - 卡片背景：`background: rgba(255, 255, 255, 0.7)`。
  - 模糊滤镜：`backdrop-filter: blur(16px) saturate(180%)`。
  - 边框：`border: 1px solid rgba(255, 255, 255, 0.5)`。
  - 阴影：极其轻量、大范围的柔和投影。
- **渐变微光侧边**:
  - **根节点**: 翡翠绿至青蓝渐变（`linear-gradient(to bottom, #10b981, #06b6d4)`）。
  - **Service/Controller 节点**: 靛蓝至紫罗兰渐变（`linear-gradient(to bottom, #6366f1, #8b5cf6)`）。
  - **Mapper 节点**: 珊瑚橙至深橘渐变（`linear-gradient(to bottom, #f97316, #ea580c)`）。
- **Hover 动效**:
  - 悬浮时卡片向上微移 `2px`，并施加一层与节点类型颜色一致 of 淡色呼吸发光阴影（Glow Shadow）。

### 2.3 背景画布与整体协调
- **微型网格画布背景**:
  - 脑图容器 `#mindMapContainer` 采用石板白背景，并通过 CSS `radial-gradient` 渲染 Figma 风格的微型点阵背景：
    ```css
    #mindMapContainer {
        background-color: #f8fafc;
        background-image: radial-gradient(#e2e8f0 1.5px, transparent 1.5px);
        background-size: 20px 20px;
    }
    ```
- **边缘展开按钮样式**:
  - 圆形按钮贴在卡片本体右侧（`right: -10px`），采用毛玻璃背景与微白色边框。

---

## 3. 交互控制与数据转换

### 3.1 数据转换控制展开收起 (`index.js` -> `transformNode`)
- 在 `transformNode` 数据转换中，根据 `node.expand`（是否展开）和 `isLoaded`（是否已加载）状态来决定返回的 `children`：
  - 若节点**未加载**或**未展开**：直接返回 `children = []`。不绘制任何多余子线。
  - 若节点**已加载**且**已展开**：返回 `node.children.map(c => transformNode(c))`，展示下级调用链。

### 3.2 贴边按钮点击响应
- 自定义卡片中的 `.card-expand-btn` 绑定点击事件：
  - 调用 `e.stopPropagation()` 阻止事件冒泡。
  - 若节点**未加载**：发起 lazy-load 请求获取子节点，获取成功后将子节点保存，设 `expand = true` 并重新渲染脑图。
  - 若节点**已加载**：切换 `expand` 状态，并重新渲染脑图。
