package dev.neovoxel.neobot.config;

import dev.neovoxel.neobot.NeoBot;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigWatcher implements Runnable{
    private static final ConfigWatcher INSTANCE = new ConfigWatcher();
    private static ExecutorService watchExecutor;
    private NeoBot plugin;
    private WatchService watchService;
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private Path pluginFolder;
    private boolean running = true;
    private final Map<String, Long> lastConfigChangeMillis = new ConcurrentHashMap<>();

    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE);
        keys.put(key, dir);
    }

    @Override
    public void run() {
        plugin.getNeoLogger().info("Loading config watcher...");
        try {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take(); // 阻塞直到有事件发生
                } catch (InterruptedException e) {
                    if (!running) {
                        break;
                    }
                    continue;
                } catch (ClosedWatchServiceException e) {
                    plugin.getNeoLogger().warn("Config watcher closed...");
                    running = false;
                    break;
                }

                Path dir = keys.get(key);
                if (dir == null) {
                    plugin.getNeoLogger().warn("Unknown watching dir...");
                    continue;
                }



                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    String fileNameStr = fileName.toString();
                    long now = System.currentTimeMillis();
                    Long last = lastConfigChangeMillis.get(fileNameStr);
                    if (last != null && (now - last) < 1000L) {
                        continue;
                    }
                    lastConfigChangeMillis.put(fileNameStr, now);
                    if (dir.endsWith("scripts") || fileNameStr.equals("scripts.json")) {
                        plugin.submitAsync(() -> {
                            plugin.getNeoLogger().info("Scripts change detected...");
                            plugin.getNeoLogger().info("Reloading scripts...");
                            plugin.getScriptScheduler().cancelAllTasks();
                            plugin.getScriptProvider().setScriptSystemLoaded(false);
                            plugin.getScriptProvider().unloadScript();
                            try {
                                plugin.getScriptProvider().loadScript(plugin);
                            } catch (Throwable e) {
                                plugin.getNeoLogger().error(plugin.getMessageConfig().getMessage("internal.script.reload.error")
                                        .replace("${error}", e.getMessage()));
                            }
                            plugin.getNeoLogger().info(plugin.getMessageConfig().getMessage("internal.script.reload.success"));
                        }, 1);
                    } else if (fileNameStr.equals("config.json") || fileNameStr.equals("messages.json")) {
                        plugin.submitAsync(() -> {
                            plugin.getNeoLogger().info(plugin.getMessageConfig().getMessage("internal.reload.reloading"));
                            plugin.reload(null);
                            plugin.getNeoLogger().info(plugin.getMessageConfig().getMessage("internal.reload.reloaded"));
                        }, 1);
                    }
                }

                // 重置key，如果无效则移除
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getNeoLogger()
                    .warn("Config watcher error!");
            e.printStackTrace();
        } finally {
            try {
                watchService.close();
            } catch (IOException e) {
                plugin.getNeoLogger().warn(
                        "Config watcher closed error!");
            }
        }
    }

    public static void start(NeoBot plugin) throws IOException {
        INSTANCE.plugin = plugin;
        INSTANCE.watchService = FileSystems.getDefault().newWatchService();
        INSTANCE.pluginFolder = plugin.getDataFolder().toPath();
        INSTANCE.registerDirectory(INSTANCE.pluginFolder);

        Path scripts = INSTANCE.pluginFolder.resolve("scripts");
        if (Files.exists(scripts) && Files.isDirectory(scripts)) {
            INSTANCE.registerDirectory(scripts);
        }

        try {
            watchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "NeoBot-ConfigWatcher");
                t.setDaemon(true);
                return t;
            });
            watchExecutor.submit(INSTANCE);
        } catch (Exception e) {
            plugin.getNeoLogger().error("Loading config watcher error!");
            e.printStackTrace();
        }
    }

    public static void stop() {
        INSTANCE.running = false;
        try {
            INSTANCE.watchService.close();
            watchExecutor.shutdownNow();
            INSTANCE.plugin.getNeoLogger().error("Config watcher closed...");
        } catch (IOException e) {
            INSTANCE.plugin.getNeoLogger().error("Config watcher closed error!");
        }
    }
}
