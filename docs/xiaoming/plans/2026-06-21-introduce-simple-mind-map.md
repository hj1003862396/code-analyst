# 引入 Simple-Mind-Map 并进行静态资源拆分 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在前端集成 Simple-Mind-Map 脑图组件替换 D3.js 树图，将前端 HTML、CSS、JS 代码完全拆分，并在后端过滤掉 DTO/Entity 的属性和通用方法。

**架构：** 前端采用原生 ESM 加载 CDN 的 `simple-mind-map`；后端利用 `JavaParser` 在 AST 解析时依据类型后缀（如 DTO/Entity）阻断无关节点的递归向下分析。

**技术栈：** Java, Spring Boot, JavaParser, Vue 3, Element Plus, Simple-Mind-Map (ESM CDN)

---

### Task 1：后端 DTO/Entity 方法过滤逻辑与单元测试

**文件：**
- 修改：`src/main/java/com/codedb/analyst/parser/JavaSourceParser.java`
- 创建：`src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java`

- [ ] **步骤 1：创建单元测试验证过滤逻辑**
  创建测试文件 `src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java`：
  ```java
  package com.codedb.analyst.parser;

  import org.junit.jupiter.api.Test;
  import java.io.FileWriter;
  import java.io.File;
  import java.util.List;
  import static org.junit.jupiter.api.Assertions.*;

  public class JavaSourceParserTest {
      @Test
      public void testIgnoredCallFiltering() throws Exception {
          JavaSourceParser parser = new JavaSourceParser();
          File tempFile = File.createTempFile("MockService", ".java");
          tempFile.deleteOnExit();
          
          try (FileWriter writer = new FileWriter(tempFile)) {
              writer.write(
                  "package com.example;\n" +
                  "public class MockService {\n" +
                  "    private UserDTO userDto;\n" +
                  "    private OrderEntity orderEntity;\n" +
                  "    private BusinessService businessService;\n" +
                  "    public void doSomething() {\n" +
                  "        userDto.getId();\n" +
                  "        orderEntity.save();\n" +
                  "        businessService.process();\n" +
                  "        String.format(\"abc\");\n" +
                  "    }\n" +
                  "}\n"
              );
          }
          
          List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "doSomething");
          assertEquals(1, calls.size());
          assertEquals("businessService", calls.get(0).getObjectName());
          assertEquals("process", calls.get(0).getMethodName());
      }
  }
  ```

- [ ] **步骤 2：运行单元测试，验证未修改前测试失败**
  运行：`mvn test -Dtest=JavaSourceParserTest`
  预期：编译错误或测试失败（因为没有过滤，calls.size() > 1）。

- [ ] **步骤 3：在 JavaSourceParser.java 中编写过滤代码**
  修改 `src/main/java/com/codedb/analyst/parser/JavaSourceParser.java` 的 `isIgnoredCall` 方法：
  ```java
      private boolean isIgnoredCall(String objectName, String objectType, String methodName) {
          if (objectName == null) return false;
          String lowerName = objectName.toLowerCase();
          
          // 过滤日志和标准流
          if (lowerName.equals("log") || lowerName.equals("logger") || lowerName.equals("system.out") || lowerName.equals("system.err") || lowerName.equals("out") || lowerName.equals("err")) {
              return true;
          }
          
          // 过滤标准 JDK 类型调用
          if (objectType != null) {
              String cleanType = objectType.replaceAll("<.*>", "");
              if (cleanType.equals("String") || cleanType.equals("List") || cleanType.equals("Map") || cleanType.equals("Set") 
                      || cleanType.equals("ArrayList") || cleanType.equals("HashMap") || cleanType.equals("HashSet") 
                      || cleanType.equals("Collections") || cleanType.equals("Objects") || cleanType.equals("Arrays") 
                      || cleanType.equals("Optional") || cleanType.equals("Stream") || cleanType.equals("Collectors") 
                      || cleanType.equals("Logger") || cleanType.equals("LoggerFactory") || cleanType.equals("System")
                      || cleanType.equals("BigDecimal") || cleanType.equals("Integer") || cleanType.equals("Long") 
                      || cleanType.equals("Boolean") || cleanType.equals("Double") || cleanType.equals("Character")) {
                  return true;
              }
              
              // 过滤 DTO, Entity, VO, PO, Req, Resp 等类型
              String lowerType = cleanType.toLowerCase();
              if (lowerType.endsWith("dto") || lowerType.endsWith("entity") || lowerType.endsWith("vo") || lowerType.endsWith("po") 
                      || lowerType.endsWith("req") || lowerType.endsWith("resp") || lowerType.endsWith("request") || lowerType.endsWith("response")
                      || lowerType.endsWith("param") || lowerType.endsWith("params") || lowerType.endsWith("query")) {
                  return true;
              }
          }
          
          return false;
      }
  ```

