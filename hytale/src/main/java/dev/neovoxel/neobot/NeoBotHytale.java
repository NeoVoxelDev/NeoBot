package dev.neovoxel.neobot;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.neovoxel.neobot.adapter.*;
import dev.neovoxel.neobot.bot.BotProvider;
import dev.neovoxel.neobot.command.CommandProvider;
import dev.neovoxel.neobot.config.EnhancedConfig;
import dev.neovoxel.neobot.config.ScriptConfig;
import dev.neovoxel.neobot.event.HytaleEventManager;
import dev.neovoxel.neobot.game.GameEventListener;
import dev.neovoxel.neobot.scheduler.ScheduledTask;
import dev.neovoxel.neobot.script.ScriptProvider;
import dev.neovoxel.neobot.script.ScriptScheduler;
import dev.neovoxel.neobot.storage.StorageProvider;
import lombok.Getter;
import lombok.Setter;
import org.graalvm.polyglot.HostAccess;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class NeoBotHytale extends JavaPlugin implements NeoBot {

    @Getter
    private GameEventListener gameEventListener;

    @Getter
    @Setter
    private BotProvider botProvider;

    @Getter
    @Setter
    private ScriptProvider scriptProvider;

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

    @Getter
    @Setter
    private CommandProvider commandProvider;

    private HytaleScheduler scheduler;

    public NeoBotHytale(JavaPluginInit init) {
        super(init);
        scheduler = new HytaleScheduler(this);
    }

    @Override
    public void start() {
        this.enable();
    }

    @HostAccess.Export
    @Override
    public NeoLogger getNeoLogger() {
        return new NHytaleLogger(this);
    }

    @Override
    public File getDataFolder() {
        return getDataDirectory().toFile();
    }

    @Override
    public void setGameEventListener(GameEventListener listener) {
        HytaleEventManager manager = new HytaleEventManager(this);
        getEventRegistry().registerGlobal(PlayerSetupConnectEvent.class, manager::onLogin);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, manager::onJoin);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, manager::onQuit);
        getEventRegistry().registerGlobal(PlayerChatEvent.class, manager::onChat);
        this.gameEventListener = listener;
    }

    @Override
    public void registerCommands() {
        HytaleCommandProvider commandProvider1 = new HytaleCommandProvider(this);
        commandProvider1.registerCommand();
        setCommandProvider(commandProvider1);
    }

    @HostAccess.Export
    @Override
    public String getPlatform() {
        return "Hytale";
    }

    @Override
    public boolean isPluginLoaded(String name) {
        boolean has = false;
        for (PluginBase plugin: PluginManager.get().getPlugins()) {
            String plname = plugin.getManifest().getName();
            if (plname.equals(name)) has = true;
        }
        return has;
    }

    @HostAccess.Export
    @Override
    public RemoteExecutor getExecutorByName(String name) {
        return null;
    }

    @HostAccess.Export
    @Override
    public Player getOnlinePlayer(String name) {
        return new HytalePlayer(Universe.get().getPlayer(name, NameMatching.EXACT));
    }

    @HostAccess.Export
    @Override
    public Player[] getOnlinePlayers() {
        return Universe.get().getPlayers().stream().map(HytalePlayer::new).toArray(HytalePlayer[]::new);
    }

    @HostAccess.Export
    @Override
    public OfflinePlayer getOfflinePlayer(String name) {
        UUID uuid = Universe.get().getPlayer(name, NameMatching.EXACT).getUuid();
        return new HytaleOfflinePlayer(name, uuid);
    }

    @HostAccess.Export
    @Override
    public void broadcast(String message) {
        Universe.get().sendMessage(Message.parse(message));
    }

    @HostAccess.Export
    @Override
    public String externalParsePlaceholder(String message, OfflinePlayer player) {
        return message;
    }


    @Override
    public ScheduledTask submit(Runnable task) {
        return new HytaleSchedulerTask(scheduler.sync(task));
    }

    @Override
    public ScheduledTask submitAsync(Runnable task) {
        return new HytaleSchedulerTask(scheduler.async(task));
    }

    @Override
    public ScheduledTask submit(Runnable task, long delay) {
        return new HytaleSchedulerTask(scheduler.syncLater(task, delay, TimeUnit.SECONDS));
    }

    @Override
    public ScheduledTask submitAsync(Runnable task, long delay) {
        return new HytaleSchedulerTask(scheduler.asyncLater(task, delay, TimeUnit.SECONDS));
    }

    @Override
    public ScheduledTask submit(Runnable task, long delay, long period) {
        return new HytaleSchedulerTask(scheduler.syncRepeating(task, delay, period, TimeUnit.SECONDS));
    }

    @Override
    public ScheduledTask submitAsync(Runnable task, long delay, long period) {
        return new HytaleSchedulerTask(scheduler.asyncRepeating(task, delay, period, TimeUnit.SECONDS));
    }

    @Override
    public void cancelAllTasks() {
        HytaleSchedulerTask.cancelAllTasks();
    }
}
