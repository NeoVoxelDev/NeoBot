package dev.neovoxel.neobot.script;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceDeduplicatorTest {
    @Test void suppressesDuplicateWithinTtl() {
        SourceDeduplicator d = new SourceDeduplicator(10000);
        assertTrue(d.first("discord:1"));
        assertFalse(d.first("discord:1"));
        assertTrue(d.first("discord:2"));
    }
}
