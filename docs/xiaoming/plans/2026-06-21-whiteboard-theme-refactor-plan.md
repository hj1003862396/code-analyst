# 白板主题界面重构 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 将代码库接口与数据库分析工具的前端页面重构为纯白背景的沉浸式无限白板，并提供精美的悬浮折叠卡片、截图同款悬浮工具栏和 XMind 风格下划线直角肘线关系树。

**架构：** 利用全屏 SVG 作为白板背景承载 D3 树，应用绝对定位将工具栏和折叠面板浮于其上。通过 Vue 3 响应式变量绑定实现侧栏淡入淡出折叠和右下角缩放数值的双向联动。在 D3.js 树图的节点更新生命周期中，根节点使用实心蓝色圆角卡片，子节点采用文本 + 底部下划线形式，连线计算采用 90 度直角肘线并在水平方向延伸对齐。

**技术栈：** HTML5, Vanilla CSS, Vue 3, Element Plus, D3.js v7

---

### Task 1：前端布局与悬浮折叠面板重构

**文件：**
- 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：重写 CSS 变量与白板布局样式**
  替换 `index.html` 中的样式块。去除原本的 `left-panel`、`middle-panel`、`right-panel` 左右分栏 flex 布局，将 `body` 设置为纯白色，`tree-svg` 改为绝对定位铺满全屏，添加浮动工具栏和折叠面板的样式：
  ```css
  /* 基础与白板样式 */
  body {
      background-color: #ffffff !important;
      overflow: hidden;
      margin: 0;
      width: 100vw;
      height: 100vh;
  }
  #app {
      position: relative;
      width: 100vw;
      height: 100vh;
      overflow: hidden;
  }
  #tree-svg {
      position: absolute;
      left: 0;
      top: 0;
      width: 100%;
      height: 100%;
      z-index: 1;
  }
  
  /* 悬浮工具栏样式 */
  .float-toolbar {
      position: absolute;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
      z-index: 10;
      display: flex;
      align-items: center;
      padding: 6px 12px;
      gap: 8px;
  }
  
  .toolbar-top-left {
      top: 20px;
      left: 20px;
  }
  
  .toolbar-top-right {
      top: 20px;
      right: 20px;
  }
  
  .toolbar-left-vertical {
      top: 100px;
      left: 20px;
      flex-direction: column;
      padding: 12px 8px;
      gap: 16px;
      border-radius: 12px;
  }
  
  .toolbar-bottom-right {
      bottom: 20px;
      right: 20px;
      border-radius: 20px;
      padding: 6px 16px;
  }
  
  /* 折叠面板悬浮样式 */
  .left-input-card {
      position: absolute;
      left: 24px;
      bottom: 24px;
      width: 340px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08);
      z-index: 9;
      padding: 20px;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }
  
  .left-input-card.collapsed {
      transform: translateX(-380px);
      opacity: 0;
      pointer-events: none;
  }
  
  .right-detail-drawer {
      position: absolute;
      right: 24px;
      top: 80px;
      bottom: 80px;
      width: 440px;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(10px);
      border: 1px solid #e2e8f0;
      border-radius: 16px;
      box-shadow: -5px 5px 25px rgba(0, 0, 0, 0.06);
      z-index: 9;
      padding: 24px;
      display: flex;
      flex-direction: column;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }
  
  .right-detail-drawer.collapsed {
      transform: translateX(480px);
      opacity: 0;
      pointer-events: none;
  }
  
  /* 折叠状态激活触发按钮 */
  .trigger-btn {
      position: absolute;
      z-index: 10;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 50%;
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
      cursor: pointer;
  }
  ```

- [ ] **步骤 2：在 HTML 中重构四大悬浮工具栏与折叠面板**
  替换 `index.html` 中的 DOM 树结构。渲染包含以下元素的高仿截图白板工具栏：
  * **左上栏**：退出 (`exit-btn`)、画板 (`board-btn`)。
  * **右上栏**：分享 (`share-btn`)、编辑下拉 (`edit-btn`)、搜索、评论、更多操作。
  * **左侧垂直栏**：Logo图标、矩形、文字(T)、便签、折线连接、帧、表格、画笔、列表、应用格、聊天气泡。
  * **右下栏**：撤销重做图标、抓手图标、缩放百分比按钮 `- 89% +`、帮助按钮。
  * **左下悬浮卡片**：分析入口表单。添加收起图标。在其外面提供一个 `leftToggleTrigger` 气泡按钮。
  * **右侧悬浮抽屉**：节点分析报告。添加收起图标。在其外面提供一个 `rightToggleTrigger` 气泡按钮。