- [ ] **步骤 4：再次运行测试，验证过滤通过**
  运行：`mvn test -Dtest=JavaSourceParserTest`
  预期：BUILD SUCCESS

---

### Task 2：前端 CSS 样式拆分与适配

**文件：**
- 创建：`src/main/resources/static/index.css`

- [ ] **步骤 1：创建 index.css 文件**
  创建 `src/main/resources/static/index.css`，将原先 HTML 中的全局 CSS 以及新加的容器布局拷入：
  ```css
  :root {
      --bg-canvas: #ffffff;
      --bg-card: #ffffff;
      --border-color: #e2e8f0;
      --primary: #3b66e2;
      --danger: #ef4444;
      --text-main: #1e293b;
      --text-muted: #64748b;
      --font-sans: 'Outfit', sans-serif;
      --font-mono: 'JetBrains Mono', monospace;
  }

  * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
  }

  body {
      font-family: var(--font-sans);
      background-color: var(--bg-canvas);
      color: var(--text-main);
      height: 100vh;
      width: 100vw;
      overflow: hidden;
  }

  #app {
      position: relative;
      width: 100vw;
      height: 100vh;
      overflow: hidden;
  }

  /* Simple Mind Map 容器 */
  #mindMapContainer {
      position: absolute;
      left: 0;
      top: 0;
      width: 100%;
      height: 100%;
      z-index: 1;
      background-color: var(--bg-canvas);
  }

  /* 悬浮工具栏通用样式 */
  .float-toolbar {
      position: absolute;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
      z-index: 10;
      display: flex;
      align-items: center;
      padding: 6px 12px;
      gap: 8px;
      transition: all 0.3s;
  }

  .float-toolbar:hover {
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
  }

  .toolbar-top-left {
      top: 20px;
      left: 20px;
      gap: 4px;
  }

  .toolbar-top-right {
      top: 20px;
      right: 20px;
      padding: 6px 10px;
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
      border-radius: 24px;
      padding: 6px 14px;
      gap: 10px;
  }

  .toolbar-btn {
      background: transparent;
      border: none;
      outline: none;
      color: var(--text-main);
      font-size: 13px;
      font-weight: 500;
      padding: 6px 10px;
      border-radius: 6px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s;
  }

  .toolbar-btn:hover, .toolbar-btn.active {
      background: #f1f5f9;
      color: var(--primary);
  }

  .toolbar-btn-icon {
      background: transparent;
      border: none;
      outline: none;
      color: var(--text-muted);
      width: 28px;
      height: 28px;
      border-radius: 6px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
  }

  .toolbar-btn-icon:hover {
      background: #f1f5f9;
      color: var(--primary);
  }

  .toolbar-btn-icon.active {
      color: var(--primary);
      background: #e0e7ff;
  }

  .share-btn {
      border: 1px solid rgba(59, 102, 226, 0.2) !important;
      color: var(--primary) !important;
      padding: 4px 10px !important;
      border-radius: 16px !important;
      background: rgba(59, 102, 226, 0.02);
  }

  .share-btn:hover {
      background: rgba(59, 102, 226, 0.08) !important;
  }

  .toolbar-divider {
      width: 1px;
      height: 18px;
      background: var(--border-color);
      margin: 0 4px;
  }

  .toolbar-divider-v {
      width: 80%;
      height: 1px;
      background: var(--border-color);
  }

  .toolbar-logo {
      margin-bottom: 4px;
  }

  /* 左侧表单卡片 */
  .left-input-card {
      position: absolute;
      left: 24px;
      bottom: 24px;
      width: 340px;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
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

  .left-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;
      border-bottom: 1px solid var(--border-color);
      padding-bottom: 10px;
  }

  .left-card-title {
      font-size: 15px;
      font-weight: 600;
      color: var(--primary);
      display: flex;
      align-items: center;
      gap: 6px;
  }

  /* 右侧详情抽屉 */
  .right-detail-drawer {
      position: absolute;
      right: 24px;
      top: 80px;
      bottom: 80px;
      width: 440px;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(10px);
      border: 1px solid var(--border-color);
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

  .drawer-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      border-bottom: 1px solid var(--border-color);
      padding-bottom: 12px;
      flex-shrink: 0;
  }

  .drawer-title {
      font-size: 16px;
      font-weight: 600;
      color: var(--primary);
      display: flex;
      align-items: center;
      gap: 6px;
  }

  .drawer-content {
      flex: 1;
      overflow-y: auto;
      padding-right: 4px;
  }

  .trigger-btn {
      position: absolute;
      z-index: 8;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 50%;
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
      cursor: pointer;
      transition: all 0.2s;
  }

  .trigger-btn:hover {
      color: var(--primary);
      box-shadow: 0 6px 14px rgba(0, 0, 0, 0.12);
  }

  .left-trigger {
      left: 24px;
      bottom: 24px;
  }

  .right-trigger {
      right: 24px;
      top: 80px;
  }

  .db-tag {
      background: rgba(239, 68, 68, 0.05);
      border: 1px solid rgba(239, 68, 68, 0.15);
      color: var(--danger);
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 12px;
      font-family: var(--font-mono);
      display: inline-flex;
      align-items: center;
      gap: 6px;
  }

  .el-input__wrapper {
      background-color: #ffffff !important;
      border: 1px solid var(--border-color) !important;
      box-shadow: none !important;
  }
  .el-input__inner {
      color: var(--text-main) !important;
      font-family: var(--font-mono);
  }
  .el-form-item__label {
      color: var(--text-muted) !important;
      font-weight: 500;
  }
  .el-button--success, .el-button--primary {
      background: var(--primary) !important;
      border: none !important;
      color: #ffffff !important;
  }

  ::-webkit-scrollbar {
      width: 6px;
  }
  ::-webkit-scrollbar-track {
      background: transparent;
  }
  ::-webkit-scrollbar-thumb {
      background: rgba(0, 0, 0, 0.05);
      border-radius: 3px;
  }
  ::-webkit-scrollbar-thumb:hover {
      background: rgba(0, 0, 0, 0.1);
  }
  ```

