# 接口与 Repository 实现类智能寻址及解析优化设计规格书

本文档规范了 `code-db-analyst` 系统中接口节点解析和实现类自动寻址的技术实现，旨在解决 `ShortLinkRepo.selectById` 等 Repository 接口方法无法在脑图中展示及无法钻取分析的问题。

---

## 1. 目标与背景

当前系统在分析代码调用链时，遇到接口方法调用时存在以下限制：
* **误过滤问题**：`JavaSourceParser` 对类名后缀的过滤规则中包含 `po`（Persistent Object），使得以 `Repo` / `repo` 结尾的 Repository 类被误判定为需要过滤的实体类，从而在解析阶段被丢弃。
* **接口不可展开问题**：`ApiController` 仅硬编码支持以 `Service` 命名结尾的接口向其 `ServiceImpl` 实现类寻址。对于 Repository（如 `ShortLinkRepo`）或其他命名不符合 `Service` 的接口，系统无法获取其实现类方法体。由于接口方法无具体实现代码，导致节点在前端页面无法展开和钻取。

本设计提出**双通道接口寻址算法（启发式优先 + 局部文本预过滤并行扫描）**，在保障毫秒级极速响应的同时，实现 100% 精准的接口实现类解析，并修复 `Repo` 命名误过滤的问题。

---

## 2. 详细设计

### 2.1 修复 `Repo` 后缀误过滤

修改 [JavaSourceParser.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/parser/JavaSourceParser.java) 中的 `isIgnoredCall` 方法：

```java
// 过滤 DTO, Entity, VO, PO, Req, Resp 等类型
String lowerType = cleanType.toLowerCase();
if (lowerType.endsWith("dto") || lowerType.endsWith("entity") || lowerType.endsWith("vo") 
        || (lowerType.endsWith("po") && !lowerType.endsWith("repo")) // 防止误过滤以 Repo/repo 结尾的类
        || lowerType.endsWith("req") || lowerType.endsWith("resp") || lowerType.endsWith("request") || lowerType.endsWith("response")
        || lowerType.endsWith("param") || lowerType.endsWith("params") || lowerType.endsWith("query")) {
    return true;
}
```

### 2.2 局部并行文本预过滤寻址

在 [ApiController.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/web/ApiController.java) 中新增及优化如下方法，构建通用的接口寻址机制：

#### A. 新增并行文件扫描方法 `findImplementationBySearch`
为了在海量源码文件（>6000个）中快速寻找实现类，采用**并行文本过滤**。先查找内容同时包含 `implements` 和 `[接口简称]` 的文件，再只对这些候选文件执行 AST 深度验证：

```java
private Optional<Path> findImplementationBySearch(String rootPath, String interfaceSimpleName, String interfaceFqName) {
    try (Stream<Path> walk = Files.walk(Path.of(rootPath))) {
        // 1. 并行文本扫描，过滤候选文件
        List<Path> candidates = walk.filter(p -> p.toString().endsWith(".java"))
                .parallel()
                .filter(p -> {
                    try {
                        String content = Files.readString(p);
                        return content.contains("implements") && content.contains(interfaceSimpleName);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
        
        // 2. 仅对候选文件进行 AST 解析，验证是否真实实现了该接口
        for (Path p : candidates) {
            try {
                com.github.javaparser.ast.CompilationUnit cu = StaticJavaParser.parse(p);
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

#### B. 提取统一的实现类解析方法 `resolveImplementation`
封装接口寻址机制，首先使用极速的命名约定（`Service` -> `ServiceImpl`，`Repo` -> `RepoImpl` 等），未命中时再回退到 `findImplementationBySearch`：

```java
private String resolveImplementation(String root, String type, String fullTargetClassName) {
    // 1. 判断是否是接口，只有当 fullTargetClassName 不以 Impl 结尾且符合接口命名时才需要寻找实现类
    if (fullTargetClassName.contains("Impl")) {
        return fullTargetClassName;
    }
    
    // 2. 启发式优先通道
    String implClass = type + "Impl";
    Optional<Path> implPath = findJavaFile(root, implClass, fullTargetClassName);
    if (implPath.isPresent()) {
        return resolveFullClassName(root, implClass, implPath.get().toString());
    }
    
    // 3. 文本扫描二级通道
    Optional<Path> searchImplPath = findImplementationBySearch(root, type, fullTargetClassName);
    if (searchImplPath.isPresent()) {
        String searchImplClass = searchImplPath.get().getFileName().toString().replace(".java", "");
        return resolveFullClassName(root, searchImplClass, searchImplPath.get().toString());
    }
    
    return fullTargetClassName;
}
```

在 [ApiController.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/web/ApiController.java) 中，将原有的硬编码 `"Service"` 替换为调用通用的 `resolveImplementation` 方法。

---

## 3. 验证方案

### 3.1 自动化测试
* 新建/更新 [JavaSourceParserTest.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java) 单元测试。
* 编写测试用例，提供包含 `ShortLinkRepo` 属性调用的模拟类文件，执行 `parseMethodCalls` 校验返回的调用列表中是否正确包含 `ShortLinkRepo.selectById`，没有被 `isIgnoredCall` 错误过滤。

### 3.2 手动集成验证
1. 启动 `code-db-analyst` 应用。
2. 展开入口节点 `com.omp.marketing.intf.web.ShortLinkController#detail`。
3. 检查脑图中是否正确呈现 `ShortLinkServiceImpl.detail` -> `ShortLinkRepoImpl.selectById` 的调用树（已自动解析到实现类 `ShortLinkRepoImpl`）。
4. 点击 `ShortLinkRepoImpl.selectById` 节点，验证其能继续展开展示下一级 `ShortLinkMapper.selectById` 数据库操作节点，且右侧边栏显示操作的物理表及 SQL 类型。
