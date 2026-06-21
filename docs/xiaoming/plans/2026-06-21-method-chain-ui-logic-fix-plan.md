# 方法调用链精准分析与前端 UI/交互逻辑修复 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 修复后端在多模块项目下面对重名类时匹配错文件、前端点击节点导致 D3 布局飘移/弹走，以及重构前端页面风格为极简浅色导图白板（根节点蓝色圆角矩形，子方法节点无边框极简下划线文字连接）。

**架构：**
1. 在 `pom.xml` 中引入 Spring Boot Test 起步依赖。
2. 后端重构 `ApiController.java` 中的类检索逻辑：如果输入为全限定类名，采用绝对包路径后缀检索；如果是简名，根据当前调用方文件（如 `ShortLinkController.java`）的包名及 `import` 列表打分，精确查找匹配度最高的类文件。
3. 后端在寻找接口实现类时，根据同源包名最长公共前缀匹配亲和度最高的目标。
4. 前端在 `index.html` 中引入 `markRaw()` 对所有 D3 节点代理进行防代理拦截，同时通过增量修改原始数据并重建 hierarchy 的方式来重渲染。
5. 前端整体切换为浅色极简主题样式（白背景、灰色边框、柔和投影）。
6. 前端重构 D3 节点渲染逻辑：根据 `d.depth === 0` 判断根节点卡片与分支节点下划线形式。先更新节点测算宽度，后通过右对齐拐角渲染连接线。

**技术栈：** Java, Spring Boot, JavaParser, D3.js (v7), Vue 3, Element Plus

---

### Task 1：引入测试框架并配置单元测试环境

**文件：**
- 修改：`pom.xml`
- 创建：`src/test/java/com/codedb/analyst/web/ApiControllerTest.java`

- [ ] **步骤 1：在 `pom.xml` 中添加 `spring-boot-starter-test` 依赖**
  
  在 `<dependencies>` 中追加如下内容：
  ```xml
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-test</artifactId>
              <scope>test</scope>
          </dependency>
  ```

- [ ] **步骤 2：创建 ApiControllerTest 测试骨架文件**
  
  创建 `src/test/java/com/codedb/analyst/web/ApiControllerTest.java`，内容如下：
  ```java
  package com.codedb.analyst.web;

  import org.junit.jupiter.api.Test;
  import org.springframework.boot.test.context.SpringBootTest;

  import static org.junit.jupiter.api.Assertions.assertTrue;

  @SpringBootTest(classes = com.codedb.analyst.CodeDbAnalystApplication.class)
  public class ApiControllerTest {
      @Test
      public void contextLoads() {
          assertTrue(true);
      }
  }
  ```

- [ ] **步骤 3：编译并运行测试以验证测试环境是否设置正确**
  
  运行命令：
  ```bash
  JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn clean test
  ```
  预期输出：`BUILD SUCCESS`，1个测试运行通过。

---

### Task 2：重构后端重名类精确路径解析与包亲和度算法

**文件：**
- 修改：`src/main/java/com/codedb/analyst/web/ApiController.java`
- 修改：`src/test/java/com/codedb/analyst/web/ApiControllerTest.java`

- [ ] **步骤 1：在 ApiControllerTest.java 中编写针对同名类的路径解析失败测试用例**
  
  在 `ApiControllerTest.java` 中增加具体解析同名类打分和最长包前缀查找的单元测试：
  ```java
      @Test
      public void testFindJavaFileWithDuplicates() throws Exception {
          com.codedb.analyst.config.ConfigManager configManager = new com.codedb.analyst.config.ConfigManager();
          com.codedb.analyst.config.AppConfig config = new com.codedb.analyst.config.AppConfig();
          config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
          configManager.saveConfig(config);

          com.codedb.analyst.parser.JavaSourceParser parser = new com.codedb.analyst.parser.JavaSourceParser();
          com.codedb.analyst.parser.SqlExtractor extractor = new com.codedb.analyst.parser.SqlExtractor();
          ApiController controller = new ApiController(configManager, parser, extractor, null);

          // 模拟调用方为 ShortLinkController.java
          String controllerPath = "/Users/hanjie/IdeaProjects/charging-ionchi/omp-trading/omp-marketing/omp-marketing-server/src/main/java/com/omp/marketing/intf/web/ShortLinkController.java";
          
          java.lang.reflect.Method findJavaFileMethod = ApiController.class.getDeclaredMethod("findJavaFile", String.class, String.class, String.class);
          findJavaFileMethod.setAccessible(true);
          
          java.util.Optional<java.nio.file.Path> res = (java.util.Optional<java.nio.file.Path>) findJavaFileMethod.invoke(
              controller, 
              "/Users/hanjie/IdeaProjects/charging-ionchi", 
              "ShortLinkService", 
              controllerPath
          );
          
          assertTrue(res.isPresent());
          String resolvedPath = res.get().toString().replace('\\', '/');
          assertTrue(resolvedPath.contains("omp-marketing-service"), "应该精准匹配至 marketing 服务模块: " + resolvedPath);
      }
  ```

