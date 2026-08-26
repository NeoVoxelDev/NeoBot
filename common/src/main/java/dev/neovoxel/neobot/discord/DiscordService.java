package dev.neovoxel.neobot.discord;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.adapter.OfflinePlayer;
import dev.neovoxel.neobot.discord.model.DiscordAccountBinding;
import dev.neovoxel.neobot.discord.model.DiscordChannelBinding;
import dev.neovoxel.neobot.discord.repository.DatabaseDiscordAccountRepository;
import dev.neovoxel.neobot.discord.repository.DatabaseDiscordChannelRepository;
import dev.neovoxel.neobot.discord.repository.DiscordAccountRepository;
import dev.neovoxel.neobot.discord.repository.DiscordChannelRepository;
import dev.neovoxel.neobot.game.event.ChatEvent;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.json.JSONArray;
import dev.neovoxel.nsapi.entity.Row;

/** Keeps Discord account and channel data independent from the OneBot data model. */
public class DiscordService {
    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    public enum AccountResult { SUCCESS, INVALID_PLAYER, PLAYER_ALREADY_BOUND, USER_MAX_BINDINGS, NOT_BOUND, NOT_OWNER }

    private final NeoBot plugin;
    private final DiscordChannelRepository channels;
    private final DiscordAccountRepository accounts;

    public DiscordService(NeoBot plugin) {
        this(plugin, new DatabaseDiscordChannelRepository(plugin.getStorageProvider().getStorage()),
                new DatabaseDiscordAccountRepository(plugin.getStorageProvider().getStorage()));
    }

    public DiscordService(NeoBot plugin, DiscordChannelRepository channels, DiscordAccountRepository accounts) {
        this.plugin = plugin;
        this.channels = channels;
        this.accounts = accounts;
    }

    public void initialize() {
        channels.initialize();
        accounts.initialize();
        migrateLegacyQqBindings();
        migrateLegacyChatChannel();
    }

    /** Imports only unambiguous legacy QQ whitelist entries; conflicts are skipped and never overwrite links. */
    private void migrateLegacyQqBindings() {
        if (plugin == null) return;
        try {
            for (Row row : plugin.getStorageProvider().getStorage().table("neobot_whitelist")
                    .select("qq", "players").execute().map()) {
                String qq = String.valueOf(row.getLong("qq"));
                JSONArray players = new JSONArray(row.getString("players"));
                for (Object value : players) {
                    String name = String.valueOf(value);
                    OfflinePlayer player = plugin.getOfflinePlayer(name);
                    if (!resolves(name, player)) {
                        plugin.getNeoLogger().warn("Skipped legacy QQ binding for unknown player " + name);
                        continue;
                    }
                    if (!accounts.bindQqUser(player.getUuid(), qq)) {
                        plugin.getNeoLogger().warn("Skipped conflicting legacy QQ binding for " + qq + " -> " + name);
                    }
                }
            }
        } catch (Throwable ignored) {
            // The legacy table is optional; absence must not prevent Discord startup.
        }
    }

    /** Detects whether a server-messages-channel message looks like an owner/admin remote command, based on
     *  configured sub-server prefixes. Java only recognizes the shape; scripts decide authorization and execution. */
    public boolean looksLikeRemoteCommand(String content) {
        if (content == null) return false;
        String first = content.trim().split("\\s+", 2)[0];
        if (first.isEmpty()) return false;
        if (plugin == null || !plugin.getGeneralConfig().has("bot.discord.management.servers")) return false;
        for (dev.neovoxel.neobot.config.Config cfg : plugin.getGeneralConfig().getArray("bot.discord.management.servers")) {
            if (first.equalsIgnoreCase(cfg.getString("prefix"))) return true;
        }
        return false;
    }

