package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.neobot.discord.DiscordChannelPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordChannelPolicyTest {
    @Test
    void keepsChatAndBindingChannelsStrictlySeparated() {
        assertTrue(DiscordChannelPolicy.isChatChannel("123456789012345678", "123456789012345678"));
        assertFalse(DiscordChannelPolicy.isChatChannel("223456789012345678", "123456789012345678"));
        assertTrue(DiscordChannelPolicy.isBindChannel("223456789012345678", "223456789012345678"));
        assertFalse(DiscordChannelPolicy.isBindChannel("123456789012345678", "223456789012345678"));
        assertFalse(DiscordChannelPolicy.isChatChannel("123456789012345678", ""));
        assertFalse(DiscordChannelPolicy.isBindChannel("", "223456789012345678"));
    }
}
