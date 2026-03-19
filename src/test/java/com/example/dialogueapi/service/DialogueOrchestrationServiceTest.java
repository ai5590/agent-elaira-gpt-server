package com.example.dialogueapi.service;

import com.example.dialogueapi.client.OpenAiClient;
import com.example.dialogueapi.client.OpenAiClient.OpenAiCallResult;
import com.example.dialogueapi.client.OpenAiClient.TokenUsage;
import com.example.dialogueapi.dto.DialogueStepRequest;
import com.example.dialogueapi.entity.DialogueStepEntity;
import com.example.dialogueapi.exception.ApiException;
import com.example.dialogueapi.repository.DialogueStepRepository;
import com.example.dialogueapi.validation.ModelResponseValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialogueOrchestrationServiceTest {

    @Mock
    private DialogueStepRepository repository;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private RetryPromptService retryPromptService;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    private final ModelResponseValidator validator = new ModelResponseValidator(new ObjectMapper());

    @Test
    void shouldRetryAndStoreCombinedTokenUsage() {
        DialogueOrchestrationService service = new DialogueOrchestrationService(
                repository,
                openAiClient,
                validator,
                retryPromptService,
                telegramNotificationService
        );

        DialogueStepRequest request = new DialogueStepRequest("dialog-1", 2, "Continue");
        DialogueStepEntity previous = new DialogueStepEntity();
        previous.setOpenaiResponseId("resp-prev");
        previous.setDialogTotalTokens(100);

        when(repository.findByDialogIdAndStepId("dialog-1", 1)).thenReturn(Optional.of(previous));
        when(retryPromptService.getRetryPrompt()).thenReturn("fix json");
        when(openAiClient.createResponse("Continue", "resp-prev", null)).thenReturn(
                new OpenAiCallResult("resp-bad", "not-json", new TokenUsage(11, 7, 18), null)
        );
        when(openAiClient.createResponse("Continue", "resp-bad", "fix json")).thenReturn(
                new OpenAiCallResult(
                        "resp-good",
                        """
                        {
                          "stepDebug": "debug",
                          "dialogGoal": "goal",
                          "actions": [
                            {
                              "command": "run_command",
                              "description": "Run build",
                              "params": {}
                            }
                          ]
                        }
                        """,
                        new TokenUsage(13, 5, 18),
                        null
                )
        );
        when(repository.save(any(DialogueStepEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.processStep(request);

        assertEquals(true, response.success());
        assertEquals(136, response.data().get("dialogTotalTokens").asInt());

        ArgumentCaptor<DialogueStepEntity> captor = ArgumentCaptor.forClass(DialogueStepEntity.class);
        verify(repository).save(captor.capture());
        DialogueStepEntity saved = captor.getValue();
        assertEquals("resp-good", saved.getOpenaiResponseId());
        assertEquals("resp-bad", saved.getPreviousResponseId());
        assertEquals(24, saved.getRequestTokens());
        assertEquals(12, saved.getResponseTokens());
        assertEquals(136, saved.getDialogTotalTokens());
        verify(telegramNotificationService).sendAnswerNotification(eq("dialog-1"), eq(2), any(String.class));
    }

    @Test
    void shouldFailWhenPreviousStepMissing() {
        DialogueOrchestrationService service = new DialogueOrchestrationService(
                repository,
                openAiClient,
                validator,
                retryPromptService,
                telegramNotificationService
        );

        when(repository.findByDialogIdAndStepId("dialog-404", 1)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.processStep(new DialogueStepRequest("dialog-404", 2, "Continue")));
        verify(openAiClient, never()).createResponse(any(), any(), any());
    }
}
