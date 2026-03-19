package com.example.dialogueapi.dto;

public record ApiErrorResponse(boolean success, String error, String details) {

    public ApiErrorResponse(boolean success, String error) {
        this(success, error, null);
    }
}
