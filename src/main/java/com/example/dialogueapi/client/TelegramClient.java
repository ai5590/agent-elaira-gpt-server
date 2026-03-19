package com.example.dialogueapi.client;

import com.example.dialogueapi.config.AppProperties;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class TelegramClient {

    private final AppProperties properties;
    private final RestClient restClient;

    public TelegramClient(RestTemplateBuilder restTemplateBuilder, AppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder(restTemplateBuilder.build())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isEnabled() {
        return StringUtils.hasText(properties.getTelegram().getBotToken())
                && StringUtils.hasText(properties.getTelegram().getChatId());
    }

    public void sendMessage(String message) {
        String botToken = properties.getTelegram().getBotToken();
        String chatId = properties.getTelegram().getChatId();
        restClient.post()
                .uri("https://api.telegram.org/bot{token}/sendMessage", botToken)
                .body(Map.of(
                        "chat_id", chatId,
                        "text", message
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
