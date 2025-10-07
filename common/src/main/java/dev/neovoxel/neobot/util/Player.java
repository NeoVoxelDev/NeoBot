package dev.neovoxel.neobot.util;

import java.util.UUID;

public abstract class Player {
    private final String name;
    private final UUID uuid;

    protected Player(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public abstract void sendMessage(String message);

    public abstract void kick(String message);
}
