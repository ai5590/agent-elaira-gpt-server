package com.example.dialogueapi.dto;

public record AudioTranscriptionResponse(
        boolean success,
        String text,
        String source,
        String language,
        String originalFileName
) {
}
