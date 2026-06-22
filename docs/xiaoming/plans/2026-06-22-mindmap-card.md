# 脑图卡片与 SQL 展现 实施计划 (第三版)

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 重构脑图卡片为带有渐变微光边框的明亮毛玻璃卡片，且内容采用严格的单行垂直序列排版（依次为类名、方法名、方法简介、SQL），并在脑图容器背景增加 Figma 网格点阵纹理以强化毛玻璃模糊效果。

**架构：**
- 修改 `index.css`，配置 `#mindMapContainer` 网格背景；将 `.mindmap-card` 重构为 `backdrop-filter: blur(16px)` 的半透明面板并配置渐变微光边框与霓虹 Hover Glow 阴影。
- 修改 `index.js`，在 `createCustomNodeDom` 方法中提取类简称并在第一行渲染（通过 title 属性保留完整包名）；第二行渲染图标与方法名；第三行垂直流式输出“方法简介”文字；最后流式输出 SQL 语句。

**技术栈：** HTML, CSS, JavaScript, simple-mind-map, Vue 3

---

### Task 1: 优化脑图容器网格点阵背景 (index.css)

**文件：**
- 修改：`src/main/resources/static/index.css`

- [ ] **步骤 1：为 #mindMapContainer 添加微型点阵网格背景**
  编辑 `index.css` 中 `#mindMapContainer` 的定义，添加浅色石板白背景和基于 `radial-gradient` 渲染的点阵网格背景，使毛玻璃拖拽移动时有真实的折射质感。
  
  ```css
  #mindMapContainer {
      position: absolute;
      left: 0;
      top: 0;
      width: 100%;
      height: 100%;
      z-index: 1;
      background-color: #f8fafc;
      background-image: radial-gradient(#e2e8f0 1.5px, transparent 1.5px);
      background-size: 20px 20px;
  }
  ```

---

### Task 2: 编写毛玻璃卡片、渐变侧边与 Hover 霓虹发光样式 (index.css)

**文件：**
- 修改：`src/main/resources/static/index.css`

