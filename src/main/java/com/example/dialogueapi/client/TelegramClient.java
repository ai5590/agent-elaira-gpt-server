package com.example.dialogueapi.client;

import com.example.dialogueapi.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

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
        Map<String, String> requestBody = Map.of(
                "chat_id", chatId,
                "text", message
        );
        if (isPacketDebugEnabled()) {
            log.debug("Outgoing packet -> Telegram POST /bot{{token}}/sendMessage: {}", requestBody);
        }

        try {
            JsonNode response = restClient.post()
                    .uri("https://api.telegram.org/bot{token}/sendMessage", botToken)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            if (isPacketDebugEnabled()) {
                log.debug("Incoming packet <- Telegram POST /bot{{token}}/sendMessage: {}", response);
            }
        } catch (RestClientException exception) {
            log.error("Telegram sendMessage request failed", exception);
            throw exception;
        }
    }

    private boolean isPacketDebugEnabled() {
        return properties.getLogging().isDebugPackets() && log.isDebugEnabled();
    }
}
