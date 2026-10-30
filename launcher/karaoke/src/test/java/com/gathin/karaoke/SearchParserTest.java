package com.gathin.karaoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import org.json.JSONException;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** The YouTube results reader, on pages shaped like the real ones (src/test/resources). */
public class SearchParserTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = SearchParserTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void addsKaraokeToTheQuery() {
        assertEquals("Đêm lạnh karaoke", SearchParser.query("  Đêm   lạnh "));
        assertEquals("dem lanh KARAOKE beat", SearchParser.query("dem lanh KARAOKE beat"));
        assertEquals("Karaôkê đêm lạnh", SearchParser.query("Karaôkê đêm lạnh"));
        assertEquals("", SearchParser.query("   "));
        assertEquals("", SearchParser.query(null));
    }

    @Test
    public void findsASingerByTypedName() {
        assertTrue(SearchParser.matchesName("Sơn Tùng M-TP", "son tung"));
        assertTrue(SearchParser.matchesName("Đàm Vĩnh Hưng", "DAM VINH"));
        assertTrue(SearchParser.matchesName("Mỹ Tâm", ""));
        assertTrue(!SearchParser.matchesName("Mỹ Tâm", "son tung"));
    }

    @Test
    public void failedSearchSaysWhy() {
        assertEquals(R.string.error_blocked, YouTubeSearch.errorText(new java.io.IOException("HTTP 429")));
        assertEquals(R.string.error_blocked, YouTubeSearch.errorText(new java.io.IOException("HTTP 403")));
        assertEquals(R.string.error_network, YouTubeSearch.errorText(new java.io.IOException("timeout")));
        assertEquals(R.string.error_page, YouTubeSearch.errorText(new JSONException("no ytInitialData")));
    }

    @Test
    public void readsVideosFromTheResultsPage() throws Exception {
        SearchParser.Page page = SearchParser.parseHtml(resource("results.html"));
        // live stream, Shorts, playlist, duplicate and a bad id are left out
        assertEquals(3, page.songs.size());

        Song first = page.songs.get(0);
        assertEquals("jMrn3bZvrCg", first.id);
        assertEquals("KARAOKE | Đêm Lạnh - Hiền Thục Beat", first.title);
        assertEquals("Cara O-Kể", first.channel);
        assertEquals("3:41", first.duration);
        assertEquals("125 lượt xem", first.views);
        assertEquals("https://i.ytimg.com/vi/jMrn3bZvrCg/mqdefault.jpg", first.thumbnail());

        // title runs are joined; "};</script>" inside a title does not end the JSON early
        Song second = page.songs.get(1);
        assertEquals("KD_ltylyU_s", second.id);
        assertEquals("Đêm Lạnh Karaoke Tone Nữ };</script> \"nhạc sống\"", second.title);
        assertEquals("1:02:03", second.duration);

        Song lockup = page.songs.get(2);
        assertEquals("MmdxrZRyOWs", lockup.id);
        assertEquals("Đêm Lạnh Karaoke Song Ca", lockup.title);
        assertEquals("Karaoke Việt", lockup.channel);
        assertEquals("45 N lượt xem", lockup.views);
        assertEquals("4:05", lockup.duration);

        assertEquals("EqgDEhBkZW0gbGFuaCBrYXJhb2tl", page.continuation);
        assertEquals("2.20261027.01.00", page.clientVersion);
    }

    @Test
    public void readsTheNextPage() throws Exception {
        SearchParser.Page page = SearchParser.parseJson(resource("continuation.json"), "2.1");
        assertEquals(2, page.songs.size());
        assertEquals("rzl7ztUZD6w", page.songs.get(0).id);
        assertEquals("Beat Chuẩn", page.songs.get(0).channel);
        assertEquals("H2ye8Q2zBiI", page.songs.get(1).id);
        assertEquals("Đêm Lạnh Karaoke Beat Gốc", page.songs.get(1).title);
        assertEquals("", page.songs.get(1).views);
        assertEquals("NEXT_PAGE_TOKEN", page.continuation);
        assertEquals("2.1", page.clientVersion);
    }

    @Test
    public void singerPhotoIsTheSingersOwnChannel() throws Exception {
        String page = resource("channels.html");
        // first channel is a namesake ("Ha Le Quyen"): the artist channel named like the singer wins
        assertEquals("https://yt3.googleusercontent.com/ytc/singer=s240-c-k-c0x00ffffff-no-rj-mo",
                SearchParser.parseChannelAvatar(page, "Lệ Quyên"));
        // nobody named like that: the verified artist still beats a merely verified channel
        assertEquals("https://yt3.googleusercontent.com/ytc/singer=s240-c-k-c0x00ffffff-no-rj-mo",
                SearchParser.parseChannelAvatar(page, "Someone Else"));
        // a page without channels: no photo, the letter tile stays
        assertNull(SearchParser.parseChannelAvatar(resource("results.html"), "Lệ Quyên"));
    }

    @Test
    public void singerInitials() {
        assertEquals("ĐH", KaraokeActivity.initials("Đàm Vĩnh Hưng"));
        assertEquals("E", KaraokeActivity.initials("Erik"));
        assertEquals("ST", KaraokeActivity.initials("Sơn Tùng M-TP"));
        assertEquals("MS", KaraokeActivity.initials("Mr. Siro"));
        assertEquals("V", KaraokeActivity.initials("Vũ."));
    }

    @Test
    public void otherMarkerSpelling() throws Exception {
        String html = "<script>window[\"ytInitialData\"] = {\"a\":[{\"videoRenderer\":{\"videoId\":\"abcdefghijk\","
                + "\"title\":{\"simpleText\":\"x\"},\"lengthText\":{\"simpleText\":\"2:00\"}}}]};</script>";
        SearchParser.Page page = SearchParser.parseHtml(html);
        assertEquals(1, page.songs.size());
        assertNull(page.continuation);
        assertNull(page.clientVersion);
    }

    @Test
    public void consentOrBlockPageIsAnError() {
        try {
            SearchParser.parseHtml("<html><body>Before you continue to YouTube</body></html>");
            fail();
        } catch (JSONException expected) {
        }
    }

    @Test
    public void extractObjectCountsBracesOutsideStrings() {
        String text = "x = {\"a\":\"}\\\"{\",\"b\":{\"c\":1}}; tail}";
        assertEquals("{\"a\":\"}\\\"{\",\"b\":{\"c\":1}}", SearchParser.extractObject(text, "x = "));
        assertNull(SearchParser.extractObject("x = {\"open\":", "x = "));
        assertNull(SearchParser.extractObject("nothing here", "x = "));
    }

    /** gradlew :karaoke:testReleaseUnitTest -PrealPage=E:\path\to\saved-results.html : a page saved from YouTube. */
    @Test
    public void realPageWhenGiven() throws Exception {
        String path = System.getProperty("karaoke.realPage", "");
        assumeTrue(!path.isEmpty());
        SearchParser.Page page = SearchParser.parseHtml(new String(Files.readAllBytes(Paths.get(path)),
                StandardCharsets.UTF_8));
        System.out.println("real page: " + page.songs.size() + " songs, more=" + (page.continuation != null));
        for (Song s : page.songs) System.out.println("  " + s.id + " [" + s.duration + "] " + s.title + " / " + s.channel);
        assertTrue(page.songs.size() >= 5);
        assertTrue(page.continuation != null);
    }
}
