package com.gathin.tvhome;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How many times each app was opened. With usage access, the system's record of apps coming to the front, which also
 * counts the launcher's own openings, read as far back as Android keeps it and added up here so old days are not lost
 * when Android drops them. The launcher's own count stands in without that access. The larger of the two is shown:
 * adding them would count every opening from Home twice.
 */
final class AppOpenCounts {
    private AppOpenCounts() {}

    private static final String PREFS = "app_open_counts", UNTIL = "until", LAST = "last_foreground";
    private static final long FIRST_SCAN_MS = 30L * 24 * 60 * 60 * 1000;
    private static boolean scanning;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int get(Context context, String pkg) {
        SharedPreferences p = prefs(context);
        return Math.max(p.getInt("h:" + pkg, 0), p.getInt("o:" + pkg, 0));
    }

    /** Home just opened this app. */
    static void launched(Context context, String pkg) {
        SharedPreferences p = prefs(context);
        p.edit().putInt("o:" + pkg, p.getInt("o:" + pkg, 0) + 1).apply();
    }

    /** An opening is an app coming to the front after another one; its own screens changing are not. Returns the last. */
    static String tally(List<String> foregrounds, String last, Map<String, Integer> counts) {
        for (String pkg : foregrounds) {
            if (pkg.equals(last)) continue;
            Integer seen = counts.get(pkg);
            counts.put(pkg, seen == null ? 1 : seen + 1);
            last = pkg;
        }
        return last;
    }

    /** Reads what happened since the last read, off the main thread; done runs on the main thread when it changed. */
    @SuppressWarnings("deprecation")
    static void scan(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        if (scanning || !AppUsage.allowed(app)) return;
        scanning = true;
        new Thread(() -> {
            boolean changed = false;
            try {
                SharedPreferences p = prefs(app);
                long now = System.currentTimeMillis();
                long from = p.getLong(UNTIL, now - FIRST_SCAN_MS);
                UsageEvents events = app.getSystemService(UsageStatsManager.class).queryEvents(from, now);
                List<String> foregrounds = new ArrayList<>();
                UsageEvents.Event event = new UsageEvents.Event();
                while (events != null && events.getNextEvent(event)) {
                    if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) foregrounds.add(event.getPackageName());
                }
                Map<String, Integer> counts = new HashMap<>();
                String last = tally(foregrounds, p.getString(LAST, null), counts);
                SharedPreferences.Editor edit = p.edit().putLong(UNTIL, now + 1).putString(LAST, last);
                for (Map.Entry<String, Integer> count : counts.entrySet()) {
                    edit.putInt("h:" + count.getKey(), p.getInt("h:" + count.getKey(), 0) + count.getValue());
                }
                edit.apply();
                changed = !counts.isEmpty();
            } catch (RuntimeException ignored) {
                // No count is better than no Home.
            }
            final boolean update = changed;
            new Handler(Looper.getMainLooper()).post(() -> {
                scanning = false;
                if (update && done != null) done.run();
            });
        }, "open-counts").start();
    }
}
