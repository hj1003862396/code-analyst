# 2026-06-21-xmind-style-db-analysis-design

## 1. 目标与背景
本项目 `code-analyst` 旨在帮助开发者分析 Java 应用程序的接口方法调用链，并提取链路中对数据库物理表的操作记录。
目前的系统设计存在以下主要痛点：
1. **调用链展示重叠**：前端 D3.js 树图的节点更新生命周期处理不当，导致展开子方法时新旧节点坐标重合。
2. **包含大量噪音**：树图把 `log.info`, `log.error`, `System.out` 等日志和系统操作也当做方法节点展现，使调用链异常臃肿。
3. **AI 依赖重且容易挂起**：使用大模型来解析数据库变更，在大模型服务超时或没有配置 key 时页面一直 loading 转圈，且大模型由于缺乏子调用链和 SQL 上下文，无法给出精准的变更明细。
4. **缺乏深度钻取与直观汇总交互**：无法在顶层方法直接汇总查阅其下级调用的所有表和 CRUD 操作，也无法直接在右侧子方法列表中向下钻取分析。

本设计文档旨在将 AI 梳理模式重构为**基于 JSqlParser 的高性能纯静态分析器**，同步修复 D3.js 的 XMind 折线风格展示、噪声过滤、递归表汇总以及双向联动下钻交互。

---

## 2. 详细设计方案

### 2.1 后端调用过滤与递归静态解析设计

#### A. 无关调用过滤 (`JavaSourceParser.java`)
在解析 Java 源代码提取方法调用链时，对不属于业务方法的调用进行静态过滤：
* **对象名称过滤**：若被调用方法的 Scope 对象名称属于以下列表，则跳过该调用：
  `log`, `logger`, `LOGGER`, `LOG`, `System`, `System.out`, `System.err`, `out`, `err`
* **对象类型过滤**：若解析出变量类型去除泛型后属于以下基础类，则跳过该调用：
  `String`, `List`, `Map`, `Set`, `ArrayList`, `HashMap`, `HashSet`, `Collections`, `Objects`, `Arrays`, `Optional`, `Stream`, `Collectors`, `BigDecimal`, `Integer`, `Long`, `Boolean`, `Double`, `Character`, `ThreadLocal`, `Logger`, `LoggerFactory`

#### B. 结构化数据库操作 (`DbOperation.java`)
升级 `DbOperation` 类以承载结构化的 SQL 信息：
* `tableName` (String): 操作的物理表名。
* `operationType` (String): 操作类型（`SELECT` / `INSERT` / `UPDATE` / `DELETE`）。
* `columns` (List<String>): 被操作的字段列表。
  * `UPDATE`：SET 子句中的更新字段。
  * `INSERT`：被插入的列字段。
  * `SELECT`：查询返回的列。
* `whereCondition` (String): 过滤条件。
* `sql` (String): 对应的原始 SQL 内容。

#### C. SQL 深度静态解析 (`SqlExtractor.java`)
引入 `JSqlParser` 解构 SQL，提取结构化字段：
* 预先清理 XML 标签和占位符（如 `#{{...}}` 替换为 `?`）。
* 将解析到的 `Statement` 转换为具体的 SQL 对象（如 `Update`, `Insert`, `Select`, `Delete`）：
  * `Insert`: 提取表名与 `getColumns()`。
  * `Update`: 提取表名、`getUpdateItems()` 中的列名，以及 `getWhere().toString()`。
  * `Select`: 提取表名、`getSelectItems()`，以及 `getWhere().toString()`。
  * `Delete`: 提取表名，以及 `getWhere().toString()`。

#### D. 递归/传导式汇总算法 (`ApiController.java`)
* 设计递归函数 `List<DbOperation> getTransitiveDbOperations(String projectRoot, String className, String methodName, Set<String> visited)`。
* 执行流程：
  1. 防环路判断：若 `visited` 已包含 `className#methodName`，直接返回空列表。
  2. 获取 `className` 下 `methodName` 的所有过滤后子调用。
  3. 若子调用是 Mapper 方法，直接提取其对应 SQL 并通过 JSqlParser 静态解析为 `DbOperation`，加入结果列表。
  4. 若子调用是 Service 方法，将其转换为对应的实现类 `ServiceImpl` 并进行递归调用。
  5. 合并全部结果并按 `(tableName, operationType, columns, whereCondition)` 属性进行去重。

---

### 2.2 前端 XMind 风格页面与双向联动交互设计

#### A. D3.js 树图重绘与重叠修复 (`index.html`)
* **折线（Elbow）连接线**：采用 `M source.y,source.x L midY,source.x L midY,target.x L target.y,target.x` (其中 `midY = source.y + 40`) 来生成干净的右角弯折效果，类似思维导图。
* **节点样式**：
  * 根节点/普通方法：渲染为圆角矩形，附带背景色（如蓝色 `#3b82f6`），文字垂直居中。
  * 数据库操作/Mapper 方法：渲染为红色/橙色虚线矩形，附带 💾 标识。
* **重叠修复**：利用 D3 v7 的 `.join()` 方法，配合 `.transition().duration(400)` 在 `update` 周期内将现存节点平滑过渡移动到其新计算好的 `(x, y)` 坐标上。

#### B. 右侧详情区与下钻交互 (`index.html`)
* 彻底移除大模型 AI 触发按钮。
* **第一部分：数据库操作汇总**：
  点击/加载任一方法时，右侧顶部立即以结构化表格展示该方法的 `dbOperations` 列表：显示 `物理表` | `操作类型` | `字段明细` | `过滤条件`，点击可折叠查看对应 `SQL 原文`。
* **第二部分：子方法下钻列表**：
  列出该方法的直接子调用列表。
  * 若子调用包含数据库操作，则在其条目下直观显示涉及的表。
  * 每个子方法条目均附带【钻取分析】按钮，点击后自动选中中间树图中对应的节点并向下一层展开，右侧内容同步刷新。

---

## 3. 验证与测试方案
* **测试用例 1：Job 入口方法解析**
  输入类 `com.omp.financial.intf.job.InvoicingApplyJob` 和方法 `process`。
  * 预期结果：不再显示 `log.info`, `log.error` 等噪声节点；能自动递归汇总深层 `InvoiceV2ServiceImpl.applyInvoicing` 所操作的 `t_invoice` 等表信息，且数据瞬时渲染在右侧，无任何转圈卡顿。
* **测试用例 2：双向钻取测试**
  点击右侧 `invoiceService.applyInvoicing` 的“钻取”按钮。
  * 预期结果：D3 树图自动聚焦并平移到该节点；右侧详情页同步展现 `applyInvoicing` 自身的数据库汇总及其底层的 Mapper 子调用。
