# 三段式滑动连线手柄 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 解决三段式连线手柄偏离及不能连续拖拽的 Bug，并实现连线首尾连接点沿节点边缘滑动、中段水平移动的正交连线调整机制。

**架构：** 在 `custom-node` 中扩充锚点至左右各 21 个。在 `custom-polyline` 中引入 `afterUpdate` 生命周期并在其中重绘手柄；兼容解析数组形式的 `path`；在 `edge:mousedown` 拖拽时动态根据鼠标 Y 坐标计算滑动比例，并更新 `sourceAnchor`/`targetAnchor`。

**技术栈：** HTML5, CSS3, JavaScript, Vue.js, AntV G6 (v4.8.24)

---

### Task 1：扩展 `custom-node` 节点锚点配置

**文件：**
- 修改：`src/main/resources/static/index.js` (getAnchorPoints 部分)

- [ ] **步骤 1：定位并修改 `custom-node` 的 `getAnchorPoints` 函数**
  修改前的代码：
  ```javascript
      getAnchorPoints(cfg) {
          return [
              [0, 0.5], // 左侧中心
              [1, 0.5]  // 右侧中心
          ];
      }
  ```
  修改后的代码：
  ```javascript
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
  ```

---

### Task 2：修复路径点解析 `parsePathPoints` 兼容数组

**文件：**
- 修改：`src/main/resources/static/index.js` (parsePathPoints 部分)

- [ ] **步骤 1：在 `parsePathPoints` 开头增加将 Array 转换为 String 的逻辑**
  修改前的代码：
  ```javascript
  const parsePathPoints = (path) => {
      let points = [];
      if (Array.isArray(path)) {
          points = path.map(segment => ({ x: segment[1], y: segment[2] }));
      } else if (typeof path === 'string') {
  ```
  修改后的代码：
  ```javascript
  const parsePathPoints = (path) => {
      if (Array.isArray(path)) {
          path = path.map(seg => seg.join(' ')).join(' ');
      }
      let points = [];
      if (typeof path === 'string') {
  ```

---

### Task 3：重构自定义边 `custom-polyline` 实现 `afterUpdate` 更新手柄

**文件：**
- 修改：`src/main/resources/static/index.js` (custom-polyline 部分)

- [ ] **步骤 1：提取 `drawHandles` 辅助绘制函数，并添加 `afterUpdate` 生命周期**
  修改前的代码：
  ```javascript
  G6.registerEdge('custom-polyline', {
      afterDraw(cfg, group) {
          const keyShape = group.get('children')[0];
          const path = keyShape.attr('path');
          if (!path || path.length < 2) return;

          const points = parsePathPoints(path);

          // 绘制 3 段的拖动控制胶囊
          for (let i = 0; i < Math.min(points.length - 1, 3); i++) {
              const pStart = points[i];
              const pEnd = points[i + 1];

              const midX = (pStart.x + pEnd.x) / 2;
              const midY = (pStart.y + pEnd.y) / 2;

              const isHorizontal = Math.abs(pStart.y - pEnd.y) < 2;

              const w = isHorizontal ? 16 : 6;
              const h = isHorizontal ? 6 : 16;

              group.addShape('rect', {
                  attrs: {
                      x: midX - w / 2,
                      y: midY - h / 2,
                      width: w,
                      height: h,
                      radius: 3,
                      fill: '#3b82f6',
                      cursor: isHorizontal ? 'ns-resize' : 'ew-resize',
                      opacity: 0.95
                  },
                  name: `edge-handle-${i}`,
                  draggable: true
              });
          }
      }
  }, 'polyline');
  ```
  修改后的代码：
  ```javascript
  const drawHandles = (cfg, group, keyShape) => {
      // 1. 清理已有手柄
      const children = group.get('children');
      for (let i = children.length - 1; i >= 0; i--) {
          const child = children[i];
          const name = child.get('name');
          if (name && name.startsWith('edge-handle-')) {
              group.removeChild(child);
          }
      }

      // 2. 绘制新手柄
      const path = keyShape.attr('path');
      if (!path || path.length < 2) return;

      const points = parsePathPoints(path);

      for (let i = 0; i < Math.min(points.length - 1, 3); i++) {
          const pStart = points[i];
          const pEnd = points[i + 1];

          const midX = (pStart.x + pEnd.x) / 2;
          const midY = (pStart.y + pEnd.y) / 2;

          const isHorizontal = Math.abs(pStart.y - pEnd.y) < 2;

          const w = isHorizontal ? 16 : 6;
          const h = isHorizontal ? 6 : 16;

          group.addShape('rect', {
              attrs: {
                  x: midX - w / 2,
                  y: midY - h / 2,
                  width: w,
                  height: h,
                  radius: 3,
                  fill: '#3b82f6',
                  cursor: isHorizontal ? 'ns-resize' : 'ew-resize',
                  opacity: 0.95
              },
              name: `edge-handle-${i}`,
              draggable: true
          });
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
  ```

---

### Task 4：修改新增边时的默认锚点索引

**文件：**
- 修改：`src/main/resources/static/index.js` (expandNodeChildren 部分)

- [ ] **步骤 1：在 `expandNodeChildren` 中，修改新增边配置项的 `sourceAnchor` 和 `targetAnchor`**
  修改前的代码：
  ```javascript
                      const edgeExists = edges.some(e => e.source === parentNode.id && e.target === child.id);
                      if (!edgeExists) {
                          edges.push({
                              source: parentNode.id,
                              target: child.id,
                              sourceAnchor: 1,
                              targetAnchor: 0
                          });
                      }
  ```
  修改后的代码：
  ```javascript
                      const edgeExists = edges.some(e => e.source === parentNode.id && e.target === child.id);
                      if (!edgeExists) {
                          edges.push({
                              source: parentNode.id,
                              target: child.id,
                              sourceAnchor: 31, // 右侧中心
                              targetAnchor: 10  // 左侧中心
                          });
                      }
  ```

---

### Task 5：修改拖拽事件响应以支持滑动锚点

**文件：**
- 修改：`src/main/resources/static/index.js` (edge:mousedown 部分)

- [ ] **步骤 1：定位并重构 `edge:mousedown` 内的 `handleMouseMove` 函数**
  修改前的代码：
  ```javascript
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
                          } else if (index === 1) {
                              cps[0].x = point.x;
                              cps[1].x = point.x;
                          } else if (index === 2) {
                              cps[1].y = point.y;
                          }

                          graphInstance.updateItem(edgeItem, {
                              controlPoints: cps
                          });
                      }
                  };
  ```
  修改后的代码：
  ```javascript
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
  ```
