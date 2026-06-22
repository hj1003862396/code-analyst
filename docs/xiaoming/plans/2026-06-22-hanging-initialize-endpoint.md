# 修复 Initialize 接口卡死并优化实现类解析 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 优化实现类解析逻辑并增强方法调用过滤，彻底解决 `/api/tree/initialize` 接口处理本地调用和工具类静态调用时卡死的问题。

**架构：**
1. 在 `JavaSourceParser.isIgnoredCall` 中补充 `util`, `utils`, `helper`, `helpers`, `context`, `contexts`, `config`, `configs`, `constant`, `constants`, `exception`, `exceptions` 后缀过滤。
2. 在 `ApiController.resolveImplementation` 中在检索实现类前判断目标类是否为接口。若不是接口，直接返回该类，避免无意义的检索。
3. 重构 `ApiController.findImplementationBySearch`，不再采用 `Files.walk` 遍历磁盘，改为读取已缓存的 `javaFileByNameCache` 文件路径，从而避免磁盘遍历开销。

**技术栈：** Java 11, Spring Boot, JavaParser

---

### Task 1：优化方法调用过滤并增加辅助类后缀过滤

**文件：**
- 修改：`src/main/java/com/code/analyst/parser/JavaSourceParser.java:140-149`
- 测试：`src/test/java/com/code/analyst/parser/JavaSourceParserTest.java`

- [ ] **步骤 1：在 JavaSourceParserTest 中编写针对后缀过滤的失败测试**
  在 `src/test/java/com/code/analyst/parser/JavaSourceParserTest.java` 中添加 `testUtilityClassAndExceptionsFiltering` 方法。
  
  ```java
  @Test
  public void testUtilityClassAndExceptionsFiltering() throws Exception {
      JavaSourceParser parser = new JavaSourceParser();
      File tempFile = File.createTempFile("MockController", ".java");
      tempFile.deleteOnExit();
      
      try (FileWriter writer = new FileWriter(tempFile)) {
          writer.write(
              "package com.example;\n" +
              "public class MockController {\n" +
              "    public void show() {\n" +
              "        StringUtils.isBlank(\"a\");\n" +
              "        ContextUtils.getUserId();\n" +
              "        MyHelper.doWork();\n" +
              "        AppConfig.setup();\n" +
              "        ErrorCodeConstant.SUCCESS.code();\n" +
              "        throw new BusinessException(\"error\");\n" +
              "    }\n" +
              "}\n"
          );
      }
      
      List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "show");
      assertEquals(0, calls.size(), "All utility, config, constant, helper, and exception calls should be filtered out");
  }
  ```

- [ ] **步骤 2：运行测试验证其失败**
  运行命令：
  ```bash
  mvn clean test -Dtest=JavaSourceParserTest#testUtilityClassAndExceptionsFiltering
  ```
  预期：测试失败（或者是 `AssertionError`，因为未被过滤的调用导致 `calls.size()` 大于 0）。

- [ ] **步骤 3：在 JavaSourceParser.java 中增加后缀过滤代码**
  修改 `src/main/java/com/code/analyst/parser/JavaSourceParser.java` 中的 `isIgnoredCall` 方法：
  
  ```java
  // 过滤 DTO, Entity, VO, PO, Req, Resp 等类型以及工具、常量、配置、上下文、辅助和异常类型
  String lowerType = cleanType.toLowerCase();
  if (lowerType.endsWith("dto") || lowerType.endsWith("entity") || lowerType.endsWith("vo") 
          || (lowerType.endsWith("po") && !lowerType.endsWith("repo")) 
          || lowerType.endsWith("req") || lowerType.endsWith("resp") || lowerType.endsWith("request") || lowerType.endsWith("response")
          || lowerType.endsWith("param") || lowerType.endsWith("params") || lowerType.endsWith("query")
          || lowerType.endsWith("util") || lowerType.endsWith("utils") 
          || lowerType.endsWith("helper") || lowerType.endsWith("helpers")
          || lowerType.endsWith("context") || lowerType.endsWith("contexts") 
          || lowerType.endsWith("config") || lowerType.endsWith("configs")
          || lowerType.endsWith("constant") || lowerType.endsWith("constants") 
          || lowerType.endsWith("exception") || lowerType.endsWith("exceptions")) {
      return true;
  }
  ```

