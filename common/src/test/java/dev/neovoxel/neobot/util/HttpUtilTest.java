package dev.neovoxel.neobot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpUtilTest {
    @Test
    void onlyPrefixesSupportedGithubUrls() {
        assertEquals("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases",
                HttpUtil.proxyUrlForTest("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases"));
        assertEquals("http://127.0.0.1:7897/https://api.github.com/repos/NeoVoxelDev/NeoBot/releases",
                HttpUtil.proxyUrlForTest("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases", "http://127.0.0.1:7897/"));
        assertEquals("https://example.com/data", HttpUtil.proxyUrlForTest("https://example.com/data"));
    }
}
