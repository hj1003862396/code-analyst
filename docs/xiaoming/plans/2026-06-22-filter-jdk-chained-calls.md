# 过滤 JDK 与非业务链式调用节点 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在方法调用树解析时，忽略 JDK 基础方法、嵌套/链式调用在 DTO/Entity/Util 等已忽略类型上的后续方法，防止出现 `Unknown.replace` 或 `Unknown.intValue` 节点。

**架构：** 在 `JavaSourceParser.java` 中扩展 `isIgnoredCall` 过滤方法名，并提取 `isIgnoredType` 用于识别忽略的类名。对 Scope 表达式追溯其最左侧的根变量，如根变量类型是忽略类型，则将其 scope 整体类型标记为忽略，从而排除所有链式数据操作。

**技术栈：** Java, JavaParser, JUnit, Maven

---

### Task 1：添加测试用例以验证链式与 JDK 方法过滤

**文件：**
- 修改：`src/test/java/com/code/analyst/parser/JavaSourceParserTest.java`

- [ ] **步骤 1：在测试类中写入失败测试**

  在 `JavaSourceParserTest` 中，新增 `testChainedAndJdkMethodCallFiltering` 测试用例。该测试使用模拟代码，其中包含 `req.getTaxNo().replace(" ", "")` 和 `loginUser.getAccountId().intValue()`，预期仅保留真实的业务服务调用 `invoiceManageService.toInvoiceApply`。

  在 `JavaSourceParserTest.java` 结尾前插入：
  ```java
      @Test
      public void testChainedAndJdkMethodCallFiltering() throws Exception {
          JavaSourceParser parser = new JavaSourceParser();
          File tempFile = File.createTempFile("FinanceControllerMock", ".java");
          tempFile.deleteOnExit();
          
          try (FileWriter writer = new FileWriter(tempFile)) {
              writer.write(
                  "package com.example;\n" +
                  "public class FinanceControllerMock {\n" +
                  "    private InvoiceManageService invoiceManageService;\n" +
                  "    public void saveInvoiceWithoutTitle(InvoiceWithTitleReq req) {\n" +
                  "        req.getTaxNo().replace(\" \", \"\");\n" +
                  "        LoginUser loginUser = ContextUtils.getLoginUser();\n" +
                  "        long userId = loginUser.getAccountId().intValue();\n" +
                  "        invoiceManageService.toInvoiceApply(userId);\n" +
                  "    }\n" +
                  "}\n"
              );
          }
          
          List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "saveInvoiceWithoutTitle");
          assertEquals(1, calls.size(), "Should only keep the business call to invoiceManageService.toInvoiceApply");
          assertEquals("invoiceManageService", calls.get(0).getObjectName());
          assertEquals("toInvoiceApply", calls.get(0).getMethodName());
      }
  ```

- [ ] **步骤 2：运行测试验证其失败**

  运行命令：
  ```bash
  mvn test -Dtest=JavaSourceParserTest#testChainedAndJdkMethodCallFiltering
  ```
  预期输出：FAIL，由于包含 `replace` 和 `intValue` 的方法调用未过滤，解析得到的 `calls` 列表大小为 3 而不是 1。

---

### Task 2：在 JavaSourceParser 中实现过滤逻辑

**文件：**
- 修改：`src/main/java/com/code/analyst/parser/JavaSourceParser.java`

