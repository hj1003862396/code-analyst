# 忽略 Unknown 类型调用实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在静态解析 Java 源码构建方法调用关系树时，过滤掉所有解析类型为 `Unknown` 的噪声节点。

**架构：** 在 `JavaSourceParser.isIgnoredCall` 方法开头，若 `objectType` 为 `"Unknown"`，直接返回 `true`。

**技术栈：** Java 17, JUnit 5, JavaParser

---

### Task 1：更新解析过滤逻辑以排除 Unknown 类型

**文件：**
- 修改：`src/main/java/com/code/analyst/parser/JavaSourceParser.java:145-150`

- [ ] **步骤 1：在 `isIgnoredCall` 最前面添加对 `"Unknown".equals(objectType)` 的过滤拦截**

修改 [JavaSourceParser.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/code/analyst/parser/JavaSourceParser.java#L145-L150)：
```java
    private boolean isIgnoredCall(String objectName, String objectType, String methodName) {
        if ("Unknown".equals(objectType)) {
            return true;
        }
        
        if (objectName == null) return false;
```

- [ ] **步骤 2：运行全部单元测试以确保没有破坏现有过滤和分析规则**

运行：`JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn test`
预期：BUILD SUCCESS，所有 13 个测试全部 PASS。
