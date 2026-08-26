package dev.neovoxel.neobot.bot.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordEndpointResolverTest {
    @Test
    void selectsOfficialEndpointsWithoutProxyConfiguration() {
        DiscordEndpoints endpoints = DiscordEndpointResolver.resolve(DiscordProxyMode.OFFICIAL, null);
        assertEquals("https://discord.com/api/v10", endpoints.getApiBase().toString());
        assertEquals("wss://gateway.discord.gg/?v=10&encoding=json", endpoints.getGateway().toString());
    }

    @Test
    void normalizesLocalProxySchemesAndTrailingSlashes() {
        DiscordEndpoints endpoints = DiscordEndpointResolver.resolve(
                DiscordProxyMode.PROXY_HTTP, "127.0.0.1:8787/discord/api/v10///");
        assertEquals("http://127.0.0.1:8787/discord/api/v10", endpoints.getApiBase().toString());
        assertEquals("ws://127.0.0.1:8787/discord/gateway?v=10&encoding=json", endpoints.getGateway().toString());
    }

    @Test
    void enforcesSecureCloudflareWorkerEndpoints() {
        DiscordEndpoints endpoints = DiscordEndpointResolver.resolve(
                DiscordProxyMode.CF_WORKER, "worker.example.workers.dev/api/v10/");
        assertEquals("https://worker.example.workers.dev/api/v10", endpoints.getApiBase().toString());
        assertEquals("wss://worker.example.workers.dev/gateway?v=10&encoding=json", endpoints.getGateway().toString());
        assertThrows(IllegalArgumentException.class, () -> DiscordEndpointResolver.resolve(
                DiscordProxyMode.CF_WORKER, "http://worker.example/api"));
        assertThrows(IllegalArgumentException.class, () -> DiscordEndpointResolver.resolve(
                DiscordProxyMode.CF_WORKER, "https://worker.example/api?token=bad"));
    }

    @Test
    void derivesHttpsProxyWithoutDuplicatingKnownPaths() {
        DiscordEndpoints endpoints = DiscordEndpointResolver.resolve(
                DiscordProxyMode.PROXY_HTTPS, "https://proxy.example/bridge/gateway/");
        assertEquals("https://proxy.example/bridge/api/v10", endpoints.getApiBase().toString());
        assertEquals("wss://proxy.example/bridge/gateway?v=10&encoding=json", endpoints.getGateway().toString());
    }

    @Test
    void parsesSupportedModeNames() {
        assertEquals(DiscordProxyMode.PROXY_HTTP, DiscordProxyMode.fromConfig("proxy_http"));
        assertEquals(DiscordProxyMode.CF_WORKER, DiscordProxyMode.fromConfig("cf_worker"));
        assertEquals(DiscordProxyMode.CF_WORKER, DiscordProxyMode.fromConfig("cf-worker"));
        assertThrows(IllegalArgumentException.class, () -> DiscordProxyMode.fromConfig("unknown"));
    }
}
