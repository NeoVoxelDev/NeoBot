package dev.neovoxel.neobot.storage;

import com.zaxxer.hikari.HikariConfig;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.nsapi.DatabaseStorage;
import dev.neovoxel.nsapi.util.DatabaseStorageType;
import org.json.JSONObject;

public interface StorageProvider {

    void setStorage(DatabaseStorage storage);

    DatabaseStorage getStorage();

    String getStorageType();

    default void loadStorage(NeoBot plugin) throws Throwable {
        DatabaseStorageType storageType = DatabaseStorageType.valueOf(plugin.getGeneralConfig().getString("storage.type").toUpperCase());
        plugin.loadStorageLibrary(storageType);
        String host = plugin.getGeneralConfig().getString("storage.host");
        int port = plugin.getGeneralConfig().getInt("storage.port");
        String database = plugin.getGeneralConfig().getString("storage.database");
        StringBuilder jdbcUrl = new StringBuilder(DatabaseStorage.generateJdbcUrl(storageType, host, port, database));
        int i = 0;
        for (Object option : plugin.getGeneralConfig().getJSONArray("storage.options")) {
            if (i == 0) {
                jdbcUrl.append("?");
            }
            jdbcUrl.append(option.toString()).append("&");
            i++;
        }
        String jdbcUrl2 = jdbcUrl.substring(0, jdbcUrl.length() - 1);
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

    default void closeStorage() {
        getStorage().save();
    }
}
