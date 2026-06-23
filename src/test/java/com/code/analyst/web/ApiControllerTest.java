package com.code.analyst.web;

import com.code.analyst.CodeAnalystApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CodeAnalystApplication.class)
public class ApiControllerTest {
    @Test
    public void contextLoads() {
        assertTrue(true);
    }

    @Test
    public void testFindJavaFileWithDuplicates() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
        configManager.saveConfig(config);

        com.code.analyst.parser.JavaSourceParser parser = new com.code.analyst.parser.JavaSourceParser();
        com.code.analyst.parser.SqlExtractor extractor = new com.code.analyst.parser.SqlExtractor();
        ApiController controller = new ApiController(configManager, parser, extractor, null);

        String controllerPath = "/Users/hanjie/IdeaProjects/charging-ionchi/omp-trading/omp-marketing/omp-marketing-server/src/main/java/com/omp/marketing/intf/web/ShortLinkController.java";
        
        java.lang.reflect.Method findJavaFileMethod = ApiController.class.getDeclaredMethod("findJavaFile", String.class, String.class, String.class);
        findJavaFileMethod.setAccessible(true);
        
        java.util.Optional<java.nio.file.Path> res = (java.util.Optional<java.nio.file.Path>) findJavaFileMethod.invoke(
            controller, 
            "/Users/hanjie/IdeaProjects/charging-ionchi", 
            "ShortLinkService", 
            controllerPath
        );
        
