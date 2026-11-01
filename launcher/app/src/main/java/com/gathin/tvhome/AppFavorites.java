package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The apps the owner marked as favourites; until the first mark, the ones pinned to the dock. */
final class AppFavorites {
    private static final String PREFS = "app_favorites", KEY = "packages";

    private final SharedPreferences prefs;
    private final Set<String> packages;

    AppFavorites(Context context, List<String> dock) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        packages = new HashSet<>(prefs.contains(KEY) ? prefs.getStringSet(KEY, new HashSet<>()) : new HashSet<>(dock));
    }

    boolean has(String pkg) {
        return packages.contains(pkg);
    }

    /** @return whether the app is a favourite now */
    boolean toggle(String pkg) {
        boolean added = packages.add(pkg);
        if (!added) packages.remove(pkg);
        prefs.edit().putStringSet(KEY, new HashSet<>(packages)).apply();
        return added;
    }
}
