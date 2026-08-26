package dev.neovoxel.neobot.script;

import dev.neovoxel.nbapi.action.data.BasicInfo;
import dev.neovoxel.nbapi.action.data.GroupMemberInfo;
import dev.neovoxel.nbapi.action.data.GroupMemberList;
import dev.neovoxel.nbapi.action.get.GetGroupMemberInfo;
import dev.neovoxel.nbapi.action.get.GetGroupMemberList;
import dev.neovoxel.nbapi.client.NBotClient;
import dev.neovoxel.neobot.NeoBot;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Resolves a QQ group member's display name (group card, falling back to nickname) via OneBot's
 *  get_group_member_info action, which is only available as an async callback. Since business-script
 *  dispatch is synchronous, a lookup blocks the calling (QQ message) thread with a short timeout and
 *  caches the result so most messages never pay the round-trip. A failed lookup is retried once with
 *  no_cache=true, since NapCat can report a group member as missing until it has cached them itself
 *  (e.g. their first message after the bot connects) - see NapNeko/NapCatQQ#682. If both attempts still
 *  fail, a last resort falls back to get_group_member_list (NeoBot's predecessor, AQQBot, resolves names
 *  this way for every message instead of a per-member lookup) and searches the roster for the member -
 *  a different code path that isn't subject to the same per-member cache gap. That binding only exposes
 *  nickname, not card, so this tier is strictly a fallback below the direct card lookup above. */
public final class QqNicknameResolver {
    private static final long POSITIVE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long LOOKUP_TIMEOUT_MILLIS = 1500L;

    private final NeoBot plugin;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public QqNicknameResolver(NeoBot plugin) {
        this.plugin = plugin;
    }

    public String resolve(long groupId, long userId) {
        String key = groupId + ":" + userId;
        String fallback = String.valueOf(userId);
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(key);
        if (cached != null && now < cached.expiresAtMillis) return cached.value;
        String resolved = fetch(groupId, userId);
        if (resolved != null) {
            cache.put(key, new CacheEntry(resolved, now + POSITIVE_TTL_MILLIS));
            return resolved;
        }
        cache.put(key, new CacheEntry(fallback, now + NEGATIVE_TTL_MILLIS));
        return fallback;
    }

    private String fetch(long groupId, long userId) {
        String result = lookup(groupId, userId, false);
        if (result != null) return result;
        result = lookup(groupId, userId, true);
        if (result != null) return result;
        return lookupFromMemberList(groupId, userId);
    }

    private String lookup(long groupId, long userId, boolean noCache) {
        CompletableFuture<GroupMemberInfo> future = new CompletableFuture<>();
        boolean dispatched = false;
        for (NBotClient client : plugin.getBotProvider().getBot()) {
            if (client.isConnected()) {
                client.action(new GetGroupMemberInfo(groupId, userId, noCache), future::complete);
                dispatched = true;
                break;
            }
        }
        if (!dispatched) return null;
        try {
            GroupMemberInfo info = future.get(LOOKUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (info == null) return null;
            String card = info.getCard();
            if (card != null && !card.trim().isEmpty()) return card;
            String nickname = info.getNickname();
            return nickname != null && !nickname.trim().isEmpty() ? nickname : null;
        } catch (TimeoutException | InterruptedException | java.util.concurrent.ExecutionException error) {
            return null;
        }
    }

    private String lookupFromMemberList(long groupId, long userId) {
        CompletableFuture<GroupMemberList> future = new CompletableFuture<>();
        boolean dispatched = false;
        for (NBotClient client : plugin.getBotProvider().getBot()) {
            if (client.isConnected()) {
                client.action(new GetGroupMemberList(groupId), future::complete);
                dispatched = true;
                break;
            }
        }
        if (!dispatched) return null;
        try {
            GroupMemberList members = future.get(LOOKUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (members == null) return null;
            for (BasicInfo member : members) {
                if (member.getUserId() == userId) {
                    String nickname = member.getNickname();
                    return nickname != null && !nickname.trim().isEmpty() ? nickname : null;
                }
            }
            return null;
        } catch (TimeoutException | InterruptedException | java.util.concurrent.ExecutionException error) {
            return null;
        }
    }

    private static final class CacheEntry {
        final String value;
        final long expiresAtMillis;
        CacheEntry(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
