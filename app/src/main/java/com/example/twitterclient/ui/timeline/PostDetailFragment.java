
package com.example.twitterclient.ui.timeline;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.VolleyError;
import com.bumptech.glide.Glide;
import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import com.example.twitterclient.repository.ProfileRepository;
import com.example.twitterclient.repository.TweetRepository;
import com.example.twitterclient.util.ImageUtils;
import com.example.twitterclient.util.TimeUtils;
import com.example.twitterclient.util.UiText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Post screen: the tweet in full size with its counters and action row,
 * the replies under it, and a reply bar pinned to the bottom.
 */
public class PostDetailFragment extends Fragment {

    private static final String ARG_TWEET = "tweet";
    private static final String ARG_POST_ID = "post_id";
    private static final int MAX_REPLY_LENGTH = 280;

    private ApiService apiService;
    private Tweet tweet;
    private Tweet originatingTweet;

    private ScrollView scroll;
    private LinearLayout repliesContainer;
    private TextView repliesEmpty;

    private EditText replyInput;
    private TextView replyButton;
    private TextView replyMediaButton;
    private LinearLayout replyMediaPreviewContainer;
    private ImageView replyMediaPreview;
    private TextView replyMediaRemove;

    private Uri selectedReplyMediaUri;
    private boolean sendingReply = false;

    private ImageView avatar;
    private TextView displayName;
    private TextView username;
    private TextView content;
    private ImageView media;
    private TextView time;
    private View statsDivider;
    private View statsRow;
    private TextView statReposts;
    private TextView statReplies;
    private TextView statLikes;
    private TextView statViews;
    private ImageView repostIcon;
    private ImageView likeIcon;
    private TextView replyingToLabel;

    /*
     * Opens the system media picker.
     * The user can select an image or GIF.
     */
    private final ActivityResultLauncher<String> replyMediaPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (!isAdded() || uri == null) {
                            return;
                        }

                        selectedReplyMediaUri = uri;

                        replyMediaPreviewContainer.setVisibility(View.VISIBLE);

                        Glide.with(this)
                                .load(uri)
                                .into(replyMediaPreview);

