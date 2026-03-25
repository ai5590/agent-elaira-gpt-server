package com.example.dialogueapi.service;

import com.example.dialogueapi.client.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final TelegramClient telegramClient;

    public TelegramNotificationService(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void sendRequestNotification(
            String dialogId,
            int stepId,
            String model,
            String previousResponseId,
            String prompt
    ) {
        String previousResponseIdValue = previousResponseId == null ? "null" : previousResponseId;
        send("""
                dialogId: %s
                stepId: %d
                type: request
                model: %s
                previous_response_id: %s
                prompt:
                %s
                """.formatted(dialogId, stepId, model, previousResponseIdValue, prompt));
    }

    public void sendAnswerNotification(String dialogId, int stepId, String answer) {
        send("""
                dialogId: %s
                stepId: %d
                type: answer
                answer:
                %s
                """.formatted(dialogId, stepId, answer));
    }

    public void sendErrorNotification(String dialogId, int stepId, String message) {
        send("""
                dialogId: %s
                stepId: %d
                type: error
                message:
                %s
                """.formatted(dialogId, stepId, message));
    }

    public void sendTranscriptionRequestNotification(String source, String fileName) {
        send("""
                type: transcription_request
                source: %s
                fileName: %s
                """.formatted(source, fileName));
    }

    public void sendTranscriptionResultNotification(String text) {
        send("""
                type: transcription_result
                text:
                %s
                """.formatted(text));
    }

    private void send(String message) {
        if (!telegramClient.isEnabled()) {
            return;
        }

        try {
            telegramClient.sendMessage(message);
            log.info("Telegram notification sent successfully");
        } catch (Exception exception) {
            log.error("Failed to send Telegram notification", exception);
        }
    }
}
