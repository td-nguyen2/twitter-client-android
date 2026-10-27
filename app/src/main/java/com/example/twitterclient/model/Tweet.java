package com.example.twitterclient.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Tweet {
    private final long id;
    private final String displayName;
    private final String username;
    private final String text;
    private final String mediaUrl;
    private int likeCount;
    private boolean likedByMe;

    // Name shown in the "X reposted" line above the tweet (only set on the Reposts tab)
    private String repostedBy;

    public Tweet(String displayName, String username, String text) {
        this(0L, displayName, username, text);
    }

    public Tweet(long id, String displayName, String username, String text) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.text = text;
        this.mediaUrl = null;
    }

    // Builds a tweet straight from the server JSON (timeline, profile, create post)
    public Tweet(JSONObject json) throws JSONException {
        this.id = json.optLong("id", 0L);
        this.username = json.optString("username", "");
        this.displayName = json.optString("displayName", this.username);
        this.text = json.optString("content", json.optString("text", ""));
        this.mediaUrl = json.isNull("mediaUrl")
                ? null
                : json.optString("mediaUrl", "");
        this.likeCount = json.optInt("likeCount", 0);

        // The server sends likedByMe as 0/1 in post lists and as true/false
        // in the like response, so accept both.
        Object liked = json.opt("likedByMe");
        this.likedByMe = (liked instanceof Boolean && (Boolean) liked)
                || (liked instanceof Number && ((Number) liked).intValue() != 0);
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
    public String getMediaUrl() { return mediaUrl; }
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

    public String getRepostedBy() {
        return repostedBy;
    }

    public void setRepostedBy(String repostedBy) {
        this.repostedBy = repostedBy;
    }
}
