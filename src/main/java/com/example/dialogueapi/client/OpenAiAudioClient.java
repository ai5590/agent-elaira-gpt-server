package com.example.dialogueapi.client;

import com.example.dialogueapi.config.AppProperties;
import com.example.dialogueapi.exception.ApiException;
import com.example.dialogueapi.service.OpenAiApiKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class OpenAiAudioClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAudioClient.class);

    private final RestClient restClient;
    private final AppProperties properties;

    public OpenAiAudioClient(
            RestTemplateBuilder restTemplateBuilder,
            AppProperties properties,
            OpenAiApiKeyProvider apiKeyProvider
    ) {
        this.properties = properties;
        this.restClient = RestClient.builder(restTemplateBuilder.build())
                .baseUrl(stripTrailingSlash(properties.getOpenai().getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyProvider.getRequiredApiKey())
                .build();
    }

    public AudioTranscriptionResult transcribe(MultipartFile file, String language, String originalFileName) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", properties.getOpenai().getTranscriptionModel());
            body.add("response_format", "verbose_json");
            if (StringUtils.hasText(language)) {
                body.add("language", language);
            }

            String resolvedFileName = StringUtils.hasText(originalFileName) ? originalFileName : file.getOriginalFilename();
            body.add("file", new HttpEntity<>(new NamedByteArrayResource(file.getBytes(), resolvedFileName), buildFileHeaders(file)));

            JsonNode response = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI transcription response is empty");
            }

            String text = readText(response, "text");
            String detectedLanguage = readText(response, "language");
            if (!StringUtils.hasText(text)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI transcription response does not contain text");
            }

            return new AudioTranscriptionResult(text, detectedLanguage, response);
        } catch (IOException exception) {
            log.error("Failed to read uploaded audio file", exception);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file");
        } catch (RestClientException exception) {
            log.error("OpenAI transcription request failed", exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI transcription request failed");
        }
    }

    private HttpHeaders buildFileHeaders(MultipartFile file) {
        HttpHeaders headers = new HttpHeaders();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(file.getContentType())) {
            mediaType = MediaType.parseMediaType(file.getContentType());
        }
        headers.setContentType(mediaType);
        return headers;
    }

    private String readText(JsonNode response, String fieldName) {
        JsonNode value = response.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text : null;
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record AudioTranscriptionResult(
            String text,
            String language,
            JsonNode rawResponse
    ) {
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] byteArray, String fileName) {
            super(byteArray);
            this.fileName = StringUtils.hasText(fileName) ? fileName : "audio.bin";
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
