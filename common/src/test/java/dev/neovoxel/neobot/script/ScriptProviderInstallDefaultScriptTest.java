package dev.neovoxel.neobot.script;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.NeoLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScriptProvider.installDefaultScript must install the bundled default script on a fresh
 * scripts folder, but must never overwrite a script directory that already exists there
 * (so "reload" and reinstalling the plugin never clobber a user's own default-script edits).
 */
class ScriptProviderInstallDefaultScriptTest {

    private static NeoBot fakePlugin() {
        NeoLogger noopLogger = (NeoLogger) Proxy.newProxyInstance(
                NeoLogger.class.getClassLoader(), new Class<?>[]{NeoLogger.class},
                (proxy, method, args) -> null);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getNeoLogger".equals(method.getName())) return noopLogger;
            if ("toString".equals(method.getName())) return "fake-plugin";
            if ("hashCode".equals(method.getName())) return 0;
            if ("equals".equals(method.getName())) return proxy == (args != null ? args[0] : null);
            throw new UnsupportedOperationException(method.getName() + " should not be called by installDefaultScript");
        };
        return (NeoBot) Proxy.newProxyInstance(NeoBot.class.getClassLoader(), new Class<?>[]{NeoBot.class}, handler);
    }

    private static void invokeInstallDefaultScript(ScriptProvider provider, NeoBot plugin, File scriptPath) throws Exception {
        Method method = ScriptProvider.class.getDeclaredMethod("installDefaultScript", NeoBot.class, File.class);
        method.setAccessible(true);
        method.invoke(provider, plugin, scriptPath);
    }

    @Test
    void installsTheDefaultScriptWhenMissing(@TempDir File scriptsDir) throws Exception {
        ScriptProvider provider = new ScriptProvider(null);
        invokeInstallDefaultScript(provider, fakePlugin(), scriptsDir);

        File manifest = new File(new File(scriptsDir, "default"), "manifest.json");
        File entry = new File(new File(scriptsDir, "default"), "main.js");
        assertTrue(manifest.exists(), "manifest.json must be installed on a fresh scripts folder");
        assertTrue(entry.exists(), "main.js must be installed on a fresh scripts folder");
        assertTrue(entry.length() > 0);
    }

    @Test
    void neverOverwritesAnExistingDefaultScript(@TempDir File scriptsDir) throws Exception {
        File target = new File(scriptsDir, "default");
        assertTrue(target.mkdirs());
        File manifest = new File(target, "manifest.json");
        File entry = new File(target, "main.js");
        Files.write(manifest.toPath(), "{\"user\":\"customized\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(entry.toPath(), "// user's own business policy\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ScriptProvider provider = new ScriptProvider(null);
        // A plugin whose getNeoLogger() would throw if called proves installDefaultScript takes
        // the "already installed" short-circuit and never touches the plugin at all.
        NeoBot plugin = (NeoBot) Proxy.newProxyInstance(NeoBot.class.getClassLoader(), new Class<?>[]{NeoBot.class},
                (proxy, method, args) -> { throw new AssertionError("must not be called when manifest already exists: " + method.getName()); });
        invokeInstallDefaultScript(provider, plugin, scriptsDir);

        assertEquals("{\"user\":\"customized\"}", new String(Files.readAllBytes(manifest.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("// user's own business policy\n", new String(Files.readAllBytes(entry.toPath()), java.nio.charset.StandardCharsets.UTF_8));
    }
}
