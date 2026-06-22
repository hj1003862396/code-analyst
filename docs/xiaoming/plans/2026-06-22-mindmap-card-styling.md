# 脑图卡片样式重构 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 将调用链脑图中的节点卡片修改为规整的白底、左侧带圆角彩色指示条、类名/方法名两列两端对齐带灰色胶囊 Badge 的现代样式，并将连接线颜色改为浅蓝紫色。

**架构：**
- 修改 `index.js` 中的 `createCustomNodeDom` 重新编排卡片 HTML 节点，并配置 simple-mind-map themeConfig。
- 修改 `index.css` 完成卡片整体、列表行（Row）、Badge 及展开按钮的 CSS 样式定制。

**技术栈：** HTML5, Vanilla CSS, JS (Vue 3, simple-mind-map)

---

### Task 1：重构 DOM 结构与 MindMap 配置

**文件：**
- 修改：`/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js`

- [ ] **步骤 1：更新 createCustomNodeDom 方法的 DOM 节点构建**
  
  修改 `/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js` 中的 `createCustomNodeDom` 函数。替换原有的 DOM 构建逻辑为符合双列 Badge 排版的全新 DOM 树结构。
  
  ```javascript
  const createCustomNodeDom = (node) => {
      const data = node.nodeData.data;
      if (!data) return null;

      // 外部包装容器，为右侧绝对定位的 + - 按钮预留空间，防止被 SVG foreignObject 剪裁
      const wrapper = document.createElement('div');
      wrapper.className = 'mindmap-card-wrapper';

      // 卡片主容器
      const div = document.createElement('div');
      div.className = 'mindmap-card';
      if (data.isMapper) {
          div.classList.add('is-mapper');
      } else if (rawTreeData && data.id === rawTreeData.id) {
          div.classList.add('is-root');
      } else {
          div.classList.add('is-service');
      }

      // 1. 头部标题区：指示器 + 类型文本
      const headerType = document.createElement('div');
      headerType.className = 'card-header-type';
      
      const indicator = document.createElement('span');
      indicator.className = 'header-indicator';
      headerType.appendChild(indicator);

      const headerText = document.createElement('span');
      headerText.className = 'header-text';
      if (rawTreeData && data.id === rawTreeData.id) {
          headerText.innerText = '🎯 入口方法';
      } else if (data.isMapper) {
          headerText.innerText = '💾 Mapper 接口';
      } else {
          headerText.innerText = '⚡ Service 方法';
      }
      headerType.appendChild(headerText);
      div.appendChild(headerType);

      // 2. 第一列属性：类名
      if (data.className) {
          const row = document.createElement('div');
          row.className = 'card-row';
          
          const label = document.createElement('span');
          label.className = 'row-label';
          label.innerText = '类名';
          row.appendChild(label);

          const dotIdx = data.className.lastIndexOf('.');
          const shortName = dotIdx !== -1 ? data.className.substring(dotIdx + 1) : data.className;
          
          const badge = document.createElement('span');
          badge.className = 'row-badge';
          badge.innerText = shortName;
          badge.setAttribute('title', data.className); // 悬浮显示完整类名
          row.appendChild(badge);
          
          div.appendChild(row);
      }

      // 3. 第二列属性：方法名
      if (data.methodName || data.text) {
          const row = document.createElement('div');
          row.className = 'card-row';
          
          const label = document.createElement('span');
          label.className = 'row-label';
          label.innerText = '方法名';
          row.appendChild(label);

          const badge = document.createElement('span');
          badge.className = 'row-badge';
          badge.innerText = data.methodName || data.text || '';
          row.appendChild(badge);

          div.appendChild(row);
      }

      // 4. 主体内容区（备注 & SQL 语句）
      const body = document.createElement('div');
      body.className = 'card-body';

      // 4.1 方法简介
      if (data.remarks) {
          const remarksContainer = document.createElement('div');
          remarksContainer.className = 'card-remarks-container';
          
          const remarksTitle = document.createElement('div');
          remarksTitle.className = 'remarks-title';
          remarksTitle.innerText = '方法简介';
          remarksContainer.appendChild(remarksTitle);

          const remarksContent = document.createElement('div');
          remarksContent.className = 'remarks-content';
          remarksContent.innerText = data.remarks;
          remarksContainer.appendChild(remarksContent);

          body.appendChild(remarksContainer);
      }

      // 4.2 SQL 语句 (仅 Mapper 且有数据时展示)
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

      wrapper.appendChild(div);

      // 5. 自定义贴边折叠/展开按钮
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
          
          wrapper.appendChild(btn);
      }

      return wrapper;
  };
  ```