- [ ] **步骤 4：再次运行测试验证其通过**
  运行命令：
  ```bash
  mvn test -Dtest=JavaSourceParserTest
  ```
  预期：PASS（所有测试均成功运行，包括之前添加的 `testLocalMethodCallParsing` 和本次的 `testUtilityClassAndExceptionsFiltering`）。

---

### Task 2：优化 ApiController 实现类检索逻辑以彻底消除磁盘扫描卡死

**文件：**
- 修改：`src/main/java/com/code/analyst/web/ApiController.java:526-577`
- 测试：`src/test/java/com/code/analyst/web/ApiControllerTest.java`

- [ ] **步骤 1：修改 ApiController.java，在 resolveImplementation 中增加接口预检**
  在 `ApiController.java` 中的 `resolveImplementation` 首部增加对是否为接口的解析逻辑。如果不是接口，直接返回 `fullTargetClassName`，避免不必要的检索。
  
  ```java
  private String resolveImplementation(String root, String type, String fullTargetClassName) {
      if (fullTargetClassName == null || fullTargetClassName.isEmpty() || fullTargetClassName.contains("Impl")) {
          return fullTargetClassName;
      }

      // 接口预检：如果是普通的 class (非 interface)，无需检索实现类，直接返回
      Optional<Path> pathOpt = findJavaFileByFqName(root, fullTargetClassName);
      if (pathOpt.isPresent()) {
          try {
              com.github.javaparser.ast.CompilationUnit cu = com.github.javaparser.StaticJavaParser.parse(pathOpt.get().toFile());
              boolean isInterface = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                      .anyMatch(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration::isInterface);
              if (!isInterface) {
                  return fullTargetClassName;
              }
          } catch (Exception ignored) {}
      }

      // 1. 启发式命名通道 (直接寻找 *Impl)
      String implClass = type + "Impl";
      Optional<Path> implPath = findJavaFile(root, implClass, fullTargetClassName);
      if (implPath.isPresent()) {
          return resolveFullClassName(root, implClass, implPath.get().toString());
      }
  ...
  ```

- [ ] **步骤 2：优化 ApiController.java 中的 findImplementationBySearch 方法**
  将其中的 `Files.walk` 改为利用内存缓存 `javaFileByNameCache`。
  
  ```java
  private Optional<Path> findImplementationBySearch(String rootPath, String interfaceSimpleName, String interfaceFqName) {
      ensureIndexInitialized(rootPath);
      List<Path> allJavaFiles = new ArrayList<>();
      for (List<Path> paths : javaFileByNameCache.values()) {
          allJavaFiles.addAll(paths);
      }
      
      try {
          java.util.List<Path> candidates = allJavaFiles.parallelStream()
                  .filter(p -> {
                      try {
                          String content = Files.readString(p);
                          return content.contains("implements") && content.contains(interfaceSimpleName);
                      } catch (Exception e) {
                          return false;
                      }
                  })
                  .collect(java.util.stream.Collectors.toList());

          for (Path p : candidates) {
              try {
                  com.github.javaparser.ast.CompilationUnit cu = com.github.javaparser.StaticJavaParser.parse(p);
                  boolean isMatch = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                          .anyMatch(cid -> cid.getImplementedTypes().stream()
                                  .anyMatch(t -> t.getNameAsString().equals(interfaceSimpleName)));
                  if (isMatch) {
                      return Optional.of(p);
                  }
              } catch (Exception ignored) {}
          }
      } catch (Exception e) {
          e.printStackTrace();
      }
      return Optional.empty();
  }
  ```

- [ ] **步骤 3：运行项目全部测试以验证重构后系统正确性**
  运行命令：
  ```bash
  mvn clean test
  ```
  预期：BUILD SUCCESS (12 个测试全部通过)。

- [ ] **步骤 4：运行模拟 curl 命令验证 `/api/tree/initialize` 是否瞬时响应而不卡死**
  我们将发起对本地 `FinanceController` 的初始化查询以验证性能。
  运行：
  ```bash
  curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" -X POST 'http://localhost:8080/api/tree/initialize' \
    -H 'Content-Type: application/json' \
    -d '{"className":"com.omp.finance.intf.app.FinanceController","methodName":"saveInvoiceWithoutTitle"}'
  ```
  预期：HTTP 200 并且响应耗时在毫秒级别（如 `0.1s` 以内）。
