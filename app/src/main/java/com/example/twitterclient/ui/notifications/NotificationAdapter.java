package com.example.twitterclient.ui.notifications;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twitterclient.R;
import com.example.twitterclient.model.AppNotification;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NotificationAdapter
        extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnActorClickListener {
        void onActorClick(String username);
    }

    public interface OnPostClickListener {
        void onPostClick(long postId);
    }

    private final List<AppNotification> items = new ArrayList<>();
    private OnActorClickListener actorClickListener;
    private OnPostClickListener postClickListener;

    public void setOnPostClickListener(OnPostClickListener listener) {
        postClickListener = listener;
    }

    public void setOnActorClickListener(OnActorClickListener listener) {
        actorClickListener = listener;
    }

    public void replaceItems(List<AppNotification> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppNotification item = items.get(position);

        String name = item.getActorDisplayName();
        String type = item.getType();

        String action;
        String icon;
        String iconColor;

        if (AppNotification.TYPE_LIKE.equals(type)) {
            action = " liked your post";
            icon = "\u2665";
            iconColor = "#F91880";
        } else if (AppNotification.TYPE_REPLY.equals(type)) {
            action = " replied to your post";
            icon = "\u21A9";
            iconColor = "#1D9BF0";
        } else if (AppNotification.TYPE_REPOST.equals(type)) {
            action = " reposted your post";
            icon = "\u21BB";
            iconColor = "#00BA7C";
        } else {
            action = " followed you";
            icon = "+";
            iconColor = "#1D9BF0";
        }

        // Bold name followed by a normal-weight action
        SpannableStringBuilder text = new SpannableStringBuilder(name + action);
        text.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                name.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        holder.text.setText(text);

        // Heart for likes, arrows for replies/reposts, plus sign for follows
        holder.typeIcon.setText(icon);
        holder.typeIcon.setTextColor(Color.parseColor(iconColor));

        String initial = name.isEmpty()
                ? "?"
                : name.substring(0, 1).toUpperCase(Locale.getDefault());
        holder.avatar.setText(initial);

        String snippet = item.getPostSnippet();
        if (snippet.isEmpty()) {
            holder.snippet.setVisibility(View.GONE);
        } else {
            holder.snippet.setText(snippet);
            holder.snippet.setVisibility(View.VISIBLE);
        }

        holder.time.setText(timeAgo(item.getCreatedAt()));

        // Likes, replies and reposts open the post; follows open the profile
        holder.itemView.setOnClickListener(v -> {
            if (item.getPostId() > 0 && postClickListener != null) {
                postClickListener.onPostClick(item.getPostId());
            } else if (actorClickListener != null) {
                actorClickListener.onActorClick(item.getActorUsername());
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // The server sends UTC timestamps like 2026-10-25T16:26:53.000Z
    private static String timeAgo(String iso) {
        if (iso == null || iso.length() < 19) {
            return "";
        }

        try {
            SimpleDateFormat format =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date date = format.parse(iso.substring(0, 19));
            if (date == null) {
                return "";
            }

            long minutes =
                    (System.currentTimeMillis() - date.getTime()) / 60000L;

            if (minutes < 1) {
                return "just now";
            }
            if (minutes < 60) {
                return minutes + "m ago";
            }
            if (minutes < 60 * 24) {
                return (minutes / 60) + "h ago";
            }
            return (minutes / (60 * 24)) + "d ago";

        } catch (ParseException error) {
            return "";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView avatar;
        final TextView typeIcon;
        final TextView text;
        final TextView snippet;
        final TextView time;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            avatar = itemView.findViewById(R.id.notification_avatar);
            typeIcon = itemView.findViewById(R.id.notification_type_icon);
            text = itemView.findViewById(R.id.notification_text);
            snippet = itemView.findViewById(R.id.notification_snippet);
            time = itemView.findViewById(R.id.notification_time);
        }
    }
}
