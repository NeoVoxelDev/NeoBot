package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.nbapi.discord.client.DiscordClient;
import dev.neovoxel.nbapi.discord.data.DiscordMessage;
import dev.neovoxel.nbapi.listener.NBotListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DiscordClientManagerTest {
    @Test
    void ownsConnectReconnectAndShutdownLifecycle() {
        DiscordClientManager manager = new DiscordClientManager();
        FakeClient client = new FakeClient();
        NBotListener listener = new NBotListener() {};

        manager.addAndConnect("discord", client, listener);
        assertEquals(1, client.connectCalls);
        assertSame(listener, client.listener);
        assertEquals(1, manager.getClients().size());

        client.connected = false;
        manager.reconnectDisconnected();
        assertEquals(1, client.reconnectCalls);

        manager.shutdownAll();
        assertEquals(1, client.shutdownCalls);
        assertTrue(manager.getClients().isEmpty());
    }

    private static class FakeClient implements DiscordClient {
        private int connectCalls;
        private int reconnectCalls;
        private int shutdownCalls;
        private boolean connected;
        private NBotListener listener;

        @Override public void connect() { connectCalls++; connected = true; }
        @Override public void disconnect() { connected = false; }
        @Override public void shutdown() { shutdownCalls++; connected = false; }
        @Override public void reconnect() { reconnectCalls++; connected = true; }
        @Override public boolean isConnected() { return connected; }
        @Override public void addListener(NBotListener listener) { this.listener = listener; }
        @Override public void removeListener(NBotListener listener) { if (this.listener == listener) this.listener = null; }
        @Override public boolean hasListener(NBotListener listener) { return this.listener == listener; }
        @Override public CompletableFuture<DiscordMessage> sendMessage(long channelId, String content) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<DiscordMessage> editMessage(long channelId, long messageId, String content) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<Void> deleteMessage(long channelId, long messageId) { return new CompletableFuture<>(); }
    }
}
