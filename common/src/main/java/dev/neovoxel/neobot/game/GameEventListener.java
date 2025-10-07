package dev.neovoxel.neobot.game;

import dev.neovoxel.nbapi.event.NEvent;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.game.event.JoinEvent;
import dev.neovoxel.neobot.util.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.IdentityHashMap;
import java.util.Map;

public class GameEventListener {

    private final NeoBot plugin;

    private final Map<Value, String> map = new IdentityHashMap<>();

    public GameEventListener(NeoBot plugin) {
        this.plugin = plugin;
    }

    @HostAccess.Export
    public void register(String eventName, Value method) {
        if (method.canExecute()) map.put(method, eventName);
    }

    private void fireEvent(String eventName, Object... args) {
        for (Map.Entry<Value, String> entry : map.entrySet()) {
            if (entry.getValue().equals(eventName)) {
                entry.getKey().execute(args);
            }
        }
    }

    public void onJoin(JoinEvent event) {
        fireEvent("JoinEvent", event);
    }

    public void onQuit(Player player) {
        fireEvent("QuitEvent", player);
    }

    public void onDeath(Player player, String message) {
        fireEvent("DeathEvent", player, message);
    }

    public void onChat(Player player, String message) {
        fireEvent("ChatEvent", player, message);
    }
}
