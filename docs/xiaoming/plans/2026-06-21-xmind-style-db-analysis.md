# XMind 风格数据库静态分析重构 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 实现 code-analyst 中对方法调用链的噪声过滤、JSqlParser 深度静态解析、递归表操作汇总，以及前端 XMind 风格的圆角折线 D3 树图和右侧下钻分析面板。

**架构：**
1. 后端升级 `DbOperation` 并配合 `JSqlParser` 静态解析 SQL 获得 Columns、Where 条件和 SQL 源码。
2. 后端对方法调用链进行递归深度优先搜索（DFS）汇总所有下游的 `DbOperation`，附带环路检测。
3. 后端在 `JavaSourceParser` 中过滤日志类与 JDK 常用类等无意义的噪声调用。
4. 前端使用 D3 v7 `.join()` 周期机制重构树图，消除重叠，采用 Elbow 右角弯折连接线，并重构右侧详情面板用于显示结构化 SQL 表信息与子方法下钻。

**技术栈：** Java, Spring Boot, JSqlParser, D3.js (v7), Vue 3, Element Plus.

---

### Task 1：升级数据库操作实体类 `DbOperation`

**文件：**
* 修改：`src/main/java/com/codedb/analyst/parser/DbOperation.java`

- [ ] **步骤 1：为 DbOperation 增加 SQL 结构化属性、构造函数以及 Getter/Setter 方法**
  在 `DbOperation` 中加入 `columns`, `whereCondition`, `sql` 字段：
  ```java
  package com.code.analyst.parser;

  import java.util.ArrayList;
  import java.util.List;

  public class DbOperation {
      private String tableName;
      private String operationType;
      private List<String> columns;
      private String whereCondition;
      private String sql;

      public DbOperation(String tableName, String operationType) {
          this.tableName = tableName;
          this.operationType = operationType;
          this.columns = new ArrayList<>();
          this.whereCondition = "";
          this.sql = "";
      }

      public DbOperation(String tableName, String operationType, List<String> columns, String whereCondition, String sql) {
          this.tableName = tableName;
          this.operationType = operationType;
          this.columns = columns;
          this.whereCondition = whereCondition;
          this.sql = sql;
      }

      public String getTableName() {
          return tableName;
      }

      public void setTableName(String tableName) {
          this.tableName = tableName;
      }

      public String getOperationType() {
          return operationType;
      }

      public void setOperationType(String operationType) {
          this.operationType = operationType;
      }

      public List<String> getColumns() {
          return columns;
      }

      public void setColumns(List<String> columns) {
          this.columns = columns;
      }

      public String getWhereCondition() {
          return whereCondition;
      }

      public void setWhereCondition(String whereCondition) {
          this.whereCondition = whereCondition;
      }

      public String getSql() {
          return sql;
      }

      public void setSql(String sql) {
          this.sql = sql;
      }
  }
  ```

- [ ] **步骤 2：编译项目以验证代码无语法错误**
  运行：`mvn clean compile`
  预期：输出 `BUILD SUCCESS`

---

### Task 2：基于 JSqlParser 的 SQL 结构化解析

**文件：**
* 修改：`src/main/java/com/codedb/analyst/parser/SqlExtractor.java`

