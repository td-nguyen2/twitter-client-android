package com.example.twitterclient.util;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.twitterclient.R;

/** Small helpers for the images the server hands out as /uploads/... paths. */
public final class ImageUtils {

    // Android emulator address of the host PC (same value as ApiService)
    private static final String BASE_URL = "http://10.0.2.2:3000";

    private ImageUtils() {
    }

    public static String fullUrl(String path) {
        return path.startsWith("http") ? path : BASE_URL + path;
    }

    public static boolean hasImage(String path) {
        return path != null && !path.trim().isEmpty() && !path.equals("null");
    }

    /** Round profile picture with the default avatar as placeholder and fallback. */
    public static void loadAvatar(ImageView target, String path) {
        if (hasImage(path)) {
            Glide.with(target)
                    .load(fullUrl(path))
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ui_default_avatar)
                    .error(R.drawable.ui_default_avatar)
                    .into(target);
        } else {
            Glide.with(target).clear(target);
            target.setImageResource(R.drawable.ui_default_avatar);
        }
    }
}
