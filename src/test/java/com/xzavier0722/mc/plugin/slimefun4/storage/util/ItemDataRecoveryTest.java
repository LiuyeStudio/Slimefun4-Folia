package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemDataRecoveryTest {

    @Test
    void writesBackupFileWithRawDataAndRealCause(@TempDir File folder) throws Exception {
        var data = new byte[] {1, 2, 3, 4, 5};
        var cause = new IllegalStateException(
                "Failed to deserialize object", new IllegalArgumentException("invalid material: LEGACY_X"));

        var file = ItemDataRecovery.writeFile(folder, data, "0123456789abcdef", cause);

        assertTrue(file.isFile());
        assertTrue(file.getName().startsWith("item-"));
        assertTrue(file.getName().endsWith(".txt"));

        var content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("0123456789abcdef"));
        assertTrue(content.contains("invalid material: LEGACY_X"));
        assertTrue(content.endsWith(Base64.getEncoder().encodeToString(data) + "\n"));
    }
}
