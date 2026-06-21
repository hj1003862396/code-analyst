# 方法未找到容错与默认方法初始化优化 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 在分析工具中，若用户指定的方法不存在，自动拦截大模型调用并列出该类中的所有可用候选方法，并默认初始化前端为 InvoicingApplyJob.process。

**架构：** 
1. 在 `JavaSourceParser` 中增加提取类中所有方法列表的功能；
2. 在 `ApiController` 中对方法源码解析结果进行判空校验，若为空则提前返回候选方法列表的 Markdown 报告并终止大模型请求；
3. 更新 `index.html` 中的默认初始方法名称。

**技术栈：** Java, Spring Boot, JavaParser, JUnit 5, Mockito

---

### Task 1：添加测试依赖

**文件：**
- 修改：`pom.xml`

- [ ] **步骤 1：在 `pom.xml` 中添加 `spring-boot-starter-test` 依赖**
  
  在 `<dependencies>` 标签内添加如下测试依赖：
  ```xml
          <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-test</artifactId>
              <scope>test</scope>
          </dependency>
  ```

- [ ] **步骤 2：执行 Maven 编译并解析依赖**
  
  运行：
  ```bash
  mvn dependency:resolve
  ```
  预期输出：编译成功，依赖正常解析。

---

### Task 2：在 JavaSourceParser 中实现并测试 getMethodNames

**文件：**
- 修改：`src/main/java/com/codedb/analyst/parser/JavaSourceParser.java`
- 创建：`src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java`

- [ ] **步骤 1：编写 JavaSourceParserTest 单元测试，包含针对 getMethodNames 的失败用例**
  
  创建 `src/test/java/com/codedb/analyst/parser/JavaSourceParserTest.java`，内容如下：
  ```java
  package com.codedb.analyst.parser;
  
  import org.junit.jupiter.api.Test;
  import java.io.File;
  import java.io.FileWriter;
  import java.util.List;
  import static org.junit.jupiter.api.Assertions.*;
  
  public class JavaSourceParserTest {
      
      @Test
      public void testGetMethodNames() throws Exception {
          // 创建临时 Java 文件
          File tempFile = File.createTempFile("DummyClass", ".java");
          tempFile.deleteOnExit();
          try (FileWriter writer = new FileWriter(tempFile)) {
              writer.write("public class DummyClass {\n" +
                           "    public void methodOne() {}\n" +
                           "    public String methodTwo(int a) { return \"\"; }\n" +
                           "}\n");
          }
  
          JavaSourceParser parser = new JavaSourceParser();
          List<String> names = parser.getMethodNames(tempFile.getAbsolutePath());
          
          assertNotNull(names);
          assertEquals(2, names.size());
          assertTrue(names.contains("methodOne"));
          assertTrue(names.contains("methodTwo"));
      }
  }
  ```

- [ ] **步骤 2：运行测试并确认编译失败**
  
  运行：
  ```bash
  mvn test -Dtest=JavaSourceParserTest
  ```
  预期输出：编译失败，提示 `JavaSourceParser` 中找不到 `getMethodNames` 方法。

- [ ] **步骤 3：在 JavaSourceParser 中添加 getMethodNames 对应实现**
  
  在 `src/main/java/com/codedb/analyst/parser/JavaSourceParser.java` 中导入依赖并实现方法：
  ```java
  // 导入需要的包
  import java.util.stream.Collectors;
  ```
  
  并在类中实现：
  ```java
      public List<String> getMethodNames(String filePath) throws FileNotFoundException {
          CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
          return cu.findAll(MethodDeclaration.class).stream()
                  .map(MethodDeclaration::getNameAsString)
                  .distinct()
                  .collect(Collectors.toList());
      }
  ```

- [ ] **步骤 4：运行测试并确认通过**
  
  运行：
  ```bash
  mvn test -Dtest=JavaSourceParserTest
  ```
  预期输出：`BUILD SUCCESS`，测试全部通过。

---

### Task 3：在 ApiController 中实现未找到方法时的拦截与报错

**文件：**
- 修改：`src/main/java/com/codedb/analyst/web/ApiController.java`
- 创建：`src/test/java/com/codedb/analyst/web/ApiControllerTest.java`

