# 关系图（DAG）可视化重构 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 将前端可视化组件重构为 AntV G6 关系图（DAG），解决同名方法在树中 ID 冲突、多处调用无法去重合并、以及展开状态联动的问题，实现多个前置方法同时连接到同一个公共方法的网络关系图。

**架构：** 前端页面移出 `simple-mind-map`，引入 `AntV G6 (v4.8.24)`，在 `svg` 渲染模式下使用自定义 `dom` 形状，并采用内置 `dagre` 层次布局引擎。每个方法由全局唯一的 ID（`className#methodName`）标识，连线指示方法的调用关系。

**技术栈：** HTML5, Vue 3, CSS3, AntV G6 (v4.8.24), Spring Boot (Maven)

---

### Task 1: 引入 AntV G6 库

**文件：**
- 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：移除旧有的 mind-map 库引用并引入 G6 依赖**
  
  在 [index.html](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.html) 中：
  1. 移除旧的 `simpleMindMap.css` 引入（大约第 17 行）：
     ```diff
     - <link href="./lib/simple-mind-map/simpleMindMap.css" rel="stylesheet">
     ```
  2. 引入 AntV G6 CDN script 标签（置于 marked/fonts 引入下方即可，大约第 18 行）：
     ```html
     <script src="https://unpkg.com/@antv/g6@4.8.24/dist/g6.min.js"></script>
     ```

- [ ] **步骤 2：验证 G6 脚本是否正确加载**
  
  运行应用，在浏览器开发者工具控制台中输入：
  ```javascript
  console.log(window.G6);
  ```
  预期输出：打印出非空的 G6 对象，包含 `Graph`、`registerNode` 等属性。

---

### Task 2: 注册自定义 HTML 节点类型

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：移除 index.js 顶部的 ES 模块导入**
  
  由于 `simple-mind-map` 已经移除，我们需要移除 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 顶部的这一行导入：
  ```diff
  - import MindMap from './lib/simple-mind-map/simpleMindMap.esm.min.js';
  ```

- [ ] **步骤 2：添加 `createNodeHtml` 模板字符串生成函数**
  
  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 的 setup 函数外围（文件最上方），编写用于渲染节点 HTML 的工具函数：
  ```javascript
  const createNodeHtml = (cfg) => {
      const dotIdx = cfg.className ? cfg.className.lastIndexOf('.') : -1;
      const shortName = dotIdx !== -1 ? cfg.className.substring(dotIdx + 1) : cfg.className;
      
      let headerText = '⚡ Service 方法';
      if (cfg.isRoot) {
          headerText = '🎯 入口方法';
      } else if (cfg.isMapper) {
          headerText = '💾 Mapper 接口';
      }

      let cardClass = 'mindmap-card';
      if (cfg.isMapper) {
          cardClass += ' is-mapper';
      } else if (cfg.isRoot) {
          cardClass += ' is-root';
      } else {
          cardClass += ' is-service';
      }

      let remarksHtml = '';
      if (cfg.remarks) {
          remarksHtml = `
              <div class="card-remarks-container">
                  <div class="remarks-title">方法简介</div>
                  <div class="remarks-content">${cfg.remarks}</div>
              </div>
          `;
      }

      let sqlHtml = '';
      if (cfg.isMapper && cfg.dbOperations && cfg.dbOperations.length > 0) {
          let opHtmls = '';
          cfg.dbOperations.forEach(op => {
              const opTypeClass = op.operationType ? op.operationType.toLowerCase() : 'sql';
              opHtmls += `
                  <div class="sql-box">
                      <span class="sql-type-tag ${opTypeClass}">${op.operationType || 'SQL'}</span>
                      <code class="sql-code">${op.sql || '[' + (op.operationType || 'SQL') + '] ' + (op.tableName || '')}</code>
                  </div>
              `;
          });
          sqlHtml = `
              <div class="card-sql-container">
                  <div class="sql-title">SQL 语句</div>
                  ${opHtmls}
              </div>
          `;
      }

      const hasBody = cfg.remarks || (cfg.isMapper && cfg.dbOperations && cfg.dbOperations.length > 0);
      const bodyHtml = hasBody ? `<div class="card-body">${remarksHtml}${sqlHtml}</div>` : '';

      let btnHtml = '';
      if (!cfg.isMapper) {
          const btnText = cfg.collapsed ? '+' : '−';
          btnHtml = `<div class="card-expand-btn" onclick="window.handleNodeExpandClick('${cfg.id}', event)">${btnText}</div>`;
      }

      return `
          <div class="mindmap-card-wrapper">
              <div class="${cardClass}">
                  <div class="card-header-type">
                      <span class="header-indicator"></span>
                      <span class="header-text">${headerText}</span>
                  </div>
                  <div class="card-row">
                      <span class="row-label">类名</span>
                      <span class="row-badge" title="${cfg.className || ''}">${shortName || ''}</span>
                  </div>
                  <div class="card-row">
                      <span class="row-label">方法名</span>
                      <span class="row-badge">${cfg.methodName || ''}</span>
                  </div>
                  ${bodyHtml}
              </div>
              ${btnHtml}
          </div>
      `;
  };
  ```

