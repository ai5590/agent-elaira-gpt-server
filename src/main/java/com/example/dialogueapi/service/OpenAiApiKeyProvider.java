package com.example.dialogueapi.service;

import com.example.dialogueapi.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpenAiApiKeyProvider {

    private final AppProperties properties;

    public OpenAiApiKeyProvider(AppProperties properties) {
        this.properties = properties;
    }

    public String getRequiredApiKey() {
        String configured = properties.getOpenai().getApiKey();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }

        String fromEnv = System.getenv("OPENAI_API_KEY");
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv.trim();
        }

        throw new IllegalStateException("OpenAI API key is missing. Set app.openai.api-key or OPENAI_API_KEY.");
    }
}
