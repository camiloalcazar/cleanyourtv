package com.gathin.tvhome;

import android.content.Context;

/** The home screen's look, picked in Launcher settings and kept with the other on-TV choices. */
final class LauncherStyle {
    private LauncherStyle() {}

    static final String KEY = "launcher_style";
    static final String APPLE = "apple", WII = "wii", ROKU = "roku", ROKU_NEW = "roku_new", METRO = "metro", GRID = "grid", TVMATCH = "tvmatch", TUBE = "tube", CINEMA = "cinema", SALA = "sala";

    /** Anything unknown is the default look, so a bad value can never leave the TV without a Home. */
    static String normalize(String value) {
        return WII.equals(value) || ROKU.equals(value) || ROKU_NEW.equals(value) || METRO.equals(value) || GRID.equals(value) || TVMATCH.equals(value) || TUBE.equals(value) || CINEMA.equals(value) || SALA.equals(value) ? value : APPLE;
    }

    static String read(Context context) {
        return normalize(context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, APPLE));
    }

    /** Written at once: Home's safety fallback may rebuild right after this. */
    static void save(Context context, String style) {
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY, normalize(style)).commit();
    }
}
