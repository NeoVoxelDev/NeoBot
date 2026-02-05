package dev.neovoxel.neobot.misc;

import dev.neovoxel.neobot.NeoBot;
import lombok.Getter;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EventListener {
    @Getter
    private final NeoBot plugin;

    private final Map<Value, String> map = new LinkedHashMap<>();

    public EventListener(NeoBot plugin) {
        this.plugin = plugin;
    }
    @HostAccess.Export
    public void register(String eventName, Value method) {
        if (method.canExecute()) map.put(method, eventName);
    }

    public void reset() {
        map.clear();
    }

    protected void fireEvent(String eventName, Object... args) {
        for (Map.Entry<Value, String> entry : map.entrySet()) {
            if (entry.getValue().equals(eventName)) {
                entry.getKey().execute(args);
            }
        }
    }

    public void clearUuidContext(String uuid) {
        List<Value> toRemove = new ArrayList<>();
        for (Map.Entry<Value, String> entry : map.entrySet()) {
            if (entry.getKey().getContext().getBindings("js").getMember("__uuid__").asString().equals(uuid)) {
                toRemove.add(entry.getKey());
            }
        }
        for (Value value : toRemove) {
            map.remove(value);
        }
    }
}
