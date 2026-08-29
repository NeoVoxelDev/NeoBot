package dev.neovoxel.neobot.config;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageEncodingRepairTest {
    @Test
    void restoresSemanticChineseInsteadOfMerelyValidUtf8() {
        JSONObject messages = new JSONObject().put("status", "\u59dd\uff45\u6e6a\u947e\u5cf0\u5f47\u93bb\u638d\u6b22\u9418\u8235\ufffd\ufffd...")
                .put("reload", "\u93bb\u638d\u6b22\u95b2\u5d88\u6d47\u7039\u5c7e\u579a\ufffd!")
                .put("valid", "\u6b63\u5728\u83b7\u53d6\u63d2\u4ef6\u72b6\u6001...");
        JSONObject defaults = new JSONObject().put("status", "\u6b63\u5728\u83b7\u53d6\u63d2\u4ef6\u72b6\u6001...")
                .put("reload", "\u63d2\u4ef6\u91cd\u8f7d\u5b8c\u6210!").put("valid", "\u6b63\u5728\u83b7\u53d6\u63d2\u4ef6\u72b6\u6001...");
        assertTrue(MessageEncodingRepair.repair(messages, defaults));
        assertEquals("\u6b63\u5728\u83b7\u53d6\u63d2\u4ef6\u72b6\u6001...", messages.getString("status"));
        assertEquals("\u63d2\u4ef6\u91cd\u8f7d\u5b8c\u6210!", messages.getString("reload"));
        assertEquals("\u6b63\u5728\u83b7\u53d6\u63d2\u4ef6\u72b6\u6001...", messages.getString("valid"));
    }
}