---

### Task 3：前端 JS 逻辑拆分与 Simple-Mind-Map 集成

**文件：**
- 创建：`src/main/resources/static/index.js`

- [ ] **步骤 1：创建 index.js 并填充逻辑**
  使用 ESM 的 `import` 从 jsDelivr 载入 `Simple-Mind-Map`，通过 `window.Vue` 交互：
  ```javascript
  import MindMap from 'https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.min.js';

  const { createApp, ref, onMounted, nextTick } = Vue;

  createApp({
      setup() {
          const entry = ref({
              className: 'com.omp.marketing.intf.web.ShortLinkController',
              methodName: 'detail'
          });

          const selectedNode = ref(null);
          const leftCardCollapsed = ref(false);
          const rightDrawerCollapsed = ref(false);
          const zoomPercent = ref(100);
          const dragMode = ref(false);

          let mindMapInstance = null;
          let rawTreeData = null; 

          // 转换后端的数据结构到 simple-mind-map 规格
          const transformNode = (backendNode) => {
              if (!backendNode) return null;
              const text = backendNode.isMapper ? `💾 ${backendNode.label}` : backendNode.label;
              const simpleNode = {
                  data: {
                      text: text,
                      id: backendNode.id,
                      label: backendNode.label,
                      className: backendNode.className,
                      methodName: backendNode.methodName,
                      isMapper: backendNode.isMapper,
                      dbOperations: backendNode.dbOperations || []
                  },
                  children: []
              };
              if (backendNode.children) {
                  simpleNode.children = backendNode.children.map(c => transformNode(c));
              }
              return simpleNode;
          };

          // 寻找对应 id 的节点
          const findNodeInTree = (node, id) => {
              if (node.id === id) return node;
              if (node.children) {
                  for (const child of node.children) {
                      const found = findNodeInTree(child, id);
                      if (found) return found;
                  }
              }
              return null;
          };

          // 选中及展开子节点
          const selectNode = async (nodeData) => {
              selectedNode.value = nodeData;
              rightDrawerCollapsed.value = false;

              if (!nodeData.children && !nodeData.isMapper) {
                  try {
                      const res = await fetch('/api/tree/expand', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({
                              className: nodeData.className,
                              methodName: nodeData.methodName
                          })
                      });
                      if (res.ok) {
                          const children = await res.json();
                          if (children && children.length) {
                              const rawNode = findNodeInTree(rawTreeData, nodeData.id);
                              if (rawNode) {
                                  rawNode.children = children;
                                  nodeData.children = children; // 同步模板绑定

                                  const mapData = transformNode(rawTreeData);
                                  mindMapInstance.setData(mapData);
                              }
                          }
                      }
                  } catch (e) {
                      ElementPlus.ElMessage.error('展开子节点失败：' + e.message);
                  }
              }
          };

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
                  rawTreeData = await res.json();

                  const mapData = transformNode(rawTreeData);
                  if (mindMapInstance) {
                      mindMapInstance.setData(mapData);
                  } else {
                      mindMapInstance = new MindMap({
                          el: document.getElementById('mindMapContainer'),
                          data: mapData,
                          layout: 'logicalStructure', // 逻辑结构图 (向右)
                          theme: 'classic',
                          readonly: true
                      });

                      mindMapInstance.on('node_active', (nodeInstance) => {
                          if (nodeInstance) {
                              const data = nodeInstance.getData().data;
                              selectNode(data);
                          }
                      });

                      mindMapInstance.on('scale_change', (scale) => {
                          zoomPercent.value = Math.round(scale * 100);
                      });
                  }

                  await selectNode(rawTreeData);
                  leftCardCollapsed.value = true;
              } catch (e) {
                  ElementPlus.ElMessage.error('无法初始化调用链：' + e.message);
              }
          };

          const selectNodeFromList = (child) => {
              // 钻取分析列表点击
              selectNode(child);
              
              if (mindMapInstance && mindMapInstance.renderer && mindMapInstance.renderer.root) {
                  const findAndActive = (nodeInst) => {
                      if (nodeInst.getData().data.id === child.id) {
                          nodeInst.active();
                          mindMapInstance.execCommand('GO_TARGET_NODE', nodeInst);
                          return true;
                      }
                      if (nodeInst.children) {
                          for (const c of nodeInst.children) {
                              if (findAndActive(c)) return true;
                          }
                      }
                      return false;
                  };
                  findAndActive(mindMapInstance.renderer.root);
              }
          };

          const zoomIn = () => {
              if (mindMapInstance) {
                  let scale = mindMapInstance.view.scale + 0.1;
                  if (scale > 3) scale = 3;
                  mindMapInstance.view.setScale(scale);
              }
          };

          const zoomOut = () => {
              if (mindMapInstance) {
                  let scale = mindMapInstance.view.scale - 0.1;
                  if (scale < 0.2) scale = 0.2;
                  mindMapInstance.view.setScale(scale);
              }
          };

          const zoomReset = () => {
              if (mindMapInstance) {
                  mindMapInstance.reset();
              }
          };

          const toggleDragMode = () => {
              dragMode.value = !dragMode.value;
              ElementPlus.ElMessage.info(dragMode.value ? '已开启拖拽' : '已关闭拖拽');
          };

          const undo = () => {
              if (mindMapInstance) mindMapInstance.execCommand('BACK');
          };

          const redo = () => {
              if (mindMapInstance) mindMapInstance.execCommand('FORWARD');
          };

          const shareLink = () => {
              if (navigator.clipboard) {
                  navigator.clipboard.writeText(window.location.href);
                  ElementPlus.ElMessage.success('已复制页面链接，可直接分享！');
              } else {
                  ElementPlus.ElMessage.success('已复制：' + window.location.href);
              }
          };

          const exitApp = () => {
              ElementPlus.ElMessageBox.confirm('是否重置分析画布？', '提示', {
                  confirmButtonText: '确定',
                  cancelButtonText: '取消',
                  type: 'warning'
              }).then(() => {
                  if (mindMapInstance) {
                      mindMapInstance.setData({ data: { text: 'Empty' }, children: [] });
                  }
                  selectedNode.value = null;
                  leftCardCollapsed.value = false;
                  ElementPlus.ElMessage.success('画布已重置');
              });
          };

          const showHelp = () => {
              ElementPlus.ElMessageBox.alert(
                  '1. 点击左下角 🎯 按钮展开或重载方法分析入口<br/>2. 点击树图中的节点，右侧将以抽屉展示对物理表的 CRUD 操作<br/>3. 点击右侧子调用列表中“钻取分析”，画布将自动聚焦到对应子节点并高亮显示',
                  '使用指南',
                  { dangerouslyUseHTMLString: true }
              );
          };

          return {
              entry,
              selectedNode,
              initTree,
              leftCardCollapsed,
              rightDrawerCollapsed,
              zoomPercent,
              dragMode,
              zoomIn,
              zoomOut,
              zoomReset,
              toggleDragMode,
              undo,
              redo,
              shareLink,
              exitApp,
              showHelp,
              selectNodeFromList
          };
      }
  }).use(ElementPlus).mount('#app');
  ```

