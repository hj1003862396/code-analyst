# 忽略 Lock 相关方法调用实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在静态解析 Java 源码构建方法调用关系树时，过滤掉所有方法名为 `lock` 和 `unlock` 的技术底层噪声节点。

**架构：** 在 `JavaSourceParser.isIgnoredCall` 的通用被忽略方法名条件中，追加 `lock` 和 `unlock`。通过修改测试类并采用测试驱动（TDD）方式进行验证。

**技术栈：** Java 17, JUnit 5, JavaParser

---

### Task 1：更新测试用例以引入 lock/unlock 过滤测试

**文件：**
- 修改：`src/test/java/com/code/analyst/parser/JavaSourceParserTest.java:11-42`

- [ ] **步骤 1：在测试代码中增加对 `lock` 和 `unlock` 调用的模拟，编写预期过滤成功的测试断言**

修改 [JavaSourceParserTest.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/test/java/com/code/analyst/parser/JavaSourceParserTest.java) 中的 `testIgnoredCallFiltering` 方法，添加对 `lockClient.lock();` 和 `lockClient.unlock();` 模拟调用：
```java
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
                "    private LockClient lockClient;\n" +
                "    public void doSomething() {\n" +
                "        userDto.getId();\n" +
                "        orderEntity.save();\n" +
                "        businessService.process();\n" +
                "        shortLinkRepo.selectById(1L);\n" +
                "        String.format(\"abc\");\n" +
                "        lockClient.lock();\n" +
                "        lockClient.unlock();\n" +
                "    }\n" +
                "}\n"
            );
        }
        
        List<MethodCallInfo> calls = parser.parseMethodCalls(tempFile.getAbsolutePath(), "doSomething");
        assertEquals(2, calls.size());
        
        boolean hasService = calls.stream().anyMatch(c -> c.getObjectName().equals("businessService") && c.getMethodName().equals("process"));
        boolean hasRepo = calls.stream().anyMatch(c -> c.getObjectName().equals("shortLinkRepo") && c.getMethodName().equals("selectById"));
        assertTrue(hasService, "Should contain businessService.process");
        assertTrue(hasRepo, "Should contain shortLinkRepo.selectById");
        
        // 显式验证返回值不包含 lock / unlock 两个调用
        boolean hasLock = calls.stream().anyMatch(c -> c.getMethodName().equals("lock") || c.getMethodName().equals("unlock"));
        assertFalse(hasLock, "Should not contain lock or unlock calls");
    }
```

- [ ] **步骤 2：运行测试验证其失败**

运行：`mvn test -Dtest=JavaSourceParserTest#testIgnoredCallFiltering`
预期：FAIL，由于目前尚未修改 `JavaSourceParser.java`，`calls.size()` 会返回 `4`（包含 `lock` 与 `unlock`），导致 `assertEquals(2, calls.size())` 失败。

---

### Task 2：更新解析过滤逻辑以排除 lock/unlock 方法名

**文件：**
- 修改：`src/main/java/com/code/analyst/parser/JavaSourceParser.java:160-181`

- [ ] **步骤 1：在 `isIgnoredCall` 过滤列表中添加对 `lock` 和 `unlock` 方法名的匹配**

修改 [JavaSourceParser.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/code/analyst/parser/JavaSourceParser.java#L160-L181)：
```java
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
                || methodName.equals("next") || methodName.equals("hasNext") || methodName.equals("asString")
                || methodName.equals("lock") || methodName.equals("unlock")) {
            return true;
        }
```

- [ ] **步骤 2：运行单元测试验证其通过**

运行：`mvn test -Dtest=JavaSourceParserTest`
预期：BUILD SUCCESS，所有测试通过。
