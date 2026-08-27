package dev.neovoxel.neobot.config;

import dev.neovoxel.neobot.NeoBot;
import org.graalvm.polyglot.HostAccess;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public interface ConfigProvider {
    default void loadConfig(NeoBot plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        loadGeneralConfig(plugin);
        loadMessageConfig(plugin);
        loadScriptConfig(plugin);
    }

    // without script config, script config will be reloaded when loading scripts
    default void reloadConfig(NeoBot plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        getGeneralConfig().flush(plugin);
        getMessageConfig().flush(plugin);
    }

    void setMessageConfig(EnhancedConfig config);

    void setScriptConfig(ScriptConfig config);

    @HostAccess.Export
    ScriptConfig getScriptConfig();

    @HostAccess.Export
    EnhancedConfig getMessageConfig();

    void setGeneralConfig(EnhancedConfig config);

    @HostAccess.Export
    EnhancedConfig getGeneralConfig();

    default void loadGeneralConfig(NeoBot plugin) {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.json");
            if (!configFile.exists()) saveResource(plugin.getDataFolder(), "config.json");
            EnhancedConfig config = new EnhancedConfig(configFile, new JSONObject(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8)));
            config.addOption("bot.discord.enabled", false);
            config.addOption("bot.discord.token", "");
            config.addOption("bot.discord.intents", 37377);
            config.addOption("bot.discord.proxy.mode", "official");
            config.addOption("bot.discord.proxy.url", "");
            config.addOption("bot.discord.channels.chat-channel-id", "");
            config.addOption("bot.discord.channels.bind-channel-id", "");
            config.addOption("bot.discord.channels.server-messages-channel-id", "");
            config.addOption("bot.discord.server-status.enabled", true);
            config.addOption("bot.discord.server-status.server-name", "Minecraft");
            config.addOption("bot.discord.server-status.start-message", "[${server}] 服务器已启动!");
            config.addOption("bot.discord.server-status.stop-message", "[${server}] 服务器已关闭!");
            config.addOption("bot.qq.server-status.enabled", true);
            config.addOption("bot.qq.player-status.enabled", true);
            config.addOption("bot.qq.management.owner-user-ids", new String[0]);
            config.addOption("bot.qq.management.admin-user-ids", new String[0]);
            config.addOption("bot.discord.player-status.enabled", true);
            config.addOption("bot.player-status.join-message", "[${server}] ${player} 进入了服务器!");
            config.addOption("bot.player-status.quit-message", "[${server}] ${player} 离开了服务器!");
            config.addOption("bot.player-status.death-message", "[${server}] ${player} 逝世了!");
            config.addOption("bot.account.require-binding", true);
            config.addOption("bot.account.require-binding-message", "请绑定 QQ 或 Discord 账号后再进入服务器。");
            config.addOption("bot.discord.chat.maximum-length", 1900);
            config.addOption("bot.discord.chat.ignore-self", true);
            config.addOption("bot.discord.chat.ignore-bots", true);
            config.addOption("bot.discord.chat.minecraft-to-discord.enabled", true);
            config.addOption("bot.discord.chat.minecraft-to-discord.format", "[MC] ${player}: ${message}");
            config.addOption("bot.discord.chat.discord-to-minecraft.enabled", true);
            config.addOption("bot.discord.chat.discord-to-minecraft.format", "§d[Discord(${channel})]§r §b${user}§r: ${message}");
            config.addOption("bot.discord.account.maximum-bindings-per-user", 1);
            config.addOption("bot.discord.management.admin-user-ids", new String[0]);
            config.addOption("bot.discord.management.owner-user-ids", new String[0]);
            config.addOption("bot.discord.management.default-server", "login");
            config.addOption("bot.discord.management.servers", new org.json.JSONArray().put(new org.json.JSONObject().put("server-name", "login").put("prefix", "login").put("executor", "bukkit").put("enabled", true)));
            config.addOption("bot.discord.management.remote-command-result-format", "[NeoBot] 命令执行结果: \n${result}");
            config.addOption("chat-forward.to-qq.enable", true);
            config.addOption("chat-forward.to-qq.format", "[MC] ${player}: ${message}");
            config.addOption("chat-forward.to-qq.max-length", 1900);
            config.addOption("chat-forward.to-game.enable", true);
            config.addOption("chat-forward.to-game.format", "§c[QQ群(${group})]§r §b${user}§r: ${message}");
            config.addOption("chat-forward.to-game.max-length", 1900);
            config.addOption("repository.use-github-proxy", false);
            config.addOption("repository.github-proxy-url", "");
            setGeneralConfig(config);
        } catch (Exception e) {
            plugin.getNeoLogger().error("Failed to release the general config file", e);
        }
    }

    default void loadMessageConfig(NeoBot plugin) {
        try {
            File messageFile = new File(plugin.getDataFolder(), "messages.json");
            if (!messageFile.exists()) saveResource(plugin.getDataFolder(), "messages.json");
            JSONObject messages = new JSONObject(new String(Files.readAllBytes(messageFile.toPath()), StandardCharsets.UTF_8));
            JSONObject defaultMessages;
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream("messages.json")) {
                if (stream == null) throw new IOException("Bundled messages.json is missing");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) >= 0) bytes.write(buffer, 0, read);
                defaultMessages = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
            if (MessageEncodingRepair.repair(messages, defaultMessages)) {
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(messageFile), StandardCharsets.UTF_8)) {
                    writer.write(messages.toString(4));
                }
                plugin.getNeoLogger().info("Repaired legacy message encoding in messages.json");
            }
            setMessageConfig(new EnhancedConfig(messageFile, messages));
        } catch (Exception e) {
            plugin.getNeoLogger().error("Failed to release the messages config file", e);
        }
    }

    default void loadScriptConfig(NeoBot plugin) {
        try {
            File scriptFile = new File(plugin.getDataFolder(), "scripts.json");
            if (!scriptFile.exists()) saveResource(plugin.getDataFolder(), "scripts.json");
            setScriptConfig(new ScriptConfig(scriptFile, new JSONObject(new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8)),
                    getGeneralConfig()));
        } catch (Exception e) {
            plugin.getNeoLogger().error("Failed to release the scripts config file", e);
        }
    }

    default void saveResource(File parent, String fileName) throws IOException {
        File file = new File(parent, fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(fileName), StandardCharsets.UTF_8));
        String line;
        StringBuilder builder = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        reader.close();
        if (!file.exists()) file.createNewFile();
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        writer.write(builder.toString());
        writer.close();
    }
}
