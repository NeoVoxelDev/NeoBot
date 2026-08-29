package dev.neovoxel.neobot.update;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {
    @Test
    void isNewerComparesDottedNumericVersions() {
        assertTrue(UpdateService.isNewer("1.2.3", "1.2.4"));
        assertTrue(UpdateService.isNewer("1.2.3", "1.3.0"));
        assertTrue(UpdateService.isNewer("v1.2.3", "V1.2.4"));
        assertFalse(UpdateService.isNewer("1.2.4", "1.2.3"));
        assertFalse(UpdateService.isNewer("1.2.3", "1.2.3"));
    }

    @Test
    void isNewerTreatsShorterVersionAsPaddedWithZeros() {
        assertTrue(UpdateService.isNewer("1.2", "1.2.1"));
        assertFalse(UpdateService.isNewer("1.2.0", "1.2"));
    }

    @Test
    void isNewerFallsBackToInequalityForUnparseableTags() {
        assertTrue(UpdateService.isNewer("build-123", "build-124"));
        assertFalse(UpdateService.isNewer("build-123", "build-123"));
    }

    @Test
    void findJarAssetUrlReturnsFirstJarAsset() {
        JSONObject release = new JSONObject().put("assets", new JSONArray()
                .put(new JSONObject().put("name", "checksums.txt").put("browser_download_url", "https://example.com/checksums.txt"))
                .put(new JSONObject().put("name", "NeoBot-1.2.3.jar").put("browser_download_url", "https://example.com/NeoBot-1.2.3.jar")));
        assertEquals("https://example.com/NeoBot-1.2.3.jar", UpdateService.findJarAssetUrl(release));
    }

    @Test
    void findJarAssetUrlReturnsNullWhenNoJarAssetExists() {
        JSONObject release = new JSONObject().put("assets", new JSONArray()
                .put(new JSONObject().put("name", "notes.txt").put("browser_download_url", "https://example.com/notes.txt")));
        assertNull(UpdateService.findJarAssetUrl(release));
    }

    @Test
    void findJarAssetUrlReturnsNullWhenReleaseHasNoAssets() {
        assertNull(UpdateService.findJarAssetUrl(new JSONObject()));
    }
}
