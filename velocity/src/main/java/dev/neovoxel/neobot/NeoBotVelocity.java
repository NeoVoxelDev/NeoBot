package dev.neovoxel.neobot;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import dev.neovoxel.jarflow.JarFlow;
import dev.neovoxel.neobot.adapter.*;
import dev.neovoxel.neobot.bot.BotProvider;
import dev.neovoxel.neobot.command.CommandProvider;
import dev.neovoxel.neobot.config.EnhancedConfig;
import dev.neovoxel.neobot.config.ScriptConfig;
import dev.neovoxel.neobot.event.VelocityEventManager;
import dev.neovoxel.neobot.game.GameEventListener;
import dev.neovoxel.neobot.loader.VelocityLibraryLoader;
import dev.neovoxel.neobot.scheduler.ScheduledTask;
import dev.neovoxel.neobot.script.ScriptProvider;
import dev.neovoxel.neobot.script.ScriptScheduler;
import dev.neovoxel.neobot.storage.StorageProvider;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

@Plugin(id = "neobot", name = "NeoBot", version = "0.2", authors = {"NeoVoxelDev Team"}, description = "A bot plugin that connects Minecraft with QQ, Kook, Discord, etc.")
public class NeoBotVelocity implements NeoBot {

    @Getter
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

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

    @Inject
    public NeoBotVelocity(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        JarFlow.setLoader(new VelocityLibraryLoader(this, proxyServer.getPluginManager()));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.enable();
    }

    @HostAccess.Export
    @Override
    public NeoLogger getNeoLogger() {
        return new DefaultNeoLogger(logger);
    }

    @Override
    public File getDataFolder() {
        return dataDirectory.toFile();
    }

    @Override
    public void setGameEventListener(GameEventListener listener) {
        proxyServer.getEventManager().register(this, new VelocityEventManager(this));
        this.gameEventListener = listener;
    }

    @Override
    public void registerCommands() {
        VelocityCommandProvider commandProvider1 = new VelocityCommandProvider(this);
        commandProvider1.registerCommand();
        setCommandProvider(commandProvider1);
    }

    @HostAccess.Export
    @Override
    public String getPlatform() {
        return proxyServer.getVersion().getName();
    }

    @Override
    public boolean isPluginLoaded(String name) {
        return proxyServer.getPluginManager().isLoaded(name);
    }

    @HostAccess.Export
    @Override
    public RemoteExecutor getExecutorByName(String name) {
        return null;
    }

    @HostAccess.Export
    @Override
    public Player getOnlinePlayer(String name) {
        return new VelocityPlayer(proxyServer.getPlayer(name).get());
    }

    @HostAccess.Export
    @Override
    public Player[] getOnlinePlayers() {
        return proxyServer.getAllPlayers().stream().map(VelocityPlayer::new).toArray(VelocityPlayer[]::new);
    }

    @HostAccess.Export
    @Override
    public OfflinePlayer getOfflinePlayer(String name) {
        return new VelocityOfflinePlayer(proxyServer, GameProfile.forOfflinePlayer(name));
    }

    @HostAccess.Export
    @Override
    public void broadcast(String message) {
        proxyServer.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
    }

    @HostAccess.Export
    @Override
    public String externalParsePlaceholder(String message, OfflinePlayer player) {
        return message;
    }

    @Override
    public ScheduledTask submit(Runnable task) {
        return new VelocitySchedulerTask(proxyServer.getScheduler().buildTask(this, task).schedule());
    }

    @Override
    public ScheduledTask submitAsync(Runnable task) {
        return submit(task);
    }

    @Override
    public ScheduledTask submit(Runnable task, long delay) {
        return new VelocitySchedulerTask(proxyServer.getScheduler().buildTask(this, task)
                .delay(Duration.ofSeconds(delay)).schedule());
    }

    @Override
    public ScheduledTask submitAsync(Runnable task, long delay) {
        return submit(task, delay);
    }

    @Override
    public ScheduledTask submit(Runnable task, long delay, long period) {
        return new VelocitySchedulerTask(proxyServer.getScheduler().buildTask(this, task)
                .delay(Duration.ofSeconds(delay)).repeat(Duration.ofSeconds(period)).schedule());
    }

    @Override
    public ScheduledTask submitAsync(Runnable task, long delay, long period) {
        return submit(task, delay, period);
    }

    @Override
    public void cancelAllTasks() {
        VelocitySchedulerTask.cancelAllTasks();
    }

    @Override
    public String getVersion() {
        return proxyServer.getPluginManager().getPlugin("neobot").get().getDescription().getVersion().orElse("Unknown");
    }

    @Override
    public String getMinecraftVersion() {
        return "Unknown";
    }

    @Override
    public String getBrand() {
        return proxyServer.getVersion().getName();
    }

    @Override
    public boolean getOnlineMode() {
        return proxyServer.getConfiguration().isOnlineMode();
    }
}
