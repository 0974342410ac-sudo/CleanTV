package com.gathin.karaoke;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads YouTube's own search results, without an API key: the results page (www.youtube.com/results) carries them
 * as JSON in "var ytInitialData = {...};", and "more results" (youtubei/v1/search with a continuation token) answers
 * with the same renderers. Videos are found by walking the whole tree for "videoRenderer" (and the newer
 * "lockupViewModel"), so a reshuffled page layout still parses; a renamed renderer does not (see README).
 */
final class SearchParser {
    private SearchParser() { }

    /** One page of results and what is needed to fetch the next one (either may be null). */
    static final class Page {
        final List<Song> songs;
        final String continuation, clientVersion;

        Page(List<Song> songs, String continuation, String clientVersion) {
            this.songs = songs;
            this.continuation = continuation;
            this.clientVersion = clientVersion;
        }
    }

    private static final String[] MARKERS = {"var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = "};
    private static final Pattern CLIENT_VERSION = Pattern.compile("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern TIME =Pattern.compile("\\d+(:\\d{2}){1,2}");

    /** What is typed or said, with "karaoke" added unless it is already there. */
    static String query(String typed) {
        String q = typed == null ? "" : typed.trim().replaceAll("\\s+", " ");
        if (q.isEmpty()) return "";
        return fold(q).contains("karaoke") ? q : q + " karaoke";
    }

    /** Lower case without Vietnamese marks, so "Karaôkê" counts as "karaoke". */
    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }

    /** Whether name holds what is typed, without marks, case or spaces: "son tung" finds "Sơn Tùng M-TP". */
    static boolean matchesName(String name, String typed) {
        return compact(name).contains(compact(typed));
    }

    /** The results page (HTML). Throws when the page has no ytInitialData (a consent page or a block page). */
    static Page parseHtml(String html) throws JSONException {
        Matcher m = CLIENT_VERSION.matcher(html);
        return parse(initialData(html), m.find() ? m.group(1) : null);
    }

    private static JSONObject initialData(String html) throws JSONException {
        for (String marker : MARKERS) {
            String json = extractObject(html, marker);
            if (json != null) return new JSONObject(json);
        }
        throw new JSONException("no ytInitialData in the page");
    }

    /**
     * The singer's photo from a channel search for their name, in a size fit for a tile; null when no channel has
     * a picture. The first channel is often someone else's (a fan, a namesake, "Ha Le Quyen" for "Lệ Quyên"), so
     * each is scored: YouTube marks it an artist (+5) or verified (+1), its name is the singer's or "<singer>
     * Official" (+4), "<singer> - Chủ đề" (+2). The best one wins, the earlier one on a tie.
     */
    static String parseChannelAvatar(String html, String singer) throws JSONException {
        List<JSONObject> channels = new ArrayList<>();
        findAll(initialData(html), "channelRenderer", channels);
        String want = compact(singer);
        JSONObject best = null;
        int bestScore = -1;
        for (JSONObject c : channels) {
            String name = compact(text(c.optJSONObject("title")));
            int score = 0;
            if (!want.isEmpty() && (name.equals(want) || name.equals(want + "official"))) score += 4;
            else if (!want.isEmpty() && name.equals(want + "chude")) score += 2;
            String badges = String.valueOf(c.optJSONArray("ownerBadges"));
            if (badges.contains("BADGE_STYLE_TYPE_VERIFIED_ARTIST")) score += 5;
            else if (badges.contains("BADGE_STYLE_TYPE_VERIFIED")) score += 1;
            if (score > bestScore && avatarUrl(c) != null) {
                best = c;
                bestScore = score;
            }
        }
        return best == null ? null : avatarUrl(best);
    }

    private static String avatarUrl(JSONObject channel) {
        JSONArray thumbs = path(channel, "thumbnail").optJSONArray("thumbnails");
        JSONObject biggest = thumbs == null ? null : thumbs.optJSONObject(thumbs.length() - 1);
        String url = biggest == null ? "" : biggest.optString("url");
        if (url.isEmpty()) return null;
        if (url.startsWith("//")) url = "https:" + url;
        // "=s88-c-k-..." asks for 88 px; the same address serves any size
        return url.replaceFirst("=s\\d+-", "=s240-");
    }

    /** "Lệ Quyên - Chủ đề" -> "lequyenchude": names compared without marks, case, spaces or punctuation. */
    private static String compact(String s) {
        return fold(s).replaceAll("[^a-z0-9]+", "");
    }

