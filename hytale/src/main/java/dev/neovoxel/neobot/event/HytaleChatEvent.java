package dev.neovoxel.neobot.event;

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import dev.neovoxel.neobot.adapter.HytalePlayer;
import dev.neovoxel.neobot.game.event.ChatEvent;
import org.graalvm.polyglot.HostAccess;

public class HytaleChatEvent extends ChatEvent {
    private final PlayerChatEvent event;

    public HytaleChatEvent(PlayerChatEvent event) {
        super(new HytalePlayer(event.getSender()), event.getContent());
        this.event = event;
    }

    @HostAccess.Export
    public void disallow() {
        event.setCancelled(true);
    }

}
