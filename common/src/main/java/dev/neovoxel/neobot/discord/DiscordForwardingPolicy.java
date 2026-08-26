package dev.neovoxel.neobot.discord;

import dev.neovoxel.nbapi.discord.data.DiscordMessage;

import java.util.Set;

public final class DiscordForwardingPolicy {
    private DiscordForwardingPolicy() {}

    public static boolean accepts(DiscordMessage message, long selfId, Set<String> channels,
                                  boolean ignoreSelf, boolean ignoreBots) {
        if (message == null || message.getAuthor() == null || message.getContent() == null || message.getContent().isEmpty()) return false;
        if (!channels.contains(Long.toString(message.getChannelId()))) return false;
        if (ignoreSelf && message.getAuthor().getId() == selfId) return false;
        return !ignoreBots || !message.getAuthor().isBot();
    }

    public static String truncate(String value, int maximumLength) {
        if (value == null) return "";
        return maximumLength > 0 && value.length() > maximumLength ? value.substring(0, maximumLength) : value;
    }
}
