package com.example.twitterclient.model;

import org.json.JSONException;
import org.json.JSONObject;

/** One entry of GET /api/notifications. */
public class AppNotification {
    public static final String TYPE_LIKE = "LIKE";
    public static final String TYPE_FOLLOW = "FOLLOW";
    public static final String TYPE_REPLY = "REPLY";
    public static final String TYPE_REPOST = "REPOST";

    private final long id;
    private final String type;
    private final long postId;
    private final String actorUsername;
    private final String actorDisplayName;
    private final String postSnippet;
    private final String createdAt;

    public AppNotification(JSONObject json) throws JSONException {
        this.id = json.optLong("id", 0L);
        this.type = json.optString("type", "");
        this.postId = json.isNull("postId") ? 0L : json.optLong("postId", 0L);
        this.actorUsername = json.optString("actorUsername", "");

        String name = json.optString("actorDisplayName", "");
        this.actorDisplayName = name.isEmpty() ? this.actorUsername : name;

        // FOLLOW notifications have no post, so the server sends null
        this.postSnippet = json.isNull("postSnippet")
                ? ""
                : json.optString("postSnippet", "");

        this.createdAt = json.optString("createdAt", "");
    }

    public long getId() {
        return id;
    }

    /** The post this notification is about (0 for follows). */
    public long getPostId() {
        return postId;
    }

    public String getType() {
        return type;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public String getPostSnippet() {
        return postSnippet;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
