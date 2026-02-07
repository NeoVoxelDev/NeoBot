package dev.neovoxel.neobot;

import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.adapter.NeoLogger;
import dev.neovoxel.neobot.adapter.RemoteExecutor;
import dev.neovoxel.neobot.bot.BotProvider;
import dev.neovoxel.neobot.command.CommandProvider;
import dev.neovoxel.neobot.config.ConfigProvider;
import dev.neovoxel.neobot.config.ConfigWatcher;
import dev.neovoxel.neobot.game.GameEventListener;
import dev.neovoxel.neobot.game.GameProvider;
import dev.neovoxel.neobot.library.LibraryProvider;
import dev.neovoxel.neobot.scheduler.SchedulerProvider;
import dev.neovoxel.neobot.script.ScriptProvider;
import dev.neovoxel.neobot.script.ScriptScheduler;
import dev.neovoxel.neobot.storage.StorageProvider;
import org.graalvm.polyglot.HostAccess;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface NeoBot extends ConfigProvider, GameProvider, LibraryProvider, SchedulerProvider {
    default void enable() {
        try {
            getNeoLogger().info("Loading libraries...");
            loadBasicLibrary(this);
            getNeoLogger().info("Loading config...");
            loadConfig(this);
            getNeoLogger().info("Loading storage...");
            setStorageProvider(new StorageProvider(this));
            getStorageProvider().loadStorage();
            getNeoLogger().info("Loading game events...");
            setGameEventListener(new GameEventListener(this));
            getNeoLogger().info("Registering commands...");
            registerCommands();
            getNeoLogger().info("Loading bot...");
            BotProvider botProvider = new BotProvider(this);
            setBotProvider(botProvider);
            getBotProvider().loadBot(this);
            getNeoLogger().info("Loading script system...");
            submitAsync(() -> {
                try {
                    setScriptScheduler(new ScriptScheduler(this));
                    ScriptProvider scriptProvider = new ScriptProvider(this);
                    setScriptProvider(scriptProvider);
                    getScriptProvider().loadScript(this);
                    getGameEventListener().onPluginEnable();
                } catch (Throwable e) {
                    getNeoLogger().error("Failed to load script system", e);
                }
            });
            ConfigWatcher.start(this);
        } catch (Throwable e) {
            getNeoLogger().error("Failed to load the plugin", e);
        }
    }
    
    default void disable() {
        ConfigWatcher.stop();
        getGameEventListener().onPluginDisable();
        getGeneralConfig().flush(this);
        getMessageConfig().flush(this);
        getScriptConfig().flush(this);
        getNeoLogger().info("Unloading all scripts...");
        getScriptProvider().unloadScript();
        getBotProvider().getBotListener().reset();
        getGameEventListener().reset();
        getNeoLogger().info("Cancelling all the tasks...");
        cancelAllTasks();
        getScriptScheduler().clear();
        getNeoLogger().info("Disconnecting to the bot...");
        getBotProvider().unloadBot();
        getNeoLogger().info("Saving data...");
        getStorageProvider().closeStorage();
    }

    default void reload(CommandSender sender) {
        getGameEventListener().onPrePluginReload();
        getScriptProvider().setScriptSystemLoaded(false);
        cancelAllTasks();
        getScriptScheduler().clear();
        getNeoLogger().info("Reloading config...");
        reloadConfig(this);
        submitAsync(() -> {
            try {
                getNeoLogger().info("Reloading bot...");
                getBotProvider().reloadBot(this);
                getBotProvider().getBotListener().reset();
                getGameEventListener().reset();
                getNeoLogger().info("Reloading scripts...");
                getScriptProvider().unloadScript();
                getScriptProvider().loadScript(this);
                getGameEventListener().onPluginReloaded();
            } catch (Throwable e) {
                getNeoLogger().error("Failed to reload scripts", e);
            }
            if (sender != null) {
                sender.sendMessage(getMessageConfig().getMessage("internal.reload.reloaded"));
            }
        });
    }

    @HostAccess.Export
    NeoLogger getNeoLogger();

    File getDataFolder();

    void setGameEventListener(GameEventListener listener);

    GameEventListener getGameEventListener();

    BotProvider getBotProvider();

    void setBotProvider(BotProvider botProvider);

    ScriptProvider getScriptProvider();

    void setScriptProvider(ScriptProvider scriptProvider);

    @HostAccess.Export
    ScriptScheduler getScriptScheduler();

    void setScriptScheduler(ScriptScheduler scriptScheduler);

    @HostAccess.Export
    StorageProvider getStorageProvider();

    void setStorageProvider(StorageProvider storageProvider);

    CommandProvider getCommandProvider();

    void setCommandProvider(CommandProvider commandProvider);

    void registerCommands();

    @HostAccess.Export
    String getPlatform();

    boolean isPluginLoaded(String name);

    @HostAccess.Export
    RemoteExecutor getExecutorByName(String name);

    String getVersion();

    boolean getOnlineMode();

    String getMinecraftVersion();

    String getBrand();
}
