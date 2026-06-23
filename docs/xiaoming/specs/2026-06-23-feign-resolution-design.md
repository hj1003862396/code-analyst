# 设计文档 - Feign 接口调用解析与深入查询

## 1. 背景与目的
在当前代码分析系统中，用户在查看方法调用链图时，无法继续往下查询 Feign 接口方法所调用的下游方法。这是因为 Feign 客户端是声明式接口（没有方法体），解析器在遇到 Feign 接口时直接返回了接口本身，而在展开调用树时，解析器无法从接口方法中提取出任何具体的方法调用，导致调用链在 Feign 接口处中断。

为了支持往下查询 Feign 接口方法，我们需要将 Feign 接口解析为它在本地的实现类（通常是微服务中实现了该 Feign 接口的 `RestController` 控制器）。这样在展开节点时，解析器就能解析实现类方法体中的具体调用，使调用链可以继续往下延伸。如果本地没有找到任何实现类，则退回返回 Feign 接口本身（此时作为终点，不再往下调用）。

## 2. 方案详述
修改 `ApiController.java` 中的 `resolveImplementation` 方法：
- 识别 FeignClient：利用 AST 检查接口类上是否带有 `@FeignClient` 注解。
- 搜索本地实现：如果带有该注解，不直接返回接口 FQCN。而是依次通过：
  1. 启发式命名匹配（如 `xxxImpl`）；
  2. 实现了该接口的本地类搜索（即 `findImplementationBySearch`）。
- 确定返回：如果找到本地实现类（例如实现了 `DeviceFeign` 的 `ChargeRpcController`），返回实现类的 FQCN。如果均未找到，则退回返回该 Feign 接口自身的 FQCN。

## 3. 影响范围
- 类匹配解析逻辑：`ApiController.resolveImplementation`
- 系统测试：`ApiControllerTest`

## 4. 验证方法
在 `ApiControllerTest` 中，以 `InvoiceRpcFeign` 接口为输入测试解析，期望结果为 `com.omp.finance.intf.rpc.invoice.InvoiceRpcController`。
