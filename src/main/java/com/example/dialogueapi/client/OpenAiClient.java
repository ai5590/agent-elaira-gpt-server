package com.example.dialogueapi.client;

import com.example.dialogueapi.config.AppProperties;
import com.example.dialogueapi.exception.ApiException;
import com.example.dialogueapi.service.OpenAiApiKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-5.4-nano"
    );

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

    public OpenAiClient(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            AppProperties properties,
            OpenAiApiKeyProvider apiKeyProvider
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        RestClient.Builder builder = RestClient.builder(restTemplateBuilder.build())
                .baseUrl(stripTrailingSlash(properties.getOpenai().getBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (!properties.getOpenai().isMockEnabled()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyProvider.getRequiredApiKey());
        }

        this.restClient = builder.build();
    }

    public OpenAiCallResult createResponse(
            String prompt,
            String previousResponseId,
            String correctiveInstruction,
            String requestedModel
    ) {
        String resolvedModel = resolveModel(requestedModel);
        if (properties.getOpenai().isMockEnabled()) {
            return createMockResponse(prompt, previousResponseId, correctiveInstruction, resolvedModel);
        }

        JsonNode requestBody = buildRequest(prompt, previousResponseId, correctiveInstruction, resolvedModel);
        if (isPacketDebugEnabled()) {
            log.debug("Outgoing packet -> OpenAI POST /v1/responses: {}", toCompactJson(requestBody));
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/v1/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            Objects.requireNonNull(response, "OpenAI response body is empty");
            if (isPacketDebugEnabled()) {
                log.debug("Incoming packet <- OpenAI POST /v1/responses: {}", toCompactJson(response));
            }

            String responseId = textOrNull(response.path("id"));
            String outputText = extractOutputText(response);
            TokenUsage usage = extractUsage(response.path("usage"));
            return new OpenAiCallResult(responseId, outputText, usage, resolvedModel, response);
        } catch (RestClientException exception) {
            log.error("OpenAI responses request failed", exception);
            throw exception;
        }
    }

    private OpenAiCallResult createMockResponse(
            String prompt,
            String previousResponseId,
            String correctiveInstruction,
            String resolvedModel
    ) {
        log.info("OpenAI mock mode is enabled; returning a synthetic response");

        var response = objectMapper.createObjectNode();
        response.put("id", "mock-response-" + System.currentTimeMillis());
        response.put("output_text", """
                {
                  "stepDebug": "Mock mode: OpenAI call was not sent to the external API",
                  "dialogGoal": "Validate local dialogue-api integration",
                  "actions": [
                    {
                      "command": "end_dialogue",
                      "description": "Finish mock dialogue successfully",
                      "params": {
                        "status": "success",
                        "message": "Mock mode completed request locally"
                      }
                    }
                  ]
                }
                """.trim());

        var usage = response.putObject("usage");
        usage.put("input_tokens", Math.max(1, prompt.length() / 4));
        usage.put("output_tokens", correctiveInstruction == null ? 32 : 48);
        usage.put("total_tokens", usage.path("input_tokens").asInt() + usage.path("output_tokens").asInt());
        if (StringUtils.hasText(previousResponseId)) {
            response.put("previous_response_id", previousResponseId);
        }
        if (isPacketDebugEnabled()) {
            log.debug("Outgoing packet -> OpenAI POST /v1/responses [mock mode]: {}", toCompactJson(buildRequest(prompt, previousResponseId, correctiveInstruction, resolvedModel)));
            log.debug("Incoming packet <- OpenAI POST /v1/responses [mock mode]: {}", toCompactJson(response));
        }

        return new OpenAiCallResult(
                response.path("id").asText(),
                response.path("output_text").asText(),
                extractUsage(response.path("usage")),
                resolvedModel,
                response
        );
    }

    private JsonNode buildRequest(
            String prompt,
            String previousResponseId,
            String correctiveInstruction,
            String resolvedModel
    ) {
        var root = objectMapper.createObjectNode();
        root.put("model", resolvedModel);
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

    public String resolveRequestedModel(String requestedModel) {
        return resolveModel(requestedModel);
    }

    private String resolveModel(String requestedModel) {
        String raw = StringUtils.hasText(requestedModel) ? requestedModel : properties.getOpenai().getModel();
        String canonical = canonicalModel(raw);
        if (!SUPPORTED_MODELS.contains(canonical)) {
            String supported = SUPPORTED_MODELS.stream().sorted().collect(Collectors.joining(", "));
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported model '%s'. Supported models: %s".formatted(raw, supported)
            );
        }
        return canonical;
    }

    private String canonicalModel(String rawModel) {
        if (!StringUtils.hasText(rawModel)) {
            return "";
        }
        String normalized = rawModel.trim().toLowerCase()
                .replace("_", "-")
                .replace(" ", "-")
                .replace("gpt5.4", "gpt-5.4")
                .replace("gpt-5-4", "gpt-5.4")
                .replace("--", "-");
        if ("mini".equals(normalized)) {
            return "gpt-5.4-mini";
        }
        if ("nano".equals(normalized)) {
            return "gpt-5.4-nano";
        }
        return normalized;
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

    private boolean isPacketDebugEnabled() {
        return properties.getLogging().isDebugPackets() && log.isDebugEnabled();
    }

    private String toCompactJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return String.valueOf(node);
        }
    }

    public record OpenAiCallResult(
            String responseId,
            String outputText,
            TokenUsage tokenUsage,
            String model,
            JsonNode rawResponse
    ) {
    }

    public record TokenUsage(int requestTokens, int responseTokens, int totalTokens) {
    }
}
