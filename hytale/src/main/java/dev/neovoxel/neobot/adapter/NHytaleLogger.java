package dev.neovoxel.neobot.adapter;

import dev.neovoxel.neobot.NeoBotHytale;

public class NHytaleLogger implements NeoLogger {
    private NeoBotHytale plugin;
    public NHytaleLogger(NeoBotHytale plugin) {
        this.plugin = plugin;
    }

    @Override
    public void info(String message) {
        plugin.getLogger().atInfo().log(message);
    }

    @Override
    public void warn(String message) {
        plugin.getLogger().atWarning().log(message);
    }

    @Override
    public void error(String message) {
        plugin.getLogger().atSevere().log(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        plugin.getLogger().atSevere().log(message);
        throwable.printStackTrace();
    }

    @Override
    public void debug(String message) {
        plugin.getLogger().atFine().log(message);
    }

    @Override
    public void trace(String message) {
        plugin.getLogger().atFinest().log(message);
    }
}
