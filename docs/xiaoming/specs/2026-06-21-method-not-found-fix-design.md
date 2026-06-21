# 方法未找到容错与默认方法初始化优化设计规格文档

本文档定义了解决类中找不到对应方法时，系统静默使用空源码请求大模型的问题，以及调整前端默认分析的目标类与方法的具体实现规格。

## 1. 变更背景与目标

目前，当前端输入的 Java 方法在后端解析的 Java 类文件中不存在时，系统会返回空的方法源码 `""`。此时，后端仍会向大模型发起调用，大模型因收到的源码为空而给出无效回复。

同时，用户要求将页面默认分析的入口方法变更为 `com.omp.financial.intf.job.InvoicingApplyJob` 类的 `process` 方法。

**目标**：
1. 拦截方法不存在的调用：后端解析时若方法不存在，抛出/返回明确错误提示，并列出该类中的所有可用方法。
2. 避免无效的大模型 Token 消耗：在未解析到源码时直接拦截大模型 API 请求。
3. 变更前端的默认初始化类为 `com.omp.financial.intf.job.InvoicingApplyJob#process`。

---

## 2. 模块级设计与改动细节

### 前端模块

#### [index.html](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.html)
- 替换 `setup` 函数中的 `entry` 初始状态：
  ```javascript
  const entry = ref({
      className: 'com.omp.financial.intf.job.InvoicingApplyJob',
      methodName: 'process'
  });
  ```

---

### 后端模块

#### [JavaSourceParser.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/parser/JavaSourceParser.java)
- 新增接口方法：
  ```java
  public List<String> getMethodNames(String filePath) throws FileNotFoundException {
      CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
      return cu.findAll(MethodDeclaration.class).stream()
              .map(MethodDeclaration::getNameAsString)
              .distinct()
              .collect(Collectors.toList());
  }
  ```

#### [ApiController.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/web/ApiController.java)
- 在 `/api/ai/explain` 映射的 `explainMethod` 方法中：
  - 调用 `sourceParser.getMethodSource(fileOpt.get().toString(), methodName)` 获取方法源码。
  - **拦截校验**：如果获取到的源码为 null 或空字符串 `""`（或者是空白字符），则：
    - 调用 `sourceParser.getMethodNames(fileOpt.get().toString())` 得到该类的所有声明的方法名称列表。
    - 构建 Markdown 格式的错误响应消息：
      ```markdown
      ### ❌ 分析失败：未找到对应的方法源码
      在类 `InvoicingApplyJob` 中找不到方法 `process`。请确认方法名称是否正确。

      **该类中存在的候选方法：**
      - `process`
      - `otherMethod`
      ```
    - 直接将该消息放入 `res.put("markdownReport", errorMsg)`，并返回 `ResponseEntity.ok(res)`。
    - **不**再向下执行 `llmService.explainMethod(...)`。

---

## 3. 验证计划

1. **初始状态验证**：
   - 访问前端页面，确认默认类与方法显示为 `com.omp.financial.intf.job.InvoicingApplyJob` 和 `process`。
   - 点击“加载调用链”和“AI 智能梳理分析”，验证其能否正常拉取和分析 `process` 方法。

2. **方法未找到拦截验证**：
   - 在前端页面将方法名手动修改为不存在的方法名（例如 `nonExistentMethod`）。
   - 点击“AI 智能梳理分析”，验证前端是否快速呈现错误提示，且输出包含了该类中所有可用的候选方法列表（如 `process`），确认大模型没有被请求（可以在控制台观察是否打印了大模型请求日志）。
