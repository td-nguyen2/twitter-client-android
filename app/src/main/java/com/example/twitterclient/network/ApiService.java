package com.example.twitterclient.network;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.twitterclient.auth.SessionManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ApiService {

    private static final String BASE_URL = "http://10.0.2.2:3000";

    private final RequestQueue queue;
    private final Context context;

    public ApiService(Context context) {
        this.context = context.getApplicationContext();
        queue = Volley.newRequestQueue(this.context);
    }

    private Map<String, String> getAuthHeaders() {
        Map<String, String> headers = new HashMap<>();

        SessionManager session = new SessionManager(context);
        String token = session.getToken();

        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }

        return headers;
    }

    // GET all posts (sends the token so the server can return likedByMe)
    public void getPosts(
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/posts",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }

    // GET posts written by the people the current user follows
    public void getFollowingPosts(
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/posts/following",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }

    // GET notifications of the logged-in user
    public void getNotifications(
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/notifications",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }

    // Create a post
    public void createPost(
            JSONObject body,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                BASE_URL + "/api/posts",
                body,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }

    // Get replies for a post
    public void getReplies(
            long postId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/posts/" + postId + "/replies",
                null,
                success,
                failure
        );

        queue.add(request);
    }

    // Create a reply
    public void createReply(
            long postId,
            JSONObject body,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                BASE_URL + "/api/posts/" + postId + "/replies",
                body,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }

    // Get current user's repost status
    public void getRepostStatus(
            long postId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/posts/" + postId + "/repost-status",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }

    // Repost
    public void repost(
            long postId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                BASE_URL + "/api/posts/" + postId + "/repost",
                new JSONObject(),
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }

    // Undo repost
    public void undoRepost(
            long postId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.DELETE,
                BASE_URL + "/api/posts/" + postId + "/repost",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return getAuthHeaders();
            }
        };

        queue.add(request);
    }
}
