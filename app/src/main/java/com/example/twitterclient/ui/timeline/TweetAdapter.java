package com.example.twitterclient.ui.timeline;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.R;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.repository.TweetRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * List of tweets used by the timeline and by profiles.
 * Reply, repost and like are handled here, so every screen that shows
 * this adapter gets the same behaviour.
 */
public class TweetAdapter extends RecyclerView.Adapter<TweetViewHolder> {

    // Tells onBindViewHolder to refresh only the counters
    private static final Object PAYLOAD_STATS = new Object();

    private final List<Tweet> tweets = new ArrayList<>();

    public interface OnAuthorClickListener {
        void onAuthorClick(String username);
    }

    public interface OnTweetClickListener {
        void onTweetClick(Tweet tweet);
    }

    private OnAuthorClickListener authorClickListener;
    private OnTweetClickListener tweetClickListener;
    private boolean removeUnbookmarked;
    private Runnable bookmarksChangedListener;

    public void setOnAuthorClickListener(OnAuthorClickListener listener) {
        authorClickListener = listener;
    }

    public void setOnTweetClickListener(OnTweetClickListener listener) {
        tweetClickListener = listener;
    }

    public void setRemoveUnbookmarked(boolean removeUnbookmarked) {
        this.removeUnbookmarked = removeUnbookmarked;
    }

    public void setOnBookmarksChangedListener(Runnable listener) {
        bookmarksChangedListener = listener;
    }

    @NonNull
    @Override
    public TweetViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tweet, parent, false);

        return new TweetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TweetViewHolder holder, int position) {
        Tweet tweet = tweets.get(position);
        Context context = holder.itemView.getContext();

        // Bind the tweet and handle the Like click
        holder.bind(tweet, clickedTweet -> {
            TweetRepository repo = new TweetRepository(context);

            repo.toggleLike(clickedTweet.getId(), response -> {
                // The server answers with likedByMe and likeCount
                clickedTweet.setLikedByMe(response.optBoolean("likedByMe", false));
                clickedTweet.setLikeCount(
                        response.optInt("likeCount", clickedTweet.getLikeCount())
                );

                onTweetStatsChanged(clickedTweet);
            }, error -> Toast.makeText(
                    context,
                    "Could not like this post",
                    Toast.LENGTH_SHORT
            ).show());
        });

        holder.setOnAuthorClickListener(v -> {
            if (authorClickListener != null) {
                authorClickListener.onAuthorClick(tweet.getUsername());
            }
        });

        holder.setOnTweetClickListener(v -> {
            if (tweetClickListener != null) {
                tweetClickListener.onTweetClick(tweet);
            }
        });

        // Reply opens the reply composer (the post itself opens by tapping its body)
        holder.setOnReplyClickListener(v ->
                TweetActions.showReplyDialog(context, tweet, this::onTweetStatsChanged)
        );

        // Repost opens the "Repost / Undo repost" sheet
        holder.setOnRepostClickListener(v ->
                TweetActions.showRepostMenu(context, tweet, this::onTweetStatsChanged)
        );

        holder.setOnShareClickListener(v -> TweetActions.share(context, tweet));
        holder.setOnMoreClickListener(v ->
                TweetActions.showBookmarkMenu(
                        context,
                        v,
                        tweet,
                        this::onTweetStatsChanged
                )
        );
        holder.setOnBookmarkClickListener(v ->
                TweetActions.setBookmarked(
                        context,
                        tweet,
                        !tweet.isBookmarkedByMe(),
                        this::onTweetStatsChanged
                )
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull TweetViewHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (payloads.contains(PAYLOAD_STATS)) {
            holder.updateStats(tweets.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    /**
     * A post can be on screen twice (the original and somebody's repost of it),
     * so copy the new numbers to every row of that post and redraw them.
     */
    private void onTweetStatsChanged(Tweet changed) {
        if (removeUnbookmarked && !changed.isBookmarkedByMe()) {
            for (int i = tweets.size() - 1; i >= 0; i--) {
                if (tweets.get(i).getId() == changed.getId()) {
                    tweets.remove(i);
                    notifyItemRemoved(i);
                }
            }
            if (bookmarksChangedListener != null) {
                bookmarksChangedListener.run();
            }
            return;
        }

        for (int i = 0; i < tweets.size(); i++) {
            Tweet row = tweets.get(i);

            if (row.getId() != changed.getId()) {
                continue;
            }

            if (row != changed) {
                row.copyStatsFrom(changed);
            }

            notifyItemChanged(i, PAYLOAD_STATS);
        }
    }

    @Override
    public int getItemCount() {
        return tweets.size();
    }

    public void replaceTweets(List<Tweet> newTweets) {
        tweets.clear();
        tweets.addAll(newTweets);
        notifyDataSetChanged();
    }
}
