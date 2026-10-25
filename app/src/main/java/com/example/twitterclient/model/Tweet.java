
package com.example.twitterclient.model;

public class Tweet {
    private final long id;
    private final String displayName;
    private final String username;
    private final String text;

    public Tweet(String displayName, String username, String text) {
        this(0L, displayName, username, text);
    }

    public Tweet(long id, String displayName, String username, String text) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.text = text;
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
}
