package dev.neovoxel.neobot.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * executeAll pairs every action with every target (they are independent lists, not zipped),
 * so when a script targets both QQ and Discord at once (e.g. ServerStartEvent with
 * actions=[sendToQQ, sendToDiscord] and targets=[qq:111, discord:222]), the cross combinations
 * (sendToQQ x discord:222, sendToDiscord x qq:111) must be filtered out before reaching execute(),
 * otherwise sendToQQ tries Long.parseLong("discord:222") and blows up.
 */
class NeoBotBusinessActionExecutorRoutingTest {

    @Test
    void sendToQqOnlyMatchesQqTargets() {
        assertTrue(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToQQ", "qq:111"));
        assertTrue(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToQQ", "qq:private:222"));
        assertFalse(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToQQ", "discord:222"));
        assertFalse(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToQQ", "minecraft"));
    }

    @Test
    void sendToDiscordOnlyMatchesDiscordTargets() {
        assertTrue(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToDiscord", "discord:222"));
        assertFalse(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToDiscord", "qq:111"));
        assertFalse(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToDiscord", "minecraft:default"));
    }

    @Test
    void sendToMinecraftOnlyMatchesMinecraftTargets() {
        assertTrue(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToMinecraft", "minecraft"));
        assertTrue(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToMinecraft", "minecraft:default"));
        assertFalse(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToMinecraft", "qq:111"));
        assertFalse(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToMinecraft", "discord:222"));
    }

    @Test
    void unrecognizedActionsAreNotFilteredByPrefix() {
        assertTrue(NeoBotBusinessActionExecutor.actionMatchesTarget("executeMinecraftCommand", "bukkit"));
    }

    @Test
    void nullTargetNeverMatches() {
        assertFalse(NeoBotBusinessActionExecutor.actionMatchesTarget("sendToQQ", null));
    }

    @Test
    void dualPlatformServerStartOnlyProducesThePlatformCorrectPairs() {
        java.util.List<String> actions = java.util.Arrays.asList("sendToQQ", "sendToDiscord");
        java.util.List<String> targets = java.util.Arrays.asList("qq:111", "discord:222");

        java.util.List<String> matchedPairs = new java.util.ArrayList<>();
        for (String action : actions) for (String target : targets) {
            if (NeoBotBusinessActionExecutor.actionMatchesTarget(action, target)) {
                matchedPairs.add(action + " -> " + target);
            }
        }

        assertTrue(matchedPairs.contains("sendToQQ -> qq:111"));
        assertTrue(matchedPairs.contains("sendToDiscord -> discord:222"));
        assertEqualsSize(2, matchedPairs);
    }

    private static void assertEqualsSize(int expected, java.util.List<String> list) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, list.size(), "expected only the same-platform action/target pairs to survive: " + list);
    }
}
