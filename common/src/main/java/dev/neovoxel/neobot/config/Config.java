package dev.neovoxel.neobot.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.graalvm.polyglot.HostAccess;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class Config {
    protected JSONObject jsonObject;

    @HostAccess.Export
    public String getString(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getString(nodes[nodes.length - 1]);
    }

    @HostAccess.Export
    public int getInt(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getInt(nodes[nodes.length - 1]);
    }

    @HostAccess.Export
    public Object get(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.get(nodes[nodes.length - 1]);
    }

    @HostAccess.Export
    public double getDouble(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getDouble(nodes[nodes.length - 1]);
    }

    @HostAccess.Export
    public boolean getBoolean(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getBoolean(nodes[nodes.length - 1]);
    }

    public JSONObject getJSONObject(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getJSONObject(nodes[nodes.length - 1]);
    }

    public JSONArray getJSONArray(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getJSONArray(nodes[nodes.length - 1]);
    }

    @HostAccess.Export
    public Config[] getArray(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        JSONArray array = newObj.getJSONArray(nodes[nodes.length - 1]);
        Config[] configs = new Config[array.length()];
        for (int i = 0; i < array.length(); i++) {
            configs[i] = new Config(array.getJSONObject(i));
        }
        return configs;
    }

    @HostAccess.Export
    public List<String> getStringArray(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        JSONArray array = newObj.getJSONArray(nodes[nodes.length - 1]);
        List<String> strings = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            strings.add(String.valueOf(array.get(i)));
        }
        return strings;
    }

    @HostAccess.Export
    public List<Long> getNumberArray(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        JSONArray array = newObj.getJSONArray(nodes[nodes.length - 1]);
        List<Long> numbers = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            numbers.add(array.getLong(i));
        }
        return numbers;
    }

    @HostAccess.Export
    public Config getObject(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return new Config(newObj.getJSONObject(nodes[nodes.length - 1]));
    }

    @HostAccess.Export
    public String[] getKeys() {
        return jsonObject.keySet().toArray(new String[0]);
    }

    @HostAccess.Export
    public boolean has(String node) {
        try {
            JSONObject newObj = jsonObject;
            String[] nodes = node.split("\\.");
            for (int i = 0; i < nodes.length - 1; i++) {
                newObj = newObj.getJSONObject(nodes[i]);
            }
            return newObj.has(nodes[nodes.length - 1]);
        } catch (Exception ignored) {
            return false;
        }
    }

    @HostAccess.Export
    public void put(String node, Object value) {
        if (node == null || node.isEmpty()) {
            return;
        }
        String[] nodes = node.split("\\.");
        if (nodes.length == 0) {
            return;
        }
        JSONObject currentJson = jsonObject;
        for (int i = 0; i < nodes.length - 1; i++) {
            String currentNode = nodes[i];
            if (currentNode.isEmpty()) {
                return;
            }
            if (!currentJson.has(currentNode) || !(currentJson.get(currentNode) instanceof JSONObject)) {
                currentJson.put(currentNode, new JSONObject());
            }
            currentJson = currentJson.getJSONObject(currentNode);
        }
        String lastNode = nodes[nodes.length - 1];
        if (!lastNode.isEmpty()) {
            currentJson.put(lastNode, convertPolyglotValue(value));
        }
    }

    public void set(String node, Object value) {
        if (node == null || node.isEmpty()) {
            return;
        }
        String[] nodes = node.split("\\.");
        if (nodes.length == 0) {
            return;
        }
        JSONObject currentJson = jsonObject;
        for (int i = 0; i < nodes.length - 1; i++) {
            String currentNode = nodes[i];
            if (currentNode.isEmpty()) {
                return;
            }
            if (!currentJson.has(currentNode) || !(currentJson.get(currentNode) instanceof JSONObject)) {
                currentJson.put(currentNode, new JSONObject());
            }
            currentJson = currentJson.getJSONObject(currentNode);
        }
        String lastNode = nodes[nodes.length - 1];
        currentJson.put(lastNode, convertPolyglotValue(value));
    }

    protected Object convertPolyglotValue(Object value) {

        // GraalJS >= 23 : PolyglotList for JS arrays
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            JSONArray arr = new JSONArray();
            for (Object o : list) {
                arr.put(convertPolyglotValue(o));
            }
            return arr;
        }

        // GraalJS 22.* : JS Array becomes PolyglotMap
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (isArrayLike(map)) {
                JSONArray arr = new JSONArray();
                int len = getArrayLikeLength(map);
                for (int i = 0; i < len; i++) {
                    Object v = map.get(String.valueOf(i));
                    arr.put(convertPolyglotValue(v));
                }
                return arr;
            }

            // normal JS object
            JSONObject obj = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                obj.put(entry.getKey().toString(), convertPolyglotValue(entry.getValue()));
            }
            return obj;
        }

        return value;
    }

    private boolean isArrayLike(Map<?, ?> map) {
        if (!map.containsKey("length")) return false;

        Object lenObj = map.get("length");
        if (!(lenObj instanceof Number)) return false;

        int length = ((Number) lenObj).intValue();

        // Check keys 0..length-1 all exist
        for (int i = 0; i < length; i++) {
            if (!map.containsKey(String.valueOf(i))) return false;
        }

        // No unexpected non-numeric keys
        for (Object key : map.keySet()) {
            String k = key.toString();
            if (k.equals("length")) continue;
            if (!k.matches("\\d+")) return false;
        }

        return true;
    }

    private int getArrayLikeLength(Map<?, ?> map) {
        Object lenObj = map.get("length");
        if (lenObj instanceof Number) return ((Number) lenObj).intValue();
        return 0;
    }
}
