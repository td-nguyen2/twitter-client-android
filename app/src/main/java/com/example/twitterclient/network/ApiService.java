package com.example.twitterclient.network;

import android.content.Context;

import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

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

    // GET one post with its counters (reply/repost/like counts, my flags)
    public void getPost(
            long postId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                BASE_URL + "/api/posts/" + postId,
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

    // Record one view per signed-in account and post.
    public void recordPostView(
            long postId,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure) {

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                BASE_URL + "/api/posts/" + postId + "/view",
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

    // Create a post with an image or GIF
    public void createPostWithMedia(
            String content,
            Uri mediaUri,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) throws IOException {

        // Determine the selected file's MIME type
        String mimeType = context.getContentResolver().getType(mediaUri);

        if (mimeType == null) {
            throw new IOException("Unable to determine media type");
        }

        if (!mimeType.equals("image/jpeg")
                && !mimeType.equals("image/png")
                && !mimeType.equals("image/webp")
                && !mimeType.equals("image/gif")) {
            throw new IOException("Unsupported media type");
        }

        // Read the file from Android's content resolver
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream input =
                     context.getContentResolver().openInputStream(mediaUri)) {

            if (input == null) {
                throw new IOException("Cannot open selected media");
            }

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 10 * 1024 * 1024) {
                    throw new IOException("Media exceeds 10 MB");
                }

                output.write(buffer, 0, count);
            }
        }

        byte[] mediaBytes = output.toByteArray();

        // Send text + file to Express
        MultipartRequest request = new MultipartRequest(
                BASE_URL + "/api/posts",
                content,
                mediaBytes,
                mimeType,
                getAuthHeaders(),
                success,
                failure
        );

        queue.add(request);
    }public void uploadAvatar(
            Uri avatarUri,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) throws IOException {

        // 1. Determine the image's MIME type
        String mimeType = context.getContentResolver().getType(avatarUri);

        if (mimeType == null) {
            throw new IOException("Unable to determine avatar type");
        }

        if (!mimeType.equals("image/jpeg")
                && !mimeType.equals("image/png")
                && !mimeType.equals("image/webp")
                && !mimeType.equals("image/gif")) {
            throw new IOException("Unsupported avatar type");
        }

        // 2. Read the image into memory
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream input =
                     context.getContentResolver().openInputStream(avatarUri)) {

            if (input == null) {
                throw new IOException("Cannot open selected avatar");
            }

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 10 * 1024 * 1024) {
                    throw new IOException("Avatar exceeds 10 MB");
                }

                output.write(buffer, 0, count);
            }
        }

        byte[] avatarBytes = output.toByteArray();

        // 3. Create a multipart request
        MultipartRequest request = new MultipartRequest(
                BASE_URL + "/api/users/me/avatar",
                avatarBytes,
                mimeType,
                "avatar",
                getAuthHeaders(),
                success,
                failure
        );

        // 4. Send the request
        queue.add(request);
    }

    public void uploadBanner(
            Uri bannerUri,
            Response.Listener<JSONObject> success,
            Response.ErrorListener failure
    ) throws IOException {

        // Determine the image type
        String mimeType = context.getContentResolver().getType(bannerUri);

        if (mimeType == null) {
            throw new IOException("Unable to determine banner type");
        }

        if (!mimeType.equals("image/jpeg")
                && !mimeType.equals("image/png")
                && !mimeType.equals("image/webp")
                && !mimeType.equals("image/gif")) {
            throw new IOException("Unsupported banner type");
        }

        // Read image bytes
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream input =
                     context.getContentResolver().openInputStream(bannerUri)) {

            if (input == null) {
                throw new IOException("Cannot open selected banner");
            }

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 10 * 1024 * 1024) {
                    throw new IOException("Banner exceeds 10 MB");
                }

                output.write(buffer, 0, count);
            }
        }

        byte[] bannerBytes = output.toByteArray();

        // Send the file to the banner endpoint
        MultipartRequest request = new MultipartRequest(
                BASE_URL + "/api/users/me/banner",
                bannerBytes,
                mimeType,
                "banner",
                getAuthHeaders(),
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
    
public void createReplyWithMedia(
        long postId,
        String content,
        Uri mediaUri,
        Response.Listener<JSONObject> success,
        Response.ErrorListener failure
) throws IOException {

    String mimeType = context.getContentResolver().getType(mediaUri);

    if (mimeType == null
            || (!mimeType.equals("image/jpeg")
            && !mimeType.equals("image/png")
            && !mimeType.equals("image/webp")
            && !mimeType.equals("image/gif"))) {
        throw new IOException("Unsupported media type");
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();

    try (InputStream input =
                 context.getContentResolver().openInputStream(mediaUri)) {

        if (input == null) {
            throw new IOException("Cannot open selected media");
        }

        byte[] buffer = new byte[8192];
        int count;

        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > 10 * 1024 * 1024) {
                throw new IOException("Media exceeds 10 MB");
            }

            output.write(buffer, 0, count);
        }
    }

    String replyText = content == null ? "" : content.trim();

    MultipartRequest request = new MultipartRequest(
            BASE_URL + "/api/posts/" + postId + "/replies",
            replyText,
            output.toByteArray(),
            mimeType,
            getAuthHeaders(),
            success,
            failure
    );

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
