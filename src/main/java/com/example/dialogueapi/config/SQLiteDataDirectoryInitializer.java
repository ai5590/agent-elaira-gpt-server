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
    private final String logFileName;

    public SQLiteDataDirectoryInitializer(
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${logging.file.name:}") String logFileName
    ) {
        this.datasourceUrl = datasourceUrl;
        this.logFileName = logFileName;
    }

    @PostConstruct
    public void ensureDirectoryExists() throws IOException {
        ensureParentDirectory(datasourceUrl, "jdbc:sqlite:", "SQLite data");
        ensureParentDirectory(logFileName, null, "log");
    }

    private void ensureParentDirectory(String value, String prefix, String label) throws IOException {
        if (value == null || value.isBlank()) {
            return;
        }

        String pathValue = value;
        if (prefix != null) {
            if (!value.startsWith(prefix)) {
                return;
            }
            pathValue = value.substring(prefix.length());
        }

        if (pathValue.isBlank() || ":memory:".equals(pathValue)) {
            return;
        }

        Path dbPath = Paths.get(pathValue).normalize();
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }

        Files.createDirectories(parent);
        log.info("Ensured {} directory exists: {}", label, parent);
    }
}
