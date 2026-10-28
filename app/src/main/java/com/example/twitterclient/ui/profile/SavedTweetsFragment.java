package com.example.twitterclient.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.MainActivity;
import com.example.twitterclient.R;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.network.ApiService;
import com.example.twitterclient.ui.timeline.TweetAdapter;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class SavedTweetsFragment extends Fragment {
    private ApiService apiService;
    private TweetAdapter adapter;
    private RecyclerView recyclerView;
    private View loadingState;
    private View errorState;
    private TextView emptyState;
    private boolean firstResume = true;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved_tweets, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        apiService = new ApiService(requireContext());
        recyclerView = view.findViewById(R.id.saved_tweets_list);
        loadingState = view.findViewById(R.id.saved_loading_state);
        errorState = view.findViewById(R.id.saved_error_state);
        emptyState = view.findViewById(R.id.saved_empty_state);

        adapter = new TweetAdapter();
        adapter.setRemoveUnbookmarked(true);
        adapter.setOnBookmarksChangedListener(this::updateEmptyState);
        adapter.setOnAuthorClickListener(handle ->
                ((MainActivity) requireActivity()).showUserProfile(handle));
        adapter.setOnTweetClickListener(tweet ->
                ((MainActivity) requireActivity()).showPostDetail(tweet));

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.saved_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());
        view.findViewById(R.id.saved_retry).setOnClickListener(v -> loadBookmarks());

        loadBookmarks();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else {
            loadBookmarks();
        }
    }

    private void loadBookmarks() {
        loadingState.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        apiService.getBookmarks(
                response -> {
                    if (!isAdded() || recyclerView == null) {
                        return;
                    }

                    JSONArray posts = response.optJSONArray("posts");
                    if (posts == null) {
                        showError();
                        return;
                    }

                    try {
                        List<Tweet> tweets = new ArrayList<>();
                        for (int i = 0; i < posts.length(); i++) {
                            tweets.add(new Tweet(posts.getJSONObject(i)));
                        }

                        loadingState.setVisibility(View.GONE);
                        errorState.setVisibility(View.GONE);
                        adapter.replaceTweets(tweets);
                        recyclerView.setVisibility(tweets.isEmpty() ? View.GONE : View.VISIBLE);
                        updateEmptyState();
                    } catch (JSONException error) {
                        showError();
                    }
                },
                error -> {
                    if (isAdded()) {
                        showError();
                    }
                }
        );
    }

    private void showError() {
        if (loadingState == null || errorState == null || recyclerView == null) {
            return;
        }
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
    }

    private void updateEmptyState() {
        if (emptyState != null && adapter != null) {
            emptyState.setVisibility(
                    adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE
            );
        }
    }
}
