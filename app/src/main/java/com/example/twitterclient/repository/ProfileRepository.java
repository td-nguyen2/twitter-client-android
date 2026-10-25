package com.example.twitterclient.repository;

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

public class ProfileRepository {

    private static final String BASE_URL =
            "http://10.0.2.2:3000";

    private final RequestQueue queue;
    private final SessionManager session;

    public ProfileRepository(Context context) {
        Context appContext = context.getApplicationContext();

        queue = Volley.newRequestQueue(appContext);
        session = new SessionManager(appContext);
    }

    // Retrieve the logged-in user's profile.
    // Requires JWT authentication.
    public void getMyProfile(
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/users/me",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();

                String token = session.getToken();

                if (token != null && !token.isEmpty()) {
                    headers.put(
                            "Authorization",
                            "Bearer " + token
                    );
                }

                return headers;
            }
        };

        queue.add(request);
    }

    // Retrieve any user's public profile.
    public void getUserProfile(
            int userId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/users/" + userId,
                null,
                success,
                failure
        );

        queue.add(request);
    }

    // Retrieve posts created by a particular user.
    public void getUserPosts(
            int userId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/users/" + userId + "/posts",
                null,
                success,
                failure
        );

        queue.add(request);
    }


    public void updateMyProfile(
            String displayName,
            String bio,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        try {
            JSONObject body = new JSONObject();
            body.put("displayName", displayName);
            body.put("bio", bio);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.PATCH,
                    BASE_URL + "/api/users/me",
                    body,
                    success,
                    failure
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();

                    String token = session.getToken();

                    if (token != null && !token.isEmpty()) {
                        headers.put(
                                "Authorization",
                                "Bearer " + token
                        );
                    }

                    return headers;
                }
            };

            queue.add(request);

        } catch (org.json.JSONException error) {
            failure.onErrorResponse(
                    new com.android.volley.VolleyError(error)
            );
        }
    }


    public void getUserByUsername(
            String username,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/users/by-username/"
                        + android.net.Uri.encode(username),
                null,
                success,
                failure
        );

        queue.add(request);
    }

    // Lấy trạng thái follow và số lượng follower/following của một user
    public void getFollowStatus(
            int userId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/users/" + userId + "/follow-status",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = session.getToken();
                if (token != null && !token.isEmpty()) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };
        queue.add(request);
    }

    // Bấm Follow hoặc Unfollow một user
    public void toggleFollow(
            int userId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                BASE_URL + "/api/users/" + userId + "/follow",
                null,
                success,
                failure
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = session.getToken();
                if (token != null && !token.isEmpty()) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };
        queue.add(request);
    }

}