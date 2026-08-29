package dev.neovoxel.neobot.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Java only recognizes the shape of a remote command; authorization and execution stay in the script. */
class DiscordServiceRemoteCommandDetectionTest {
    @Test
    void withoutPluginConfigNothingLooksLikeARemoteCommand() {
        DiscordService service = new DiscordService(null, null, null);
        assertFalse(service.looksLikeRemoteCommand("command say hi"));
        assertFalse(service.looksLikeRemoteCommand("login say hi"));
    }

    @Test
    void rejectsOrdinaryChatAndEmptyContent() {
        DiscordService service = new DiscordService(null, null, null);
        assertFalse(service.looksLikeRemoteCommand("hello everyone"));
        assertFalse(service.looksLikeRemoteCommand(""));
        assertFalse(service.looksLikeRemoteCommand(null));
    }
}
