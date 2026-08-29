package dev.neovoxel.neobot.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Discord line in "/neobot status" used to be built via raw string concatenation
 * ("&f  - &adiscord: &b" + ...) instead of the "internal.status.data.bot" template that
 * the QQ lines use. EnhancedConfig translates &-color-codes to §-color-codes for the whole
 * messages.json file at load time, so template-driven text renders correctly, but a Java
 * string literal built at runtime never goes through that translation and stays raw. These
 * tests cover the fix: reusing the same template renderer for every bot line, QQ or Discord.
 */
class CommandProviderStatusFormattingTest {

    @Test
    void formatBotStatusLineSubstitutesTypeAndConnected() {
        String template = "&f  - &a${type}: &b${connected}";
        assertEquals("&f  - &aonebot11-ws: &b在线",
                CommandProvider.formatBotStatusLine(template, "onebot11-ws", "在线"));
    }

    @Test
    void formatBotStatusLineWorksForDiscordTooReusingTheSameTemplate() {
        String template = "&f  - &a${type}: &b${connected}";
        assertEquals("&f  - &adiscord: &b离线 (代理=official, server-messages=未配置)",
                CommandProvider.formatBotStatusLine(template, "discord",
                        CommandProvider.buildDiscordStatusText(false, "official", "")));
    }

    @Test
    void buildDiscordStatusTextReportsConnectedAndConfiguredChannel() {
        assertEquals("在线 (代理=official, server-messages=已配置)",
                CommandProvider.buildDiscordStatusText(true, "official", "1536213930849079306"));
    }

    @Test
    void buildDiscordStatusTextReportsDisconnectedAndUnconfiguredChannel() {
        assertEquals("离线 (代理=official, server-messages=未配置)",
                CommandProvider.buildDiscordStatusText(false, "official", ""));
    }

    @Test
    void buildDiscordStatusTextTreatsBlankChannelAsUnconfigured() {
        assertEquals("离线 (代理=official, server-messages=未配置)",
                CommandProvider.buildDiscordStatusText(false, "official", "   "));
    }

    @Test
    void buildDiscordStatusTextReflectsNonOfficialProxyMode() {
        assertEquals("在线 (代理=local, server-messages=已配置)",
                CommandProvider.buildDiscordStatusText(true, "local", "222"));
    }
}
