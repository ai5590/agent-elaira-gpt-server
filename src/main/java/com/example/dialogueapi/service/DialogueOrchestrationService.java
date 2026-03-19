package com.example.dialogueapi.service;

import com.example.dialogueapi.client.OpenAiClient;
import com.example.dialogueapi.client.OpenAiClient.OpenAiCallResult;
import com.example.dialogueapi.dto.DialogueStepRequest;
import com.example.dialogueapi.dto.DialogueStepResponse;
import com.example.dialogueapi.entity.DialogueStepEntity;
import com.example.dialogueapi.exception.ApiException;
import com.example.dialogueapi.exception.InvalidModelResponseException;
import com.example.dialogueapi.repository.DialogueStepRepository;
import com.example.dialogueapi.validation.ModelResponseValidator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DialogueOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DialogueOrchestrationService.class);

    private final DialogueStepRepository dialogueStepRepository;
    private final OpenAiClient openAiClient;
    private final ModelResponseValidator modelResponseValidator;
    private final RetryPromptService retryPromptService;
    private final TelegramNotificationService telegramNotificationService;

    public DialogueOrchestrationService(
            DialogueStepRepository dialogueStepRepository,
            OpenAiClient openAiClient,
            ModelResponseValidator modelResponseValidator,
            RetryPromptService retryPromptService,
            TelegramNotificationService telegramNotificationService
    ) {
        this.dialogueStepRepository = dialogueStepRepository;
        this.openAiClient = openAiClient;
        this.modelResponseValidator = modelResponseValidator;
        this.retryPromptService = retryPromptService;
        this.telegramNotificationService = telegramNotificationService;
    }

    @Transactional
    public DialogueStepResponse processStep(DialogueStepRequest request) {
        log.info("Incoming dialogue step request: dialogId={}, stepId={}", request.dialogId(), request.stepId());
        telegramNotificationService.sendRequestNotification(request.dialogId(), request.stepId(), request.prompt());

        DialogueStepEntity previousStep = null;
        String initialPreviousResponseId = null;
        int previousDialogTotalTokens = 0;

        if (request.stepId() == 1) {
            log.info("Processing first step for dialogId={}", request.dialogId());
        } else {
            log.info("Processing continuation step for dialogId={}, stepId={}", request.dialogId(), request.stepId());
            log.info("Searching previous dialogue step for dialogId={}, stepId={}", request.dialogId(), request.stepId() - 1);
            previousStep = dialogueStepRepository.findByDialogIdAndStepId(request.dialogId(), request.stepId() - 1)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "Previous step not found for dialogId=%s and stepId=%d".formatted(request.dialogId(), request.stepId() - 1)
                    ));
            initialPreviousResponseId = previousStep.getOpenaiResponseId();
            previousDialogTotalTokens = previousStep.getDialogTotalTokens();
        }

        log.info("Sending first request to OpenAI for dialogId={}, stepId={}", request.dialogId(), request.stepId());
        OpenAiCallResult firstAttempt = openAiClient.createResponse(request.prompt(), initialPreviousResponseId, null);
        logUsage("first", firstAttempt);

        ObjectNode validatedResponse = tryValidate(firstAttempt.outputText(), "first");
        OpenAiCallResult finalAttempt = firstAttempt;
        String storedPreviousResponseId = initialPreviousResponseId;

        int totalRequestTokens = firstAttempt.tokenUsage().requestTokens();
        int totalResponseTokens = firstAttempt.tokenUsage().responseTokens();

        if (validatedResponse == null) {
            log.info("Retrying OpenAI request for dialogId={}, stepId={} due to invalid JSON", request.dialogId(), request.stepId());
            String retryPreviousResponseId = firstAttempt.responseId();
            OpenAiCallResult retryAttempt = openAiClient.createResponse(
                    request.prompt(),
                    retryPreviousResponseId,
                    retryPromptService.getRetryPrompt()
            );
            logUsage("retry", retryAttempt);

            totalRequestTokens += retryAttempt.tokenUsage().requestTokens();
            totalResponseTokens += retryAttempt.tokenUsage().responseTokens();
            finalAttempt = retryAttempt;
            storedPreviousResponseId = retryPreviousResponseId;
            validatedResponse = tryValidate(retryAttempt.outputText(), "retry");
            if (validatedResponse == null) {
                String message = "Model returned invalid JSON after retry for dialogId=%s, stepId=%d"
                        .formatted(request.dialogId(), request.stepId());
                log.error(message);
                telegramNotificationService.sendErrorNotification(request.dialogId(), request.stepId(), message);
                throw new InvalidModelResponseException("Model returned invalid JSON after retry");
            }
        }

        int currentStepTokens = totalRequestTokens + totalResponseTokens;
        int dialogTotalTokens = previousDialogTotalTokens + currentStepTokens;
        validatedResponse.put("dialogTotalTokens", dialogTotalTokens);

        String answerText = toJson(validatedResponse);
        DialogueStepEntity entity = buildEntity(
                request,
                answerText,
                finalAttempt.responseId(),
                storedPreviousResponseId,
                totalRequestTokens,
                totalResponseTokens,
                dialogTotalTokens
        );

        try {
            dialogueStepRepository.save(entity);
            log.info("Dialogue step saved successfully: dialogId={}, stepId={}", request.dialogId(), request.stepId());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Dialogue step already exists for dialogId=%s and stepId=%d".formatted(request.dialogId(), request.stepId()));
        }

        telegramNotificationService.sendAnswerNotification(request.dialogId(), request.stepId(), answerText);
        return new DialogueStepResponse(true, request.dialogId(), request.stepId(), validatedResponse);
    }

    private DialogueStepEntity buildEntity(
            DialogueStepRequest request,
            String answerText,
            String openAiResponseId,
            String previousResponseId,
            int requestTokens,
            int responseTokens,
            int dialogTotalTokens
    ) {
        if (!StringUtils.hasText(openAiResponseId)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI response id is missing");
        }

        DialogueStepEntity entity = new DialogueStepEntity();
        entity.setDialogId(request.dialogId());
        entity.setStepId(request.stepId());
        entity.setPromptText(request.prompt());
        entity.setAnswerText(answerText);
        entity.setOpenaiResponseId(openAiResponseId);
        entity.setPreviousResponseId(previousResponseId);
        entity.setRequestTokens(requestTokens);
        entity.setResponseTokens(responseTokens);
        entity.setDialogTotalTokens(dialogTotalTokens);
        entity.setCreatedAtMs(System.currentTimeMillis());
        return entity;
    }

    private void logUsage(String attemptName, OpenAiCallResult result) {
        log.info(
                "OpenAI {} request usage: requestTokens={}, responseTokens={}, totalTokens={}",
                attemptName,
                result.tokenUsage().requestTokens(),
                result.tokenUsage().responseTokens(),
                result.tokenUsage().totalTokens()
        );
    }

    private ObjectNode tryValidate(String rawResponse, String attemptName) {
        try {
            ObjectNode validated = modelResponseValidator.validateAndParse(rawResponse);
            log.info("JSON validation succeeded for {} attempt", attemptName);
            return validated;
        } catch (InvalidModelResponseException exception) {
            log.warn("JSON validation failed for {} attempt: {}", attemptName, exception.getMessage());
            return null;
        }
    }

    private String toJson(ObjectNode objectNode) {
        try {
            return objectNode.toPrettyString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize final response");
        }
    }
}
