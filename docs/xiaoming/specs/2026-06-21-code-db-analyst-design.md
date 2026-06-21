# 接口调用链与数据库操作智能梳理工具 (code-db-analyst) 设计规格说明书

本规格说明书详细阐述了 `code-db-analyst` 工具的设计方案。该工具旨在通过解析 Java 抽象语法树 (AST) 与 SQL 语句，结合大语言模型 (LLM)，以可视化交互树的方式智能梳理接口调用链及数据库表操作。

---

## 1. 核心需求与设计目标

1. **输入与定位**：用户配置目标 Java 项目根路径，输入指定的 Java 类名和方法名作为分析入口。
2. **免编译实时刷新**：代码修改后实时生效，无需对目标 Java 项目进行编译或打包。
3. **调用链增量展开**：网页端提供树状图展示调用关系。默认仅展示入口方法，点击节点时动态加载并渲染下一级方法调用。
4. **数据库与 SQL 关联解析**：对于自定义 MyBatis Mapper SQL（解析 XML）和 MyBatis-Plus 通用 CRUD（基于实体注解），自动提取出物理表名与操作类型（SELECT/INSERT/UPDATE/DELETE）。
5. **AI 智能梳理**：调用 LLM 对“方法源码 + 关联 SQL”进行微上下文的高精准分析，生成结构化业务逻辑和数据表变更说明。

---

## 2. 系统整体架构与模块设计

系统采用前后端分离但集成运行的轻量级模式，后端提供 Web 服务，前端页面打包后嵌入后端中，最终交付为单个可执行的 JAR 包。

```mermaid
graph TD
    subgraph Frontend (Vue 3 + Element Plus)
        UI[网页端树状图 D3.js] -->|增量展开/AI分析请求| Controller
        Panel[配置面板] -->|设置项目路径/API Key| Controller
    end
    
    subgraph Backend (Spring Boot)
        Controller[Web API 控制器] --> Parser[Parser Service 语法解析]
        Controller --> LLM[LLM Service 大模型对接]
        Parser -->|提取方法调用| JavaParser[JavaParser 源码解析]
        Parser -->|提取数据库操作| SQLParser[JSqlParser & XML 扫描]
    end
```

### 2.1 后端模块定义
*   **Controller 模块**：提供 API 接口以保存配置、初始化调用链、展开子节点以及请求 AI 分析。
*   **Parser 模块 (JavaParser)**：按需读取目标 Java 代码的 `.java` 源码文件，并定位指定类中的方法体，遍历其内部的方法调用表达式 (`MethodCallExpr`)。
*   **SQL & XML Solver 模块 (JSqlParser)**：
    *   根据 Mapper 接口及方法名定位对应的 XML 文件，提取特定 SQL。
    *   读取实体类上的 `@TableName` 注解，还原无 XML 的通用 CRUD 对应的物理表。
    *   使用 `JSqlParser` 对提取出的 SQL 语句进行解析，输出精细的物理表名和操作类型。
*   **LLM Service 模块**：封装通用 OpenAI API 请求，使用预配置的 `api-key` 与 `base-url` 传递定制化的 Prompt，获取并返回 Markdown 报告。

### 2.2 前端模块定义
*   **配置管理面板**：用于输入目标项目的绝对路径、API 密钥以及入口类与方法。
*   **D3.js 树图区域**：基于 D3.js 渲染层次调用树，支持交互式点击节点以增量发送请求并展开子节点。对于 Mapper 调用，在节点前标记数据库图标。
*   **代码及分析报告面板**：分为“源码与 SQL 展示”和“AI 梳理报告”两个 Tab，为用户提供选中节点的静态源码结构与大模型生成的 Markdown 语义解析报告。

---

## 3. 核心流程与“按需解析”算法

为了实现“免编译”与“快速响应”，系统不扫描全局文件，而是采用**按需解析定位**的算法。

### 3.1 节点展开核心逻辑
当用户展开节点 `(className, methodName)` 时，后端执行以下逻辑：
1. **定位源文件**：在用户配置的目标项目根路径下，通过文件名模糊匹配检索 `className.java`（例如 `InvoiceMigrationServiceImpl` 对应搜索 `InvoiceMigrationServiceImpl.java`）。
2. **解析 AST**：使用 `JavaParser` 载入源码，定位到目标方法声明。
3. **识别方法调用**：
    *   遍历方法体内的所有方法调用（如 `orderService.createOrder(...)` 或 `invoiceMapper.insert(...)`）。
    *   根据局部变量声明、方法形参、或类字段成员，推导调用者（如 `orderService`）的声明类型（如 `OrderService`）。
    *   根据该类型进行二次定位：
        *   若为普通 Service 接口，在项目目录下查找名为 `OrderServiceImpl.java` 或包含 `implements OrderService` 的实现类文件，并作为下一级节点。
        *   若为 Mapper 接口，则将其标记为数据库操作节点。
4. **获取数据库操作**：
    *   若是自定义 Mapper 方法：定位对应的 Mapper XML，查找 `id` 为该方法名的 SQL。将 SQL 片段传递给 JSqlParser 提取物理表名与操作。
    *   若是通用 CRUD 方法：找到该 Mapper 接口泛型关联的 Entity 类，读取其类定义上的 `@TableName` 注解值作为物理表名。

