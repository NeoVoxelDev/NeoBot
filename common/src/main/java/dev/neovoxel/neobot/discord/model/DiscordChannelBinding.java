package dev.neovoxel.neobot.discord.model;

import lombok.Data;

@Data
public class DiscordChannelBinding {
    private final String guildId;
    private final String channelId;
}
