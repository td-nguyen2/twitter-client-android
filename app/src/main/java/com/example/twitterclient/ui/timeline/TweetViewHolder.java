package com.example.twitterclient.ui.timeline;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.R;
import com.example.twitterclient.model.Tweet;

public class TweetViewHolder extends RecyclerView.ViewHolder {

    private final TextView displayName;
    private final TextView username;
    private final TextView content;
    private final ImageView replyButton;
    private final ImageView repostButton;
    private final ImageView likeButton;
    private final ImageView shareButton;

    public TweetViewHolder(@NonNull View itemView) {
        super(itemView);

        displayName = itemView.findViewById(R.id.tweet_display_name);
        username = itemView.findViewById(R.id.tweet_username);
        content = itemView.findViewById(R.id.tweet_content);

        replyButton = itemView.findViewById(R.id.tweet_reply_button);
        repostButton = itemView.findViewById(R.id.tweet_repost_button);
        likeButton = itemView.findViewById(R.id.tweet_like_button);
        shareButton = itemView.findViewById(R.id.tweet_share_button);
    }

    public void bind(Tweet tweet) {
        displayName.setText(tweet.getDisplayName());
        username.setText("@" + tweet.getUsername());
        content.setText(tweet.getText());
    }

    public void setOnAuthorClickListener(View.OnClickListener listener) {
        displayName.setOnClickListener(listener);
        username.setOnClickListener(listener);
    }

    public void setOnReplyClickListener(View.OnClickListener listener) {
        replyButton.setOnClickListener(listener);
    }

    public void setOnRepostClickListener(View.OnClickListener listener) {
        repostButton.setOnClickListener(listener);
    }

    public void setOnLikeClickListener(View.OnClickListener listener) {
        likeButton.setOnClickListener(listener);
    }

    public void setOnShareClickListener(View.OnClickListener listener) {
        shareButton.setOnClickListener(listener);
    }
}