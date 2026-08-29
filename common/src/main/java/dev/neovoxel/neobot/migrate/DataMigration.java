package dev.neovoxel.neobot.migrate;

import com.zaxxer.hikari.HikariConfig;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.CommandSender;
import dev.neovoxel.neobot.config.Config;
import dev.neovoxel.neobot.util.MigrationUtil;
import dev.neovoxel.nsapi.DatabaseStorage;
import dev.neovoxel.nsapi.entity.Result;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataMigration {
    public static void migrate(NeoBot plugin, CommandSender sender) throws Throwable {
        JSONObject config = MigrationUtil.parseOriginConfig(plugin, "config");
        if (config == null) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.config-not-found")
                    .replace("${config}", "config.yml"));
        }
        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.start-migrating-data"));
        // step 1: read old data
        Config oldConfig = new Config(config);
        Map<String, List<String>> data;
        if (oldConfig.getString("storage.type").equalsIgnoreCase("file")) {
            data = parseOriginFromFile(plugin.getDataFolder().toPath().resolve("..").resolve("AQQBot").resolve("data.yml").toFile());
        } else if (oldConfig.getString("storage.type").equalsIgnoreCase("sqlite")) {
            String database = oldConfig.getString("storage.sqlite.file");
            data = parseOriginFromSqlite(plugin.getDataFolder().toPath().resolve("..").resolve("AQQBot").resolve(database).toFile());
        } else {
            data = parseOriginFromMysql(oldConfig.getString("storage.host"), oldConfig.getInt("storage.port"),
                    oldConfig.getString("storage.username"), oldConfig.getString("storage.password"),
                    oldConfig.getString("storage.database"));
        }
        // step 2: convert
        plugin.getStorageProvider().getStorage().table("neobot_whitelist").create()
                .column("qq", "BIGINT", "PRIMARY KEY")
                .column("players", "TEXT")
                .execute();
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            String qq = entry.getKey();
            List<String> names = entry.getValue();
            JSONArray jsonArray = new JSONArray();
            for (String name : names) {
                jsonArray.put(name);
            }
            plugin.getStorageProvider().getStorage().table("neobot_whitelist")
                    .insert()
                    .column("qq", Long.valueOf(qq))
                    .column("players", "'" + jsonArray + "'")
                    .execute();
        }
        sender.sendMessage(plugin.getMessageConfig().getMessage("internal.migration.success"));
    }

    public static Map<String, List<String>> parseOriginFromFile(File file) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.load(new FileInputStream(file));
        JSONObject jsonObject = new JSONObject(map);
        Config config = new Config(jsonObject);
        Map<String, List<String>> data = new HashMap<>();
        for (String key : config.getKeys()) {
            String s = config.get(key).toString();
            List<String> names = Arrays.stream(s.split(", ")).collect(Collectors.toList());
            data.put(key, names);
        }
        return data;
    }

    public static Map<String, List<String>> parseOriginFromSqlite(File file) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        DatabaseStorage storage = new DatabaseStorage(hikariConfig);
        return parseFromDatabase(storage);
    }

    public static Map<String, List<String>> parseOriginFromMysql(String host, int port, String username, String password, String database) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        DatabaseStorage storage = new DatabaseStorage(hikariConfig);
        return parseFromDatabase(storage);
    }

    public static Map<String, List<String>> parseFromDatabase(DatabaseStorage storage) {
        Result result = storage
                .table("account_data")
                .select("name", "userId")
                .execute();
        Map<String, List<String>> data = new HashMap<>();
        result.map().forEach(row -> {
            List<String> names = Arrays.stream(row.getString("name").split(", ")).collect(Collectors.toList());
            String userId = String.valueOf(row.getLong("userId"));
            data.put(userId, names);
        });
        return data;
    }
}
