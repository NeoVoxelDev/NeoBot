package dev.neovoxel.neobot.script;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import dev.neovoxel.neobot.NeoBot;
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
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class ScriptProvider {
    private final ScriptDispatcher businessDispatcher = new ScriptDispatcher(2000L);
    private volatile BusinessActionExecutor actionExecutor;
    @Getter
    @Setter
    private boolean scriptSystemLoaded = false;

    private final List<ValueWithScript> placeholderParsers = new ArrayList<>();
    
    private final Map<String, ValueWithScript> methods = new HashMap<>();

    private final NeoBot plugin;

    public ScriptProvider(NeoBot plugin) {
        this.plugin = plugin;
        this.engine = GraalScriptRuntime.createEngine();
        initializeHostAccess();
    }

    /** Registers a Java-side bridge for script business dispatch. */
    public void registerBusinessHandler(ScriptDispatcher.Handler handler) { businessDispatcher.register(handler); }
    @HostAccess.Export
    public void registerBusinessScript(final String event, final Value callback) {
        if (callback == null || !callback.canExecute()) return;
        businessDispatcher.register((name, context) -> {
            if (!event.equals(name)) return ScriptDispatchResult.unhandled();
            Value value = callback.execute(context);
            if (value == null || value.isNull()) return ScriptDispatchResult.unhandled();
            if (!value.hasMembers()) return value.asBoolean() ? new ScriptDispatchResult(true, false, null, null, null) : ScriptDispatchResult.unhandled();
            boolean handled = value.hasMember("handled") && value.getMember("handled").asBoolean();
            boolean cancelled = value.hasMember("cancelled") && value.getMember("cancelled").asBoolean();
            String content = value.hasMember("content") && !value.getMember("content").isNull() ? value.getMember("content").asString() : null;
            java.util.List<String> targets = new java.util.ArrayList<>();
            java.util.List<String> actions = new java.util.ArrayList<>();
            if (value.hasMember("targets") && value.getMember("targets").hasArrayElements()) {
                for (long i = 0; i < value.getMember("targets").getArraySize(); i++) targets.add(value.getMember("targets").getArrayElement(i).asString());
            }
            if (value.hasMember("actions") && value.getMember("actions").hasArrayElements()) {
                for (long i = 0; i < value.getMember("actions").getArraySize(); i++) actions.add(value.getMember("actions").getArrayElement(i).asString());
            }
            java.util.Map<String, String> contentByAction = new java.util.HashMap<>();
            if (value.hasMember("contentByAction") && value.getMember("contentByAction").hasMembers()) {
                Value byAction = value.getMember("contentByAction");
                for (String key : byAction.getMemberKeys()) {
                    Value entry = byAction.getMember(key);
                    if (entry != null && !entry.isNull()) contentByAction.put(key, entry.asString());
                }
            }
            return new ScriptDispatchResult(handled, cancelled, content, targets, actions, contentByAction);
        });
    }
    public ScriptDispatchResult dispatchBusiness(String event, Object context) { return businessDispatcher.dispatch(event, context); }
    public void setBusinessActionExecutor(BusinessActionExecutor executor) { this.actionExecutor = executor; }
    public void executeBusinessActions(ScriptDispatchResult result) {
        if (result == null || result.isCancelled() || actionExecutor == null) return;
        if (result.getContentByAction().isEmpty()) {
            actionExecutor.executeAll(result.getActions(), result.getTargets(), result.getContent());
            return;
        }
        for (String action : result.getActions()) {
            actionExecutor.executeAll(java.util.Collections.singletonList(action), result.getTargets(), result.getContentFor(action));
        }
    }

    int pluginSchemaVersion = 1;

    private final Map<Script, Context> contexts = new HashMap<>();

    private final Engine engine;

    private static final List<Class<?>> exposed = new ArrayList<>();

    private static HostAccess hostAccess;

    private static synchronized void initializeHostAccess() {
        if (hostAccess != null) return;
        GraalScriptRuntime.withPluginClassLoader(() -> {
        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .acceptPackages(
                        "dev.neovoxel.nsapi",
                        "dev.neovoxel.nbapi.action",
                        "dev.neovoxel.nbapi.event",
                        "dev.neovoxel.nbapi.discord",
                        "dev.neovoxel.nbapi.util")
                .scan()) {
            exposed.addAll(scan.getAllClasses().loadClasses());
        } catch (RuntimeException e) {
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
            return null;
        });
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
        installDefaultScript(plugin, scriptPath);
        Set<Script> unsortedScripts = new HashSet<>();
        for (File file : scriptPath.listFiles()) {
            if (!file.isDirectory()) {
                continue;
            }
            File manifest = new File(file, "manifest.json");
            if (!manifest.exists()) {
                continue;
            }
            JSONObject jsonObject = new JSONObject(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
            int schemaVersion = jsonObject.getInt("schema_version");
            if (schemaVersion > pluginSchemaVersion) {
                plugin.getNeoLogger().warn("The script " + file.getName() + " is using a newer schema version than the current one. Please update the plugin.");
                continue;
            }
            if (jsonObject.has("required-all-access") && jsonObject.getBoolean("required-all-access")) {
                if (!plugin.getGeneralConfig().getBoolean("js.all-access")) {
                    plugin.getNeoLogger().warn("The script " + file.getName() + " requires all-access, but it is not enabled.");
                    continue;
                }
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

    private void installDefaultScript(NeoBot plugin, File scriptPath) {
        File target = new File(scriptPath, "default");
        File manifest = new File(target, "manifest.json");
        if (manifest.exists()) return;
        InputStream manifestStream = getClass().getClassLoader().getResourceAsStream("default-script/manifest.json");
        InputStream entryStream = getClass().getClassLoader().getResourceAsStream("default-script/main.js");
        if (manifestStream == null || entryStream == null) {
            plugin.getNeoLogger().error("Default business script resources are missing; business events will be rejected");
            return;
        }
        try {
            target.mkdirs();
            copyResource(manifestStream, new File(target, "manifest.json"));
            copyResource(entryStream, new File(target, "main.js"));
            plugin.getNeoLogger().info("Installed default business script");
        } catch (IOException error) {
            plugin.getNeoLogger().error("Failed to install default business script; business events will be rejected", error);
        }
    }

    private static void copyResource(InputStream input, File output) throws IOException {
        try (InputStream in = input; OutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[4096]; int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
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
            jsonObject = new JSONObject(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
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
        if (jsonObject.has("required-all-access") && jsonObject.getBoolean("required-all-access")) {
            if (!plugin.getGeneralConfig().getBoolean("js.all-access")) {
                return plugin.getMessageConfig().getMessage("internal.script.load.all-access");
            }
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
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ScriptProvider.class.getClassLoader());
        try {
            loadScriptWithPluginClassLoader(plugin, script);
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private void loadScriptWithPluginClassLoader(NeoBot plugin, Script script) throws Throwable {
        if (!script.getEntrypoint().exists()) {
            plugin.getNeoLogger().warn("The script " + script.getId() + " is missing the entrypoint file.");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(script.getEntrypoint()), StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        Context.Builder contextBuilder = Context.newBuilder("js")
                .allowIO(true)
                .allowCreateThread(true)
                .logHandler(new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        plugin.getNeoLogger().info("[[JS]] " + record.getMessage());
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() throws SecurityException {}
                })
                .engine(engine);
        if (plugin.getGeneralConfig().getBoolean("js.all-access")) {
            contextBuilder.allowAllAccess(true);
        } else {
            contextBuilder.allowHostAccess(hostAccess);
        }
        GraalScriptRuntime.requireJavaScript(engine);
        Context context = GraalScriptRuntime.buildContext(contextBuilder);
        String uuid = UUID.randomUUID().toString();
        context.getBindings("js").putMember("qq", plugin.getBotProvider().getBotListener());
        context.getBindings("js").putMember("discord", plugin.getBotProvider().getDiscordBotListener());
        context.getBindings("js").putMember("plugin", plugin);
        context.getBindings("js").putMember("gameEvent", plugin.getGameEventListener());
        context.getBindings("js").putMember("gameCommand", plugin.getCommandProvider());
        context.getBindings("js").putMember("messageConfig", plugin.getMessageConfig());
        context.getBindings("js").putMember("generalConfig", plugin.getScriptConfig());
        context.getBindings("js").putMember("http", new HttpBuilder.Factory());
        context.getBindings("js").putMember("ws", new ExternalWSUtil());
        context.getBindings("js").putMember("scriptManager", this);
        context.getBindings("js").putMember("business", actionExecutor);
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
                plugin.getBotProvider().getDiscordBotListener().clearUuidContext(uuid);
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
        businessDispatcher.clearHandlers();
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