    private static void findAll(Object node, String key, List<JSONObject> out) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONObject hit = o.optJSONObject(key);
            if (hit != null) {
                out.add(hit);
                return;
            }
            for (Iterator<String> keys = o.keys(); keys.hasNext(); ) findAll(o.opt(keys.next()), key, out);
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) findAll(a.opt(i), key, out);
        }
    }

    /** The answer to a youtubei/v1/search continuation request (JSON). */
    static Page parseJson(String json, String clientVersion) throws JSONException {
        return parse(new JSONObject(json), clientVersion);
    }

    private static Page parse(JSONObject root, String clientVersion) {
        Map<String, Song> songs = new LinkedHashMap<>();
        String[] continuation = {null};
        walk(root, songs, continuation);
        return new Page(new ArrayList<>(songs.values()), continuation[0], clientVersion);
    }

    private static void walk(Object node, Map<String, Song> songs, String[] continuation) {
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) walk(a.opt(i), songs, continuation);
        } else if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            for (Iterator<String> keys = o.keys(); keys.hasNext(); ) {
                String key = keys.next();
                Object value = o.opt(key);
                if (!(value instanceof JSONObject)) {
                    walk(value, songs, continuation);
                    continue;
                }
                JSONObject v = (JSONObject) value;
                Song song = null;
                switch (key) {
                    case "videoRenderer": song = fromVideoRenderer(v); break;
                    case "lockupViewModel": song = fromLockup(v); break;
                    case "continuationItemRenderer":
                        String token = path(v, "continuationEndpoint", "continuationCommand").optString("token", null);
                        if (token != null && continuation[0] == null) continuation[0] = token;
                        continue;
                    default:
                        walk(v, songs, continuation);
                        continue;
                }
                // The id goes into the player page's script: only real video ids pass
                if (song != null && VIDEO_ID.matcher(song.id).matches() && !songs.containsKey(song.id)) {
                    songs.put(song.id, song);
                }
            }
        }
    }

    /** Classic renderer. A video without a length is live or upcoming: no use for karaoke, skipped. */
    static Song fromVideoRenderer(JSONObject v) {
        String id = v.optString("videoId", null);
        String duration = text(v.optJSONObject("lengthText"));
        if (id == null || duration.isEmpty()) return null;
        String channel = text(v.optJSONObject("ownerText"));
        if (channel.isEmpty()) channel = text(v.optJSONObject("longBylineText"));
        String views = text(v.optJSONObject("shortViewCountText"));
        if (views.isEmpty()) views = text(v.optJSONObject("viewCountText"));
        return new Song(id, text(v.optJSONObject("title")), channel, duration, views);
    }

    /** Newer "lockup" layout YouTube is moving lists to; title, channel and length sit in other places. */
    static Song fromLockup(JSONObject v) {
        if (!"LOCKUP_CONTENT_TYPE_VIDEO".equals(v.optString("contentType"))) return null;
        String id = v.optString("contentId", null);
        if (id == null) return null;
        JSONObject meta = path(v, "metadata", "lockupMetadataViewModel");
        String title = path(meta, "title").optString("content");
        List<String> parts = new ArrayList<>();
        JSONArray rows = path(meta, "metadata", "contentMetadataViewModel").optJSONArray("metadataRows");
        for (int r = 0; rows != null && r < rows.length(); r++) {
            JSONArray ps = rows.optJSONObject(r) == null ? null : rows.optJSONObject(r).optJSONArray("metadataParts");
            for (int p = 0; ps != null && p < ps.length(); p++) {
                JSONObject part = ps.optJSONObject(p);
                String t = part == null ? "" : path(part, "text").optString("content");
                if (!t.isEmpty()) parts.add(t);
            }
        }
        String duration = findTime(v.optJSONObject("contentImage"));
        if (duration == null) return null;
        return new Song(id, title, parts.isEmpty() ? "" : parts.get(0), duration, parts.size() > 1 ? parts.get(1) : "");
    }

    /** First badge text that looks like a length ("3:41", "1:02:03") under a lockup's image. */
    private static String findTime(Object node) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONObject badge = o.optJSONObject("thumbnailBadgeViewModel");
            if (badge != null && TIME.matcher(badge.optString("text")).matches()) return badge.optString("text");
            for (Iterator<String> keys = o.keys(); keys.hasNext(); ) {
                String t = findTime(o.opt(keys.next()));
                if (t != null) return t;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                String t = findTime(a.opt(i));
                if (t != null) return t;
            }
        }
        return null;
    }

    /** {"simpleText": ...} or {"runs": [{"text": ...}, ...]}. */
    static String text(JSONObject t) {
        if (t == null) return "";
        if (t.has("simpleText")) return t.optString("simpleText").trim();
        JSONArray runs = t.optJSONArray("runs");
        if (runs == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run != null) sb.append(run.optString("text"));
        }
        return sb.toString().trim();
    }

    private static JSONObject path(JSONObject o, String... keys) {
        for (String k : keys) {
            o = o == null ? null : o.optJSONObject(k);
        }
        return o == null ? new JSONObject() : o;
    }

    /**
     * The JSON object that starts right after marker: braces are counted outside strings, so a title holding
     * "};" or "</script>" does not end it early.
     */
    static String extractObject(String text, String marker) {
        int at = text.indexOf(marker);
        if (at < 0) return null;
        int start = text.indexOf('{', at + marker.length());
        if (start < 0 || text.substring(at + marker.length(), start).trim().length() > 0) return null;
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return text.substring(start, i + 1);
            }
        }
        return null;
    }
}
