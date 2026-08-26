package dev.neovoxel.neobot.discord.model;

import lombok.Data;

import java.util.UUID;

@Data
public class DiscordAccountBinding {
    private final UUID minecraftUuid;
    private final String minecraftName;
    private final String discordUserId;
    /** QQ identity is optional; this table is the shared binding record for both bridges. */
    private String qqUserId;

    public DiscordAccountBinding(UUID minecraftUuid, String minecraftName, String discordUserId) {
        this.minecraftUuid = minecraftUuid;
        this.minecraftName = minecraftName;
        this.discordUserId = discordUserId;
    }
}
