package com.example.dialogueapi.validation;

import com.example.dialogueapi.exception.InvalidModelResponseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelResponseValidatorTest {

    private final ModelResponseValidator validator = new ModelResponseValidator(new ObjectMapper());

    @Test
    void shouldValidateStrictJson() {
        String json = """
                {
                  "stepDebug": "debug",
                  "dialogGoal": "goal",
                  "actions": [
                    {
                      "command": "run_command",
                      "description": "Run build",
                      "params": {
                        "command": "./gradlew build"
                      }
                    }
                  ]
                }
                """;

        var node = validator.validateAndParse(json);

        assertEquals("debug", node.get("stepDebug").asText());
        assertEquals("goal", node.get("dialogGoal").asText());
        assertEquals(1, node.get("actions").size());
    }

    @Test
    void shouldRejectInvalidActionParams() {
        String json = """
                {
                  "stepDebug": "debug",
                  "dialogGoal": "goal",
                  "actions": [
                    {
                      "command": "run_command",
                      "description": "Run build",
                      "params": "wrong"
                    }
                  ]
                }
                """;

        assertThrows(InvalidModelResponseException.class, () -> validator.validateAndParse(json));
    }
}
