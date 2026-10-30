package com.gathin.karaoke;

import org.json.JSONException;
import org.json.JSONObject;

/** One YouTube video in the results or the queue. */
final class Song {
    final String id, title, channel, duration, views;

    Song(String id, String title, String channel, String duration, String views) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.channel = channel == null ? "" : channel;
        this.duration = duration == null ? "" : duration;
        this.views = views == null ? "" : views;
    }

    /** 320x180 thumbnail: a fixed address per video, smaller than the hq720 one in the results page. */
    String thumbnail() {
        return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("channel", channel)
                .put("duration", duration).put("views", views);
    }

    static Song fromJson(JSONObject o) {
        return new Song(o.optString("id"), o.optString("title"), o.optString("channel"),
                o.optString("duration"), o.optString("views"));
    }
}