- [ ] **步骤 1：修改 `JavaSourceParser.java` 添加核心过滤与追溯逻辑**

  在 `JavaSourceParser.java` 中进行如下修改：
  1. 编写辅助递归方法 `getRootExpression` 追溯链式表达式根节点。
  2. 提取 `isIgnoredType` 辅助方法过滤不需要解析的类型。
  3. 扩展 `isIgnoredCall` 中的方法名，加入常见 JDK 方法。
  4. 重构 `parseMethodCalls` 里的 scope 解析逻辑以集成根对象查找。

  目标替换内容（增加 `isIgnoredType`，扩展 `isIgnoredCall`）：
  ```java
      private boolean isIgnoredType(String objectType) {
          if (objectType == null) return false;
          String cleanType = objectType.replaceAll("<.*>", "");
          if (cleanType.equals("String") || cleanType.equals("List") || cleanType.equals("Map") || cleanType.equals("Set") 
                  || cleanType.equals("ArrayList") || cleanType.equals("HashMap") || cleanType.equals("HashSet") 
                  || cleanType.equals("Collections") || cleanType.equals("Objects") || cleanType.equals("Arrays") 
                  || cleanType.equals("Optional") || cleanType.equals("Stream") || cleanType.equals("Collectors") 
                  || cleanType.equals("Logger") || cleanType.equals("LoggerFactory") || cleanType.equals("System")
                  || cleanType.equals("BigDecimal") || cleanType.equals("Integer") || cleanType.equals("Long") 
                  || cleanType.equals("Boolean") || cleanType.equals("Double") || cleanType.equals("Character")
                  || cleanType.equals("BeanUtils") || cleanType.equals("BeanUtil") || cleanType.equals("CollUtil")
                  || cleanType.equals("CollectionUtils") || cleanType.equals("StringUtils")) {
              return true;
          }
          
          String lowerType = cleanType.toLowerCase();
          return lowerType.endsWith("dto") || lowerType.endsWith("entity") || lowerType.endsWith("vo") 
                  || (lowerType.endsWith("po") && !lowerType.endsWith("repo")) 
                  || lowerType.endsWith("req") || lowerType.endsWith("resp") || lowerType.endsWith("request") || lowerType.endsWith("response")
                  || lowerType.endsWith("param") || lowerType.endsWith("params") || lowerType.endsWith("query")
                  || lowerType.endsWith("util") || lowerType.endsWith("utils") 
                  || lowerType.endsWith("helper") || lowerType.endsWith("helpers")
                  || lowerType.endsWith("context") || lowerType.endsWith("contexts") 
                  || lowerType.endsWith("config") || lowerType.endsWith("configs")
                  || lowerType.endsWith("constant") || lowerType.endsWith("constants") 
                  || lowerType.endsWith("exception") || lowerType.endsWith("exceptions");
      }

      private com.github.javaparser.ast.expr.Expression getRootExpression(com.github.javaparser.ast.expr.Expression expr) {
          if (expr.isMethodCallExpr()) {
              com.github.javaparser.ast.expr.MethodCallExpr call = expr.asMethodCallExpr();
              if (call.getScope().isPresent()) {
                  return getRootExpression(call.getScope().get());
              }
          } else if (expr.isFieldAccessExpr()) {
              com.github.javaparser.ast.expr.FieldAccessExpr fa = expr.asFieldAccessExpr();
              return getRootExpression(fa.getScope());
          }
          return expr;
      }

      private boolean isIgnoredCall(String objectName, String objectType, String methodName) {
          if (objectName == null) return false;
          String lowerName = objectName.toLowerCase();
          
          // 过滤常见的 getter / setter 方法
          if (methodName.startsWith("get") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
              return true;
          }
          if (methodName.startsWith("set") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
              return true;
          }
          if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
              return true;
          }

          // 过滤常见的 Stream / 集合 / 辅助方法以及 JDK 常用基础方法
          if (methodName.equals("map") || methodName.equals("collect") || methodName.equals("filter") 
                  || methodName.equals("forEach") || methodName.equals("stream") || methodName.equals("flatMap") 
                  || methodName.equals("reduce") || methodName.equals("orElse") || methodName.equals("orElseGet") 
                  || methodName.equals("orElseThrow") || methodName.equals("isPresent") || methodName.equals("ifPresent")
                  || methodName.equals("get") || methodName.equals("set") || methodName.equals("add") 
                  || methodName.equals("put") || methodName.equals("size") || methodName.equals("isEmpty")
                  || methodName.equals("equals") || methodName.equals("hashCode") || methodName.equals("toString")
                  || methodName.equals("containsKey") || methodName.equals("containsValue") || methodName.equals("clear")
                  || methodName.equals("remove") || methodName.equals("build") || methodName.equals("builder")
                  || methodName.equals("replace") || methodName.equals("replaceAll") || methodName.equals("replaceFirst")
                  || methodName.equals("split") || methodName.equals("substring") || methodName.equals("trim")
                  || methodName.equals("toLowerCase") || methodName.equals("toUpperCase") || methodName.equals("startsWith")
                  || methodName.equals("endsWith") || methodName.equals("contains") || methodName.equals("length")
                  || methodName.equals("charAt") || methodName.equals("indexOf") || methodName.equals("lastIndexOf")
                  || methodName.equals("intValue") || methodName.equals("longValue") || methodName.equals("doubleValue")
                  || methodName.equals("floatValue") || methodName.equals("shortValue") || methodName.equals("byteValue")
                  || methodName.equals("booleanValue") || methodName.equals("compareTo") || methodName.equals("equalsIgnoreCase")
                  || methodName.equals("append") || methodName.equals("valueOf") || methodName.equals("values")
                  || methodName.equals("next") || methodName.equals("hasNext") || methodName.equals("asString")) {
              return true;
          }

          // 过滤日志和标准流
          if (lowerName.equals("log") || lowerName.equals("logger") || lowerName.equals("system.out") || lowerName.equals("system.err") || lowerName.equals("out") || lowerName.equals("err")) {
              return true;
          }
          
          // 过滤标准 JDK 类型调用
          if ("Unknown".equals(objectType) && objectName.length() > 0 && Character.isUpperCase(objectName.charAt(0))) {
              objectType = objectName;
              if (objectType.contains(".")) {
                  objectType = objectType.substring(0, objectType.indexOf('.'));
              }
          }

          return isIgnoredType(objectType);
      }
  ```

  并在 `parseMethodCalls` 内部的 `visit` 逻辑中（约 63-82 行），替换为以下集成根对象检查的逻辑：
  ```java
                      // 4. Scan all method call expressions inside the method body
                      method.accept(new VoidVisitorAdapter<Void>() {
                          @Override
                          public void visit(MethodCallExpr n, Void arg) {
                              super.visit(n, arg);
                              String objectName;
                              String objectType;
                              String callMethod = n.getNameAsString();
                              if (n.getScope().isPresent()) {
                                  com.github.javaparser.ast.expr.Expression scopeExpr = n.getScope().get();
                                  objectName = scopeExpr.toString();
                                  objectType = localTypes.getOrDefault(objectName, "Unknown");
                                  
                                  // Trace root type for chained expressions
                                  if ("Unknown".equals(objectType)) {
                                      com.github.javaparser.ast.expr.Expression rootExpr = getRootExpression(scopeExpr);
                                      String rootName = rootExpr.toString();
                                      String rootType = localTypes.getOrDefault(rootName, "Unknown");
                                      if ("Unknown".equals(rootType) && rootName.length() > 0 && Character.isUpperCase(rootName.charAt(0))) {
                                          rootType = rootName;
                                      }
                                      if (!"Unknown".equals(rootType)) {
                                          if (isIgnoredType(rootType)) {
                                              objectType = rootType;
                                          }
                                      }
                                  }
                              } else {
                                  objectName = "this";
                                  objectType = currentClassName;
                              }
                              if (isIgnoredCall(objectName, objectType, callMethod)) {
                                  return;
                              }
                              calls.add(new MethodCallInfo(objectName, callMethod, objectType));
                          }
                      }, null);
  ```

- [ ] **步骤 2：运行单元测试验证其通过**

  运行命令：
  ```bash
  mvn test -Dtest=JavaSourceParserTest
  ```
  预期输出：BUILD SUCCESS，所有测试通过。
