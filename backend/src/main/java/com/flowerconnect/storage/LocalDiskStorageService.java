package com.flowerconnect.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local-disk {@link StorageService}, active whenever the {@code s3} profile is
 * <em>not</em> enabled (plan task 3.8: "local disk (dev)"; plan section 9: "File
 * uploads — local disk (dev) / S3 (prod) behind {@code StorageService}").
 *
 * <p>Keys map to paths under a configured root. The key is server-generated
 * (see {@link StorageService}), so traversal is already structurally impossible
 * for the upload path — but {@link #resolve(String)} re-checks it anyway. The
 * interface is reachable from any future caller, and the check costs a string
 * compare while the failure mode (writing outside the root) does not.
 *
 * <p>Writes go to a temporary file in the same directory and are then moved into
 * place with {@link StandardCopyOption#ATOMIC_MOVE}. A reader can therefore never
 * observe a half-written object, which a plain {@code Files.write} allows, and a
 * crash mid-write leaves a {@code .part} file rather than a truncated image the
 * catalog believes is intact.
 */
@Slf4j
@Service
@Profile("!s3")
public class LocalDiskStorageService implements StorageService {

    private static final String TEMP_SUFFIX = ".part";

    private final StorageProperties properties;
    private final Path root;

    public LocalDiskStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.getLocalDirectory()).toAbsolutePath().normalize();
    }

    /**
     * Creates the root up front so a misconfigured or read-only path fails at
     * startup rather than on a vendor's first upload.
     */
    @PostConstruct
    void prepareRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create upload directory " + root, e);
        }
        log.info("Local disk storage writing under {}", root);
    }

    @Override
    public void store(String key, byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        Path target = resolve(key);
        Path temporary = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw new StorageException("Failed to store object at " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = resolve(key);
        deleteQuietly(target);
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public String describe() {
        return "local-disk:" + properties.getLocalDirectory();
    }

    /**
     * Resolves a key to a path inside the root, rejecting anything that could
     * escape it. Three separate checks, because they fail differently:
     * <ul>
     *   <li>a backslash — never produced by the server, and a Windows
     *       separator smuggled into a {@code /}-separated key;</li>
     *   <li>an absolute or Windows-drive path — a key is always relative;</li>
     *   <li>a resolved path outside the root — catches {@code ../} sequences
     *       after normalisation, which is the check that actually does the
     *       work.</li>
     * </ul>
     */
    Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        if (key.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Storage key must not contain a backslash: " + key);
        }
        if (key.startsWith("/") || key.startsWith("~")) {
            throw new IllegalArgumentException("Storage key must be relative: " + key);
        }
        Path resolved;
        try {
            resolved = root.resolve(Paths.get(key)).normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Storage key is not a valid path: " + key, e);
        }
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root: " + key);
        }
        return resolved;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }
}