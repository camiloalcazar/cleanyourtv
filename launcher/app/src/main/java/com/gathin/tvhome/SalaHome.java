package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.StatFs;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * "Sala", a home of this launcher's own: a photo with the time and six favourites, and a row of widgets below it.
 * Positions are pixels of a 1920x1080 screen, read from the approved sketches.
 */
final class SalaHome {
    private static final int TILE = 255, FAVORITES = 6;
    private static final float CARD_W = 431, CARD_H = 292, CARD_GAP = 33, CARD_LEFT = 48;
    private static final long HOLD_MS = 550;

    private final MainActivity activity;
    private final FrameLayout root;
    private final AppAdapter model;
    private final boolean animate;
    private final int width, height;
    private final float density;
    private final Typeface regular, medium, light;
    private final View dim;
    private final FrameLayout homeLayer, widgetLayer, cardsPane;
    private final ScrollView cardsScroll;
    private final TextView greeting, dateLine, hint;
    private final FrameLayout favRow;
    private final List<View> pills = new ArrayList<>();
    private final List<View> favViews = new ArrayList<>();
    private final List<AppAdapter.Entry> favEntries = new ArrayList<>();
    private final List<AppAdapter.Entry> allApps = new ArrayList<>();
    private final Map<String, Drawable> blurs = new HashMap<>();
    private final TextView favName;
    private final FrameLayout appsLayer, appsPane;
    private final ScrollView appsScroll;
    private final List<Target> appTargets = new ArrayList<>();
    private boolean appsOpen;
    private int appFocus;

    private static final class Target {
        View view;
        int x, y, w, h;
        Runnable action;
    }

    private final List<Target> targets = new ArrayList<>();
    private final List<Object[]> clocks = new ArrayList<>();
    private boolean widgetsOpen;
    private int favFocus, pillFocus = -1, targetFocus;
    private long okDown = -1;
    private boolean okFired;
    private String shownBackground;
    private final Runnable tick = this::tick;
    private final Runnable holdTimer = this::fireHold;

    SalaHome(MainActivity activity, FrameLayout root, AppAdapter model, boolean animate) {
        this.activity = activity;
        this.root = root;
        this.model = model;
        this.animate = animate;
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        density = metrics.density;
        regular = UiFont.get(activity, R.string.font_regular);
        medium = UiFont.get(activity, R.string.font_medium);
        light = UiFont.get(activity, R.string.font_extralight);
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(0xFF101418));
        root.setClipChildren(false);

        dim = new View(activity);
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homeLayer = layer();
        greeting = text("", 26, 0xE6FFFFFF, medium, Gravity.CENTER_VERTICAL);
        greeting.setLetterSpacing(.2f);
        homeLayer.addView(greeting, at(1100, 40, 121, 92));
        TextClock big = clock(190, Color.WHITE, light);
        big.setGravity(Gravity.CENTER_VERTICAL);
        homeLayer.addView(big, at(900, 190, 114, 122));
        dateLine = text("", 32, 0xE6FFFFFF, regular, Gravity.CENTER_VERTICAL);
        homeLayer.addView(dateLine, at(1300, 50, 121, 306));

        int x = 1812;
        String[] labels = {activity.getString(R.string.settings), activity.getString(R.string.wifi), activity.getString(R.string.inputs)};
        int[] icons = {R.drawable.ic_settings, 0, R.drawable.ic_input};
        Runnable[] actions = {activity::styledSalaSettings, activity::styledNetwork, activity::styledInputs};
        View[] made = new View[3];
        for (int i = 0; i < 3; i++) {
            made[i] = pill(labels[i], icons[i], i == 0);
            int w = i == 0 ? 74 : Math.round(((TextView) made[i]).getPaint().measureText(labels[i]) / px(1)) + (icons[i] != 0 ? 92 : 66);
            x -= w;
            homeLayer.addView(made[i], at(w, 74, x, 96));
            final Runnable action = actions[i];
            made[i].setTag(action);
            x -= 18;
        }
        made[2].setId(R.id.btnInputs);
        pills.add(made[2]);
        pills.add(made[1]);
        pills.add(made[0]);

        favName = text("", 42, Color.WHITE, Typeface.create(medium, Typeface.BOLD), Gravity.CENTER_VERTICAL);
        homeLayer.addView(favName, at(1400, 60, 121, 596));
        favRow = new FrameLayout(activity);
        favRow.setClipChildren(false);
        homeLayer.addView(favRow, at(1920, TILE + 60, 0, 650));
        hint = text(activity.getString(R.string.sala_widgets_hint), 24, 0xCCFFFFFF, regular, Gravity.CENTER);
        homeLayer.addView(hint, at(400, 40, 760, 1000));

