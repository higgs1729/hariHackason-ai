package com.hanamizuki.backend.integration.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Files live on disk and the database stores their paths. Nothing here is
 * clever; it exists so no other class has to know where the root is or think
 * about path traversal.
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.storage.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /**
     * @param relative where to put it, e.g. {@code overlays/901.png}
     * @return the same relative path, which is what gets stored in the row
     */
    public String write(String relative, byte[] content) {
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "upload", ".part");
            Files.write(tmp, content);
            // Move into place last, so a reader never sees a half-written file.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return relative;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + relative, e);
        }
    }

    public byte[] read(String relative) {
        try {
            return Files.readAllBytes(resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + relative, e);
        }
    }

    public boolean exists(String relative) {
        return relative != null && Files.isRegularFile(resolve(relative));
    }

    /**
     * Rejects anything that would land outside the storage root. The inputs are
     * built server-side today, but a single future endpoint that takes a path
     * from a request would otherwise be a directory traversal.
     */
    private Path resolve(String relative) {
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the storage root: " + relative);
        }
        return candidate;
    }
}
