package com.example.twitterclient.util;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

/** Styled strings shared by the reply screens. */
public final class UiText {

    private UiText() {
    }

    /** "Replying to @username" with the handle in Twitter blue. */
    public static CharSequence replyingTo(String username) {
        String prefix = "Replying to ";
        String text = prefix + "@" + username;

        SpannableString span = new SpannableString(text);
        span.setSpan(
                new ForegroundColorSpan(Color.parseColor("#1D9BF0")),
                prefix.length(),
                text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return span;
    }
}
