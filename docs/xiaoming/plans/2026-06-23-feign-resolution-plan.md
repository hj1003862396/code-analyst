# Feign 接口调用解析与深入查询 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 使 Feign 接口方法在调用链展开时能够解析到本地微服务的 Controller 实现类，从而支持往下查询调用链。

**架构：** 在 `ApiController.resolveImplementation` 中，当被解析的类是 `@FeignClient` 接口时，不再立即返回接口类本身，而是尝试在项目源码中搜索实现该 Feign 接口的本地实现类（一般为 Controller 类）。如果找到实现类，则返回实现类的全限定名；如果找不到实现类，则退回返回接口自身。

**技术栈：** Java, Spring Boot, JavaParser

---

### Task 1：修改 Feign 接口解析逻辑并进行单元测试验证

**文件：**
- 修改：`src/main/java/com/code/analyst/web/ApiController.java`
- 测试：`src/test/java/com/code/analyst/web/ApiControllerTest.java`

- [ ] **步骤 1：在 ApiControllerTest.java 中编写失败测试**

在 `src/test/java/com/code/analyst/web/ApiControllerTest.java` 文件末尾添加以下测试用例：

```java
    @Test
    public void testResolveFeignClientImplementation() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
        configManager.saveConfig(config);

        ApiController controller = new ApiController(configManager, new com.code.analyst.parser.JavaSourceParser(), new com.code.analyst.parser.SqlExtractor(), null);

        java.lang.reflect.Method resolveImplementation = ApiController.class.getDeclaredMethod("resolveImplementation", String.class, String.class, String.class);
        resolveImplementation.setAccessible(true);

        String resolved = (String) resolveImplementation.invoke(
            controller, 
            "/Users/hanjie/IdeaProjects/charging-ionchi", 
            "InvoiceRpcFeign", 
            "com.omp.finance.api.invoice.InvoiceRpcFeign"
        );
        assertEquals("com.omp.finance.intf.rpc.invoice.InvoiceRpcController", resolved);
    }
```

- [ ] **步骤 2：运行测试验证其失败**

运行以下命令：
`mvn test -Dtest=ApiControllerTest#testResolveFeignClientImplementation`

预期输出：测试失败（Fail），因为当前逻辑直接返回了接口本身 `"com.omp.finance.api.invoice.InvoiceRpcFeign"`，而不是实现类 `"com.omp.finance.intf.rpc.invoice.InvoiceRpcController"`。

- [ ] **步骤 3：在 ApiController.java 中编写解析逻辑**

修改 `src/main/java/com/code/analyst/web/ApiController.java` 中的 `resolveImplementation` 函数（第 570 行起）：

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
                  // 如果是 Feign 客户端接口（由 Spring 动态代理生成）
                  boolean isFeign = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                          .filter(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration::isInterface)
                          .flatMap(cid -> cid.getAnnotations().stream())
                          .anyMatch(ann -> ann.getNameAsString().equals("FeignClient"));
                  if (isFeign) {
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
              } catch (Exception ignored) {}
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

- [ ] **步骤 4：运行测试验证其通过**

再次运行以下命令：
`mvn test -Dtest=ApiControllerTest#testResolveFeignClientImplementation`

预期输出：测试通过（PASS）。

- [ ] **步骤 5：运行全部测试确保无 Regression**

运行命令：
`mvn test`

预期输出：全部单元测试通过。
