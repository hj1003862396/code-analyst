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
        if (url == null || url.isEmpty()) {
            url = "https://api.gptsapi.net/";
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "v1/chat/completions";

        String prompt = buildPrompt(className, methodName, sourceCode, sqlContext);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel() != null ? config.getModel() : "gpt-5.5");

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestBody.put("messages", messages);
        System.out.println("param : " + prompt);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            System.out.println("response: " + response);
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
