package dev.neovoxel.neobot.script;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.bot.BotListener;
import dev.neovoxel.neobot.bot.discord.DiscordBotListener;
import dev.neovoxel.neobot.adapter.OfflinePlayer;
import org.graalvm.polyglot.HostAccess;

/** Default adapter exposing only low-level platform sends to business scripts. */
public final class NeoBotBusinessActionExecutor implements BusinessActionExecutor {
    private final NeoBot plugin;
    private final SourceDeduplicator deduplicator = new SourceDeduplicator(30000L);
    private final QqNicknameResolver qqNicknameResolver;
    private final DiscordChannelNameResolver discordChannelNameResolver;
    public NeoBotBusinessActionExecutor(NeoBot plugin) {
        this.plugin = plugin;
        this.qqNicknameResolver = new QqNicknameResolver(plugin);
        this.discordChannelNameResolver = new DiscordChannelNameResolver(plugin);
    }
    @HostAccess.Export
    public String resolveQqNickname(long groupId, long userId) {
        return qqNicknameResolver.resolve(groupId, userId);
    }
    @HostAccess.Export
    public String resolveDiscordChannelName(String channelId) {
        return discordChannelNameResolver.resolve(channelId);
    }
    @HostAccess.Export
    public String bind(String playerName, String identity, String kind) {
        OfflinePlayer player = plugin.getOfflinePlayer(playerName);
        if (player == null || player.getUuid() == null) return "error:unknown-player";
        boolean ok = "qq".equalsIgnoreCase(kind)
                ? plugin.getDiscordService().bindFromQq(playerName, player, identity)
                : plugin.getDiscordService().adminBind(playerName, player.getUuid(), identity);
        return ok ? "success" : "error:already-bound-or-conflict";
    }
    @HostAccess.Export
    public String unbind(String playerName, String identity, String kind) {
        OfflinePlayer player = plugin.getOfflinePlayer(playerName);
        if (player == null || player.getUuid() == null) return "error:unknown-player";
        if ("qq".equalsIgnoreCase(kind)) return plugin.getDiscordService().unbindFromQq(playerName, player, identity) ? "success" : "error:not-owner-or-unbound";
        return plugin.getDiscordService().unbindFromDiscord(playerName, player, identity).name();
    }
    @HostAccess.Export
    public boolean isOwnerOrAdmin(String userId) {
        for (String id : plugin.getGeneralConfig().getStringArray("bot.discord.management.owner-user-ids")) if (id.equals(userId)) return true;
        for (String id : plugin.getGeneralConfig().getStringArray("bot.discord.management.admin-user-ids")) if (id.equals(userId)) return true;
        return false;
    }
    @HostAccess.Export
    public boolean isQqOwnerOrAdmin(String userId) {
        for (String id : plugin.getGeneralConfig().getStringArray("bot.qq.management.owner-user-ids")) if (id.equals(userId)) return true;
        for (String id : plugin.getGeneralConfig().getStringArray("bot.qq.management.admin-user-ids")) if (id.equals(userId)) return true;
        return false;
    }
    @HostAccess.Export
    public String executeMinecraftCommand(String server, String command) {
        if (server == null || server.trim().isEmpty() || command == null || command.trim().isEmpty()) return "error:invalid-command";
        if (!plugin.getGeneralConfig().has("bot.discord.management.servers")) return "error:server-not-configured";
        String executorName = null;
        for (dev.neovoxel.neobot.config.Config cfg : plugin.getGeneralConfig().getArray("bot.discord.management.servers")) {
            if (server.equalsIgnoreCase(cfg.getString("server-name")) || server.equalsIgnoreCase(cfg.getString("prefix"))) {
                if (!cfg.getBoolean("enabled")) return "error:server-disabled";
                executorName = cfg.getString("executor"); break;
            }
        }
        if (executorName == null) return "error:unknown-server";
        dev.neovoxel.neobot.adapter.RemoteExecutor executor = plugin.getExecutorByName(executorName);
        if (executor == null || !executor.init()) return "error:executor-unavailable";
        try { executor.execute(command); return executor.getResult() == null || executor.getResult().isEmpty() ? "success" : executor.getResult(); }
        catch (RuntimeException error) { return "error:execution-failed"; }
    }
    @Override public void executeAll(java.util.List<String> actions, java.util.List<String> targets, String content) {
        for (String action : actions) for (String target : targets) {
            if (!actionMatchesTarget(action, target)) continue;
            try { execute(action, target, content); }
            catch (Exception e) { plugin.getNeoLogger().error("Business action failed: action=" + action + " target=" + target, e); }
        }
    }
    /** Actions and targets are independent lists (not paired), so executeAll pairs every action with every
     *  target; this filters out cross-platform combinations (e.g. sendToQQ with a discord: target) before
     *  they reach execute(), instead of relying on execute() to reject and log them as failures. */
    static boolean actionMatchesTarget(String action, String target) {
        if (target == null) return false;
        switch (action) {
            case "sendToQQ": return target.startsWith("qq:");
            case "sendToDiscord": return target.startsWith("discord:");
            case "sendToMinecraft": return target.startsWith("minecraft");
            default: return true;
        }
    }
    @Override public void execute(String action, String target, String content) throws Exception {
        if (content == null) content = "";
        if ("sendToQQ".equals(action)) {
            BotListener qq = plugin.getBotProvider().getBotListener();
            String raw = target != null && target.startsWith("qq:") ? target.substring(3) : target;
            if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("QQ target is empty");
            if (raw.startsWith("private:")) {
                qq.sendPrivateMessage(Long.parseLong(raw.substring("private:".length())), content);
            } else {
                qq.sendGroupMessage(Long.parseLong(raw), content);
            }
        } else if ("sendToDiscord".equals(action)) {
            DiscordBotListener discord = plugin.getBotProvider().getDiscordBotListener();
            String channel = target != null && target.startsWith("discord:") ? target.substring(8) : target;
            if (channel == null || channel.trim().isEmpty()) throw new IllegalArgumentException("Discord channel target is empty");
            discord.sendMessageString(channel, content);
        } else if ("sendToMinecraft".equals(action)) {
            plugin.broadcast(content);
        } else if ("executeMinecraftCommand".equals(action)) {
            throw new UnsupportedOperationException("Script command execution requires a configured server target");
        } else {
            throw new IllegalArgumentException("Unknown business action: " + action);
        }
    }
    public boolean firstSource(String sourceId) { return deduplicator.first(sourceId); }
    @HostAccess.Export public String[] configuredQqGroups() {
        java.util.List<String> groups = plugin.getGeneralConfig().getStringArray("bot.options.enable-groups");
        return groups.toArray(new String[0]);
    }
    @HostAccess.Export public String configuredDiscordChannel() {
        return plugin.getGeneralConfig().has("bot.discord.channels.server-messages-channel-id")
                ? plugin.getGeneralConfig().getString("bot.discord.channels.server-messages-channel-id") : "";
    }
    @HostAccess.Export public String configuredServerName() {
        return plugin.getGeneralConfig().has("bot.discord.server-status.server-name")
                ? plugin.getGeneralConfig().getString("bot.discord.server-status.server-name") : "Minecraft";
    }
    @HostAccess.Export public String configuredStartMessage() {
        return plugin.getGeneralConfig().has("bot.discord.server-status.start-message")
                ? plugin.getGeneralConfig().getString("bot.discord.server-status.start-message") : "[${server}] 服务器已启动!";
    }
    @HostAccess.Export public String configuredStopMessage() {
        return plugin.getGeneralConfig().has("bot.discord.server-status.stop-message")
                ? plugin.getGeneralConfig().getString("bot.discord.server-status.stop-message") : "[${server}] 服务器已关闭!";
    }
    @HostAccess.Export public String configuredDiscordToMinecraftFormat() {
        return plugin.getGeneralConfig().has("bot.discord.chat.discord-to-minecraft.format")
                ? plugin.getGeneralConfig().getString("bot.discord.chat.discord-to-minecraft.format") : "[Discord(${channel})] ${user}: ${message}";
    }
    @HostAccess.Export public String configuredMinecraftToDiscordFormat() {
        return plugin.getGeneralConfig().has("bot.discord.chat.minecraft-to-discord.format")
                ? plugin.getGeneralConfig().getString("bot.discord.chat.minecraft-to-discord.format") : "[MC] ${player}: ${message}";
    }
    @HostAccess.Export public String configuredChatForwardToQqFormat() {
        return plugin.getGeneralConfig().has("chat-forward.to-qq.format")
                ? plugin.getGeneralConfig().getString("chat-forward.to-qq.format") : "[MC] ${player}: ${message}";
    }
    @HostAccess.Export public String configuredChatForwardToGameFormat() {
        return plugin.getGeneralConfig().has("chat-forward.to-game.format")
                ? plugin.getGeneralConfig().getString("chat-forward.to-game.format") : "[QQ群(${group})] ${user}: ${message}";
    }
    @HostAccess.Export public boolean configuredChatForwardToQqEnabled() {
        return !plugin.getGeneralConfig().has("chat-forward.to-qq.enable")
                || plugin.getGeneralConfig().getBoolean("chat-forward.to-qq.enable");
    }
    @HostAccess.Export public boolean configuredChatForwardToGameEnabled() {
        return !plugin.getGeneralConfig().has("chat-forward.to-game.enable")
                || plugin.getGeneralConfig().getBoolean("chat-forward.to-game.enable");
    }
    @HostAccess.Export public boolean configuredQqServerStatusEnabled() {
        return !plugin.getGeneralConfig().has("bot.qq.server-status.enabled")
                || plugin.getGeneralConfig().getBoolean("bot.qq.server-status.enabled");
    }
    @HostAccess.Export public boolean configuredDiscordServerStatusEnabled() {
        return !plugin.getGeneralConfig().has("bot.discord.server-status.enabled")
                || plugin.getGeneralConfig().getBoolean("bot.discord.server-status.enabled");
    }
    @HostAccess.Export public boolean configuredQqPlayerStatusEnabled() {
        return !plugin.getGeneralConfig().has("bot.qq.player-status.enabled")
                || plugin.getGeneralConfig().getBoolean("bot.qq.player-status.enabled");
    }
    @HostAccess.Export public boolean configuredDiscordPlayerStatusEnabled() {
        return !plugin.getGeneralConfig().has("bot.discord.player-status.enabled")
                || plugin.getGeneralConfig().getBoolean("bot.discord.player-status.enabled");
    }
    @HostAccess.Export public String configuredJoinMessage() {
        return plugin.getGeneralConfig().has("bot.player-status.join-message")
                ? plugin.getGeneralConfig().getString("bot.player-status.join-message") : "[${server}] ${player} 进入了服务器!";
    }
    @HostAccess.Export public String configuredQuitMessage() {
        return plugin.getGeneralConfig().has("bot.player-status.quit-message")
                ? plugin.getGeneralConfig().getString("bot.player-status.quit-message") : "[${server}] ${player} 离开了服务器!";
    }
    @HostAccess.Export public String configuredRemoteCommandResultFormat() {
        return plugin.getGeneralConfig().has("bot.discord.management.remote-command-result-format")
                ? plugin.getGeneralConfig().getString("bot.discord.management.remote-command-result-format") : "[NeoBot] 命令执行结果: \n${result}";
    }
    @HostAccess.Export public String configuredDeathMessage() {
        return plugin.getGeneralConfig().has("bot.player-status.death-message")
                ? plugin.getGeneralConfig().getString("bot.player-status.death-message") : "[${server}] ${player} 逝世了!";
    }
    @HostAccess.Export public boolean configuredAccountRequireBinding() {
        return plugin.getGeneralConfig().has("bot.account.require-binding")
                && plugin.getGeneralConfig().getBoolean("bot.account.require-binding");
    }
    @HostAccess.Export public String configuredAccountRequireBindingMessage() {
        return plugin.getGeneralConfig().has("bot.account.require-binding-message")
                ? plugin.getGeneralConfig().getString("bot.account.require-binding-message") : "";
    }
    @HostAccess.Export public boolean configuredNotifyBindSuccess() {
        return !plugin.getGeneralConfig().has("bot.account.notify-bind-success")
                || plugin.getGeneralConfig().getBoolean("bot.account.notify-bind-success");
    }
    @HostAccess.Export public boolean hasQqBinding(java.util.UUID uuid) {
        dev.neovoxel.neobot.discord.model.DiscordAccountBinding binding = plugin.getDiscordService().account(uuid);
        return binding != null && binding.getQqUserId() != null && !binding.getQqUserId().isEmpty();
    }
    @HostAccess.Export public boolean hasDiscordBinding(java.util.UUID uuid) {
        dev.neovoxel.neobot.discord.model.DiscordAccountBinding binding = plugin.getDiscordService().account(uuid);
        return binding != null && binding.getDiscordUserId() != null && !binding.getDiscordUserId().isEmpty();
    }
}
