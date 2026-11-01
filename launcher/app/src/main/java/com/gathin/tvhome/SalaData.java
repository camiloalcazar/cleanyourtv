package com.gathin.tvhome;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The Sala style's choices and data. Weather and air come from Open-Meteo, which needs no key; nothing else is sent. */
final class SalaData {
    private SalaData() {}

    static final String WEATHER = "weather", CONTINUE = "continue", INPUTS = "inputs", SLEEP = "sleep", NOTE = "note",
            CALENDAR = "calendar", STATUS = "status", MATCHES = "matches", SCREEN = "screen", WORLD = "world", AIR = "air";
    static final String[] ALL = {WEATHER, CONTINUE, INPUTS, SLEEP, NOTE, CALENDAR, STATUS, MATCHES, SCREEN, WORLD, AIR};
    private static final String[] DEFAULTS = {WEATHER, CONTINUE, INPUTS, SLEEP};
    /** Zone id and name, for the world clock. */
    static final String[][] ZONES = {{"America/Bogota", "Bogotá"}, {"Europe/Madrid", "Madrid"}, {"America/New_York", "Miami"},
            {"America/Mexico_City", "México"}, {"America/Argentina/Buenos_Aires", "Buenos Aires"}, {"America/Santiago", "Santiago"},
            {"America/Los_Angeles", "Los Ángeles"}, {"Europe/London", "Londres"}, {"Europe/Paris", "París"},
            {"Asia/Tokyo", "Tokio"}, {"Australia/Sydney", "Sídney"}, {"Asia/Dubai", "Dubái"}};
    private static final long FRESH_MS = 30 * 60 * 1000L;
    private static boolean fetching;

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("sala", Context.MODE_PRIVATE);
    }

    private static List<String> list(Context context, String key, String[] fallback) {
        String saved = prefs(context).getString(key, null);
        if (saved == null) return new ArrayList<>(Arrays.asList(fallback));
        List<String> out = new ArrayList<>();
        for (String part : saved.split(",")) if (!part.isEmpty()) out.add(part);
        return out;
    }

    private static void saveList(Context context, String key, List<String> values) {
        prefs(context).edit().putString(key, String.join(",", values)).apply();
    }

    /** Every widget in the owner's order; one added in a later version goes last. */
    static List<String> order(Context context) {
        List<String> order = list(context, "order", ALL);
        for (String widget : ALL) if (!order.contains(widget)) order.add(widget);
        order.retainAll(Arrays.asList(ALL));
        return order;
    }

    static void saveOrder(Context context, List<String> order) {
        saveList(context, "order", order);
    }

    static Set<String> active(Context context) {
        return new HashSet<>(list(context, "active", DEFAULTS));
    }

    static void saveActive(Context context, Set<String> active) {
        saveList(context, "active", new ArrayList<>(active));
    }

    static List<String> activeInOrder(Context context) {
        Set<String> active = active(context);
        List<String> out = new ArrayList<>();
        for (String widget : order(context)) if (active.contains(widget)) out.add(widget);
        return out;
    }

    static List<String> favorites(Context context) {
        return list(context, "favorites", new String[0]);
    }

    static void saveFavorites(Context context, List<String> favorites) {
        saveList(context, "favorites", favorites);
    }

    static List<String> zones(Context context) {
        return list(context, "zones", new String[]{"America/Bogota", "Europe/Madrid", "America/New_York"});
    }

    static void saveZones(Context context, List<String> zones) {
        saveList(context, "zones", zones);
    }

    static String zoneName(String id) {
        for (String[] zone : ZONES) if (zone[0].equals(id)) return zone[1];
        return id;
    }

    static String city(Context context) {
        return prefs(context).getString("city", "Bogotá");
    }

    static String name(Context context) {
        return prefs(context).getString("name", context.getString(R.string.sala_default_name));
    }

    static String note(Context context) {
        return prefs(context).getString("note", "");
    }

    /** A plain symbol for a WMO weather code. */
    static String weatherSymbol(int code) {
        if (code <= 1) return "☀";
        if (code == 2) return "⛅";
        if (code == 3 || code == 45 || code == 48) return "☁";
        if (code >= 95) return "⚡";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        return "☂";
    }

    static boolean needsNetwork(Context context) {
        Set<String> active = active(context);
        return active.contains(WEATHER) || active.contains(AIR);
    }

    static JSONObject forecast(Context context) {
        return parse(prefs(context).getString("forecast", null));
    }

    static JSONObject air(Context context) {
        return parse(prefs(context).getString("air", null));
    }

    private static JSONObject parse(String text) {
        if (text == null) return null;
        try {
            return new JSONObject(text);
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /** Fetches when a widget needs it and what is saved is old; done runs on the main thread after a change. */
    static void refresh(Context context, boolean force, Runnable done) {
        Context app = context.getApplicationContext();
        if (fetching || !needsNetwork(app)) return;
        SharedPreferences p = prefs(app);
        if (!force && System.currentTimeMillis() - p.getLong("fetched", 0) < FRESH_MS
                && p.getString("fetched_city", "").equals(city(app))) return;
        fetching = true;
        new Thread(() -> {
            boolean changed = false;
            try {
                String city = city(app);
                String place = "latitude=" + p.getFloat("lat", 0) + "&longitude=" + p.getFloat("lon", 0);
                if (!city.equals(p.getString("geo_city", ""))) {
                    JSONObject found = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&name="
                            + URLEncoder.encode(city, "UTF-8"))).getJSONArray("results").getJSONObject(0);
                    float lat = (float) found.getDouble("latitude"), lon = (float) found.getDouble("longitude");
                    p.edit().putString("geo_city", city).putFloat("lat", lat).putFloat("lon", lon).apply();
                    place = "latitude=" + lat + "&longitude=" + lon;
                }
                String forecast = get("https://api.open-meteo.com/v1/forecast?" + place
                        + "&current=temperature_2m,weather_code&hourly=precipitation_probability"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=4&timezone=auto");
                String air = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + place + "&current=us_aqi&timezone=auto");
                p.edit().putString("forecast", forecast).putString("air", air).putString("fetched_city", city)
                        .putLong("fetched", System.currentTimeMillis()).apply();
                changed = true;
            } catch (Exception e) {
                Log.i("SalaData", "no weather: " + e);
            }
            final boolean update = changed;
            new Handler(Looper.getMainLooper()).post(() -> {
                fetching = false;
                if (update && done != null) done.run();
            });
        }, "sala-data").start();
    }

    private static String get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    // ---- today's matches, from ESPN's public scoreboards, which take one day at a time ---------------

    private static final String[] LEAGUES = {"fifa.worldq.conmebol", "fifa.friendly", "fifa.world", "col.1"};
    private static final long MATCHES_MS = 15 * 60000L, LIVE_MS = 60000L;
    private static boolean fetchingMatches;

    static String team(Context context) {
        return prefs(context).getString("team", "Colombia");
    }

    /** What was found for today: {"day", "ok", "items": [...]}, or null when nothing is saved for today. */
    static JSONObject matches(Context context) {
        JSONObject saved = parse(prefs(context).getString("matches", null));
        return saved != null && day(new java.util.Date()).equals(saved.optString("day")) ? saved : null;
    }

    private static String day(java.util.Date date) {
        return new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(date);
    }

    /** Every 15 minutes, or every minute while a match is on. A failure saves "ok": false and the widget says so. */
    static void refreshMatches(Context context, boolean force, Runnable done) {
        Context app = context.getApplicationContext();
        if (fetchingMatches || !active(app).contains(MATCHES)) return;
        SharedPreferences p = prefs(app);
        JSONObject saved = matches(app);
        boolean live = saved != null && saved.toString().contains("\"state\":\"in\"");
        boolean sameTeam = team(app).equals(p.getString("matches_team", ""));
        if (!force && sameTeam && saved != null
                && System.currentTimeMillis() - p.getLong("matches_at", 0) < (live ? LIVE_MS : MATCHES_MS)) return;
        fetchingMatches = true;
        new Thread(() -> {
            String team = team(app);
            JSONObject result = new JSONObject();
            try {
                java.util.Date today = new java.util.Date();
                org.json.JSONArray items = new org.json.JSONArray();
                int answered = 0;
                long tomorrow = today.getTime() + 24 * 60 * 60000L;
                for (String league : LEAGUES) {
                    for (String date : new String[]{day(today), day(new java.util.Date(tomorrow))}) {
                        try {
                            collect(new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + league
                                    + "/scoreboard?dates=" + date)), team, day(today), items);
                            answered++;
                        } catch (Exception e) {
                            Log.i("SalaData", "no scoreboard " + league + ": " + e);
                        }
                    }
                }
                result.put("day", day(today)).put("ok", answered > 0).put("items", items);
                p.edit().putString("matches", result.toString()).putString("matches_team", team)
                        .putLong("matches_at", System.currentTimeMillis()).apply();
            } catch (org.json.JSONException e) {
                Log.i("SalaData", "no matches: " + e);
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                fetchingMatches = false;
                if (done != null) done.run();
            });
        }, "sala-matches").start();
    }

    private static void collect(JSONObject board, String team, String today, org.json.JSONArray items) throws Exception {
        org.json.JSONArray events = board.optJSONArray("events");
        if (events == null) return;
        String league = board.optJSONArray("leagues") == null ? "" : board.getJSONArray("leagues").getJSONObject(0).optString("name");
        java.text.SimpleDateFormat parse = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", java.util.Locale.ROOT);
        parse.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            java.util.Date when = parse.parse(event.getString("date"));
            if (!today.equals(day(when))) continue;
            JSONObject competition = event.getJSONArray("competitions").getJSONObject(0);
            org.json.JSONArray sides = competition.getJSONArray("competitors");
            JSONObject home = null, away = null;
            for (int s = 0; s < sides.length(); s++) {
                JSONObject side = sides.getJSONObject(s);
                if ("home".equals(side.optString("homeAway"))) home = side;
                else away = side;
            }
            if (home == null || away == null) continue;
            String homeName = home.getJSONObject("team").optString("displayName");
            String awayName = away.getJSONObject("team").optString("displayName");
            if (!teamMatches(team, homeName) && !teamMatches(team, awayName)) continue;
            JSONObject status = event.getJSONObject("status");
            items.put(new JSONObject().put("home", homeName).put("away", awayName)
                    .put("hs", home.optString("score")).put("as", away.optString("score"))
                    .put("state", status.getJSONObject("type").optString("state"))
                    .put("clock", status.optString("displayClock")).put("league", league).put("when", when.getTime()));
        }
    }

    /** ESPN writes names in English: "Colombia", "Brazil", "Atlético Nacional". Accents and capitals do not matter. */
    static boolean teamMatches(String wanted, String name) {
        String a = plain(wanted), b = plain(name);
        return !a.isEmpty() && b.contains(a);
    }

    private static String plain(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
    }

    /** Milliseconds each app was on screen since midnight, largest first; empty without usage access. */
    @SuppressWarnings("deprecation")
    static List<Map.Entry<String, Long>> screenToday(Context context) {
        Map<String, Long> totals = new HashMap<>();
        if (!AppUsage.allowed(context)) return new ArrayList<>();
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        long now = System.currentTimeMillis();
        try {
            UsageEvents events = context.getSystemService(UsageStatsManager.class).queryEvents(midnight.getTimeInMillis(), now);
            UsageEvents.Event event = new UsageEvents.Event();
            String open = null;
            long since = 0;
            while (events != null && events.getNextEvent(event)) {
                int type = event.getEventType();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (open != null) add(totals, open, event.getTimeStamp() - since);
                    open = event.getPackageName();
                    since = event.getTimeStamp();
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND && event.getPackageName().equals(open)) {
                    add(totals, open, event.getTimeStamp() - since);
                    open = null;
                }
            }
            if (open != null) add(totals, open, now - since);
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        totals.remove(context.getPackageName());
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(totals.entrySet());
        Collections.sort(sorted, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted;
    }

    private static void add(Map<String, Long> totals, String pkg, long ms) {
        Long seen = totals.get(pkg);
        totals.put(pkg, (seen == null ? 0 : seen) + Math.max(0, ms));
    }

    /** "3 h 12 min" or "12 min". */
    static String duration(long ms) {
        long minutes = ms / 60000;
        return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
    }

    /** Whether Android lets this launcher read other apps' Watch Next rows; logged so a run on a TV can settle it. */
    static void logWatchNext(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try (Cursor rows = app.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,
                    new String[]{TvContract.WatchNextPrograms.COLUMN_TITLE}, null, null, null)) {
                Log.i("SalaData", "watch next rows=" + (rows == null ? -1 : rows.getCount()));
            } catch (RuntimeException e) {
                Log.i("SalaData", "watch next refused: " + e);
            }
        }, "sala-watch-next").start();
    }
}
