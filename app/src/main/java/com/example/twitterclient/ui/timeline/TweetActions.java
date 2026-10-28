package com.example.twitterclient.ui.timeline;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;

import com.bumptech.glide.Glide;
import com.android.volley.VolleyError;
import com.example.twitterclient.R;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import com.example.twitterclient.repository.ProfileRepository;
import com.example.twitterclient.util.ImageUtils;
import com.example.twitterclient.util.TimeUtils;
import com.example.twitterclient.util.UiText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * The reply and repost behaviour shared by the timeline, profile and Post screens.
 *
 * Every method updates the Tweet object it is given and then calls
 * {@link OnChanged}, so the caller only has to redraw that one tweet.
 */
public final class TweetActions {

    /** Called on the main thread each time the tweet's counters/flags change. */
    public interface OnChanged {
        void onChanged(Tweet tweet);
    }

    private static final int MAX_REPLY_LENGTH = 280;

    // One shared Volley queue instead of a new one per tap
    private static ApiService api;

    // Posts with a repost request on the way (ignore taps until it answers)
    private static final Set<Long> repostsInFlight = new HashSet<>();

    private TweetActions() {
    }

    private static ApiService api(Context context) {
        if (api == null) {
            api = new ApiService(context);
        }
        return api;
    }

    private static boolean requireLogin(Context context, String action) {
        if (new SessionManager(context).isLoggedIn()) {
            return true;
        }

        Toast.makeText(context, "Please log in to " + action, Toast.LENGTH_SHORT).show();
        return false;
    }

    // ------------------------------------------------------------------ repost

    /** Bottom sheet with "Repost" or, if you already did, "Undo repost". */
    public static void showRepostMenu(Context context, Tweet tweet, OnChanged onChanged) {
        if (!requireLogin(context, "repost")) {
            return;
        }

        if (repostsInFlight.contains(tweet.getId())) {
            return;
        }

        AppCompatDialog dialog =
                new AppCompatDialog(context, R.style.Theme_TwitterStarter_Sheet);
        dialog.setContentView(R.layout.sheet_repost);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        boolean alreadyReposted = tweet.isRepostedByMe();

        ImageView icon = dialog.findViewById(R.id.sheet_repost_icon);
        TextView label = dialog.findViewById(R.id.sheet_repost_text);
        View row = dialog.findViewById(R.id.sheet_repost_row);
        View cancel = dialog.findViewById(R.id.sheet_cancel);

        // Undoing is shown in red, reposting in the normal text colour
        int color = Color.parseColor(alreadyReposted ? "#F4212E" : "#0F1419");

        if (label != null) {
            label.setText(alreadyReposted ? "Undo repost" : "Repost");
            label.setTextColor(color);
        }
        if (icon != null) {
            icon.setImageTintList(ColorStateList.valueOf(color));
        }
        if (row != null) {
            row.setOnClickListener(v -> {
                dialog.dismiss();
                setReposted(context, tweet, !alreadyReposted, onChanged);
            });
        }
        if (cancel != null) {
            cancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Updates the screen first (like Twitter does), then asks the server.
     * If the request fails the old state is put back.
     */
    private static void setReposted(
            Context context,
            Tweet tweet,
            boolean repost,
            OnChanged onChanged
    ) {
        long postId = tweet.getId();

        if (!repostsInFlight.add(postId)) {
            return;
        }

        boolean oldReposted = tweet.isRepostedByMe();
        int oldCount = tweet.getRepostCount();

        tweet.setRepostedByMe(repost);
        tweet.setRepostCount(oldCount + (repost ? 1 : -1));
        notifyChanged(onChanged, tweet);

        com.android.volley.Response.Listener<JSONObject> success = response -> {
            repostsInFlight.remove(postId);

            // The server's numbers win over our guess
            tweet.setRepostedByMe(response.optBoolean("reposted", repost));
            tweet.setRepostCount(response.optInt("repostCount", tweet.getRepostCount()));
            notifyChanged(onChanged, tweet);
        };

        com.android.volley.Response.ErrorListener failure = error -> {
            repostsInFlight.remove(postId);

            tweet.setRepostedByMe(oldReposted);
            tweet.setRepostCount(oldCount);
            notifyChanged(onChanged, tweet);

            Toast.makeText(
                    context,
                    isAuthError(error)
                            ? "Please log in again to repost."
                            : "Could not update repost. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();
        };

        if (repost) {
            api(context).repost(postId, success, failure);
        } else {
            api(context).undoRepost(postId, success, failure);
        }
    }

    // ------------------------------------------------------------------- reply

    /**
     * Full-screen reply composer: the tweet you answer, "Replying to @user",
     * and a box with a 280-character counter.
     */
    public static void showReplyDialog(Context context, Tweet tweet, OnChanged onChanged) {
        if (!requireLogin(context, "reply")) {
            return;
        }

        AppCompatDialog dialog =
                new AppCompatDialog(context, R.style.Theme_TwitterStarter_ReplyDialog);
        dialog.setContentView(R.layout.dialog_reply);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );
        }

        TextView cancel = dialog.findViewById(R.id.reply_dialog_cancel);
        TextView send = dialog.findViewById(R.id.reply_dialog_send);
        TextView counter = dialog.findViewById(R.id.reply_dialog_counter);
        EditText input = dialog.findViewById(R.id.reply_dialog_input);
        ImageView originalAvatar = dialog.findViewById(R.id.reply_original_avatar);
        ImageView originalMedia = dialog.findViewById(R.id.reply_original_media);
        ImageView myAvatar = dialog.findViewById(R.id.reply_my_avatar);
        TextView originalName = dialog.findViewById(R.id.reply_original_name);
        TextView originalUsername = dialog.findViewById(R.id.reply_original_username);
        TextView originalText = dialog.findViewById(R.id.reply_original_text);
        TextView replyingTo = dialog.findViewById(R.id.reply_replying_to);

        if (cancel == null || send == null || counter == null || input == null
                || originalAvatar == null || originalMedia == null || myAvatar == null || originalName == null
                || originalUsername == null || originalText == null || replyingTo == null) {
            return;
        }

        // The tweet being answered
        ImageUtils.loadAvatar(originalAvatar, tweet.getProfileImageUrl());
        originalName.setText(tweet.getDisplayName());

        String ago = TimeUtils.shortAgo(tweet.getCreatedAt());
        originalUsername.setText(
                "@" + tweet.getUsername() + (ago.isEmpty() ? "" : " · " + ago)
        );

        String originalContent = tweet.getText();
        if (originalContent == null || originalContent.trim().isEmpty()) {
            originalText.setVisibility(View.GONE);
        } else {
            originalText.setText(originalContent);
            originalText.setVisibility(View.VISIBLE);
        }

        String originalMediaUrl = tweet.getMediaUrl();
        if (originalMediaUrl == null || originalMediaUrl.trim().isEmpty()) {
            Glide.with(originalMedia).clear(originalMedia);
            originalMedia.setVisibility(View.GONE);
        } else {
            originalMedia.setVisibility(View.VISIBLE);
            Glide.with(originalMedia)
                    .load(ImageUtils.fullUrl(originalMediaUrl))
                    .into(originalMedia);
        }
        replyingTo.setText(UiText.replyingTo(tweet.getUsername()));

        // Your own avatar (falls back to the default picture)
        new ProfileRepository(context).getMyProfile(
                response -> {
                    JSONObject user = response.optJSONObject("user");
                    if (user != null && dialog.isShowing()) {
                        ImageUtils.loadAvatar(myAvatar, user.optString("profileImageUrl", ""));
                    }
                },
                error -> { /* keep the default avatar */ }
        );

        final boolean[] sending = {false};

        Runnable refresh = () -> {
            int length = input.getText().length();
            int remaining = MAX_REPLY_LENGTH - length;
            boolean hasText = !input.getText().toString().trim().isEmpty();

            send.setEnabled(hasText && remaining >= 0 && !sending[0]);

            // Show the counter only when the limit is getting close
            if (remaining <= 20) {
                counter.setText(String.valueOf(remaining));
                counter.setTextColor(Color.parseColor(remaining < 0 ? "#F4212E" : "#536471"));
            } else {
                counter.setText("");
            }
        };

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        });
        refresh.run();

