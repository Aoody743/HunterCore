package org.huntercore.api.huntengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class HuntEngineApiTest {

    @Test
    void contentPackageUploadDefensivelyCopiesThePayload() {
        final byte[] bytes = {1, 2, 3};
        final HuntEngineContentPackageUpload upload = new HuntEngineContentPackageUpload("pack.zip", bytes);

        bytes[0] = 9;
        final byte[] returned = upload.contents();
        returned[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, upload.contents());
    }

    @Test
    void unavailableMigrationReportAndServiceAreSafeFallbacks() {
        final HuntEngineMigrationReport report = HuntEngineMigrationReport.unavailable();

        assertFalse(HuntEngineServices.unavailable().available());
        assertNotNull(report.journalLocation());
        assertFalse(report.journalLocation().isBlank());
    }
}
