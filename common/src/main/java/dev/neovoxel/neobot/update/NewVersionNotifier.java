package dev.neovoxel.neobot.update;

import dev.neovoxel.nbapi.action.data.BasicInfo;
import dev.neovoxel.nbapi.action.data.GroupMemberInfo;
import dev.neovoxel.nbapi.action.data.GroupMemberList;
import dev.neovoxel.nbapi.action.get.GetGroupMemberInfo;
import dev.neovoxel.nbapi.action.get.GetGroupMemberList;
import dev.neovoxel.nbapi.action.set.SendGroupMessage;
import dev.neovoxel.nbapi.client.NBotClient;
import dev.neovoxel.nbapi.util.Role;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.util.HttpUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Routes a "new NeoBot version available" notice to whichever platforms are actually connected:
 *  both QQ and Discord if both are up, just the one that's up if only one is, or nothing beyond the
 *  log line UpdateService already wrote if neither is connected. */
public class NewVersionNotifier {
    private static final String OWNER_ROLE_NAME = "Owner";

    private final NeoBot plugin;

    public NewVersionNotifier(NeoBot plugin) {
        this.plugin = plugin;
    }

    public void notifyNewVersion(String currentVersion, String latestVersion) {
        boolean qqConnected = isQqConnected();
        boolean discordConnected = isDiscordConnected();
        if (!qqConnected && !discordConnected) {
            plugin.getNeoLogger().info("No QQ or Discord connection is active; the new-version notice above was logged only.");
            return;
        }
        String message = plugin.getMessageConfig().getMessage("internal.update.new-version-available")
                .replace("${current}", currentVersion).replace("${latest}", latestVersion);
        if (qqConnected) notifyQq(message);
        if (discordConnected) notifyDiscord(message);
    }

    private boolean isQqConnected() {
        for (NBotClient client : plugin.getBotProvider().getBot()) if (client.isConnected()) return true;
        return false;
    }

    private boolean isDiscordConnected() {
        for (dev.neovoxel.nbapi.discord.client.DiscordClient client : plugin.getBotProvider().getDiscordClients()) {
            if (client.isConnected()) return true;
        }
        return false;
    }

    // ---- QQ ----

    /** Tries the real group owner first (GetGroupMemberList + Role.OWNER); only if that lookup
     *  fails or times out does it fall back to @-mentioning bot.qq.management.admin-user-ids. */
    private void notifyQq(String message) {
        List<String> adminIds = plugin.getGeneralConfig().getStringArray("bot.qq.management.admin-user-ids");
        for (String groupIdStr : plugin.getGeneralConfig().getStringArray("bot.options.enable-groups")) {
            long groupId;
            try {
                groupId = Long.parseLong(groupIdStr.trim());
            } catch (NumberFormatException error) {
                continue;
            }
            for (NBotClient client : plugin.getBotProvider().getBot()) {
                if (client.isConnected()) notifyQqGroup(client, groupId, adminIds, message);
            }
        }
    }

    private void notifyQqGroup(NBotClient client, long groupId, List<String> adminIds, String message) {
        client.action(new GetGroupMemberList(groupId), (GroupMemberList members) -> {
            if (members == null || members.isEmpty()) {
                plugin.getNeoLogger().warn("Could not list members of QQ group " + groupId + " to find its owner; falling back to admin-user-ids");
                notifyQqAdmins(client, groupId, adminIds, message);
                return;
            }
            resolveGroupOwner(client, groupId, members, ownerId -> {
                if (ownerId != null) {
                    client.action(new SendGroupMessage(groupId, atAndTextSegments(java.util.Collections.singletonList(ownerId), message)));
                } else {
                    plugin.getNeoLogger().warn("Could not resolve the owner of QQ group " + groupId + "; falling back to admin-user-ids");
                    notifyQqAdmins(client, groupId, adminIds, message);
                }
            });
        });
    }

