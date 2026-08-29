package dev.neovoxel.neobot.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bind/unbind detection stays a Java-side shape check; the actual binding logic lives in the script. */
class BotListenerRoutingTest {
    @Test
    void recognizesBindAndUnbindWithOrWithoutSlash() {
        assertEquals("bind", BotListener.bindOrUnbindAction("/bind Steve"));
        assertEquals("bind", BotListener.bindOrUnbindAction("bind Steve"));
        assertEquals("unbind", BotListener.bindOrUnbindAction("/unbind Steve"));
        assertEquals("unbind", BotListener.bindOrUnbindAction("unbind Steve"));
    }

    @Test
    void ignoresEverythingElseSoItFallsThroughToOrdinaryForwarding() {
        assertNull(BotListener.bindOrUnbindAction("hello everyone"));
        assertNull(BotListener.bindOrUnbindAction("/bind"));
        assertNull(BotListener.bindOrUnbindAction("/bind Steve extra"));
        assertNull(BotListener.bindOrUnbindAction(null));
        assertNull(BotListener.bindOrUnbindAction(""));
    }

    @Test
    void recognizesTheBotsOwnMessagesToAvoidForwardingLoops() {
        assertTrue(BotListener.isSelfMessage(42, 42));
        assertFalse(BotListener.isSelfMessage(42, 99));
    }
}