---

### Task 4：清空 index.html 冗余并引入新资源

**文件：**
- 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：修改并重写 index.html**
  将原有 inline style 和 Vue 代码删除，替换为干净的外部引用，用 `#mindMapContainer` 替换 `#tree-svg`：
  ```html
  <!DOCTYPE html>
  <html lang="zh-CN">
  <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>code-db-analyst // 接口与数据库智能梳理</title>
      <!-- 引入 Vue 3, Element Plus -->
      <script src="https://unpkg.com/vue@3/dist/vue.global.js"></script>
      <link rel="stylesheet" href="https://unpkg.com/element-plus/dist/index.css" />
      <script src="https://unpkg.com/element-plus"></script>
      <!-- Marked 用于富文本渲染 -->
      <script src="https://unpkg.com/marked/marked.min.js"></script>
      <!-- Google 字体 -->
      <link rel="preconnect" href="https://fonts.googleapis.com">
      <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
      <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
      
      <!-- simple-mind-map 脑图样式 -->
      <link href="https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.css" rel="stylesheet">
      
      <!-- 外部样式 -->
      <link rel="stylesheet" href="./index.css">
  </head>
  <body>
  <div id="app">
      <!-- 脑图容器 -->
      <div id="mindMapContainer"></div>

      <!-- 1. 左上工具栏：退出 / 画板 -->
      <div class="float-toolbar toolbar-top-left">
          <button class="toolbar-btn exit-btn" @click="exitApp">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
              退出
          </button>
          <button class="toolbar-btn board-btn active">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#22c55e" stroke-width="2.5"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>
              画板
          </button>
      </div>

      <!-- 2. 右上工具栏 -->
      <div class="float-toolbar toolbar-top-right">
          <button class="toolbar-btn share-btn" @click="shareLink">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#3b66e2" stroke-width="2.5"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></svg>
              分享
          </button>
          <div class="toolbar-divider"></div>
          <button class="toolbar-btn edit-btn">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
              编辑 <span style="font-size: 8px; margin-left: 2px;">▼</span>
          </button>
          <button class="toolbar-btn-icon" title="搜索"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg></button>
          <button class="toolbar-btn-icon" title="评论"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg></button>
          <button class="toolbar-btn-icon" title="更多"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="1.5"></circle><circle cx="19" cy="12" r="1.5"></circle><circle cx="5" cy="12" r="1.5"></circle></svg></button>
      </div>

      <!-- 3. 左侧浮动垂直工具栏 -->
      <div class="float-toolbar toolbar-left-vertical">
          <div class="toolbar-logo">
              <svg viewBox="0 0 32 32" width="24" height="24">
                  <circle cx="12" cy="12" r="6" fill="#ef4444" opacity="0.85"></circle>
                  <circle cx="20" cy="12" r="6" fill="#3b66e2" opacity="0.85"></circle>
                  <circle cx="16" cy="20" r="6" fill="#eab308" opacity="0.85"></circle>
              </svg>
          </div>
          <div class="toolbar-divider-v"></div>
          <button class="toolbar-btn-icon active" title="选择"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="3"></rect></svg></button>
          <button class="toolbar-btn-icon" title="文本"><span style="font-size: 16px; font-weight: bold;">T</span></button>
          <button class="toolbar-btn-icon" title="便签"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg></button>
          <button class="toolbar-btn-icon" title="连线"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 17L4 12L9 7"></path><path d="M20 12H4"></path></svg></button>
          <button class="toolbar-btn-icon" title="框架"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" style="stroke-dasharray: 2,2"></rect></svg></button>
          <button class="toolbar-btn-icon" title="表格"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="9" x2="21" y2="9"></line><line x1="3" y1="15" x2="21" y2="15"></line><line x1="12" y1="3" x2="12" y2="21"></line><rect x="3" y="3" width="18" height="18" rx="2"></rect></svg></button>
          <button class="toolbar-btn-icon" title="画笔"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19l7-7 3 3-7 7-3-3z"></path><path d="M18 13l-1.5-1.5"></path><path d="M2 22l5-5-3-3-5 5v3h3z"></path></svg></button>
          <button class="toolbar-btn-icon" title="列表"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"></line><line x1="8" y1="12" x2="21" y2="12"></line><line x1="8" y1="18" x2="21" y2="18"></line><line x1="3" y1="6" x2="3.01" y2="6"></line><line x1="3" y1="12" x2="3.01" y2="12"></line><line x1="3" y1="18" x2="3.01" y2="18"></line></svg></button>
          <button class="toolbar-btn-icon" title="格栅"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg></button>
          <button class="toolbar-btn-icon" title="沟通"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg></button>
      </div>

      <!-- 4. 右下角工具栏：缩放与操控 -->
      <div class="float-toolbar toolbar-bottom-right">
          <button class="toolbar-btn-icon" title="撤销" @click="undo"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M3 7v6h6"></path><path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13"></path></svg></button>
          <button class="toolbar-btn-icon" title="重做" @click="redo"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 7v6h-6"></path><path d="M3 17a9 9 0 0 1 9-9 9 9 0 0 1 6 2.3l3 2.7"></path></svg></button>
          <div class="toolbar-divider"></div>
          <button class="toolbar-btn-icon" :class="{ active: dragMode }" title="手势拖拽" @click="toggleDragMode"><span style="font-size: 14px;">🖐</span></button>
          <div class="toolbar-divider"></div>
          <button class="toolbar-btn-icon" @click="zoomOut" style="font-weight: bold; font-size: 16px;">−</button>
          <span style="font-size: 13px; font-weight: 600; min-width: 36px; text-align: center; color: var(--text-main);">{{ zoomPercent }}%</span>
          <button class="toolbar-btn-icon" @click="zoomIn" style="font-weight: bold; font-size: 16px;">+</button>
          <div class="toolbar-divider"></div>
          <button class="toolbar-btn-icon" title="帮助" @click="showHelp"><span style="font-size: 13px; font-weight: bold;">?</span></button>
      </div>

      <!-- 5. 左下悬浮卡片：分析入口 -->
      <div class="left-input-card" :class="{ collapsed: leftCardCollapsed }">
          <div class="left-card-header">
              <div class="left-card-title">🎯 分析方法入口</div>
              <button class="toolbar-btn-icon" @click="leftCardCollapsed = true" title="收起">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"></polyline></svg>
              </button>
          </div>
          <el-form label-position="top">
              <el-form-item label="Java 类名">
                  <el-input v-model="entry.className" placeholder="com.example.service.impl.UserServiceImpl"></el-input>
              </el-form-item>
              <el-form-item label="Java 方法名">
                  <el-input v-model="entry.methodName" placeholder="getUserInfo"></el-input>
              </el-form-item>
              <el-button type="success" @click="initTree" style="width: 100%; margin-top: 10px;">加载调用链</el-button>
          </el-form>
      </div>

      <!-- 左侧收起后的悬浮气泡 -->
      <div v-if="leftCardCollapsed" class="trigger-btn left-trigger" @click="leftCardCollapsed = false" title="展开分析入口">
          🎯
      </div>

      <!-- 6. 右侧悬浮卡片：节点详细分析 -->
      <div class="right-detail-drawer" :class="{ collapsed: rightDrawerCollapsed }">
          <div class="drawer-header">
              <div class="drawer-title">📝 节点详细分析</div>
              <button class="toolbar-btn-icon" @click="rightDrawerCollapsed = true" title="收起">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>
              </button>
          </div>
          <div class="drawer-content" v-if="selectedNode">
              <el-card style="background: #f8fafc; border: 1px solid var(--border-color); margin-bottom: 20px; box-shadow: none;">
                  <div style="font-size: 15px; font-weight: 600; margin-bottom: 10px; color: var(--primary);">
                      {{ selectedNode.methodName }}()
                  </div>
                  <div style="font-size: 12px; color: var(--text-muted); word-break: break-all; font-family: var(--font-mono); line-height: 1.5;">
                      {{ selectedNode.className }}
                  </div>
              </el-card>

              <!-- 1. 数据库操作汇总 -->
              <div style="margin-bottom: 24px;">
                  <div style="font-size: 14px; font-weight: 600; margin-bottom: 12px; color: var(--danger); display: flex; align-items: center; gap: 6px;">
                      <span>💾</span> 数据库操作汇总 ({{ selectedNode.dbOperations ? selectedNode.dbOperations.length : 0 }})
                  </div>
                  
                  <div v-if="selectedNode.dbOperations && selectedNode.dbOperations.length">
                      <el-collapse accordion>
                          <el-collapse-item v-for="(op, idx) in selectedNode.dbOperations" :key="idx" :name="idx">
                              <template #title>
                                  <span class="db-tag" style="margin-right: 10px;">
                                      <strong>{{ op.operationType }}</strong> {{ op.tableName }}
                                  </span>
                              </template>
                              <div style="font-size: 13px; padding: 12px; background: #fafafa; border-radius: 6px; border: 1px solid var(--border-color); line-height: 1.6;">
                                  <div style="margin-bottom: 6px;" v-if="op.columns && op.columns.length">
                                      <strong style="color: var(--primary);">操作字段:</strong> 
                                      <code style="word-break: break-all; color: var(--text-main); font-family: var(--font-mono); font-size: 12px; background: #e2e8f0; padding: 2px 4px; border-radius: 3px;">{{ op.columns.join(', ') }}</code>
                                  </div>
                                  <div style="margin-bottom: 6px;" v-if="op.whereCondition">
                                      <strong style="color: var(--primary);">条件 (WHERE):</strong> 
                                      <code style="word-break: break-all; color: #16a34a; font-family: var(--font-mono); font-size: 12px; background: #f0fdf4; padding: 2px 4px; border-radius: 3px;">{{ op.whereCondition }}</code>
                                  </div>
                                  <div v-if="op.sql">
                                      <strong style="color: var(--primary);">SQL 语句:</strong>
                                      <pre style="margin-top: 6px; font-size: 12px; max-height: 150px; overflow-y: auto; background: #1e293b; padding: 10px; border-radius: 4px; color: #f8fafc; font-family: var(--font-mono); border: 1px solid var(--border-color);"><code>{{ op.sql }}</code></pre>
                                  </div>
                              </div>
                          </el-collapse-item>
                      </el-collapse>
                  </div>
                  <div v-else style="font-size: 13px; color: var(--text-muted); padding: 20px; border: 1px dashed var(--border-color); border-radius: 8px; text-align: center; background: #fafafa;">
                      暂无数据库操作（该方法及其下游子调用未操作数据库）
                  </div>
              </div>

              <!-- 2. 子方法下钻列表 -->
              <div style="margin-bottom: 20px;">
                  <div style="font-size: 14px; font-weight: 600; margin-bottom: 12px; color: var(--primary); display: flex; align-items: center; gap: 6px;">
                      <span>🔗</span> 子方法调用 ({{ selectedNode.children ? selectedNode.children.length : 0 }})
                  </div>
                  
                  <div v-if="selectedNode.children && selectedNode.children.length">
                      <div v-for="child in selectedNode.children" :key="child.id" style="padding: 12px; background: #fafafa; border: 1px solid var(--border-color); border-radius: 8px; margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center; transition: all 0.2s;">
                          <div style="flex: 1; min-width: 0; padding-right: 10px;">
                              <div style="font-size: 13px; font-weight: 500; color: var(--text-main); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" :title="child.label">
                                  {{ child.label }}
                              </div>
                              <div style="font-size: 11px; color: var(--text-muted); word-break: break-all; font-family: var(--font-mono); margin-top: 6px;" v-if="child.dbOperations && child.dbOperations.length">
                                  💾 操作表: <span v-for="op in child.dbOperations" :key="op.tableName" style="margin-right: 6px; color: var(--danger); font-weight: 600;">{{ op.tableName }}</span>
                              </div>
                          </div>
                          <el-button size="small" type="primary" link @click="selectNodeFromList(child)">钻取分析</el-button>
                      </div>
                  </div>
                  <div v-else style="font-size: 13px; color: var(--text-muted); padding: 20px; border: 1px dashed var(--border-color); border-radius: 8px; text-align: center; background: #fafafa;">
                      未调用下级方法
                  </div>
              </div>
          </div>
          <div v-else style="flex: 1; display: flex; align-items: center; justify-content: center;">
              <el-empty description="在画布上点击任意一个方法节点以进行分析" style="padding: 0;"></el-empty>
          </div>
      </div>

      <!-- 右侧收起后的悬浮气泡 -->
      <div v-if="rightDrawerCollapsed" class="trigger-btn right-trigger" @click="rightDrawerCollapsed = false" title="展开详细分析">
          📝
      </div>
  </div>

  <!-- 引入局部外部 JS -->
  <script type="module" src="./index.js"></script>
  </body>
  </html>
  ```
