package com.example.dialogueapi.exception;

import com.example.dialogueapi.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        log.error("API exception: status={}, message={}", exception.getStatus(), exception.getMessage(), exception);
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(false, exception.getMessage(), rootCauseMessage(exception)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("Validation exception: {}", message, exception);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(false, message, rootCauseMessage(exception)));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        String message = "Invalid request payload";
        if (exception instanceof MissingServletRequestPartException missingPartException) {
            message = "Required multipart field is missing: " + missingPartException.getRequestPartName();
        } else if (exception instanceof MissingServletRequestParameterException missingParameterException) {
            message = "Required request parameter is missing: " + missingParameterException.getParameterName();
        }
        log.error("Bad request exception: {}", message, exception);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(false, message, rootCauseMessage(exception)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled application error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(false, "Internal server error", rootCauseMessage(exception)));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return (message == null || message.isBlank()) ? current.getClass().getName() : message;
    }
}