- [ ] **步骤 2：更新 MindMap 实例化 themeConfig 连线配置**
  
  修改 `/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js` 中的 `new MindMap(...)` 构造参数：
  - 在 `themeConfig` 中增加 `lineColor: '#818cf8'` 和 `lineWidth: 2` 的配置。
  
  ```javascript
                      mindMapInstance = new MindMap({
                          el: document.getElementById('mindMapContainer'),
                          data: mapData,
                          layout: 'logicalStructure',
                          theme: 'classic',
                          readonly: true,
                          alwaysShowExpandBtn: false, // 禁用默认的展开按钮
                          isUseCustomNodeContent: true,
                          customCreateNodeContent: (node) => {
                              return createCustomNodeDom(node);
                          },
                          themeConfig: {
                              paddingX: 0,
                              paddingY: 0,
                              lineColor: '#818cf8',
                              lineWidth: 2,
                              root: {
                                  fillColor: 'transparent',
                                  borderColor: 'transparent',
                                  borderWidth: 0,
                                  active: {
                                      fillColor: 'transparent',
                                      borderColor: 'transparent'
                                  },
                                  hover: {
                                      fillColor: 'transparent',
                                      borderColor: 'transparent',
                                      borderWidth: 0
                                  }
                              },
                              second: {
                                  fillColor: 'transparent',
                                  borderColor: 'transparent',
                                  borderWidth: 0,
                                  active: {
                                      fillColor: 'transparent',
                                      borderColor: 'transparent'
                                  },
                                  hover: {
                                      fillColor: 'transparent',
                                      borderColor: 'transparent',
                                      borderWidth: 0
                                  }
                              },
                              node: {
                                  fillColor: 'transparent',
                                  borderColor: 'transparent',
                                  borderWidth: 0,
                                  active: {
                                      fillColor: 'transparent',
                                      borderColor: 'transparent'
                                  },
                                  hover: {
                                      fillColor: 'transparent',
                                      borderColor: 'transparent',
                                      borderWidth: 0
                                  }
                              }
                          }
                      });
  ```

- [ ] **步骤 3：验证 index.js 代码无语法错误**
  
  无需特殊的终端命令，手动验证修改正确即可。

---

### Task 2：设计并实施 CSS 卡片样式

**文件：**
- 修改：`/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.css`

- [ ] **步骤 1：添加全局颜色变量**
  
  在 `:root` 样式选择器中加入：
  ```css
      --accent-root: #10b981;
      --accent-mapper: #f97316;
      --accent-service: #3b82f6;
  ```

