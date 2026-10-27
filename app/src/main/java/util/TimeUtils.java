package com.example.twitterclient.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Turns the server's UTC timestamps (2026-10-25T16:26:53.000Z) into Twitter-style text. */
public final class TimeUtils {

    private TimeUtils() {
    }

    private static Date parse(String iso) {
        if (iso == null || iso.length() < 19) {
            return null;
        }

        try {
            SimpleDateFormat format =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.parse(iso.substring(0, 19));
        } catch (ParseException error) {
            return null;
        }
    }

    /** "now", "5m", "3h", "Oct 25", or "Oct 25, 2025" for another year. Used in lists. */
    public static String shortAgo(String iso) {
        Date date = parse(iso);
        if (date == null) {
            return "";
        }

        long seconds = Math.max(0, (System.currentTimeMillis() - date.getTime()) / 1000L);

        if (seconds < 60) {
            return "now";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h";
        }

        SimpleDateFormat sameYear = new SimpleDateFormat("MMM d", Locale.US);
        SimpleDateFormat otherYear = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        SimpleDateFormat yearOnly = new SimpleDateFormat("yyyy", Locale.US);

        boolean thisYear = yearOnly.format(date)
                .equals(yearOnly.format(new Date()));
        return (thisYear ? sameYear : otherYear).format(date);
    }

    /** "10:42 AM · Oct 27, 2026" in the phone's time zone. Used on the Post screen. */
    public static String fullDate(String iso) {
        Date date = parse(iso);
        if (date == null) {
            return "";
        }

        SimpleDateFormat time = new SimpleDateFormat("h:mm a", Locale.US);
        SimpleDateFormat day = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        return time.format(date) + " · " + day.format(date);
    }

    /** 1234 -> "1.2K", 2500000 -> "2.5M". Counts stay short like on Twitter. */
    public static String compactCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        if (count < 1_000_000) {
            return trim(count / 1000.0) + "K";
        }
        return trim(count / 1_000_000.0) + "M";
    }

    private static String trim(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
