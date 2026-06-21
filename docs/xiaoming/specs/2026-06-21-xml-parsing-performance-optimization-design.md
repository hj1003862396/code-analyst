# 规格说明书 (Spec) - XML解析与文件路径检索性能优化

为了解决代码分析系统中在处理 XML Mapper 文件及大量文件检索时出现的长时间卡顿问题，本方案将优化 DTD 实体的联网解析，并对 XML 解析结果与文件路径进行高效的内存缓存管理。

## 1. 目标
* **消除 XML 实体解析挂起**：拦截 `http://mybatis.org` 的 DTD 联网加载，解决网络超时导致的秒级/分钟级挂起问题。
* **降低 I/O 及全盘扫描开销**：用 $O(1)$ 的内存 Map 检索替换频繁的 $O(N)$ `Files.walk` 全盘遍历。
* **保持实时开发体验**：支持检测 XML 文件的最近修改时间（Last Modified），实现热更新；且支持在分析重新初始化时刷新文件树索引。

## 2. 详细设计

### 2.1 外部 DTD 拦截与 XML 缓存 (SqlExtractor.java)
* **EntityResolver 拦截**：向 `DocumentBuilder` 实例注入自定义的 `EntityResolver`。对包含 `.dtd` 或 mybatis.org 的实体请求直接返回空流，避免外部网络连接。
* **文件修改时间校对的缓存**：
  * 定义静态或成员变量 `xmlCache`：`Map<String, XmlSqlCache>`。
  * `XmlSqlCache` 结构包含：`long lastModified` 和 `Map<String, String> methodSqlMap`。
  * 在解析前比对当前文件与缓存中的 `lastModified`。如果一致，直接在内存中返回提取的 SQL；如果不一致，则触发重新解析并更新缓存。

### 2.2 文件检索缓存索引 (ApiController.java)
* **懒加载与按需构建**：
  * 在 `ApiController` 维护两张哈希表：`javaFileByNameCache`（Java 类名与路径映射）和 `xmlFileByNameCache`（XML 文件名与路径映射）。
  * 首次查询或路径切换时，执行一次 `Files.walk` 并填充哈希表。
* **方法重构**：
  * `findXmlFile(root, mapperName)`: 改为直接从 `xmlFileByNameCache` 获取，不再遍历。
  * `findJavaFiles(root, className)`: 改为从 `javaFileByNameCache` 获取。
  * `findJavaFileByFqName(root, fqName)`: 改为利用 `javaFileByNameCache` 获取候选者后再进行包名比对。
* **缓存主动清空**：
  * 在保存配置 `/api/config` 时清空缓存。
  * 在重新初始化树 `/api/tree/initialize` 时清空缓存，确保在 IDE 中新建的类或 XML 能立即参与下一次分析。

## 3. 验证计划
1. **启动测试**：运行应用程序并执行接口 analysis，观察页面展开和分析时的等待时间是否大幅缩短。
2. **离线测试**：拔掉网线或禁用外网，测试接口分析是否依然能在毫秒级响应（确保 DTD 加载已被拦截）。
3. **实时修改测试**：修改一个 XML 文件中的 SQL 语句，不重启服务重新执行分析，确保最新修改的 SQL 能够被实时检测并重新解析。
4. **单元测试**：编写或运行测试用例以验证 `SqlExtractor` 能正确解析带有 DTD 的 XML，并且在文件未改变时走缓存，文件改变时更新缓存。
