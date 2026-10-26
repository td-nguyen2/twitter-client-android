package com.example.twitterclient.ui.timeline;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Response;
import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import com.example.twitterclient.repository.TweetRepository;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TimelineFragment extends Fragment {

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

    private final Set<Long> repostRequestsInProgress = new HashSet<>();

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

        adapter.setOnRepostClickListener(this::toggleRepost);

        recyclerView = view.findViewById(R.id.timeline_recycler_view);
        recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        recyclerView.setAdapter(adapter);

        EditText composeInput = view.findViewById(R.id.compose_input);
        View postButton = view.findViewById(R.id.compose_button);

        postButton.setOnClickListener(v -> {
            String text = composeInput.getText().toString().trim();

            if (text.isEmpty()) {
                composeInput.setError(getString(R.string.empty_tweet));
                return;
            }

            if (text.length() > 280) {
                composeInput.setError(getString(R.string.tweet_too_long));
                return;
            }

            postButton.setEnabled(false);

            repository.createTweet(
                    text,
                    new TweetRepository.Callback<Tweet>() {
                        @Override
                        public void onSuccess(Tweet tweet) {
                            if (!isAdded()) {
                                return;
                            }

                            postButton.setEnabled(true);
                            composeInput.setText("");

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
                    }
            );
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

    // Loads the tab that is currently selected
    private void loadTweets() {
        final boolean following = showingFollowing;

        TweetRepository.Callback<List<Tweet>> callback =
                new TweetRepository.Callback<List<Tweet>>() {
                    @Override
                    public void onSuccess(List<Tweet> tweets) {
                        // Ignore answers for a tab the user has already left
                        if (!isAdded()
                                || recyclerView == null
                                || following != showingFollowing) {
                            return;
                        }

                        adapter.replaceTweets(tweets);
                        updateEmptyMessage(tweets.isEmpty());
                    }

                    @Override
                    public void onError(String message) {
                        if (following == showingFollowing) {
                            showMessage(message);
                        }
                    }
                };

        if (following) {
            repository.getFollowingTweets(callback);
        } else {
            repository.getTweets(callback);
        }
    }

    private void toggleRepost(Tweet tweet) {
        // This project version uses numeric post IDs.
        long postId = tweet.getId();

        if (repostRequestsInProgress.contains(postId)) {
            return;
        }

        SessionManager session = new SessionManager(requireContext());

        if (!session.isLoggedIn()) {
            showMessage("Please log in to repost.");
            return;
        }

        repostRequestsInProgress.add(postId);

        // Ask the server for the current state instead of assuming
        // the post has not been reposted before.
        apiService.getRepostStatus(
                postId,
                response -> {
                    if (!isAdded()) {
                        repostRequestsInProgress.remove(postId);
                        return;
                    }

                    boolean alreadyReposted =
                            response.optBoolean("reposted", false);

                    Response.Listener<JSONObject> success = result -> {
                        repostRequestsInProgress.remove(postId);

                        if (!isAdded()) {
                            return;
                        }

                        showMessage(
                                alreadyReposted
                                        ? "Repost undone."
                                        : "Post reposted."
                        );

                        loadTweets();
                    };

                    Response.ErrorListener failure = error -> {
                        repostRequestsInProgress.remove(postId);

                        if (isAdded()) {
                            showMessage(
                                    "Could not update repost. Please try again."
                            );
                        }
                    };

                    if (alreadyReposted) {
                        apiService.undoRepost(
                                postId,
                                success,
                                failure
                        );
                    } else {
                        apiService.repost(
                                postId,
                                success,
                                failure
                        );
                    }
                },
                error -> {
                    repostRequestsInProgress.remove(postId);

                    if (isAdded()) {
                        int status = error.networkResponse != null
                                ? error.networkResponse.statusCode : -1;

                        if (status == 401 || status == 403) {
                            showMessage("Please log in again to repost.");
                        } else {
                            showMessage(
                                    "Could not check repost status. Please try again."
                            );
                        }
                    }
                }
        );
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
        super.onDestroyView();
    }
}