        cancel.setOnClickListener(v -> {
            if (input.getText().toString().trim().isEmpty() || sending[0]) {
                dialog.dismiss();
                return;
            }

            new AlertDialog.Builder(context)
                    .setTitle("Discard reply?")
                    .setMessage("This can't be undone and you'll lose your draft.")
                    .setPositiveButton("Discard", (d, which) -> dialog.dismiss())
                    .setNegativeButton("Keep editing", null)
                    .show();
        });

        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();

            if (text.isEmpty() || text.length() > MAX_REPLY_LENGTH) {
                return;
            }

            JSONObject body = new JSONObject();
            try {
                body.put("content", text);
            } catch (JSONException error) {
                Toast.makeText(context, "Could not prepare reply", Toast.LENGTH_SHORT).show();
                return;
            }

            sending[0] = true;
            refresh.run();

            api(context).createReply(
                    tweet.getId(),
                    body,
                    response -> {
                        tweet.setReplyCount(
                                response.optInt("replyCount", tweet.getReplyCount() + 1)
                        );
                        dialog.dismiss();

                        Toast.makeText(context, "Your reply was sent", Toast.LENGTH_SHORT).show();
                        notifyChanged(onChanged, tweet);
                    },
                    error -> {
                        sending[0] = false;
                        refresh.run();

                        Toast.makeText(
                                context,
                                isAuthError(error)
                                        ? "Please log in again to reply."
                                        : "Could not send reply. Please try again.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        });

        dialog.show();
    }

    // ------------------------------------------------------------------- share

    /** Opens the system share sheet with the tweet's text. */
    public static void share(Context context, Tweet tweet) {
        String text = tweet.getDisplayName() + " (@" + tweet.getUsername() + "):\n"
                + (tweet.getText() == null ? "" : tweet.getText());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);

        context.startActivity(Intent.createChooser(intent, "Share post"));
    }

    // ----------------------------------------------------------------- helpers

    private static boolean isAuthError(VolleyError error) {
        return error.networkResponse != null
                && (error.networkResponse.statusCode == 401
                || error.networkResponse.statusCode == 403);
    }

    private static void notifyChanged(OnChanged onChanged, Tweet tweet) {
        if (onChanged != null) {
            onChanged.onChanged(tweet);
        }
    }
}