- [ ] **步骤 1：重构 `.mindmap-card` 以及相关的渐变边框和毛玻璃细节**
  在 `index.css` 的 `.mindmap-card` 块及之后，将其改为半透明明亮背景并引入 `backdrop-filter: blur(16px)`。配置渐变边框，并移除黄色备注框改用清雅文字样式。
  代码块：
  ```css
  /* ==========================================
     MindMap Custom Node Cards (Glassmorphism Style)
     ========================================== */
  .mindmap-card {
      background: rgba(255, 255, 255, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.5);
      border-radius: 10px;
      padding: 12px 16px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.03), 0 2px 4px -1px rgba(0, 0, 0, 0.02);
      min-width: 200px;
      max-width: 360px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      backdrop-filter: blur(16px) saturate(180%);
      -webkit-backdrop-filter: blur(16px) saturate(180%);
      transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
      font-family: var(--font-sans);
      position: relative; /* 支撑加减号贴边定位 */
      border-left: 5px solid;
      border-image: linear-gradient(to bottom, #6366f1, #8b5cf6) 1; /* Service 节点默认渐变 */
  }

  .mindmap-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 10px 20px rgba(99, 102, 241, 0.15);
  }

  /* Mapper 节点珊瑚暖橙渐变 */
  .mindmap-card.is-mapper {
      border-image: linear-gradient(to bottom, #f97316, #ea580c) 1;
  }
  .mindmap-card.is-mapper:hover {
      box-shadow: 0 10px 20px rgba(249, 115, 22, 0.15);
  }

  /* 根节点翡翠绿渐变 */
  .mindmap-card.is-root {
      border-image: linear-gradient(to bottom, #10b981, #06b6d4) 1;
  }
  .mindmap-card.is-root:hover {
      box-shadow: 0 10px 20px rgba(16, 185, 129, 0.15);
  }

  /* 卡片内部自定义展开/折叠按钮 (半透明毛玻璃) */
  .card-expand-btn {
      position: absolute;
      right: -10px; /* 居中贴在右边线上 */
      top: 50%;
      transform: translateY(-50%);
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.85);
      border: 1px solid rgba(255, 255, 255, 0.6);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 800;
      color: var(--text-muted);
      cursor: pointer;
      z-index: 100;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
      transition: all 0.15s ease-in-out;
  }

  .card-expand-btn:hover {
      color: var(--primary);
      border-color: var(--primary);
      transform: translateY(-50%) scale(1.15);
  }

  /* 隐藏 simple-mind-map 自带的外部展开收起按钮 */
  .smm-expand-btn {
      display: none !important;
  }

  /* 垂直单行流式排版元素 */
  .card-class-name {
      font-size: 11px;
      color: #64748b;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      cursor: help;
  }

  .card-header {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-shrink: 0;
  }

  .card-icon {
      font-size: 14px;
  }

  .card-title {
      font-size: 14px;
      font-weight: 700;
      color: var(--text-main);
      word-break: break-all;
  }

  /* 紧凑垂直主体 */
  .card-body {
      display: flex;
      flex-direction: column;
      gap: 8px;
      border-top: 1px solid rgba(226, 232, 240, 0.5);
      padding-top: 8px;
  }

  /* 方法简介展示区 */
  .card-remarks-container {
      font-size: 12px;
      line-height: 1.5;
      color: #475569;
  }

  .remarks-title {
      font-size: 10px;
      color: var(--text-muted);
      font-weight: 700;
      margin-bottom: 2px;
  }

  .remarks-content {
      white-space: pre-wrap;
      word-break: break-all;
  }

  /* SQL 展现容器 - 高透深色毛玻璃 */
  .card-sql-container {
      background: rgba(15, 23, 42, 0.9);
      border: 1px solid rgba(255, 255, 255, 0.1);
      backdrop-filter: blur(8px);
      border-radius: 6px;
      padding: 8px;
      display: flex;
      flex-direction: column;
      gap: 6px;
  }

  .sql-title {
      font-size: 9px;
      color: #94a3b8;
      font-weight: bold;
      text-transform: uppercase;
      letter-spacing: 0.05em;
  }

  .sql-box {
      display: flex;
      flex-direction: column;
      gap: 4px;
  }

  .sql-type-tag {
      font-size: 8px;
      font-weight: 700;
      padding: 1px 4px;
      border-radius: 3px;
      width: fit-content;
      color: #ffffff;
      font-family: var(--font-mono);
  }
  .sql-type-tag.select { background: #3b82f6; }
  .sql-type-tag.insert { background: #10b981; }
  .sql-type-tag.update { background: #f59e0b; }
  .sql-type-tag.delete { background: #ef4444; }
  .sql-type-tag.unknown { background: #6b7280; }

  .sql-code {
      font-family: var(--font-mono);
      font-size: 11px;
      color: #38bdf8;
      white-space: pre-wrap;
      word-break: break-all;
  }
  ```

---

