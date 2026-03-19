package com.example.dialogueapi.service;

import com.example.dialogueapi.client.OpenAiAudioClient;
import com.example.dialogueapi.client.OpenAiAudioClient.AudioTranscriptionResult;
import com.example.dialogueapi.config.AppProperties;
import com.example.dialogueapi.dto.AudioTranscriptionResponse;
import com.example.dialogueapi.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);

    private final AppProperties properties;
    private final OpenAiAudioClient openAiAudioClient;
    private final TelegramNotificationService telegramNotificationService;

    public AudioTranscriptionService(
            AppProperties properties,
            OpenAiAudioClient openAiAudioClient,
            TelegramNotificationService telegramNotificationService
    ) {
        this.properties = properties;
        this.openAiAudioClient = openAiAudioClient;
        this.telegramNotificationService = telegramNotificationService;
    }

    public AudioTranscriptionResponse transcribe(
            MultipartFile file,
            String language,
            String source,
            String originalFileName
    ) {
        if (file == null || file.isEmpty()) {
            log.warn("Audio transcription request validation failed: file is missing or empty");
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is required and must not be empty");
        }

        long maxSizeBytes = properties.getAudio().getMaxFileSizeMb() * 1024L * 1024L;
        if (maxSizeBytes > 0 && file.getSize() > maxSizeBytes) {
            log.warn("Audio transcription request validation failed: file exceeds max size, fileSize={}, maxSize={}",
                    file.getSize(), maxSizeBytes);
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "file size exceeds limit of %d MB".formatted(properties.getAudio().getMaxFileSizeMb()));
        }

        String resolvedOriginalFileName = resolveOriginalFileName(file, originalFileName);
        log.info("Incoming transcription request: fileName={}, sizeBytes={}, source={}, language={}",
                resolvedOriginalFileName, file.getSize(), source, language);

        telegramNotificationService.sendTranscriptionRequestNotification(source, resolvedOriginalFileName);

        try {
            AudioTranscriptionResult result = openAiAudioClient.transcribe(file, language, resolvedOriginalFileName);
            String resolvedLanguage = StringUtils.hasText(language) ? language : result.language();
            log.info("Audio transcription succeeded: fileName={}, textLength={}", resolvedOriginalFileName, result.text().length());
            telegramNotificationService.sendTranscriptionResultNotification(result.text());
            return new AudioTranscriptionResponse(true, result.text(), source, resolvedLanguage, resolvedOriginalFileName);
        } catch (ApiException exception) {
            log.error("Audio transcription failed: fileName={}, source={}", resolvedOriginalFileName, source, exception);
            throw exception;
        }
    }

    private String resolveOriginalFileName(MultipartFile file, String originalFileName) {
        if (StringUtils.hasText(originalFileName)) {
            return originalFileName;
        }
        if (StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename();
        }
        return "audio.bin";
    }
}
