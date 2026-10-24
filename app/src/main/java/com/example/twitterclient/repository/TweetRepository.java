
package com.example.twitterclient.repository;

import android.content.Context;
import com.android.volley.VolleyError;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Converts API JSON into app models; keeps Volley out of the Fragment. */
public class TweetRepository {
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final ApiService api;

    public TweetRepository(Context context) {
        api = new ApiService(context);
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

            // The backend identifies the author using JWT.
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

    private static Tweet toTweet(JSONObject item) throws JSONException {
        long id = item.getLong("id");
        String username = item.getString("username");
        String content = item.getString("content");

        return new Tweet(id, username, username, content);
    }

    private static String describe(VolleyError error) {
        if (error.networkResponse != null) {
            return "HTTP " + error.networkResponse.statusCode
                    + " from server";
        }

        return "Cannot reach server. Is Express running on port 3000?";
    }
}
