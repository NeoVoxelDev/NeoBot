package dev.neovoxel.neobot.script;

import java.util.List;

/** Controlled bridge for business scripts; platform implementations own the actual clients. */
public interface BusinessActionExecutor {
    void execute(String action, String target, String content) throws Exception;
    default void executeAll(List<String> actions, List<String> targets, String content) {
        for (String action : actions) for (String target : targets) {
            try { execute(action, target, content); }
            catch (Exception ignored) { }
        }
    }
}
