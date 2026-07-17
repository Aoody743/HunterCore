package org.huntercore.huntengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HunterHuntEnginePublicationFilesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void immutableFileCollisionNeverReplacesTheOriginalCandidate() throws Exception {
        final Path firstSource = this.temporaryDirectory.resolve("first.zip");
        final Path secondSource = this.temporaryDirectory.resolve("second.zip");
        final Path publication = this.temporaryDirectory.resolve("publications/candidate.zip");
        Files.writeString(firstSource, "first immutable candidate", StandardCharsets.UTF_8);
        Files.writeString(secondSource, "different candidate", StandardCharsets.UTF_8);

        HunterHuntEngineServiceManager.copyImmutableFile(firstSource, publication);

        assertThrows(IOException.class, () -> HunterHuntEngineServiceManager.copyImmutableFile(secondSource, publication));
        assertEquals("first immutable candidate", Files.readString(publication, StandardCharsets.UTF_8));
    }

    @Test
    void immutableResourcesCollisionIsBoundToTheOriginalDirectoryDigest() throws Exception {
        final Path firstResources = this.temporaryDirectory.resolve("first-resources");
        final Path secondResources = this.temporaryDirectory.resolve("second-resources");
        final Path publication = this.temporaryDirectory.resolve("publications/candidate.resources");
        Files.createDirectories(firstResources.resolve("assets/huntercraft"));
        Files.createDirectories(secondResources.resolve("assets/huntercraft"));
        Files.writeString(firstResources.resolve("assets/huntercraft/model.json"), "{\"model\":\"first\"}", StandardCharsets.UTF_8);
        Files.writeString(secondResources.resolve("assets/huntercraft/model.json"), "{\"model\":\"second\"}", StandardCharsets.UTF_8);

        final String firstRevision = HunterHuntEngineServiceManager.copyImmutableDirectory(firstResources, publication);
        final String changedRevision = HunterHuntEngineServiceManager.digestDirectory(secondResources);

        assertNotEquals(firstRevision, changedRevision);
        assertThrows(IOException.class, () -> HunterHuntEngineServiceManager.copyImmutableDirectory(secondResources, publication));
        assertEquals(firstRevision, HunterHuntEngineServiceManager.digestDirectory(publication));
        assertEquals("{\"model\":\"first\"}", Files.readString(publication.resolve("assets/huntercraft/model.json"), StandardCharsets.UTF_8));
    }

    @Test
    void failedDirectoryReplacementLeavesTheLiveResourcesUntouched() throws Exception {
        final Path liveResources = this.temporaryDirectory.resolve("resources");
        final Path invalidReplacement = this.temporaryDirectory.resolve("not-a-directory");
        Files.createDirectories(liveResources.resolve("assets/huntercraft"));
        Files.writeString(liveResources.resolve("assets/huntercraft/live.json"), "old", StandardCharsets.UTF_8);
        Files.writeString(invalidReplacement, "not a resources directory", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> HunterHuntEngineServiceManager.replaceDirectory(invalidReplacement, liveResources));

        assertTrue(Files.isDirectory(liveResources));
        assertTrue(Files.exists(liveResources.resolve("assets/huntercraft/live.json")));
        assertEquals("old", Files.readString(liveResources.resolve("assets/huntercraft/live.json"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(liveResources.resolve("not-a-directory")));
    }
}
