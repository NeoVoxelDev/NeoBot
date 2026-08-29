package dev.neovoxel.neobot.config;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Repairs historical UTF-8 text that was accidentally decoded as GBK. */
public final class MessageEncodingRepair {
    private static final Charset GBK = Charset.forName("GBK");
    private static final String SUSPICIOUS = "\u59dd\u6e6a\u927e\u5cf0\u5f67\u9387\u9418\u8235\u63d2\u4ef6\u91cd\u8f7d\u5b8c\u6210\u7cfb\u7edf\u6b63\u5728\u83b7\u53d6";

    private MessageEncodingRepair() { }

    public static boolean repair(JSONObject object) {
        return repair(object, null);
    }

    public static boolean repair(JSONObject object, JSONObject defaults) {
        boolean changed = false;
        for (String key : object.keySet()) {
            Object value = object.get(key);
            Object defaultValue = defaults != null && defaults.has(key) ? defaults.get(key) : null;
            if (value instanceof JSONObject) changed |= repair((JSONObject) value,
                    defaultValue instanceof JSONObject ? (JSONObject) defaultValue : null);
            else if (value instanceof JSONArray) changed |= repair((JSONArray) value,
                    defaultValue instanceof JSONArray ? (JSONArray) defaultValue : null);
            else if (value instanceof String) {
                String original = (String) value;
                String repaired = isCorrupted(original) && defaultValue instanceof String
                        ? (String) defaultValue : repairString(original);
                if (!repaired.equals(value)) { object.put(key, repaired); changed = true; }
            }
        }
        return changed;
    }

    static String repairString(String value) {
        if (suspiciousScore(value) < 2) return value;
        try {
            ByteBuffer bytes = GBK.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            String repaired = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes).toString();
            return suspiciousScore(repaired) < suspiciousScore(value) ? repaired : value;
        } catch (CharacterCodingException ignored) { return value; }
    }

    private static boolean repair(JSONArray array, JSONArray defaults) {
        boolean changed = false;
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            Object defaultValue = defaults != null && i < defaults.length() ? defaults.get(i) : null;
            if (value instanceof JSONObject) changed |= repair((JSONObject) value,
                    defaultValue instanceof JSONObject ? (JSONObject) defaultValue : null);
            else if (value instanceof JSONArray) changed |= repair((JSONArray) value,
                    defaultValue instanceof JSONArray ? (JSONArray) defaultValue : null);
            else if (value instanceof String) {
                String original = (String) value;
                String repaired = isCorrupted(original) && defaultValue instanceof String
                        ? (String) defaultValue : repairString(original);
                if (!repaired.equals(value)) { array.put(i, repaired); changed = true; }
            }
        }
        return changed;
    }

    private static int suspiciousScore(String value) {
        int score = 0;
        for (int i = 0; i < value.length(); i++) if (SUSPICIOUS.indexOf(value.charAt(i)) >= 0) score++;
        return score;
    }

    private static boolean isCorrupted(String value) {
        return suspiciousScore(value) >= 2 || value.indexOf('\uFFFD') >= 0 || value.contains("锟") || value.contains("�");
    }
}
