package com.example.dialogueapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "dialogue_steps",
        uniqueConstraints = @UniqueConstraint(name = "uk_dialogue_steps_dialog_step", columnNames = {"dialog_id", "step_id"}),
        indexes = {
                @Index(name = "idx_dialogue_steps_dialog_id", columnList = "dialog_id")
        }
)
public class DialogueStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dialog_id", nullable = false)
    private String dialogId;

    @Column(name = "step_id", nullable = false)
    private int stepId;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "openai_response_id", nullable = false)
    private String openaiResponseId;

    @Column(name = "previous_response_id")
    private String previousResponseId;

    @Column(name = "request_tokens", nullable = false)
    private int requestTokens;

    @Column(name = "response_tokens", nullable = false)
    private int responseTokens;

    @Column(name = "dialog_total_tokens", nullable = false)
    private int dialogTotalTokens;

    @Column(name = "created_at_ms", nullable = false)
    private long createdAtMs;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDialogId() {
        return dialogId;
    }

    public void setDialogId(String dialogId) {
        this.dialogId = dialogId;
    }

    public int getStepId() {
        return stepId;
    }

    public void setStepId(int stepId) {
        this.stepId = stepId;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public String getOpenaiResponseId() {
        return openaiResponseId;
    }

    public void setOpenaiResponseId(String openaiResponseId) {
        this.openaiResponseId = openaiResponseId;
    }

    public String getPreviousResponseId() {
        return previousResponseId;
    }

    public void setPreviousResponseId(String previousResponseId) {
        this.previousResponseId = previousResponseId;
    }

    public int getRequestTokens() {
        return requestTokens;
    }

    public void setRequestTokens(int requestTokens) {
        this.requestTokens = requestTokens;
    }

    public int getResponseTokens() {
        return responseTokens;
    }

    public void setResponseTokens(int responseTokens) {
        this.responseTokens = responseTokens;
    }

    public int getDialogTotalTokens() {
        return dialogTotalTokens;
    }

    public void setDialogTotalTokens(int dialogTotalTokens) {
        this.dialogTotalTokens = dialogTotalTokens;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }
}
