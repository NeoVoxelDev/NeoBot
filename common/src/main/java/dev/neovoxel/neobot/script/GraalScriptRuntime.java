package dev.neovoxel.neobot.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import java.util.concurrent.Callable;

/** Centralizes Graal provider discovery under the plugin class loader. */
public final class GraalScriptRuntime {
    private GraalScriptRuntime() { }

    public static Engine createEngine() {
        return withPluginClassLoader(() -> {
            System.setProperty("polyglotimpl.DisableMultiReleaseCheck", "true");
            Engine engine = Engine.newBuilder()
                    .allowExperimentalOptions(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            requireJavaScript(engine);
            return engine;
        });
    }

    public static Context buildContext(Context.Builder builder) {
        return withPluginClassLoader(builder::build);
    }

    public static void requireJavaScript(Engine engine) {
        if (!engine.getLanguages().containsKey("js")) {
            throw new IllegalStateException("GraalJS provider is unavailable from the NeoBot plugin class loader");
        }
    }

    public static <T> T withPluginClassLoader(Callable<T> action) {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(ScriptProvider.class.getClassLoader());
        try {
            return action.call();
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