    /** Legacy channel rows remain available for administrator-managed advanced configuration. */
    public List<DiscordChannelBinding> channels() { return channels.findAll(); }
    public boolean bindChannel(String guild, String channel) { return valid(guild) && valid(channel) && channels.add(new DiscordChannelBinding(guild, channel)); }
    public boolean unbindChannel(String guild, String channel) { return channels.remove(guild, channel); }
    public DiscordAccountBinding account(UUID uuid) { return accounts.findByMinecraftUuid(uuid); }
    public List<DiscordAccountBinding> accountsForUser(String userId) { return accounts.findByDiscordUserId(userId); }

    /** Shared binding lookup used by the OneBot bridge; QQ IDs remain strings at the boundary. */
    public DiscordAccountBinding accountForQq(String qqUserId) { return accounts.findByQqUserId(qqUserId); }
    public boolean bindFromQq(String playerName, OfflinePlayer player, String qqUserId) {
        if (!qqUserIdValid(qqUserId) || !resolves(playerName, player)) return false;
        DiscordAccountBinding existing = account(player.getUuid());
        if (existing != null && existing.getQqUserId() != null && !existing.getQqUserId().isEmpty()) return false;
        return accounts.bindQqUser(player.getUuid(), qqUserId);
    }
    public boolean unbindFromQq(String playerName, OfflinePlayer player, String qqUserId) {
        if (!qqUserIdValid(qqUserId) || !resolves(playerName, player)) return false;
        return accounts.unbindQqUser(player.getUuid(), qqUserId);
    }

    public boolean adminBind(String playerName, UUID uuid, String discordUserId) {
        return valid(discordUserId) && accounts.add(new DiscordAccountBinding(uuid, playerName, discordUserId), maxBindings());
    }

    public boolean unbindAccount(UUID uuid) { return accounts.removeByMinecraftUuid(uuid); }

    /** Unified server messages channel; legacy chat-channel-id remains a fallback. */
    public String chatChannelId() {
        String server = string("channels.server-messages-channel-id", "").trim();
        return server.isEmpty() ? string("channels.chat-channel-id", "").trim() : server;
    }
    public String bindChannelId() { return string("channels.bind-channel-id", "").trim(); }
    public String serverMessagesChannelId() { return string("channels.server-messages-channel-id", "").trim(); }
    private String messagingChannelId() {
        String channel = serverMessagesChannelId();
        return channel.isEmpty() ? chatChannelId() : channel;
    }
    public boolean isServerMessagesChannel(String channelId) {
        return valid(channelId) && DiscordChannelPolicy.isChatChannel(channelId, messagingChannelId())
                && !channelId.equals(bindChannelId());
    }
    public boolean isChatChannel(String channelId) {
        return valid(channelId) && DiscordChannelPolicy.isChatChannel(channelId, chatChannelId()) && !channelId.equals(bindChannelId());
    }
    public boolean isBindChannel(String channelId) {
        return valid(channelId) && DiscordChannelPolicy.isBindChannel(channelId, bindChannelId()) && !channelId.equals(chatChannelId());
    }

    public AccountResult bindFromDiscord(String playerName, OfflinePlayer player, String discordUserId) {
        if (!valid(discordUserId) || !resolves(playerName, player)) return AccountResult.INVALID_PLAYER;
        if (account(player.getUuid()) != null) return AccountResult.PLAYER_ALREADY_BOUND;
        if (accounts.findByDiscordUserId(discordUserId).size() >= maxBindings()) return AccountResult.USER_MAX_BINDINGS;
        return accounts.add(new DiscordAccountBinding(player.getUuid(), player.getName(), discordUserId), maxBindings())
                ? AccountResult.SUCCESS : AccountResult.PLAYER_ALREADY_BOUND;
    }

    public AccountResult unbindFromDiscord(String playerName, OfflinePlayer player, String discordUserId) {
        if (!valid(discordUserId) || !resolves(playerName, player)) return AccountResult.INVALID_PLAYER;
        DiscordAccountBinding binding = account(player.getUuid());
        if (binding == null) return AccountResult.NOT_BOUND;
        if (!discordUserId.equals(binding.getDiscordUserId())) return AccountResult.NOT_OWNER;
        return accounts.removeByMinecraftUuid(player.getUuid()) ? AccountResult.SUCCESS : AccountResult.NOT_BOUND;
    }

