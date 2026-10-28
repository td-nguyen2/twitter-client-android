package com.example.twitterclient.ui.timeline;

import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import com.example.twitterclient.repository.TweetRepository;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.twitterclient.repository.ProfileRepository;


public class TimelineFragment extends Fragment {
    private Uri selectedImageUri;
    private ImageView imagePreview;
    private FrameLayout previewContainer;
    private TweetAdapter adapter;
    private RecyclerView recyclerView;
    private TweetRepository repository;
    private ApiService apiService;

    // false = "For you" (all posts), true = "Following" (followed users only)
    private boolean showingFollowing = false;

    private TextView tabForYouText;
    private TextView tabFollowingText;
    private View indicatorForYou;
    private View indicatorFollowing;
    private TextView emptyMessage;
    private View loadingState;
    private View errorState;
    private TextView errorMessage;
    private int loadGeneration = 0;
    private boolean firstResume = true;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedImageUri = uri;

                            if (imagePreview != null) {
                                imagePreview.setImageURI(uri);
                                previewContainer.setVisibility(View.VISIBLE);
                            }
                        }
                    }
            );

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_timeline,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        ImageView composerAvatar = view.findViewById(R.id.composer_avatar);

        ProfileRepository profileRepository =
                new ProfileRepository(requireContext());

        profileRepository.getMyProfile(
                response -> {
                    if (!isAdded()) return;

                    JSONObject user = response.optJSONObject("user");
                    if (user == null) return;

                    String avatarUrl = user.optString("profileImageUrl", "");

                    if (!avatarUrl.isEmpty() && !avatarUrl.equals("null")) {
                        String fullUrl = avatarUrl.startsWith("http")
                                ? avatarUrl
                                : "http://10.0.2.2:3000" + avatarUrl;

                        Glide.with(this)
                                .load(fullUrl)
                                .transform(new CircleCrop())
                                .placeholder(R.drawable.ui_default_avatar)
                                .error(R.drawable.ui_default_avatar)
                                .into(composerAvatar);
                    } else {
                        Glide.with(this).clear(composerAvatar);
                        composerAvatar.setImageResource(R.drawable.ui_default_avatar);
                    }
                },
                error -> {
                    if (!isAdded()) return;
                    Glide.with(this).clear(composerAvatar);
                    composerAvatar.setImageResource(R.drawable.ui_default_avatar);
                }
        );
        repository = new TweetRepository(requireContext());
        apiService = new ApiService(requireContext());

        adapter = new TweetAdapter();

        adapter.setOnAuthorClickListener(username ->
                ((MainActivity) requireActivity())
                        .showUserProfile(username)
        );

        adapter.setOnTweetClickListener(tweet ->
                ((MainActivity) requireActivity())
                        .showPostDetail(tweet)
        );

        recyclerView = view.findViewById(R.id.timeline_recycler_view);
        recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        recyclerView.setAdapter(adapter);

        View mediaButton = view.findViewById(R.id.compose_media_button);

        mediaButton.setOnClickListener(v -> {
            imagePicker.launch("image/*");
        });

        imagePreview = view.findViewById(R.id.compose_image_preview);
        previewContainer = view.findViewById(R.id.compose_preview_container);

        view.findViewById(R.id.compose_remove_image).setOnClickListener(v -> {
            selectedImageUri = null;
            imagePreview.setImageDrawable(null);
            previewContainer.setVisibility(View.GONE);
        });

        if (selectedImageUri != null) {
            imagePreview.setImageURI(selectedImageUri);
            previewContainer.setVisibility(View.VISIBLE);
        }

        EditText composeInput = view.findViewById(R.id.compose_input);
        View postButton = view.findViewById(R.id.compose_button);

        postButton.setOnClickListener(v -> {
            String text = composeInput.getText().toString().trim();

            // A tweet must contain text, media, or both
            if (text.isEmpty() && selectedImageUri == null) {
                composeInput.setError("Add text or an image");
                return;
            }

            if (text.length() > 280) {
                composeInput.setError(getString(R.string.tweet_too_long));
                return;
            }

            postButton.setEnabled(false);

            // Remember which image belongs to this post
            Uri postingImageUri = selectedImageUri;

            TweetRepository.Callback<Tweet> postCallback =
                    new TweetRepository.Callback<Tweet>() {

                        @Override
                        public void onSuccess(Tweet tweet) {
                            if (!isAdded()) {
                                return;
                            }

                            postButton.setEnabled(true);
                            composeInput.setText("");

                            // Clear selected media after successful posting
                            selectedImageUri = null;
                            imagePreview.setImageDrawable(null);
                            previewContainer.setVisibility(View.GONE);

                            showMessage(getString(R.string.post_saved));
                            loadTweets();

                            if (recyclerView != null) {
                                recyclerView.scrollToPosition(0);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (!isAdded()) {
                                return;
                            }

                            postButton.setEnabled(true);
                            showMessage(message);
                        }
                    };

            // Choose the appropriate posting method
            if (postingImageUri != null) {
                repository.createTweetWithMedia(
                        text,
                        postingImageUri,
                        postCallback
                );
            } else {
                repository.createTweet(
                        text,
                        postCallback
                );
            }
        });

        TextView logoutButton = view.findViewById(R.id.buttonLogout);
        logoutButton.setOnClickListener(v -> {
            SessionManager session =
                    new SessionManager(requireContext());

            session.logout();
            ((MainActivity) requireActivity()).showLogin();
        });

        TextView profileButton = view.findViewById(R.id.buttonProfile);
        profileButton.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showProfile()
        );

        tabForYouText = view.findViewById(R.id.tab_for_you_text);
        tabFollowingText = view.findViewById(R.id.tab_following_text);
        indicatorForYou = view.findViewById(R.id.indicator_for_you);
        indicatorFollowing = view.findViewById(R.id.indicator_following);
        emptyMessage = view.findViewById(R.id.timeline_empty_message);
        loadingState = view.findViewById(R.id.timeline_loading_state);
        errorState = view.findViewById(R.id.timeline_error_state);
        errorMessage = view.findViewById(R.id.timeline_error_message);
        view.findViewById(R.id.timeline_retry_button).setOnClickListener(v -> loadTweets());

        view.findViewById(R.id.tab_for_you)
                .setOnClickListener(v -> selectTab(false));
        view.findViewById(R.id.tab_following)
                .setOnClickListener(v -> selectTab(true));

        // Bottom navigation
        view.findViewById(R.id.nav_home).setOnClickListener(v -> {
            if (recyclerView != null) {
                recyclerView.scrollToPosition(0);
            }
            loadTweets();
        });
        view.findViewById(R.id.nav_notifications).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showNotifications()
        );
        view.findViewById(R.id.nav_profile).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showProfile()
        );

        updateTabStyles();
        loadTweets();
    }

    private void selectTab(boolean following) {
        showingFollowing = following;
        updateTabStyles();

        // Clear the old list so posts from the other tab never flash up
        adapter.replaceTweets(new ArrayList<Tweet>());
        updateEmptyMessage(false);

        loadTweets();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else if (recyclerView != null) {
            loadTweets();
        }
    }

    private void updateTabStyles() {
        if (tabForYouText == null || tabFollowingText == null) {
            return;
        }

        styleTab(tabForYouText, indicatorForYou, !showingFollowing);
        styleTab(tabFollowingText, indicatorFollowing, showingFollowing);
    }

    private void styleTab(TextView label, View indicator, boolean selected) {
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        label.setTextColor(Color.parseColor(selected ? "#0F1419" : "#536471"));
        indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateEmptyMessage(boolean empty) {
        if (emptyMessage == null) {
            return;
        }

        emptyMessage.setText(
                showingFollowing
                        ? "No posts yet. Follow people from their profile to see their posts here."
                        : "No posts yet."
        );
        emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showTimelineLoading() {
        loadingState.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
        emptyMessage.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showTimelineError() {
        loadingState.setVisibility(View.GONE);
        emptyMessage.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorMessage.setText("Couldn’t load posts. Check your connection and try again.");
        errorState.setVisibility(View.VISIBLE);
    }

    // Loads the tab that is currently selected
    private void loadTweets() {
        final boolean following = showingFollowing;
        final int generation = ++loadGeneration;
        showTimelineLoading();

        TweetRepository.Callback<List<Tweet>> callback =
                new TweetRepository.Callback<List<Tweet>>() {
                    @Override
                    public void onSuccess(List<Tweet> tweets) {
                        // Ignore answers for a tab the user has already left
                        if (!isAdded()
                                || recyclerView == null
                                || generation != loadGeneration
                                || following != showingFollowing) {
                            return;
                        }

                        loadingState.setVisibility(View.GONE);
                        errorState.setVisibility(View.GONE);
                        adapter.replaceTweets(tweets);
                        recyclerView.setVisibility(tweets.isEmpty() ? View.GONE : View.VISIBLE);
                        updateEmptyMessage(tweets.isEmpty());
                    }

                    @Override
                    public void onError(String message) {
                        if (isAdded()
                                && generation == loadGeneration
                                && following == showingFollowing) {
                            showTimelineError();
                        }
                    }
                };

        if (following) {
            repository.getFollowingTweets(callback);
        } else {
            repository.getTweets(callback);
        }
    }

    private void showMessage(String message) {
        if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        recyclerView = null;
        tabForYouText = null;
        tabFollowingText = null;
        indicatorForYou = null;
        indicatorFollowing = null;
        emptyMessage = null;
        loadingState = null;
        errorState = null;
        errorMessage = null;
        loadGeneration++;
        super.onDestroyView();
    }
}
