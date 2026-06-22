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

    private String lastProjectRoot = null;
    private final Map<String, List<Path>> javaFileByNameCache = new HashMap<>();
    private final Map<String, Path> xmlFileByNameCache = new HashMap<>();

    public ApiController(ConfigManager configManager, JavaSourceParser sourceParser, SqlExtractor sqlExtractor, LlmService llmService) {
        this.configManager = configManager;
        this.sourceParser = sourceParser;
        this.sqlExtractor = sqlExtractor;
        this.llmService = llmService;
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody AppConfig config) {
        configManager.saveConfig(config);
        synchronized (this) {
            lastProjectRoot = null;
            javaFileByNameCache.clear();
            xmlFileByNameCache.clear();
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "配置保存成功");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/config")
    public ResponseEntity<AppConfig> getConfig() {
        return ResponseEntity.ok(configManager.getConfig());
    }

    @PostMapping("/tree/initialize")
    public ResponseEntity<Map<String, Object>> initializeTree(@RequestBody Map<String, String> request) {
        String className = request.get("className");
        String methodName = request.get("methodName");

        synchronized (this) {
            lastProjectRoot = null;
            javaFileByNameCache.clear();
            xmlFileByNameCache.clear();
        }

        Map<String, Object> node = new HashMap<>();
        node.put("id", className + "#" + methodName);
        node.put("label", className.substring(className.lastIndexOf('.') + 1) + "." + methodName);
        node.put("className", className);
        node.put("methodName", methodName);
        node.put("isMapper", false);
        
        AppConfig config = configManager.getConfig();
        String root = config.getProjectRoot();
        List<DbOperation> dbOps = Collections.emptyList();
        if (root != null && !root.isEmpty()) {
            dbOps = getTransitiveDbOperations(root, className, methodName, new HashSet<>());
        }
        node.put("dbOperations", dbOps);
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

            // 1. Find the Java file for target class
            Optional<Path> fileOpt = findJavaFileByFqName(root, className);
            if (fileOpt.isEmpty()) {
                return ResponseEntity.ok(result);
            }

            String filePath = fileOpt.get().toString();

            // 2. Parse method calls in target method
            List<MethodCallInfo> calls = sourceParser.parseMethodCalls(filePath, methodName);
            for (MethodCallInfo call : calls) {
                Map<String, Object> node = new HashMap<>();
                String type = call.getObjectType();
                String method = call.getMethodName();

                // If type is "Unknown", heuristic: if the objectName contains mapper, mapper is likely
                boolean isMapper = type.toLowerCase().contains("mapper") || call.getObjectName().toLowerCase().contains("mapper");
                
                // Try to resolve the actual class type if "Unknown"
                if ("Unknown".equals(type)) {
                    if (call.getObjectName().toLowerCase().contains("service")) {
                        type = uppercaseFirst(call.getObjectName());
                    } else if (call.getObjectName().toLowerCase().contains("mapper")) {
                        type = uppercaseFirst(call.getObjectName());
                    }
                }

                String fullTargetClassName = resolveFullClassName(root, type, filePath);
                if (!isMapper) {
                    String resolvedFull = resolveImplementation(root, type, fullTargetClassName);
                    if (!resolvedFull.equals(fullTargetClassName)) {
                        fullTargetClassName = resolvedFull;
                        type = resolvedFull.substring(resolvedFull.lastIndexOf('.') + 1);
                    }
                }

                List<DbOperation> dbOps = new ArrayList<>();

                // If Mapper call, resolve XML SQL or MyBatis-Plus annotation table operations
                if (isMapper) {
                    Optional<Path> xmlOpt = findXmlFile(root, type);
                    if (xmlOpt.isPresent()) {
                        String sql = sqlExtractor.findSqlFromXml(xmlOpt.get().toString(), method);
                        dbOps = sqlExtractor.extractDbOperations(sql);
                    }
                    
                    if (dbOps.isEmpty()) {
                        dbOps = getFallbackDbOps(root, type, fullTargetClassName, method);
                    }
                } else {
                    dbOps = getTransitiveDbOperations(root, fullTargetClassName, method, new HashSet<>());
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

    private List<DbOperation> getTransitiveDbOperations(String root, String className, String methodName, Set<String> visited) {
        String key = className + "#" + methodName;
        if (visited.contains(key)) {
            return Collections.emptyList();
        }
        visited.add(key);

        List<DbOperation> ops = new ArrayList<>();
        
        Optional<Path> fileOpt = findJavaFileByFqName(root, className);
        if (fileOpt.isEmpty()) {
            return ops;
        }

        try {
            List<MethodCallInfo> calls = sourceParser.parseMethodCalls(fileOpt.get().toString(), methodName);
            for (MethodCallInfo call : calls) {
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
                    List<DbOperation> dbOps = new ArrayList<>();
                    Optional<Path> xmlOpt = findXmlFile(root, type);
                    if (xmlOpt.isPresent()) {
                        String sql = sqlExtractor.findSqlFromXml(xmlOpt.get().toString(), method);
                        dbOps = sqlExtractor.extractDbOperations(sql);
                    }
                    
                    if (dbOps.isEmpty()) {
                        String fullTargetClassName = resolveFullClassName(root, type, fileOpt.get().toString());
                        dbOps = getFallbackDbOps(root, type, fullTargetClassName, method);
                    }
                    ops.addAll(dbOps);
                } else {
                    String fullTargetClassName = resolveFullClassName(root, type, fileOpt.get().toString());
                    String resolvedFull = resolveImplementation(root, type, fullTargetClassName);
                    if (!resolvedFull.equals(fullTargetClassName)) {
                        fullTargetClassName = resolvedFull;
                        type = resolvedFull.substring(resolvedFull.lastIndexOf('.') + 1);
                    }
                    ops.addAll(getTransitiveDbOperations(root, fullTargetClassName, method, visited));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Deduplicate operations
        Map<String, DbOperation> dedup = new HashMap<>();
        for (DbOperation op : ops) {
            String opKey = op.getTableName() + "#" + op.getOperationType() + "#" + String.join(",", op.getColumns()) + "#" + op.getWhereCondition();
            dedup.put(opKey, op);
        }
        return new ArrayList<>(dedup.values());
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

            // Fetch XML SQL context
            StringBuilder sqlCtx = new StringBuilder();
            if (className.toLowerCase().contains("mapper")) {
                Optional<Path> xmlOpt = findXmlFile(root, simpleName);
                if (xmlOpt.isPresent()) {
                    String sql = sqlExtractor.findSqlFromXml(xmlOpt.get().toString(), methodName);
                    if (sql != null && !sql.trim().isEmpty()) {
                        sqlCtx.append("XML SQL: ").append(sql);
                    }
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
        return findJavaFile(rootPath, className, null);
    }

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

    private List<Path> findJavaFiles(String rootPath, String className) {
        ensureIndexInitialized(rootPath);
        return javaFileByNameCache.getOrDefault(className, Collections.emptyList());
    }

    private Optional<Path> findJavaFile(String rootPath, String className, String sourceOrRef) {
        List<Path> candidates = findJavaFiles(rootPath, className);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        if (sourceOrRef == null || sourceOrRef.isEmpty()) {
            return Optional.of(candidates.get(0));
        }

        if (sourceOrRef.endsWith(".java") || sourceOrRef.contains("/") || sourceOrRef.contains("\\")) {
            String sourcePackage = "";
            List<String> sourceImports = new ArrayList<>();
            try {
                List<String> lines = Files.readAllLines(Path.of(sourceOrRef));
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("package ")) {
                        sourcePackage = trimmed.replace("package ", "").replace(";", "").trim();
                    } else if (trimmed.startsWith("import ")) {
                        sourceImports.add(trimmed.replace("import ", "").replace(";", "").trim());
                    }
                }
            } catch (Exception ignored) {}

            Path bestPath = candidates.get(0);
            int bestScore = -1;
            for (Path p : candidates) {
                String fqName = getFqNameFromFile(rootPath, p, className);
                String pkg = fqName.contains(".") ? fqName.substring(0, fqName.lastIndexOf('.')) : "";
                
                int score = 0;
                if (sourceImports.contains(fqName)) {
                    score = 300;
                } else if (pkg.equals(sourcePackage)) {
                    score = 200;
                } else if (sourceImports.contains(pkg + ".*")) {
                    score = 100;
                }
                score += commonPackagePrefixLength(sourcePackage, pkg);
                
                if (score > bestScore) {
                    bestScore = score;
                    bestPath = p;
                }
            }
            return Optional.of(bestPath);
        } else {
            Path bestPath = candidates.get(0);
            int maxCommonPrefix = -1;
            for (Path p : candidates) {
                String fqName = getFqNameFromFile(rootPath, p, className);
                int score = commonPackagePrefixLength(sourceOrRef, fqName);
                if (score > maxCommonPrefix) {
                    maxCommonPrefix = score;
                    bestPath = p;
                }
            }
            return Optional.of(bestPath);
        }
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

    private String getFqNameFromFile(String rootPath, Path path, String className) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (line.trim().startsWith("package ")) {
                    return line.replace("package ", "").replace(";", "").trim() + "." + className;
                }
            }
        } catch (Exception ignored) {}
        return className;
    }

    private int commonPackagePrefixLength(String fqName1, String fqName2) {
        String[] parts1 = fqName1.split("\\.");
        String[] parts2 = fqName2.split("\\.");
        int matchCount = 0;
        int minLen = Math.min(parts1.length, parts2.length);
        for (int i = 0; i < minLen - 1; i++) {
            if (parts1[i].equals(parts2[i])) {
                matchCount++;
            } else {
                break;
            }
        }
        return matchCount;
    }

    private Optional<Path> findXmlFile(String rootPath, String mapperName) {
        ensureIndexInitialized(rootPath);
        Path xmlPath = xmlFileByNameCache.get(mapperName + ".xml");
        return Optional.ofNullable(xmlPath);
    }

    private String resolveFullClassName(String rootPath, String className) {
        return resolveFullClassName(rootPath, className, null);
    }

    private String resolveFullClassName(String rootPath, String className, String sourceFilePath) {
        Optional<Path> path = findJavaFile(rootPath, className, sourceFilePath);
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

      private String uppercaseFirst(String str) {
          if (str == null || str.isEmpty()) return str;
          return str.substring(0, 1).toUpperCase() + str.substring(1);
      }

      private String camelToSnake(String str) {
          if (str == null || str.isEmpty()) return str;
          return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
      }

      private Optional<Path> findImplementationBySearch(String rootPath, String interfaceSimpleName, String interfaceFqName) {
          try (java.util.stream.Stream<Path> walk = Files.walk(Path.of(rootPath))) {
              java.util.List<Path> candidates = walk.filter(p -> p.toString().endsWith(".java"))
                      .parallel()
                      .filter(p -> {
                          try {
                              String content = Files.readString(p);
                              return content.contains("implements") && content.contains(interfaceSimpleName);
                          } catch (Exception e) {
                              return false;
                          }
                      })
                      .collect(java.util.stream.Collectors.toList());

              for (Path p : candidates) {
                  try {
                      com.github.javaparser.ast.CompilationUnit cu = com.github.javaparser.StaticJavaParser.parse(p);
                      boolean isMatch = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                              .anyMatch(cid -> cid.getImplementedTypes().stream()
                                      .anyMatch(t -> t.getNameAsString().equals(interfaceSimpleName)));
                      if (isMatch) {
                          return Optional.of(p);
                      }
                  } catch (Exception ignored) {}
              }
          } catch (Exception e) {
              e.printStackTrace();
          }
          return Optional.empty();
      }

      private String resolveImplementation(String root, String type, String fullTargetClassName) {
          if (fullTargetClassName == null || fullTargetClassName.isEmpty() || fullTargetClassName.contains("Impl")) {
              return fullTargetClassName;
          }

          // 1. 启发式命名通道 (直接寻找 *Impl)
          String implClass = type + "Impl";
          Optional<Path> implPath = findJavaFile(root, implClass, fullTargetClassName);
          if (implPath.isPresent()) {
              return resolveFullClassName(root, implClass, implPath.get().toString());
          }

          // 2. 文本特征预过滤扫描通道
          Optional<Path> searchImplPath = findImplementationBySearch(root, type, fullTargetClassName);
          if (searchImplPath.isPresent()) {
              String searchImplClass = searchImplPath.get().getFileName().toString().replace(".java", "");
              return resolveFullClassName(root, searchImplClass, searchImplPath.get().toString());
          }

          return fullTargetClassName;
      }

      private String getTableNameFromMapper(String root, String fullMapperClassName) {
          Optional<Path> mapperPathOpt = findJavaFileByFqName(root, fullMapperClassName);
          if (mapperPathOpt.isEmpty()) {
              return null;
          }
          try {
              com.github.javaparser.ast.CompilationUnit cu = com.github.javaparser.StaticJavaParser.parse(mapperPathOpt.get().toFile());
              return cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                      .filter(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration::isInterface)
                      .flatMap(cid -> cid.getExtendedTypes().stream())
                      .filter(et -> et.getNameAsString().equals("BaseMapper"))
                      .flatMap(et -> et.getTypeArguments().map(java.util.Collection::stream).orElse(java.util.stream.Stream.empty()))
                      .findFirst()
                      .map(typeArg -> {
                          String entitySimpleName = typeArg.toString();
                          String entityFqName = resolveFullClassName(root, entitySimpleName, mapperPathOpt.get().toString());
                          Optional<Path> entityPathOpt = findJavaFileByFqName(root, entityFqName);
                          if (entityPathOpt.isPresent()) {
                              try {
                                  com.github.javaparser.ast.CompilationUnit entityCu = com.github.javaparser.StaticJavaParser.parse(entityPathOpt.get().toFile());
                                  return entityCu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                                          .filter(ecid -> !ecid.isInterface())
                                          .flatMap(ecid -> ecid.getAnnotations().stream())
                                          .filter(ann -> ann.getNameAsString().equals("TableName"))
                                          .findFirst()
                                          .map(ann -> {
                                              String val = ann.toString();
                                              java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(val);
                                              if (m.find()) {
                                                  return m.group(1);
                                              }
                                              return null;
                                          }).orElse(null);
                              } catch (Exception ignored) {}
                          }
                          return null;
                      }).orElse(null);
          } catch (Exception ignored) {}
          return null;
      }

      private List<DbOperation> getFallbackDbOps(String root, String type, String fullTargetClassName, String method) {
          if (method.equals("insert") || method.equals("selectById") || method.equals("updateById") || method.equals("deleteById") || method.equals("selectList") || method.equals("delete")) {
              String tableName = getTableNameFromMapper(root, fullTargetClassName);
              if (tableName == null || tableName.isEmpty()) {
                  tableName = "t_" + camelToSnake(type.replace("Mapper", "").replace("mapper", ""));
              }
              String opType = "UNKNOWN";
              String mockSql = "";
              if (method.contains("select")) {
                  opType = "SELECT";
                  if (method.equals("selectById")) {
                      mockSql = "SELECT * FROM " + tableName + " WHERE id = ?";
                  } else {
                      mockSql = "SELECT * FROM " + tableName;
                  }
              } else if (method.contains("insert")) {
                  opType = "INSERT";
                  mockSql = "INSERT INTO " + tableName + " (...) VALUES (...)";
              } else if (method.contains("update")) {
                  opType = "UPDATE";
                  mockSql = "UPDATE " + tableName + " SET ... WHERE id = ?";
              } else if (method.contains("delete")) {
                  opType = "DELETE";
                  if (method.equals("deleteById")) {
                      mockSql = "DELETE FROM " + tableName + " WHERE id = ?";
                  } else {
                      mockSql = "DELETE FROM " + tableName + " WHERE ...";
                  }
              }
              return List.of(new DbOperation(tableName, opType, Collections.emptyList(), "", mockSql));
          }
          return Collections.emptyList();
      }
  }
