package com.example.dialogueapi.controller;

import com.example.dialogueapi.config.ApiTokenInterceptor;
import com.example.dialogueapi.config.AppProperties;
import com.example.dialogueapi.config.WebConfig;
import com.example.dialogueapi.dto.DialogueStepResponse;
import com.example.dialogueapi.exception.GlobalExceptionHandler;
import com.example.dialogueapi.service.DialogueOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DialogueController.class, HealthController.class})
@EnableConfigurationProperties(AppProperties.class)
@Import({GlobalExceptionHandler.class, WebConfig.class, ApiTokenInterceptor.class})
class DialogueControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DialogueOrchestrationService dialogueOrchestrationService;

    @Autowired
    private AppProperties appProperties;

    @BeforeEach
    void resetToken() {
        appProperties.getSecurity().setToken("");
    }

    @Test
    void shouldReturnHealth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldEnforceApiTokenWhenConfigured() throws Exception {
        appProperties.getSecurity().setToken("secret");

        mockMvc.perform(get("/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnDialogueResponse() throws Exception {
        appProperties.getSecurity().setToken("");
        ObjectNode data = objectMapper.createObjectNode();
        data.put("stepDebug", "debug");
        data.put("dialogGoal", "goal");
        data.put("dialogTotalTokens", 42);
        data.putArray("actions");

        when(dialogueOrchestrationService.processStep(any())).thenReturn(
                new DialogueStepResponse(true, "dialog-1", 1, data)
        );

        mockMvc.perform(post("/api/dialogue/step")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dialogId": "dialog-1",
                                  "stepId": 1,
                                  "prompt": "Make a plan"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogId").value("dialog-1"))
                .andExpect(jsonPath("$.data.dialogTotalTokens").value(42));
    }
}