- [ ] **步骤 3：在 Vue setup() 中定义折叠状态与缩放变量**
  添加折叠状态相关的响应式变量并绑定事件：
  ```javascript
  const leftCardCollapsed = ref(false);
  const rightDrawerCollapsed = ref(false);
  const zoomPercent = ref(100);
  const dragMode = ref(false); // 抓手模式
  ```

- [ ] **步骤 4：通过本地启动测试验证悬浮面板折叠交互**
  手动开启调试服务并检查面板在点击收起时是否流畅缩回屏幕外，露出完整的白色画布背景，且触发气泡显现。

---

### Task 2：D3.js 树节点下划线化与直角连线渲染

**文件：**
- 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：重构 D3 节点生成与属性计算逻辑**
  更新 `updateTree` 里的节点绘制。限制只有 `d.depth === 0` 才渲染填充的蓝色圆角 `rect.card-bg`；当 `d.depth > 0` 时隐藏背景，测量文本 `bbox.width`，让 `line.underline` 成为唯一的底部横线：
  ```javascript
  // 测量宽度
  update.each(function(d) {
      const nodeG = d3.select(this);
      const text = nodeG.select("text");
      const labelText = d.data.isMapper ? `💾 ${d.data.label}` : d.data.label;
      text.text(labelText);

      const bbox = text.node().getBBox();
      d.width = bbox.width + 20; // 为下划线和节点距离分配宽度

      if (d.depth === 0) {
          // 根节点：皇家蓝实心背景
          nodeG.select("rect.card-bg")
              .attr("width", d.width)
              .style("fill", "#3b66e2")
              .style("display", "block");
          nodeG.select("line.underline").style("display", "none");
          text.style("fill", "#ffffff").attr("x", 10);
      } else {
          // 子方法节点：无底色，纯文字 + 蓝色/红色下划线
          nodeG.select("rect.card-bg").style("display", "none");
          nodeG.select("line.underline")
              .attr("x1", -5)
              .attr("x2", d.width - 15)
              .style("stroke", d.data.isMapper ? "#ef4444" : "#3b66e2")
              .style("stroke-dasharray", d.data.isMapper ? "3,3" : "none")
              .style("display", "block");
          text.style("fill", "#1e293b").attr("x", -5);
      }
  });
  ```

- [ ] **步骤 2：重写直角折弯肘线 (Elbow Link) 生成算法**
  连线需从父节点下划线右侧平滑弯折连接到子节点下划线左侧：
  ```javascript
  // 精确计算连接点
  const computeElbowPath = (d) => {
      const startY = d.source.y + (d.source.width ? (d.source.width - 20) : 100);
      const endY = d.target.y - 10;
      const midY = (startY + endY) / 2;
      return `M ${startY} ${d.source.x} L ${midY} ${d.source.x} L ${midY} ${d.target.x} L ${endY} ${d.target.x}`;
  };
  ```
  在 `.link` path 的 `d` 属性赋值上绑定 `computeElbowPath(d)`。根据 `d.target.data.isMapper` 动态将连线色彩设为蓝色实线或红色虚线。

- [ ] **步骤 3：绑定缩放百分比到 D3.js 缩放控制器**
  在 `renderD3Tree` 初始化 D3 Zoom 时，监听 zoom 事件，实时计算 `Math.round(event.transform.k * 100)` 并赋值给 `zoomPercent.value`。
  编写缩放控制函数 `zoomIn()` and `zoomOut()` 以绑定 to HTML 中的 `+` 和 `-` 按钮，通过 `d3.select('#tree-svg').transition().call(zoom.scaleBy, 1.2)` 等方法实现平滑缩放。

- [ ] **步骤 4：在浏览器中打开并对整体做打包验证**
  访问并渲染 `ShortLinkController#detail` 的方法调用链：
  * 确认连接线完美呈 90 度直角，折线流向下划线，普通节点和 SQL 节点区分明显，白色背景纯净且缩放指示准确。
  * 运行 `mvn clean package -DskipTests` 打包项目。
