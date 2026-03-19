package com.example.dialogueapi.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record DialogueStepResponse(
        boolean success,
        String dialogId,
        int stepId,
        ObjectNode data
) {
}
