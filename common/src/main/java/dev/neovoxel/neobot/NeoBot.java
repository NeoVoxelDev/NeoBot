package dev.neovoxel.neobot;

import dev.neovoxel.neobot.bot.BotProvider;
import dev.neovoxel.neobot.config.ConfigProvider;
import dev.neovoxel.neobot.game.GameEventListener;
import dev.neovoxel.neobot.library.LibraryProvider;
import dev.neovoxel.neobot.scheduler.SchedulerProvider;
import dev.neovoxel.neobot.script.ScriptProvider;
import dev.neovoxel.neobot.storage.StorageProvider;
import org.slf4j.Logger;

import java.io.File;
import java.net.URISyntaxException;

public interface NeoBot extends BotProvider, ConfigProvider, LibraryProvider, SchedulerProvider, ScriptProvider, StorageProvider {
    default void enable() {
        try {
            getLogger().info("Loading libraries...");
            loadBasicLibrary();
            getLogger().info("Loading config...");
            loadConfig(this);
            getLogger().info("Loading storage...");
            loadStorage(this);
            getLogger().info("Loading game events...");
            setGameEventListener(new GameEventListener(this));
            getLogger().info("Loading bot...");
            loadBot(this);
            getLogger().info("Loading script system...");
            submitAsync(() -> {
                try {
                    loadScript(this);
                } catch (Throwable e) {
                    getLogger().error("Failed to load script system", e);
                }
            });
        } catch (Throwable e) {
            getLogger().error("Failed to load the plugin", e);
        }
    }
    
    default void disable() {
        getLogger().info("Disconnecting to the bot...");
        unloadBot();
        getLogger().info("Saving data...");
        closeStorage();
    }

    default void reload() {
        setScriptSystemLoaded(false);
        getLogger().info("Reloading config...");
        loadConfig(this);
        submitAsync(() -> {
            getLogger().info("Reloading bot...");
            unloadBot();
            try {
                loadBot(this);
            } catch (URISyntaxException e) {
                getLogger().error("Failed to reload bot", e);
            }
        });
    }

    Logger getLogger();

    File getDataFolder();

    void setGameEventListener(GameEventListener listener);

    GameEventListener getGameEventListener();
}