- [ ] **步骤 2：执行测试，验证失败**
  
  运行命令：
  ```bash
  JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn test -Dtest=ApiControllerTest#testFindJavaFileWithDuplicates
  ```
  预期输出：单元测试失败（因为目前的解析算法只做简单 `findFirst`，必然在 `omp-support-service` 中找到错误文件）。

- [ ] **步骤 3：在 ApiController.java 中重写类解析及亲和度判定方法**
  
  替换 `ApiController.java` 中的 `findJavaFile` 方法，并追加 `findJavaFileByFqName`、精确匹配评分算法以及实现类的公共包前缀打分算法：
  ```java
      private Optional<Path> findJavaFile(String rootPath, String className) {
          return findJavaFile(rootPath, className, null);
      }

      private List<Path> findJavaFiles(String rootPath, String className) {
          try (Stream<Path> walk = Files.walk(Path.of(rootPath))) {
              return walk.filter(p -> p.getFileName().toString().equals(className + ".java"))
                      .collect(Collectors.toList());
          } catch (Exception e) {
              return Collections.emptyList();
          }
      }

      private Optional<Path> findJavaFile(String rootPath, String className, String sourceOrRef) {
          List<Path> candidates = findJavaFiles(rootPath, className);
          if (candidates.isEmpty()) {
              return Optional.empty();
          }
          if (candidates.size() == 1) {
              return Optional.of(candidates.get(0));
          }
          if (sourceOrRef == null || sourceOrRef.isEmpty()) {
              return Optional.of(candidates.get(0));
          }

          if (sourceOrRef.endsWith(".java") || sourceOrRef.contains("/") || sourceOrRef.contains("\\")) {
              String sourcePackage = "";
              List<String> sourceImports = new ArrayList<>();
              try {
                  List<String> lines = Files.readAllLines(Path.of(sourceOrRef));
                  for (String line : lines) {
                      String trimmed = line.trim();
                      if (trimmed.startsWith("package ")) {
                          sourcePackage = trimmed.replace("package ", "").replace(";", "").trim();
                      } else if (trimmed.startsWith("import ")) {
                          sourceImports.add(trimmed.replace("import ", "").replace(";", "").trim());
                      }
                  }
              } catch (Exception ignored) {}

              Path bestPath = candidates.get(0);
              int bestScore = -1;
              for (Path p : candidates) {
                  String fqName = getFqNameFromFile(rootPath, p, className);
                  String pkg = fqName.contains(".") ? fqName.substring(0, fqName.lastIndexOf('.')) : "";
                  
                  int score = 0;
                  if (sourceImports.contains(fqName)) {
                      score = 3;
                  } else if (pkg.equals(sourcePackage)) {
                      score = 2;
                  } else if (sourceImports.contains(pkg + ".*")) {
                      score = 1;
                  }
                  
                  if (score > bestScore) {
                      bestScore = score;
                      bestPath = p;
                  }
              }
              return Optional.of(bestPath);
          } else {
              Path bestPath = candidates.get(0);
              int maxCommonPrefix = -1;
              for (Path p : candidates) {
                  String fqName = getFqNameFromFile(rootPath, p, className);
                  int score = commonPackagePrefixLength(sourceOrRef, fqName);
                  if (score > maxCommonPrefix) {
                      maxCommonPrefix = score;
                      bestPath = p;
                  }
              }
              return Optional.of(bestPath);
          }
      }

      private Optional<Path> findJavaFileByFqName(String rootPath, String fqName) {
          if (fqName == null || fqName.isEmpty()) {
              return Optional.empty();
          }
          if (!fqName.contains(".")) {
              return findJavaFile(rootPath, fqName, null);
          }
          String pathSuffix = fqName.replace('.', '/') + ".java";
          try (Stream<Path> walk = Files.walk(Path.of(rootPath))) {
              return walk.filter(p -> p.toString().replace('\\', '/').endsWith(pathSuffix))
                      .findFirst();
          } catch (Exception e) {
              return Optional.empty();
          }
      }

      private String getFqNameFromFile(String rootPath, Path path, String className) {
          try {
              List<String> lines = Files.readAllLines(path);
              for (String line : lines) {
                  if (line.trim().startsWith("package ")) {
                      return line.replace("package ", "").replace(";", "").trim() + "." + className;
                  }
              }
          } catch (Exception ignored) {}
          return className;
      }

      private int commonPackagePrefixLength(String fqName1, String fqName2) {
          String[] parts1 = fqName1.split("\\.");
          String[] parts2 = fqName2.split("\\.");
          int matchCount = 0;
          int minLen = Math.min(parts1.length, parts2.length);
          for (int i = 0; i < minLen - 1; i++) {
              if (parts1[i].equals(parts2[i])) {
                  matchCount++;
              } else {
                  break;
              }
          }
          return matchCount;
      }
  ```

