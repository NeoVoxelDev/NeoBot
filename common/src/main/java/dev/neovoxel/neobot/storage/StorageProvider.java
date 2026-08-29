package dev.neovoxel.neobot.storage;

import com.zaxxer.hikari.HikariConfig;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.nsapi.DatabaseStorage;
import dev.neovoxel.nsapi.util.DatabaseStorageType;
import lombok.Getter;
import lombok.Setter;
import org.graalvm.polyglot.HostAccess;
import org.json.JSONObject;

import java.io.File;

public class StorageProvider {

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private DatabaseStorage storage;

    @Getter(onMethod_ = {@HostAccess.Export})
    @Setter
    private String storageType;

    private final NeoBot plugin;


    public StorageProvider(NeoBot plugin) {
        this.plugin = plugin;
    }

    public void loadStorage() throws Throwable {
        DatabaseStorageType storageType = DatabaseStorageType.valueOf(plugin.getGeneralConfig().getString("storage.type").toUpperCase());
        setStorageType(storageType.name().toLowerCase());
        initDriver(storageType);
        String host = plugin.getGeneralConfig().getString("storage.host");
        int port = plugin.getGeneralConfig().getInt("storage.port");
        String database = plugin.getGeneralConfig().getString("storage.database");
        StringBuilder jdbcUrl = new StringBuilder(generateJdbcUrl(storageType, host, port, database));
        String jdbcUrl2;
        if (storageType == DatabaseStorageType.H2) {
            jdbcUrl2 = "jdbc:" + storageType.name().toLowerCase() + ":file:" + new File(plugin.getDataFolder(), database).getAbsolutePath();
        } else if (storageType == DatabaseStorageType.SQLITE) {
            jdbcUrl2 = "jdbc:" + storageType.name().toLowerCase() + ":" + new File(plugin.getDataFolder(), database + ".db").getAbsolutePath();
        } else {
            int i = 0;
            for (Object option : plugin.getGeneralConfig().getJSONArray("storage.options")) {
                if (i == 0) {
                    jdbcUrl.append("?");
                }
                jdbcUrl.append(option.toString()).append("&");
                i++;
            }
            jdbcUrl2 = jdbcUrl.substring(0, jdbcUrl.length() - 1);
        }
        String username = plugin.getGeneralConfig().getString("storage.username");
        String password = plugin.getGeneralConfig().getString("storage.password");
        JSONObject poolSettings = plugin.getGeneralConfig().getJSONObject("storage.pool-settings");
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(poolSettings.getInt("maximum-pool-size"));
        config.setMinimumIdle(poolSettings.getInt("minimum-idle"));
        config.setMaxLifetime(poolSettings.getInt("maximum-lifetime"));
        config.setKeepaliveTime(poolSettings.getInt("keepalive-time"));
        config.setConnectionTimeout(poolSettings.getInt("connection-timeout"));
        config.setJdbcUrl(jdbcUrl2);
        config.setUsername(username);
        config.setPassword(password);
        DatabaseStorage storage = new DatabaseStorage(config);
        setStorage(storage);
    }

    public void initDriver(DatabaseStorageType type) {
        try {
            if (type == DatabaseStorageType.H2) {
                Class.forName("org.h2.Driver");
            } else if (type == DatabaseStorageType.SQLITE) {
                Class.forName("org.sqlite.JDBC");
            } else if (type == DatabaseStorageType.MYSQL) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else if (type == DatabaseStorageType.MARIADB) {
                Class.forName("org.mariadb.jdbc.Driver");
            } else {
                Class.forName("org.postgresql.Driver");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeStorage() {
        getStorage().save();
    }

    private String generateJdbcUrl(DatabaseStorageType type, String host, int port, String database) {
        return "jdbc:" + type.toString().toLowerCase() + "://" + host + ":" + port + "/" + database;
    }
}
