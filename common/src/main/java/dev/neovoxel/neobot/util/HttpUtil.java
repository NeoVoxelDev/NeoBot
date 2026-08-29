package dev.neovoxel.neobot.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HttpUtil {

    public static String get(String urlString, Map<String, String> headers, boolean needGithubProxy) throws IOException {
        return get(urlString, headers, needGithubProxy, null);
    }

    public static String get(String urlString, Map<String, String> headers, boolean needGithubProxy, String proxyBase) throws IOException {
        IOException proxyFailure = null;
        if (needGithubProxy) {
            try {
                return getDirect(githubProxy(urlString, proxyBase), headers);
            } catch (IOException error) {
                proxyFailure = error;
                // Public GitHub proxies can reject API traffic. Fall back to
                // the original URL rather than breaking status/repositories.
            }
        }
        try {
            return getDirect(urlString, headers);
        } catch (IOException directFailure) {
            if (proxyFailure != null) {
                throw new IOException("GitHub proxy request failed (" + proxyFailure.getMessage() + "); direct fallback failed (" + directFailure.getMessage() + ")", directFailure);
            }
            throw directFailure;
        }
    }

    private static String getDirect(String urlString, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "NeoBot/0.2");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            StringBuilder response = new StringBuilder();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } else {
            throw new IOException("HTTP " + responseCode + " " + connection.getResponseMessage() + " from " + url.getHost());
        }
    }

    public static void download(String urlString, File file, Map<String, String> headers, boolean needGithubProxy) throws IOException {
        download(urlString, file, headers, needGithubProxy, null);
    }

    public static void download(String urlString, File file, Map<String, String> headers, boolean needGithubProxy, String proxyBase) throws IOException {
        if (needGithubProxy) {
            try { downloadDirect(githubProxy(urlString, proxyBase), file, headers); return; }
            catch (IOException ignored) { }
        }
        downloadDirect(urlString, file, headers);
    }

    private static void downloadDirect(String urlString, File file, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException(connection.getResponseMessage());
        }
        try (InputStream in = connection.getInputStream();
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String githubProxy(String url) { return githubProxy(url, null); }

    static String githubProxy(String url, String configuredBase) {
        if (configuredBase == null || configuredBase.trim().isEmpty()) return url;
        List<String> domain = Arrays.asList("https://github.com/", "https://raw.githubusercontent.com/", "https://gist.githubusercontent.com/", "https://api.github.com/");
        for (String s : domain) {
            if (url.startsWith(s)) {
                return configuredBase.replaceAll("/+$", "") + "/" + url;
            }
        }
        return url;
    }

    public static String getLatestVersion(boolean needGithubProxy) throws IOException {
        return getLatestVersion(needGithubProxy, null);
    }

    public static String getLatestVersion(boolean needGithubProxy, String proxyBase) throws IOException {
        String content = get("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases",
                new HashMap<>(), needGithubProxy, proxyBase);
        JSONArray jsonArray = new JSONArray(content);
        return jsonArray.getJSONObject(0).getString("name");
    }

    /** Full JSON of the newest GitHub release (name, assets, etc.), for callers that need more than
     *  just the version name (e.g. locating a downloadable .jar asset for auto-update). */
    public static JSONObject getLatestReleaseJson(boolean needGithubProxy, String proxyBase) throws IOException {
        String content = get("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases",
                new HashMap<>(), needGithubProxy, proxyBase);
        return new JSONArray(content).getJSONObject(0);
    }

    static String proxyUrlForTest(String url) { return githubProxy(url); }
    static String proxyUrlForTest(String url, String proxyBase) { return githubProxy(url, proxyBase); }

    public static String getLatestCommit(boolean needGithubProxy) throws IOException {
        return getLatestCommit(needGithubProxy, null);
    }

    public static String getLatestCommit(boolean needGithubProxy, String proxyBase) throws IOException {
        String content = get("https://api.github.com/repos/NeoVoxelDev/NeoBot/commits",
                new HashMap<>(), needGithubProxy, proxyBase);
        JSONArray jsonArray = new JSONArray(content);
        return jsonArray.getJSONObject(0).getString("sha").substring(0, 7);
    }

    /** Measures how long a HEAD request against this mirror, rewriting a real GitHub API URL the way
     *  auto-update requests will, takes to come back. Returns -1 if the mirror errors out, times out,
     *  or is otherwise unusable, rather than throwing, so callers can rank many mirrors without
     *  per-mirror try/catch. */
    public static long probeMirrorLatencyMillis(String mirrorBase, int timeoutMs) {
        try {
            String testUrl = githubProxy("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases", mirrorBase);
            long start = System.currentTimeMillis();
            URL url = new URL(testUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "NeoBot/0.2");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            int code = connection.getResponseCode();
            connection.disconnect();
            if (code >= 200 && code < 500) return System.currentTimeMillis() - start;
            return -1;
        } catch (IOException error) {
            return -1;
        }
    }

    /** Pure selection logic (no network), kept separate from probeMirrorLatencyMillis so it can be
     *  unit-tested with a fake prober instead of real HTTP calls. */
    static String selectFastest(List<String> mirrors, java.util.function.ToLongFunction<String> prober) {
        String best = null;
        long bestLatency = Long.MAX_VALUE;
        for (String mirror : mirrors) {
            if (mirror == null || mirror.trim().isEmpty()) continue;
            long latency = prober.applyAsLong(mirror);
            if (latency >= 0 && latency < bestLatency) {
                bestLatency = latency;
                best = mirror;
            }
        }
        return best;
    }

    /** The fastest reachable mirror among the configured ones, or null when none respond within
     *  timeoutMs. Callers should fall back to a direct connection (or the single legacy
     *  repository.github-proxy-url) when this returns null. */
    public static String pickBestMirror(List<String> mirrors, int timeoutMs) {
        return selectFastest(mirrors, mirror -> probeMirrorLatencyMillis(mirror, timeoutMs));
    }
}
