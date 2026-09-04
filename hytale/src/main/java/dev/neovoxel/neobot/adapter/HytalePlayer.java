package dev.neovoxel.neobot.adapter;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.graalvm.polyglot.HostAccess;

public class HytalePlayer extends Player {

    private final PlayerRef playerRef;

    public HytalePlayer(PlayerRef playerRef) {
        super(playerRef.getUsername(), playerRef.getUuid());
        this.playerRef = playerRef;
    }

    @HostAccess.Export
    @Override
    public void sendMessage(String message) {
        playerRef.sendMessage(Message.parse(message));
    }

    @HostAccess.Export
    @Override
    public void kick(String message) {
        playerRef.getPacketHandler().disconnect(message);

    }

    @Override
    public boolean hasPermission(String node) {
        return PermissionsModule.get().hasPermission(playerRef.getUuid(), node);
    }
}
