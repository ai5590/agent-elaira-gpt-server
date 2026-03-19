package com.example.dialogueapi.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record ActionDto(String command, String description, ObjectNode params) {
}
