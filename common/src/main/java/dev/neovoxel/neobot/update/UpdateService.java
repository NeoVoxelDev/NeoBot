package dev.neovoxel.neobot.update;

import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.util.HttpUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/** Periodically checks GitHub for a newer NeoBot release, notifies connected platforms, and (when
 *  repository.auto-update.enabled) stages the new jar for the next restart. Every failure path here
 *  degrades to a log message rather than propagating, since this runs on a background scheduler tick
 *  and must never take the plugin down. */
public class UpdateService {
    private final NeoBot plugin;
    private final NewVersionNotifier notifier;

    public UpdateService(NeoBot plugin) {
        this(plugin, new NewVersionNotifier(plugin));
    }

    UpdateService(NeoBot plugin, NewVersionNotifier notifier) {
        this.plugin = plugin;
        this.notifier = notifier;
    }

    public void checkForUpdate() {
        try {
            boolean needGithubProxy = plugin.getGeneralConfig().getBoolean("repository.use-github-proxy");
            String githubProxy = plugin.getGeneralConfig().getString("repository.github-proxy-url");
            String currentVersion = plugin.getVersion();
            JSONObject release = HttpUtil.getLatestReleaseJson(needGithubProxy, githubProxy);
            String latestVersion = release.getString("name");
            if (!isNewer(currentVersion, latestVersion)) return;
            plugin.getNeoLogger().info("A new NeoBot version is available: " + latestVersion
                    + " (current: " + currentVersion + ")");
            notifier.notifyNewVersion(currentVersion, latestVersion);
            if (plugin.getGeneralConfig().getBoolean("repository.auto-update.enabled")) {
                downloadUpdate(release, latestVersion, needGithubProxy, githubProxy);
            }
        } catch (Exception error) {
            plugin.getNeoLogger().warn("Failed to check for updates: " + error.getMessage());
        }
    }

    private void downloadUpdate(JSONObject release, String latestVersion, boolean needGithubProxy, String legacyProxy) {
        try {
            String assetUrl = findJarAssetUrl(release);
            if (assetUrl == null) {
                plugin.getNeoLogger().warn("Auto-update enabled but release " + latestVersion + " has no downloadable .jar asset");
                return;
            }
            File staging = plugin.getUpdateStagingFile(latestVersion);
            if (staging == null) {
                plugin.getNeoLogger().warn("Auto-update enabled but this platform doesn't support staged updates");
                return;
            }
            int timeoutMs = plugin.getGeneralConfig().getInt("repository.auto-update.connect-timeout-ms");
            List<String> mirrors = plugin.getGeneralConfig().getStringArray("repository.auto-update.mirrors");
            String bestMirror = mirrors.isEmpty() ? null : HttpUtil.pickBestMirror(mirrors, timeoutMs);
            String proxyBase = bestMirror != null ? bestMirror : (needGithubProxy ? legacyProxy : null);
            File parent = staging.getParentFile();
            if (parent != null) parent.mkdirs();
            HttpUtil.download(assetUrl, staging, new HashMap<>(), proxyBase != null && !proxyBase.trim().isEmpty(), proxyBase);
            plugin.getNeoLogger().info("Downloaded NeoBot " + latestVersion + " to " + staging.getPath()
                    + "; it will be applied on next restart");
        } catch (Exception error) {
            plugin.getNeoLogger().warn("Auto-update download failed, staying on current version: " + error.getMessage());
        }
    }

    static String findJarAssetUrl(JSONObject release) {
        if (!release.has("assets")) return null;
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.optString("name", "").endsWith(".jar")) {
                String url = asset.optString("browser_download_url", "");
                return url.isEmpty() ? null : url;
            }
        }
        return null;
    }

    /** Numeric dotted-version comparison (e.g. "1.4.2" vs "v1.10.0"); falls back to plain inequality
     *  for tags that don't parse as dotted numbers, so unrecognized formats still trigger a notice
     *  rather than being silently ignored. */
    static boolean isNewer(String current, String latest) {
        if (current == null || latest == null) return false;
        String normalizedCurrent = normalize(current);
        String normalizedLatest = normalize(latest);
        int[] currentParts = parseParts(normalizedCurrent);
        int[] latestParts = parseParts(normalizedLatest);
        if (currentParts == null || latestParts == null) return !normalizedCurrent.equals(normalizedLatest);
        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int c = i < currentParts.length ? currentParts[i] : 0;
            int l = i < latestParts.length ? latestParts[i] : 0;
            if (l != c) return l > c;
        }
        return false;
    }

    private static String normalize(String version) {
        String trimmed = version.trim();
        return (trimmed.startsWith("v") || trimmed.startsWith("V")) ? trimmed.substring(1) : trimmed;
    }

    private static int[] parseParts(String version) {
        String[] segments = version.split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            if (!segments[i].matches("\\d+")) return null;
            parts[i] = Integer.parseInt(segments[i]);
        }
        return parts;
    }
}