                        updateReplyButton();
                    }
            );

    public static PostDetailFragment newInstance(Tweet tweet) {
        PostDetailFragment fragment = new PostDetailFragment();

        Bundle args = new Bundle();
        args.putSerializable(ARG_TWEET, tweet);

        fragment.setArguments(args);
        return fragment;
    }

    /** Opens a post we only know the id of; the server fills in the rest. */
    public static PostDetailFragment newInstance(long postId) {
        PostDetailFragment fragment = new PostDetailFragment();

        Bundle args = new Bundle();
        args.putLong(ARG_POST_ID, postId);

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
        Object passed = args == null ? null : args.getSerializable(ARG_TWEET);
        long idOnly = args == null ? 0L : args.getLong(ARG_POST_ID, 0L);

        if (passed instanceof Tweet) {
            tweet = (Tweet) passed;
            originatingTweet = tweet;
        } else if (idOnly > 0) {
            // Placeholder: hidden until the server answers
            tweet = new Tweet(idOnly, "", "", "");
        } else {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        final boolean waitForServer = !(passed instanceof Tweet);

        apiService = new ApiService(requireContext());

        scroll = view.findViewById(R.id.detail_scroll);
        avatar = view.findViewById(R.id.detail_avatar);
        displayName = view.findViewById(R.id.detail_display_name);
        username = view.findViewById(R.id.detail_username);
        content = view.findViewById(R.id.detail_content);
        media = view.findViewById(R.id.detail_media);
        time = view.findViewById(R.id.detail_time);

        statsDivider = view.findViewById(R.id.detail_stats_divider);
        statsRow = view.findViewById(R.id.detail_stats_row);
        statReposts = view.findViewById(R.id.detail_stat_reposts);
        statReplies = view.findViewById(R.id.detail_stat_replies);
        statLikes = view.findViewById(R.id.detail_stat_likes);
        statViews = view.findViewById(R.id.detail_stat_views);

        repostIcon = view.findViewById(R.id.detail_repost_icon);
        likeIcon = view.findViewById(R.id.detail_like_icon);

        replyInput = view.findViewById(R.id.reply_input);
        replyButton = view.findViewById(R.id.reply_button);
        replyMediaButton = view.findViewById(R.id.reply_media_button);
        replyMediaPreviewContainer =
                view.findViewById(R.id.reply_media_preview_container);
        replyMediaPreview = view.findViewById(R.id.reply_media_preview);
        replyMediaRemove = view.findViewById(R.id.reply_media_remove);

        repliesContainer = view.findViewById(R.id.replies_container);
        repliesEmpty = view.findViewById(R.id.detail_replies_empty);

        view.findViewById(R.id.detail_back_button).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );

        // Tapping the author opens their profile
        view.findViewById(R.id.detail_author_row).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showUserProfile(tweet.getUsername())
        );

        // Action row
        view.findViewById(R.id.detail_reply_container)
                .setOnClickListener(v -> focusReplyInput());

        view.findViewById(R.id.detail_repost_container).setOnClickListener(v ->
                TweetActions.showRepostMenu(
                        requireContext(),
                        tweet,
                        changed -> {
                            if (isAdded()) {
                                bindStats();
                            }
                        }
                )
        );

        view.findViewById(R.id.detail_like_container)
                .setOnClickListener(v -> toggleLike());

        view.findViewById(R.id.detail_share_container)
                .setOnClickListener(v ->
                        TweetActions.share(requireContext(), tweet)
                );

        // Reply bar
        replyingToLabel = view.findViewById(R.id.detail_replying_to);
        replyingToLabel.setText(UiText.replyingTo(tweet.getUsername()));

        replyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateReplyButton();
            }
        });

        replyMediaButton.setOnClickListener(v -> {
            if (!sendingReply) {
                replyMediaPicker.launch("image/*");
            }
        });

        replyMediaRemove.setOnClickListener(v ->
                clearSelectedReplyMedia()
        );

        replyButton.setOnClickListener(v -> addReply());

        updateReplyButton();

        loadMyAvatar(view.findViewById(R.id.detail_my_avatar));

        if (waitForServer) {
            scroll.setVisibility(View.INVISIBLE);
        }

        bindTweet();
        recordPostView();
        refreshTweet(waitForServer);

        // With only an id we need the author first ("Replying to @author")
        if (!waitForServer) {
            loadReplies();
        }
    }

    // ------------------------------------------------------------------ tweet

    private void bindTweet() {
        ImageUtils.loadAvatar(avatar, tweet.getProfileImageUrl());
        displayName.setText(tweet.getDisplayName());
        username.setText("@" + tweet.getUsername());

        String text = tweet.getText();

        if (text == null || text.trim().isEmpty()) {
            content.setVisibility(View.GONE);
        } else {
            content.setText(text);
            content.setVisibility(View.VISIBLE);
        }

        String mediaUrl = tweet.getMediaUrl();

        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            Glide.with(media).clear(media);
            media.setVisibility(View.GONE);
        } else {
            media.setVisibility(View.VISIBLE);
            Glide.with(media)
                    .load(ImageUtils.fullUrl(mediaUrl))
                    .into(media);
        }

        time.setText(TimeUtils.fullDate(tweet.getCreatedAt()));
        time.setVisibility(
                time.getText().length() == 0 ? View.GONE : View.VISIBLE
        );

        bindStats();
    }

    // Counters line, repost/like colours
    private void bindStats() {
        int reposts = tweet.getRepostCount();
        int replies = tweet.getReplyCount();
        int likes = tweet.getLikeCount();

        setStat(
                statReposts,
                reposts,
                reposts == 1 ? "Repost" : "Reposts"
        );

        setStat(
                statReplies,
                replies,
                replies == 1 ? "Reply" : "Replies"
        );

        setStat(
                statLikes,
                likes,
                likes == 1 ? "Like" : "Likes"
        );

        int views = tweet.getViewCount();
        setStat(statViews, views, views == 1 ? "View" : "Views");

        boolean anyStat = reposts > 0 || replies > 0 || likes > 0 || views > 0;

        statsRow.setVisibility(anyStat ? View.VISIBLE : View.GONE);
        statsDivider.setVisibility(anyStat ? View.VISIBLE : View.GONE);

        repostIcon.setImageTintList(ColorStateList.valueOf(
                Color.parseColor(
                        tweet.isRepostedByMe() ? "#00BA7C" : "#536471"
                )
        ));

        likeIcon.setImageTintList(ColorStateList.valueOf(
                Color.parseColor(
                        tweet.isLikedByMe() ? "#F91880" : "#536471"
                )
        ));
    }

    private void recordPostView() {
        if (tweet == null || tweet.getId() <= 0) {
            return;
        }

        apiService.recordPostView(
                tweet.getId(),
                response -> {
                    if (!isAdded() || tweet == null) {
                        return;
                    }

                    tweet.setViewCount(response.optInt("viewCount", tweet.getViewCount()));
                    if (originatingTweet != null) {
                        originatingTweet.setViewCount(tweet.getViewCount());
                    }
                    bindStats();
                },
                error -> { /* Keep the last known count if view tracking is unavailable. */ }
        );
    }

    // "12 Likes" with the number in bold dark text, hidden at zero
    private void setStat(TextView target, int count, String label) {
        if (count <= 0) {
            target.setVisibility(View.GONE);
            return;
        }

        String number = TimeUtils.compactCount(count);

        SpannableStringBuilder text =
                new SpannableStringBuilder(number + " " + label);

        text.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                number.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        text.setSpan(
                new ForegroundColorSpan(Color.parseColor("#0F1419")),
                0,
                number.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        target.setText(text);
        target.setVisibility(View.VISIBLE);
    }

    // The tweet we were given can be stale: ask the server for the current numbers
    private void refreshTweet(boolean waitForServer) {
        if (tweet.getId() <= 0) {
            return;
        }

        apiService.getPost(
                tweet.getId(),
                response -> {
                    if (!isAdded()) {
                        return;
                    }

                    JSONObject post = response.optJSONObject("post");

                    if (post == null) {
                        return;
                    }

                    try {
                        tweet = new Tweet(post);
                        if (originatingTweet != null) {
                            originatingTweet.copyStatsFrom(tweet);
                        }
                        bindTweet();

                        replyingToLabel.setText(
                                UiText.replyingTo(tweet.getUsername())
                        );

                        scroll.setVisibility(View.VISIBLE);

                        if (waitForServer) {
                            loadReplies();
                        }
                    } catch (JSONException ignored) {
                        // Keep what we already show.
                    }
                },
                error -> {
                    if (!isAdded()) {
                        return;
                    }

                    if (waitForServer) {
                        showError("Could not open this post", error);
                        requireActivity()
                                .getSupportFragmentManager()
                                .popBackStack();
                    }
                }
        );
    }

    private void toggleLike() {
        if (!new SessionManager(requireContext()).isLoggedIn()) {
            Toast.makeText(
                    requireContext(),
                    "Please log in to like",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new TweetRepository(requireContext()).toggleLike(
                tweet.getId(),
                response -> {
                    if (!isAdded()) {
                        return;
                    }

                    tweet.setLikedByMe(
                            response.optBoolean("likedByMe", false)
                    );

                    tweet.setLikeCount(
                            response.optInt(
                                    "likeCount",
                                    tweet.getLikeCount()
                            )
                    );

                    bindStats();
                },
                error -> showError("Could not like this post", error)
        );
    }

    private void loadMyAvatar(ImageView target) {
        new ProfileRepository(requireContext()).getMyProfile(
                response -> {
                    if (!isAdded()) {
                        return;
                    }

                    JSONObject user = response.optJSONObject("user");

                    if (user != null) {
                        ImageUtils.loadAvatar(
                                target,
                                user.optString("profileImageUrl", "")
                        );
                    }
                },
                error -> {
                    // Keep the default avatar.
                }
        );
    }

    // ---------------------------------------------------------------- replies

    private void loadReplies() {
        apiService.getReplies(
                tweet.getId(),
                response -> {
                    if (!isAdded() || repliesContainer == null) {
                        return;
                    }

                    JSONArray replies = response.optJSONArray("replies");

                    if (replies == null) {
                        Toast.makeText(
                                requireContext(),
                                "Unexpected replies response from server",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    displayReplies(replies);

                    // Keep the counter in step with what the server really holds
                    tweet.setReplyCount(replies.length());
                    bindStats();
                },
                error -> showError("Could not load replies", error)
        );
    }

    private void displayReplies(JSONArray replies) {
        repliesContainer.removeAllViews();

        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.optJSONObject(i);

            if (reply != null) {
                addReplyView(reply);
            }
        }

        updateEmptyState();
    }

    private void addReplyView(JSONObject reply) {
        View item = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_reply, repliesContainer, false);

        String replyUsername = reply.optString("username", "");
        String name = reply.optString("displayName", replyUsername);

        ImageView replyAvatar = item.findViewById(R.id.reply_avatar);
        TextView replyName = item.findViewById(R.id.reply_display_name);
        TextView replyHandle = item.findViewById(R.id.reply_username);
        TextView replyTo = item.findViewById(R.id.reply_to_label);
        TextView replyContent = item.findViewById(R.id.reply_content);

        String profileImage = reply.isNull("profileImageUrl")
                ? null
                : reply.optString("profileImageUrl", "");

        ImageUtils.loadAvatar(replyAvatar, profileImage);

        replyName.setText(name.isEmpty() ? replyUsername : name);

        String ago = TimeUtils.shortAgo(
                reply.optString("createdAt", "")
        );

        replyHandle.setText(
                "@" + replyUsername + (ago.isEmpty() ? "" : " · " + ago)
        );

        replyTo.setText(UiText.replyingTo(tweet.getUsername()));

        String replyText = reply.optString("content", "");
        replyContent.setText(replyText);
        replyContent.setVisibility(
                replyText.trim().isEmpty() ? View.GONE : View.VISIBLE
        );

        // Display the attached image/GIF when item_reply.xml has reply_media.
        ImageView replyMedia = item.findViewById(R.id.reply_media);

        if (replyMedia != null) {
            String mediaUrl = reply.isNull("mediaUrl")
                    ? ""
                    : reply.optString("mediaUrl", "");

            if (mediaUrl.trim().isEmpty()) {
                Glide.with(this).clear(replyMedia);
                replyMedia.setVisibility(View.GONE);
            } else {
                replyMedia.setVisibility(View.VISIBLE);

                Glide.with(this)
                        .load(ImageUtils.fullUrl(mediaUrl))
                        .into(replyMedia);
            }
        }

        View.OnClickListener openProfile = v -> {
            if (!replyUsername.isEmpty() && isAdded()) {
                ((MainActivity) requireActivity())
                        .showUserProfile(replyUsername);
            }
        };

        replyAvatar.setOnClickListener(openProfile);
        item.findViewById(R.id.reply_author_row)
                .setOnClickListener(openProfile);

        repliesContainer.addView(item);
    }

    private void updateEmptyState() {
        repliesEmpty.setVisibility(
                repliesContainer.getChildCount() == 0
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    // Enable Reply if there is text OR an attached image/GIF.
    private void updateReplyButton() {
        if (replyInput == null || replyButton == null) {
            return;
        }

        String text = replyInput.getText().toString();

        boolean hasText = !text.trim().isEmpty();
        boolean hasMedia = selectedReplyMediaUri != null;
        boolean validLength = text.length() <= MAX_REPLY_LENGTH;

        replyButton.setEnabled(
                (hasText || hasMedia)
                        && validLength
                        && !sendingReply
        );

        if (replyMediaButton != null) {
            replyMediaButton.setEnabled(!sendingReply);
            replyMediaButton.setAlpha(sendingReply ? 0.5f : 1.0f);
        }

        replyInput.setTextColor(Color.parseColor(
                text.length() > MAX_REPLY_LENGTH
                        ? "#F4212E"
                        : "#0F1419"
        ));
    }

    private void clearSelectedReplyMedia() {
        selectedReplyMediaUri = null;

        if (replyMediaPreview != null) {
            Glide.with(this).clear(replyMediaPreview);
            replyMediaPreview.setImageDrawable(null);
        }

        if (replyMediaPreviewContainer != null) {
            replyMediaPreviewContainer.setVisibility(View.GONE);
        }

        updateReplyButton();
    }

    private void focusReplyInput() {
        replyInput.requestFocus();

        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (imm != null) {
            imm.showSoftInput(
                    replyInput,
                    InputMethodManager.SHOW_IMPLICIT
            );
        }
    }

    private void addReply() {
        String reply = replyInput.getText().toString().trim();

        boolean hasMedia = selectedReplyMediaUri != null;

        if (reply.isEmpty() && !hasMedia) {
            return;
        }

        if (reply.length() > MAX_REPLY_LENGTH) {
            Toast.makeText(
                    requireContext(),
                    "Reply must be 280 characters or fewer",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (sendingReply) {
            return;
        }

        if (!new SessionManager(requireContext()).isLoggedIn()) {
            Toast.makeText(
                    requireContext(),
                    "Please log in to reply",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        sendingReply = true;
        updateReplyButton();

        if (hasMedia) {
            // Multipart request: text is optional when media is attached.
            try {
                apiService.createReplyWithMedia(
                        tweet.getId(),
                        reply,
                        selectedReplyMediaUri,
                        this::handleReplySuccess,
                        this::handleReplyError
                );
            } catch (IOException e) {
                sendingReply = false;
                updateReplyButton();

                Toast.makeText(
                        requireContext(),
                        e.getMessage() == null
                                ? "Could not read the selected media"
                                : e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }

            return;
        }

        // Keep the existing JSON request for text-only replies.
        JSONObject body = new JSONObject();

        try {
            body.put("content", reply);
        } catch (JSONException e) {
            sendingReply = false;
            updateReplyButton();

            Toast.makeText(
                    requireContext(),
                    "Could not prepare reply",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        apiService.createReply(
                tweet.getId(),
                body,
                this::handleReplySuccess,
                this::handleReplyError
        );
    }

    private void handleReplySuccess(JSONObject response) {
        if (!isAdded()) {
            return;
        }

        sendingReply = false;

        replyInput.setText("");
        clearSelectedReplyMedia();
        updateReplyButton();
        hideKeyboard();

        tweet.setReplyCount(
                response.optInt(
                        "replyCount",
                        tweet.getReplyCount() + 1
                )
        );

        bindStats();

        // Show the saved reply immediately, at the bottom of the thread.
        JSONObject saved = response.optJSONObject("reply");

        if (saved != null) {
            addReplyView(saved);
            updateEmptyState();

            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        } else {
            loadReplies();
        }

        Toast.makeText(
                requireContext(),
                "Your reply was sent",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void handleReplyError(VolleyError error) {
        if (!isAdded()) {
            return;
        }

        sendingReply = false;
        updateReplyButton();

        showError("Could not send reply", error);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (imm != null) {
            imm.hideSoftInputFromWindow(
                    replyInput.getWindowToken(),
                    0
            );
        }

        replyInput.clearFocus();
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
