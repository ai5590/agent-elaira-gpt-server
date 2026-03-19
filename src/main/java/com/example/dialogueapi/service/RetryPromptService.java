package com.example.dialogueapi.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class RetryPromptService {

    private String retryPrompt;

    @PostConstruct
    public void loadPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/retry-invalid-json.txt");
        this.retryPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (this.retryPrompt.isBlank()) {
            throw new IllegalStateException("Retry prompt file is empty: prompts/retry-invalid-json.txt");
        }
    }

    public String getRetryPrompt() {
        return retryPrompt;
    }
}
