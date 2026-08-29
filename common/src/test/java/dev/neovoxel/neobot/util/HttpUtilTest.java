package dev.neovoxel.neobot.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpUtilTest {
    @Test
    void onlyPrefixesSupportedGithubUrls() {
        assertEquals("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases",
                HttpUtil.proxyUrlForTest("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases"));
        assertEquals("http://127.0.0.1:7897/https://api.github.com/repos/NeoVoxelDev/NeoBot/releases",
                HttpUtil.proxyUrlForTest("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases", "http://127.0.0.1:7897/"));
        assertEquals("https://example.com/data", HttpUtil.proxyUrlForTest("https://example.com/data"));
    }

    @Test
    void selectFastestPicksLowestNonNegativeLatency() {
        Map<String, Long> latencies = new HashMap<>();
        latencies.put("https://slow.example/", 500L);
        latencies.put("https://fast.example/", 50L);
        latencies.put("https://dead.example/", -1L);
        String best = HttpUtil.selectFastest(Arrays.asList("https://slow.example/", "https://fast.example/", "https://dead.example/"),
                latencies::get);
        assertEquals("https://fast.example/", best);
    }

    @Test
    void selectFastestReturnsNullWhenAllMirrorsAreInvalid() {
        String best = HttpUtil.selectFastest(Arrays.asList("https://a.example/", "https://b.example/"), mirror -> -1L);
        assertNull(best);
    }

    @Test
    void selectFastestIgnoresBlankEntries() {
        String best = HttpUtil.selectFastest(Arrays.asList("", null, "https://only.example/"), mirror -> 10L);
        assertEquals("https://only.example/", best);
    }
}