### Task 3: 重构 createCustomNodeDom 生成垂直流式布局 (index.js)

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：更新 `createCustomNodeDom` 方法代码以实现流式排列**
  修改 `index.js` 中 `createCustomNodeDom` 函数。
  - 第一行渲染类简称，配置 `title` 属性为全类名。
  - 第二行渲染方法头部（包含图标和方法名）。
  - 第三行若有备注，渲染“方法简介”文本块。
  - 第四行渲染 SQL 代码区域。
  
  ```javascript
  const createCustomNodeDom = (node) => {
      const data = node.nodeData.data;
      if (!data) return null;

      // 卡片主容器
      const div = document.createElement('div');
      div.className = 'mindmap-card';
      if (data.isMapper) {
          div.classList.add('is-mapper');
      }
      if (rawTreeData && data.id === rawTreeData.id) {
          div.classList.add('is-root');
      }

      // 1. 第一行：类简称 (Row 1)
      if (data.className) {
          const classNameDiv = document.createElement('div');
          classNameDiv.className = 'card-class-name';
          const dotIdx = data.className.lastIndexOf('.');
          classNameDiv.innerText = dotIdx !== -1 ? data.className.substring(dotIdx + 1) : data.className;
          classNameDiv.setAttribute('title', data.className); // 悬浮显示完整类名
          div.appendChild(classNameDiv);
      }

      // 2. 第二行：图标与方法名 (Row 2)
      const header = document.createElement('div');
      header.className = 'card-header';
      
      const icon = document.createElement('span');
      icon.className = 'card-icon';
      icon.innerText = data.isMapper ? '💾' : '⚡';
      
      const title = document.createElement('span');
      title.className = 'card-title';
      title.innerText = data.methodName || data.text || '';

      header.appendChild(icon);
      header.appendChild(title);
      div.appendChild(header);

      // 主体区块容器
      const body = document.createElement('div');
      body.className = 'card-body';

      // 3. 第三行：方法简介 (Row 3)
      if (data.remarks) {
          const remarksContainer = document.createElement('div');
          remarksContainer.className = 'card-remarks-container';
          
          const remarksTitle = document.createElement('div');
          remarksTitle.className = 'remarks-title';
          remarksTitle.innerText = '方法简介：';
          remarksContainer.appendChild(remarksTitle);

          const remarksContent = document.createElement('div');
          remarksContent.className = 'remarks-content';
          remarksContent.innerText = data.remarks;
          remarksContainer.appendChild(remarksContent);

          body.appendChild(remarksContainer);
      }

      // 4. 第四行：SQL 语句展示 (仅 Mapper且含有数据时展示) (Row 4)
      if (data.isMapper && data.dbOperations && data.dbOperations.length > 0) {
          const sqlContainer = document.createElement('div');
          sqlContainer.className = 'card-sql-container';

          const sqlTitle = document.createElement('div');
          sqlTitle.className = 'sql-title';
          sqlTitle.innerText = 'SQL 语句';
          sqlContainer.appendChild(sqlTitle);

          data.dbOperations.forEach(op => {
              const sqlBox = document.createElement('div');
              sqlBox.className = 'sql-box';

              if (op.operationType) {
                  const tag = document.createElement('span');
                  tag.className = `sql-type-tag ${op.operationType.toLowerCase()}`;
                  tag.innerText = op.operationType;
                  sqlBox.appendChild(tag);
              }

              const code = document.createElement('code');
              code.className = 'sql-code';
              code.innerText = op.sql || `[${op.operationType || 'SQL'}] ${op.tableName || ''}`;
              sqlBox.appendChild(code);

              sqlContainer.appendChild(sqlBox);
          });

          body.appendChild(sqlContainer);
      }

      // 仅在有备注或有 SQL 时才将主体 append 进来
      const hasBody = data.remarks || (data.isMapper && data.dbOperations && data.dbOperations.length > 0);
      if (hasBody) {
          div.appendChild(body);
      }

      // 添加自定义贴边折叠/展开按钮
      const isLoaded = loadedSet.has(data.id);
      const rawNode = findNode(rawTreeData, data.id);
      
      let showBtn = false;
      let btnText = '+';
      
      if (rawNode && !rawNode.isMapper) {
          if (!isLoaded) {
              showBtn = true;
              btnText = '+';
          } else if (rawNode.children && rawNode.children.length > 0) {
              showBtn = true;
              btnText = rawNode.expand ? '−' : '+';
          }
      }

      if (showBtn) {
          const btn = document.createElement('div');
          btn.className = 'card-expand-btn';
          btn.innerText = btnText;
          
          btn.addEventListener('click', (e) => {
              e.stopPropagation();
              if (!isLoaded) {
                  loadChildren(rawNode);
              } else {
                  rawNode.expand = !rawNode.expand;
                  rerender();
              }
          });
          
          div.appendChild(btn);
      }

      return div;
  };
  ```

---

### Task 4: 手动视觉集成测试与验证

**文件：**
- 无 (本地手动集成测试)

- [ ] **步骤 1：本地预览并验证脑图卡片的毛玻璃视觉效果**
  1. 确保本地应用正常运行在端口 `8080` 上。
  2. 打开浏览器访问 `http://localhost:8080/index.html`。
  3. 输入类名 `com.omp.marketing.intf.web.ShortLinkController`，方法名 `detail`，点击加载。
  4. 观察主节点是否以明亮毛玻璃效果展示，左侧是否有翡翠绿至青蓝渐变条。
  5. 检查卡片是否严格按照“第一行：类名，第二行：方法名，第三行：方法简介，第四行：SQL”排版。
  6. 点击 Service/Controller 节点的右边缘 `+` 按钮加载子方法，点击 Mapper 展开 SQL，验证卡片是否全部展示正确，背后的点阵背景与毛玻璃折射层是否协调舒适。
