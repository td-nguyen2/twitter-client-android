package com.example.twitterclient.repository;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.twitterclient.auth.SessionManager;
import android.net.Uri;
import com.android.volley.VolleyError;
import com.example.twitterclient.network.MultipartImageRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ProfileRepository {

    private static final String BASE_URL =
            "http://10.0.2.2:3000";

    private final RequestQueue queue;

    private final Context appContext;

    private final SessionManager session;

    public ProfileRepository(Context context) {
        this.appContext = context.getApplicationContext();

        queue = Volley.newRequestQueue(this.appContext);
        session = new SessionManager(this.appContext);
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

    public void uploadProfileImage(
            Uri imageUri,
            String kind,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) {
        if (!"avatar".equals(kind) && !"banner".equals(kind)) {
            failure.onErrorResponse(
                    new VolleyError("Invalid image kind")
            );
            return;
        }

        String token = session.getToken();

        if (token == null || token.isEmpty()) {
            failure.onErrorResponse(
                    new VolleyError("Not logged in")
            );
            return;
        }

        // Read the selected file outside the UI thread.
        new Thread(() -> {
            try (InputStream input =
                         appContext.getContentResolver()
                                 .openInputStream(imageUri)) {

                if (input == null) {
                    throw new IOException("Cannot open selected image");
                }

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream();

                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                int maxSize = 5 * 1024 * 1024;

                while ((count = input.read(buffer)) != -1) {
                    total += count;

                    if (total > maxSize) {
                        throw new IOException(
                                "Image exceeds 5 MB"
                        );
                    }

                    output.write(buffer, 0, count);
                }

                if (total == 0) {
                    throw new IOException("Image is empty");
                }

                byte[] imageBytes = output.toByteArray();

                MultipartImageRequest request =
                        new MultipartImageRequest(
                                BASE_URL + "/api/users/me/images/" + kind,
                                imageBytes,
                                token,
                                success,
                                failure
                        );

                // Volley dispatches the network request.
                queue.add(request);

            } catch (Exception error) {
                android.os.Handler mainHandler =
                        new android.os.Handler(
                                android.os.Looper.getMainLooper()
                        );

                mainHandler.post(() ->
                        failure.onErrorResponse(
                                new VolleyError(error)
                        )
                );
            }
        }).start();
    }

}
