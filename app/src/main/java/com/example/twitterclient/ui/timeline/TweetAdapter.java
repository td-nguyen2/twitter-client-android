
package com.example.twitterclient.ui.timeline;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.R;
import com.example.twitterclient.model.Tweet;

import java.util.ArrayList;
import java.util.List;

public class TweetAdapter extends RecyclerView.Adapter<TweetViewHolder> {

    private final List<Tweet> tweets = new ArrayList<>();

    public interface OnAuthorClickListener {
        void onAuthorClick(String username);
    }

    public interface OnTweetClickListener {
        void onTweetClick(Tweet tweet);
    }

    public interface OnRepostClickListener {
        void onRepostClick(Tweet tweet);
    }

    private OnAuthorClickListener authorClickListener;
    private OnTweetClickListener tweetClickListener;
    private OnRepostClickListener repostClickListener;

    public void setOnAuthorClickListener(OnAuthorClickListener listener) {
        authorClickListener = listener;
    }

    public void setOnTweetClickListener(OnTweetClickListener listener) {
        tweetClickListener = listener;
    }

    public void setOnRepostClickListener(OnRepostClickListener listener) {
        repostClickListener = listener;
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
    public void onBindViewHolder(
            @NonNull TweetViewHolder holder,
            int position
    ) {
        Tweet tweet = tweets.get(position);

        holder.bind(tweet);

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

        holder.setOnRepostClickListener(v -> {
            if (repostClickListener != null) {
                repostClickListener.onRepostClick(tweet);
            }
        });
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