- [ ] **步骤 2：重写卡片 CSS 样式规则**
  
  在 `/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.css` 的 `/* MindMap Custom Node Cards */` 区域，替换原有的相关 CSS 样式（约第337-466行左右）。
  
  ```css
  /* ==========================================
     MindMap Custom Node Cards (New Elegant Table Style)
     ========================================== */
  .mindmap-card-wrapper {
      position: relative;
      padding-right: 12px; /* 为贴边折叠按钮预留宽度，防止被 SVG foreignObject 截断 */
      display: inline-block;
  }

  .mindmap-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 14px 16px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
      min-width: 220px;
      max-width: 360px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      font-family: var(--font-sans);
      position: relative; /* 支撑加减号贴边定位 */
      border-left: 4px solid var(--accent-service);
  }

  /* 各种类型卡片的左侧指示条颜色 */
  .mindmap-card.is-mapper {
      border-left-color: var(--accent-mapper);
  }

  .mindmap-card.is-root {
      border-left-color: var(--accent-root);
  }

  /* 头部类型文本 */
  .card-header-type {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 2px;
  }

  .header-text {
      font-size: 13px;
      font-weight: 700;
      color: var(--text-main);
  }

  /* 行排版 */
  .card-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
  }

  .row-label {
      font-size: 12px;
      font-weight: 500;
      color: #64748b;
      white-space: nowrap;
  }

  /* 灰色胶囊 Badge */
  .row-badge {
      font-size: 12px;
      font-weight: 500;
      padding: 3px 8px;
      border-radius: 6px;
      background: #f1f5f9;
      color: #334155;
      font-family: var(--font-mono);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 180px;
  }

  /* 卡片内部自定义展开/折叠按钮 */
  .card-expand-btn {
      position: absolute;
      right: 0; /* 贴在 wrapper 容器的最右侧 */
      top: 50%;
      transform: translateY(-50%) translateX(50%); /* 让按钮半挂在卡片外边缘 */
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 800;
      color: var(--text-muted);
      cursor: pointer;
      z-index: 100;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      transition: all 0.2s ease-in-out;
  }

  .card-expand-btn:hover {
      color: var(--primary);
      border-color: var(--primary);
      transform: translateY(-50%) translateX(50%) scale(1.15);
  }

  /* 隐藏 simple-mind-map 自带的外部展开收起按钮 */
  .smm-expand-btn {
      display: none !important;
  }

  /* 分隔线与额外内容区 */
  .card-body {
      display: flex;
      flex-direction: column;
      gap: 8px;
      border-top: 1px dashed #e2e8f0;
      padding-top: 8px;
      margin-top: 4px;
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
      text-transform: uppercase;
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

- [ ] **步骤 3：验证 CSS 无语法与排版层级冲突**
  
  确认 CSS 修改后的语法和规则匹配正确。

---

### Task 3：折叠按钮定位与隐藏蓝色边框微调

**文件：**
- 修改：`/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js`
- 修改：`/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.css`

- [ ] **步骤 1：在 `index.js` 初始化中添加 `hoverRectColor: 'transparent'`**

修改 `/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js` 中 `new MindMap(...)` 的实例化参数：

```javascript
                    mindMapInstance = new MindMap({
                        el: document.getElementById('mindMapContainer'),
                        data: mapData,
                        layout: 'logicalStructure',
                        theme: 'classic',
                        readonly: true,
                        alwaysShowExpandBtn: false, // 禁用默认的展开按钮
                        hoverRectColor: 'transparent', // 隐藏悬浮和激活选中时的外围蓝色矩形框
                        isUseCustomNodeContent: true,
```

- [ ] **步骤 2：在 `index.css` 中调整 `.card-expand-btn` 的定位参数**

将 `/Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.css` 中的：

```css
  /* 卡片内部自定义展开/折叠按钮 */
  .card-expand-btn {
      position: absolute;
      right: 0; /* 贴在 wrapper 容器的最右侧 */
      top: 50%;
```

修改为：

```css
  /* 卡片内部自定义展开/折叠按钮 */
  .card-expand-btn {
      position: absolute;
      right: 12px; /* 贴在卡片右侧边界，与 wrapper 的 padding-right 一致 */
      top: 50%;
```

- [ ] **步骤 3：验证修改**

在浏览器中检查卡片：
1. 双击或选中卡片时，周围不再出现淡蓝色的外层边框。
2. 鼠标悬停在卡片上时，同样不会显示淡蓝色边框。
3. 卡片右侧的 `+` 或 `-` 展开按钮完整显示，没有被截断或剪裁半个的情况。