        widgetLayer = layer();
        widgetLayer.setVisibility(View.GONE);
        TextClock small = clock(58, Color.WHITE, light);
        small.setGravity(Gravity.CENTER_VERTICAL);
        widgetLayer.addView(small, at(400, 84, 121, 38));
        cardsScroll = new ScrollView(activity);
        // The cards scroll inside this window only, never over the clock and the apps above it.
        cardsScroll.setClipToPadding(true);
        cardsScroll.setVerticalScrollBarEnabled(false);
        cardsScroll.setFocusable(false);
        cardsScroll.setPadding(0, py(20), 0, 0);
        cardsScroll.setSmoothScrollingEnabled(animate);
        cardsPane = new FrameLayout(activity);
        cardsPane.setClipChildren(false);
        cardsScroll.addView(cardsPane, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        widgetLayer.addView(cardsScroll, at(1920, 950, 0, 130));
        appsLayer = layer();
        appsLayer.setVisibility(View.GONE);
        appsLayer.addView(text(activity.getString(R.string.sala_all_apps), 46, Color.WHITE,
                Typeface.create(medium, Typeface.BOLD), Gravity.CENTER_VERTICAL), at(1200, 70, 121, 34));
        appsScroll = new ScrollView(activity);
        appsScroll.setClipToPadding(true);
        appsScroll.setVerticalScrollBarEnabled(false);
        appsScroll.setFocusable(false);
        appsScroll.setPadding(0, py(26), 0, 0);
        appsScroll.setSmoothScrollingEnabled(animate);
        appsPane = new FrameLayout(activity);
        appsPane.setClipChildren(false);
        appsScroll.addView(appsPane, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        appsLayer.addView(appsScroll, at(1920, 970, 0, 110));
        SalaData.logWatchNext(activity);
    }

    // ---- small helpers ----------------------------------------------------------------------------

    private int px(float v) {
        return Math.round(v * width / 1920f);
    }

    private int py(float v) {
        return Math.round(v * height / 1080f);
    }

    private FrameLayout layer() {
        FrameLayout layer = new FrameLayout(activity);
        layer.setClipChildren(false);
        root.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return layer;
    }

    private FrameLayout.LayoutParams at(float w, float h, float left, float top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), py(h));
        lp.leftMargin = px(left);
        lp.topMargin = py(top);
        return lp;
    }

    private TextView text(String value, float sizePx, int color, Typeface face, int gravity) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(sizePx));
        view.setTextColor(color);
        view.setTypeface(face);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setGravity(gravity);
        return view;
    }

    private TextClock clock(float size, int color, Typeface face) {
        TextClock clock = new TextClock(activity);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(size));
        clock.setTextColor(color);
        clock.setTypeface(face);
        clock.setSingleLine(true);
        return clock;
    }

    /** Frosted glass, painted: a lighter top, a darker base and a bright rim. Real blur would cost too much on a TV. */
    private GradientDrawable glass() {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x4AFFFFFF, 0x1FFFFFFF});
        shape.setCornerRadius(py(26));
        shape.setStroke(py(1.5f), 0x59FFFFFF);
        return shape;
    }

    private GradientDrawable rounded(int fill, int stroke, float strokePx, float radiusPx) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(py(radiusPx));
        if (strokePx > 0) shape.setStroke(py(strokePx), stroke);
        return shape;
    }

    private void clipRound(View view, final float radiusPx) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), py(radiusPx));
            }
        });
        view.setClipToOutline(true);
    }

    private View pill(String label, int icon, boolean iconOnly) {
        TextView pill = text(iconOnly ? "" : label, 28, Color.WHITE, medium, Gravity.CENTER);
        pill.setContentDescription(label);
        if (icon != 0) {
            Drawable d = activity.getDrawable(icon).mutate();
            d.setBounds(0, 0, py(30), py(30));
            d.setTint(Color.WHITE);
            pill.setCompoundDrawablesRelative(d, null, null, null);
            pill.setCompoundDrawablePadding(iconOnly ? 0 : px(10));
            pill.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            pill.setPadding(px(22), 0, iconOnly ? 0 : px(26), 0);
        }
        pill.setFocusable(true);
        paintPill(pill, false);
        pill.setOnFocusChangeListener((v, focused) -> paintPill(pill, focused));
        return pill;
    }

    private void paintPill(TextView pill, boolean focused) {
        pill.setBackground(focused ? rounded(0xF2FFFFFF, 0, 0, 37) : rounded(0x2EFFFFFF, 0x4DFFFFFF, 1.5f, 37));
        pill.setTextColor(focused ? 0xFF10141A : Color.WHITE);
        Drawable[] drawables = pill.getCompoundDrawablesRelative();
        if (drawables[0] != null) drawables[0].setTint(focused ? 0xFF10141A : Color.WHITE);
    }

    // ---- lifecycle --------------------------------------------------------------------------------

    void reload(List<AppAdapter.Entry> apps) {
        allApps.clear();
        allApps.addAll(apps);
        buildFavorites();
        buildCards();
    }

    void onResume() {
        applyBackground();
        updateLines();
        SalaData.refresh(activity, false, this::dataChanged);
        SalaData.refreshMatches(activity, false, this::dataChanged);
        root.removeCallbacks(tick);
        root.postDelayed(tick, 30000);
        buildCards();
        root.post(this::restoreFocus);
    }

    void onPause() {
        root.removeCallbacks(tick);
        root.removeCallbacks(holdTimer);
        okDown = -1;
    }

    void releaseArt() {
        blurs.clear();
        favRow.removeAllViews();
        cardsPane.removeAllViews();
        targets.clear();
        allApps.clear();
        root.setBackground(null);
        shownBackground = null;
    }

    void goHome() {
        if (appsOpen) closeApps();
        showWidgets(false);
        favFocus = 0;
        pillFocus = -1;
        restoreFocus();
    }

    private void dataChanged() {
        updateLines();
        buildCards();
        if (widgetsOpen) restoreFocus();
    }

    private void tick() {
        updateLines();
        if (SalaBackdrops.rotate(activity)) applyBackground();
        if (SalaBackdrops.AUTO.equals(SalaBackdrops.current(activity))) {
            shownBackground = null;
            applyBackground();
        }
        SimpleDateFormat format = new SimpleDateFormat(DateFormat.is24HourFormat(activity) ? "H:mm" : "h:mm a", Locale.getDefault());
        for (Object[] entry : clocks) {
            format.setTimeZone(TimeZone.getTimeZone((String) entry[1]));
            ((TextView) entry[0]).setText(format.format(new Date()));
        }
        SalaData.refresh(activity, false, this::dataChanged);
        SalaData.refreshMatches(activity, false, this::dataChanged);
        root.postDelayed(tick, 30000);
    }

    private void applyBackground() {
        String choice = SalaBackdrops.current(activity);
        int percent = SalaData.prefs(activity).getInt("dim", 30);
        dim.setBackgroundColor(Math.round(percent * 2.55f) << 24);
        if (choice.equals(shownBackground)) return;
        shownBackground = choice;
        boolean photo = choice.startsWith("photo:");
        Bitmap picture = SalaBackdrops.paint(activity, choice, photo ? 1920 : 960, photo ? 1080 : 540);
        BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), picture);
        drawable.setFilterBitmap(true);
        root.setBackground(drawable);
    }

    private void updateLines() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int hello = hour < 12 ? R.string.good_morning : hour < 19 ? R.string.good_afternoon : R.string.good_evening;
        String name = SalaData.name(activity);
        String line = activity.getString(hello) + (name.isEmpty() ? "" : ", " + name);
        greeting.setText(line.toUpperCase(Locale.getDefault()));
        String date = new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault()).format(new Date());
        date = date.substring(0, 1).toUpperCase(Locale.getDefault()) + date.substring(1);
        if (SalaData.active(activity).contains(SalaData.WEATHER)) {
            JSONObject forecast = SalaData.forecast(activity);
            if (forecast != null) {
                try {
                    JSONObject now = forecast.getJSONObject("current");
                    date += " · " + Math.round(now.getDouble("temperature_2m")) + "° " + SalaData.weatherSymbol(now.getInt("weather_code"));
                } catch (org.json.JSONException ignored) {
                }
            }
        }
        dateLine.setText(date);
    }

    // ---- favourites -------------------------------------------------------------------------------

    private AppAdapter.Entry byPackage(String pkg) {
        for (AppAdapter.Entry entry : allApps) if (entry.pkg.equals(pkg)) return entry;
        return null;
    }

    private void buildFavorites() {
        favEntries.clear();
        for (String pkg : SalaData.favorites(activity)) {
            AppAdapter.Entry entry = byPackage(pkg);
            if (entry != null && favEntries.size() < FAVORITES) favEntries.add(entry);
        }
        for (AppAdapter.Entry entry : allApps) {
            if (favEntries.size() >= FAVORITES) break;
            if (!favEntries.contains(entry) && SalaData.favorites(activity).isEmpty()) favEntries.add(entry);
        }
        favRow.removeAllViews();
        favViews.clear();
        for (int i = 0; i < favEntries.size(); i++) {
            final int index = i;
            FrameLayout tile = tile(favEntries.get(i), TILE, 27);
            tile.setFocusable(true);
            tile.setOnFocusChangeListener((v, focused) -> {
                v.setForeground(focused ? rounded(Color.TRANSPARENT, Color.WHITE, 5, 25) : null);
                v.setElevation(focused ? py(26) : 0);
                float scale = focused ? 1.1f : 1f;
                if (animate) v.animate().scaleX(scale).scaleY(scale).setDuration(130).start();
                else {
                    v.setScaleX(scale);
                    v.setScaleY(scale);
                }
                if (focused) favName.setText(favEntries.get(index).label);
            });
            favRow.addView(tile, at(TILE, TILE, 120 + i * 294, 20));
            favViews.add(tile);
        }
        // After the favourites, the way to every other app. Its edge shows at the end of the row.
        FrameLayout all = new FrameLayout(activity);
        clipRound(all, 27);
        all.setBackground(rounded(0x2EFFFFFF, 0x59FFFFFF, 1.5f, 27));
        ImageView grid = new ImageView(activity);
        grid.setImageResource(R.drawable.ic_grid);
        grid.setColorFilter(Color.WHITE);
        all.addView(grid, new FrameLayout.LayoutParams(py(84), py(84), Gravity.CENTER));
        all.setContentDescription(activity.getString(R.string.sala_all_apps));
        all.setFocusable(true);
        all.setOnFocusChangeListener((v, focused) -> {
            v.setForeground(focused ? rounded(Color.TRANSPARENT, Color.WHITE, 5, 25) : null);
            float scale = focused ? 1.1f : 1f;
            v.setScaleX(scale);
            v.setScaleY(scale);
            if (focused) favName.setText(R.string.sala_all_apps);
        });
        favRow.addView(all, at(TILE, TILE, 120 + favEntries.size() * 294, 20));
        favViews.add(all);
        favFocus = Math.min(favFocus, Math.max(0, favViews.size() - 1));
    }

    private void openApps() {
        appsPane.removeAllViews();
        appTargets.clear();
        int columns = FAVORITES;
        for (int i = 0; i < allApps.size(); i++) {
            final AppAdapter.Entry entry = allApps.get(i);
            FrameLayout view = tile(entry, TILE, 27);
            int x = 120 + i % columns * 294, y = i / columns * 294;
            appsPane.addView(view, at(TILE, TILE, x, y));
            Target target = new Target();
            target.view = view;
            target.x = x;
            target.y = y;
            target.w = TILE;
            target.h = TILE;
            target.action = () -> model.launch(entry);
            view.setFocusable(true);
            view.setOnFocusChangeListener((v, focused) -> {
                v.setForeground(focused ? rounded(Color.TRANSPARENT, Color.WHITE, 5, 25) : null);
                v.setElevation(focused ? py(26) : 0);
                float scale = focused ? 1.08f : 1f;
                v.setScaleX(scale);
                v.setScaleY(scale);
                if (focused) keepAppVisible(target);
            });
            appTargets.add(target);
        }
        View end = new View(activity);
        appsPane.addView(end, at(1, 1, 0, (allApps.size() + columns - 1) / columns * 294 + 30));
        appFocus = 0;
        appsScroll.scrollTo(0, 0);
        appsOpen = true;
        homeLayer.setVisibility(View.GONE);
        appsLayer.setVisibility(View.VISIBLE);
        if (animate) {
            appsLayer.setTranslationX(py(240));
            appsLayer.setAlpha(0f);
            appsLayer.animate().translationX(0).alpha(1).setDuration(180).start();
        }
        restoreFocus();
    }

    private void closeApps() {
        appsOpen = false;
        appsLayer.setVisibility(View.GONE);
        homeLayer.setVisibility(View.VISIBLE);
        appsPane.removeAllViews();
        appTargets.clear();
    }

    private void keepAppVisible(Target t) {
        int window = appsScroll.getHeight();
        if (window <= 0) return;
        int top = py(t.y) + appsScroll.getPaddingTop(), bottom = top + py(t.h), margin = py(24);
        int y = appsScroll.getScrollY();
        if (top - margin < y) y = top - margin;
        else if (bottom + margin > y + window) y = bottom + margin - window;
        if (animate) appsScroll.smoothScrollTo(0, Math.max(0, y));
        else appsScroll.scrollTo(0, Math.max(0, y));
    }

    /** The whole banner across the width, on a blur of its own edges; an app with only an icon shows that and its name. */
    private FrameLayout tile(AppAdapter.Entry entry, int size, float radius) {
        FrameLayout tile = new FrameLayout(activity);
        clipRound(tile, radius);
        ImageView art = new ImageView(activity);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (entry.banner != null) {
            Drawable blur = blurs.get(entry.pkg);
            if (blur == null) {
                blur = RokuSkin.blurred(activity.getResources(), entry.banner);
                blurs.put(entry.pkg, blur);
            }
            tile.setBackground(blur.getConstantState().newDrawable().mutate());
            art.setImageDrawable(entry.banner);
            // The focus glow takes the colour of the app, as in the glass theme.
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                int glow = RokuSkin.edges(entry.banner).bottom;
                tile.setOutlineSpotShadowColor(glow);
                tile.setOutlineAmbientShadowColor(glow);
            }
        } else {
            tile.setBackgroundColor(0xFF2A2F38);
            art.setImageDrawable(entry.icon);
            int pad = py(size) / 4;
            art.setPadding(pad, pad / 2, pad, pad + pad / 2);
            TextView name = text(entry.label, Math.max(18, size / 9f), Color.WHITE, medium, Gravity.CENTER);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            lp.bottomMargin = py(size) / 10;
            tile.addView(name, lp);
        }
        tile.setContentDescription(entry.label);
        return tile;
    }

    /** Hold OK on a favourite: move it, change it, or take it off. */
    private void showFavoriteMenu() {
        if (favFocus >= favEntries.size()) return;
        final int index = favFocus;
        CharSequence[] items = {activity.getString(R.string.sala_move_left), activity.getString(R.string.sala_move_right),
                activity.getString(R.string.sala_replace), activity.getString(R.string.sala_remove)};
        new android.app.AlertDialog.Builder(activity)
                .setTitle(favEntries.get(index).label)
                .setItems(items, (d, which) -> {
                    List<String> packages = new ArrayList<>();
                    for (AppAdapter.Entry entry : favEntries) packages.add(entry.pkg);
                    if (which == 0 && index > 0) {
                        Collections.swap(packages, index, index - 1);
                        favFocus = index - 1;
                    } else if (which == 1 && index < packages.size() - 1) {
                        Collections.swap(packages, index, index + 1);
                        favFocus = index + 1;
                    } else if (which == 2) {
                        pickFavorite(packages, index);
                        return;
                    } else if (which == 3) {
                        packages.remove(index);
                        if (packages.isEmpty()) return;
                    }
                    SalaData.saveFavorites(activity, packages);
                    buildFavorites();
                    root.post(this::restoreFocus);
                })
                .show();
    }

    private void pickFavorite(List<String> packages, int index) {
        List<AppAdapter.Entry> others = new ArrayList<>();
        for (AppAdapter.Entry entry : allApps) if (!packages.contains(entry.pkg)) others.add(entry);
        CharSequence[] names = new CharSequence[others.size()];
        for (int i = 0; i < names.length; i++) names[i] = others.get(i).label;
        new android.app.AlertDialog.Builder(activity)
                .setItems(names, (d, which) -> {
                    packages.set(index, others.get(which).pkg);
                    SalaData.saveFavorites(activity, packages);
                    buildFavorites();
                    root.post(this::restoreFocus);
                })
                .show();
    }

    // ---- widgets ----------------------------------------------------------------------------------

    private void buildCards() {
        cardsPane.removeAllViews();
        targets.clear();
        clocks.clear();
        List<String> widgets = SalaData.activeInOrder(activity);
        int[][] spans = new int[widgets.size()][];
        for (int i = 0; i < spans.length; i++) spans[i] = new int[]{span(widgets.get(i))};
        int[][] places = StyleNav.flow(spans, 4);
        int bottom = 0;
        for (int i = 0; i < widgets.size(); i++) {
            int w = spans[i][0] == 2 ? Math.round(CARD_W * 2 + CARD_GAP) : Math.round(CARD_W);
            int x = Math.round(CARD_LEFT + places[i][0] * (CARD_W + CARD_GAP));
            int y = Math.round(places[i][1] * (CARD_H + CARD_GAP));
            FrameLayout card = new FrameLayout(activity);
            card.setBackground(glass());
            cardsPane.addView(card, at(w, CARD_H, x, y));
            fill(widgets.get(i), card, w, x, y);
            bottom = Math.max(bottom, y + Math.round(CARD_H));
        }
        View end = new View(activity);
        cardsPane.addView(end, at(1, 1, 0, bottom + 40));
        targetFocus = Math.min(targetFocus, Math.max(0, targets.size() - 1));
        hint.setVisibility(targets.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static int span(String widget) {
        return SalaData.CONTINUE.equals(widget) || SalaData.AIR.equals(widget) ? 2 : 1;
    }

    private TextView caption(FrameLayout card, int title) {
        TextView view = text(activity.getString(title).toUpperCase(Locale.getDefault()), 21, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL);
        view.setLetterSpacing(.14f);
        card.addView(view, at(CARD_W * 2, 30, 28, 24));
        return view;
    }

    private void put(FrameLayout card, View view, float w, float h, float left, float top) {
        card.addView(view, at(w, h, left, top));
    }

    private Target target(View view, float x, float y, float w, float h, float radius, Runnable action) {
        Target t = new Target();
        t.view = view;
        t.x = Math.round(x);
        t.y = Math.round(y);
        t.w = Math.round(w);
        t.h = Math.round(h);
        t.action = action;
        view.setFocusable(true);
        view.setOnFocusChangeListener((v, focused) -> {
            v.setForeground(focused ? rounded(Color.TRANSPARENT, Color.WHITE, 4, radius) : null);
            if (focused) keepVisible(t);
        });
        targets.add(t);
        return t;
    }

    private void keepVisible(Target t) {
        int window = cardsScroll.getHeight();
        if (window <= 0) return;
        int top = py(t.y) + cardsScroll.getPaddingTop(), bottom = top + py(t.h), margin = py(20);
        int y = cardsScroll.getScrollY();
        if (top - margin < y) y = top - margin;
        else if (bottom + margin > y + window) y = bottom + margin - window;
        if (animate) cardsScroll.smoothScrollTo(0, Math.max(0, y));
        else cardsScroll.scrollTo(0, Math.max(0, y));
    }

    private void fill(String widget, FrameLayout card, int w, int x, int y) {
        Runnable refresh = () -> SalaData.refresh(activity, true, this::dataChanged);
        switch (widget) {
            case SalaData.WEATHER: weather(card, refresh, x, y); break;
            case SalaData.CONTINUE: continueWatching(card, x, y); break;
            case SalaData.INPUTS: inputs(card, x, y); break;
            case SalaData.SLEEP: sleep(card, x, y); break;
            case SalaData.NOTE: note(card, x, y); break;
            case SalaData.CALENDAR: calendar(card, x, y); break;
            case SalaData.STATUS: status(card, x, y); break;
            case SalaData.MATCHES: matches(card, x, y); break;
            case SalaData.SCREEN: screen(card, x, y); break;
            case SalaData.WORLD: world(card, x, y); break;
            default: air(card, refresh, w, x, y); break;
        }
    }

    private void weather(FrameLayout card, Runnable refresh, int x, int y) {
        caption(card, R.string.sala_w_weather);
        target(card, x, y, CARD_W, CARD_H, 26, refresh);
        JSONObject forecast = SalaData.forecast(activity);
        if (forecast == null) {
            put(card, text(activity.getString(R.string.style_offline), 30, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 380, 60, 28, 110);
            return;
        }
        try {
            JSONObject now = forecast.getJSONObject("current");
            JSONObject daily = forecast.getJSONObject("daily");
            put(card, text(Math.round(now.getDouble("temperature_2m")) + "° " + SalaData.weatherSymbol(now.getInt("weather_code")),
                    78, Color.WHITE, light, Gravity.CENTER_VERTICAL), 380, 90, 28, 62);
            put(card, text(SalaData.city(activity) + " · " + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)) + "° / "
                    + Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)) + "°", 25, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 385, 34, 28, 160);
            JSONArray days = daily.getJSONArray("time");
            SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            SimpleDateFormat show = new SimpleDateFormat("EEE", Locale.getDefault());
            for (int i = 1; i <= 3 && i < days.length(); i++) {
                String label = show.format(parse.parse(days.getString(i))).replace(".", "") + "\n"
                        + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(i)) + "° " + SalaData.weatherSymbol(daily.getJSONArray("weather_code").getInt(i));
                TextView day = text(label, 24, Color.WHITE, regular, Gravity.START);
                day.setSingleLine(false);
                put(card, day, 125, 70, 28 + (i - 1) * 128, 204);
            }
        } catch (Exception e) {
            put(card, text(activity.getString(R.string.style_offline), 30, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 380, 60, 28, 110);
        }
    }

    /** The apps used most recently: Android keeps other apps' Watch Next rows from a launcher like this one. */
    private void continueWatching(FrameLayout card, int x, int y) {
        caption(card, R.string.sala_w_continue);
        AppUsage usage = new AppUsage(activity);
        final Map<String, Long> times = new HashMap<>();
        List<AppAdapter.Entry> used = new ArrayList<>();
        for (AppAdapter.Entry entry : allApps) {
            long then = usage.lastUsed(entry.pkg);
            times.put(entry.pkg, then);
            if (then > 0) used.add(entry);
        }
        Collections.sort(used, (a, b) -> Long.compare(times.get(b.pkg), times.get(a.pkg)));
        if (used.isEmpty()) {
            TextView empty = text(activity.getString(R.string.style_empty_recent), 26, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL);
            put(card, empty, 800, 60, 28, 110);
            target(card, x, y, CARD_W * 2 + CARD_GAP, CARD_H, 26, null);
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(3, used.size()); i++) {
            final AppAdapter.Entry entry = used.get(i);
            float left = 28 + i * 287;
            FrameLayout thumb = wideTile(entry);
            put(card, thumb, 270, 152, left, 66);
            String ago = AppUsage.ago(now, times.get(entry.pkg));
            put(card, text(entry.label + (ago == null ? "" : " · " + ago), 22, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 270, 34, left, 226);
            target(thumb, x + left, y + 66, 270, 152, 14, () -> model.launch(entry));
        }
    }

    private FrameLayout wideTile(AppAdapter.Entry entry) {
        FrameLayout tile = tile(entry, 152, 14);
        return tile;
    }

    private void inputs(FrameLayout card, int x, int y) {
        caption(card, R.string.inputs);
        List<String[]> found = activity.styledInputList();
        float top = 58;
        int shown = 0;
        for (String[] input : found) {
            if (shown == 3) break;
            final String id = input[0];
            boolean connected = input.length > 2 && "1".equals(input[2]);
            TextView row = text((connected ? "● " : "○ ") + input[1], 27, connected ? Color.WHITE : 0xB0FFFFFF, regular, Gravity.CENTER_VERTICAL);
            row.setPadding(px(14), 0, px(14), 0);
            put(card, row, 395, 52, 18, top);
            target(row, x + 18, y + top, 395, 52, 12, () -> activity.styledOpenInput(id));
            top += 56;
            shown++;
        }
        if (activity.styledHasLiveTv()) {
            TextView row = text("▶ " + activity.getString(R.string.live_tv), 27, Color.WHITE, regular, Gravity.CENTER_VERTICAL);
            row.setPadding(px(14), 0, px(14), 0);
            put(card, row, 395, 52, 18, top);
            target(row, x + 18, y + top, 395, 52, 12, activity::styledLiveTv);
        }
    }

    private void sleep(FrameLayout card, int x, int y) {
        caption(card, R.string.sala_w_sleep);
        put(card, text("☾", 74, 0xFFF2C14E, regular, Gravity.CENTER_VERTICAL), 90, 90, 28, 80);
        TextView label = text(activity.getString(R.string.sala_sleep_open), 28, Color.WHITE, regular, Gravity.CENTER_VERTICAL);
        label.setSingleLine(false);
        put(card, label, 290, 110, 118, 72);
        target(card, x, y, CARD_W, CARD_H, 26, activity::styledSleepTimer);
    }

    private void note(FrameLayout card, int x, int y) {
        caption(card, R.string.sala_w_note);
        String note = SalaData.note(activity);
        TextView body = text(note.isEmpty() ? activity.getString(R.string.sala_note_default) : note, 30,
                note.isEmpty() ? 0xB0FFFFFF : Color.WHITE, regular, Gravity.TOP);
        body.setSingleLine(false);
        body.setMaxLines(5);
        put(card, body, 380, 200, 28, 68);
        target(card, x, y, CARD_W, CARD_H, 26, activity::styledSalaSettings);
    }

    private void calendar(FrameLayout card, int x, int y) {
        Calendar now = Calendar.getInstance();
        TextView title = text(new SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(now.getTime()).toUpperCase(Locale.getDefault()), 21, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL);
        title.setLetterSpacing(.14f);
        card.addView(title, at(390, 30, 28, 20));
        int today = now.get(Calendar.DAY_OF_MONTH);
        int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar first = (Calendar) now.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        float cellW = 54, cellH = 33;
        Calendar weekday = (Calendar) first.clone();
        weekday.add(Calendar.DAY_OF_MONTH, -offset);
        SimpleDateFormat letter = new SimpleDateFormat("EEEEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            put(card, text(letter.format(weekday.getTime()).toUpperCase(Locale.getDefault()), 21, 0xAAFFFFFF, regular, Gravity.CENTER), cellW, cellH, 28 + i * cellW, 52);
            weekday.add(Calendar.DAY_OF_MONTH, 1);
        }
        for (int day = 1; day <= days; day++) {
            int slot = day - 1 + offset;
            boolean isToday = day == today;
            TextView cell = text(String.valueOf(day), 22, isToday ? 0xFF10141A : Color.WHITE, isToday ? medium : regular, Gravity.CENTER);
            if (isToday) cell.setBackground(rounded(Color.WHITE, 0, 0, 16));
            put(card, cell, cellW, cellH - 2, 28 + slot % 7 * cellW, 85 + slot / 7 * cellH);
        }
        target(card, x, y, CARD_W, CARD_H, 26, null);
    }

    private void meter(FrameLayout card, String label, float share, int color, float top) {
        put(card, text(label, 24, 0xDDFFFFFF, regular, Gravity.CENTER_VERTICAL), 380, 34, 28, top);
        View track = new View(activity);
        track.setBackground(rounded(0x40FFFFFF, 0, 0, 6));
        put(card, track, 375, 12, 28, top + 38);
        View fill = new View(activity);
        fill.setBackground(rounded(color, 0, 0, 6));
        put(card, fill, Math.max(12, 375 * Math.min(1f, share)), 12, 28, top + 38);
    }

    private void status(FrameLayout card, int x, int y) {
        caption(card, R.string.sala_w_status);
        String network = activity.getString(R.string.style_offline);
        try {
            ConnectivityManager cm = activity.getSystemService(ConnectivityManager.class);
            if (cm != null && cm.getActiveNetwork() != null) {
                WifiManager wifi = activity.getApplicationContext().getSystemService(WifiManager.class);
                WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
                String ssid = info == null ? null : info.getSSID();
                network = ssid == null || ssid.contains("unknown") ? activity.getString(R.string.style_online)
                        : activity.getString(R.string.wifi) + " · " + ssid.replace("\"", "");
            }
        } catch (RuntimeException ignored) {
        }
        put(card, text(network, 25, Color.WHITE, regular, Gravity.CENTER_VERTICAL), 385, 36, 28, 60);
        StatFs disk = new StatFs(activity.getFilesDir().getPath());
        long total = disk.getTotalBytes(), used = total - disk.getAvailableBytes();
        meter(card, activity.getString(R.string.sala_storage) + " " + gb(used) + " / " + gb(total) + " GB", (float) used / total, Color.WHITE, 106);
        android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
        activity.getSystemService(android.app.ActivityManager.class).getMemoryInfo(memory);
        long inUse = memory.totalMem - memory.availMem;
        meter(card, activity.getString(R.string.sala_memory) + " " + gb(inUse) + " / " + gb(memory.totalMem) + " GB", (float) inUse / memory.totalMem, Color.WHITE, 184);
        target(card, x, y, CARD_W, CARD_H, 26, activity::styledNetwork);
    }

    private static String gb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f", bytes / 1073741824f);
    }

    private void matches(FrameLayout card, int x, int y) {
        caption(card, R.string.sala_w_matches);
        target(card, x, y, CARD_W, CARD_H, 26, () -> SalaData.refreshMatches(activity, true, this::dataChanged));
        JSONObject saved = SalaData.matches(activity);
        String team = SalaData.team(activity);
        if (saved == null || !saved.optBoolean("ok")) {
            put(card, text(activity.getString(saved == null ? R.string.sala_loading : R.string.sala_no_data), 30, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 380, 60, 28, 110);
            return;
        }
        JSONArray items = saved.optJSONArray("items");
        if (items == null || items.length() == 0) {
            TextView none = text(activity.getString(R.string.sala_no_match, team), 30, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL);
            none.setSingleLine(false);
            put(card, none, 380, 90, 28, 96);
            return;
        }
        JSONObject match = items.optJSONObject(0);
        for (int i = 0; i < items.length(); i++) {
            if ("in".equals(items.optJSONObject(i).optString("state"))) match = items.optJSONObject(i);
        }
        String state = match.optString("state");
        boolean started = !"pre".equals(state);
        put(card, text(match.optString("home"), 30, Color.WHITE, medium, Gravity.CENTER_VERTICAL), 300, 44, 28, 62);
        put(card, text(match.optString("away"), 30, Color.WHITE, medium, Gravity.CENTER_VERTICAL), 300, 44, 28, 112);
        if (started) {
            put(card, text(match.optString("hs"), 38, Color.WHITE, Typeface.create(medium, Typeface.BOLD), Gravity.CENTER), 70, 44, 340, 62);
            put(card, text(match.optString("as"), 38, Color.WHITE, Typeface.create(medium, Typeface.BOLD), Gravity.CENTER), 70, 44, 340, 112);
        }
        String line;
        int color = 0xCCFFFFFF;
        if ("in".equals(state)) {
            line = activity.getString(R.string.sala_live) + " \u00B7 " + match.optString("clock");
            color = 0xFFFF6B5E;
        } else if ("post".equals(state)) {
            line = activity.getString(R.string.sala_final);
        } else {
            line = DateFormat.getTimeFormat(activity).format(new Date(match.optLong("when")));
        }
        put(card, text(line, 26, color, medium, Gravity.CENTER_VERTICAL), 385, 38, 28, 172);
        put(card, text(match.optString("league"), 22, 0xAAFFFFFF, regular, Gravity.CENTER_VERTICAL), 385, 34, 28, 214);
    }

    private void screen(FrameLayout card, int x, int y) {
        caption(card, R.string.sala_w_screen);
        target(card, x, y, CARD_W, CARD_H, 26, null);
        List<Map.Entry<String, Long>> today = new ArrayList<>(SalaData.screenToday(activity));
        java.util.Iterator<Map.Entry<String, Long>> each = today.iterator();
        while (each.hasNext()) if (byPackage(each.next().getKey()) == null) each.remove();
        long total = 0;
        for (Map.Entry<String, Long> app : today) total += app.getValue();
        put(card, text(SalaData.duration(total), 56, Color.WHITE, light, Gravity.CENTER_VERTICAL), 385, 72, 28, 60);
        for (int i = 0; i < Math.min(2, today.size()); i++) {
            AppAdapter.Entry entry = byPackage(today.get(i).getKey());
            String name = entry == null ? today.get(i).getKey() : entry.label;
            meter(card, name + " " + SalaData.duration(today.get(i).getValue()), total == 0 ? 0 : (float) today.get(i).getValue() / total, 0xFFFF5A52, 138 + i * 72);
        }
    }

    private void world(FrameLayout card, int x, int y) {
        caption(card, R.string.sala_w_world);
        SimpleDateFormat format = new SimpleDateFormat(DateFormat.is24HourFormat(activity) ? "H:mm" : "h:mm a", Locale.getDefault());
        float top = 70;
        for (String zone : SalaData.zones(activity)) {
            put(card, text(SalaData.zoneName(zone), 28, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 200, 56, 28, top);
            format.setTimeZone(TimeZone.getTimeZone(zone));
            TextView time = text(format.format(new Date()), 32, Color.WHITE, regular, Gravity.CENTER_VERTICAL | Gravity.END);
            put(card, time, 190, 56, 215, top);
            clocks.add(new Object[]{time, zone});
            top += 66;
        }
        target(card, x, y, CARD_W, CARD_H, 26, activity::styledSalaSettings);
    }

    private void air(FrameLayout card, Runnable refresh, int w, int x, int y) {
        caption(card, R.string.sala_w_air);
        target(card, x, y, CARD_W * 2 + CARD_GAP, CARD_H, 26, refresh);
        JSONObject air = SalaData.air(activity), forecast = SalaData.forecast(activity);
        if (air == null || forecast == null) {
            put(card, text(activity.getString(R.string.style_offline), 30, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 380, 60, 28, 110);
            return;
        }
        try {
            int aqi = air.getJSONObject("current").getInt("us_aqi");
            put(card, text("AQI " + aqi, 62, Color.WHITE, light, Gravity.CENTER_VERTICAL), 270, 80, 28, 76);
            int quality = aqi <= 50 ? R.string.sala_air_good : aqi <= 100 ? R.string.sala_air_fair : R.string.sala_air_bad;
            put(card, text(activity.getString(quality), 26, aqi <= 50 ? 0xFF7BE08A : aqi <= 100 ? 0xFFF2C14E : 0xFFFF7A6E, regular, Gravity.CENTER_VERTICAL), 270, 40, 28, 166);
            JSONArray times = forecast.getJSONObject("hourly").getJSONArray("time");
            JSONArray rain = forecast.getJSONObject("hourly").getJSONArray("precipitation_probability");
            SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT);
            long now = System.currentTimeMillis();
            int start = 0;
            while (start < times.length() - 1 && parse.parse(times.getString(start)).getTime() < now - 3600000L) start++;
            int count = Math.min(8, times.length() - start);
            int[] values = new int[count];
            String[] labels = new String[count];
            SimpleDateFormat hour = new SimpleDateFormat(DateFormat.is24HourFormat(activity) ? "H" : "ha", Locale.getDefault());
            for (int i = 0; i < count; i++) {
                values[i] = rain.isNull(start + i) ? 0 : rain.getInt(start + i);
                labels[i] = hour.format(parse.parse(times.getString(start + i))).replace(" ", "").toLowerCase(Locale.getDefault());
            }
            put(card, new Bars(values, labels), 570, 210, 310, 62);
        } catch (Exception e) {
            put(card, text(activity.getString(R.string.style_offline), 30, 0xCCFFFFFF, regular, Gravity.CENTER_VERTICAL), 380, 60, 28, 110);
        }
    }

    /** Chance of rain per hour, as plain bars. */
    private final class Bars extends View {
        private final int[] values;
        private final String[] labels;
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bars(int[] values, String[] labels) {
            super(activity);
            this.values = values;
            this.labels = labels;
            bar.setColor(0xFF5AA9FF);
            label.setColor(0xDDFFFFFF);
            label.setTextSize(py(21));
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(regular);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cell = (float) getWidth() / Math.max(1, values.length), room = getHeight() - py(62);
            for (int i = 0; i < values.length; i++) {
                float h = Math.max(py(4), room * values[i] / 100f), cx = cell * (i + .5f), base = getHeight() - py(30);
                canvas.drawRoundRect(cx - cell * .3f, base - h, cx + cell * .3f, base, py(6), py(6), bar);
                canvas.drawText(labels[i], cx, getHeight() - py(4), label);
                canvas.drawText(values[i] + "%", cx, base - h - py(6), label);
            }
        }
    }

    // ---- the remote -------------------------------------------------------------------------------

    private void showWidgets(boolean open) {
        widgetsOpen = open;
        homeLayer.setVisibility(open ? View.GONE : View.VISIBLE);
        widgetLayer.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open && animate) {
            widgetLayer.setAlpha(0f);
            widgetLayer.setTranslationY(py(30));
            widgetLayer.animate().alpha(1).translationY(0).setDuration(180).start();
        }
    }

    private View expected() {
        if (appsOpen) return appFocus < appTargets.size() ? appTargets.get(appFocus).view : null;
        if (widgetsOpen) return targetFocus < targets.size() ? targets.get(targetFocus).view : null;
        if (pillFocus >= 0) return pills.get(pillFocus);
        return favFocus < favViews.size() ? favViews.get(favFocus) : null;
    }

    void restoreFocus() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View view = expected();
        if (view == null && !widgetsOpen) view = pills.get(0);
        if (view != null) view.requestFocus();
        if (appsOpen) return;
        if (!widgetsOpen && pillFocus < 0 && favFocus < favEntries.size()) favName.setText(favEntries.get(favFocus).label);
        // Slides only for the tile after the favourites, which starts past the screen's edge.
        float slide = !widgetsOpen && pillFocus < 0 && favFocus >= FAVORITES ? -py(300) : 0;
        if (animate) favRow.animate().translationX(slide).setDuration(180).start();
        else favRow.setTranslationX(slide);
    }

    boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        boolean arrow = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (!down) back();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (!down && !event.isCanceled()) activity.styledSalaSettings();
            return true;
        }
        if (!ok && !arrow) return false;
        // What the viewer sees selected is the only thing OK may ever open.
        View expected = expected();
        if (expected != null && activity.getCurrentFocus() != expected) {
            if (down) restoreFocus();
            okDown = -1;
            return true;
        }
        if (ok) {
            handleOk(event);
            return true;
        }
        if (down) move(code);
        return true;
    }

    private void back() {
        if (appsOpen) {
            closeApps();
        } else if (widgetsOpen) {
            showWidgets(false);
        } else {
            pillFocus = -1;
        }
        restoreFocus();
    }

    private void handleOk(KeyEvent event) {
        boolean onFavorite = !appsOpen && !widgetsOpen && pillFocus < 0 && favFocus < favEntries.size();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                okFired = false;
                okDown = event.getDownTime();
                if (onFavorite) {
                    root.removeCallbacks(holdTimer);
                    root.postDelayed(holdTimer, HOLD_MS);
                }
            } else if (onFavorite && !okFired && event.getEventTime() - okDown >= HOLD_MS) {
                fireHold();
            }
            return;
        }
        root.removeCallbacks(holdTimer);
        boolean pressed = okDown >= 0 && !okFired && !event.isCanceled();
        okDown = -1;
        if (pressed) activate();
    }

    private void fireHold() {
        root.removeCallbacks(holdTimer);
        if (okDown < 0 || okFired) return;
        okFired = true;
        showFavoriteMenu();
    }

    private void activate() {
        if (appsOpen) {
            if (appFocus < appTargets.size()) appTargets.get(appFocus).action.run();
        } else if (widgetsOpen) {
            if (targetFocus < targets.size() && targets.get(targetFocus).action != null) targets.get(targetFocus).action.run();
        } else if (pillFocus >= 0) {
            ((Runnable) pills.get(pillFocus).getTag()).run();
        } else if (favFocus < favEntries.size()) {
            model.launch(favEntries.get(favFocus));
        } else if (favFocus == favEntries.size()) {
            openApps();
        }
    }

    private void move(int code) {
        int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int dy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (appsOpen) {
            int next = StyleNav.move(appFocus, appTargets.size(), FAVORITES, 0, dx, dy);
            if (next != StyleNav.OUT) {
                appFocus = next;
                restoreFocus();
            }
            return;
        }
        if (widgetsOpen) {
            int[][] rects = new int[targets.size()][];
            for (int i = 0; i < rects.length; i++) {
                Target t = targets.get(i);
                rects[i] = new int[]{t.x, t.y, t.w, t.h};
            }
            int next = StyleNav.nearest(rects, targetFocus, dx, dy);
            if (next != StyleNav.OUT) {
                targetFocus = next;
                targets.get(next).view.requestFocus();
            } else if (dy < 0) {
                back();
            }
            return;
        }
        if (pillFocus >= 0) {
            if (dx != 0) {
                int next = pillFocus + dx;
                if (next >= 0 && next < pills.size()) pillFocus = next;
            } else if (dy > 0 && !favViews.isEmpty()) {
                pillFocus = -1;
            }
            restoreFocus();
            return;
        }
        if (dx != 0) {
            int next = favFocus + dx;
            if (next >= 0 && next < favViews.size()) favFocus = next;
        } else if (dy < 0) {
            pillFocus = 0;
        } else if (dy > 0 && !targets.isEmpty()) {
            showWidgets(true);
        }
        restoreFocus();
    }
}
