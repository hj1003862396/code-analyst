# 无箭头可手动调整连线 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 移除调用链连线上的箭头，并支持用户直接拖拽连线任意位置进行弯折调整，双击可恢复默认路径。

**架构：** 在 G6 Graph 初始化时，修改 defaultEdge 样式移去 endArrow。通过注册 edge:mousedown 和 edge:dblclick 事件监听器，动态计算并更新 edge 的 controlPoints 以实现连线调整和重置。

**技术栈：** JavaScript, G6 (v4.8.24)

---

### Task 1: 移除连线箭头

**文件：**
- 修改：`src/main/resources/static/index.js` (第 213-225 行)

- [ ] **步骤 1：修改 G6 defaultEdge 样式配置**

  定位到 `src/main/resources/static/index.js` 中的 `defaultEdge` 定义，将 `endArrow` 属性移除或注释掉。

  修改前的代码结构：
  ```javascript
                  defaultEdge: {
                      type: 'polyline',
                      style: {
                          radius: 10,
                          offset: 20,
                          stroke: '#000000',
                          lineWidth: 2,
                          endArrow: {
                              path: G6.Arrow.triangle(8, 10, 0),
                              fill: '#000000'
                          }
                      }
                  },
  ```

  修改后的代码结构：
  ```javascript
                  defaultEdge: {
                      type: 'polyline',
                      style: {
                          radius: 10,
                          offset: 20,
                          stroke: '#000000',
                          lineWidth: 2
                      }
                  },
  ```

- [ ] **步骤 2：进行简单检查**
  运行/预览页面，确保调用链加载后的连接线不再带有箭头。

---

### Task 2: 实现连线拖动与重置功能

**文件：**
- 修改：`src/main/resources/static/index.js` (在 `initGraph` 内部)

- [ ] **步骤 1：在 `initGraph` 中注册 `edge:mousedown` 拖拽监听事件**

  在 `initGraph` 函数的 `graphInstance` 实例化后，添加 `edge:mousedown` 监听。在事件回调中调用 `e.stopPropagation()` 以防触发背景画布的拖拽模式。然后监听全局的 `mousemove` 和 `mouseup` 来更新 `controlPoints`。

  添加的代码：
  ```javascript
              graphInstance.on('edge:mousedown', (e) => {
                  e.stopPropagation();
                  const edgeItem = e.item;
                  if (!edgeItem) return;

                  const handleMouseMove = (moveEvent) => {
                      const point = graphInstance.getPointByClient(moveEvent.clientX, moveEvent.clientY);
                      graphInstance.updateItem(edgeItem, {
                          controlPoints: [{ x: point.x, y: point.y }]
                      });
                  };

                  const handleMouseUp = () => {
                      document.removeEventListener('mousemove', handleMouseMove);
                      document.removeEventListener('mouseup', handleMouseUp);
                  };

                  document.addEventListener('mousemove', handleMouseMove);
                  document.addEventListener('mouseup', handleMouseUp);
              });
  ```

- [ ] **步骤 2：在 `initGraph` 中注册 `edge:dblclick` 双击重置事件**

  在 `initGraph` 中继续添加 `edge:dblclick` 监听。双击连线时，将 `controlPoints` 更新为 `null`，使折线退回默认自动寻路状态。

  添加的代码：
  ```javascript
              graphInstance.on('edge:dblclick', (e) => {
                  e.stopPropagation();
                  const edgeItem = e.item;
                  if (edgeItem) {
                      graphInstance.updateItem(edgeItem, {
                          controlPoints: null
                      });
                  }
              });
  ```

- [ ] **步骤 3：进行手动功能验证**
  - 加载调用链生成图。
  - 用鼠标左键按住任意线条并拖拽，检查线条是否能够根据鼠标位置实时发生折弯。
  - 双击被弯折的连线，检查连线是否恢复成初始的自动对齐状态。