---

## 4. 接口设计与数据模型

本工具暴露以下 4 个核心 API：

### 4.1 保存配置
*   **接口**：`POST /api/config`
*   **请求体格式**：
    ```json
    {
      "projectRoot": "/Users/hanjie/IdeaProjects/charging-ionchi",
      "apiKey": "sk-idY301ee0674c883dbfe9018d5fe5f3417096b7fbc60Hs80",
      "baseUrl": "https://api.gptsapi.net/",
      "model": "gpt-5.5"
    }
    ```
*   **响应体格式**：
    ```json
    {
      "success": true,
      "message": "配置保存成功"
    }
    ```

### 4.2 初始化调用链树
*   **接口**：`POST /api/tree/initialize`
*   **请求体格式**：
    ```json
    {
      "className": "com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl",
      "methodName": "migrateInvoice"
    }
    ```
*   **响应体格式**：
    ```json
    {
      "id": "com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl#migrateInvoice",
      "label": "migrateInvoice",
      "className": "com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl",
      "methodName": "migrateInvoice",
      "isMapper": false,
      "dbOperations": []
    }
    ```

### 4.3 增量展开子节点
*   **接口**：`POST /api/tree/expand`
*   **请求体格式**：
    ```json
    {
      "className": "com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl",
      "methodName": "migrateInvoice"
    }
    ```
*   **响应体格式**：
    ```json
    [
      {
        "id": "com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl#saveRecord",
        "label": "saveRecord",
        "className": "com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl",
        "methodName": "saveRecord",
        "isMapper": false,
        "dbOperations": []
      },
      {
        "id": "com.omp.financial.mapper.InvoiceMapper#insert",
        "label": "InvoiceMapper.insert",
        "className": "com.omp.financial.mapper.InvoiceMapper",
        "methodName": "insert",
        "isMapper": true,
        "dbOperations": [
          { "tableName": "t_invoice", "operationType": "INSERT" }
        ]
      }
    ]
    ```

### 4.4 请求 AI 智能分析报告
*   **接口**：`POST /api/ai/explain`
*   **请求体格式**：
    ```json
    {
      "className": "com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl",
      "methodName": "migrateInvoice"
    }
    ```
*   **响应体格式**：
    ```json
    {
      "markdownReport": "### 1. 业务逻辑总结\n- 该方法主要用于发票的整体迁移逻辑。通过对发票记录进行校验，完成主表与详情表的批量插入。\n\n### 2. 数据库变更说明\n- 涉及数据表：`t_invoice` (INSERT)、`t_invoice_detail` (INSERT)。\n- 条件与字段：在 `t_invoice` 中插入迁移成功的发票，并更新 `status` 字段为已迁移。\n\n### 3. 事务与异常处理\n- 该方法包含 `@Transactional(rollbackFor = Exception.class)` 注解，确保发生任何未捕获异常时数据库操作全部回滚。"
    }
    ```

---

## 5. 大模型对接及提示词设计

### 5.1 默认大模型参数
*   **服务地址**：`https://api.gptsapi.net/`
*   **大模型名称**：`gpt-5.5`
*   **连接超时时间**：30秒（由于涉及源码与 SQL 的业务归纳，保证响应稳定性）

### 5.2 核心 Prompt 模板
```text
你是一个资深的 Java 架构师兼数据库专家。请为以下 Java 方法进行业务逻辑和数据库操作的智能梳理：

【方法类名】：{className}
【方法名称】：{methodName}

【方法源码】：
```java
{methodSource}
```

【关联 SQL / 数据库操作】：
{sqlContext}

【输出要求】：
1. 业务逻辑总结：请用清晰、条理的语言，总结该方法的业务意图、核心控制流（如 if-else 条件判断）和主干逻辑。
2. 数据库变更说明：精细分析该方法对物理表的操作（如操作了哪些表、基于什么 WHERE 条件、变更了什么核心字段等）。
3. 事务与异常：如果该方法（或其类）上有 `@Transactional` 事务注解，或者包含 try-catch，请特别说明其事务机制和异常回滚行为。

请使用精美、格式合理的 Markdown 输出。
```

---

## 6. 前端 UI 交互设计说明

前端使用 Vue3 搭配 Element Plus 和 D3.js 渲染单页面：
1. **左侧配置区**：提供卡片式的输入，包括目标项目路径、API 配置，以及分析入口。支持一键保存配置和加载初始调用树。
2. **中间可视化树状图**：
    *   以 D3.js 树布局为基础，节点支持折叠/展开交互。
    *   若节点存在数据库操作，在节点文字右侧呈现带有颜色标示的物理表及动作标签（例如 `[t_order: INSERT]`）。
    *   支持缩放和拖拽树状图，以便查看复杂的方法深层嵌套。
3. **右侧详细内容展示区**：
    *   **Tab 1：方法详情**：展示从 JavaParser 提取出的当前方法的完整源码，带语法高亮，并在下方高亮列出其解析到的 SQL 或涉及的通用 CRUD 操作。
    *   **Tab 2：AI 报告**：包含“生成分析报告”按钮。点击后向后端请求 AI 生成接口，并将返回的 Markdown 进行转义和样式美化渲染。
