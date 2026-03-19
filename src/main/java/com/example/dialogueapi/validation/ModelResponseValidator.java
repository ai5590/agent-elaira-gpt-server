package com.example.dialogueapi.validation;

import com.example.dialogueapi.exception.InvalidModelResponseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ModelResponseValidator {

    private final ObjectMapper objectMapper;

    public ModelResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode validateAndParse(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new InvalidModelResponseException("Model response is empty");
        }

        final JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException exception) {
            throw new InvalidModelResponseException("Model response is not valid JSON");
        }

        if (!(rootNode instanceof ObjectNode objectNode)) {
            throw new InvalidModelResponseException("Model response must be a JSON object");
        }

        JsonNode stepDebug = objectNode.get("stepDebug");
        JsonNode dialogGoal = objectNode.get("dialogGoal");
        JsonNode actions = objectNode.get("actions");

        if (stepDebug == null || !stepDebug.isTextual()) {
            throw new InvalidModelResponseException("Field stepDebug must be a string");
        }
        if (dialogGoal == null || !dialogGoal.isTextual()) {
            throw new InvalidModelResponseException("Field dialogGoal must be a string");
        }
        if (actions == null || !actions.isArray()) {
            throw new InvalidModelResponseException("Field actions must be an array");
        }

        for (JsonNode actionNode : actions) {
            if (!actionNode.isObject()) {
                throw new InvalidModelResponseException("Each action must be an object");
            }
            JsonNode command = actionNode.get("command");
            JsonNode description = actionNode.get("description");
            JsonNode params = actionNode.get("params");

            if (command == null || !command.isTextual()) {
                throw new InvalidModelResponseException("Action field command must be a string");
            }
            if (description == null || !description.isTextual()) {
                throw new InvalidModelResponseException("Action field description must be a string");
            }
            if (params == null || !params.isObject()) {
                throw new InvalidModelResponseException("Action field params must be an object");
            }
        }

        return objectNode;
    }
}
