package dev.neovoxel.neobot.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalScriptRuntimeTest {
    @Test
    void javascriptRemainsAvailableAcrossTwoContextLifecycles() {
        Engine engine = GraalScriptRuntime.createEngine();
        try {
            assertTrue(engine.getLanguages().containsKey("js"));
            for (int round = 0; round < 2; round++) {
                Context context = GraalScriptRuntime.buildContext(Context.newBuilder("js").engine(engine));
                try {
                    assertEquals(3, context.eval("js", "1 + 2").asInt());
                } finally {
                    context.close();
                }
                GraalScriptRuntime.requireJavaScript(engine);
            }
        } finally {
            engine.close();
        }
    }
}
