package com.example.dialogueapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DialogueStepRequest(
        @NotBlank(message = "dialogId is required")
        String dialogId,
        @Min(value = 1, message = "stepId must be greater than or equal to 1")
        int stepId,
        @NotBlank(message = "prompt is required")
        String prompt,
        String model
) {
}
