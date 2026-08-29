package dev.neovoxel.neobot.config;

import dev.neovoxel.nbapi.util.Role;
import dev.neovoxel.neobot.NeoBot;
import org.graalvm.polyglot.HostAccess;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EnhancedConfig extends Config {
    private final File file;
    private final Map<String, Object> needFlushOptions = new HashMap<>();
    private final Set<String> missingAtLoad = new HashSet<>();

    public EnhancedConfig(File file, JSONObject jsonObject) {
        super(new JSONObject(jsonObject.toString().replace("&", "§")));
        this.file = file;
    }

    @HostAccess.Export
    public String getMessage(String node) {
        return (super.getString("prefix") + super.getString(node));
    }

    public void setOption(String node, Object value) {
        super.put(node, value);
        needFlushOptions.put(node, convertPolyglotValue(value));
    }

    @HostAccess.Export
    public void addOption(String node, Object defaultValue) {
        if (!super.has(node)) {
            missingAtLoad.add(node);
            super.put(node, defaultValue);
            needFlushOptions.put(node, convertPolyglotValue(defaultValue));
        }
    }

    /** True only when this option did not exist in the user file loaded for this session. */
    public boolean wasMissingAtLoad(String node) { return missingAtLoad.contains(node); }

    public void flush(NeoBot plugin) {
        try {
            if (!file.exists()) {
                plugin.saveResource(plugin.getDataFolder(), file.getName());
            }
            String originContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(originContent);
            Config config = new Config(jsonObject);
            for (Map.Entry<String, Object> entry : needFlushOptions.entrySet()) {
                config.set(entry.getKey(), entry.getValue());
            }
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write(config.getJsonObject().toString(4));
            writer.close();
            needFlushOptions.clear();
            super.jsonObject = new JSONObject(config.getJsonObject().toString().replace("&", "§"));
        } catch (IOException e) {
            plugin.getNeoLogger().error("Failed to flush the config", e);
        } catch (RuntimeException e) {
            plugin.getNeoLogger().error("Invalid JSON in " + file.getAbsolutePath() + "; keeping existing in-memory configuration", e);
            throw new IllegalArgumentException("Invalid JSON in " + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }
}
