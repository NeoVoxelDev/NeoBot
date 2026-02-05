package dev.neovoxel.neobot.script;

import dev.neovoxel.jarflow.JarFlow;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.misc.EventListener;
import dev.neovoxel.neobot.util.ValueWithScript;
import dev.neovoxel.neobot.util.http.HttpBuilder;
import dev.neovoxel.neobot.util.ws.ExternalWSUtil;
import lombok.Getter;
import lombok.Setter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class ScriptProvider {
    @Getter
    @Setter
    private boolean scriptSystemLoaded = false;

    private final List<ValueWithScript> placeholderParsers = new ArrayList<>();
    
    private final Map<String, ValueWithScript> methods = new HashMap<>();

    private final NeoBot plugin;

    public ScriptProvider(NeoBot plugin) {
        this.plugin = plugin;
    }

    int pluginSchemaVersion = 1;

    private final Map<Script, Context> contexts = new HashMap<>();

    private static final Engine engine;

    private static final List<Class<?>> exposed = new ArrayList<>();

    private static final HostAccess hostAccess;

    static {
        OutputStream stream = null;
        try {
            File jsLog = new File("engine.log");
            if (!jsLog.exists()) jsLog.createNewFile();
            stream = new FileOutputStream(jsLog);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Engine.Builder builder = Engine.newBuilder()
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false");
        if (stream != null) {
            builder.logHandler(stream);
        }
        engine = builder.build();
        try {
            exposed.addAll(JarFlow.searchClasses("dev.neovoxel.nsapi"));
            exposed.addAll(JarFlow.searchClasses("dev.neovoxel.nbapi.action"));
            exposed.addAll(JarFlow.searchClasses("dev.neovoxel.nbapi.event"));
            exposed.addAll(JarFlow.searchClasses("dev.neovoxel.nbapi.util"));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        HostAccess.Builder builder1 = HostAccess.newBuilder(HostAccess.EXPLICIT);
        builder1.allowAccessAnnotatedBy(HostAccess.Export.class);
        builder1.allowListAccess(true);
        builder1.allowArrayAccess(true);
        exposed.add(Object.class);
        exposed.add(Enum.class);
        exposed.add(JSONObject.class);
        exposed.add(JSONArray.class);
        for (Class<?> clazz : exposed) {
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals("wait") || method.getName().equals("notify") || method.getName().equals("notifyAll")) {
                    continue;
                }
                builder1.allowAccess(method);
            }
        }
        hostAccess = builder1.build();
    }

    public boolean isScriptLoaded(Script script) {
        return contexts.containsKey(script);
    }

    public void loadScript(NeoBot plugin) throws Throwable {
        plugin.getNeoLogger().info("Loading scripts...");
        File scriptPath = new File(plugin.getDataFolder(), "scripts");
        if (!scriptPath.exists()) {
            scriptPath.mkdirs();
        }
        Set<Script> unsortedScripts = new HashSet<>();
        for (File file : scriptPath.listFiles()) {
            if (!file.isDirectory()) {
                continue;
            }
            File manifest = new File(file, "manifest.json");
            if (!manifest.exists()) {
                continue;
            }
            JSONObject jsonObject = new JSONObject(new String(Files.readAllBytes(manifest.toPath())));
            int schemaVersion = jsonObject.getInt("schema_version");
            if (schemaVersion > pluginSchemaVersion) {
                plugin.getNeoLogger().warn("The script " + file.getName() + " is using a newer schema version than the current one. Please update the plugin.");
                continue;
            }
            Script script = new Script(
                    schemaVersion,
                    jsonObject.getString("id"),
                    jsonObject.getString("name"),
                    jsonObject.getString("author"),
                    jsonObject.getString("version"),
                    new File(file, jsonObject.getString("entrypoint"))
            );
            if (jsonObject.has("description")) {
                script.setDescription(jsonObject.getString("description"));
            }
            if (jsonObject.has("loadbefore")) {
                for (Object object : jsonObject.getJSONArray("loadbefore")) {
                    script.getLoadbefore().add(object.toString());
                }
            }
            if (jsonObject.has("loadafter")) {
                for (Object object : jsonObject.getJSONArray("loadafter")) {
                    script.getLoadafter().add(object.toString());
                }
            }
            if (jsonObject.has("depends")) {
                for (Object object : jsonObject.getJSONArray("depends")) {
                    script.getDepends().add(object.toString());
                }
            }
            unsortedScripts.add(script);
        }
        List<Script> sortedScripts = Script.sortScripts(unsortedScripts);
        List<Script> checkedScripts = new ArrayList<>();
        for (Script script : sortedScripts) {
            if (!script.checkDepends(sortedScripts)) {
                plugin.getNeoLogger().warn("The script " + script.getId() + " is missing dependencies, it needs: " +
                        Arrays.toString(script.getDepends().toArray()));
            } else {
                checkedScripts.add(script);
            }
        }
        for (Script script : checkedScripts) {
            loadScript(plugin, script);
        }
        plugin.getScriptConfig().flush(plugin);
        scriptSystemLoaded = true;
    }
    
    public String loadScript(NeoBot plugin, String dir) {
        File scriptPath = new File(plugin.getDataFolder(), "scripts");
        if (!scriptPath.exists()) {
            plugin.getMessageConfig().getMessage("internal.script.load.not-found");
        }
        File file = new File(scriptPath, dir);
        if (!file.exists()) {
            plugin.getMessageConfig().getMessage("internal.script.load.not-found");
        }
        if (!file.isDirectory()) {
            plugin.getMessageConfig().getMessage("internal.script.load.not-found");
        }
        File manifest = new File(file, "manifest.json");
        if (!manifest.exists()) {
            plugin.getMessageConfig().getMessage("internal.script.load.manifest-not-found");
        }
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(new String(Files.readAllBytes(manifest.toPath())));
        } catch (IOException e) {
            return plugin.getMessageConfig().getMessage("internal.script.load.error")
                    .replace("${error}", e.getMessage());
        }
        int schemaVersion = jsonObject.getInt("schema_version");
        if (schemaVersion > pluginSchemaVersion) {
            plugin.getMessageConfig().getMessage("internal.script.load.too-new")
                    .replace("supported", String.valueOf(pluginSchemaVersion))
                    .replace("current", String.valueOf(schemaVersion));
        }
        Script script = new Script(
                schemaVersion,
                jsonObject.getString("id"),
                jsonObject.getString("name"),
                jsonObject.getString("author"),
                jsonObject.getString("version"),
                new File(file, jsonObject.getString("entrypoint"))
        );
        if (jsonObject.has("description")) {
            script.setDescription(jsonObject.getString("description"));
        }
        if (jsonObject.has("loadbefore")) {
            for (Object object : jsonObject.getJSONArray("loadbefore")) {
                script.getLoadbefore().add(object.toString());
            }
        }
        if (jsonObject.has("loadafter")) {
            for (Object object : jsonObject.getJSONArray("loadafter")) {
                script.getLoadafter().add(object.toString());
            }
        }
        if (jsonObject.has("depends")) {
            for (Object object : jsonObject.getJSONArray("depends")) {
                script.getDepends().add(object.toString());
            }
        }
        try {
            loadScript(plugin, script);
            plugin.getScriptConfig().flush(plugin);
        } catch (Throwable e) {
            return plugin.getMessageConfig().getMessage("internal.script.load.error")
                    .replace("error", e.getMessage());
        }
        return plugin.getMessageConfig().getMessage("internal.script.load.success")
                .replace("${id}",script.getId())
                .replace("${name}", script.getName())
                .replace("${author}", script.getAuthor())
                .replace("${version}", script.getVersion());
    }

    public void loadScript(NeoBot plugin, Script script) throws Throwable {
        if (!script.getEntrypoint().exists()) {
            plugin.getNeoLogger().warn("The script " + script.getId() + " is missing the entrypoint file.");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(script.getEntrypoint()), StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        Context context = Context.newBuilder("js")
                .allowIO(true)
                .allowHostAccess(hostAccess)
                .allowCreateThread(true)
                .engine(engine)
                .build();
        String uuid = UUID.randomUUID().toString();
        context.getBindings("js").putMember("qq", plugin.getBotProvider().getBotListener());
        context.getBindings("js").putMember("plugin", plugin);
        context.getBindings("js").putMember("gameEvent", plugin.getGameEventListener());
        for(Map.Entry<String, EventListener> entry : plugin.getListenerMap().entrySet()){
            context.getBindings("js").putMember(entry.getKey(), entry.getValue());
//            System.out.println(entry.getKey());
        }
        context.getBindings("js").putMember("gameCommand", plugin.getCommandProvider());
        context.getBindings("js").putMember("messageConfig", plugin.getMessageConfig());
        context.getBindings("js").putMember("generalConfig", plugin.getScriptConfig());
        context.getBindings("js").putMember("http", new HttpBuilder.Factory());
        context.getBindings("js").putMember("ws", new ExternalWSUtil());
        context.getBindings("js").putMember("scriptManager", this);
        context.getBindings("js").putMember("__uuid__", uuid);
        contexts.put(script, context);
        context.eval("js", builder.toString());
        plugin.getNeoLogger().info("Loaded script " + script.getId());
    }

    public boolean unloadScript(String id) {
        for (Map.Entry<Script, Context> entry : contexts.entrySet()) {
            if (entry.getKey().getId().equalsIgnoreCase(id)) {
                String uuid = entry.getValue().getBindings("js").getMember("__uuid__").asString();
                for (Map.Entry<Script, Context> contextEntry : contexts.entrySet()) {
                    if (contextEntry.getKey().getDepends().contains(id)) {
                        return false;
                    }
                }
                List<String> toRemove = new ArrayList<>();
                for (Map.Entry<String, ValueWithScript> method : methods.entrySet()) {
                    if (method.getValue().getScript().getId().equalsIgnoreCase(id)) {
                        toRemove.add(method.getKey());
                    }
                }
                toRemove.forEach(methods::remove);
                placeholderParsers.removeIf(method -> method.getScript().getId().equalsIgnoreCase(id));
                plugin.getBotProvider().getBotListener().clearUuidContext(uuid);
                plugin.getGameEventListener().clearUuidContext(uuid);
                plugin.getCommandProvider().clearUuidContext(uuid);
                plugin.getScriptScheduler().clearUuidContext(uuid);
                entry.getValue().close();
                return true;
            }
        }
        return false;
    }

    public void unloadScript() {
        contexts.values().forEach(Context::close);
        contexts.clear();
        placeholderParsers.clear();
        methods.clear();
    }

    public void downloadDefault() {

    }

    @HostAccess.Export
    public void loadParser(Value value) {
        if (!value.canExecute()) {
            return;
        }
        for (Map.Entry<Script, Context> entry : contexts.entrySet()) {
            if (entry.getValue().getBindings("js").getMember("__uuid__").asString().equals(
                    value.getContext().getBindings("js").getMember("__uuid__").asString())) {
                placeholderParsers.add(new ValueWithScript(value, entry.getKey()));
            }
        }
    }

    @HostAccess.Export
    public String parse(String content) {
        for (ValueWithScript value : placeholderParsers) {
            content = value.getValue().execute(content).asString();
        }
        return content;
    }

    @HostAccess.Export
    public boolean isScriptLoaded(String id) {
        return contexts.keySet().stream().anyMatch(script -> script.getId().equals(id));
    }

    @HostAccess.Export
    public void addJsMethod(String name, Value value) {
        for (Map.Entry<Script, Context> entry : contexts.entrySet()) {
            if (Objects.equals(entry.getValue().getBindings("js").getMember("__uuid__").asString(),
                    value.getContext().getBindings("js").getMember("__uuid__").asString())) {
                methods.put(name, new ValueWithScript(value, entry.getKey()));
            }
        }
    }

    @HostAccess.Export
    public boolean hasJsMethod(String name) {
        return methods.containsKey(name);
    }

    @HostAccess.Export
    public Value callJsMethod(String name, Object... args) {
        return methods.get(name).getValue().execute(args);
    }

    public Script getScriptInfo(String id) {
        return contexts.keySet().stream().filter(script -> script.getId().equals(id)).findFirst().orElse(null);
    }

    public Context getScriptContext(String name) {
        return contexts.get(getScriptInfo(name));
    }

    public Set<Script> getLoadedScripts() {
        return contexts.keySet();
    }
}
