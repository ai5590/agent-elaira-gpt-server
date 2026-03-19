package com.example.dialogueapi.controller;

import com.example.dialogueapi.dto.DialogueStepRequest;
import com.example.dialogueapi.dto.DialogueStepResponse;
import com.example.dialogueapi.service.DialogueOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dialogue")
public class DialogueController {

    private final DialogueOrchestrationService dialogueOrchestrationService;

    public DialogueController(DialogueOrchestrationService dialogueOrchestrationService) {
        this.dialogueOrchestrationService = dialogueOrchestrationService;
    }

    @PostMapping("/step")
    public ResponseEntity<DialogueStepResponse> createStep(@Valid @RequestBody DialogueStepRequest request) {
        return ResponseEntity.ok(dialogueOrchestrationService.processStep(request));
    }
}
