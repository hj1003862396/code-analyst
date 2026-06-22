# 脑图卡片与 SQL 展现 实施计划 (第二版)

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在解析中过滤 getter/setter/Stream 等常用方法，利用自定义的卡片右侧边缘贴边按钮控制折叠展开（即在卡片本体后，不拉长线或显示 `...` 框），并且禁用点击节点本体展开的行为。卡片仅展示方法名、备注与 SQL 语句。

**架构：**
- 修改前端 `index.js`，重写 `transformNode` 以通过空 `children` 数组彻底折叠子节点；
- 编写 `createCustomNodeDom` 渲染精简版 Dify 卡片，并绝对定位 `.card-expand-btn` 到卡片右边缘作为折叠按钮，拦截冒泡并控制 `expand` / lazy-load；
- 修改 `index.css` 注入卡片及贴边按钮样式，利用 `.smm-expand-btn { display: none !important; }` 屏蔽内置展开按钮。

**技术栈：** JavaScript, HTML, CSS, Vue 3, simple-mind-map

---

### Task 1: 重构 transformNode 实现本体折叠

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：重构 transformNode 逻辑，使其根据展开状态返回子节点或空数组**
  编辑 `index.js` 中的 `transformNode`，如果节点未加载或未展开，则直接返回 `children = []`。不返回任何 placeholder/dummy 节点（如之前的 `···` 占位节点）。
  代码块：
  ```javascript
  const transformNode = (node) => {
      if (!node) return null;

      const isLoaded = loadedSet.has(node.id);
      const isExpanded = !!node.expand;
      const hasSql = node.isMapper && node.dbOperations && node.dbOperations.length > 0;

      let children = [];

      // 仅在已加载且处于展开状态时，才递归返回子节点
      if (isLoaded && isExpanded && node.children && node.children.length > 0) {
          children = node.children.map(c => transformNode(c));
      }

      return {
          data: {
              id: node.id,
              className: node.className,
              methodName: node.methodName,
              isMapper: node.isMapper,
              dbOperations: node.dbOperations,
              remarks: node.remarks,
              label: node.label
          },
          children
      };
  };
  ```

---

### Task 2: 实现精简卡片 DOM 与自定义贴边按钮

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：实现 createCustomNodeDom 方法**
  重写 `createCustomNodeDom` 方法。卡片内不再渲染 `className`（类名）和 `sourceCode`（源码预览），仅保留方法名、备注说明和 SQL 语句。同时在卡片边缘追加 `.card-expand-btn` 折叠按钮，绑定展开/折叠及 lazy-load 响应事件，并调用 `e.stopPropagation()` 阻止冒泡。
  代码块：
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

      // 头部
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

      // 主体
      const body = document.createElement('div');
      body.className = 'card-body';

      // 接口备注 (说明)
      if (data.remarks) {
          const remarksContainer = document.createElement('div');
          remarksContainer.className = 'card-remarks-container';
          
          const remarksTitle = document.createElement('div');
          remarksTitle.className = 'remarks-title';
          remarksTitle.innerText = '说明';
          remarksContainer.appendChild(remarksTitle);

          const remarksContent = document.createElement('div');
          remarksContent.className = 'remarks-content';
          remarksContent.innerText = data.remarks;
          remarksContainer.appendChild(remarksContent);

          body.appendChild(remarksContainer);
      }

      // SQL 语句展示 (仅 Mapper)
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

      // 仅在有备注或有 SQL 时才渲染 body
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

