package dev.neovoxel.neobot.update;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewVersionNotifierTest {
    private static JSONArray roles() {
        return new JSONArray()
                .put(new JSONObject().put("id", "111").put("name", "Owner"))
                .put(new JSONObject().put("id", "222").put("name", "Moderator"));
    }

    @Test
    void isRoleIdMatchesConfiguredRoleId() {
        assertTrue(NewVersionNotifier.isRoleId(roles(), "111"));
        assertFalse(NewVersionNotifier.isRoleId(roles(), "999"));
    }

    @Test
    void findRoleIdByNameIsCaseInsensitiveAndDefaultsToCapitalizedOwner() {
        assertEquals("111", NewVersionNotifier.findRoleIdByName(roles(), "Owner"));
        assertEquals("111", NewVersionNotifier.findRoleIdByName(roles(), "owner"));
        assertNull(NewVersionNotifier.findRoleIdByName(roles(), "Admin"));
    }
}