    /** Whether Discord messages sent by the bot's own account should be ignored when forwarding to Minecraft. */
    public boolean ignoresSelfMessages() { return bool("chat.ignore-self", true); }
    /** Whether Discord messages sent by any bot account (webhooks included) should be ignored when forwarding to Minecraft. */
    public boolean ignoresBotMessages() { return bool("chat.ignore-bots", true); }

    public void onMinecraftChat(ChatEvent event) {
        if (!enabled() || !bool("chat.minecraft-to-discord.enabled", true) || !isServerMessagesChannel(messagingChannelId())) return;
        String message = format("chat.minecraft-to-discord.format", "[MC] ${player}: ${message}")
                .replace("${player}", event.getPlayer().getName()).replace("${message}", event.getMessage());
        plugin.getBotProvider().getDiscordBotListener().sendMessageString(messagingChannelId(),
                DiscordForwardingPolicy.truncate(message, integer("chat.maximum-length", 1900)));
    }

    public void notifyServerStarted() {
        dev.neovoxel.neobot.script.ScriptDispatchResult result = plugin.getScriptProvider() == null
                ? dev.neovoxel.neobot.script.ScriptDispatchResult.rejected()
                : plugin.getScriptProvider().dispatchBusiness("ServerStartEvent", this);
        if (plugin.getScriptProvider() == null) {
            plugin.getNeoLogger().warn("ServerStartEvent rejected: script system is not loaded");
            return;
        }
        plugin.getScriptProvider().executeBusinessActions(result);
    }

    public void notifyServerStopping() {
        dev.neovoxel.neobot.script.ScriptDispatchResult result = plugin.getScriptProvider() == null
                ? dev.neovoxel.neobot.script.ScriptDispatchResult.rejected()
                : plugin.getScriptProvider().dispatchBusiness("ServerStopEvent", this);
        if (plugin.getScriptProvider() == null) {
            plugin.getNeoLogger().warn("ServerStopEvent rejected: script system is not loaded");
            return;
        }
        plugin.getScriptProvider().executeBusinessActions(result);
    }

    private void migrateLegacyChatChannel() {
        if (plugin == null || !plugin.getGeneralConfig().wasMissingAtLoad("bot.discord.channels.chat-channel-id")) return;
        List<DiscordChannelBinding> legacy = channels();
        if (!legacy.isEmpty()) plugin.getGeneralConfig().setOption("bot.discord.channels.chat-channel-id", legacy.get(0).getChannelId());
    }

    private static boolean resolves(String requested, OfflinePlayer player) {
        return requested != null && PLAYER_NAME.matcher(requested).matches() && player != null
                && player.getName() != null && !player.getName().trim().isEmpty()
                && requested.equalsIgnoreCase(player.getName());
    }
    private boolean enabled() { return plugin != null && plugin.getGeneralConfig().has("bot.discord.enabled") && plugin.getGeneralConfig().getBoolean("bot.discord.enabled"); }
    private boolean bool(String path, boolean fallback) { String key = "bot.discord." + path; return plugin != null && plugin.getGeneralConfig().has(key) ? plugin.getGeneralConfig().getBoolean(key) : fallback; }
    private int integer(String path, int fallback) { String key = "bot.discord." + path; return plugin != null && plugin.getGeneralConfig().has(key) ? plugin.getGeneralConfig().getInt(key) : fallback; }
    private String string(String path, String fallback) { String key = "bot.discord." + path; return plugin != null && plugin.getGeneralConfig().has(key) ? plugin.getGeneralConfig().getString(key) : fallback; }
    private String format(String path, String fallback) { return string(path, fallback); }
    private int maxBindings() { return Math.max(1, integer("account.maximum-bindings-per-user", 1)); }
    private static boolean valid(String value) { try { dev.neovoxel.nbapi.discord.DiscordSnowflake.parse(value); return true; } catch (Exception e) { return false; } }
    private static boolean qqUserIdValid(String value) { return value != null && value.matches("[0-9]{1,20}"); }
}
