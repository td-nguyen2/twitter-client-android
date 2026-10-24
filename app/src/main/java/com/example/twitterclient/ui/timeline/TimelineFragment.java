
package com.example.twitterclient.ui.timeline;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TimelineFragment extends Fragment {

    private TweetAdapter adapter;
    private RecyclerView recyclerView;
    private TweetRepository repository;
    private ApiService apiService;

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

        view.findViewById(R.id.compose_button)
                .setOnClickListener(v -> showComposeDialog());

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

        loadTweets();
    }

    private void loadTweets() {
        repository.getTweets(new TweetRepository.Callback<List<Tweet>>() {
            @Override
            public void onSuccess(List<Tweet> tweets) {
                if (!isAdded() || recyclerView == null) {
                    return;
                }

                adapter.replaceTweets(tweets);
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        });
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

    private void showComposeDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(R.string.compose_hint);
        input.setMinLines(3);
        input.setMaxLines(5);

        int padding = (int) (
                16 * getResources().getDisplayMetrics().density
        );
        input.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.new_tweet)
                .setView(input)
                .setNegativeButton(
                        R.string.cancel,
                        (d, which) -> d.dismiss()
                )
                .setPositiveButton(R.string.post, null)
                .create();

        dialog.setOnShowListener(v ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(button -> {
                            String text = input.getText()
                                    .toString()
                                    .trim();

                            if (text.isEmpty()) {
                                input.setError(
                                        getString(R.string.empty_tweet)
                                );
                                return;
                            }

                            if (text.length() > 280) {
                                input.setError(
                                        getString(R.string.tweet_too_long)
                                );
                                return;
                            }

                            dialog.getButton(
                                    AlertDialog.BUTTON_POSITIVE
                            ).setEnabled(false);

                            repository.createTweet(
                                    text,
                                    new TweetRepository.Callback<Tweet>() {
                                        @Override
                                        public void onSuccess(Tweet tweet) {
                                            if (!isAdded()) {
                                                return;
                                            }

                                            dialog.dismiss();
                                            showMessage(
                                                    getString(R.string.post_saved)
                                            );
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

                                            dialog.getButton(
                                                    AlertDialog.BUTTON_POSITIVE
                                            ).setEnabled(true);

                                            showMessage(message);
                                        }
                                    }
                            );
                        })
        );

        dialog.show();
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
        super.onDestroyView();
    }
}
