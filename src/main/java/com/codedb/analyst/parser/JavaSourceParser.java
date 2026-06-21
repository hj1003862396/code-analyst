package com.codedb.analyst.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
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
import java.util.stream.Collectors;

@Service
public class JavaSourceParser {

    public List<MethodCallInfo> parseMethodCalls(String filePath, String targetMethodName) throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        List<MethodCallInfo> calls = new ArrayList<>();

        // 1. Scan and cache class field types
        Map<String, String> fieldTypes = new HashMap<>();
        cu.findAll(FieldDeclaration.class).forEach(fd -> {
            for (VariableDeclarator vd : fd.getVariables()) {
                fieldTypes.put(vd.getNameAsString(), vd.getTypeAsString());
            }
        });

        // 2. Find target method
        cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(targetMethodName))
                .findFirst()
                .ifPresent(method -> {
                    // 3. Scan parameters and local variables inside the method
                    Map<String, String> localTypes = new HashMap<>(fieldTypes);
                    method.getParameters().forEach(param -> {
                        localTypes.put(param.getNameAsString(), param.getTypeAsString());
                    });
                    method.findAll(VariableDeclarator.class).forEach(vd -> {
                        localTypes.put(vd.getNameAsString(), vd.getTypeAsString());
                    });

                    // 4. Scan all method call expressions inside the method body
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr n, Void arg) {
                            super.visit(n, arg);
                            n.getScope().ifPresent(scope -> {
                                String objectName = scope.toString();
                                String callMethod = n.getNameAsString();
                                String objectType = localTypes.getOrDefault(objectName, "Unknown");
                                if (isIgnoredCall(objectName, objectType, callMethod)) {
                                    return;
                                }
                                calls.add(new MethodCallInfo(objectName, callMethod, objectType));
                            });
                        }
                    }, null);
                });

        return calls;
    }

    private boolean isIgnoredCall(String objectName, String objectType, String methodName) {
        if (objectName == null) return false;
        String lowerName = objectName.toLowerCase();
        
        // 过滤日志和标准流
        if (lowerName.equals("log") || lowerName.equals("logger") || lowerName.equals("system.out") || lowerName.equals("system.err") || lowerName.equals("out") || lowerName.equals("err")) {
            return true;
        }
        
        // 过滤标准 JDK 类型调用
        if ("Unknown".equals(objectType) && objectName.length() > 0 && Character.isUpperCase(objectName.charAt(0))) {
            objectType = objectName;
        }

        if (objectType != null) {
            String cleanType = objectType.replaceAll("<.*>", "");
            if (cleanType.equals("String") || cleanType.equals("List") || cleanType.equals("Map") || cleanType.equals("Set") 
                    || cleanType.equals("ArrayList") || cleanType.equals("HashMap") || cleanType.equals("HashSet") 
                    || cleanType.equals("Collections") || cleanType.equals("Objects") || cleanType.equals("Arrays") 
                    || cleanType.equals("Optional") || cleanType.equals("Stream") || cleanType.equals("Collectors") 
                    || cleanType.equals("Logger") || cleanType.equals("LoggerFactory") || cleanType.equals("System")
                    || cleanType.equals("BigDecimal") || cleanType.equals("Integer") || cleanType.equals("Long") 
                    || cleanType.equals("Boolean") || cleanType.equals("Double") || cleanType.equals("Character")
                    || cleanType.equals("BeanUtils") || cleanType.equals("BeanUtil") || cleanType.equals("CollUtil")
                    || cleanType.equals("CollectionUtils") || cleanType.equals("StringUtils")) {
                return true;
            }
            
            // 过滤 DTO, Entity, VO, PO, Req, Resp 等类型
            String lowerType = cleanType.toLowerCase();
            if (lowerType.endsWith("dto") || lowerType.endsWith("entity") || lowerType.endsWith("vo") 
                    || (lowerType.endsWith("po") && !lowerType.endsWith("repo")) 
                    || lowerType.endsWith("req") || lowerType.endsWith("resp") || lowerType.endsWith("request") || lowerType.endsWith("response")
                    || lowerType.endsWith("param") || lowerType.endsWith("params") || lowerType.endsWith("query")) {
                return true;
            }
        }
        
        return false;
    }

    public String getMethodSource(String filePath, String targetMethodName) throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(targetMethodName))
                .findFirst()
                .map(MethodDeclaration::toString)
                .orElse("");
    }

    public List<String> getMethodNames(String filePath) throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        return cu.findAll(MethodDeclaration.class).stream()
                .map(MethodDeclaration::getNameAsString)
                .distinct()
                .collect(Collectors.toList());
    }
}
