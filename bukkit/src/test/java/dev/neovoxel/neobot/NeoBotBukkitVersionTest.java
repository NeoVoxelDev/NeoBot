package dev.neovoxel.neobot;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class NeoBotBukkitVersionTest {
    @Test
    public void parsesLegacyMinecraftVersion() {
        assertArrayEquals(new int[]{1, 8},
                NeoBotBukkit.parseMinecraftVersion("1.8.8-R0.1-SNAPSHOT"));
    }

    @Test
    public void parsesCurrentMinecraftVersion() {
        assertArrayEquals(new int[]{26, 2},
                NeoBotBukkit.parseMinecraftVersion("26.2-R0.1-SNAPSHOT"));
    }

    @Test
    public void parsesVersionEmbeddedByServerBrand() {
        assertArrayEquals(new int[]{1, 21},
                NeoBotBukkit.parseMinecraftVersion("git-Paper-123 (MC: 1.21.8)"));
    }

    @Test
    public void letsUnknownFutureVersionAttemptToLoad() {
        assertArrayEquals(new int[]{Integer.MAX_VALUE, 0},
                NeoBotBukkit.parseMinecraftVersion("custom-server"));
    }
}
