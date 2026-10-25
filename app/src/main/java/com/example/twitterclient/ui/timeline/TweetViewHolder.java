package com.example.twitterclient.ui.timeline;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private final ImageView shareButton;
    private final LinearLayout btnLikeContainer;
    private final ImageView ivLikeIcon;
    private final TextView tvLikeCount;

    public TweetViewHolder(@NonNull View itemView) {
        super(itemView);

        displayName = itemView.findViewById(R.id.tweet_display_name);
        username = itemView.findViewById(R.id.tweet_username);
        content = itemView.findViewById(R.id.tweet_content);

        replyButton = itemView.findViewById(R.id.tweet_reply_button);
        repostButton = itemView.findViewById(R.id.tweet_repost_button);
        shareButton = itemView.findViewById(R.id.tweet_share_button);

        btnLikeContainer = itemView.findViewById(R.id.btn_like_container);
        ivLikeIcon = itemView.findViewById(R.id.iv_like_icon);
        tvLikeCount = itemView.findViewById(R.id.tv_like_count);
    }

    public void bind(Tweet tweet) {
        bind(tweet, null);
    }

    public void bind(Tweet tweet, OnLikeClickListener likeClickListener) {
        displayName.setText(tweet.getDisplayName());
        username.setText("@" + tweet.getUsername());
        content.setText(tweet.getText());

        tvLikeCount.setText(String.valueOf(tweet.getLikeCount()));
        updateLikeUI(tweet.isLikedByMe());

        if (btnLikeContainer != null) {
            btnLikeContainer.setOnClickListener(v -> {
                if (likeClickListener != null) {
                    likeClickListener.onLikeClick(tweet);
                }
            });
        }
    }

    public void updateLikeUI(boolean likedByMe) {
        int color = Color.parseColor(likedByMe ? "#F91880" : "#536471");
        ivLikeIcon.setImageResource(R.drawable.ui_icon_heart);
        ivLikeIcon.setImageTintList(ColorStateList.valueOf(color));
        tvLikeCount.setTextColor(color);
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
        btnLikeContainer.setOnClickListener(listener);
    }

    public void setOnShareClickListener(View.OnClickListener listener) {
        shareButton.setOnClickListener(listener);
    }

    public void setOnTweetClickListener(View.OnClickListener listener) {
        itemView.setOnClickListener(listener);
    }

    public interface OnLikeClickListener {
        void onLikeClick(Tweet tweet);
    }
}