package dev.neovoxel.neobot.script;

import org.graalvm.polyglot.HostAccess;

/** Platform-neutral context handed to business scripts for bind/unbind/remote-command events. */
public final class InboundCommandContext {
    private final String content;
    private final String userId;
    private final String replyTarget;
    private final String platform;

    public InboundCommandContext(String content, String userId, String replyTarget, String platform) {
        this.content = content == null ? "" : content;
        this.userId = userId == null ? "" : userId;
        this.replyTarget = replyTarget == null ? "" : replyTarget;
        this.platform = platform == null ? "" : platform;
    }

    @HostAccess.Export
    public String getContent() { return content; }

    @HostAccess.Export
    public String getUserId() { return userId; }

    /** Fully-qualified send target, e.g. "qq:123" or "discord:456". */
    @HostAccess.Export
    public String getReplyTarget() { return replyTarget; }

    /** "qq" or "discord". */
    @HostAccess.Export
    public String getPlatform() { return platform; }
}
