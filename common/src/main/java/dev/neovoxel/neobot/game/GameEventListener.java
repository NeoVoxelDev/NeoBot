package dev.neovoxel.neobot.game;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.game.event.ChatEvent;
import dev.neovoxel.neobot.game.event.DeathEvent;
import dev.neovoxel.neobot.game.event.LoginEvent;
import dev.neovoxel.neobot.game.event.PlayerEvent;
import dev.neovoxel.neobot.adapter.Player;
import dev.neovoxel.neobot.misc.EventListener;

public class GameEventListener extends EventListener {
    public GameEventListener(NeoBot plugin) {
        super(plugin);
    }

    public void onLogin(LoginEvent event) {
        fireEvent("LoginEvent", event);
    }

    public void onJoin(PlayerEvent event) {
        fireEvent("JoinEvent", event);
    }

    public void onQuit(PlayerEvent event) {
        fireEvent("QuitEvent", event);
    }

    public void onDeath(DeathEvent event) {
        fireEvent("DeathEvent", event);
    }

    public void onChat(ChatEvent event) {
        fireEvent("ChatEvent", event);
    }

    public void onPluginEnable() {
        fireEvent("PluginEnableEvent");
    }

    public void onPluginDisable() {
        fireEvent("PluginDisableEvent");
    }

    public void onPrePluginReload() {
        fireEvent("PrePluginReloadEvent");
    }

    public void onPluginReloaded() {
        fireEvent("PluginReloadedEvent");
    }
}
