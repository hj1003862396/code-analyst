# code-db-analyst 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标**：构建一个独立的 Spring Boot Web 服务，自带单页面 Vue 前端，用于按需增量展开 Java 接口的方法调用树，并利用 JavaParser、JSqlParser 和 GPT-5.5 大模型分析并总结每个方法的业务逻辑及对应的数据库表操作。

**架构**：后端（Spring Boot）利用 JavaParser 在项目目录内按需模糊检索并解析 `.java` 源码文件，解析变量类型及方法调用关系，通过 XML 提取及 JSqlParser 解析 SQL。前端（Vue 3 + D3.js）通过 AJAX 请求增量展开子节点并渲染分析报告。

**技术栈**：Spring Boot 3.x, JavaParser, JSqlParser, JUnit 5, Vue 3, D3.js, Element Plus.

---

### Task 1：项目初始化与依赖配置 (Maven POM)

**文件**：
- 创建：`pom.xml`
- 创建：`src/main/resources/application.yml`
- 创建：`src/main/java/com/codedb/analyst/CodeDbAnalystApplication.java`

- [ ] **步骤 1：创建 `pom.xml` 文件**
  编写包含 Spring Boot、JavaParser、JSqlParser 的 `pom.xml`。
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <project xmlns="http://maven.apache.org/POM/4.0.0"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
      <modelVersion>4.0.0</modelVersion>

      <groupId>com.codedb</groupId>
      <artifactId>code-db-analyst</artifactId>
      <version>1.0.0</version>

      <parent>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-parent</artifactId>
          <version>3.2.4</version>
          <relativePath/>
      </parent>

      <properties>
          <java.version>17</java.version>
          <javaparser.version>3.26.1</javaparser.version>
          <jsqlparser.version>4.9</jsqlparser.version>
      </properties>

      <dependencies>
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-web</artifactId>
          </dependency>
          <dependency>
              <groupId>com.github.javaparser</groupId>
              <artifactId>javaparser-core</artifactId>
              <version>${javaparser.version}</version>
          </dependency>
          <dependency>
              <groupId>com.github.jsqlparser</groupId>
              <artifactId>jsqlparser</artifactId>
              <version>${jsqlparser.version}</version>
          </dependency>
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-test</artifactId>
              <scope>test</scope>
          </dependency>
      </dependencies>

      <build>
          <plugins>
              <plugin>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-maven-plugin</artifactId>
              </plugin>
          </plugins>
      </build>
  </project>
  ```

- [ ] **步骤 2：创建配置文件 `application.yml`**
  ```yaml
  server:
    port: 8080
  spring:
    application:
      name: code-db-analyst
  ```

- [ ] **步骤 3：创建启动类 `CodeDbAnalystApplication.java`**
  ```java
  package com.codedb.analyst;

  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;

  @SpringBootApplication
  public class CodeDbAnalystApplication {
      public static void main(String[] args) {
          SpringApplication.run(CodeDbAnalystApplication.class, args);
      }
  }
  ```

- [ ] **步骤 4：测试编译**
  运行：`mvn clean compile`
  预期：编译成功，生成 target 目录且无报错。

---

### Task 2：配置管理器 (ConfigManager) 实现

**文件**：
- 创建：`src/main/java/com/codedb/analyst/config/AppConfig.java`
- 创建：`src/main/java/com/codedb/analyst/config/ConfigManager.java`
- 创建：`src/test/java/com/codedb/analyst/config/ConfigManagerTest.java`

- [ ] **步骤 1：写失败测试 `ConfigManagerTest.java`**
  ```java
  package com.codedb.analyst.config;

  import org.junit.jupiter.api.Test;
  import static org.junit.jupiter.api.Assertions.*;

  public class ConfigManagerTest {
      @Test
      public void testSaveAndGetConfig() {
          ConfigManager manager = new ConfigManager();
          AppConfig config = new AppConfig();
          config.setProjectRoot("/test/root");
          config.setApiKey("test-key");
          config.setBaseUrl("http://test.url");
          config.setModel("test-model");
          
          manager.saveConfig(config);
          
          AppConfig loaded = manager.getConfig();
          assertNotNull(loaded);
          assertEquals("/test/root", loaded.getProjectRoot());
          assertEquals("test-key", loaded.getApiKey());
      }
  }
  ```
  运行：`mvn test -Dtest=ConfigManagerTest`
  预期：编译失败，提示缺少 `ConfigManager` 和 `AppConfig` 类。

- [ ] **步骤 2：创建配置实体类 `AppConfig.java`**
  ```java
  package com.codedb.analyst.config;

  public class AppConfig {
      private String projectRoot;
      private String apiKey;
      private String baseUrl;
      private String model;

      // Getters & Setters
      public String getProjectRoot() { return projectRoot; }
      public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }
      public String getApiKey() { return apiKey; }
      public void setApiKey(String apiKey) { this.apiKey = apiKey; }
      public String getBaseUrl() { return baseUrl; }
      public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
      public String getModel() { return model; }
      public void setModel(String model) { this.model = model; }
  }
  ```

- [ ] **步骤 3：创建配置管理器 `ConfigManager.java`**
  用内存和临时系统文件存储配置。
  ```java
  package com.codedb.analyst.config;

  import org.springframework.stereotype.Component;

  @Component
  public class ConfigManager {
      private AppConfig currentConfig = new AppConfig();

      public synchronized void saveConfig(AppConfig config) {
          this.currentConfig = config;
      }

      public synchronized AppConfig getConfig() {
          return this.currentConfig;
      }
  }
  ```

- [ ] **步骤 4：运行测试验证其通过**
  运行：`mvn test -Dtest=ConfigManagerTest`
  预期：测试通过。

---

### Task 3：AST 文件搜索与解析器 (JavaParser API)

**文件**：
- 创建：`src/main/java/com/codedb/analyst/parser/JavaSourceParser.java`
- 创建：`src/main/java/com/codedb/analyst/parser/MethodCallInfo.java`
- 创建：`src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java`

- [ ] **步骤 1：创建测试数据及 `JavaSourceParserTest.java`**
  写一个对 Dummy 代码文件进行方法调用提取的失败测试。
  ```java
  package com.codedb.analyst.parser;

  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;
  import java.io.IOException;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.util.List;
  import static org.junit.jupiter.api.Assertions.*;

  public class JavaSourceParserTest {
      @TempDir
      Path tempDir;

      @Test
      public void testParseMethodCalls() throws IOException {
          String dummyClass = "package com.example;\n" +
                  "public class OrderServiceImpl implements OrderService {\n" +
                  "    private OrderMapper orderMapper;\n" +
                  "    public void createOrder() {\n" +
                  "        String code = \"A100\";\n" +
                  "        orderMapper.insertOrder(code);\n" +
                  "    }\n" +
                  "}\n";
          Path classPath = tempDir.resolve("OrderServiceImpl.java");
          Files.writeString(classPath, dummyClass);

          JavaSourceParser parser = new JavaSourceParser();
          List<MethodCallInfo> calls = parser.parseMethodCalls(classPath.toString(), "createOrder");
          
          assertNotNull(calls);
          assertEquals(1, calls.size());
          MethodCallInfo call = calls.get(0);
          assertEquals("orderMapper", call.getObjectName());
          assertEquals("insertOrder", call.getMethodName());
          assertEquals("OrderMapper", call.getObjectType());
      }
  }
  ```
  运行：`mvn test -Dtest=JavaSourceParserTest`
  预期：编译失败，缺少对应的 Parser 类。

- [ ] **步骤 2：创建数据传输类 `MethodCallInfo.java`**
  ```java
  package com.codedb.analyst.parser;

  public class MethodCallInfo {
      private String objectName;
      private String methodName;
      private String objectType;

      public MethodCallInfo(String objectName, String methodName, String objectType) {
          this.objectName = objectName;
          this.methodName = methodName;
          this.objectType = objectType;
      }

      public String getObjectName() { return objectName; }
      public String getMethodName() { return methodName; }
      public String getObjectType() { return objectType; }
  }
  ```

- [ ] **步骤 3：编写源码解析实现类 `JavaSourceParser.java`**
  使用 JavaParser 解析 AST，并在方法体内提取局部变量或类成员变量对应的类型。
  ```java
  package com.codedb.analyst.parser;

  import com.github.javaparser.StaticJavaParser;
  import com.github.javaparser.ast.CompilationUnit;
  import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
  import com.github.javaparser.ast.body.FieldDeclaration;
  import com.github.javaparser.ast.body.MethodDeclaration;
  import com.github.javaparser.ast.body.VariableDeclarator;
  import com.github.javaparser.ast.expr.MethodCallExpr;
  import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
  import org.springframework.stereotype.Service;

  import java.io.File;
  import java.io.FileNotFoundException;
  import java.util.ArrayList;
  import java.util.HashMap;
  import java.util.List;
  import java.util.Map;

  @Service
  public class JavaSourceParser {

      public List<MethodCallInfo> parseMethodCalls(String filePath, String targetMethodName) throws FileNotFoundException {
          CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
          List<MethodCallInfo> calls = new ArrayList<>();

          // 1. 扫描并缓存类的字段(Field)类型，用于后面的变量推导
          Map<String, String> fieldTypes = new HashMap<>();
          cu.findAll(FieldDeclaration.class).forEach(fd -> {
              for (VariableDeclarator vd : fd.getVariables()) {
                  fieldTypes.put(vd.getNameAsString(), vd.getTypeAsString());
              }
          });

          // 2. 找到目标方法
          cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals(targetMethodName))
            .findFirst()
            .ifPresent(method -> {
                // 3. 扫描方法体中的局部变量并加入映射表
                Map<String, String> localTypes = new HashMap<>(fieldTypes);
                method.getParameters().forEach(param -> {
                    localTypes.put(param.getNameAsString(), param.getTypeAsString());
                });
                method.findAll(VariableDeclarator.class).forEach(vd -> {
                    localTypes.put(vd.getNameAsString(), vd.getTypeAsString());
                });

                // 4. 扫描所有方法调用表达式
                method.accept(new VoidVisitorAdapter<Void>() {
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
                }, null);
            });

          return calls;
      }

      public String getMethodSource(String filePath, String targetMethodName) throws FileNotFoundException {
          CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
          return cu.findAll(MethodDeclaration.class).stream()
                  .filter(m -> m.getNameAsString().equals(targetMethodName))
                  .findFirst()
                  .map(MethodDeclaration::toString)
                  .orElse("");
      }
  }
  ```

- [ ] **步骤 4：运行测试验证其通过**
  运行：`mvn test -Dtest=JavaSourceParserTest`
  预期：测试通过。

---

### Task 4：SQL与XML解析及JSqlParser数据库提取

**文件**：
- 创建：`src/main/java/com/codedb/analyst/parser/SqlExtractor.java`
- 创建：`src/main/java/com/codedb/analyst/parser/DbOperation.java`
- 创建：`src/test/java/com/codedb/analyst/parser/SqlExtractorTest.java`

- [ ] **步骤 1：创建测试文件 `SqlExtractorTest.java`**
  编写对 MyBatis Mapper XML SQL 的提取与物理表解析的失败测试。
  ```java
  package com.codedb.analyst.parser;

  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;
  import java.io.IOException;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.util.List;
  import static org.junit.jupiter.api.Assertions.*;

  public class SqlExtractorTest {
      @TempDir
      Path tempDir;

      @Test
      public void testParseXmlSql() throws IOException {
          String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                  "<mapper namespace=\"com.example.OrderMapper\">\n" +
                  "    <select id=\"selectOrderById\">\n" +
                  "        select * from t_order where id = #{id}\n" +
                  "    </select>\n" +
                  "</mapper>\n";
          Path xmlPath = tempDir.resolve("OrderMapper.xml");
          Files.writeString(xmlPath, xmlContent);

          SqlExtractor extractor = new SqlExtractor();
          String sql = extractor.findSqlFromXml(xmlPath.toString(), "selectOrderById");
          assertEquals("select * from t_order where id = #{id}", sql.trim());

          List<DbOperation> ops = extractor.extractDbOperations(sql);
          assertEquals(1, ops.size());
          assertEquals("t_order", ops.get(0).getTableName());
          assertEquals("SELECT", ops.get(0).getOperationType());
      }
  }
  ```
  运行：`mvn test -Dtest=SqlExtractorTest`
  预期：编译失败，缺少 `SqlExtractor`。

- [ ] **步骤 2：创建数据模型类 `DbOperation.java`**
  ```java
  package com.codedb.analyst.parser;

  public class DbOperation {
      private String tableName;
      private String operationType;

      public DbOperation(String tableName, String operationType) {
          this.tableName = tableName;
          this.operationType = operationType;
      }

      public String getTableName() { return tableName; }
      public String getOperationType() { return operationType; }
  }
  ```

- [ ] **步骤 3：编写 `SqlExtractor.java` 服务类**
  包含 XML 精准读取和 JSqlParser 物理表解析。
  ```java
  package com.codedb.analyst.parser;

  import net.sf.jsqlparser.parser.CCJSqlParserUtil;
  import net.sf.jsqlparser.statement.Statement;
  import net.sf.jsqlparser.util.TablesNamesFinder;
  import org.springframework.stereotype.Service;
  import org.w3c.dom.Document;
  import org.w3c.dom.Element;
  import org.w3c.dom.NodeList;

  import javax.xml.parsers.DocumentBuilder;
  import javax.xml.parsers.DocumentBuilderFactory;
  import java.io.File;
  import java.util.ArrayList;
  import java.util.Collections;
  import java.util.List;

  @Service
  public class SqlExtractor {

      public String findSqlFromXml(String xmlFilePath, String methodId) {
          try {
              File file = new File(xmlFilePath);
              if (!file.exists()) return "";
              DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
              DocumentBuilder builder = factory.newDocumentBuilder();
              Document doc = builder.parse(file);
              doc.getDocumentElement().normalize();

              String[] tags = {"select", "insert", "update", "delete"};
              for (String tag : tags) {
                  NodeList list = doc.getElementsByTagName(tag);
                  for (int i = 0; i < list.getLength(); i++) {
                      Element el = (Element) list.item(i);
                      if (methodId.equals(el.getAttribute("id"))) {
                          return el.getTextContent();
                      }
                  }
              }
          } catch (Exception e) {
              return "";
          }
          return "";
      }

      public List<DbOperation> extractDbOperations(String sql) {
          if (sql == null || sql.trim().isEmpty()) {
              return Collections.emptyList();
          }
          try {
              // 简单预处理清除 MyBatis 动态标签符号
              String cleanSql = sql.replaceAll("<[^>]+>", " ")
                                   .replaceAll("#\\{[^\\}]+\\}", "?")
                                   .replaceAll("\\$\\{[^\\}]+\\}", "?")
                                   .trim();
              Statement stmt = CCJSqlParserUtil.parse(cleanSql);
              String type = "UNKNOWN";
              String stmtStr = stmt.getClass().getSimpleName().toUpperCase();
              if (stmtStr.contains("SELECT")) type = "SELECT";
              else if (stmtStr.contains("INSERT")) type = "INSERT";
              else if (stmtStr.contains("UPDATE")) type = "UPDATE";
              else if (stmtStr.contains("DELETE")) type = "DELETE";

              TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
              List<String> tables = tablesNamesFinder.getTableList(stmt);
              List<DbOperation> ops = new ArrayList<>();
              for (String t : tables) {
                  ops.add(new DbOperation(t.replace("`", ""), type));
              }
              return ops;
          } catch (Exception e) {
              // 回退至简易的关键字匹配兜底
              List<DbOperation> fallback = new ArrayList<>();
              String lower = sql.toLowerCase();
              if (lower.contains("select") && lower.contains("from")) {
                  fallback.add(new DbOperation("unknown_table", "SELECT"));
              }
              return fallback;
          }
      }
  }
  ```

- [ ] **步骤 4：运行测试验证其通过**
  运行：`mvn test -Dtest=SqlExtractorTest`
  预期：测试通过。

---

### Task 5：GPT-5.5 大模型接口与服务集成

**文件**：
- 创建：`src/main/java/com/codedb/analyst/llm/LlmService.java`
- 创建：`src/test/java/com/codedb/analyst/llm/LlmServiceTest.java`

- [ ] **步骤 1：编写测试类 `LlmServiceTest.java`**
  模拟调用或使用 Mock HTTP 服务器验证 Llm 请求逻辑。
  ```java
  package com.codedb.analyst.llm;

  import com.codedb.analyst.config.AppConfig;
  import com.codedb.analyst.config.ConfigManager;
  import org.junit.jupiter.api.Test;
  import static org.junit.jupiter.api.Assertions.*;

  public class LlmServiceTest {
      @Test
      public void testBuildPrompt() {
          ConfigManager configManager = new ConfigManager();
          AppConfig config = new AppConfig();
          config.setApiKey("mock-key");
          config.setBaseUrl("http://mock.url/");
          config.setModel("gpt-5.5");
          configManager.saveConfig(config);

          LlmService service = new LlmService(configManager);
          String prompt = service.buildPrompt("MyClass", "myMethod", "void myMethod() { }", "SELECT * FROM t_user");
          assertTrue(prompt.contains("MyClass"));
          assertTrue(prompt.contains("t_user"));
      }
  }
  ```
  运行：`mvn test -Dtest=LlmServiceTest`
  预期：编译失败，缺失 `LlmService` 类。

- [ ] **步骤 2：编写 `LlmService.java` 实现类**
  ```java
  package com.codedb.analyst.llm;

  import com.codedb.analyst.config.AppConfig;
  import com.codedb.analyst.config.ConfigManager;
  import org.springframework.http.HttpEntity;
  import org.springframework.http.HttpHeaders;
  import org.springframework.http.MediaType;
  import org.springframework.http.ResponseEntity;
  import org.springframework.stereotype.Service;
  import org.springframework.web.client.RestTemplate;

  import java.util.ArrayList;
  import java.util.HashMap;
  import java.util.List;
  import java.util.Map;

  @Service
  public class LlmService {

      private final ConfigManager configManager;
      private final RestTemplate restTemplate = new RestTemplate();

      public LlmService(ConfigManager configManager) {
          this.configManager = configManager;
      }

      public String buildPrompt(String className, String methodName, String sourceCode, String sqlContext) {
          return "你是一个资深的 Java 架构师兼数据库专家。请为以下 Java 方法进行业务逻辑和数据库操作的智能梳理：\n\n" +
                  "【方法类名】：" + className + "\n" +
                  "【方法名称】：" + methodName + "\n\n" +
                  "【方法源码】\n```java\n" + sourceCode + "\n```\n\n" +
                  "【关联 SQL / 数据库操作】\n" + sqlContext + "\n\n" +
                  "【输出要求】\n" +
                  "1. 业务逻辑总结：请用清晰、条理的语言，总结该方法的业务意图、核心控制流（如 if-else 条件判断）和主干逻辑。\n" +
                  "2. 数据库变更说明：精细分析该方法对物理表的操作（如操作了哪些表、基于什么 WHERE 条件、变更了什么核心字段等）。\n" +
                  "3. 事务与异常：如果该方法（或其类）上有 @Transactional 事务注解，或者包含 try-catch，请特别说明其事务机制和异常回滚行为。\n\n" +
                  "请使用精美、格式合理的 Markdown 输出。";
      }

      public String explainMethod(String className, String methodName, String sourceCode, String sqlContext) {
          AppConfig config = configManager.getConfig();
          if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
              return "错误：大模型 API Key 未配置，请先保存配置。";
          }
          String url = config.getBaseUrl();
          if (!url.endsWith("/")) {
              url += "/";
          }
          url += "v1/chat/completions";

          String prompt = buildPrompt(className, methodName, sourceCode, sqlContext);

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          headers.setBearerAuth(config.getApiKey());

          Map<String, Object> requestBody = new HashMap<>();
          requestBody.put("model", config.getModel());
          
          List<Map<String, String>> messages = new ArrayList<>();
          Map<String, String> userMessage = new HashMap<>();
          userMessage.put("role", "user");
          userMessage.put("content", prompt);
          messages.add(userMessage);
          
          requestBody.put("messages", messages);

          HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

          try {
              ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
              if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                  List choices = (List) response.getBody().get("choices");
                  if (choices != null && !choices.isEmpty()) {
                      Map choice = (Map) choices.get(0);
                      Map message = (Map) choice.get("message");
                      return (String) message.get("content");
                  }
              }
              return "大模型调用返回异常，HTTP 状态码: " + response.getStatusCode();
          } catch (Exception e) {
              return "大模型调用发生异常：" + e.getMessage();
          }
      }
  }
  ```

- [ ] **步骤 3：运行测试验证其通过**
  运行：`mvn test -Dtest=LlmServiceTest`
  预期：测试通过。

---

### Task 6：后端 HTTP API Controller 设计与实现

**文件**：
- 创建：`src/main/java/com/codedb/analyst/web/ApiController.java`
- 创建：`src/test/java/com/codedb/analyst/web/ApiControllerTest.java`

- [ ] **步骤 1：编写测试类 `ApiControllerTest.java`**
  编写对 Web 端 API 的 Spring Boot MockMvc 集成测试。
  ```java
  package com.codedb.analyst.web;

  import com.codedb.analyst.CodeDbAnalystApplication;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.servlet.MockMvc;

  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  @SpringBootTest(classes = CodeDbAnalystApplication.class)
  @AutoConfigureMockMvc
  public class ApiControllerTest {

      @Autowired
      private MockMvc mockMvc;

      @Test
      public void testSaveConfigApi() throws Exception {
          String json = "{\"projectRoot\":\"/tmp\",\"apiKey\":\"test\",\"baseUrl\":\"http://test\",\"model\":\"gpt-5.5\"}";
          mockMvc.perform(post("/api/config")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.success").value(true));
      }
  }
  ```
  运行：`mvn test -Dtest=ApiControllerTest`
  预期：编译失败，缺失 `ApiController` 类。

- [ ] **步骤 2：创建控制器类 `ApiController.java`**
  ```java
  package com.codedb.analyst.web;

  import com.codedb.analyst.config.AppConfig;
  import com.codedb.analyst.config.ConfigManager;
  import com.codedb.analyst.llm.LlmService;
  import com.codedb.analyst.parser.DbOperation;
  import com.codedb.analyst.parser.JavaSourceParser;
  import com.codedb.analyst.parser.MethodCallInfo;
  import com.codedb.analyst.parser.SqlExtractor;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  import java.io.File;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.util.*;
  import java.util.stream.Stream;

  @RestController
  @RequestMapping("/api")
  @CrossOrigin
  public class ApiController {

      private final ConfigManager configManager;
      private final JavaSourceParser sourceParser;
      private final SqlExtractor sqlExtractor;
      private final LlmService llmService;

      public ApiController(ConfigManager configManager, JavaSourceParser sourceParser, SqlExtractor sqlExtractor, LlmService llmService) {
          this.configManager = configManager;
          this.sourceParser = sourceParser;
          this.sqlExtractor = sqlExtractor;
          this.llmService = llmService;
      }

      @PostMapping("/config")
      public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody AppConfig config) {
          configManager.saveConfig(config);
          Map<String, Object> res = new HashMap<>();
          res.put("success", true);
          res.put("message", "配置保存成功");
          return ResponseEntity.ok(res);
      }

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
          node.put("dbOperations", Collections.emptyList());
          return ResponseEntity.ok(node);
      }

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

              // 1. 查找源文件
              String simpleName = className.substring(className.lastIndexOf('.') + 1);
              Optional<Path> fileOpt = findJavaFile(root, simpleName);
              if (fileOpt.isEmpty()) {
                  return ResponseEntity.ok(result); // 没找到源文件返回空
              }

              String filePath = fileOpt.get().toString();

              // 2. 解析方法调用
              List<MethodCallInfo> calls = sourceParser.parseMethodCalls(filePath, methodName);
              for (MethodCallInfo call : calls) {
                  Map<String, Object> node = new HashMap<>();
                  String type = call.getObjectType();
                  String method = call.getMethodName();

                  boolean isMapper = type.toLowerCase().contains("mapper");
                  List<DbOperation> dbOps = new ArrayList<>();

                  // 如果是 Mapper 调用，尝试解析 SQL
                  if (isMapper) {
                      Optional<Path> xmlOpt = findXmlFile(root, type);
                      if (xmlOpt.isPresent()) {
                          String sql = sqlExtractor.findSqlFromXml(xmlOpt.get().toString(), method);
                          dbOps = sqlExtractor.extractDbOperations(sql);
                      }
                      // 针对 MyBatis-Plus 的通用 CRUD 提取备用
                      if (dbOps.isEmpty() && (method.equals("insert") || method.equals("selectById") || method.equals("updateById") || method.equals("deleteById"))) {
                          dbOps = List.of(new DbOperation("t_" + type.toLowerCase().replace("mapper", ""), method.toUpperCase()));
                      }
                  }

                  String fullTargetClassName = resolveFullClassName(root, type);
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

      @PostMapping("/ai/explain")
      public ResponseEntity<Map<String, Object>> explainMethod(@RequestBody Map<String, String> request) {
          String className = request.get("className");
          String methodName = request.get("methodName");
          Map<String, Object> res = new HashMap<>();

          try {
              AppConfig config = configManager.getConfig();
              String root = config.getProjectRoot();
              String simpleName = className.substring(className.lastIndexOf('.') + 1);
              Optional<Path> fileOpt = findJavaFile(root, simpleName);

              if (fileOpt.isEmpty()) {
                  res.put("markdownReport", "错误：找不到该类对应的源码文件 " + simpleName + ".java");
                  return ResponseEntity.ok(res);
              }

              String source = sourceParser.getMethodSource(fileOpt.get().toString(), methodName);
              
              // 抓取关联的 SQL 上下文
              StringBuilder sqlCtx = new StringBuilder();
              if (className.toLowerCase().contains("mapper")) {
                  Optional<Path> xmlOpt = findXmlFile(root, simpleName);
                  if (xmlOpt.isPresent()) {
                      String sql = sqlExtractor.findSqlFromXml(xmlOpt.get().toString(), methodName);
                      sqlCtx.append("XML SQL: ").append(sql);
                  }
              }

              String report = llmService.explainMethod(className, methodName, source, sqlCtx.toString());
              res.put("markdownReport", report);

          } catch (Exception e) {
              res.put("markdownReport", "分析发生异常：" + e.getMessage());
          }

          return ResponseEntity.ok(res);
      }

      private Optional<Path> findJavaFile(String rootPath, String className) {
          try (Stream<Path> walk = Files.walk(Path.of(rootPath))) {
              return walk.filter(p -> p.getFileName().toString().equals(className + ".java"))
                      .findFirst();
          } catch (Exception e) {
              return Optional.empty();
          }
      }

      private Optional<Path> findXmlFile(String rootPath, String mapperName) {
          try (Stream<Path> walk = Files.walk(Path.of(rootPath))) {
              return walk.filter(p -> p.getFileName().toString().equals(mapperName + ".xml"))
                      .findFirst();
          } catch (Exception e) {
              return Optional.empty();
          }
      }

      private String resolveFullClassName(String rootPath, String className) {
          Optional<Path> path = findJavaFile(rootPath, className);
          if (path.isPresent()) {
              try {
                  List<String> lines = Files.readAllLines(path.get());
                  for (String line : lines) {
                      if (line.trim().startsWith("package ")) {
                          return line.replace("package ", "").replace(";", "").trim() + "." + className;
                      }
                  }
              } catch (Exception ignored) {}
          }
          return className;
      }
  }
  ```

- [ ] **步骤 3：运行测试验证其通过**
  运行：`mvn test`
  预期：所有 JUnit 测试通过，构建正常。

---

### Task 7：前端页面构建与静态文件集成

**文件**：
- 创建：`src/main/resources/static/index.html`

- [ ] **步骤 1：创建全功能静态单页面 `index.html`**
  利用 Vue 3、Element Plus（通过 CDN）和 D3.js 编写页面逻辑。
  ```html
  <!DOCTYPE html>
  <html lang="zh-CN">
  <head>
      <meta charset="UTF-8">
      <title>接口调用链与数据库操作智能梳理工具</title>
      <!-- 引入 Vue 3, Element Plus 和 D3.js -->
      <script src="https://unpkg.com/vue@3/dist/vue.global.js"></script>
      <link rel="stylesheet" href="https://unpkg.com/element-plus/dist/index.css" />
      <script src="https://unpkg.com/element-plus"></script>
      <script src="https://unpkg.com/d3@7"></script>
      <!-- 引入 Marked 用于 Markdown 渲染 -->
      <script src="https://unpkg.com/marked/marked.min.js"></script>
      <style>
          body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; margin: 0; padding: 0; background-color: #f5f7fa; }
          #app { display: flex; height: 100vh; flex-direction: column; }
          .header { background-color: #409EFF; color: #fff; padding: 15px 20px; font-size: 18px; font-weight: bold; }
          .main-content { display: flex; flex: 1; overflow: hidden; }
          .left-panel { width: 320px; border-right: 1px solid #dcdfe6; padding: 20px; background-color: #fff; overflow-y: auto; }
          .middle-panel { flex: 1; position: relative; background-color: #fafafa; }
          .right-panel { width: 450px; border-left: 1px solid #dcdfe6; padding: 20px; background-color: #fff; overflow-y: auto; }
          #tree-svg { width: 100%; height: 100%; }
          .node circle { fill: #fff; stroke: #409EFF; stroke-width: 3px; cursor: pointer; }
          .node text { font: 12px sans-serif; }
          .link { fill: none; stroke: #ccc; stroke-width: 2px; }
          .markdown-body { line-height: 1.6; }
          .markdown-body pre { background: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; }
      </style>
  </head>
  <body>
  <div id="app">
      <div class="header">💾 code-db-analyst // 接口调用链与数据库操作智能梳理</div>
      <div class="main-content">
          <!-- 左侧配置区 -->
          <div class="left-panel">
              <h3>⚙️ 配置面板</h3>
              <el-form label-position="top">
                  <el-form-item label="项目根路径">
                      <el-input v-model="config.projectRoot" placeholder="输入分析项目的绝对路径"></el-input>
                  </el-form-item>
                  <el-form-item label="API Key">
                      <el-input v-model="config.apiKey" type="password" placeholder="sk-..."></el-input>
                  </el-form-item>
                  <el-form-item label="API Base URL">
                      <el-input v-model="config.baseUrl" placeholder="https://api.gptsapi.net/"></el-input>
                  </el-form-item>
                  <el-form-item label="模型名称">
                      <el-input v-model="config.model" placeholder="gpt-5.5"></el-input>
                  </el-form-item>
                  <el-button type="primary" @click="saveConfig" style="width: 100%">保存配置</el-button>
                  
                  <el-divider></el-divider>
                  
                  <h3>🚀 入口初始化</h3>
                  <el-form-item label="全限定类名">
                      <el-input v-model="entry.className" placeholder="com.example.service.impl.OrderServiceImpl"></el-input>
                  </el-form-item>
                  <el-form-item label="方法名">
                      <el-input v-model="entry.methodName" placeholder="createOrder"></el-input>
                  </el-form-item>
                  <el-button type="success" @click="initTree" style="width: 100%">加载调用树</el-button>
              </el-form>
          </div>

          <!-- 中间画布区 -->
          <div class="middle-panel">
              <svg id="tree-svg"></svg>
          </div>

          <!-- 右侧详情区 -->
          <div class="right-panel">
              <h3>🔍 节点详情</h3>
              <div v-if="selectedNode">
                  <el-descriptions title="基本信息" :column="1" border>
                      <el-descriptions-item label="类名">{{ selectedNode.data.className }}</el-descriptions-item>
                      <el-descriptions-item label="方法">{{ selectedNode.data.methodName }}</el-descriptions-item>
                      <el-descriptions-item label="数据库节点">{{ selectedNode.data.isMapper ? '是' : '否' }}</el-descriptions-item>
                  </el-descriptions>
                  
                  <div v-if="selectedNode.data.dbOperations && selectedNode.data.dbOperations.length" style="margin-top: 15px;">
                      <h4>💾 数据库操作</h4>
                      <el-tag v-for="op in selectedNode.data.dbOperations" :key="op.tableName" type="danger" style="margin-right: 5px;">
                          {{ op.operationType }} -> {{ op.tableName }}
                      </el-tag>
                  </div>
                  
                  <el-divider></el-divider>
                  <el-button type="warning" :loading="aiLoading" @click="triggerAiAnalysis" style="width: 100%">🤖 AI 智能梳理分析</el-button>
                  
                  <div v-if="aiReport" style="margin-top: 20px;" class="markdown-body" v-html="renderedReport"></div>
              </div>
              <div v-else>
                  <el-empty description="在中间点击选择一个方法节点以查看详情"></el-empty>
              </div>
          </div>
      </div>
  </div>

  <script>
      const { createApp, ref, computed } = Vue;

      createApp({
          setup() {
              const config = ref({
                  projectRoot: '',
                  apiKey: 'sk-idY301ee0674c883dbfe9018d5fe5f3417096b7fbc60Hs80',
                  baseUrl: 'https://api.gptsapi.net/',
                  model: 'gpt-5.5'
              });

              const entry = ref({
                  className: 'com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl',
                  methodName: 'migrateInvoice'
              });

              const selectedNode = ref(null);
              const aiReport = ref('');
              const aiLoading = ref(false);

              const renderedReport = computed(() => {
                  return marked.parse(aiReport.value);
              });

              const saveConfig = async () => {
                  const res = await fetch('/api/config', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify(config.value)
                  });
                  const data = await res.json();
                  if (data.success) {
                      ElementPlus.ElMessage.success('配置已成功保存！');
                  }
              };

              let svg, g, tree, rootData;

              const initTree = async () => {
                  const res = await fetch('/api/tree/initialize', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify(entry.value)
                  });
                  const data = await res.json();
                  
                  rootData = d3.hierarchy(data);
                  renderD3Tree();
              };

              const renderD3Tree = () => {
                  d3.select("#tree-svg").selectAll("*").remove();

                  svg = d3.select("#tree-svg")
                          .call(d3.zoom().on("zoom", (event) => {
                              g.attr("transform", event.transform);
                          }));

                  g = svg.append("g").attr("transform", "translate(50, 200)");

                  tree = d3.tree().nodeSize([80, 250]);
                  updateTree(rootData);
              };

              const updateTree = (source) => {
                  const nodes = tree(rootData).descendants();
                  const links = rootData.links();

                  // 节点数据绑定
                  const node = g.selectAll(".node")
                      .data(nodes, d => d.data.id);

                  const nodeEnter = node.enter().append("g")
                      .attr("class", "node")
                      .attr("transform", d => `translate(${d.y}, ${d.x})`)
                      .on("click", async (event, d) => {
                          selectedNode.value = d;
                          aiReport.value = '';
                          
                          if (!d.children && !d.data.isMapper) {
                              const res = await fetch('/api/tree/expand', {
                                  method: 'POST',
                                  headers: { 'Content-Type': 'application/json' },
                                  body: JSON.stringify({
                                      className: d.data.className,
                                      methodName: d.data.methodName
                                  })
                              });
                              const children = await res.json();
                              if (children && children.length) {
                                  d.children = children.map(c => d3.hierarchy(c));
                                  d.children.forEach(c => c.parent = d);
                                  updateTree(d);
                              } else {
                                  ElementPlus.ElMessage.info('未解析到下一级方法调用');
                              }
                          }
                      });

                  nodeEnter.append("circle")
                      .attr("r", 10)
                      .style("fill", d => d.data.isMapper ? "#F56C6C" : "#409EFF");

                  nodeEnter.append("text")
                      .attr("dy", ".35em")
                      .attr("x", d => d.children ? -15 : 15)
                      .attr("style", "font-weight: bold")
                      .attr("text-anchor", d => d.children ? "end" : "start")
                      .text(d => d.data.label);

                  // 链接数据绑定
                  g.selectAll(".link")
                      .data(links, d => d.target.data.id)
                      .join("path")
                      .attr("class", "link")
                      .attr("d", d3.linkHorizontal()
                          .x(d => d.y)
                          .y(d => d.x));
              };

              const triggerAiAnalysis = async () => {
                  if (!selectedNode.value) return;
                  aiLoading.value = true;
                  try {
                      const res = await fetch('/api/ai/explain', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({
                              className: selectedNode.value.data.className,
                              methodName: selectedNode.value.data.methodName
                          })
                      });
                      const data = await res.json();
                      aiReport.value = data.markdownReport;
                  } catch (e) {
                      aiReport.value = '分析失败：' + e.message;
                  } finally {
                      aiLoading.value = false;
                  }
              };

              return {
                  config,
                  entry,
                  selectedNode,
                  aiReport,
                  aiLoading,
                  renderedReport,
                  saveConfig,
                  initTree,
                  triggerAiAnalysis
              };
          }
      }).use(ElementPlus).mount('#app');
  </script>
  </body>
  </html>
  ```

- [ ] **步骤 2：启动并运行整个服务，通过浏览器手动测试**
  运行命令启动后端项目：`mvn spring-boot:run`
  访问 `http://localhost:8080/index.html` 验证各功能正常运作。
