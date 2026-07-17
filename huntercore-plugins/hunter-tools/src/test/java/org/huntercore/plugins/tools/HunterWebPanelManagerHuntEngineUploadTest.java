package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class HunterWebPanelManagerHuntEngineUploadTest {
    @Test
    void acceptsSafeNativeContentPackageNamesAndZipEntries() {
        assertTrue(HunterWebPanelManager.safeHuntEnginePackageFileName("huntercraft-ui.zip"));
        assertDoesNotThrow(() -> HunterWebPanelManager.preflightHuntEngineZip(zip("pack.yml", "id: huntercraft-ui")));
    }

    @Test
    void rejectsTraversalAndNonZipPackageNames() {
        assertFalse(HunterWebPanelManager.safeHuntEnginePackageFileName("../huntercraft.zip"));
        assertFalse(HunterWebPanelManager.safeHuntEnginePackageFileName("huntercraft.jar"));
        assertFalse(HunterWebPanelManager.safeHuntEnginePackageFileName("huntercraft.zip/"));
    }

    @Test
    void rejectsZipTraversalBeforeStaging() {
        final byte[] traversal = zip("../outside.yml", "never stage this");
        assertThrows(IOException.class, () -> HunterWebPanelManager.preflightHuntEngineZip(traversal));
    }

    @Test
    void createsAConstrainedNativeSimpleItemPackage() throws Exception {
        final HunterWebPanelManager.SimpleHuntEngineItemPackage draft = HunterWebPanelManager.createHuntEngineSimpleItemPackage(
            "starter_token", "PAPER", "Scout's Token", "A player's welcome item"
        );

        assertEquals("huntercraft:starter_token", draft.contentId());
        assertEquals("huntercraft-item-starter_token.zip", draft.fileName());
        assertDoesNotThrow(() -> HunterWebPanelManager.preflightHuntEngineZip(draft.contents()));
        assertTrue(zipEntry(draft.contents(), "pack.yml").contains("namespace: huntercraft"));
        final String configuration = zipEntry(draft.contents(), "configuration/huntercraft-simple-items.yml");
        assertTrue(configuration.contains("huntercraft:starter_token:"));
        assertTrue(configuration.contains("material: paper"));
        assertTrue(configuration.contains("item_name: 'Scout''s Token'"));
        assertTrue(configuration.contains("- 'A player''s welcome item'"));
    }

    @Test
    void rejectsUnsafeSimpleItemFields() {
        assertThrows(IllegalArgumentException.class, () -> HunterWebPanelManager.createHuntEngineSimpleItemPackage(
            "other:token", "PAPER", "Token", "Description"
        ));
        assertThrows(IllegalArgumentException.class, () -> HunterWebPanelManager.createHuntEngineSimpleItemPackage(
            "token", "COMMAND_BLOCK", "Token", "Description"
        ));
        assertThrows(IllegalArgumentException.class, () -> HunterWebPanelManager.createHuntEngineSimpleItemPackage(
            "token", "PAPER", "<click:run_command:'/op'>Token</click>", "Description"
        ));
    }

    private static byte[] zip(final String entryName, final String contents) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(contents.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String zipEntry(final byte[] contents, final String wantedName) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(contents), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().equals(wantedName)) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("missing test ZIP entry: " + wantedName);
    }
}
