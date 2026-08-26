package dev.neovoxel.neobot.bot.discord;

public enum DiscordProxyMode {
    OFFICIAL,
    PROXY_HTTP,
    PROXY_HTTPS,
    LOCAL,
    CF_WORKER;

    public static DiscordProxyMode fromConfig(String value) {
        if (value == null || value.trim().isEmpty() || "official".equalsIgnoreCase(value)) return OFFICIAL;
        if ("proxy_http".equalsIgnoreCase(value)) return PROXY_HTTP;
        if ("proxy_https".equalsIgnoreCase(value)) return PROXY_HTTPS;
        if ("local".equalsIgnoreCase(value)) return LOCAL;
        if ("cf_worker".equalsIgnoreCase(value) || "cf-worker".equalsIgnoreCase(value)) return CF_WORKER;
        throw new IllegalArgumentException("Unsupported Discord proxy mode: " + value);
    }
}
