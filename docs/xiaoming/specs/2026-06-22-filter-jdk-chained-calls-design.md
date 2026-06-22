# 过滤 JDK 与非业务链式调用节点设计文档

设计并实现过滤机制，避免在方法调用关系树的解析结果中出现如 `Unknown.replace` 或 `Unknown.intValue` 等非业务、JDK 底层或嵌套调用的无意义节点。

## 需求背景

目前接口 `POST /api/tree/expand` 和 `POST /api/tree/initialize` 返回的树节点中包含了一些无意义的叶子节点，类名显示为 `Unknown`，方法名为 `replace`、`intValue` 等。
这通常是因为以下两类代码调用：
1. **嵌套/链式调用表达式**：形如 `req.getTaxNo().replace(" ", "")`，其 Scope 为 `req.getTaxNo()` 本身，静态分析在未引入完整类型推断时无法确定其返回类型，导致 `objectType` 被标识为 `"Unknown"`。
2. **DTO 或辅助对象的普通方法调用**：形如 `loginUser.getAccountId().intValue()`，由于 DTO 或 JDK 方法的返回值不是业务 Service/Mapper，解析为 `Unknown` 并被带入生成树节点，造成视图噪音。

## 详细方案

### 1. 扩展 `isIgnoredCall` 忽略方法列表

在 [JavaSourceParser.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/code/analyst/parser/JavaSourceParser.java) 中，扩展 `isIgnoredCall` 中的 `methodName` 名单。凡是匹配到以下常见 JDK 类型的底层数据处理、比较、格式化、转换方法，直接忽略，不收集为方法调用：

- **String 方法**：`replace`, `replaceAll`, `replaceFirst`, `split`, `substring`, `trim`, `toLowerCase`, `toUpperCase`, `startsWith`, `endsWith`, `contains`, `length`, `charAt`, `indexOf`, `lastIndexOf`
- **数值与基础包装类型转换方法**：`intValue`, `longValue`, `doubleValue`, `floatValue`, `shortValue`, `byteValue`, `booleanValue`, `charValue`, `compareTo`, `equalsIgnoreCase`, `valueOf`, `values`, `asString`
- **StringBuilder / 常用容器辅助方法**：`append`, `next`, `hasNext`

### 2. 根对象类型追溯忽略

对于形如 `a.b().c()` 的链式调用，我们需要一种机制识别其“根对象”是否属于已知应该被忽略的类型（如 DTO/Entity/Util 等）。

#### 2.1 递归提取表达式根对象

在 `JavaSourceParser.java` 中增加私有递归辅助方法，用于提取任意 Scope 表达式的根节点：

```java
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
```

#### 2.2 忽略类型匹配提取器

为了复用类型过滤逻辑，把 `isIgnoredCall` 中原有的类型后缀及 JDK 类型过滤规则，抽离并封装为 `isIgnoredType(String objectType)` 方法：

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
```

#### 2.3 根对象追踪逻辑集成

在 `JavaSourceParser.parseMethodCalls` 解析方法调用的 `VoidVisitorAdapter` 中，检查如果 scope 的 `objectType` 解析为 `"Unknown"`，则提取其根表达式，并在 `localTypes` 中查找根表达式的类型。

- 若根表达式类型属于被忽略的类型（即 `isIgnoredType(rootType)` 结果为 `true`），则直接将该方法调用的 `objectType` 标记为 `rootType`，以便后续的 `isIgnoredCall` 拦截并过滤它。

```java
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
```

## 验证计划

1. **单元测试验证**：
   在 `JavaSourceParserTest.java` 中增加针对嵌套链式调用（如带有 `req.getTaxNo().replace(...)` 与 `loginUser.getAccountId().intValue()`）的测试用例，验证解析结果中不再包含 `replace` 和 `intValue` 相关的 `MethodCallInfo`。
2. **集成测试验证**：
   启动后端服务，手动调用接口 `/api/tree/expand`，传入包含此类调用的类与方法，确认响应中不再包含以 `Unknown` 为类名的无关调用节点。