- [ ] **步骤 3：使用 G6 注册名为 `'custom-node'` 的 DOM 节点类型**
  
  在 `createNodeHtml` 下方，编写注册代码：
  ```javascript
  G6.registerNode('custom-node', {
      draw(cfg, group) {
          // 根据是否有 body 部分动态调整卡片的高度
          const hasBody = cfg.remarks || (cfg.isMapper && cfg.dbOperations && cfg.dbOperations.length > 0);
          const height = hasBody ? 240 : 120;
          cfg.size = [360, height]; // 强制写回尺寸以供 G6 布局引擎参考
          
          return group.addShape('dom', {
              attrs: {
                  width: 360,
                  height: height,
                  html: createNodeHtml(cfg)
              },
              name: 'dom-node-keyshape',
              draggable: true
          });
      }
  });
  ```

- [ ] **步骤 4：挂载全局折叠/展开回调函数**
  
  我们需要为 HTML 字符串中生成的 `window.handleNodeExpandClick` 提供一个全局实现：
  ```javascript
  window.handleNodeExpandClick = (nodeId, event) => {
      event.stopPropagation();
      if (window.vueAppInstance && window.vueAppInstance.toggleNode) {
          window.vueAppInstance.toggleNode(nodeId);
      }
  };
  ```

---

### Task 3: 初始化并配置 G6 关系图

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：在 Setup 中声明 G6 数据状态与初始化配置**
  
  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 的 setup() 函数中进行重构：
  1. 移除旧的 `mindMapInstance` 与 `loadedSet`；
  2. 声明 Graph 实例引用、`nodes` 数组、`edges` 数组：
     ```javascript
     let graphInstance = null;
     const nodes = [];
     const edges = [];
     ```
  3. 定义 `updateGraph` 更新函数：
     ```javascript
     const updateGraph = () => {
         if (graphInstance) {
             graphInstance.read({ nodes, edges });
         }
     };
     ```
  4. 定义 `initGraph` 初始化配置：
     ```javascript
     const initGraph = () => {
         if (graphInstance) return;
         
         graphInstance = new G6.Graph({
             container: 'mindMapContainer',
             width: window.innerWidth,
             height: window.innerHeight,
             renderer: 'svg', // 必须使用 SVG 渲染 DOM 节点
             layout: {
                 type: 'dagre',
                 rankdir: 'LR',      // 自左向右的分层布局
                 nodesep: 40,        // 节点间距
                 ranksep: 80,        // 层级间距
                 controlPoints: true
             },
             defaultNode: {
                 type: 'custom-node'
             },
             defaultEdge: {
                 type: 'cubic-horizontal',
                 style: {
                     stroke: '#818cf8',
                     lineWidth: 2,
                     endArrow: {
                         path: G6.Arrow.triangle(8, 10, 0),
                         fill: '#818cf8'
                     }
                 }
             },
             modes: {
                 default: ['drag-canvas', 'zoom-canvas']
             }
         });
         
         // 绑定窗口变化自适应大小
         window.addEventListener('resize', () => {
             if (graphInstance) {
                 graphInstance.changeSize(window.innerWidth, window.innerHeight);
             }
         });
     };
     ```

- [ ] **步骤 2：重写 `initTree` 入口分析逻辑**
  
  重构 setup() 中的 `initTree` 方法。它将作为整个关系图的启动源：
  ```javascript
  const initTree = async () => {
      try {
          const res = await fetch('/api/tree/initialize', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(entry.value)
          });
          if (!res.ok) {
              ElementPlus.ElMessage.error('初始化失败，请检查服务');
              return;
          }
          const rootData = await res.json();

          // 清空原有的图数据
          nodes.length = 0;
          edges.length = 0;

          // 推入根节点
          nodes.push({
              id: rootData.id,
              className: rootData.className,
              methodName: rootData.methodName,
              isMapper: rootData.isMapper,
              dbOperations: rootData.dbOperations,
              remarks: rootData.remarks,
              isRoot: true,
              collapsed: true // 默认收起
          });

          // 初始化 G6 画布并渲染
          initGraph();
          updateGraph();

          leftCardCollapsed.value = true;
          ElementPlus.ElMessage.success('调用链加载成功，点击 + 按钮展开节点');
      } catch (e) {
          console.error('[Init] error:', e);
          ElementPlus.ElMessage.error('无法初始化调用链：' + e.message);
      }
  };
  ```