- [ ] **步骤 1：编写 ApiControllerTest 失败用例**
  
  创建 `src/test/java/com/codedb/analyst/web/ApiControllerTest.java`：
  ```java
  package com.codedb.analyst.web;
  
  import com.codedb.analyst.config.AppConfig;
  import com.codedb.analyst.config.ConfigManager;
  import com.codedb.analyst.llm.LlmService;
  import com.codedb.analyst.parser.JavaSourceParser;
  import org.junit.jupiter.api.Test;
  import org.mockito.Mockito;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.servlet.MockMvc;
  
  import java.nio.file.Path;
  import java.util.Arrays;
  import java.util.Optional;
  
  import static org.hamcrest.Matchers.containsString;
  import static org.mockito.ArgumentMatchers.anyString;
  import static org.mockito.Mockito.*;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
  
  @WebMvcTest(ApiController.class)
  public class ApiControllerTest {
  
      @Autowired
      private MockMvc mockMvc;
  
      @MockBean
      private ConfigManager configManager;
  
      @MockBean
      private JavaSourceParser sourceParser;
  
      @MockBean
      private LlmService llmService;
  
      @MockBean
      private com.codedb.analyst.parser.SqlExtractor sqlExtractor;
  
      @Test
      public void testExplainMethodNotFound() throws Exception {
          AppConfig config = new AppConfig();
          config.setProjectRoot("/dummy/root");
          when(configManager.getConfig()).thenReturn(config);
  
          // 模拟解析出的源码为空（方法不存在）
          when(sourceParser.getMethodSource(anyString(), eq("nonExistentMethod"))).thenReturn("");
          when(sourceParser.getMethodNames(anyString())).thenReturn(Arrays.asList("methodOne", "methodTwo"));
  
          mockMvc.perform(post("/api/ai/explain")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"className\":\"com.dummy.MyClass\",\"methodName\":\"nonExistentMethod\"}"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.markdownReport", containsString("分析失败")))
                  .andExpect(jsonPath("$.markdownReport", containsString("methodOne")))
                  .andExpect(jsonPath("$.markdownReport", containsString("methodTwo")));
  
          // 验证绝对没有调用大模型服务
          verify(llmService, never()).explainMethod(anyString(), anyString(), anyString(), anyString());
      }
  }
  ```

- [ ] **步骤 2：运行测试验证其失败**
  
  运行：
  ```bash
  mvn test -Dtest=ApiControllerTest
  ```
  预期输出：测试失败（或者是 Mock 检查失败，因为目前 `ApiController` 依旧会调用 `llmService.explainMethod`）。

- [ ] **步骤 3：在 ApiController.explainMethod 中添加未找到方法源码的提前拦截逻辑**
  
  在 `src/main/java/com/codedb/analyst/web/ApiController.java` 引入：
  ```java
  import java.util.stream.Collectors;
  ```
  
  并在 `/api/ai/explain` 映射的方法中修改：
  ```java
              String source = sourceParser.getMethodSource(fileOpt.get().toString(), methodName);
  
              if (source == null || source.trim().isEmpty()) {
                  List<String> methods = sourceParser.getMethodNames(fileOpt.get().toString());
                  StringBuilder errorReport = new StringBuilder();
                  errorReport.append("### ❌ 分析失败：未找到对应的方法源码\n");
                  errorReport.append("在类 `").append(simpleName).append("` 中找不到方法 `").append(methodName).append("`。请确认方法名称是否正确。\n\n");
                  errorReport.append("**该类中存在的候选方法：**\n");
                  for (String m : methods) {
                      errorReport.append("- `").append(m).append("`\n");
                  }
                  res.put("markdownReport", errorReport.toString());
                  return ResponseEntity.ok(res);
              }
  ```

- [ ] **步骤 4：运行测试验证其通过**
  
  运行：
  ```bash
  mvn test -Dtest=ApiControllerTest
  ```
  预期输出：`BUILD SUCCESS`，`ApiControllerTest` 顺利通过。

---

### Task 4：修改前端初始化默认值

**文件：**
- 修改：`src/main/resources/static/index.html`

- [ ] **步骤 1：在前端 `index.html` 中替换默认的 `className` 与 `methodName`**
  
  将 `index.html` 第 401 行附近的 entry 初始值：
  ```javascript
              const entry = ref({
                  className: 'com.omp.financial.service.invoice.impl.InvoiceMigrationServiceImpl',
                  methodName: 'migrateInvoice'
              });
  ```
  修改为：
  ```javascript
              const entry = ref({
                  className: 'com.omp.financial.intf.job.InvoicingApplyJob',
                  methodName: 'process'
              });
  ```

- [ ] **步骤 2：对修改后的项目进行完整编译验证**
  
  运行：
  ```bash
  mvn clean compile
  ```
  预期输出：`BUILD SUCCESS`。
