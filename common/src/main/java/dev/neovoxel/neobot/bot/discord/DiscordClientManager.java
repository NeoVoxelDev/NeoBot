package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.nbapi.discord.client.DiscordClient;
import dev.neovoxel.nbapi.listener.NBotListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscordClientManager {
    private final Map<String, DiscordClient> clients = new ConcurrentHashMap<>();

    public void addAndConnect(String type, DiscordClient client, NBotListener listener) {
        client.addListener(listener);
        clients.put(type, client);
        try {
            client.connect();
        } catch (RuntimeException error) {
            clients.remove(type, client);
            client.shutdown();
            throw error;
        }
    }

    public Collection<DiscordClient> getClients() {
        return new ArrayList<>(clients.values());
    }

    public void reconnectDisconnected() {
        for (DiscordClient client : getClients()) {
            if (!client.isConnected()) client.reconnect();
        }
    }

    public void shutdownAll() {
        for (DiscordClient client : getClients()) client.shutdown();
        clients.clear();
    }
}
