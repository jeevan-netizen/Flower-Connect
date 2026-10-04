package com.flowerconnect.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Local-disk backend behaviour, above all the key rules that make path traversal
 * impossible (plan task 3.8: "random filenames, no path traversal").
 *
 * <p>The upload path never sends a client-controlled key, so traversal is already
 * structurally impossible there. These tests exercise the boundary itself,
 * because the interface is public: a future caller that builds a key from
 * anything the client influenced must still be refused, not merely unlikely to
 * escape.
 */
class LocalDiskStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalDiskStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalDirectory(tempDir.toString());
        storage = new LocalDiskStorageService(properties);
        storage.prepareRoot();
    }

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    void aStoredObjectIsWrittenExactlyAndCanBeReadBack() throws IOException {
        byte[] content = "image bytes".getBytes(StandardCharsets.UTF_8);

        storage.store("product-images/42/abc.jpg", content);

        assertTrue(storage.exists("product-images/42/abc.jpg"));
        assertArrayEquals(content, Files.readAllBytes(tempDir.resolve("product-images/42/abc.jpg")));
    }

    @Test
    void storingTheSameKeyTwiceReplacesTheObject() throws IOException {
        storage.store("a/b.jpg", "first".getBytes(StandardCharsets.UTF_8));
        storage.store("a/b.jpg", "second".getBytes(StandardCharsets.UTF_8));

        assertEquals("second", Files.readString(tempDir.resolve("a/b.jpg")));
        assertEquals(1, Files.list(tempDir.resolve("a")).count(), "no leftover temp files");
    }

    @Test
    void missingDirectoriesAreCreatedOnDemand() {
        storage.store("product-images/7/deep/nested/name.png", new byte[]{1, 2, 3});

        assertTrue(storage.exists("product-images/7/deep/nested/name.png"));
    }

    @Test
    void deleteRemovesTheObjectAndIsIdempotent() {
        storage.store("a/b.jpg", new byte[]{1});

        storage.delete("a/b.jpg");
        assertFalse(storage.exists("a/b.jpg"));

        // A compensating delete after a rolled-back transaction calls this
        // unconditionally, so a second call must not throw.
        storage.delete("a/b.jpg");
    }

    @Test
    void existsIsFalseForAnUnknownKey() {
        assertFalse(storage.exists("product-images/1/never-written.jpg"));
    }

    @Test
    void noTemporaryPartFileSurvivesASuccessfulWrite() throws IOException {
        storage.store("a/b.jpg", new byte[]{1, 2, 3});

        try (var files = Files.walk(tempDir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")),
                    "the atomic-write temp file must be gone after the move");
        }
    }

    // ------------------------------------------------------------------
    // Key rules: the traversal boundary
    // ------------------------------------------------------------------

    @Test
    void aKeyThatClimbsOutOfTheRootIsRefused() {
        for (String key : new String[]{
                "../escaped.jpg",
                "product-images/../../escaped.jpg",
                "a/b/../../../escaped.jpg",
                "..",
                "product-images/../../../etc/passwd"}) {
            assertThrows(IllegalArgumentException.class, () -> storage.store(key, new byte[]{1}),
                    "key must be refused: " + key);
        }
        assertNothingWasWrittenOutsideRoot();
    }

    @Test
    void anAbsoluteKeyIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("/etc/passwd", new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("~/secret.jpg", new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("C:\\Windows\\win.ini", new byte[]{1}));
    }

    @Test
    void aBackslashInAKeyIsRefused() {
        // The server only ever emits '/'-separated keys; a backslash is either a
        // Windows separator smuggled into one or an attempt to exploit a
        // platform difference.
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("..\\..\\escaped.jpg", new byte[]{1}));
        assertNothingWasWrittenOutsideRoot();
    }

    @Test
    void blankAndNullKeysAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> storage.store(null, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> storage.store("  ", new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> storage.exists(""));
    }

    @Test
    void aTraversalKeyIsRefusedOnEveryEntryPointNotJustStore() {
        String key = "../../escaped.jpg";

        assertThrows(IllegalArgumentException.class, () -> storage.store(key, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> storage.exists(key));
        assertThrows(IllegalArgumentException.class, () -> storage.delete(key));
        assertNothingWasWrittenOutsideRoot();
    }

    @Test
    void storingNullContentIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> storage.store("a/b.jpg", null));
    }

    @Test
    void describeNamesTheConfiguredDirectory() {
        assertTrue(storage.describe().startsWith("local-disk:"));
        assertTrue(storage.describe().contains(tempDir.toString()));
    }

    // ------------------------------------------------------------------

    /**
     * Nothing may exist above the temp root. {@code @TempDir} is itself inside the
     * build output directory, so the assertion walks up two levels and looks for
     * the specific names the refused keys named.
     */
    private void assertNothingWasWrittenOutsideRoot() {
        for (String name : new String[]{"escaped.jpg", "x.jpg", "win.ini", "passwd"}) {
            assertFalse(Files.exists(tempDir.getParent().resolve(name)),
                    "a refused key wrote outside the storage root: " + name);
            assertFalse(Files.exists(tempDir.getParent().getParent().resolve(name)),
                    "a refused key wrote outside the storage root: " + name);
        }
    }
}