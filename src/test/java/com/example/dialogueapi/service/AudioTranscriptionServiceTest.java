package com.example.dialogueapi.service;

import com.example.dialogueapi.client.OpenAiAudioClient;
import com.example.dialogueapi.client.OpenAiAudioClient.AudioTranscriptionResult;
import com.example.dialogueapi.config.AppProperties;
import com.example.dialogueapi.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioTranscriptionServiceTest {

    @Mock
    private OpenAiAudioClient openAiAudioClient;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @Test
    void shouldTranscribeAudio() {
        AppProperties properties = new AppProperties();
        AudioTranscriptionService service = new AudioTranscriptionService(properties, openAiAudioClient, telegramNotificationService);
        MockMultipartFile file = new MockMultipartFile("file", "voice.ogg", "audio/ogg", "abc".getBytes());

        when(openAiAudioClient.transcribe(file, "ru", "voice.ogg"))
                .thenReturn(new AudioTranscriptionResult("тестовый текст", "ru", new ObjectMapper().createObjectNode()));

        var response = service.transcribe(file, "ru", "telegram", "voice.ogg");

        assertEquals(true, response.success());
        assertEquals("тестовый текст", response.text());
        assertEquals("telegram", response.source());
        verify(telegramNotificationService).sendTranscriptionRequestNotification("telegram", "voice.ogg");
        verify(telegramNotificationService).sendTranscriptionResultNotification("тестовый текст");
    }

    @Test
    void shouldRejectEmptyFile() {
        AppProperties properties = new AppProperties();
        AudioTranscriptionService service = new AudioTranscriptionService(properties, openAiAudioClient, telegramNotificationService);
        MockMultipartFile file = new MockMultipartFile("file", "voice.ogg", "audio/ogg", new byte[0]);

        assertThrows(ApiException.class, () -> service.transcribe(file, "ru", "telegram", "voice.ogg"));
    }

    @Test
    void shouldRejectLargeFile() {
        AppProperties properties = new AppProperties();
        properties.getAudio().setMaxFileSizeMb(1);
        AudioTranscriptionService service = new AudioTranscriptionService(properties, openAiAudioClient, telegramNotificationService);
        byte[] payload = new byte[1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "voice.ogg", "audio/ogg", payload);

        assertThrows(ApiException.class, () -> service.transcribe(file, null, "telegram", null));
    }
}
