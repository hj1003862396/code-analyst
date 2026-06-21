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
import java.util.Map;
import java.util.HashMap;

@Service
public class SqlExtractor {

    private final Map<String, XmlSqlCache> xmlCache = new java.util.concurrent.ConcurrentHashMap<>();

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
            // Disable DTD loading over the network
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

    public List<DbOperation> extractDbOperations(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // Clean XML tags and MyBatis variable placeholders
            String cleanSql = sql.replaceAll("<[^>]+>", " ")
                    .replaceAll("#\\{[^\\}]+\\}", "?")
                    .replaceAll("\\$\\{[^\\}]+\\}", "?")
                    .trim();

            Statement stmt = CCJSqlParserUtil.parse(cleanSql);
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
                if (update.getColumns() != null) {
                    for (net.sf.jsqlparser.schema.Column col : update.getColumns()) {
                        columns.add(col.getColumnName());
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
            // Fallback keywords logic if parsing fails
            List<DbOperation> fallback = new ArrayList<>();
            String lower = sql.toLowerCase();
            if (lower.contains("select") && lower.contains("from")) {
                fallback.add(new DbOperation("unknown_table", "SELECT", Collections.emptyList(), "", sql));
            } else if (lower.contains("insert") && lower.contains("into")) {
                fallback.add(new DbOperation("unknown_table", "INSERT", Collections.emptyList(), "", sql));
            } else if (lower.contains("update")) {
                fallback.add(new DbOperation("unknown_table", "UPDATE", Collections.emptyList(), "", sql));
            } else if (lower.contains("delete") && lower.contains("from")) {
                fallback.add(new DbOperation("unknown_table", "DELETE", Collections.emptyList(), "", sql));
            }
            return fallback;
        }
    }
}