---

### Task 4: 实现展开、折叠与 DAG 更新流程

**文件：**
- 修改：`src/main/resources/static/index.js`

- [ ] **步骤 1：实现 `toggleNode` 折叠展开切换函数**
  
  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) setup 内编写折叠展开切换逻辑：
  ```javascript
  const toggleNode = async (nodeId) => {
      const node = nodes.find(n => n.id === nodeId);
      if (!node) return;

      if (!node.collapsed) {
          // 折叠逻辑：标记为 true，并清理其子节点
          node.collapsed = true;
          collapseNodeChildren(nodeId);
          updateGraph();
      } else {
          // 展开逻辑：标记为 false，加载子方法并连线
          node.collapsed = false;
          await expandNodeChildren(node);
          updateGraph();
      }
  };
  ```

- [ ] **步骤 2：实现 `expandNodeChildren` 异步子节点加载**
  
  ```javascript
  const expandNodeChildren = async (parentNode) => {
      try {
          const res = await fetch('/api/tree/expand', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                  className: parentNode.className,
                  methodName: parentNode.methodName
              })
          });
          if (!res.ok) {
              ElementPlus.ElMessage.error(`加载失败 HTTP ${res.status}`);
              return;
          }
          const apiChildren = await res.json();
          if (!apiChildren || apiChildren.length === 0) return;

          apiChildren.forEach(child => {
              // 1. 如果子节点不存在，推入 nodes
              let existingNode = nodes.find(n => n.id === child.id);
              if (!existingNode) {
                  nodes.push({
                      id: child.id,
                      className: child.className,
                      methodName: child.methodName,
                      isMapper: child.isMapper,
                      dbOperations: child.dbOperations,
                      remarks: child.remarks,
                      collapsed: true // 子节点默认折叠
                  });
              }
              
              // 2. 如果当前父节点到子节点的边不存在，则添加它
              const edgeExists = edges.some(e => e.source === parentNode.id && e.target === child.id);
              if (!edgeExists) {
                  edges.push({
                      source: parentNode.id,
                      target: child.id
                  });
              }
          });
      } catch (e) {
          console.error('[Expand] error:', e);
          ElementPlus.ElMessage.error('加载子节点失败：' + e.message);
      }
  };
  ```

- [ ] **步骤 3：实现 `collapseNodeChildren` 递归折叠清理算法**
  
  当折叠某个父节点时，若其子节点没有其他未折叠的父节点引用，需要递归将它们从数据中清理掉，防止留在画布上成为孤立的野节点：
  ```javascript
  const collapseNodeChildren = (parentId) => {
      // 找出当前节点引出的所有边
      const outgoingEdges = edges.filter(e => e.source === parentId);
      
      outgoingEdges.forEach(edge => {
          const childId = edge.target;
          
          // 检查该子节点是否还有其他父节点连接
          const otherParents = edges.filter(e => e.target === childId && e.source !== parentId);
          
          // 若没有其他前置方法指向该子节点，则可以安全清理它
          if (otherParents.length === 0) {
              collapseNodeChildren(childId); // 递归折叠孙子辈
              
              // 从 nodes 中移除
              const nodeIdx = nodes.findIndex(n => n.id === childId);
              if (nodeIdx !== -1) {
                  nodes.splice(nodeIdx, 1);
              }
          }
      });

      // 从 edges 列表中清除所有由当前 parentId 引出的边
      for (let i = edges.length - 1; i >= 0; i--) {
          if (edges[i].source === parentId) {
              edges.splice(i, 1);
          }
      }
  };
  ```

