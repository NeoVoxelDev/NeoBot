package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.nbapi.discord.data.DiscordMessage;
import dev.neovoxel.nbapi.discord.data.DiscordUser;
import dev.neovoxel.neobot.discord.DiscordForwardingPolicy;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DiscordForwardingPolicyTest {
    @Test
    void filtersUnboundChannelsAndBotsToAvoidForwardingLoops() {
        DiscordMessage self = message(1, 9, true, "hello");
        DiscordMessage human = message(2, 9, false, "hello");
        assertFalse(DiscordForwardingPolicy.accepts(self, 1, Collections.singleton("9"), true, true));
        assertFalse(DiscordForwardingPolicy.accepts(self, 2, Collections.singleton("8"), true, true));
        assertTrue(DiscordForwardingPolicy.accepts(human, 1, Collections.singleton("9"), true, true));
        assertEquals("abc", DiscordForwardingPolicy.truncate("abcdef", 3));
    }
    private DiscordMessage message(long user, long channel, boolean bot, String content) {
        return new DiscordMessage(3, channel, null, content, new DiscordUser(user, "user", null, bot), new JSONObject());
    }
}
