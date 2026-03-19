package com.example.dialogueapi.controller;

import com.example.dialogueapi.config.ApiTokenInterceptor;
import com.example.dialogueapi.config.AppProperties;
import com.example.dialogueapi.config.WebConfig;
import com.example.dialogueapi.dto.AudioTranscriptionResponse;
import com.example.dialogueapi.exception.GlobalExceptionHandler;
import com.example.dialogueapi.service.AudioTranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AudioController.class)
@EnableConfigurationProperties(AppProperties.class)
@Import({GlobalExceptionHandler.class, WebConfig.class, ApiTokenInterceptor.class})
class AudioControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppProperties appProperties;

    @MockBean
    private AudioTranscriptionService audioTranscriptionService;

    @BeforeEach
    void resetToken() {
        appProperties.getSecurity().setToken("");
    }

    @Test
    void shouldReturnTranscriptionResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "voice.ogg", MediaType.APPLICATION_OCTET_STREAM_VALUE, "abc".getBytes());
        when(audioTranscriptionService.transcribe(any(), eq("ru"), eq("telegram"), eq("voice.ogg")))
                .thenReturn(new AudioTranscriptionResponse(true, "распознанный текст", "telegram", "ru", "voice.ogg"));

        mockMvc.perform(multipart("/api/audio/transcribe")
                        .file(file)
                        .param("language", "ru")
                        .param("source", "telegram")
                        .param("originalFileName", "voice.ogg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.text").value("распознанный текст"))
                .andExpect(jsonPath("$.source").value("telegram"))
                .andExpect(jsonPath("$.language").value("ru"))
                .andExpect(jsonPath("$.originalFileName").value("voice.ogg"));
    }

    @Test
    void shouldRejectMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/audio/transcribe")
                        .param("source", "telegram"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRequireApiTokenForTranscriptionWhenConfigured() throws Exception {
        appProperties.getSecurity().setToken("secret");
        MockMultipartFile file = new MockMultipartFile("file", "voice.ogg", MediaType.APPLICATION_OCTET_STREAM_VALUE, "abc".getBytes());

        mockMvc.perform(multipart("/api/audio/transcribe").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
