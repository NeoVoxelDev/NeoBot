package dev.neovoxel.neobot;

import dev.neovoxel.neobot.adapter.*;
import dev.neovoxel.neobot.adapter.executor.BukkitConsoleSender;
import dev.neovoxel.neobot.adapter.executor.DedicatedExecutor;
import dev.neovoxel.neobot.adapter.executor.MinecraftServerExecutor;
import dev.neovoxel.neobot.adapter.executor.NativeExecutor;
import dev.neovoxel.neobot.bot.BotProvider;
import dev.neovoxel.neobot.command.CommandProvider;
import dev.neovoxel.neobot.config.EnhancedConfig;
import dev.neovoxel.neobot.config.ScriptConfig;
import dev.neovoxel.neobot.event.BukkitEventManager;
import dev.neovoxel.neobot.game.GameEventListener;
import dev.neovoxel.neobot.misc.EventListener;
import dev.neovoxel.neobot.extension.BukkitExtensionsManager;
import dev.neovoxel.neobot.scheduler.ScheduledTask;
import dev.neovoxel.neobot.script.ScriptProvider;
import dev.neovoxel.neobot.script.ScriptScheduler;
import dev.neovoxel.neobot.storage.StorageProvider;
import lombok.Getter;
import lombok.Setter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class NeoBotBukkit extends JavaPlugin implements NeoBot {
    private GameEventListener gameEventListener;

    @Setter
    private BotProvider botProvider;

    @Setter
    private ScriptProvider scriptProvider;

    @Getter
    @Setter
    private BukkitExtensionsManager extensionsManager;

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private StorageProvider storageProvider;

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private ScriptScheduler scriptScheduler;

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private EnhancedConfig messageConfig;

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private EnhancedConfig generalConfig;

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private ScriptConfig scriptConfig;

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private String storageType;

    @Setter
    private CommandProvider commandProvider;

    @HostAccess.Export
    @Override
    public NeoLogger getNeoLogger() {
        return new BukkitLogger(getLogger());
    }

    public void initExtensionsManager() {
        extensionsManager = new BukkitExtensionsManager();
    }

    @Override
    public void setGameEventListener(GameEventListener listener) {
        Bukkit.getPluginManager().registerEvents(new BukkitEventManager(this), this);
        this.gameEventListener = listener;
    }

    @Override
    public Map<String, EventListener> getListenerMap(){
        return extensionsManager.getListenerMap();
    }

    @Override
    public void loadExtensions(NeoBot plugin) {
        extensionsManager.loadExtensions(plugin);
    }

    @Override
    public void unloadExtensions() {
        extensionsManager.unloadExtensions();
    }


    @Override
    public ScheduledTask submit(Runnable task) {
        return new BukkitScheduledTask(Bukkit.getScheduler().runTask(this, task));
    }

    @Override
    public ScheduledTask submitAsync(Runnable task) {
        return new BukkitScheduledTask(Bukkit.getScheduler().runTaskAsynchronously(this, task));
    }

    @Override
    public ScheduledTask submit(Runnable task, long delay) {
        return new BukkitScheduledTask(Bukkit.getScheduler().runTaskLater(this, task, delay * 20));
    }

    @Override
    public ScheduledTask submitAsync(Runnable task, long delay) {
        return new BukkitScheduledTask(Bukkit.getScheduler().runTaskLaterAsynchronously(this, task, delay * 20));
    }

    @Override
    public ScheduledTask submit(Runnable task, long delay, long period) {
        return new BukkitScheduledTask(Bukkit.getScheduler().runTaskTimer(this, task, delay * 20, period * 20));
    }

    @Override
    public ScheduledTask submitAsync(Runnable task, long delay, long period) {
        return new BukkitScheduledTask(Bukkit.getScheduler().runTaskTimerAsynchronously(this, task, delay * 20, period * 20));
    }

    @Override
    public void cancelAllTasks() {
        BukkitScheduledTask.cancelAll(this);
    }

    @Override
    public void onEnable() {
        this.enable();
    }

    @Override
    public void onDisable() {
        this.disable();
    }

    @HostAccess.Export
    @Override
    public void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    @Override
    public void registerCommands() {
        BukkitCommandProvider commandProvider1 = new BukkitCommandProvider(this);
        commandProvider1.registerCommand();
        setCommandProvider(commandProvider1);
    }

    @HostAccess.Export
    @Override
    public Player getOnlinePlayer(String name) {
        return new BukkitPlayer(Bukkit.getPlayer(name));
    }

    @HostAccess.Export
    @Override
    public OfflinePlayer getOfflinePlayer(String name) {
        return new BukkitOfflinePlayer(Bukkit.getOfflinePlayer(name));
    }

    @HostAccess.Export
    @Override
    public Player[] getOnlinePlayers() {
        List<Player> players = new ArrayList<>();
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            players.add(new BukkitPlayer(player));
        }
        return players.toArray(new Player[0]);
    }

    @HostAccess.Export
    @Override
    public String externalParsePlaceholder(String message, OfflinePlayer player) {
        try {
            if (player == null) {
                return PlaceholderAPI.setPlaceholders(null, message);
            } else if (Bukkit.getPlayer(player.getName()) == null) {
                return PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(player.getName()), message);
            } else {
                return PlaceholderAPI.setPlaceholders(Bukkit.getPlayer(player.getName()), message);
            }
        } catch (Exception ignored) {
            return message;
        }
    }

    @HostAccess.Export
    @Override
    public String getPlatform() {
        return "Bukkit";
    }

    @Override
    public boolean isPluginLoaded(String name) {
        return Bukkit.getPluginManager().isPluginEnabled(name);
    }

    @HostAccess.Export
    @Override
    public RemoteExecutor getExecutorByName(String name) {
        if (name.equalsIgnoreCase("native")) {
            return new NativeExecutor();
        } else if (name.equalsIgnoreCase("dedicated")) {
            return new DedicatedExecutor();
        } else if (name.equalsIgnoreCase("minecraft")) {
            return new MinecraftServerExecutor();
        } else if (name.equalsIgnoreCase("bukkit")) {
            return new BukkitConsoleSender();
        }
        return null;
    }
}
