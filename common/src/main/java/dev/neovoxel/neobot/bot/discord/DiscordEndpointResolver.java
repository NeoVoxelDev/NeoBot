package dev.neovoxel.neobot.bot.discord;

import dev.neovoxel.nbapi.discord.client.DiscordGatewayClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class DiscordEndpointResolver {
    private DiscordEndpointResolver() {
    }

    public static DiscordEndpoints resolve(DiscordProxyMode mode, String url) {
        if (mode == DiscordProxyMode.OFFICIAL) {
            return new DiscordEndpoints(mode, DiscordGatewayClient.DEFAULT_API_BASE, DiscordGatewayClient.DEFAULT_GATEWAY);
        }
        if (mode == DiscordProxyMode.LOCAL) {
            throw new IllegalArgumentException("Legacy local mode requires its old endpoint fields");
        }
        URI base = normalizeBase(mode, url);
        String path = trimPath(base.getPath());
        String rootPath = stripKnownSuffix(path);
        URI api = replace(base, append(rootPath, "api/v10"), httpScheme(mode));
        URI gateway = replace(base, append(rootPath, "gateway"), wsScheme(mode));
        gateway = URI.create(gateway.toString() + "?v=10&encoding=json");
        return new DiscordEndpoints(mode, api, gateway);
    }

    public static DiscordEndpoints resolveLegacy(DiscordProxyMode mode, String apiBase, String gateway) {
        boolean secure = mode == DiscordProxyMode.CF_WORKER;
        URI api = normalizeLegacyApi(apiBase, secure);
        URI ws = normalizeLegacyGateway(gateway, secure);
        return new DiscordEndpoints(mode, api, ws);
    }

    private static URI normalizeBase(DiscordProxyMode mode, String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Discord proxy URL is required");
        String required = httpScheme(mode);
        String normalized = value.trim();
        if (!normalized.contains("://")) normalized = required + "://" + normalized;
        URI uri = URI.create(normalized);
        validateBase(uri, required);
        return uri;
    }

    private static void validateBase(URI uri, String requiredScheme) {
        if (!requiredScheme.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Discord proxy URL must use " + requiredScheme);
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) throw new IllegalArgumentException("Discord proxy URL must include a host");
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Discord proxy URL must not contain a query or fragment");
        }
    }

    private static String stripKnownSuffix(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/api/v10")) return path.substring(0, path.length() - 8);
        if (lower.endsWith("/gateway")) return path.substring(0, path.length() - 8);
        return path;
    }

    private static String append(String root, String suffix) {
        return (root.isEmpty() ? "" : root) + "/" + suffix;
    }

    private static String trimPath(String value) {
        if (value == null || value.isEmpty() || "/".equals(value)) return "";
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.startsWith("/") ? result : "/" + result;
    }

    private static URI replace(URI base, String path, String scheme) {
        try {
            return new URI(scheme, base.getUserInfo(), base.getHost(), base.getPort(), path, null, null);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Invalid Discord proxy URL", error);
        }
    }

    private static String httpScheme(DiscordProxyMode mode) {
        return mode == DiscordProxyMode.PROXY_HTTP ? "http" : "https";
    }

    private static String wsScheme(DiscordProxyMode mode) {
        return mode == DiscordProxyMode.PROXY_HTTP ? "ws" : "wss";
    }

    private static URI normalizeLegacyApi(String value, boolean secure) {
        String normalized = addScheme(value, secure ? "https" : "http");
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        URI uri = URI.create(normalized);
        if ((secure && !"https".equalsIgnoreCase(uri.getScheme())) || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Invalid legacy Discord API proxy URL");
        }
        return uri;
    }

    private static URI normalizeLegacyGateway(String value, boolean secure) {
        String normalized = addScheme(value, secure ? "wss" : "ws")
                .replaceFirst("(?i)^https://", "wss://").replaceFirst("(?i)^http://", "ws://");
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        URI uri = URI.create(normalized);
        if (secure && !"wss".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Invalid legacy Discord Gateway URL");
        return uri.getQuery() == null ? URI.create(uri + "?v=10&encoding=json") : uri;
    }

    private static String addScheme(String value, String scheme) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Legacy Discord proxy address is required");
        return value.contains("://") ? value.trim() : scheme + "://" + value.trim();
    }
}
