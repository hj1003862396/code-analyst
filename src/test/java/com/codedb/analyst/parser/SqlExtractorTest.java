package com.codedb.analyst.parser;

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
