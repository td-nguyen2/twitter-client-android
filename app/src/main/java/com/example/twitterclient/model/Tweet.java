package com.example.twitterclient.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/** A post as shown in the timeline, on a profile and on the Post screen. */
public class Tweet implements Serializable {
    private final long id;
    private final String displayName;
    private final String username;
    private final String text;
    private final String mediaUrl;
    private final String profileImageUrl;
    private final String createdAt;

    private int likeCount;
    private boolean likedByMe;
    private int replyCount;
    private int repostCount;
    private int viewCount;
    private boolean repostedByMe;

    // "X reposted" line above the tweet. Set when this row is a repost.
    private String repostedBy;
    private String repostedByUsername;

    public Tweet(String displayName, String username, String text) {
        this(0L, displayName, username, text);
    }

    public Tweet(long id, String displayName, String username, String text) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.text = text;
        this.mediaUrl = null;
        this.profileImageUrl = null;
        this.createdAt = null;
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
        this.profileImageUrl = json.isNull("profileImageUrl")
                ? null
                : json.optString("profileImageUrl", "");

        String created = json.optString("createdAt", json.optString("created_at", ""));
        this.createdAt = created.isEmpty() ? null : created;

        this.likeCount = json.optInt("likeCount", 0);
        this.replyCount = json.optInt("replyCount", 0);
        this.repostCount = json.optInt("repostCount", 0);
        this.viewCount = json.optInt("viewCount", 0);

        // The server sends the flags as 0/1 in lists and as true/false in
        // action responses, so accept both.
        this.likedByMe = readFlag(json.opt("likedByMe"));
        this.repostedByMe = readFlag(json.opt("repostedByMe"));

        // Only repost rows carry these two fields
        if (!json.isNull("repostedBy")) {
            String by = json.optString("repostedBy", "");
            this.repostedBy = by.isEmpty() ? null : by;
        }
        if (!json.isNull("repostedByUsername")) {
            String by = json.optString("repostedByUsername", "");
            this.repostedByUsername = by.isEmpty() ? null : by;
        }
    }

    private static boolean readFlag(Object value) {
        return (value instanceof Boolean && (Boolean) value)
                || (value instanceof Number && ((Number) value).intValue() != 0);
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

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getCreatedAt() {
        return createdAt;
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

    public int getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(int replyCount) {
        this.replyCount = Math.max(0, replyCount);
    }

    public int getRepostCount() {
        return repostCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = Math.max(0, viewCount);
    }

    public void setRepostCount(int repostCount) {
        this.repostCount = Math.max(0, repostCount);
    }

    public boolean isRepostedByMe() {
        return repostedByMe;
    }

    public void setRepostedByMe(boolean repostedByMe) {
        this.repostedByMe = repostedByMe;
    }

    /** Copies the counters and my like/repost flags from another row of the same post. */
    public void copyStatsFrom(Tweet other) {
        this.likeCount = other.likeCount;
        this.likedByMe = other.likedByMe;
        this.replyCount = other.replyCount;
        this.repostCount = other.repostCount;
        this.viewCount = other.viewCount;
        this.repostedByMe = other.repostedByMe;
    }

    public String getRepostedBy() {
        return repostedBy;
    }

    public void setRepostedBy(String repostedBy) {
        this.repostedBy = repostedBy;
    }

    public String getRepostedByUsername() {
        return repostedByUsername;
    }
}