### Task 3: 优化 MindMap 初始化与事件响应

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：更新 MindMap 构造函数以引入透明主题，并移除 node_click 事件**
  修改 `new MindMap({...})` 配置：
  - 传入 `themeConfig` 以清除自带容器背景色、边框、并使内边距归零；
  - 传入 `isUseCustomNodeContent` 开启自定义渲染；
  - 移除原 `node_click` 事件绑定，仅保留 `scale_change` 监听。
  代码块：
  ```javascript
  mindMapInstance = new MindMap({
      el: document.getElementById('mindMapContainer'),
      data: mapData,
      layout: 'logicalStructure',
      theme: 'classic',
      readonly: true,
      alwaysShowExpandBtn: false, // 不使用自带按钮
      isUseCustomNodeContent: true,
      customCreateNodeContent: (node) => {
          return createCustomNodeDom(node);
      },
      themeConfig: {
          paddingX: 0,
          paddingY: 0,
          root: {
              fillColor: 'transparent',
              borderColor: 'transparent',
              borderWidth: 0,
              active: {
                  fillColor: 'transparent',
                  borderColor: 'transparent'
              }
          },
          second: {
              fillColor: 'transparent',
              borderColor: 'transparent',
              borderWidth: 0,
              active: {
                  fillColor: 'transparent',
                  borderColor: 'transparent'
              }
          },
          node: {
              fillColor: 'transparent',
              borderColor: 'transparent',
              borderWidth: 0,
              active: {
                  fillColor: 'transparent',
                  borderColor: 'transparent'
              }
          }
      }
  });

  mindMapInstance.on('scale_change', (scale) => {
      zoomPercent.value = Math.round(scale * 100);
  });
  ```

---

### Task 4: 更新自定义卡片与贴边按钮 CSS 样式

**文件：**
- 修改：`src/main/resources/static/index.css`

- [ ] **步骤 1：替换和新增 CSS 类**
  更新 `index.css` 尾部样式：
  - 添加 `.card-expand-btn` 绝对定位贴边效果；
  - 隐藏 `.smm-expand-btn`；
  - 移除 `.card-class-name` 和 `.card-code-container` 样式。
  代码块：
  ```css
  /* ==========================================
     MindMap Custom Node Cards (Dify Style)
     ========================================== */
  .mindmap-card {
      background: #ffffff;
      border: 1.5px solid #cbd5e1;
      border-radius: 10px;
      padding: 12px 16px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
      min-width: 200px;
      max-width: 360px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      transition: all 0.2s ease-in-out;
      font-family: var(--font-sans);
      border-left: 5px solid #6366f1; /* Service 节点星空靛蓝 */
      position: relative; /* 支撑加减号贴边定位 */
  }

  .mindmap-card:hover {
      box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -2px rgba(0, 0, 0, 0.04);
      transform: translateY(-2px);
      border-color: #6366f1;
  }

  /* Mapper 节点珊瑚暖橙 */
  .mindmap-card.is-mapper {
      border-left: 5px solid #f97316;
      background: #fafaf9;
  }
  .mindmap-card.is-mapper:hover {
      border-color: #f97316;
  }

  /* 根节点翡翠绿 */
  .mindmap-card.is-root {
      border-left: 5px solid #10b981;
      background: #f8fafc;
  }
  .mindmap-card.is-root:hover {
      border-color: #10b981;
  }

  /* 卡片内部自定义展开/折叠按钮 */
  .card-expand-btn {
      position: absolute;
      right: -10px; /* 居中贴在右边线上 */
      top: 50%;
      transform: translateY(-50%);
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #ffffff;
      border: 1.5px solid #cbd5e1;
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

  /* 卡片头部 */
  .card-header {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
  }

  .card-icon {
      font-size: 15px;
  }

  .card-title {
      font-size: 14px;
      font-weight: 700;
      color: var(--text-main);
      word-break: break-all;
  }

  /* 卡片主体 */
  .card-body {
      display: flex;
      flex-direction: column;
      gap: 6px;
      border-top: 1px solid #e2e8f0;
      padding-top: 6px;
  }

  /* SQL 展现容器 */
  .card-sql-container {
      background: #1e293b;
      border-radius: 6px;
      padding: 8px;
      margin-top: 4px;
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

  /* 备注说明 */
  .card-remarks-container {
      background: #fffbeb;
      border: 1px solid #fef3c7;
      border-radius: 6px;
      padding: 6px;
  }

  .remarks-title {
      font-size: 9px;
      color: #b45309;
      font-weight: bold;
  }

  .remarks-content {
      font-size: 11px;
      color: #78350f;
      line-height: 1.4;
      margin-top: 2px;
      white-space: pre-wrap;
      word-break: break-all;
  }
  ```
