package com.example.dialogueapi.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SQLiteDataDirectoryInitializer {

    private static final Logger log = LoggerFactory.getLogger(SQLiteDataDirectoryInitializer.class);

    private final String datasourceUrl;

    public SQLiteDataDirectoryInitializer(@Value("${spring.datasource.url}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void ensureDirectoryExists() throws IOException {
        String prefix = "jdbc:sqlite:";
        if (!datasourceUrl.startsWith(prefix)) {
            return;
        }

        String sqlitePath = datasourceUrl.substring(prefix.length());
        if (sqlitePath.isBlank() || ":memory:".equals(sqlitePath)) {
            return;
        }

        Path dbPath = Paths.get(sqlitePath).normalize();
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }

        Files.createDirectories(parent);
        log.info("Ensured SQLite data directory exists: {}", parent);
    }
}
