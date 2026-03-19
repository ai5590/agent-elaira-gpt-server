package com.example.dialogueapi;

import com.example.dialogueapi.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DialogueApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DialogueApiApplication.class, args);
    }
}
