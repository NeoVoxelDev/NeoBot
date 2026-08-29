package dev.neovoxel.neobot.bot;

import dev.neovoxel.nbapi.client.NBotClient;
import dev.neovoxel.nbapi.client.OBWSClient;
import dev.neovoxel.nbapi.client.OBWSServer;
import dev.neovoxel.nbapi.discord.DiscordClients;
import dev.neovoxel.nbapi.discord.client.DiscordClient;
import dev.neovoxel.nbapi.discord.client.DiscordGatewayClient;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.bot.discord.DiscordBotListener;
import dev.neovoxel.neobot.bot.discord.DiscordClientManager;
import dev.neovoxel.neobot.bot.discord.DiscordEndpointResolver;
import dev.neovoxel.neobot.bot.discord.DiscordEndpoints;
import dev.neovoxel.neobot.bot.discord.DiscordProxyMode;
import dev.neovoxel.neobot.discord.DiscordService;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BotProvider {
    private static final long RECONNECT_BACKOFF_BASE_MILLIS = 5_000L;
    private static final long RECONNECT_BACKOFF_MAX_MILLIS = 300_000L;

    private Map<String, NBotClient> clients = new HashMap<>();
    private final Map<String, ReconnectBackoff> reconnectBackoff = new HashMap<>();
    private BotListener listener;
    private final DiscordBotListener discordListener;
    private final DiscordClientManager discordClientManager = new DiscordClientManager();
    private final NeoBot plugin;
    private final DiscordService discordService;
    private String discordApiBase;
    private String discordToken;

    public BotProvider(NeoBot plugin) {
        this.plugin = plugin;
        this.listener = new BotListener(plugin);
        this.discordListener = new DiscordBotListener(plugin);
        this.discordService = new DiscordService(plugin);
        this.discordService.initialize();
    }

    private void addBot(String type, NBotClient client) {
        clients.put(type, client);
    }

    private void setBotListener(BotListener listener) {
        this.listener = listener;
    }

    public Collection<NBotClient> getBot() {
        return clients.values();
    }

    public BotListener getBotListener() {
        return listener;
    }

    public DiscordBotListener getDiscordBotListener() {
        return discordListener;
    }

    public Collection<DiscordClient> getDiscordClients() {
        return discordClientManager.getClients();
    }
    public DiscordService getDiscordService() { return discordService; }

    /** REST API base for the currently configured Discord connection (official or proxied), or null
     *  when Discord has never successfully loaded. Used for ad-hoc REST calls (e.g. channel-info lookups)
     *  that the underlying gateway client doesn't expose an API for. */
    public String getDiscordApiBase() { return discordApiBase; }

    public String getDiscordToken() { return discordToken; }

    public void loadBot(NeoBot plugin) throws URISyntaxException {
        for (String type : plugin.getGeneralConfig().getStringArray("bot.type")) {
            if (type.equalsIgnoreCase("onebot11-ws")) {
                loadOnebot11Ws(plugin);
            } else if (type.equalsIgnoreCase("onebot11-ws-reverse")) {
                loadOnebot11WsReverse(plugin);
            } else if (type.equalsIgnoreCase("discord")) {
                try {
                    loadDiscord(plugin);
                } catch (RuntimeException error) {
                    plugin.getNeoLogger().error("Failed to configure Discord", error);
                }
            }
        }
        plugin.submitAsync(() -> {
            for (Map.Entry<String, NBotClient> entry : clients.entrySet()) {
                NBotClient client = entry.getValue();
                String type = entry.getKey();
                if (client.isConnected()) {
                    reconnectBackoff.remove(type);
                    continue;
                }
                ReconnectBackoff backoff = reconnectBackoff.computeIfAbsent(type, k -> new ReconnectBackoff());
                long now = System.currentTimeMillis();
                if (now < backoff.nextAttemptAtMillis) continue;
                plugin.getNeoLogger().info("Attempting to reconnect bot client: " + type);
                try {
                    client.reconnect();
                    plugin.getNeoLogger().info("Reconnect requested for bot client: " + type);
                } catch (RuntimeException error) {
                    plugin.getNeoLogger().error("Failed to reconnect bot client: " + type, error);
                }
                backoff.nextAttemptAtMillis = now + nextBackoffMillis(backoff.attemptStreak);
                backoff.attemptStreak++;
            }
            try {
                discordClientManager.reconnectDisconnected();
            } catch (RuntimeException error) {
                plugin.getNeoLogger().error("Failed to reconnect Discord", error);
            }
        }, 5, plugin.getGeneralConfig().getInt("bot.options.check-interval"));
    }

    public void loadOnebot11Ws(NeoBot plugin) throws URISyntaxException {
        String url = plugin.getGeneralConfig().getString("bot.onebot11-ws.url");
        URI uri = new URI(url);
        String token = plugin.getGeneralConfig().getString("bot.onebot11-ws.access-token");
        OBWSClient client;
        if (!token.isEmpty()) {
            client = new OBWSClient(uri, token);
        } else client = new OBWSClient(uri);
        addBot("onebot11-ws", client);
        client.addListener(getBotListener());
        client.connect();
    }

    public void loadOnebot11WsReverse(NeoBot plugin) {
        String address = plugin.getGeneralConfig().getString("bot.onebot11-ws-reverse.address");
        int port = plugin.getGeneralConfig().getInt("bot.onebot11-ws-reverse.port");
        String token = plugin.getGeneralConfig().getString("bot.onebot11-ws-reverse.access-token");
        OBWSServer client;
        if (!token.isEmpty()) {
            client = new OBWSServer(address, port, token);
        } else client = new OBWSServer(address, port);
        addBot("onebot11-ws-reverse", client);
        client.addListener(getBotListener());
        client.connect();
    }

    public void loadDiscord(NeoBot plugin) {
        if (!plugin.getGeneralConfig().has("bot.discord.enabled")
                || !plugin.getGeneralConfig().getBoolean("bot.discord.enabled")) {
            plugin.getNeoLogger().info("Discord is disabled");
            return;
        }
        String token = plugin.getGeneralConfig().getString("bot.discord.token").trim();
        if (token.isEmpty()) {
            plugin.getNeoLogger().warn("Discord is enabled but no bot token is configured");
            return;
        }
        int intents = plugin.getGeneralConfig().has("bot.discord.intents")
                ? plugin.getGeneralConfig().getInt("bot.discord.intents")
                : DiscordGatewayClient.DEFAULT_INTENTS;
        DiscordProxyMode mode = DiscordProxyMode.fromConfig(
                plugin.getGeneralConfig().has("bot.discord.proxy.mode")
                        ? plugin.getGeneralConfig().getString("bot.discord.proxy.mode") : "official");
        String proxyUrl = plugin.getGeneralConfig().has("bot.discord.proxy.url")
                ? plugin.getGeneralConfig().getString("bot.discord.proxy.url").trim() : "";
        DiscordEndpoints endpoints;
        if (mode == DiscordProxyMode.OFFICIAL) {
            endpoints = DiscordEndpointResolver.resolve(mode, null);
        } else if (!proxyUrl.isEmpty()) {
            endpoints = DiscordEndpointResolver.resolve(mode, proxyUrl);
        } else if (mode == DiscordProxyMode.LOCAL || mode == DiscordProxyMode.CF_WORKER) {
            String prefix = mode == DiscordProxyMode.LOCAL
                    ? "bot.discord.proxy.local" : "bot.discord.proxy.cf_worker";
            endpoints = DiscordEndpointResolver.resolveLegacy(
                    mode,
                    plugin.getGeneralConfig().getString(prefix + ".api-base"),
                    plugin.getGeneralConfig().getString(prefix + ".gateway"));
        } else {
            throw new IllegalArgumentException("Discord proxy.url is required for " + mode.name().toLowerCase(java.util.Locale.ROOT));
        }
        DiscordClient client = DiscordClients.gateway(token, intents, endpoints.getGateway(), endpoints.getApiBase());
        discordApiBase = endpoints.getApiBase().toString();
        discordToken = token;
        discordClientManager.addAndConnect("discord", client, discordListener);
        plugin.getNeoLogger().info("Discord client started with proxy mode " + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** WebSocket connects (both QQ OBWSClient and Discord gateway) are asynchronous, so right after
     *  loadBot() returns none of the clients are connected yet. Callers that need to send a message
     *  immediately after startup (e.g. the server-start announcement) should wait here first, otherwise
     *  the send fails with "not connected" on any link slower than an instant local handshake. */
    public boolean awaitConnections(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!allConnected(getBot(), getDiscordClients())) {
            if (System.currentTimeMillis() >= deadline) return allConnected(getBot(), getDiscordClients());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return allConnected(getBot(), getDiscordClients());
            }
        }
        return true;
    }

    static boolean allConnected(Collection<NBotClient> bots, Collection<DiscordClient> discordClients) {
        for (NBotClient client : bots) if (!client.isConnected()) return false;
        for (DiscordClient client : discordClients) if (!client.isConnected()) return false;
        return true;
    }

    /** Fixed-step doubling backoff: BASE, BASE*2, BASE*4, ... capped at MAX.
     *  Streak resets to 0 once a client is observed connected again. */
    static long nextBackoffMillis(int attemptStreak) {
        long delay = RECONNECT_BACKOFF_BASE_MILLIS << Math.min(attemptStreak, 10);
        return Math.min(delay, RECONNECT_BACKOFF_MAX_MILLIS);
    }

    private static final class ReconnectBackoff {
        int attemptStreak = 0;
        long nextAttemptAtMillis = 0;
    }

    public void unloadBot() {
        getBot().forEach(NBotClient::disconnect);
        discordClientManager.shutdownAll();
        reconnectBackoff.clear();
    }

    public void reloadBot(NeoBot plugin) throws URISyntaxException {
        unloadBot();
        getBot().clear();
        loadBot(plugin);
    }

    public void resetListeners() {
        listener.reset();
        discordListener.reset();
    }
}
