package dev.neovoxel.neobot.config;

import lombok.Data;
import org.json.JSONArray;
import org.json.JSONObject;

@Data
public class Config {
    private final JSONObject jsonObject;

    public String getString(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getString(nodes[nodes.length - 1]);
    }

    public int getInt(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getInt(nodes[nodes.length - 1]);
    }

    public double getDouble(String node) {
        JSONObject newObj = jsonObject;
        String[] nodes = node.split("\\.");
        for (int i = 0; i < nodes.length - 1; i++) {
            newObj = newObj.getJSONObject(nodes[i]);
        }
        return newObj.getDouble(nodes[nodes.length - 1]);
    }

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
}
