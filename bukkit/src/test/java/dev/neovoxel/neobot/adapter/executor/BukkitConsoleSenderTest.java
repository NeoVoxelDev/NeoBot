package dev.neovoxel.neobot.adapter.executor;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class BukkitConsoleSenderTest {
    @Test
    public void capturesAllStringMessageShapes() {
        BukkitConsoleSender sender = new BukkitConsoleSender();

        sender.capture(new Object[]{"first", new String[]{"second", "third"}});

        assertEquals("first\nsecond\nthird", sender.getResult());
    }

    @Test
    public void ignoresSenderIdentityFromModernBukkitOverloads() {
        BukkitConsoleSender sender = new BukkitConsoleSender();

        sender.capture(new Object[]{UUID.randomUUID(), "message"});

        assertEquals("message", sender.getResult());
    }
}
