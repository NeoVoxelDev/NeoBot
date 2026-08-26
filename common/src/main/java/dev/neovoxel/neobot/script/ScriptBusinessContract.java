package dev.neovoxel.neobot.script;

/** Stable names shared by platform adapters and business scripts. */
public final class ScriptBusinessContract {
    private ScriptBusinessContract() {}
    public static final String QQ_GROUP_MESSAGE = "QQGroupMessageEvent";
    public static final String DISCORD_MESSAGE = "DiscordMessageEvent";
    public static final String MC_CHAT = "MinecraftChatEvent";
    public static final String BIND = "BindEvent";
    public static final String UNBIND = "UnbindEvent";
    public static final String REMOTE_COMMAND = "RemoteCommandEvent";
    public static final String SERVER_START = "ServerStartEvent";
    public static final String SERVER_STOP = "ServerStopEvent";
    public static final String SEND_QQ = "sendToQQ";
    public static final String SEND_DISCORD = "sendToDiscord";
    public static final String SEND_MINECRAFT = "sendToMinecraft";
    public static final String EXECUTE_MINECRAFT = "executeMinecraftCommand";
    public static final String BIND_ACTION = "bind";
    public static final String UNBIND_ACTION = "unbind";
}
