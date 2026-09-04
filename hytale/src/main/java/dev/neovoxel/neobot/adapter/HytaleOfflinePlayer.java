package dev.neovoxel.neobot.adapter;

import com.hypixel.hytale.server.core.universe.Universe;
import org.graalvm.polyglot.HostAccess;

import java.util.UUID;

public class HytaleOfflinePlayer extends OfflinePlayer {
    private UUID uuid;

    public HytaleOfflinePlayer(String name, UUID uuid) {
        super(name, uuid);
        this.uuid = uuid;
    }

    @HostAccess.Export
    @Override
    public boolean isOnline() {
        return Universe.get().getPlayer(uuid).isValid();
    }
}
