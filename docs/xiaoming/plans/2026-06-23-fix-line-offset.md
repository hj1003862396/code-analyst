# G6 关系图连线偏移与视觉关联问题修复 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 修复方法调用链关系图（DAG）中连线位置向下偏移、节点卡片间距重合，以及折叠状态下产生的非预期节点连线关联的视觉错觉问题。

**架构：** 在前端 `index.js` 的 G6 Graph 配置中设置 `defaultNode` 的 `size: [300, 120]`；在将根节点与展开获取的子节点数据推入 `nodes` 响应式列表之前，直接计算节点真实的尺寸大小并以 `size` 属性保存在节点模型中，提供给 Dagre 布局引擎和锚点计算。

**技术栈：** HTML5, Vue 3, AntV G6 (v4.8.x)

---

### Task 1: 调整 G6 Graph 配置的 defaultNode 尺寸属性

**文件：**
- 修改：`src/main/resources/static/index.js:189-219`

- [ ] **步骤 1：在 `initGraph` 的 `defaultNode` 中配置默认 size**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 大约第 201 行的 `initGraph` 中修改 `defaultNode` 的定义，补充 `size` 键：
  
  ```javascript
                  defaultNode: {
                      type: 'custom-node',
                      size: [300, 120]
                  },
  ```

- [ ] **步骤 2：检查 G6 自定义节点注册的 draw 宽高匹配**

  检查 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 大约第 85 行的 `G6.registerNode('custom-node')` 的实现，确保高度逻辑正确（`height = hasBody ? 240 : 120`，DOM 大小为 `300 * height`）。保持原样即可，无需修改此段代码。

---

### Task 2: 动态设置根节点的 size 属性

**文件：**
- 修改：`src/main/resources/static/index.js:232-268`

- [ ] **步骤 1：在 `initTree` 根节点加载成功后动态计算其高度并赋值 `size`**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 大约第 248 行，对初始化的根节点添加 `size` 属性：

  ```javascript
                  const rootData = await res.json();
  
                  nodes.length = 0;
                  edges.length = 0;
  
                  const hasBody = rootData.remarks || (rootData.isMapper && rootData.dbOperations && rootData.dbOperations.length > 0);
                  const height = hasBody ? 240 : 120;
  
                  nodes.push({
                      id: rootData.id,
                      className: rootData.className,
                      methodName: rootData.methodName,
                      isMapper: rootData.isMapper,
                      dbOperations: rootData.dbOperations,
                      remarks: rootData.remarks,
                      isRoot: true,
                      collapsed: true,
                      size: [300, height]
                  });
  ```

---

### Task 3: 动态设置展开获取的子节点 size 属性

**文件：**
- 修改：`src/main/resources/static/index.js:270-313`

- [ ] **步骤 1：在 `expandNodeChildren` 子节点加载时动态计算并赋值 `size`**

  在 [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) 大约第 287 行对新增子节点添加 `size` 属性：

  ```javascript
                  apiChildren.forEach(child => {
                      let existingNode = nodes.find(n => n.id === child.id);
                      if (!existingNode) {
                          const hasBody = child.remarks || (child.isMapper && child.dbOperations && child.dbOperations.length > 0);
                          const height = hasBody ? 240 : 120;
                          nodes.push({
                              id: child.id,
                              className: child.className,
                              methodName: child.methodName,
                              isMapper: child.isMapper,
                              dbOperations: child.dbOperations,
                              remarks: child.remarks,
                              collapsed: true,
                              size: [300, height]
                          });
                      }
  ```

---

### Task 4: 构建并验证页面效果

**文件：**
- 修改：无
- 验证：利用浏览器手动联调测试

- [ ] **步骤 1：编译项目确保正常启动**

  在终端运行后端构建命令：
  ```bash
  mvn clean compile
  ```
  预期结果：构建成功，无任何编译错误。

- [ ] **步骤 2：启动应用联调测试**

  运行 Spring Boot 启动命令：
  ```bash
  mvn spring-boot:run
  ```
  然后在浏览器中打开 `http://localhost:8080`，输入默认类名 `com.omp.finance.intf.app.FinanceController` 和方法名 `saveInvoiceWithoutTitle`，加载调用链。

- [ ] **步骤 3：验证关系图布局与连线偏移修复效果**

  1. 依次点击展开根节点和 `doInvoice` -> `blueInvoice`。
  2. 展开 `chargingInvoice` 节点，观察关系图：
     * 卡片间距是否合理（卡片与卡片之间没有垂直重合重叠）。
     * 所有子卡片的输入线是否整齐地指引到卡片最左侧边界的中心，输出线是否整齐地从卡片右侧折叠按钮中心发出，无垂直或水平位置的偏移。
     * 当 `memberInvoice` 保持在折叠状态（显示为 `+` 按钮）时，确认其右侧没有任何多余的连线弯曲轨迹与其触碰，彻底修复视觉上的“自动关联”错觉。
