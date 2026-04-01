package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final AppProperties appProperties;

    private Path basePath() {
        String configured = appProperties.getStorage().getFilePath();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        return Paths.get(System.getProperty("user.dir"), "uploads");
    }

    public void save(String relativePath, byte[] content) {
        Path target = basePath().resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + relativePath, e);
        }
    }

    public byte[] read(String relativePath) {
        Path target = basePath().resolve(relativePath);
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + relativePath, e);
        }
    }

    public Path resolve(String relativePath) {
        return basePath().resolve(relativePath);
    }

    public void delete(String relativePath) {
        Path target = basePath().resolve(relativePath);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + relativePath, e);
        }
    }
}
