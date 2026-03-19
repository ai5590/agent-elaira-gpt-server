package com.example.dialogueapi.client;

import com.example.dialogueapi.config.AppProperties;
import com.example.dialogueapi.service.OpenAiApiKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private static final String BASE_SYSTEM_INSTRUCTION = """
            You are a backend component. Respond with strictly valid JSON only.
            Do not wrap JSON in markdown. Do not add any text before or after the JSON.
            Required JSON structure:
            {
              "stepDebug": "string",
              "dialogGoal": "string",
              "actions": [
                {
                  "command": "string",
                  "description": "string",
                  "params": {}
                }
              ]
            }
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final String resolvedApiKey;

    public OpenAiClient(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            AppProperties properties,
            OpenAiApiKeyProvider apiKeyProvider
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.resolvedApiKey = apiKeyProvider.getRequiredApiKey();
        this.restClient = RestClient.builder(restTemplateBuilder.build())
                .baseUrl(stripTrailingSlash(properties.getOpenai().getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resolvedApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public OpenAiCallResult createResponse(String prompt, String previousResponseId, String correctiveInstruction) {
        JsonNode requestBody = buildRequest(prompt, previousResponseId, correctiveInstruction);
        JsonNode response = restClient.post()
                .uri("/v1/responses")
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        Objects.requireNonNull(response, "OpenAI response body is empty");

        String responseId = textOrNull(response.path("id"));
        String outputText = extractOutputText(response);
        TokenUsage usage = extractUsage(response.path("usage"));

        return new OpenAiCallResult(responseId, outputText, usage, response);
    }

    private JsonNode buildRequest(String prompt, String previousResponseId, String correctiveInstruction) {
        var root = objectMapper.createObjectNode();
        root.put("model", properties.getOpenai().getModel());
        if (StringUtils.hasText(previousResponseId)) {
            root.put("previous_response_id", previousResponseId);
        }

        var input = root.putArray("input");
        addMessage(input, "system", BASE_SYSTEM_INSTRUCTION);
        if (StringUtils.hasText(correctiveInstruction)) {
            addMessage(input, "system", correctiveInstruction);
        }
        addMessage(input, "user", prompt);
        return root;
    }

    private void addMessage(com.fasterxml.jackson.databind.node.ArrayNode input, String role, String text) {
        var messageNode = input.addObject();
        messageNode.put("role", role);
        var contentArray = messageNode.putArray("content");
        var contentNode = contentArray.addObject();
        contentNode.put("type", "input_text");
        contentNode.put("text", text);
    }

    private String extractOutputText(JsonNode response) {
        String topLevel = textOrNull(response.path("output_text"));
        if (StringUtils.hasText(topLevel)) {
            return topLevel;
        }

        List<String> texts = new ArrayList<>();
        JsonNode outputArray = response.path("output");
        if (outputArray.isArray()) {
            for (JsonNode outputItem : outputArray) {
                JsonNode contentArray = outputItem.path("content");
                if (contentArray.isArray()) {
                    for (JsonNode contentItem : contentArray) {
                        String text = textOrNull(contentItem.path("text"));
                        if (StringUtils.hasText(text)) {
                            texts.add(text);
                        }
                    }
                }
            }
        }

        String combined = String.join("", texts).trim();
        if (!StringUtils.hasText(combined)) {
            log.warn("OpenAI response does not contain extractable text payload");
        }
        return combined;
    }

    private TokenUsage extractUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            log.warn("OpenAI response usage is absent");
            return new TokenUsage(0, 0, 0);
        }

        int inputTokens = readInt(usageNode, "input_tokens", "prompt_tokens");
        int outputTokens = readInt(usageNode, "output_tokens", "completion_tokens");
        int totalTokens = readInt(usageNode, "total_tokens");
        if (totalTokens == 0 && (inputTokens > 0 || outputTokens > 0)) {
            totalTokens = inputTokens + outputTokens;
        }
        return new TokenUsage(inputTokens, outputTokens, totalTokens);
    }

    private int readInt(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.path(fieldName);
            if (field.isInt() || field.isLong()) {
                return field.asInt();
            }
        }
        return 0;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record OpenAiCallResult(
            String responseId,
            String outputText,
            TokenUsage tokenUsage,
            JsonNode rawResponse
    ) {
    }

    public record TokenUsage(int requestTokens, int responseTokens, int totalTokens) {
    }
}
