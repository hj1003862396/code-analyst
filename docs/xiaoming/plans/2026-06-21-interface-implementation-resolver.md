# 接口与 Repository 实现类寻址优化实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 解决 `ShortLinkRepo.selectById` 被解析器误过滤的问题，并使接口节点在脑图中能够自动寻址其实现类，从而使用户可以通过点击下一级进行深入的数据库操作钻取分析。

**架构：** 
1. 在 `JavaSourceParser.java` 中细化过滤规则，防止将以 `Repo` / `repo` 结尾的接口判定为 `po` 实体类而误过滤；
2. 在 `ApiController.java` 中实现通用的双通道实现类寻址机制：启发式优先（直接匹配 `*Impl`），其次是局部并行文本关键字扫描检索，仅对符合条件的极少候选文件进行 AST 解析，在保障毫秒级性能前提下实现 100% 精准的接口到实现类映射。

**技术栈：** Java 17, Spring Boot, JavaParser, JUnit 5

---

### Task 1：修复 JavaSourceParser 中的误过滤规则并完善单元测试

**文件：**
- 修改：`src/main/java/com/codedb/analyst/parser/JavaSourceParser.java`
- 测试：`src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java`

- [ ] **步骤 1：恢复 / 更新 JavaSourceParserTest 单元测试验证 Repository 被正确保留**
  修改 `src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java`：
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
                  "    private ShortLinkRepo shortLinkRepo;\n" +
                  "    public void doSomething() {\n" +
                  "        userDto.getId();\n" +
                  "        orderEntity.save();\n" +
                  "        businessService.process();\n" +
                  "        shortLinkRepo.selectById(1L);\n" +
                  "        String.format(\"abc\");\n" +
                  "    }\n" +
                  "}\n"
              );
          }
          
          List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "doSomething");
          assertEquals(2, calls.size());
          
          // Verify that businessService and shortLinkRepo are kept, while DTO and Entity are ignored.
          boolean hasService = calls.stream().anyMatch(c -> c.getObjectName().equals("businessService") && c.getMethodName().equals("process"));
          boolean hasRepo = calls.stream().anyMatch(c -> c.getObjectName().equals("shortLinkRepo") && c.getMethodName().equals("selectById"));
          assertTrue(hasService, "Should contain businessService.process");
          assertTrue(hasRepo, "Should contain shortLinkRepo.selectById");
      }
  }
  ```

- [ ] **步骤 2：运行单元测试验证其失败**
  运行：`JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn test -Dtest=JavaSourceParserTest`
  预期：测试失败，或者抛出 AssertionFailedError，指出 `calls.size()` 不是 2，因为 `shortLinkRepo` 被误过滤了。

- [ ] **步骤 3：修改 JavaSourceParser 过滤条件**
  修改 `src/main/java/com/codedb/analyst/parser/JavaSourceParser.java` 中 `isIgnoredCall` 逻辑：
  ```java
              // 过滤 DTO, Entity, VO, PO, Req, Resp 等类型
              String lowerType = cleanType.toLowerCase();
              if (lowerType.endsWith("dto") || lowerType.endsWith("entity") || lowerType.endsWith("vo") 
                      || (lowerType.endsWith("po") && !lowerType.endsWith("repo")) 
                      || lowerType.endsWith("req") || lowerType.endsWith("resp") || lowerType.endsWith("request") || lowerType.endsWith("response")
                      || lowerType.endsWith("param") || lowerType.endsWith("params") || lowerType.endsWith("query")) {
                  return true;
              }
  ```

- [ ] **步骤 4：重新运行单元测试验证通过**
  运行：`JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn test -Dtest=JavaSourceParserTest`
  预期：BUILD SUCCESS

---

### Task 2：在 ApiController 中实现通用的双通道实现类智能寻址机制

**文件：**
- 修改：`src/main/java/com/codedb/analyst/web/ApiController.java`

- [ ] **步骤 1：在 ApiController.java 中新增 `findImplementationBySearch` 辅助方法**
  在类内部末尾添加以下方法实现局部并行扫描检索：
  ```java
      private Optional<Path> findImplementationBySearch(String rootPath, String interfaceSimpleName, String interfaceFqName) {
          try (java.util.stream.Stream<Path> walk = Files.walk(Path.of(rootPath))) {
              java.util.List<Path> candidates = walk.filter(p -> p.toString().endsWith(".java"))
                      .parallel()
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

- [ ] **步骤 2：在 ApiController.java 中新增 `resolveImplementation` 整合机制**
  ```java
      private String resolveImplementation(String root, String type, String fullTargetClassName) {
          if (fullTargetClassName == null || fullTargetClassName.isEmpty() || fullTargetClassName.contains("Impl")) {
              return fullTargetClassName;
          }

          // 1. 启发式命名通道 (直接寻找 *Impl)
          String implClass = type + "Impl";
          Optional<Path> implPath = findJavaFile(root, implClass, fullTargetClassName);
          if (implPath.isPresent()) {
              return resolveFullClassName(root, implClass, implPath.get().toString());
          }

          // 2. 文本特征预过滤扫描通道
          Optional<Path> searchImplPath = findImplementationBySearch(root, type, fullTargetClassName);
          if (searchImplPath.isPresent()) {
              String searchImplClass = searchImplPath.get().getFileName().toString().replace(".java", "");
              return resolveFullClassName(root, searchImplClass, searchImplPath.get().toString());
          }

          return fullTargetClassName;
      }
  ```

- [ ] **步骤 3：重构 ApiController.java 中三处硬编码的 Service 寻址逻辑**
  第一处修改：在 `expandTree` 方法中（约 134-144 行）：
  替换前：
  ```java
                      // If it is service interface, heuristic: look for implementations
                      if (fullTargetClassName.contains("Service") && !fullTargetClassName.contains("ServiceImpl")) {
                          String implClass = type + "Impl";
                          Optional<Path> implPath = findJavaFile(root, implClass, fullTargetClassName);
                          if (implPath.isPresent()) {
                              type = implClass;
                              fullTargetClassName = resolveFullClassName(root, implClass, implPath.get().toString());
                          }
                      }
  ```
  替换后：
  ```java
                      String resolvedFull = resolveImplementation(root, type, fullTargetClassName);
                      if (!resolvedFull.equals(fullTargetClassName)) {
                          fullTargetClassName = resolvedFull;
                          type = resolvedFull.substring(resolvedFull.lastIndexOf('.') + 1);
                      }
  ```

  第二处修改：在 `expandTree` 方法中（约 150-158 行）：
  替换前：
  ```java
                  // If it is service interface, heuristic: look for implementations
                  if (fullTargetClassName.contains("Service") && !fullTargetClassName.contains("ServiceImpl")) {
                      String implClass = type + "Impl";
                      Optional<Path> implPath = findJavaFile(root, implClass, fullTargetClassName);
                      if (implPath.isPresent()) {
                          type = implClass;
                          fullTargetClassName = resolveFullClassName(root, implClass, implPath.get().toString());
                      }
                  }
  ```
  替换后：
  ```java
                  String resolvedFull = resolveImplementation(root, type, fullTargetClassName);
                  if (!resolvedFull.equals(fullTargetClassName)) {
                      fullTargetClassName = resolvedFull;
                      type = resolvedFull.substring(resolvedFull.lastIndexOf('.') + 1);
                  }
  ```

  第三处修改：在 `getTransitiveDbOperations` 方法中（约 224-232 行）：
  替换前：
  ```java
                      String fullTargetClassName = resolveFullClassName(root, type, fileOpt.get().toString());
                      if (fullTargetClassName.contains("Service") && !fullTargetClassName.contains("ServiceImpl")) {
                          String implClass = type + "Impl";
                          Optional<Path> implPath = findJavaFile(root, implClass, fullTargetClassName);
                          if (implPath.isPresent()) {
                              type = implClass;
                              fullTargetClassName = resolveFullClassName(root, implClass, implPath.get().toString());
                          }
                      }
  ```
  替换后：
  ```java
                      String fullTargetClassName = resolveFullClassName(root, type, fileOpt.get().toString());
                      String resolvedFull = resolveImplementation(root, type, fullTargetClassName);
                      if (!resolvedFull.equals(fullTargetClassName)) {
                          fullTargetClassName = resolvedFull;
                          type = resolvedFull.substring(resolvedFull.lastIndexOf('.') + 1);
                      }
  ```

- [ ] **步骤 4：编译整个项目并确保通过**
  运行：`JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn clean compile`
  预期：BUILD SUCCESS

---

## 4. 验证方案

### 4.1 自动化测试
* 运行全量单元测试：
  `JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn test`
  预期所有测试全部通过。

### 4.2 手动功能核对
* 请用户重启前端和后端应用，刷新系统页面；
* 展开 `ShortLinkController.detail`，确认能在 mind map 中看到子级 `ShortLinkServiceImpl.detail` 以及其调用的 `ShortLinkRepoImpl.selectById`，且可以点击展开下一级看到 MyBatis 物理表 CRUD 操作。
