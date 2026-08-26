package dev.neovoxel.neobot.migrate;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.config.Config;
import dev.neovoxel.neobot.config.EnhancedConfig;
import dev.neovoxel.neobot.config.ScriptConfig;
import dev.neovoxel.neobot.util.ListUtil;
import dev.neovoxel.neobot.util.MigrationUtil;
import org.json.JSONObject;

public class ConfigMigration {
    private final NeoBot plugin;
    private final Config oldConfig;
    private final EnhancedConfig newConfig;

    public ConfigMigration(NeoBot plugin, Config oldConfig, EnhancedConfig newConfig) {
        this.plugin = plugin;
        this.oldConfig = oldConfig;
        this.newConfig = newConfig;
    }

    public static void migrate(NeoBot plugin, CommandSender sender) throws Throwable {
        JSONObject configObj = MigrationUtil.parseOriginConfig(plugin, "config");
        if (configObj == null) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.no-config")
                    .replace("${config}", "config.yml"));
        }
        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.start-migrating-config"));
        // step 1: config.yml -> scripts.json
        Config oldConfig = new Config(configObj);
        ScriptConfig scriptConfig = plugin.getScriptConfig();
        ConfigMigration migration = new ConfigMigration(plugin, oldConfig, scriptConfig);
        migration.alternative("whitelist.enable");
        migration.alternative("whitelist.need-bind-to-login");
        migration.alternative("whitelist.max-bind-count");
        migration.alternative("whitelist.prefix.bind");
        migration.alternative("whitelist.prefix.unbind");
        migration.alternative("whitelist.unbind_on_leave", "whitelist.unbind-on-leaving");
        migration.alternative("whitelist.admin.enable", "whitelist.admin.bind.enable");
        migration.alternative("whitelist.admin.bind", "whitelist.admin.bind.prefix");
        migration.alternative("whitelist.admin.enable", "whitelist.admin.unbind.enable");
        migration.alternative("whitelist.admin.unbind", "whitelist.admin.unbind.prefix");
        migration.alternative("whitelist.change-nickname-on-bind.enable");
        migration.alternative("whitelist.change-nickname-on-bind.format");
        migration.alternative("chat.max_forward_length", "chat-forward.to-game.max-length");
        migration.alternative("chat.max_forward_length", "chat-forward.to-qq.max-length");
        migration.alternative("chat.group_to_server.enable", "chat-forward.to-game.enable");
        migration.alternative("chat.server_to_group.enable", "chat-forward.to-qq.enable");
        migration.alternative("chat.server_to_group.default_format", "chat-forward.to-qq.format");
        migration.alternative("notify.server_status.enable", "game-notice.server-start.enable");
        migration.alternative("notify.server_status.enable", "game-notice.server-stop.enable");
        migration.alternative("notify.player_status.enable", "game-notice.join.enable");
        migration.alternative("notify.player_status.enable", "game-notice.quit.enable");
        migration.alternative("notify.player_death.enable", "game-notice.die.enable");
        migration.alternative("command_execution.enable", "command-execute.enable");
        migration.alternative("command_execution.allow", "command-execute.allow");
        migration.alternative("command_execution.prefix", "command-execute.prefix");
        migration.alternative("command_execution.sort", "command-execute.sort");
        migration.alternative("command_execution.format", "command-execute.format");
        migration.alternative("command_execution.delay", "command-execute.delay");
        migration.save();
        // step 2: bot.yml -> config.json
        JSONObject botConfigObj = MigrationUtil.parseOriginConfig(plugin, "bot");
        if (botConfigObj == null) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.no-config")
                    .replace("${config}", "bot.yml"));
        }
        Config botConfig = new Config(botConfigObj);
        EnhancedConfig generalConfig = plugin.getGeneralConfig();
        generalConfig.put("bot.onebot11-ws.url", "ws://" + botConfig.getString("ws.host") + ":" + botConfig.getInt("ws.port"));
        generalConfig.put("bot.type", ListUtil.of("onebot11-ws"));
        migration = new ConfigMigration(plugin, botConfig, generalConfig);
        migration.alternative("access_token", "bot.onebot11-ws.access-token");
        migration.alternative("check_interval", "bot.options.check-interval");
        migration.alternative("groups", "bot.options.enable-groups");
        migration.save();
        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.success"));
    }
    
    public static void migrateMessage(NeoBot plugin, CommandSender sender) throws Throwable {
        JSONObject configObj = MigrationUtil.parseOriginConfig(plugin, "config");
        if (configObj == null) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.no-config")
                    .replace("${config}", "config.yml"));
        }
        // step 1: config.yml -> messages.json
        Config oldConfig = new Config(configObj);
        EnhancedConfig messageConfig = plugin.getMessageConfig();
        ConfigMigration migration = new ConfigMigration(plugin, oldConfig, messageConfig);
        migration.alternative("notify.server_status.start", "game-notice.on-server-start");
        migration.alternative("notify.server_status.stop", "game-notice.on-server-stop");
        migration.alternative("notify.player_status.join", "game-notice.on-join");
        migration.alternative("notify.player_status.leave", "game-notice.on-quit");
        migration.alternative("notify.player_death.message", "game-notice.on-die");
        migration.save();
        // step 2: messages.yml -> messages.json
        JSONObject messageConfigObj = MigrationUtil.parseOriginConfig(plugin, "messages");
        if (messageConfigObj == null) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.no-config")
                    .replace("${config}", "messages.yml"));
        }
        Config oldMessageConfig = new Config(messageConfigObj);
        migration = new ConfigMigration(plugin, oldMessageConfig, messageConfig);
        migration.alternative("qq.chat_from_game", "chat-forward.to-qq");
        migration.alternative("game.chat_from_qq", "chat-forward.to-game");
        migration.alternative("qq.whitelist.bind_successful", "whitelist.bind-success");
        migration.alternative("qq.whitelist.unbind_successful", "whitelist.unbind-success");
        migration.alternative("qq.whitelist.invalid_bind", "whitelist.player-didnt-bind");
        migration.alternative("qq.whitelist.already_exist", "whitelist.player-already-bind");
        migration.alternative("qq.whitelist.already_bind", "whitelist.bind-too-many");
        migration.alternative("game.not_bind", "whitelist.unbind");
        migration.save();
    }

    public void alternative(String key) {
        if (oldConfig.has(key.replace("-", "_"))) {
            newConfig.setOption(key, oldConfig.get(key.replace("-", "_")));
        }
    }

    public void alternative(String originKey, String newKey) {
        if (oldConfig.has(originKey)) {
            newConfig.setOption(newKey, oldConfig.get(originKey));
        }
    }

    public void save() {
        newConfig.flush(plugin);
    }
}
