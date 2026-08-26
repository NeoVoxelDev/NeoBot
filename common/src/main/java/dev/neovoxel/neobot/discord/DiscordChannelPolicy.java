package dev.neovoxel.neobot.discord;

/** Resolves the mutually exclusive Discord channel roles without OneBot identifiers. */
public final class DiscordChannelPolicy {
    private DiscordChannelPolicy() { }

    public static boolean isChatChannel(String channelId, String configuredChatChannelId) {
        return nonEmpty(channelId) && channelId.equals(configuredChatChannelId);
    }

    public static boolean isBindChannel(String channelId, String configuredBindChannelId) {
        return nonEmpty(channelId) && channelId.equals(configuredBindChannelId);
    }

    private static boolean nonEmpty(String value) { return value != null && !value.trim().isEmpty(); }
}
