package com.example.twitterclient.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Tweet {
    private final long id;
    private final String displayName;
    private final String username;
    private final String text;
    private int likeCount;
    private boolean likedByMe;

    public Tweet(String displayName, String username, String text) {
        this(0L, displayName, username, text);
    }

    public Tweet(long id, String displayName, String username, String text) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.text = text;
    }

    // Used by ProfileFragment (parses a tweet straight from JSON)
    public Tweet(JSONObject json) throws JSONException {
        this.id = json.optLong("id", json.optLong("_id", 0L));
        this.username = json.optString("username", "");
        this.displayName = json.optString(
                "displayName",
                json.optString("display_name", this.username)
        );
        this.text = json.optString("text", json.optString("content", ""));
        this.likeCount = json.optInt("likeCount", json.optInt("like_count", 0));
        this.likedByMe = json.optBoolean(
                "likedByMe",
                json.optBoolean("liked_by_me", false)
        );
    }

    public long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public String getText() {
        return text;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public boolean isLikedByMe() {
        return likedByMe;
    }

    public void setLikedByMe(boolean likedByMe) {
        this.likedByMe = likedByMe;
    }
}