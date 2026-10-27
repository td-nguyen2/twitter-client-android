package com.example.twitterclient.ui.timeline;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.R;
import com.example.twitterclient.auth.SessionManager;
import com.example.twitterclient.model.Tweet;
import com.example.twitterclient.util.TimeUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

public class TweetViewHolder extends RecyclerView.ViewHolder {

    private final TextView displayName;
    private final TextView username;
    private final TextView content;
    private final ImageView tweetMedia;
    private final ImageView tweetAvatar;
    private final TextView time;
    private final View replyButton;
    private final View repostButton;
    private final ImageView ivRepostIcon;
    private final TextView tvReplyCount;
    private final TextView tvRepostCount;
    private final ImageView shareButton;
    private final LinearLayout btnLikeContainer;
    private final ImageView ivLikeIcon;
    private final TextView tvLikeCount;
    private final View repostHeader;
    private final TextView repostLabel;

    public TweetViewHolder(@NonNull View itemView) {
        super(itemView);

        displayName = itemView.findViewById(R.id.tweet_display_name);
        username = itemView.findViewById(R.id.tweet_username);
        content = itemView.findViewById(R.id.tweet_content);
        tweetMedia = itemView.findViewById(R.id.tweet_media);
        tweetAvatar = itemView.findViewById(R.id.tweet_avatar);
        time = itemView.findViewById(R.id.tweet_time);
        replyButton = itemView.findViewById(R.id.btn_reply_container);
        repostButton = itemView.findViewById(R.id.btn_repost_container);
        ivRepostIcon = itemView.findViewById(R.id.iv_repost_icon);
        tvReplyCount = itemView.findViewById(R.id.tv_reply_count);
        tvRepostCount = itemView.findViewById(R.id.tv_repost_count);
        shareButton = itemView.findViewById(R.id.tweet_share_button);

        btnLikeContainer = itemView.findViewById(R.id.btn_like_container);
        ivLikeIcon = itemView.findViewById(R.id.iv_like_icon);
        tvLikeCount = itemView.findViewById(R.id.tv_like_count);

        repostHeader = itemView.findViewById(R.id.tweet_repost_header);
        repostLabel = itemView.findViewById(R.id.tweet_repost_label);
    }

    public void bind(Tweet tweet) {
        bind(tweet, null);
    }

    public void bind(Tweet tweet, OnLikeClickListener likeClickListener) {
        displayName.setText(tweet.getDisplayName());
        username.setText("@" + tweet.getUsername());

        String ago = TimeUtils.shortAgo(tweet.getCreatedAt());
        time.setText(ago.isEmpty() ? "" : "\u00B7 " + ago);

        String profileImageUrl = tweet.getProfileImageUrl();

        if (profileImageUrl != null
                && !profileImageUrl.trim().isEmpty()
                && !profileImageUrl.equals("null")) {

            String fullAvatarUrl = profileImageUrl.startsWith("http")
                    ? profileImageUrl
                    : "http://10.0.2.2:3000" + profileImageUrl;

            Glide.with(itemView)
                    .load(fullAvatarUrl)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ui_default_avatar)
                    .error(R.drawable.ui_default_avatar)
                    .into(tweetAvatar);

        } else {
            Glide.with(itemView).clear(tweetAvatar);
            tweetAvatar.setImageResource(R.drawable.ui_default_avatar);
        }
        // "X reposted" line, shown only on rows that are reposts
        String repostedBy = tweet.getRepostedBy();

        if (repostHeader != null && repostLabel != null) {
            if (repostedBy == null || repostedBy.trim().isEmpty()) {
                repostHeader.setVisibility(View.GONE);
            } else {
                String me = new SessionManager(itemView.getContext()).getUsername();
                boolean byMe = me != null
                        && me.equalsIgnoreCase(tweet.getRepostedByUsername());

                repostLabel.setText(byMe ? "You reposted" : repostedBy + " reposted");
                repostHeader.setVisibility(View.VISIBLE);
            }
        }

        String tweetText = tweet.getText();

        if (tweetText == null || tweetText.trim().isEmpty()) {
            content.setVisibility(View.GONE);
        } else {
            content.setText(tweetText);
            content.setVisibility(View.VISIBLE);
        }
        String mediaUrl = tweet.getMediaUrl();

        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {

            // Cancel any previous image loading
            Glide.with(itemView).clear(tweetMedia);

            // Hide the media view AFTER clearing Glide
            tweetMedia.setImageDrawable(null);
            tweetMedia.setVisibility(View.GONE);

        } else {

            tweetMedia.setVisibility(View.VISIBLE);

            String fullUrl = "http://10.0.2.2:3000" + mediaUrl;

            Glide.with(itemView)
                    .load(fullUrl)
                    .into(tweetMedia);
        }
        updateStats(tweet);

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

    /** Redraws only the counters and the like/repost colours (no image reload). */
    public void updateStats(Tweet tweet) {
        tvLikeCount.setText(countText(tweet.getLikeCount()));
        updateLikeUI(tweet.isLikedByMe());
        updateReplyUI(tweet.getReplyCount());
        updateRepostUI(tweet.isRepostedByMe(), tweet.getRepostCount());
    }

    // A zero count is hidden, like on Twitter
    private static String countText(int count) {
        return count > 0 ? TimeUtils.compactCount(count) : "";
    }

    public void updateReplyUI(int replyCount) {
        tvReplyCount.setText(countText(replyCount));
    }

    // Green icon and number while you have reposted the tweet
    public void updateRepostUI(boolean repostedByMe, int repostCount) {
        int color = Color.parseColor(repostedByMe ? "#00BA7C" : "#536471");
        ivRepostIcon.setImageTintList(ColorStateList.valueOf(color));
        tvRepostCount.setTextColor(color);
        tvRepostCount.setText(countText(repostCount));
        tvRepostCount.setTypeface(null, repostedByMe ? Typeface.BOLD : Typeface.NORMAL);
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