- [ ] **步骤 4：暴露 Vue 实例函数供全局 handle 调用**
  
  为了让 `window.handleNodeExpandClick` 全局能正常通信，我们在 setup 尾部将 `toggleNode` 赋值给 window 全局属性，并在返回列表中增加：
  ```javascript
  const setupInstance = {
      entry,
      initTree,
      leftCardCollapsed,
      toggleNode,
      // 兼容原有的按钮逻辑
      zoomIn: () => { if (graphInstance) graphInstance.zoom(1.1); },
      zoomOut: () => { if (graphInstance) graphInstance.zoom(0.9); },
      zoomReset: () => { if (graphInstance) { graphInstance.zoomTo(1.0); graphInstance.fitView(); } },
      toggleDragMode: () => { ElementPlus.ElMessage.info('拖拽模式已默认开启。'); },
      undo: () => {},
      redo: () => {},
      shareLink: () => {
          if (navigator.clipboard) {
              navigator.clipboard.writeText(window.location.href);
              ElementPlus.ElMessage.success('已复制页面链接，可直接分享！');
          } else {
              ElementPlus.ElMessage.info('链接：' + window.location.href);
          }
      },
      exitApp: () => {
          ElementPlus.ElMessageBox.confirm('是否重置分析画布？', '提示', {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning'
          }).then(() => {
              nodes.length = 0;
              edges.length = 0;
              if (graphInstance) {
                  graphInstance.clear();
              }
              leftCardCollapsed.value = false;
              ElementPlus.ElMessage.success('画布已重置');
          });
      },
      showHelp: () => {
          ElementPlus.ElMessageBox.alert(
              '1. 在左下角 🎯 输入类名和方法名，点击「加载调用链」<br/>' +
              '2. 点击节点的 <b>+</b> 按钮，动态加载子方法调用，关系线会自动收敛到共享节点上<br/>' +
              '3. 再次点击节点的 <b>−</b> 按钮可收起折叠节点',
              '使用指南',
              { dangerouslyUseHTMLString: true }
          );
      }
  };

  // 绑定到 window 暴露给 onclick 挂载
  window.vueAppInstance = setupInstance;
  return setupInstance;
  ```

- [ ] **步骤 5：移除原有的 window 辅助交互逻辑（如 `handleNodeClick` 等）**
  
  删除原 `index.js` 中所有关于 `loadedSet`，`findNode`，`transformNode`，`createCustomNodeDom`，以及 `handleNodeClick` 的冗余代码，使其高度简化聚焦。

---

### Task 5: 优化样式布局

**文件：**
- 修改：`src/main/resources/static/index.css`

- [ ] **步骤 1：移除 simple-mind-map 相关样式并在 CSS 中补强 G6 节点定位**
  
  在 [index.css](file:///Users/hanjie/IdeaProjects/code-analysis/src/index.css) 尾部，移除 `smm-expand-btn` 相关设置，并为 G6 容器及 DOM 节点微调层级与展示样式：
  ```css
  /* 为 G6 foreignObject 适配滚动和背景隔离 */
  .mindmap-card-wrapper {
      position: relative;
      display: inline-block;
      padding-right: 18px; /* 扩展右侧内边距，完全预留折叠按钮的溢出外沿 */
  }

  .card-expand-btn {
      position: absolute;
      right: 18px; /* 居中挂在卡片外框右边沿 */
      top: 50%;
      transform: translateY(-50%) translateX(50%);
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      font-weight: bold;
      color: #64748b;
      cursor: pointer;
      z-index: 999;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
      transition: all 0.2s ease-in-out;
  }

  .card-expand-btn:hover {
      color: #3b66e2;
      border-color: #3b66e2;
      transform: translateY(-50%) translateX(50%) scale(1.1);
  }

  .remarks-content {
      word-break: break-all;
      overflow-wrap: break-word;
  }
  ```

---

## 4. 验证计划

### 自动验证测试
* 运行以下单元测试，确保后端 Java 源码解析器工作完全正常：
  ```bash
  mvn test
  ```
  预期结果：测试套件应 100% 通过（"Tests run: 12, Failures: 0, Errors: 0, Skipped: 0"）。

### 手动功能联调测试
1. 在终端中启动 Spring Boot 应用：
   ```bash
   mvn spring-boot:run
   ```
2. 访问 `http://localhost:8080`，确认默认类名 `com.omp.finance.intf.app.FinanceController` 和方法名 `saveInvoiceWithoutTitle` 正确载入。
3. 点击 “加载调用链” 按钮：
   - 确认根节点展示为 `FinanceController.saveInvoiceWithoutTitle`，其右侧带有一个 `+` 按钮。
4. 依次点击 `+` 按钮展开后续节点。
5. 验证两个不同的上游 Service 方法（如 `chargingInvoice` 与 `chargingMoneyInvoice`）在都调用 `saveInvoice` 时，画布上只显示一个共享的 `saveInvoice` 方法节点，并且有两个指向它的边。
6. 点击此共享节点右侧的 `+`，验证它能够继续向右展开加载下级的 SQL 或者 Mapper 接口。
