package com.gathin.karaoke;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Fetches YouTube search results the way a desktop browser does: no API key, no account. Blocking: call it off the
 * main thread. YouTube can change the page or refuse requests it takes for a bot (see README for the risks).
 */
final class YouTubeSearch {
    private YouTubeSearch() { }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    /** Used for "more results" when the page did not give its own client version. */
    private static final String FALLBACK_CLIENT_VERSION = "2.20261027.01.00";
    /** Search filters (the results page's "sp"): videos only, so no Shorts, channels or playlists. */
    static final String VIDEOS_ONLY = "EgIQAQ%3D%3D";
    /** Videos uploaded this month: with "karaoke" it gives the new karaoke tracks people watch now. */
    static final String VIDEOS_THIS_MONTH = "EgQIBBAB";
    /** Channels only: a singer's own channel comes first, its picture is the singer's photo. */
    private static final String CHANNELS_ONLY = "EgIQAg%3D%3D";

    /** query is searched as given ("karaoke" already added by the caller, see SearchParser.query). */
    static SearchParser.Page search(String query, String filter) throws IOException, JSONException {
        return SearchParser.parseHtml(request(resultsUrl(query, filter), null));
    }

    /** Photo of the singer's channel (SearchParser.parseChannelAvatar picks it), or null. */
    static String channelAvatar(String name) throws IOException, JSONException {
        return SearchParser.parseChannelAvatar(request(resultsUrl(name, CHANNELS_ONLY), null), name);
    }

    /** The message to show for a failed search: refused by YouTube, no network, or a page we cannot read. */
    static int errorText(Exception e) {
        if (e instanceof IOException) {
            String m = String.valueOf(e.getMessage());
            return m.startsWith("HTTP 429") || m.startsWith("HTTP 403") ? R.string.error_blocked : R.string.error_network;
        }
        return R.string.error_page;
    }

    private static String resultsUrl(String query, String filter) throws IOException {
        return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
                + "&sp=" + filter + "&hl=vi&gl=VN";
    }

    static SearchParser.Page more(SearchParser.Page page) throws IOException, JSONException {
        String version = page.clientVersion != null ? page.clientVersion : FALLBACK_CLIENT_VERSION;
        JSONObject client = new JSONObject().put("clientName", "WEB").put("clientVersion", version)
                .put("hl", "vi").put("gl", "VN");
        JSONObject body = new JSONObject().put("context", new JSONObject().put("client", client))
                .put("continuation", page.continuation);
        String json = request("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", body.toString());
        return SearchParser.parseJson(json, version);
    }

    private static String request(String url, String postBody) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.5");
            // Skips the cookie-consent page YouTube shows in some countries instead of results
            c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
            if (postBody != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(postBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 20);
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                return buf.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }
}