        assertTrue(res.isPresent());
        String resolvedPath = res.get().toString().replace('\\', '/');
        assertTrue(resolvedPath.contains("omp-marketing-service"), "应该精准匹配至 marketing 服务模块: " + resolvedPath);
    }

    @Test
    public void testFileIndexCache() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        // Use a temporary directory as the mock project root
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("test_project_root").toFile();
        tempDir.deleteOnExit();

        java.io.File javaFile = new java.io.File(tempDir, "OrderService.java");
        javaFile.createNewFile();
        javaFile.deleteOnExit();

        java.io.File xmlFile = new java.io.File(tempDir, "OrderMapper.xml");
        xmlFile.createNewFile();
        xmlFile.deleteOnExit();

        config.setProjectRoot(tempDir.getAbsolutePath());
        configManager.saveConfig(config);

        ApiController controller = new ApiController(configManager, new com.code.analyst.parser.JavaSourceParser(), new com.code.analyst.parser.SqlExtractor(), null);

        // Invoke ensureIndexInitialized via reflection
        java.lang.reflect.Method ensureIndex = ApiController.class.getDeclaredMethod("ensureIndexInitialized", String.class);
        ensureIndex.setAccessible(true);
        ensureIndex.invoke(controller, tempDir.getAbsolutePath());

        // Read javaFileByNameCache and xmlFileByNameCache via reflection
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

    @Test
    public void testExpandTree() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
        configManager.saveConfig(config);

        com.code.analyst.parser.JavaSourceParser parser = new com.code.analyst.parser.JavaSourceParser();
        com.code.analyst.parser.SqlExtractor extractor = new com.code.analyst.parser.SqlExtractor();
        ApiController controller = new ApiController(configManager, parser, extractor, null);

        java.util.Map<String, String> req = new java.util.HashMap<>();
        req.put("className", "com.omp.marketing.service.shortlink.impl.ShortLinkServiceImpl");
        req.put("methodName", "detail");

        org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> res = controller.expandTree(req);
        System.out.println("EXPAND TREE RESULT: " + res.getBody());
        boolean hasMarketingRepo = res.getBody().stream().anyMatch(node -> 
            "com.omp.marketing.infrastructure.repository.impl.shortlink.ShortLinkRepoImpl".equals(node.get("className"))
        );
        assertTrue(hasMarketingRepo, "Should resolve to marketing ShortLinkRepoImpl");
    }

    @Test
    public void testFindJavaFileForRepoImpl() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
        configManager.saveConfig(config);

        ApiController controller = new ApiController(configManager, new com.code.analyst.parser.JavaSourceParser(), new com.code.analyst.parser.SqlExtractor(), null);

        java.lang.reflect.Method findJavaFileMethod = ApiController.class.getDeclaredMethod("findJavaFile", String.class, String.class, String.class);
        findJavaFileMethod.setAccessible(true);
        
        java.util.Optional<java.nio.file.Path> res = (java.util.Optional<java.nio.file.Path>) findJavaFileMethod.invoke(
            controller, 
            "/Users/hanjie/IdeaProjects/charging-ionchi", 
            "ShortLinkRepoImpl", 
            "com.omp.marketing.core.infrastructure.repository.shortlink.ShortLinkRepo"
        );
        assertTrue(res.isPresent());
        System.out.println("RESOLVED REPO IMPL PATH: " + res.get());
    }

    @Test
    public void testFindJavaFileForRepoImplWithServiceRef() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
        configManager.saveConfig(config);

        ApiController controller = new ApiController(configManager, new com.code.analyst.parser.JavaSourceParser(), new com.code.analyst.parser.SqlExtractor(), null);

        java.lang.reflect.Method findJavaFileMethod = ApiController.class.getDeclaredMethod("findJavaFile", String.class, String.class, String.class);
        findJavaFileMethod.setAccessible(true);
        
        String servicePath = "/Users/hanjie/IdeaProjects/charging-ionchi/omp-trading/omp-marketing/omp-marketing-service/src/main/java/com/omp/marketing/service/shortlink/impl/ShortLinkServiceImpl.java";
        java.util.Optional<java.nio.file.Path> res = (java.util.Optional<java.nio.file.Path>) findJavaFileMethod.invoke(
            controller, 
            "/Users/hanjie/IdeaProjects/charging-ionchi", 
            "ShortLinkRepoImpl", 
            servicePath
        );
        
        assertTrue(res.isPresent());
        System.out.println("RESOLVED REPO IMPL WITH SERVICE REF PATH: " + res.get());
    }

    @Test
    public void testGetTableNameFromMapperAnnotation() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
        configManager.saveConfig(config);
        
        ApiController controller = new ApiController(configManager, new com.code.analyst.parser.JavaSourceParser(), new com.code.analyst.parser.SqlExtractor(), null);
        
        java.lang.reflect.Method getTableName = ApiController.class.getDeclaredMethod("getTableNameFromMapper", String.class, String.class);
        getTableName.setAccessible(true);
        
        String tableName = (String) getTableName.invoke(
            controller, 
            "/Users/hanjie/IdeaProjects/charging-ionchi",
            "com.omp.marketing.infrastructure.repository.impl.mybatis.shortlink.ShortLinkMapper"
        );
        assertEquals("t_marketing_short_link", tableName);

        java.lang.reflect.Method getFallback = ApiController.class.getDeclaredMethod("getFallbackDbOps", String.class, String.class, String.class, String.class);
        getFallback.setAccessible(true);
        
        java.util.List<com.code.analyst.parser.DbOperation> dbOps = (java.util.List<com.code.analyst.parser.DbOperation>) getFallback.invoke(
            controller,
            "/Users/hanjie/IdeaProjects/charging-ionchi",
            "ShortLinkMapper",
            "com.omp.marketing.infrastructure.repository.impl.mybatis.shortlink.ShortLinkMapper",
            "selectById"
        );
        assertEquals(1, dbOps.size());
        assertEquals("t_marketing_short_link", dbOps.get(0).getTableName());
        assertEquals("SELECT * FROM t_marketing_short_link WHERE id = ?", dbOps.get(0).getSql());
    }

    @Test
    public void testResolveFeignClientImplementation() throws Exception {
        com.code.analyst.config.ConfigManager configManager = new com.code.analyst.config.ConfigManager();
        com.code.analyst.config.AppConfig config = new com.code.analyst.config.AppConfig();
        config.setProjectRoot("/Users/hanjie/IdeaProjects/charging-ionchi");
        configManager.saveConfig(config);

        ApiController controller = new ApiController(configManager, new com.code.analyst.parser.JavaSourceParser(), new com.code.analyst.parser.SqlExtractor(), null);

        java.lang.reflect.Method resolveImplementation = ApiController.class.getDeclaredMethod("resolveImplementation", String.class, String.class, String.class);
        resolveImplementation.setAccessible(true);

        String resolved = (String) resolveImplementation.invoke(
            controller, 
            "/Users/hanjie/IdeaProjects/charging-ionchi", 
            "InvoiceRpcFeign", 
            "com.omp.finance.api.invoice.InvoiceRpcFeign"
        );
        assertEquals("com.omp.finance.intf.rpc.invoice.InvoiceRpcController", resolved);
    }
}
