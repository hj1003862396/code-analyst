package com.code.analyst.parser;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
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

    @Test
    public void testFindSqlWithInclude() throws Exception {
        SqlExtractor extractor = new SqlExtractor();
        File tempFile = File.createTempFile("MockMapperWithInclude", ".xml");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<mapper namespace=\"com.example.MockMapper\">\n" +
                "    <sql id=\"columns\">id, name, age</sql>\n" +
                "    <select id=\"getUser\">SELECT <include refid=\"columns\"/> FROM t_user WHERE id = #{id}</select>\n" +
                "    <update id=\"updateUser\">UPDATE t_user <set><if test=\"name != null\">name = #{name}</if></set> WHERE id = #{id}</update>\n" +
                "</mapper>\n"
            );
        }
        
        // 1. 验证 Include 展开
        String sql = extractor.findSqlFromXml(tempFile.getAbsolutePath(), "getUser");
        assertTrue(sql.contains("id, name, age"));
        
        List<DbOperation> ops = extractor.extractDbOperations(sql);
        assertEquals(1, ops.size());
        assertEquals("t_user", ops.get(0).getTableName());

        // 2. 验证 Update 语句因含有 <set>/<if> 无法用 JSqlParser 解析时的 Heuristic 提取表名
        String updateSql = extractor.findSqlFromXml(tempFile.getAbsolutePath(), "updateUser");
        List<DbOperation> updateOps = extractor.extractDbOperations(updateSql);
        assertEquals(1, updateOps.size());
        assertEquals("t_user", updateOps.get(0).getTableName());
    }
}