    private void notifyQqAdmins(NBotClient client, long groupId, List<String> adminIds, String message) {
        if (adminIds.isEmpty()) {
            plugin.getNeoLogger().warn("Could not resolve the owner of QQ group " + groupId + " and no admin-user-ids are configured as a fallback");
            return;
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (String adminId : adminIds) {
            try {
                ids.add(Long.parseLong(adminId.trim()));
            } catch (NumberFormatException error) {
                plugin.getNeoLogger().warn("Invalid QQ admin id in bot.qq.management.admin-user-ids: " + adminId);
            }
        }
        if (!ids.isEmpty()) client.action(new SendGroupMessage(groupId, atAndTextSegments(ids, message)));
    }

    /** Fires one GetGroupMemberInfo lookup per member (the list action doesn't include role) and
     *  reports the first OWNER found, or null if none responds within 10s. */
    private void resolveGroupOwner(NBotClient client, long groupId, List<BasicInfo> members, java.util.function.Consumer<Long> onResolved) {
        AtomicReference<Long> ownerId = new AtomicReference<>();
        CompletableFuture<?>[] futures = members.stream().map(member -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            client.action(new GetGroupMemberInfo(groupId, member.getUserId()), (GroupMemberInfo info) -> {
                if (info != null && info.getRole() == Role.OWNER) ownerId.compareAndSet(null, info.getUserId());
                future.complete(null);
            });
            return future;
        }).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> onResolved.accept(ownerId.get()));
    }

    private static JSONArray atAndTextSegments(List<Long> userIds, String message) {
        JSONArray segments = new JSONArray();
        for (long userId : userIds) {
            segments.put(new JSONObject().put("type", "at").put("data", new JSONObject().put("qq", String.valueOf(userId))));
        }
        segments.put(new JSONObject().put("type", "text").put("data", new JSONObject().put("text", " " + message)));
        return segments;
    }

    // ---- Discord ----

    private void notifyDiscord(String message) {
        String channelId = plugin.getDiscordService().serverMessagesChannelId();
        if (channelId.isEmpty()) {
            plugin.getNeoLogger().warn("Discord is connected but bot.discord.channels.server-messages-channel-id is not configured; skipping new-version notice");
            return;
        }
        String mention = resolveDiscordMention(channelId);
        String content = mention.isEmpty() ? message : (mention + " " + message);
        plugin.getBotProvider().getDiscordBotListener().sendMessageString(channelId, content);
    }

    /** Tries the real guild owner first (GET /guilds/{id}'s owner_id); only if that lookup fails
     *  does it fall back to admin-user-ids, then owner-user-ids (auto-detecting per id whether it
     *  names a guild role or a user), then a role literally named "Owner" in the channel's guild. */
    private String resolveDiscordMention(String channelId) {
        String apiBase = plugin.getBotProvider().getDiscordApiBase();
        String token = plugin.getBotProvider().getDiscordToken();
        String guildId = null;
        if (apiBase != null && token != null) {
            try {
                guildId = fetchChannelGuildId(apiBase, token, channelId);
                if (guildId != null) {
                    String ownerId = fetchGuildOwnerId(apiBase, token, guildId);
                    if (ownerId != null) return "<@" + ownerId + ">";
                }
            } catch (Exception error) {
                plugin.getNeoLogger().warn("Failed to resolve the real Discord guild owner for the new-version notice: " + error.getMessage());
            }
        }
        List<String> adminIds = plugin.getGeneralConfig().getStringArray("bot.discord.management.admin-user-ids");
        if (!adminIds.isEmpty()) {
            StringBuilder mention = new StringBuilder();
            for (String id : adminIds) mention.append("<@").append(id.trim()).append("> ");
            return mention.toString().trim();
        }
        List<String> ownerIds = plugin.getGeneralConfig().getStringArray("bot.discord.management.owner-user-ids");
        if (apiBase == null || token == null || guildId == null) return "";
        try {
            JSONArray roles = fetchGuildRoles(apiBase, token, guildId);
            if (!ownerIds.isEmpty()) {
                StringBuilder mention = new StringBuilder();
                for (String id : ownerIds) {
                    String trimmed = id.trim();
                    mention.append(isRoleId(roles, trimmed) ? "<@&" + trimmed + ">" : "<@" + trimmed + ">").append(' ');
                }
                return mention.toString().trim();
            }
            String ownerRoleId = findRoleIdByName(roles, OWNER_ROLE_NAME);
            if (ownerRoleId != null) return "<@&" + ownerRoleId + ">";
            plugin.getNeoLogger().warn("Could not resolve the real Discord guild owner and no admin-user-ids, owner-user-ids, or a role named \""
                    + OWNER_ROLE_NAME + "\" is configured; posting the new-version notice without a mention");
            return "";
        } catch (Exception error) {
            plugin.getNeoLogger().warn("Failed to resolve who to mention for the Discord new-version notice: " + error.getMessage());
            return "";
        }
    }

    private static String fetchGuildOwnerId(String apiBase, String token, String guildId) throws java.io.IOException {
        String content = HttpUtil.get(trimTrailingSlash(apiBase) + "/guilds/" + guildId, authHeaders(token), false);
        JSONObject guild = new JSONObject(content);
        return guild.has("owner_id") ? guild.getString("owner_id") : null;
    }

    private static String fetchChannelGuildId(String apiBase, String token, String channelId) throws java.io.IOException {
        String content = HttpUtil.get(trimTrailingSlash(apiBase) + "/channels/" + channelId, authHeaders(token), false);
        JSONObject channel = new JSONObject(content);
        return channel.has("guild_id") ? channel.getString("guild_id") : null;
    }

    private static JSONArray fetchGuildRoles(String apiBase, String token, String guildId) throws java.io.IOException {
        String content = HttpUtil.get(trimTrailingSlash(apiBase) + "/guilds/" + guildId + "/roles", authHeaders(token), false);
        return new JSONArray(content);
    }

    static boolean isRoleId(JSONArray roles, String id) {
        for (int i = 0; i < roles.length(); i++) {
            if (id.equals(roles.getJSONObject(i).optString("id", ""))) return true;
        }
        return false;
    }

    static String findRoleIdByName(JSONArray roles, String name) {
        for (int i = 0; i < roles.length(); i++) {
            JSONObject role = roles.getJSONObject(i);
            if (name.equalsIgnoreCase(role.optString("name", ""))) return role.optString("id", null);
        }
        return null;
    }

    private static Map<String, String> authHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bot " + token);
        return headers;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
