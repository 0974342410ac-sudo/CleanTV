package com.gathin.karaoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

/** The phone page's PIN lock, form reading and JSON. */
public class RemoteApiTest {

    @Test
    public void pinLocksAfterFiveWrongTries() {
        RemoteApi.PinGate gate = new RemoteApi.PinGate();
        for (int i = 0; i < 4; i++) assertEquals("wrong", gate.check("0000", "1234", 1000));
        assertEquals("ok", gate.check("1234", "1234", 1000));       // a right PIN resets the count
        for (int i = 0; i < 5; i++) assertEquals("wrong", gate.check("0000", "1234", 2000));
        // locked now, even for the right PIN
        assertEquals("locked:60", gate.check("1234", "1234", 2000));
        assertEquals("locked:1", gate.check("1234", "1234", 2000 + RemoteApi.PinGate.LOCK_MS - 500));
        assertEquals("ok", gate.check("1234", "1234", 2000 + RemoteApi.PinGate.LOCK_MS));
        assertEquals("wrong", gate.check(null, "1234", 2000 + RemoteApi.PinGate.LOCK_MS));
    }

    @Test
    public void readsForms() {
        Map<String, String> f = RemoteApi.form("q=%C4%91%C3%AAm+l%E1%BA%A1nh&index=2&broken&=x&bad=%zz");
        assertEquals("đêm lạnh", f.get("q"));
        assertEquals("2", f.get("index"));
        assertEquals(2, RemoteApi.index(f));
        assertEquals(2, f.size());
        assertEquals(-1, RemoteApi.index(RemoteApi.form("index=x")));
        assertTrue(RemoteApi.form(null).isEmpty());
    }

    @Test
    public void songFromPhoneNeedsARealVideoId() throws Exception {
        Song s = RemoteApi.song(RemoteApi.form("id=jMrn3bZvrCg&title=%C4%90%C3%AAm+L%E1%BA%A1nh&duration=3%3A41"));
        assertEquals("jMrn3bZvrCg", s.id);
        assertEquals("Đêm Lạnh", s.title);
        assertEquals("3:41", s.duration);
        assertEquals("", s.channel);
        assertNull(RemoteApi.song(RemoteApi.form("id=bad'id&title=x")));
        assertNull(RemoteApi.song(RemoteApi.form("title=x")));
        // over-long fields are cut, not refused
        StringBuilder longTitle = new StringBuilder();
        for (int i = 0; i < 300; i++) longTitle.append('a');
        assertEquals(200, RemoteApi.song(RemoteApi.form("id=jMrn3bZvrCg&title=" + longTitle)).title.length());
    }

    @Test
    public void pageJson() throws Exception {
        Song a = new Song("jMrn3bZvrCg", "Đêm Lạnh", "Cara O-Kể", "3:41", "125 lượt xem");
        JSONObject o = RemoteApi.page(new SearchParser.Page(Arrays.asList(a), "TOKEN", "2.1"));
        assertTrue(o.getBoolean("ok"));
        assertEquals("TOKEN", o.getString("more"));
        assertEquals("2.1", o.getString("version"));
        JSONObject song = o.getJSONArray("songs").getJSONObject(0);
        assertEquals("Đêm Lạnh", song.getString("title"));
        assertEquals("https://i.ytimg.com/vi/jMrn3bZvrCg/mqdefault.jpg", song.getString("thumb"));
        JSONObject last = RemoteApi.page(new SearchParser.Page(Arrays.asList(a), null, null));
        assertFalse(last.has("more"));
    }
}
