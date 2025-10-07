package dev.neovoxel.neobot.script;

import dev.neovoxel.jarflow.JarFlow;
import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.game.GameEventListener;
import org.graalvm.polyglot.Context;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public interface ScriptProvider {
    boolean getScriptSystemLoaded();

    void setScriptSystemLoaded(boolean loaded);

    void addLoadedScript(Script script);

    void isScriptLoaded(Script script);

    int pluginSchemaVersion = 1;

    Context context = Context.create();

    default void loadLibrary() throws Throwable {
        Dependency dependency = Dependency.builder()
                .groupId("org.graalvm.js")
                .artifactId("js")
                .version("22.0.0.2")
                .build();
        JarFlow.loadDependency(dependency);
    }

    default void loadScript(NeoBot plugin) throws Throwable {
        plugin.getLogger().info("Loading libraries...");
        loadLibrary();
        plugin.getLogger().info("Loading scripts...");
        context.getBindings("js").putMember("qq", plugin.getBotListener());
        context.getBindings("js").putMember("plugin", plugin);
        context.getBindings("js").putMember("game", plugin.getGameEventListener());
        File scriptPath = new File(plugin.getDataFolder(), "scripts");
        if (!scriptPath.exists()) {
            scriptPath.mkdirs();
        }
        for (File file : scriptPath.listFiles()) {
            if (!file.isDirectory()) {
                continue;
            }
            File manifest = new File(file, "manifest.json");
            if (!manifest.exists()) {
                continue;
            }
            JSONObject jsonObject = new JSONObject(new InputStreamReader(new FileInputStream(manifest), StandardCharsets.UTF_8));
            int schemaVersion = jsonObject.getInt("schema_version");
            if (schemaVersion > pluginSchemaVersion) {
                plugin.getLogger().warn("The script {} is using a newer schema version than the current one. Please update the plugin.", file.getName());
                continue;
            }
            Script script = new Script(
                    schemaVersion,
                    jsonObject.getString("name"),
                    jsonObject.getString("author"),
                    jsonObject.getString("version"),
                    jsonObject.getString("entrypoint")
            );
            if (jsonObject.has("description")) {
                script.setDescription(jsonObject.getString("description"));
            }
            File jsFile = new File(file, jsonObject.getString("entrypoint"));
            if (!jsFile.exists()) {
                plugin.getLogger().warn("The script {} does not have a entrypoint file {}.", script.getName(), jsFile.getName());
                continue;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsFile), StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            context.eval("js", builder.toString());
            addLoadedScript(script);
            plugin.getLogger().info("Loaded script {}.", script.getName());
        }
    }

    default void downloadDefault() {

    }
}
