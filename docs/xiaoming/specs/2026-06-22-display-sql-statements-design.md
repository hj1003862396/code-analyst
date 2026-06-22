# 2026-06-22 Display SQL Statements Design

This document details the design for parsing and displaying database SQL statements in the code analysis tool. It includes:
1. Backend changes to extract SQL from MyBatis XML `<include>` tags and dynamic MyBatis-Plus reflection annotations.
2. Frontend changes to support dynamic lazy loading of the calling tree on the mind map.
3. Rendering SQL statements directly as child nodes in the mind map.
4. Removing the redundant right-hand detail drawer.

## Background

In the current code-analysis platform:
1. When mapping database calls, MyBatis XML mapper files frequently use `<include refid="..."/>` fragments to define columns. Since the parser previously called `el.getTextContent()`, it stripped these tags and left blank SELECT items (e.g. `SELECT  FROM table`), causing JSqlParser to fail and return `unknown_table` with incomplete SQL.
2. For default MyBatis-Plus dynamic CRUD methods (such as `selectById`, `insert`), there is no XML SQL defined. The fallback path mapped the operation but left the SQL string empty, making it invisible to the user.
3. The UI automatically loaded the entire tree and selected the root node, but clicking sub-nodes did not automatically expand them in the mind map; the user was forced to click "钻取分析" (drill down analysis) in a heavy right-hand drawer that slid out.

---

## Proposed Changes

### 1. XML SQL Parser (`SqlExtractor.java`)

We will update [SqlExtractor.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/parser/SqlExtractor.java):
* Implement recursive XML node text parsing to handle `<include>` tags recursively.
* Improve fallback parsing heuristic in `extractDbOperations`:
  If JSqlParser fails to parse the SQL (e.g. due to MyBatis XML elements like `<if>` or `<where>`), we extract the table name using case-insensitive regex patterns:
  - `SELECT`: `from\\s+([a-zA-Z0-9_\\\`\\.]+)`
  - `INSERT`: `insert\\s+into\\s+([a-zA-Z0-9_\\\`\\.]+)`
  - `UPDATE`: `update\\s+([a-zA-Z0-9_\\\`\\.]+)`
  - `DELETE`: `delete\\s+from\\s+([a-zA-Z0-9_\\\`\\.]+)`

### 2. MyBatis-Plus Table Name & Mock SQL Resolution (`ApiController.java`)

We will update [ApiController.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/web/ApiController.java):
* **Mapper and Entity Analysis**:
  Add `getTableNameFromMapper(String root, String fullMapperClassName)` to find the generic argument of `BaseMapper<Entity>` in the Mapper interface and parse that Entity's source file to extract the `@TableName` value.
* **Fallback CRUD DB Operation Generation**:
  Add `getFallbackDbOps(String root, String type, String fullTargetClassName, String method)` to generate mock SQL (e.g. `SELECT * FROM table WHERE id = ?`) when the mapper is calling standard built-in CRUD operations and no XML mapping is found.
* **Integrate Fallback in `expandTree` & `getTransitiveDbOperations`**:
  Call `getFallbackDbOps` to populate `dbOps` if XML parsing yields no results.

### 3. Frontend Mind Map Lazy Loading & SQL Node Rendering (`index.js` & `index.html`)

We will update [index.js](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.js) and [index.html](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/resources/static/index.html):
* **Remove the Right-Hand Detail Drawer**:
  - Remove `<div class="right-detail-drawer">` and `<div class="right-trigger">` from `index.html`.
  - Remove all Vue reactive variables for the drawer (e.g. `selectedNode`, `rightDrawerCollapsed`) and the `selectNodeFromList` function from `index.js`.
* **Implement Lazy Loading on "+" Button Click**:
  - Initially, the root node is rendered collapsed (`expand: false`) and has a dummy child node `加载中...` (`isPlaceholder: true`) so simple-mind-map renders a `+` button.
  - Listen to the `expand_btn_click` event of the mind map.
  - When a node is expanded, check if it has the placeholder child. If yes, fetch its child method calls using `/api/tree/expand`.
  - On success, replace the placeholder, update the tree data, and set `expand: true` for the parent.
  - Call `mindMap.setData(mapData)` and programmatically reactivate and focus the clicked node to keep it highlighted.
  - Maintain a helper function `syncExpandState` to keep the user's manual expand/collapse states of other nodes synchronized in our data structure.
* **Render SQL Statements as Child Nodes**:
  - For Mapper nodes (`isMapper: true`), do not request backend endpoints on expansion. Instead, map the predefined `dbOperations` directly to child nodes where the node text is the SQL statement itself.
  - Since these SQL nodes have actual children (pre-populated) and `expand` defaults to `false`, the Mapper node will display a `+` button, and clicking it will expand the SQL statement node(s) instantly in the UI.

---

## Verification Plan

### Automated Tests
* Run `JAVA_HOME=/Users/hanjie/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home mvn test` to verify that existing XML include parsing and reflection SQL mock tests pass.

### Manual Verification
* Run the Spring Boot application using Maven.
* Open the browser at `http://localhost:8080/index.html`.
* Load a method analysis tree. Verify that:
  - The root node starts collapsed with a `+` button.
  - Clicking `+` loads children dynamically from the backend and expands them, keeping the selected node focused.
  - Mapper nodes can be expanded to show the actual SQL statements as child nodes directly in the mind map.
  - The right drawer is completely removed and the UI is clean and distraction-free.
