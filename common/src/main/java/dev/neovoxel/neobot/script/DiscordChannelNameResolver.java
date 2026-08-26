package dev.neovoxel.neobot.script;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.util.http.HttpBuilder;
import dev.neovoxel.neobot.util.http.HttpResult;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Resolves a Discord channel's display name via the REST "get channel" endpoint, which the underlying
 *  gateway client (dev.neovoxel.nbapi) does not expose. Routed through whatever API base the configured
 *  proxy mode resolved to, so it keeps working for deployments that can't reach discord.com directly.
 *  Cached, since business-script dispatch is synchronous and this must not cost a request per message. */
public final class DiscordChannelNameResolver {
    private static final long POSITIVE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final int LOOKUP_TIMEOUT_MILLIS = 1500;

    private final NeoBot plugin;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public DiscordChannelNameResolver(NeoBot plugin) {
        this.plugin = plugin;
    }

    public String resolve(String channelId) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(channelId);
        if (cached != null && now < cached.expiresAtMillis) return cached.value;
        String resolved = fetch(channelId);
        if (resolved != null) {
            cache.put(channelId, new CacheEntry(resolved, now + POSITIVE_TTL_MILLIS));
            return resolved;
        }
        cache.put(channelId, new CacheEntry(channelId, now + NEGATIVE_TTL_MILLIS));
        return channelId;
    }

    private String fetch(String channelId) {
        String apiBase = plugin.getBotProvider().getDiscordApiBase();
        String token = plugin.getBotProvider().getDiscordToken();
        if (apiBase == null || token == null || token.trim().isEmpty()) return null;
        try {
            HttpResult result = HttpBuilder.builder(apiBase + "/channels/" + channelId).get()
                    .header("Authorization", "Bot " + token)
                    .timeout(LOOKUP_TIMEOUT_MILLIS)
                    .connect();
            if (result.getStatusCode() != 200 || result.getResponseContent() == null) return null;
            String name = new JSONObject(result.getResponseContent()).optString("name", "");
            return name.trim().isEmpty() ? null : name;
        } catch (Exception error) {
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
