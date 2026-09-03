package dev.neovoxel.neobot.config;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DefaultConfigJsonTest {
    @Test
    void defaultConfigContainsExplicitQqForwardingSwitchesAndGroups() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            assertNotNull(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            assertTrue(json.getJSONObject("bot").getJSONObject("options").getJSONArray("enable-groups").length() > 0);
            JSONObject forwarding = json.getJSONObject("chat-forward");
            assertTrue(forwarding.getJSONObject("to-qq").getBoolean("enable"));
            assertTrue(forwarding.getJSONObject("to-game").getBoolean("enable"));
            assertEquals(1900, forwarding.getJSONObject("to-qq").getInt("max-length"));
        }
    }

    @Test
    void defaultConfigContainsQqServerStatusSwitch() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            assertNotNull(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            assertTrue(json.getJSONObject("bot").getJSONObject("qq").getJSONObject("server-status").getBoolean("enabled"));
        }
    }

    @Test
    void defaultConfigContainsPlayerStatusSwitchesAndMessages() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            assertNotNull(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            assertTrue(json.getJSONObject("bot").getJSONObject("qq").getJSONObject("player-status").getBoolean("enabled"));
            assertTrue(json.getJSONObject("bot").getJSONObject("discord").getJSONObject("player-status").getBoolean("enabled"));
            JSONObject playerStatus = json.getJSONObject("bot").getJSONObject("player-status");
            assertFalse(playerStatus.getString("join-message").isEmpty());
            assertFalse(playerStatus.getString("quit-message").isEmpty());
            assertFalse(playerStatus.getString("death-message").isEmpty());
        }
    }

    @Test
    void defaultConfigEnablesAccountRequireBindingWithNonEmptyMessage() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            assertNotNull(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            JSONObject account = json.getJSONObject("bot").getJSONObject("account");
            assertTrue(account.getBoolean("require-binding"));
            assertFalse(account.getString("require-binding-message").isEmpty());
        }
    }

    @Test
    void defaultConfigEnablesNotifyBindSuccess() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            assertNotNull(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            JSONObject account = json.getJSONObject("bot").getJSONObject("account");
            assertTrue(account.getBoolean("notify-bind-success"));
        }
    }

    @Test
    void defaultConfigContainsRemoteCommandResultFormatWithResultPlaceholder() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            assertNotNull(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            String format = json.getJSONObject("bot").getJSONObject("discord").getJSONObject("management")
                    .getString("remote-command-result-format");
            assertEquals("[NeoBot] 命令执行结果: \n${result}", format);
        }
    }

    @Test
    void defaultConfigContainsAutoUpdateBlockDisabledByDefault() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            assertNotNull(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            JSONObject repository = json.getJSONObject("repository");
            assertFalse(repository.getBoolean("use-github-proxy"));
            JSONObject autoUpdate = repository.getJSONObject("auto-update");
            assertFalse(autoUpdate.getBoolean("enabled"));
            assertEquals(60, autoUpdate.getInt("check-interval-minutes"));
            assertEquals(3000, autoUpdate.getInt("connect-timeout-ms"));
            assertEquals(0, autoUpdate.getJSONArray("mirrors").length());
        }
    }
}
