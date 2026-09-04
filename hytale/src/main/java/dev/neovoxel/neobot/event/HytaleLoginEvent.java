package dev.neovoxel.neobot.event;

import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import dev.neovoxel.neobot.game.event.LoginEvent;
import org.graalvm.polyglot.HostAccess;

public class HytaleLoginEvent extends LoginEvent {
    private final PlayerSetupConnectEvent loginEvent;

    public HytaleLoginEvent(PlayerSetupConnectEvent loginEvent) {
        super(loginEvent.getUsername(), loginEvent.getUuid());
        this.loginEvent = loginEvent;
    }

    @HostAccess.Export
    @Override
    public void disallow(String reason) {
        loginEvent.getPacketHandler().disconnect(reason);
        loginEvent.setReason(reason);
        loginEvent.setCancelled(true);
    }
}
