package dev.neovoxel.neobot.bot.discord;

import lombok.Data;

import java.net.URI;

@Data
public class DiscordEndpoints {
    private final DiscordProxyMode mode;
    private final URI apiBase;
    private final URI gateway;
}
