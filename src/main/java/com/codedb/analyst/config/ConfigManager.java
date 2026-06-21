package com.codedb.analyst.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ConfigManager {

    @Value("${analyst.project-root:}")
    private String projectRoot;

    @Value("${analyst.api-key:}")
    private String apiKey;

    @Value("${analyst.base-url:}")
    private String baseUrl;

    @Value("${analyst.model:}")
    private String model;

    private AppConfig currentConfig = new AppConfig();

    @PostConstruct
    public void init() {
        currentConfig.setProjectRoot(projectRoot);
        currentConfig.setApiKey(apiKey);
        currentConfig.setBaseUrl(baseUrl);
        currentConfig.setModel(model);
    }

    public synchronized void saveConfig(AppConfig config) {
        this.currentConfig = config;
    }

    public synchronized AppConfig getConfig() {
        return this.currentConfig;
    }
}
