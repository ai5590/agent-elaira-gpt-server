package com.example.dialogueapi.controller;

import com.example.dialogueapi.dto.AudioTranscriptionResponse;
import com.example.dialogueapi.service.AudioTranscriptionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioTranscriptionService audioTranscriptionService;

    public AudioController(AudioTranscriptionService audioTranscriptionService) {
        this.audioTranscriptionService = audioTranscriptionService;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioTranscriptionResponse> transcribe(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "originalFileName", required = false) String originalFileName
    ) {
        return ResponseEntity.ok(audioTranscriptionService.transcribe(file, language, source, originalFileName));
    }
}
