# XML解析与文件路径检索性能优化 实施计划

> **对于代理工作者：** 必须使用子技能：使用 xiaoming:xiaoming-brainstorming-subagent-driven-development（推荐）或 xiaoming:xiaoming-brainstorming-executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行追踪。

**目标：** 消除 XML DTD 联网挂起，并添加 XML 解析结果与文件路径 $O(1)$ 哈希缓存，大幅提升代码接口到数据库的调用链分析速度。

**架构：**
1. 在 `SqlExtractor` 中通过 `EntityResolver` 拦截 DTD 联网加载请求。同时，引入基于最近修改时间校验的 `xmlCache`。
2. 在 `ApiController` 中维护项目文件路径索引哈希表（`javaFileByNameCache`、`xmlFileByNameCache`），在首次查询、重新初始化接口 `/api/tree/initialize` 或更新配置 `/api/config` 时刷新索引，以 $O(1)$ 的内存 Map 检索代替高开销的 `Files.walk` 遍历。

**技术栈：** Java 11, Spring Boot, JUnit 5, org.w3c.dom

---

### Task 1：DTD加载拦截与XML缓存解析实现

**文件：**
- 修改：`src/main/java/com/codedb/analyst/parser/SqlExtractor.java`
- 测试：`src/test/java/com/codedb/analyst/parser/SqlExtractorTest.java` (新文件)

- [ ] **步骤 1：编写测试类验证带 DTD 的 XML 解析，验证缓存与失效**

创建新文件 [SqlExtractorTest.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/test/java/com/codedb/analyst/parser/SqlExtractorTest.java) 并写入如下内容：

```java
package com.code.analyst.parser;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

public class SqlExtractorTest {
    @Test
    public void testFindSqlFromXmlWithDtd() throws Exception {
        SqlExtractor extractor = new SqlExtractor();
        File tempFile = File.createTempFile("MockMapper", ".xml");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-0-mapper.dtd\">\n" +
                            "<mapper namespace=\"com.example.MockMapper\">\n" +
                            "    <select id=\"getUser\">SELECT * FROM t_user WHERE id = #{id}</select>\n" +
                            "</mapper>\n"
            );
        }

        // 测试不联网即可快速解析并命中对应 SQL
        long start = System.currentTimeMillis();
        String sql = extractor.findSqlFromXml(tempFile.getAbsolutePath(), "getUser");
        long duration = System.currentTimeMillis() - start;

        // 预期耗时非常短（通常 < 50ms），说明没有请求外部网络
        assertTrue(duration < 200, "XML 解析应当非常迅速，避开 DTD 联网等待，当前耗时: " + duration + "ms");
        assertTrue(sql.contains("t_user"));

        // 测试修改文件后能够识别最新修改（缓存更新）
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-0-mapper.dtd\">\n" +
                            "<mapper namespace=\"com.example.MockMapper\">\n" +
                            "    <select id=\"getUser\">SELECT * FROM t_user_new WHERE id = #{id}</select>\n" +
                            "</mapper>\n"
            );
        }
        // 人为修改最近修改时间确保其变化
        tempFile.setLastModified(System.currentTimeMillis() + 2000);

        String newSql = extractor.findSqlFromXml(tempFile.getAbsolutePath(), "getUser");
        assertTrue(newSql.contains("t_user_new"), "当文件被修改时，应当重新解析而不是走旧缓存： " + newSql);
    }
}
```

- [ ] **步骤 2：运行测试验证其失败**

运行：`mvn test -Dtest=SqlExtractorTest`
预期：因 `SqlExtractor.java` 尚未设置 `EntityResolver`，如果执行环境网络连接正常则不一定报错但速度会较慢，如在无外网环境下执行则该测试会超时或抛出外部 DTD 加载异常而失败。

- [ ] **步骤 3：在 SqlExtractor.java 中引入缓存结构并拦截 DTD 加载**

修改 [SqlExtractor.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/parser/SqlExtractor.java)：
1. 引入必要包：`import java.util.Map;`、`import java.util.concurrent.ConcurrentHashMap;`、`import java.util.HashMap;`。
2. 添加 `XmlSqlCache` 静态内部类与 `xmlCache` 变量。
3. 重写 `findSqlFromXml` 逻辑。

