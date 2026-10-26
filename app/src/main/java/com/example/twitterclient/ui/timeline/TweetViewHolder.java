package com.example.twitterclient.ui.timeline;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import androidx.annotation.Nullable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.R;
import com.example.twitterclient.model.Tweet;

public class TweetViewHolder extends RecyclerView.ViewHolder {
    private static final String SERVER_URL = "http://10.0.2.2:3000";
    private final TextView avatarFallback;
    private final ImageView avatarImage;

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

        avatarFallback = itemView.findViewById(R.id.tweet_avatar_fallback);
        avatarImage = itemView.findViewById(R.id.tweet_avatar_image);
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
        bindAvatar(tweet);

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
        avatarFallback.setOnClickListener(listener);
        avatarImage.setOnClickListener(listener);
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

    private void bindAvatar(Tweet tweet) {
        Glide.with(itemView).clear(avatarImage);
        avatarImage.setImageDrawable(null);
        avatarImage.setVisibility(View.INVISIBLE);
        avatarFallback.setVisibility(View.VISIBLE);
        String handle = tweet.getUsername();
        avatarFallback.setText(handle == null || handle.isEmpty() ? "?"
                : handle.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        String path = tweet.getProfileImageUrl();
        if (path == null || path.trim().isEmpty() || "null".equalsIgnoreCase(path)) return;
        String url;
        if (path.startsWith("/uploads/")) url = SERVER_URL + path;
        else if (path.startsWith("https://") || path.startsWith("http://")) url = path;
        else return;
        Glide.with(itemView).load(url).override(128, 128).circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(@Nullable GlideException e, Object model,
                            Target<Drawable> target, boolean first) {
                        Log.w("TWEET_AVATAR", "Avatar failed: " + url, e);
                        avatarImage.setVisibility(View.INVISIBLE);
                        avatarFallback.setVisibility(View.VISIBLE);
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable drawable, Object model,
                            Target<Drawable> target, DataSource dataSource, boolean first) {
                        avatarFallback.setVisibility(View.GONE);
                        avatarImage.setVisibility(View.VISIBLE);
                        return false;
                    }
                }).into(avatarImage);
    }

}