- [ ] **步骤 4：在 ApiController.java 中集成新的类加载方式**
  
  修改 `expandTree` 和 `getTransitiveDbOperations` 方法，使其在解析类和寻找接口实现时采用带上下文的 `findJavaFile` 或 `findJavaFileByFqName`：
  
  1. 在 `expandTree` 中：
     将原本的：
     ```java
                 String simpleName = className.substring(className.lastIndexOf('.') + 1);
                 Optional<Path> fileOpt = findJavaFile(root, simpleName);
     ```
     修改为：
     ```java
                 Optional<Path> fileOpt = findJavaFileByFqName(root, className);
     ```
     
     并且将解析 `type` 以及实现类 `implClass` 时，调用 `resolveFullClassName` 和 `findJavaFile` 的位置传入正确的上下文（参见设计文档 2.1 节）。
  
  2. 在 `getTransitiveDbOperations` 中：
     将原本的 `findJavaFile(root, simpleName)` 改为 `findJavaFileByFqName(root, className)`，且后续的 `resolveFullClassName` 和 `findJavaFile(root, implClass)` 均传入对应的接口路径或实现路径作为上下文。

- [ ] **步骤 5：运行测试以确保其通过**
  
  运行命令：
  ```bash
  JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn test
  ```
  预期输出：`BUILD SUCCESS`。

---

### Task 3：重构前端页面为极简浅色白板主题与 XMind 下划线导图样式

**文件：**
- 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：重构 `index.html` CSS 样式为浅色极简主题**
  
  1. 修改 `:root` 主题配色为浅色系：
     ```css
             :root {
                 --bg-main: #F8FAFC;       /* 浅灰白背景 */
                 --bg-card: #FFFFFF;       /* 纯白卡片 */
                 --border-color: #E2E8F0;  /* 浅灰边框 */
                 --primary: #4F73DF;       /* 经典睿智蓝 */
                 --secondary: #A855F7;    /* 霓虹紫 */
                 --success: #10B981;      /* 翠绿 */
                 --danger: #EF4444;       /* 红色 */
                 --text-main: #0F172A;     /* 深灰色主字 */
                 --text-muted: #64748B;    /* 灰蓝色辅字 */
                 --font-sans: 'Outfit', sans-serif;
                 --font-mono: 'JetBrains Mono', monospace;
             }
     ```
  
  2. 修改 `.left-panel`、`.right-panel` 和 `.header` 为白色底色、轻阴影：
     ```css
             body {
                 background-color: var(--bg-main);
                 color: var(--text-main);
             }
             .header {
                 background: #FFFFFF;
                 border-bottom: 1px solid var(--border-color);
                 box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
             }
             .left-panel, .right-panel {
                 background: #FFFFFF;
                 border-color: var(--border-color);
                 box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
             }
             .panel-card {
                 background: #F8FAFC;
                 border: 1px solid var(--border-color);
             }
             .link {
                 stroke: var(--primary); /* 连接线改为核心蓝 */
                 stroke-width: 1.5px;
             }
             .db-tag {
                 background: rgba(239, 68, 68, 0.05);
                 border-color: rgba(239, 68, 68, 0.2);
             }
     ```

- [ ] **步骤 2：在前端中集成 `markRaw` 并实现增量层次树安全重建**
  
  在 `setup()` 函数中通过 `Vue.markRaw` 解除对 D3 节点的代理，保证坐标计算数据纯净：
  ```javascript
      const { createApp, ref, computed, markRaw } = Vue;
  ```
  在 `selectNode` 加载新子方法时更新 `data.children` 并通过 `rootData = markRaw(d3.hierarchy(rootData.data))` 完好重构，通过 `data.id` 还原重设选中的 `selectedNode.value`。

- [ ] **步骤 3：重写 `updateTree` 实现分支节点下划线风格与边缘 Elbow 折线计算**
  
  重构节点 SVG 结构：
  - 根节点 `d.depth === 0` 渲染为填充为 `var(--primary)` 的圆角矩形，文字填充为白色。
  - 分支节点 `d.depth > 0` 矩形填充设为 `transparent`，不加框线。
  - 在每个分支节点下面绘制高 2px 的 `<line>` 下划线元素，横跨 `x1 = -10` 到 `x2 = d.width - 10`。
  
  更新连线算法，连线起点连向父节点右边缘，终点交汇于子节点下划线起点。
  
  修改后的 `updateTree` 完整实现参见设计文档 2.2 节。

---

### Task 4：完整编译与部署打包

**文件：**
- 修改：无

- [ ] **步骤 1：执行整个项目的完整 Maven 打包验证**
  
  运行命令：
  ```bash
  JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn clean package -DskipTests
  ```
  预期输出：`BUILD SUCCESS`，项目完全正常构建打包完毕。
