package com.example.dialogueapi.exception;

import org.springframework.http.HttpStatus;

public class InvalidModelResponseException extends ApiException {

    public InvalidModelResponseException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
