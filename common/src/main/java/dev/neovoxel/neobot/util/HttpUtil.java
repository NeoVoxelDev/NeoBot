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
        if (needGithubProxy) urlString = githubProxy(urlString);
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
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
            throw new IOException(connection.getResponseMessage());
        }
    }

    public static void download(String urlString, File file, Map<String, String> headers, boolean needGithubProxy) throws IOException {
        if (needGithubProxy) urlString = githubProxy(urlString);
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

    private static String githubProxy(String url) {
        List<String> domain = Arrays.asList("https://github.com/", "https://raw.githubusercontent.com/", "https://gist.githubusercontent.com/", "https://api.github.com/");
        for (String s : domain) {
            if (url.startsWith(s)) {
                return ("https://gh-proxy.org/" + url);
            }
        }
        return url;
    }

    public static String getLatestVersion(boolean needGithubProxy) throws IOException {
        String content = get("https://api.github.com/repos/NeoVoxelDev/NeoBot/releases",
                new HashMap<>(), needGithubProxy);
        JSONArray jsonArray = new JSONArray(content);
        return jsonArray.getJSONObject(0).getString("name");
    }

    public static String getLatestCommit(boolean needGithubProxy) throws IOException {
        String content = get("https://api.github.com/repos/NeoVoxelDev/NeoBot/commits",
                new HashMap<>(), needGithubProxy);
        JSONArray jsonArray = new JSONArray(content);
        return jsonArray.getJSONObject(0).getString("sha").substring(0, 7);
    }
}