替换内容：

```java
package com.code.analyst.parser;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SqlExtractor {

    private final Map<String, XmlSqlCache> xmlCache = new ConcurrentHashMap<>();

    private static class XmlSqlCache {
        final long lastModified;
        final Map<String, String> methodSqlMap;

        XmlSqlCache(long lastModified, Map<String, String> methodSqlMap) {
            this.lastModified = lastModified;
            this.methodSqlMap = methodSqlMap;
        }
    }

    public String findSqlFromXml(String xmlFilePath, String methodId) {
        try {
            File file = new File(xmlFilePath);
            if (!file.exists()) {
                return "";
            }
            long currentLastModified = file.lastModified();
            XmlSqlCache cached = xmlCache.get(xmlFilePath);
            if (cached != null && cached.lastModified == currentLastModified) {
                return cached.methodSqlMap.getOrDefault(methodId, "");
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 解决 DTD 联网卡顿问题，跳过外部实体加载
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));

            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();

            Map<String, String> newSqlMap = new HashMap<>();
            String[] tags = {"select", "insert", "update", "delete"};
            for (String tag : tags) {
                NodeList list = doc.getElementsByTagName(tag);
                for (int i = 0; i < list.getLength(); i++) {
                    Element el = (Element) list.item(i);
                    String id = el.getAttribute("id");
                    if (id != null && !id.isEmpty()) {
                        newSqlMap.put(id, el.getTextContent());
                    }
                }
            }

            xmlCache.put(xmlFilePath, new XmlSqlCache(currentLastModified, newSqlMap));
            return newSqlMap.getOrDefault(methodId, "");
        } catch (Exception e) {
            return "";
        }
    }
```

- [ ] **步骤 4：运行测试验证其通过**

运行：`mvn test -Dtest=SqlExtractorTest`
预期：测试通过，耗时远低于 200 毫秒，且两次内容验证全部符合预期。

---

### Task 2：实现文件检索缓存索引

**文件：**
- 修改：`src/main/java/com/codedb/analyst/web/ApiController.java`
- 测试：`src/test/java/com/codedb/analyst/web/ApiControllerTest.java`

- [ ] **步骤 1：在 ApiControllerTest.java 中编写新增的方法扫描与重构后的路径匹配测试**

修改 [ApiControllerTest.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/test/java/com/codedb/analyst/web/ApiControllerTest.java) 添加方法 `testFileIndexCache` 验证索引能正确命中，并且能在缓存失效重新初始化后再次加载：

```java

@Test
public void testFileIndexCache() throws Exception {
    com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
    com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
    // 使用一个临时目录作为测试项目根目录
    File tempDir = Files.createTempDirectory("test_project_root").toFile();
    tempDir.deleteOnExit();

    File javaFile = new File(tempDir, "OrderService.java");
    javaFile.createNewFile();
    javaFile.deleteOnExit();

    File xmlFile = new File(tempDir, "OrderMapper.xml");
    xmlFile.createNewFile();
    xmlFile.deleteOnExit();

    config.setProjectRoot(tempDir.getAbsolutePath());
    configManager.saveConfig(config);

    ApiController controller = new ApiController(configManager, new com.code.analyst.parser.JavaSourceParser(), new com.code.analyst.parser.SqlExtractor(), null);

    // 利用反射调用 ensureIndexInitialized
    java.lang.reflect.Method ensureIndex = ApiController.class.getDeclaredMethod("ensureIndexInitialized", String.class);
    ensureIndex.setAccessible(true);
    ensureIndex.invoke(controller, tempDir.getAbsolutePath());

    // 利用反射读取 javaFileByNameCache 和 xmlFileByNameCache
    java.lang.reflect.Field javaCacheField = ApiController.class.getDeclaredField("javaFileByNameCache");
    javaCacheField.setAccessible(true);
    java.util.Map<String, java.util.List<java.nio.file.Path>> javaCache = (java.util.Map<String, java.util.List<java.nio.file.Path>>) javaCacheField.get(controller);

    java.lang.reflect.Field xmlCacheField = ApiController.class.getDeclaredField("xmlFileByNameCache");
    xmlCacheField.setAccessible(true);
    java.util.Map<String, java.nio.file.Path> xmlCache = (java.util.Map<String, java.nio.file.Path>) xmlCacheField.get(controller);

    assertTrue(javaCache.containsKey("OrderService"));
    assertTrue(xmlCache.containsKey("OrderMapper.xml"));
    assertEquals(javaFile.getAbsolutePath(), javaCache.get("OrderService").get(0).toAbsolutePath().toString());
}
```

