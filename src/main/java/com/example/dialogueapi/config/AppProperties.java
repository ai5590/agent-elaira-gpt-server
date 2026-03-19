package com.example.dialogueapi.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    private final Security security = new Security();
    @Valid
    private final OpenAi openai = new OpenAi();
    @Valid
    private final Telegram telegram = new Telegram();
    @Valid
    private final Audio audio = new Audio();

    public Security getSecurity() {
        return security;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public Telegram getTelegram() {
        return telegram;
    }

    public Audio getAudio() {
        return audio;
    }

    public static class Security {
        private String token = "";

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class OpenAi {
        private String apiKey = "";
        private String model = "gpt-5.4";
        private String transcriptionModel = "whisper-1";
        private String baseUrl = "https://api.openai.com";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getTranscriptionModel() {
            return transcriptionModel;
        }

        public void setTranscriptionModel(String transcriptionModel) {
            this.transcriptionModel = transcriptionModel;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Telegram {
        private String botToken = "";
        private String chatId = "";

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getChatId() {
            return chatId;
        }

        public void setChatId(String chatId) {
            this.chatId = chatId;
        }
    }

    public static class Audio {
        private int maxFileSizeMb = 25;

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }
    }
}
