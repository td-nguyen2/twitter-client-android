package com.example.twitterclient.repository;

import android.content.Context;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts API JSON into app models; keeps Volley out of the Fragment. */
public class TweetRepository {
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final ApiService api;
    private final Context context;

    public TweetRepository(Context context) {
        this.context = context;
        this.api = new ApiService(context);
    }

    public void getTweets(Callback<List<Tweet>> callback) {
        api.getPosts(response -> {
            try {
                JSONArray posts = response.getJSONArray("posts");
                List<Tweet> result = new ArrayList<>();

                for (int i = 0; i < posts.length(); i++) {
                    result.add(toTweet(posts.getJSONObject(i)));
                }

                callback.onSuccess(result);
            } catch (JSONException error) {
                callback.onError(
                        "Invalid server response: " + error.getMessage()
                );
            }
        }, error -> callback.onError(describe(error)));
    }

    public void createTweet(
            String content,
            Callback<Tweet> callback
    ) {
        try {
            JSONObject body = new JSONObject();
            body.put("content", content);

            api.createPost(
                    body,
                    response -> {
                        try {
                            callback.onSuccess(
                                    toTweet(response.getJSONObject("post"))
                            );
                        } catch (JSONException error) {
                            callback.onError(
                                    "Invalid server response: "
                                            + error.getMessage()
                            );
                        }
                    },
                    error -> callback.onError(describe(error))
            );

        } catch (JSONException error) {
            callback.onError("Could not create request");
        }
    }

    public void toggleLike(
            long tweetId,
            Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener
    ) {
        // Android emulator localhost address
        String url = "http://10.0.2.2:3000/api/posts/" + tweetId + "/like";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                null,
                listener,
                errorListener
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                SessionManager sessionManager = new SessionManager(context);
                String token = sessionManager.getToken();
                if (token != null) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(context).add(request);
    }

    private static Tweet toTweet(JSONObject item) throws JSONException {
        long id = item.getLong("id");
        String username = item.getString("username");
        String content = item.getString("content");

        Tweet tweet = new Tweet(id, username, username, content);
        tweet.setLikeCount(
                item.optInt("likeCount", item.optInt("like_count", 0))
        );
        tweet.setLikedByMe(
                item.optBoolean("likedByMe", item.optBoolean("liked_by_me", false))
        );
        return tweet;
    }

    private static String describe(VolleyError error) {
        if (error.networkResponse != null) {
            return "HTTP " + error.networkResponse.statusCode
                    + " from server";
        }

        return "Cannot reach server. Is Express running on port 3000?";
    }
}