- [ ] **步骤 2：运行测试验证其失败**

运行：`mvn test -Dtest=ApiControllerTest#testFileIndexCache`
预期：编译报错或运行抛出 `NoSuchFieldException`（因为 `javaFileByNameCache` 等字段尚未在 `ApiController` 中定义）。

- [ ] **步骤 3：修改 ApiController.java，引入成员变量、重构检索方法，并添加清空逻辑**

在 [ApiController.java](file:///Users/hanjie/IdeaProjects/code-analysis/src/main/java/com/codedb/analyst/web/ApiController.java) 中进行以下修改：
1. 声明缓存变量：
```java
    private String lastProjectRoot = null;
    private final Map<String, List<Path>> javaFileByNameCache = new HashMap<>();
    private final Map<String, Path> xmlFileByNameCache = new HashMap<>();
```
2. 编写 `ensureIndexInitialized` 私有方法：
```java
    private synchronized void ensureIndexInitialized(String rootPath) {
        if (rootPath == null || rootPath.isEmpty()) {
            return;
        }
        if (rootPath.equals(lastProjectRoot) && !javaFileByNameCache.isEmpty()) {
            return;
        }

        javaFileByNameCache.clear();
        xmlFileByNameCache.clear();
        lastProjectRoot = rootPath;

        try (Stream<Path> walk = Files.walk(Path.of(rootPath))) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String fileName = p.getFileName().toString();
                if (fileName.endsWith(".java")) {
                    String className = fileName.substring(0, fileName.length() - 5);
                    javaFileByNameCache.computeIfAbsent(className, k -> new ArrayList<>()).add(p);
                } else if (fileName.endsWith(".xml")) {
                    xmlFileByNameCache.put(fileName, p);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```
3. 在 `saveConfig` 接口和 `initializeTree` 接口首行加入清理缓存块：
```java
        synchronized (this) {
            lastProjectRoot = null;
            javaFileByNameCache.clear();
            xmlFileByNameCache.clear();
        }
```
4. 修改 `findXmlFile`、`findJavaFiles`、`findJavaFileByFqName` 的实现以利用此缓存，具体如下：

```java
    private List<Path> findJavaFiles(String rootPath, String className) {
        ensureIndexInitialized(rootPath);
        return javaFileByNameCache.getOrDefault(className, Collections.emptyList());
    }

    private Optional<Path> findJavaFileByFqName(String rootPath, String fqName) {
        if (fqName == null || fqName.isEmpty()) {
            return Optional.empty();
        }
        if (!fqName.contains(".")) {
            return findJavaFile(rootPath, fqName, null);
        }
        ensureIndexInitialized(rootPath);
        String pathSuffix = fqName.replace('.', '/') + ".java";
        String simpleName = fqName.substring(fqName.lastIndexOf('.') + 1);
        List<Path> candidates = javaFileByNameCache.getOrDefault(simpleName, Collections.emptyList());
        for (Path p : candidates) {
            if (p.toString().replace('\\', '/').endsWith(pathSuffix)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private Optional<Path> findXmlFile(String rootPath, String mapperName) {
        ensureIndexInitialized(rootPath);
        Path xmlPath = xmlFileByNameCache.get(mapperName + ".xml");
        return Optional.ofNullable(xmlPath);
    }
```

- [ ] **步骤 4：运行测试验证其通过**

运行：`mvn test`
预期：所有测试全数通过，包括新增的 `testFileIndexCache` 测试。
