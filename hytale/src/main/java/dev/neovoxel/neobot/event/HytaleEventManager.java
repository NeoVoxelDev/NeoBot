package dev.neovoxel.neobot.event;

import com.hypixel.hytale.server.core.event.events.player.*;
import dev.neovoxel.neobot.NeoBotHytale;
import dev.neovoxel.neobot.adapter.HytalePlayer;
import dev.neovoxel.neobot.game.event.PlayerEvent;

public class HytaleEventManager {
    private final NeoBotHytale plugin;

    public HytaleEventManager(NeoBotHytale plugin) {
        this.plugin = plugin;
    }

    public void onLogin(PlayerSetupConnectEvent loginEvent) {
        plugin.getGameEventListener().onLogin(new HytaleLoginEvent(loginEvent));
    }

    public void onJoin(PlayerReadyEvent event) {
        plugin.getGameEventListener().onJoin(new PlayerEvent(new HytalePlayer(event.getPlayer().getPlayerRef())));
    }

    public void onQuit(PlayerDisconnectEvent event) {
        plugin.getGameEventListener().onQuit(new PlayerEvent(new HytalePlayer(event.getPlayerRef())));
    }

    public void onChat(PlayerChatEvent event) {
        plugin.getGameEventListener().onChat(new HytaleChatEvent(event));
    }
}
