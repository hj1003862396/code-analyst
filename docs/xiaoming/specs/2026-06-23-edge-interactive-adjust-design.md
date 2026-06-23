# 可手动调整的三段式无箭头连线设计文档

本文档定义了在代码调用链分析工具中，移除连线箭头并支持通过拖动三个段手柄手动调整连线弯折形状的设计方案。

## 需求背景
1. **无箭头**：调用链中的关系线不需要箭头。
2. **三段式手柄调整**：在连线的三条段（水平首段、垂直中段、水平尾段）的中心处提供蓝色胶囊形手柄，用户拖拽这些手柄可分别上下、左右移动对应的线段，以直观改变折线走向。

## 方案设计

### 1. 注册自定义边 `custom-polyline`
继承 G6 的 `polyline`（折线），并在其 `afterDraw` 中根据计算出的渲染路径绘制三个胶囊形矩形：
- 获取当前折线的所有端点坐标（从 `path` 属性中解析）。
- 计算每段（Segment）的中心点坐标。
- 判断段的水平/垂直性：
  - 水平段：高 6px，宽 16px，设置 `ns-resize` 缩放光标，命名为 `edge-handle-0` 或 `edge-handle-2`。
  - 垂直段：高 16px，宽 6px，设置 `ew-resize` 缩放光标，命名为 `edge-handle-1`。
- 将默认边的类型更改为 `'custom-polyline'`。

### 2. 拖拽与坐标更新机制
在图实例上监听 `edge:mousedown`：
1. 检查点击的 target 名字是否为 `edge-handle-0`、`edge-handle-1` 或 `edge-handle-2`。
2. 在 `mousemove` 事件中，调用 `graphInstance.getPointByClient` 转换鼠标坐标：
   - 拖动 `edge-handle-0` (首段水平段) 垂直移动：修改 `controlPoints[0].y`。
   - 拖动 `edge-handle-1` (中段垂直段) 水平移动：修改 `controlPoints[0].x` 和 `controlPoints[1].x`。
   - 拖动 `edge-handle-2` (尾段水平段) 垂直移动：修改 `controlPoints[1].y`。
3. 调用 `graphInstance.updateItem` 更新边的 `controlPoints`。

---

## 模块修改清单

### `static/index.js`
- **修改位置 1**: 注册自定义边 `G6.registerEdge('custom-polyline', ...)`。
- **修改位置 2**: 修改 `defaultEdge.type` 为 `'custom-polyline'`。
- **修改位置 3**: 重写 `edge:mousedown` 以支持拖拽三段式手柄。
