package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.nbapi.discord.data.DiscordMessage;
import dev.neovoxel.nbapi.discord.data.DiscordUser;
import dev.neovoxel.nbapi.discord.event.DiscordEvent;
import dev.neovoxel.nbapi.discord.event.DiscordMessageDeleteEvent;
import dev.neovoxel.nbapi.discord.event.DiscordMessageEvent;
import dev.neovoxel.nbapi.discord.event.DiscordReadyEvent;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordBotListenerTest {
    @Test
    void mapsApiEventsToIndependentScriptEvents() {
        JSONObject raw = new JSONObject();
        DiscordUser user = new DiscordUser(1, "bot", null, true);
        DiscordMessage message = new DiscordMessage(2, 3, null, "hello", user, raw);

        assertEquals("DiscordReadyEvent", DiscordBotListener.scriptEventName(
                new DiscordReadyEvent(1, 1, user, "session", raw)));
        assertEquals("DiscordMessageCreateEvent", DiscordBotListener.scriptEventName(
                new DiscordMessageEvent(1, 1, "MESSAGE_CREATE", message, raw)));
        assertEquals("DiscordMessageUpdateEvent", DiscordBotListener.scriptEventName(
                new DiscordMessageEvent(1, 1, "MESSAGE_UPDATE", message, raw)));
        assertEquals("DiscordMessageDeleteEvent", DiscordBotListener.scriptEventName(
                new DiscordMessageDeleteEvent(1, 1, 2, 3, null, raw)));
        assertEquals("DiscordRawEvent", DiscordBotListener.scriptEventName(
                new DiscordEvent(1, 1, "GUILD_CREATE", raw)));
    }

    @Test
    void exportsSnowflakeSafeMessagingAndClientAccessToScripts() throws Exception {
        assertExported("sendMessage", String.class, String.class);
        assertExported("editMessage", String.class, String.class, String.class);
        assertExported("deleteMessage", String.class, String.class);
        assertExported("getClients");
        assertExported("register", String.class, org.graalvm.polyglot.Value.class);
    }

    private static void assertExported(String name, Class<?>... parameters) throws Exception {
        assertNotNull(DiscordBotListener.class.getMethod(name, parameters)
                .getAnnotation(org.graalvm.polyglot.HostAccess.Export.class));
    }

    @Test
    void recognizesTheBotsOwnAndOtherBotsMessagesToAvoidForwardingLoops() {
        DiscordUser self = new DiscordUser(1, "bot", null, true);
        DiscordUser otherBot = new DiscordUser(2, "webhook", null, true);
        DiscordUser human = new DiscordUser(3, "player", null, false);
        DiscordMessage fromSelf = new DiscordMessage(10, 9, null, "hello", self, new JSONObject());
        DiscordMessage fromOtherBot = new DiscordMessage(11, 9, null, "hello", otherBot, new JSONObject());
        DiscordMessage fromHuman = new DiscordMessage(12, 9, null, "hello", human, new JSONObject());

        assertTrue(DiscordBotListener.isEcho(fromSelf, 1, "9", true, true));
        assertTrue(DiscordBotListener.isEcho(fromOtherBot, 1, "9", true, true));
        assertFalse(DiscordBotListener.isEcho(fromHuman, 1, "9", true, true));
        assertFalse(DiscordBotListener.isEcho(fromSelf, 1, "9", false, false));
    }

    @Test
    void bindChannelRecognizesBindAndUnbindWithOrWithoutSlash() {
        assertEquals("BindEvent", DiscordBotListener.bindChannelEventName("/bind Steve"));
        assertEquals("UnbindEvent", DiscordBotListener.bindChannelEventName("/unbind Steve"));
        assertEquals("BindEvent", DiscordBotListener.bindChannelEventName("bind Steve"));
        assertEquals("UnbindEvent", DiscordBotListener.bindChannelEventName("unbind Steve"));
        assertEquals("UnbindEvent", DiscordBotListener.bindChannelEventName("UNBIND Steve"));
    }
}
