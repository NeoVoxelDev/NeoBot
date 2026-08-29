package dev.neovoxel.neobot.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Structured result returned by a script business dispatcher. */
public final class ScriptDispatchResult {
    private final boolean handled;
    private final boolean cancelled;
    private final String content;
    private final List<String> targets;
    private final List<String> actions;
    private final Map<String, String> contentByAction;

    public ScriptDispatchResult(boolean handled, boolean cancelled, String content,
                                List<String> targets, List<String> actions) {
        this(handled, cancelled, content, targets, actions, null);
    }

    /** contentByAction lets a single event address multiple platforms with different wording
     *  per action (e.g. distinct QQ vs Discord chat-forward formats) without breaking every other
     *  event that still relies on a single shared content string applied to every action. */
    public ScriptDispatchResult(boolean handled, boolean cancelled, String content,
                                List<String> targets, List<String> actions, Map<String, String> contentByAction) {
        this.handled = handled;
        this.cancelled = cancelled;
        this.content = content;
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets == null ? Collections.<String>emptyList() : targets));
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions == null ? Collections.<String>emptyList() : actions));
        this.contentByAction = Collections.unmodifiableMap(new HashMap<>(contentByAction == null ? Collections.<String, String>emptyMap() : contentByAction));
    }
    public boolean isHandled() { return handled; }
    public boolean isCancelled() { return cancelled; }
    public String getContent() { return content; }
    public List<String> getTargets() { return targets; }
    public List<String> getActions() { return actions; }
    public Map<String, String> getContentByAction() { return contentByAction; }
    /** Resolves the content for a given action: its per-action override if present, else the shared content. */
    public String getContentFor(String action) {
        String override = contentByAction.get(action);
        return override != null ? override : content;
    }
    public static ScriptDispatchResult rejected() { return new ScriptDispatchResult(true, true, null, null, null); }
    public static ScriptDispatchResult unhandled() { return new ScriptDispatchResult(false, false, null, null, null); }
}
