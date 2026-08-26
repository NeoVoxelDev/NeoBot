package dev.neovoxel.neobot;

import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.adapter.NeoLogger;
import dev.neovoxel.neobot.adapter.RemoteExecutor;
import dev.neovoxel.neobot.bot.BotProvider;
import dev.neovoxel.neobot.command.CommandProvider;
import dev.neovoxel.neobot.config.ConfigProvider;
import dev.neovoxel.neobot.game.GameEventListener;
import dev.neovoxel.neobot.game.GameProvider;
import dev.neovoxel.neobot.scheduler.SchedulerProvider;
import dev.neovoxel.neobot.script.ScriptProvider;
import dev.neovoxel.neobot.script.ScriptScheduler;
import dev.neovoxel.neobot.storage.StorageProvider;
import dev.neovoxel.neobot.discord.DiscordService;
import org.graalvm.polyglot.HostAccess;

import java.io.File;

public interface NeoBot extends ConfigProvider, GameProvider, SchedulerProvider {
    default void enable() {
        try {
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
                    scriptProvider.setBusinessActionExecutor(new dev.neovoxel.neobot.script.NeoBotBusinessActionExecutor(this));
                    getScriptProvider().loadScript(this);
                    getGameEventListener().onPluginEnable();
                    if (!getBotProvider().awaitConnections(8000)) {
                        getNeoLogger().warn("Bot connections not fully established after 8s; "
                                + "server-start announcement may not reach every platform");
                    }
                    getDiscordService().notifyServerStarted();
                } catch (Throwable e) {
                    getNeoLogger().error("Failed to load script system", e);
                }
            });
        } catch (Throwable e) {
            getNeoLogger().error("Failed to load the plugin", e);
        }
    }
    
    default void disable() {
        // Bukkit calls disable even after a failed enable. Each subsystem is
        // therefore optional until its initialization has completed.
        if (getGameEventListener() != null) getGameEventListener().onPluginDisable();
        if (getBotProvider() != null) getDiscordService().notifyServerStopping();
        if (getGeneralConfig() != null) getGeneralConfig().flush(this);
        if (getMessageConfig() != null) getMessageConfig().flush(this);
        if (getScriptConfig() != null) getScriptConfig().flush(this);
        getNeoLogger().info("Unloading all scripts...");
        if (getScriptProvider() != null) getScriptProvider().unloadScript();
        if (getBotProvider() != null) getBotProvider().resetListeners();
        if (getGameEventListener() != null) getGameEventListener().reset();
        getNeoLogger().info("Cancelling all the tasks...");
        cancelAllTasks();
        if (getScriptScheduler() != null) getScriptScheduler().clear();
        getNeoLogger().info("Disconnecting to the bot...");
        if (getBotProvider() != null) getBotProvider().unloadBot();
        getNeoLogger().info("Saving data...");
        if (getStorageProvider() != null) getStorageProvider().closeStorage();
    }

    default void reload(CommandSender sender) {
        getGameEventListener().onPrePluginReload();
        getScriptProvider().setScriptSystemLoaded(false);
        cancelAllTasks();
        getScriptScheduler().clear();
        getNeoLogger().info("Reloading config...");
        try {
            reloadConfig(this);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            getNeoLogger().error("Config reload aborted; old configuration remains active", e);
            if (sender != null) sender.sendMessage("&c配置文件 JSON 无效，已保留旧配置: " + message);
            return;
        }
        submitAsync(() -> {
            try {
                getNeoLogger().info("Reloading bot...");
                getBotProvider().reloadBot(this);
                getBotProvider().resetListeners();
                getGameEventListener().reset();
                getNeoLogger().info("Reloading scripts...");
                getScriptProvider().unloadScript();
                getScriptProvider().loadScript(this);
                getGameEventListener().onPluginReloaded();
                getNeoLogger().info("Script system reloaded successfully");
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

    default DiscordService getDiscordService() { return getBotProvider().getDiscordService(); }

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
