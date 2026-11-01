package com.gathin.tvhome;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.Collections;
import java.util.Map;

/**
 * When each app was last used. The launcher's own note of what it opened (LastOpened) always works; where the owner
 * has granted usage access, the system's record also counts apps started some other way. The later of the two wins.
 */
final class AppUsage {
    private static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR;

    private final Context context;
    private final Map<String, UsageStats> system;

    AppUsage(Context context) {
        this.context = context;
        system = readSystem(context);
    }

    long lastUsed(String pkg) {
        long own = LastOpened.get(context, pkg);
        UsageStats stats = system.get(pkg);
        return stats == null ? own : Math.max(own, stats.getLastTimeUsed());
    }

    /** The owner granted usage access. */
    static boolean allowed(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private static Map<String, UsageStats> readSystem(Context context) {
        try {
            if (!allowed(context)) return Collections.emptyMap();
            long now = System.currentTimeMillis();
            UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
            return manager.queryAndAggregateUsageStats(now - 60 * DAY, now);
        } catch (RuntimeException e) {
            return Collections.emptyMap();
        }
    }

    /** "2 min", "3 h", "1 d", or null for an app with no record. */
    static String ago(long now, long then) {
        if (then <= 0) return null;
        long gone = Math.max(0, now - then);
        if (gone < HOUR) return Math.max(1, gone / MINUTE) + " min";
        if (gone < DAY) return gone / HOUR + " h";
        if (gone < 7 * DAY) return gone / DAY + " d";
        if (gone < 30 * DAY) return gone / (7 * DAY) + " sem";
        return gone / (30 * DAY) + " mes";
    }

    /** tvmatch's deck: favourites first, then the latest used, then the rest by name; favourites keep that same order. */
    static int compareForDeck(boolean favoriteA, long usedA, String labelA, boolean favoriteB, long usedB, String labelB) {
        if (favoriteA != favoriteB) return favoriteA ? -1 : 1;
        if (usedA != usedB) return usedA > usedB ? -1 : 1;
        return labelA.compareToIgnoreCase(labelB);
    }

    /** Used in the last 24 hours. */
    static boolean recent(long now, long then) {
        return then > 0 && now - then < DAY;
    }
}
