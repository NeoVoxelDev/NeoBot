package dev.neovoxel.neobot.bot;

import dev.neovoxel.nbapi.client.NBotClient;
import dev.neovoxel.nbapi.client.OBWSClient;
import dev.neovoxel.nbapi.listener.NBotListener;
import dev.neovoxel.neobot.NeoBot;

import java.net.URI;
import java.net.URISyntaxException;

public interface BotProvider {
    void setBot(NBotClient client);

    void setBotListener(NBotListener listener);

    NBotClient getBot();

    BotListener getBotListener();

    default void loadBot(NeoBot plugin) throws URISyntaxException {
        if (plugin.getGeneralConfig().getString("bot.type").equalsIgnoreCase("onebot11-ws")) {
            String url = plugin.getGeneralConfig().getString("bot.onebot11-ws.url");
            URI uri = new URI(url);
            String token = plugin.getGeneralConfig().getString("bot.onebot11-ws.access-token");
            OBWSClient client;
            if (!token.isEmpty()) {
                client = new OBWSClient(uri, token);
            } else client = new OBWSClient(uri);
            setBot(client);
            setBotListener(new BotListener(plugin));
            client.addListener(getBotListener());
        }
        plugin.submitAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (!getBot().isConnected()) {
                    getBot().reconnect();
                }
            }
        }, 0, plugin.getGeneralConfig().getInt("bot.options.check-interval"));
    }

    default void unloadBot() {
        getBot().disconnect();
    }
}
