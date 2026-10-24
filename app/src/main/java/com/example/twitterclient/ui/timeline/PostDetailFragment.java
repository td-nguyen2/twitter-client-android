
package com.example.twitterclient.ui.timeline;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.example.twitterclient.R;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import com.example.twitterclient.auth.SessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PostDetailFragment extends Fragment {

    private static final String ARG_POST_ID = "post_id";
    private static final String ARG_DISPLAY_NAME = "display_name";
    private static final String ARG_USERNAME = "username";
    private static final String ARG_CONTENT = "content";

    private LinearLayout repliesContainer;
    private EditText replyInput;
    private Button replyButton;

    private ApiService apiService;
    private long postId;

    public static PostDetailFragment newInstance(Tweet tweet) {
        PostDetailFragment fragment = new PostDetailFragment();

        Bundle args = new Bundle();
        args.putLong(ARG_POST_ID, tweet.getId());
        args.putString(ARG_DISPLAY_NAME, tweet.getDisplayName());
        args.putString(ARG_USERNAME, tweet.getUsername());
        args.putString(ARG_CONTENT, tweet.getText());

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_post_detail,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();

        if (args == null) {
            requireActivity()
                    .getSupportFragmentManager()
                    .popBackStack();
            return;
        }

        postId = args.getLong(ARG_POST_ID);
        apiService = new ApiService(requireContext());

        TextView author = view.findViewById(R.id.detail_display_name);
        TextView username = view.findViewById(R.id.detail_username);
        TextView content = view.findViewById(R.id.detail_content);
        TextView backButton = view.findViewById(R.id.detail_back_button);

        replyInput = view.findViewById(R.id.reply_input);
        replyButton = view.findViewById(R.id.reply_button);
        repliesContainer = view.findViewById(R.id.replies_container);

        author.setText(args.getString(ARG_DISPLAY_NAME, ""));
        username.setText("@" + args.getString(ARG_USERNAME, ""));
        content.setText(args.getString(ARG_CONTENT, ""));

        backButton.setOnClickListener(v ->
                requireActivity()
                        .getSupportFragmentManager()
                        .popBackStack()
        );

        replyButton.setOnClickListener(v -> addReply());

        loadReplies();
    }

    private void loadReplies() {
        apiService.getReplies(
                postId,
                response -> {
                    if (!isAdded() || repliesContainer == null) {
                        return;
                    }

                    JSONArray replies;

                    try {
                        // Expected response: {"replies": [...]}
                        replies = response.optJSONArray("replies");

                        // Also accept {"data": [...]}
                        if (replies == null) {
                            replies = response.optJSONArray("data");
                        }

                        if (replies == null) {
                            Toast.makeText(
                                    requireContext(),
                                    "Unexpected replies response from server",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        displayReplies(replies);

                    } catch (Exception e) {
                        Toast.makeText(
                                requireContext(),
                                "Could not display replies",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                },
                error -> showError("Could not load replies", error)
        );
    }

    private void addReply() {
        String reply = replyInput.getText().toString().trim();

        if (reply.isEmpty()) {
            replyInput.setError("Please enter a reply");
            return;
        }

        if (reply.length() > 280) {
            replyInput.setError("Reply must be 280 characters or fewer");
            return;
        }

        SessionManager session = new SessionManager(requireContext());
        String token = session.getToken();

        if (token == null || token.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Please log in to reply",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        JSONObject body = new JSONObject();

        try {
            body.put("content", reply);
        } catch (JSONException e) {
            Toast.makeText(
                    requireContext(),
                    "Could not prepare reply",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        replyButton.setEnabled(false);

        apiService.createReply(
                postId,
                body,
                response -> {
                    if (!isAdded()) {
                        return;
                    }

                    replyButton.setEnabled(true);
                    replyInput.setText("");

                    Toast.makeText(
                            requireContext(),
                            "Reply added",
                            Toast.LENGTH_SHORT
                    ).show();

                    // Reload from server so the saved reply is displayed.
                    loadReplies();
                },
                error -> {
                    if (!isAdded()) {
                        return;
                    }

                    replyButton.setEnabled(true);
                    showError("Could not send reply", error);
                }
        );
    }

    private void displayReplies(JSONArray replies) {
        if (!isAdded() || repliesContainer == null) {
            return;
        }

        repliesContainer.removeAllViews();

        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply;

            try {
                reply = replies.getJSONObject(i);
            } catch (JSONException e) {
                continue;
            }

            String displayName = reply.optString("displayName", "");
            String username = reply.optString("username", "");
            String content = reply.optString("content", "");

            if (displayName.isEmpty()) {
                displayName = username.isEmpty() ? "User" : username;
            }

            TextView replyView = new TextView(requireContext());

            String header = username.isEmpty()
                    ? displayName
                    : displayName + "  @" + username;

            replyView.setText(header + "\n" + content);
            replyView.setTextSize(15);
            replyView.setTextColor(
                    android.graphics.Color.rgb(15, 20, 25)
            );

            int padding = (int) (
                    16 * getResources().getDisplayMetrics().density
            );

            replyView.setPadding(
                    padding, padding, padding, padding
            );

            repliesContainer.addView(replyView);

            View divider = new View(requireContext());
            divider.setBackgroundColor(
                    android.graphics.Color.rgb(239, 243, 244)
            );

            int dividerHeight = Math.max(
                    1,
                    (int) getResources().getDisplayMetrics().density
            );

            repliesContainer.addView(
                    divider,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dividerHeight
                    )
            );
        }
    }

    private void showError(String message, VolleyError error) {
        if (!isAdded()) {
            return;
        }

        String detail = "";

        if (error.networkResponse != null) {
            detail = " (HTTP " + error.networkResponse.statusCode + ")";
        } else if (error instanceof com.android.volley.NoConnectionError) {
            detail = ": cannot connect to server";
        }

        Toast.makeText(
                requireContext(),
                message + detail,
                Toast.LENGTH_LONG
        ).show();
    }
}
