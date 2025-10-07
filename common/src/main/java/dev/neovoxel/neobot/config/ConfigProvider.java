package dev.neovoxel.neobot.config;

import dev.neovoxel.neobot.NeoBot;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;

public interface ConfigProvider {
    default void loadConfig(NeoBot plugin) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        loadGeneralConfig(plugin);
    }

    void setGeneralConfig(Config config);

    Config getGeneralConfig();

    default void loadGeneralConfig(NeoBot plugin) {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.json");
            if (!configFile.exists()) saveResource(plugin.getDataFolder(), "config.json");
            setGeneralConfig(new Config(new JSONObject(new String(Files.readAllBytes(configFile.toPath())))));
        } catch (Exception e) {
            plugin.getLogger().error("Failed to release the general config file", e);
        }
    }

    default void saveResource(File parent, String fileName) throws IOException {
        File file = new File(parent, fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("config.json")));
        String line;
        StringBuilder builder = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        if (!file.exists()) file.createNewFile();
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file));
        writer.write(builder.toString());
        writer.close();
    }
}
