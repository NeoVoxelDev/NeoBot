package dev.neovoxel.neobot.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

/** Command-line probe executed with the final ShadowJar as its only Graal runtime. */
public final class GraalShadowJarProbe {
    private GraalShadowJarProbe() { }

    public static void main(String[] args) {
        System.setProperty("polyglotimpl.DisableMultiReleaseCheck", "true");
        Engine engine = Engine.create();
        try {
            if (!engine.getLanguages().containsKey("js")) throw new AssertionError(engine.getLanguages());
            for (int round = 0; round < 2; round++) {
                Context context = Context.newBuilder("js").engine(engine).build();
                try {
                    if (context.eval("js", "40 + 2").asInt() != 42) throw new AssertionError("JavaScript evaluation failed");
                } finally {
                    context.close();
                }
            }
            System.out.println("GRAAL_SHADOW_RELOAD_OK languages=" + engine.getLanguages().keySet());
        } finally {
            engine.close();
        }
    }
}