- [ ] **步骤 1：升级 extractDbOperations 方法，支持对 Select/Insert/Update/Delete 语句的结构化字段提取**
  在 `SqlExtractor.java` 中替换原 `extractDbOperations` 方法：
  ```java
      public List<DbOperation> extractDbOperations(String sql) {
          if (sql == null || sql.trim().isEmpty()) {
              return java.util.Collections.emptyList();
          }
          try {
              // 清理 XML 标签和 MyBatis 变量占位符
              String cleanSql = sql.replaceAll("<[^>]+>", " ")
                      .replaceAll("#\\{[^\\}]+\\}", "?")
                      .replaceAll("\\$\\{[^\\}]+\\}", "?")
                      .trim();

              net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(cleanSql);
              String type = "UNKNOWN";
              List<String> columns = new ArrayList<>();
              String where = "";

              if (stmt instanceof net.sf.jsqlparser.statement.select.Select) {
                  type = "SELECT";
                  net.sf.jsqlparser.statement.select.Select select = (net.sf.jsqlparser.statement.select.Select) stmt;
                  if (select.getSelectBody() instanceof net.sf.jsqlparser.statement.select.PlainSelect) {
                      net.sf.jsqlparser.statement.select.PlainSelect plain = (net.sf.jsqlparser.statement.select.PlainSelect) select.getSelectBody();
                      if (plain.getWhere() != null) {
                          where = plain.getWhere().toString();
                      }
                      if (plain.getSelectItems() != null) {
                          for (net.sf.jsqlparser.statement.select.SelectItem item : plain.getSelectItems()) {
                              columns.add(item.toString());
                          }
                      }
                  }
              } else if (stmt instanceof net.sf.jsqlparser.statement.insert.Insert) {
                  type = "INSERT";
                  net.sf.jsqlparser.statement.insert.Insert insert = (net.sf.jsqlparser.statement.insert.Insert) stmt;
                  if (insert.getColumns() != null) {
                      for (net.sf.jsqlparser.schema.Column col : insert.getColumns()) {
                          columns.add(col.getColumnName());
                      }
                  }
              } else if (stmt instanceof net.sf.jsqlparser.statement.update.Update) {
                  type = "UPDATE";
                  net.sf.jsqlparser.statement.update.Update update = (net.sf.jsqlparser.statement.update.Update) stmt;
                  if (update.getUpdateItems() != null) {
                      for (net.sf.jsqlparser.statement.update.UpdateItem item : update.getUpdateItems()) {
                          for (net.sf.jsqlparser.schema.Column col : item.getColumns()) {
                              columns.add(col.getColumnName());
                          }
                      }
                  }
                  if (update.getWhere() != null) {
                      where = update.getWhere().toString();
                  }
              } else if (stmt instanceof net.sf.jsqlparser.statement.delete.Delete) {
                  type = "DELETE";
                  net.sf.jsqlparser.statement.delete.Delete delete = (net.sf.jsqlparser.statement.delete.Delete) stmt;
                  if (delete.getWhere() != null) {
                      where = delete.getWhere().toString();
                  }
              }

              TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
              List<String> tables = tablesNamesFinder.getTableList(stmt);
              List<DbOperation> ops = new ArrayList<>();
              for (String t : tables) {
                  ops.add(new DbOperation(t.replace("`", ""), type, columns, where, sql));
              }
              return ops;
          } catch (Exception e) {
              // 容错处理
              List<DbOperation> fallback = new ArrayList<>();
              String lower = sql.toLowerCase();
              if (lower.contains("select") && lower.contains("from")) {
                  fallback.add(new DbOperation("unknown_table", "SELECT", java.util.Collections.emptyList(), "", sql));
              } else if (lower.contains("insert") && lower.contains("into")) {
                  fallback.add(new DbOperation("unknown_table", "INSERT", java.util.Collections.emptyList(), "", sql));
              } else if (lower.contains("update")) {
                  fallback.add(new DbOperation("unknown_table", "UPDATE", java.util.Collections.emptyList(), "", sql));
              } else if (lower.contains("delete") && lower.contains("from")) {
                  fallback.add(new DbOperation("unknown_table", "DELETE", java.util.Collections.emptyList(), "", sql));
              }
              return fallback;
          }
      }
  ```

- [ ] **步骤 2：编译项目以确保 JSqlParser 解析代码正常**
  运行：`mvn compile`
  预期：输出 `BUILD SUCCESS`

---

### Task 3：调用噪声静态过滤

**文件：**
* 修改：`src/main/java/com/codedb/analyst/parser/JavaSourceParser.java`

- [ ] **步骤 1：增加 `isIgnoredCall` 过滤辅助方法，并在 `parseMethodCalls` 遍历 AST 节点时调用该方法过滤噪声**
  在 `JavaSourceParser.java` 中增加方法并在 `visit(MethodCallExpr n, Void arg)` 中集成：
  ```java
      private boolean isIgnoredCall(String objectName, String objectType, String methodName) {
          if (objectName == null) return false;
          String lowerName = objectName.toLowerCase();
          
          // 过滤日志和标准流
          if (lowerName.equals("log") || lowerName.equals("logger") || lowerName.equals("system.out") || lowerName.equals("system.err") || lowerName.equals("out") || lowerName.equals("err")) {
              return true;
          }
          
          // 过滤标准 JDK 类型调用
          if (objectType != null) {
              String cleanType = objectType.replaceAll("<.*>", "");
              if (cleanType.equals("String") || cleanType.equals("List") || cleanType.equals("Map") || cleanType.equals("Set") 
                      || cleanType.equals("ArrayList") || cleanType.equals("HashMap") || cleanType.equals("HashSet") 
                      || cleanType.equals("Collections") || cleanType.equals("Objects") || cleanType.equals("Arrays") 
                      || cleanType.equals("Optional") || cleanType.equals("Stream") || cleanType.equals("Collectors") 
                      || cleanType.equals("Logger") || cleanType.equals("LoggerFactory") || cleanType.equals("System")
                      || cleanType.equals("BigDecimal") || cleanType.equals("Integer") || cleanType.equals("Long") 
                      || cleanType.equals("Boolean") || cleanType.equals("Double") || cleanType.equals("Character")) {
                  return true;
              }
          }
          
          return false;
      }
  ```
  在 `parseMethodCalls` 的方法主体中，将第 52-60 行：
  ```java
                          @Override
                          public void visit(MethodCallExpr n, Void arg) {
                              super.visit(n, arg);
                              n.getScope().ifPresent(scope -> {
                                  String objectName = scope.toString();
                                  String callMethod = n.getNameAsString();
                                  String objectType = localTypes.getOrDefault(objectName, "Unknown");
                                  calls.add(new MethodCallInfo(objectName, callMethod, objectType));
                              });
                          }
  ```
  替换为：
  ```java
                          @Override
                          public void visit(MethodCallExpr n, Void arg) {
                              super.visit(n, arg);
                              n.getScope().ifPresent(scope -> {
                                  String objectName = scope.toString();
                                  String callMethod = n.getNameAsString();
                                  String objectType = localTypes.getOrDefault(objectName, "Unknown");
                                  if (isIgnoredCall(objectName, objectType, callMethod)) {
                                      return;
                                  }
                                  calls.add(new MethodCallInfo(objectName, callMethod, objectType));
                              });
                          }
  ```

- [ ] **步骤 2：编译项目，检查语法无误**
  运行：`mvn compile`
  预期：输出 `BUILD SUCCESS`

---

### Task 4：递归调用链表操作分析与接口升级

**文件：**
* 修改：`src/main/java/com/codedb/analyst/web/ApiController.java`

- [ ] **步骤 1：增加递归函数 `getTransitiveDbOperations`，实现 DFS 获取节点及其所有下游的所有去重物理表操作**
  在 `ApiController.java` 类体中加入以下辅助和核心方法：
  ```java
      private List<com.code.analyst.parser.DbOperation> getTransitiveDbOperations(String root, String className, String methodName, Set<String> visited) {
          String key = className + "#" + methodName;
          if (visited.contains(key)) {
              return Collections.emptyList();
          }
          visited.add(key);

          List<com.code.analyst.parser.DbOperation> ops = new ArrayList<>();
          
          String simpleName = className.substring(className.lastIndexOf('.') + 1);
          Optional<Path> fileOpt = findJavaFile(root, simpleName);
          if (fileOpt.isEmpty()) {
              return ops;
          }

          try {
              List<com.code.analyst.parser.MethodCallInfo> calls = sourceParser.parseMethodCalls(fileOpt.get().toString(), methodName);
              for (com.code.analyst.parser.MethodCallInfo call : calls) {
                  String type = call.getObjectType();
                  String method = call.getMethodName();
                  
                  boolean isMapper = type.toLowerCase().contains("mapper") || call.getObjectName().toLowerCase().contains("mapper");

                  if ("Unknown".equals(type)) {
                      if (call.getObjectName().toLowerCase().contains("service")) {
                          type = uppercaseFirst(call.getObjectName());
                      } else if (call.getObjectName().toLowerCase().contains("mapper")) {
                          type = uppercaseFirst(call.getObjectName());
                      }
                  }

                  if (isMapper) {
                      List<com.code.analyst.parser.DbOperation> dbOps = new ArrayList<>();
                      Optional<Path> xmlOpt = findXmlFile(root, type);
                      if (xmlOpt.isPresent()) {
                          String sql = sqlExtractor.findSqlFromXml(xmlOpt.get().toString(), method);
                          dbOps = sqlExtractor.extractDbOperations(sql);
                      }
                      
                      if (dbOps.isEmpty() && (method.equals("insert") || method.equals("selectById") || method.equals("updateById") || method.equals("deleteById") || method.equals("selectList") || method.equals("delete"))) {
                          String tableName = "t_" + camelToSnake(type.replace("Mapper", "").replace("mapper", ""));
                          String opType = "UNKNOWN";
                          if (method.contains("select")) opType = "SELECT";
                          else if (method.contains("insert")) opType = "INSERT";
                          else if (method.contains("update")) opType = "UPDATE";
                          else if (method.contains("delete")) opType = "DELETE";
                          dbOps = List.of(new com.code.analyst.parser.DbOperation(tableName, opType, Collections.emptyList(), "", ""));
                      }
                      ops.addAll(dbOps);
                  } else {
                      String fullTargetClassName = resolveFullClassName(root, type);
                      if (fullTargetClassName.contains("Service") && !fullTargetClassName.contains("ServiceImpl")) {
                          String implClass = type + "Impl";
                          Optional<Path> implPath = findJavaFile(root, implClass);
                          if (implPath.isPresent()) {
                              type = implClass;
                              fullTargetClassName = resolveFullClassName(root, implClass);
                          }
                      }
                      ops.addAll(getTransitiveDbOperations(root, fullTargetClassName, method, visited));
                  }
              }
          } catch (Exception e) {
              e.printStackTrace();
          }

          // 按字段属性去重
          Map<String, com.code.analyst.parser.DbOperation> dedup = new HashMap<>();
          for (com.code.analyst.parser.DbOperation op : ops) {
              String opKey = op.getTableName() + "#" + op.getOperationType() + "#" + String.join(",", op.getColumns()) + "#" + op.getWhereCondition();
              dedup.put(opKey, op);
          }
          return new ArrayList<>(dedup.values());
      }
  ```

- [ ] **步骤 2：更新 `/api/tree/initialize` 和 `/api/tree/expand` 两个接口以集成 `getTransitiveDbOperations` 分析能力**
  在 `ApiController.java` 中修改 `/api/tree/initialize` 方法体：
  ```java
      @PostMapping("/tree/initialize")
      public ResponseEntity<Map<String, Object>> initializeTree(@RequestBody Map<String, String> request) {
          String className = request.get("className");
          String methodName = request.get("methodName");

          Map<String, Object> node = new HashMap<>();
          node.put("id", className + "#" + methodName);
          node.put("label", className.substring(className.lastIndexOf('.') + 1) + "." + methodName);
          node.put("className", className);
          node.put("methodName", methodName);
          node.put("isMapper", false);
          
          AppConfig config = configManager.getConfig();
          String root = config.getProjectRoot();
          List<com.code.analyst.parser.DbOperation> dbOps = Collections.emptyList();
          if (root != null && !root.isEmpty()) {
              dbOps = getTransitiveDbOperations(root, className, methodName, new HashSet<>());
          }
          node.put("dbOperations", dbOps);
          return ResponseEntity.ok(node);
      }
  ```
  修改 `/api/tree/expand` 方法体：
  ```java
      @PostMapping("/tree/expand")
      public ResponseEntity<List<Map<String, Object>>> expandTree(@RequestBody Map<String, String> request) {
          String className = request.get("className");
          String methodName = request.get("methodName");
          List<Map<String, Object>> result = new ArrayList<>();

          try {
              AppConfig config = configManager.getConfig();
              String root = config.getProjectRoot();
              if (root == null || root.isEmpty()) {
                  return ResponseEntity.badRequest().build();
              }

              String simpleName = className.substring(className.lastIndexOf('.') + 1);
              Optional<Path> fileOpt = findJavaFile(root, simpleName);
              if (fileOpt.isEmpty()) {
                  return ResponseEntity.ok(result);
              }

              String filePath = fileOpt.get().toString();
              List<com.code.analyst.parser.MethodCallInfo> calls = sourceParser.parseMethodCalls(filePath, methodName);
              for (com.code.analyst.parser.MethodCallInfo call : calls) {
                  Map<String, Object> node = new HashMap<>();
                  String type = call.getObjectType();
                  String method = call.getMethodName();

                  boolean isMapper = type.toLowerCase().contains("mapper") || call.getObjectName().toLowerCase().contains("mapper");
                  
                  if ("Unknown".equals(type)) {
                      if (call.getObjectName().toLowerCase().contains("service")) {
                          type = uppercaseFirst(call.getObjectName());
                      } else if (call.getObjectName().toLowerCase().contains("mapper")) {
                          type = uppercaseFirst(call.getObjectName());
                      }
                  }

                  List<com.code.analyst.parser.DbOperation> dbOps = new ArrayList<>();
                  if (isMapper) {
                      Optional<Path> xmlOpt = findXmlFile(root, type);
                      if (xmlOpt.isPresent()) {
                          String sql = sqlExtractor.findSqlFromXml(xmlOpt.get().toString(), method);
                          dbOps = sqlExtractor.extractDbOperations(sql);
                      }
                      
                      if (dbOps.isEmpty() && (method.equals("insert") || method.equals("selectById") || method.equals("updateById") || method.equals("deleteById") || method.equals("selectList") || method.equals("delete"))) {
                          String tableName = "t_" + camelToSnake(type.replace("Mapper", "").replace("mapper", ""));
                          String opType = "UNKNOWN";
                          if (method.contains("select")) opType = "SELECT";
                          else if (method.contains("insert")) opType = "INSERT";
                          else if (method.contains("update")) opType = "UPDATE";
                          else if (method.contains("delete")) opType = "DELETE";
                          dbOps = List.of(new com.code.analyst.parser.DbOperation(tableName, opType, Collections.emptyList(), "", ""));
                      }
                  } else {
                      String fullTargetClassName = resolveFullClassName(root, type);
                      if (fullTargetClassName.contains("Service") && !fullTargetClassName.contains("ServiceImpl")) {
                          String implClass = type + "Impl";
                          Optional<Path> implPath = findJavaFile(root, implClass);
                          if (implPath.isPresent()) {
                              type = implClass;
                              fullTargetClassName = resolveFullClassName(root, implClass);
                          }
                      }
                      dbOps = getTransitiveDbOperations(root, fullTargetClassName, method, new HashSet<>());
                  }

                  String fullTargetClassName = resolveFullClassName(root, type);
                  if (fullTargetClassName.contains("Service") && !fullTargetClassName.contains("ServiceImpl")) {
                      String implClass = type + "Impl";
                      Optional<Path> implPath = findJavaFile(root, implClass);
                      if (implPath.isPresent()) {
                          type = implClass;
                          fullTargetClassName = resolveFullClassName(root, implClass);
                      }
                  }

                  node.put("id", fullTargetClassName + "#" + method);
                  node.put("label", type + "." + method);
                  node.put("className", fullTargetClassName);
                  node.put("methodName", method);
                  node.put("isMapper", isMapper);
                  node.put("dbOperations", dbOps);
                  result.add(node);
              }
          } catch (Exception e) {
              e.printStackTrace();
          }

          return ResponseEntity.ok(result);
      }
  ```

- [ ] **步骤 3：编译打包后端代码，确认无误**
  运行：`mvn package -DskipTests`
  预期：输出 `BUILD SUCCESS`

---

### Task 5：前端页面 XMind 重构与双向联动交互设计

**文件：**
* 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：修改 CSS 并在 `index.html` 的 UI 布局中应用新详情面板结构，移除 AI 按钮**
  在 `index.html` 中：
  1. 将 `.node circle` 改为 `.node rect` 的对应样式：
     ```css
             .node rect {
                 fill: #1E293B;
                 stroke-width: 2px;
                 cursor: pointer;
                 transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
             }

             .node:hover rect {
                 filter: drop-shadow(0 0 8px var(--primary));
             }
     ```
  2. 替换 `<div class="right-panel">` 模块，改用纯静态多面板展示：
     ```html
             <!-- 右侧详情/静态梳理区域 -->
             <div class="right-panel">
                 <div class="panel-title">📝 节点详细分析</div>
                 <div v-if="selectedNode">
                     <el-card style="background: rgba(255, 255, 255, 0.02); border: 1px solid var(--border-color); margin-bottom: 20px;">
                         <div style="font-size: 15px; font-weight: 600; margin-bottom: 10px;">
                             {{ selectedNode.data.methodName }}()
                         </div>
                         <div style="font-size: 13px; color: var(--text-muted); word-break: break-all; font-family: var(--font-mono);">
                             {{ selectedNode.data.className }}
                         </div>
                     </el-card>

                     <!-- 1. 数据库操作汇总 -->
                     <div style="margin-bottom: 20px;">
                         <div style="font-size: 14px; font-weight: 600; margin-bottom: 10px; color: var(--danger)">
                             💾 数据库操作汇总 ({{ selectedNode.data.dbOperations ? selectedNode.data.dbOperations.length : 0 }})
                         </div>
                         
                         <div v-if="selectedNode.data.dbOperations && selectedNode.data.dbOperations.length">
                             <el-collapse accordion>
                                 <el-collapse-item v-for="(op, idx) in selectedNode.data.dbOperations" :key="idx" :name="idx">
                                     <template #title>
                                         <span class="db-tag" style="margin-right: 10px;">
                                             <strong>{{ op.operationType }}</strong> {{ op.tableName }}
                                         </span>
                                     </template>
                                     <div style="font-size: 13px; padding: 12px; background: rgba(0,0,0,0.2); border-radius: 6px; border: 1px solid rgba(255,255,255,0.05); line-height: 1.6;">
                                         <div style="margin-bottom: 6px;" v-if="op.columns && op.columns.length">
                                             <strong style="color: var(--primary);">操作字段:</strong> 
                                             <code style="word-break: break-all; color: var(--secondary);">{{ op.columns.join(', ') }}</code>
                                         </div>
                                         <div style="margin-bottom: 6px;" v-if="op.whereCondition">
                                             <strong style="color: var(--primary);">条件 (WHERE):</strong> 
                                             <code style="word-break: break-all; color: var(--success);">{{ op.whereCondition }}</code>
                                         </div>
                                         <div v-if="op.sql">
                                             <strong style="color: var(--primary);">SQL 语句:</strong>
                                             <pre style="margin-top: 6px; font-size: 12px; max-height: 150px; overflow-y: auto; background: #070a13; padding: 10px; border-radius: 4px; color: #f3f4f6; font-family: var(--font-mono); border: 1px solid rgba(255,255,255,0.03);"><code>{{ op.sql }}</code></pre>
                                         </div>
                                     </div>
                                 </el-collapse-item>
                             </el-collapse>
                         </div>
                         <div v-else style="font-size: 13px; color: var(--text-muted); padding: 15px; border: 1px dashed var(--border-color); border-radius: 6px; text-align: center;">
                             暂无数据库操作（该方法及其下游子调用未操作数据库）
                         </div>
                     </div>

                     <!-- 2. 子方法下钻列表 -->
                     <div style="margin-bottom: 20px;">
                         <div style="font-size: 14px; font-weight: 600; margin-bottom: 10px; color: var(--primary)">
                             🔗 子方法调用
                         </div>
                         
                         <div v-if="selectedNode.children && selectedNode.children.length">
                             <div v-for="child in selectedNode.children" :key="child.data.id" style="padding: 12px; background: rgba(255,255,255,0.02); border: 1px solid var(--border-color); border-radius: 8px; margin-bottom: 8px; display: flex; justify-content: space-between; align-items: center;">
                                 <div>
                                     <div style="font-size: 13px; font-weight: 500; color: var(--text-main);">
                                         {{ child.data.label }}
                                     </div>
                                     <div style="font-size: 11px; color: var(--text-muted); word-break: break-all; font-family: var(--font-mono); margin-top: 6px;" v-if="child.data.dbOperations && child.data.dbOperations.length">
                                         💾 操作表: <span v-for="op in child.data.dbOperations" :key="op.tableName" style="margin-right: 6px; color: var(--danger); font-weight: 600;">{{ op.tableName }}</span>
                                     </div>
                                 </div>
                                 <el-button size="small" type="primary" link @click="selectNode(child)">钻取分析</el-button>
                             </div>
                         </div>
                         <div v-else style="font-size: 13px; color: var(--text-muted); padding: 15px; border: 1px dashed var(--border-color); border-radius: 6px; text-align: center;">
                             未调用下级方法
                         </div>
                     </div>
                 </div>
                 <div v-else>
                     <el-empty description="在中间的树图上，点击任意一个方法节点以开始分析" style="padding-top: 80px;"></el-empty>
                 </div>
             </div>
     ```

- [ ] **步骤 2：重写 `index.html` 中的 JS 脚本，实现 XMind 右角圆角折线绘制、D3 坐标平滑过渡、以及联动选中**
  在 `<script>` 中替换 JS 主体：
  ```javascript
      const { createApp, ref, computed } = Vue;

      createApp({
          setup() {
              const entry = ref({
                  className: 'com.omp.financial.intf.job.InvoicingApplyJob',
                  methodName: 'process'
              });

              const selectedNode = ref(null);

              let svg, g, tree, rootData;

              const selectNode = async (d3Node) => {
                  selectedNode.value = d3Node;

                  // 自动展开到该节点的路径
                  let curr = d3Node;
                  while (curr.parent) {
                      curr.parent.children = curr.parent.children || [];
                      curr = curr.parent;
                  }

                  // 增量加载子节点
                  if (!d3Node.children && !d3Node.data.isMapper) {
                      try {
                          const res = await fetch('/api/tree/expand', {
                              method: 'POST',
                              headers: { 'Content-Type': 'application/json' },
                              body: JSON.stringify({
                                  className: d3Node.data.className,
                                  methodName: d3Node.data.methodName
                              })
                          });
                          if (res.ok) {
                              const children = await res.json();
                              if (children && children.length) {
                                  d3Node.children = children.map(c => d3.hierarchy(c));
                                  d3Node.children.forEach(c => c.parent = d3Node);
                                  updateTree(d3Node);
                              }
                          }
                      } catch (e) {
                          ElementPlus.ElMessage.error('展开子节点失败：' + e.message);
                      }
                  }
              };

              const initTree = async () => {
                  try {
                      const res = await fetch('/api/tree/initialize', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify(entry.value)
                      });
                      if (!res.ok) {
                          ElementPlus.ElMessage.error('初始化失败，请检查后端状态。');
                          return;
                      }
                      const data = await res.json();
                      rootData = d3.hierarchy(data);
                      renderD3Tree();
                      // 初始化时默认选中根节点
                      await selectNode(rootData);
                  } catch (e) {
                      ElementPlus.ElMessage.error('无法初始化调用链：' + e.message);
                  }
              };

              const renderD3Tree = () => {
                  d3.select("#tree-svg").selectAll("*").remove();

                  svg = d3.select("#tree-svg")
                      .call(d3.zoom().on("zoom", (event) => {
                          g.attr("transform", event.transform);
                      }));

                  g = svg.append("g").attr("transform", "translate(100, 300)");

                  tree = d3.tree().nodeSize([60, 280]);
                  updateTree(rootData);
              };

              const updateTree = (source) => {
                  const nodes = tree(rootData).descendants();
                  const links = rootData.links();

                  // Elbow 右角折线连接线渲染
                  g.selectAll(".link")
                      .data(links, d => d.target.data.id)
                      .join(
                          enter => enter.append("path")
                              .attr("class", "link")
                              .attr("d", d => {
                                  const midY = d.source.y + 40;
                                  return `M ${d.source.y} ${d.source.x} L ${midY} ${d.source.x} L ${midY} ${d.target.x} L ${d.target.y} ${d.target.x}`;
                              }),
                          update => update.transition().duration(400)
                              .attr("d", d => {
                                  const midY = d.source.y + 40;
                                  return `M ${d.source.y} ${d.source.x} L ${midY} ${d.source.x} L ${midY} ${d.target.x} L ${d.target.y} ${d.target.x}`;
                              }),
                          exit => exit.remove()
                      );

                  // 节点渲染
                  const node = g.selectAll(".node")
                      .data(nodes, d => d.data.id)
                      .join(
                          enter => {
                              const nodeG = enter.append("g")
                                  .attr("class", "node")
                                  .attr("transform", d => `translate(${d.y}, ${d.x})`)
                                  .on("click", (event, d) => selectNode(d));

                              nodeG.append("rect")
                                  .attr("rx", 6)
                                  .attr("ry", 6)
                                  .attr("y", -16)
                                  .attr("x", -10)
                                  .attr("height", 32)
                                  .attr("width", 1)
                                  .style("stroke-width", "2px")
                                  .style("fill", "#0f172a");

                              nodeG.append("text")
                                  .attr("dy", ".35em")
                                  .style("fill", "var(--text-main)")
                                  .style("font-size", "12px")
                                  .style("font-family", "var(--font-sans)");

                              return nodeG;
                          },
                          update => {
                              update.transition().duration(400)
                                  .attr("transform", d => `translate(${d.y}, ${d.x})`);

                              update.each(function(d) {
                                  const nodeG = d3.select(this);
                                  const text = nodeG.select("text");
                                  const labelText = d.data.isMapper ? `💾 ${d.data.label}` : d.data.label;
                                  text.text(labelText);

                                  const bbox = text.node().getBBox();
                                  const rect = nodeG.select("rect");
                                  rect.attr("width", bbox.width + 20)
                                      .style("stroke", d.data.isMapper ? "var(--danger)" : "var(--primary)")
                                      .style("stroke-dasharray", d.data.isMapper ? "4,4" : "none");
                              });

                              return update;
                          },
                          exit => exit.remove()
                      );
              };

              return {
                  entry,
                  selectedNode,
                  initTree,
                  selectNode
              };
          }
      }).use(ElementPlus).mount('#app');
  ```

---
