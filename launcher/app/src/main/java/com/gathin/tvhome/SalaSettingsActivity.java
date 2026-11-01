package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Sala's own settings: its background, its widgets and the few words it shows. */
public final class SalaSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    private static final int[] DIMS = {0, 10, 20, 30, 40, 50, 60, 70};
    private static final int[] ROTATIONS = {0, 5, 60, 1440};
    private LinearLayout content;
    private ScrollView scroll;
    private SharedPreferences prefs;
    private float density;
    private int level;
    private int tab;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = SalaData.prefs(this);
        density = getResources().getDisplayMetrics().density;
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0E1217);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(48);
        content.setPadding(pad, dp(36), pad, dp(36));
        scroll.addView(content);
        setContentView(scroll);
        showMain();
    }

    @Override
    public void onBackPressed() {
        if (level > 0) showMain();
        else super.onBackPressed();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void changed() {
        SharedPreferences main = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        main.edit().putLong(Config.SETTINGS_REVISION_KEY, main.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void title(int text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(18);
        content.addView(title, lp);
    }

    private TextView rowView(String label, Runnable action) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(18);
        row.setTextColor(getColorStateList(R.color.settings_text));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setSingleLine(true);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private TextView row(String label, Runnable action) {
        TextView row = rowView(label, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.bottomMargin = dp(10);
        content.addView(row, lp);
        return row;
    }

    private void ask(int title, String value, java.util.function.Consumer<String> done) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(value);
        new AlertDialog.Builder(this).setTitle(title).setView(field)
                .setPositiveButton(android.R.string.ok, (d, w) -> done.accept(field.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showMain() {
        level = 0;
        content.removeAllViews();
        title(R.string.sala_settings);
        TextView first = row(getString(R.string.sala_background), this::showBackgrounds);
        row(getString(R.string.sala_widgets), this::showWidgets);
        row(getString(R.string.sala_w_note), () -> ask(R.string.sala_w_note, SalaData.note(this), value -> {
            prefs.edit().putString("note", value).apply();
            changed();
        }));
        row(getString(R.string.sala_name), () -> ask(R.string.sala_name, SalaData.name(this), value -> {
            prefs.edit().putString("name", value).apply();
            changed();
        }));
        row(getString(R.string.sala_city) + ": " + SalaData.city(this), () -> ask(R.string.sala_city, SalaData.city(this), value -> {
            if (value.isEmpty()) return;
            prefs.edit().putString("city", value).apply();
            changed();
            showMain();
        }));
        row(getString(R.string.sala_team) + ": " + SalaData.team(this), () -> ask(R.string.sala_team, SalaData.team(this), value -> {
            if (value.isEmpty()) return;
            prefs.edit().putString("team", value).apply();
            changed();
            showMain();
        }));
        row(getString(R.string.sala_cities), this::chooseZones);
        row(getString(R.string.launcher_settings), () -> startActivity(new Intent(this, LauncherSettingsActivity.class)));
        first.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void chooseZones() {
        String[] names = new String[SalaData.ZONES.length];
        boolean[] checked = new boolean[names.length];
        List<String> zones = SalaData.zones(this);
        for (int i = 0; i < names.length; i++) {
            names[i] = SalaData.ZONES[i][1];
            checked[i] = zones.contains(SalaData.ZONES[i][0]);
        }
        new AlertDialog.Builder(this).setTitle(R.string.sala_cities)
                .setMultiChoiceItems(names, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    List<String> chosen = new ArrayList<>();
                    for (int i = 0; i < checked.length && chosen.size() < 3; i++) {
                        if (checked[i]) chosen.add(SalaData.ZONES[i][0]);
                    }
                    if (chosen.isEmpty()) return;
                    SalaData.saveZones(this, chosen);
                    changed();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int widgetName(String widget) {
        switch (widget) {
            case SalaData.WEATHER: return R.string.sala_w_weather;
            case SalaData.CONTINUE: return R.string.sala_w_continue;
            case SalaData.INPUTS: return R.string.inputs;
            case SalaData.SLEEP: return R.string.sala_w_sleep;
            case SalaData.NOTE: return R.string.sala_w_note;
            case SalaData.CALENDAR: return R.string.sala_w_calendar;
            case SalaData.STATUS: return R.string.sala_w_status;
            case SalaData.MATCHES: return R.string.sala_w_matches;
            case SalaData.SCREEN: return R.string.sala_w_screen;
            case SalaData.WORLD: return R.string.sala_w_world;
            default: return R.string.sala_w_air;
        }
    }

    private void showWidgets() {
        showWidgets(null);
    }

    /** Every widget: OK switches it on or off, the arrows beside it move it up or down the order. */
    private void showWidgets(String focusOn) {
        level = 1;
        content.removeAllViews();
        title(R.string.sala_widgets);
        List<String> order = SalaData.order(this);
        Set<String> active = SalaData.active(this);
        View focus = null;
        for (int i = 0; i < order.size(); i++) {
            final String widget = order.get(i);
            final int index = i;
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            String state = getString(active.contains(widget) ? R.string.on : R.string.off);
            TextView main = rowView(getString(widgetName(widget)) + ": " + state, () -> {
                Set<String> now = new HashSet<>(SalaData.active(this));
                if (!now.remove(widget)) now.add(widget);
                SalaData.saveActive(this, now);
                changed();
                showWidgets(widget);
            });
            LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(0, dp(52), 1);
            line.addView(main, mainLp);
            TextView up = rowView("▲", () -> moveWidget(order, index, -1));
            up.setGravity(Gravity.CENTER);
            up.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(dp(64), dp(52));
            small.leftMargin = dp(10);
            line.addView(up, small);
            TextView down = rowView("▼", () -> moveWidget(order, index, 1));
            down.setGravity(Gravity.CENTER);
            down.setPadding(0, 0, 0, 0);
            line.addView(down, new LinearLayout.LayoutParams(small));
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            lineLp.bottomMargin = dp(10);
            content.addView(line, lineLp);
            if (widget.equals(focusOn) || focus == null) focus = main;
            if (widget.equals(focusOn)) focusOn = "";
        }
        if (focus != null) focus.requestFocus();
    }

    private void moveWidget(List<String> order, int index, int by) {
        int to = index + by;
        if (to < 0 || to >= order.size()) return;
        String widget = order.get(index);
        java.util.Collections.swap(order, index, to);
        SalaData.saveOrder(this, order);
        changed();
        showWidgets(widget);
    }

    private void showBackgrounds() {
        level = 1;
        content.removeAllViews();
        title(R.string.sala_background);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        int rotate = prefs.getInt("rotate", 0);
        String rotateText = rotate == 0 ? getString(R.string.off) : rotate == 5 ? "5 min" : rotate == 60 ? "1 h" : getString(R.string.sala_daily);
        options.addView(rowView(getString(R.string.sala_rotate, rotateText), () -> {
            int at = 0;
            for (int i = 0; i < ROTATIONS.length; i++) if (ROTATIONS[i] == rotate) at = i;
            prefs.edit().putInt("rotate", ROTATIONS[(at + 1) % ROTATIONS.length]).putLong("rotated", System.currentTimeMillis()).apply();
            changed();
            showBackgrounds();
        }), new LinearLayout.LayoutParams(0, dp(52), 1));
        int dim = prefs.getInt("dim", 30);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(52), 1);
        second.leftMargin = dp(10);
        options.addView(rowView(getString(R.string.sala_dim, dim), () -> {
            int at = 0;
            for (int i = 0; i < DIMS.length; i++) if (DIMS[i] == dim) at = i;
            prefs.edit().putInt("dim", DIMS[(at + 1) % DIMS.length]).apply();
            changed();
            showBackgrounds();
        }), second);
        LinearLayout.LayoutParams optionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        optionsLp.bottomMargin = dp(10);
        content.addView(options, optionsLp);

        int[] tabs = {R.string.sala_tab_collections, R.string.sala_tab_photos, R.string.sala_tab_colors, R.string.sala_tab_time};
        LinearLayout bar = new LinearLayout(this);
        View focus = null;
        for (int i = 0; i < tabs.length; i++) {
            final int which = i;
            TextView tabView = rowView(getString(tabs[i]), () -> {
                tab = which;
                showBackgrounds();
            });
            tabView.setGravity(Gravity.CENTER);
            tabView.setSelected(i == tab);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (i > 0) lp.leftMargin = dp(10);
            bar.addView(tabView, lp);
            if (i == tab) focus = tabView;
        }
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        barLp.bottomMargin = dp(16);
        content.addView(bar, barLp);

        List<String> choices = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (tab == 0) {
            for (int i = 0; i < SalaBackdrops.SCENES.length; i++) {
                choices.add(SalaBackdrops.SCENES[i]);
                names.add(getString(SalaBackdrops.SCENE_NAMES[i]));
            }
        } else if (tab == 1) {
            row(getString(R.string.sala_import), this::importPhotos);
            for (String photo : SalaBackdrops.photos(this)) {
                choices.add(photo);
                names.add(photo.substring(6));
            }
        } else if (tab == 2) {
            for (int color : SalaBackdrops.COLORS) {
                choices.add("color:" + color);
                names.add(String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF));
            }
        } else {
            choices.add(SalaBackdrops.AUTO);
            names.add(getString(R.string.sala_tab_time));
        }
        gallery(choices, names);
        if (focus != null) focus.requestFocus();
    }

    private void gallery(List<String> choices, List<String> names) {
        String current = SalaBackdrops.current(this);
        LinearLayout line = null;
        for (int i = 0; i < choices.size(); i++) {
            if (i % 4 == 0) {
                line = new LinearLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(10);
                content.addView(line, lp);
            }
            final String choice = choices.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackgroundResource(R.drawable.settings_choice_bg);
            item.setPadding(dp(8), dp(8), dp(8), dp(6));
            item.setFocusable(true);
            item.setClickable(true);
            item.setSelected(choice.equals(current));
            ImageView thumb = new ImageView(this);
            Bitmap picture = SalaBackdrops.paint(this, choice, 240, 135);
            thumb.setImageBitmap(picture);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            item.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));
            TextView name = new TextView(this);
            name.setText(names.get(i));
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            name.setTextColor(getColorStateList(R.color.settings_text));
            name.setDuplicateParentStateEnabled(true);
            name.setPadding(0, dp(4), 0, 0);
            item.addView(name);
            item.setOnClickListener(v -> {
                prefs.edit().putString("background", choice).apply();
                changed();
                showBackgrounds();
            });
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            itemLp.rightMargin = dp(10);
            line.addView(item, itemLp);
        }
        while (line != null && line.getChildCount() < 4) {
            LinearLayout.LayoutParams spacer = new LinearLayout.LayoutParams(0, 1, 1);
            spacer.rightMargin = dp(10);
            line.addView(new View(this), spacer);
        }
    }

    // ---- photos from a USB drive ------------------------------------------------------------------

    private void importPhotos() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, 1);
            return;
        }
        File[] volumes = new File("/storage").listFiles();
        int copied = 0;
        boolean found = false;
        if (volumes != null) {
            for (File volume : volumes) {
                File folder = new File(volume, "Fondos");
                File[] files = folder.listFiles();
                if (files == null) continue;
                found = true;
                for (File file : files) {
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) continue;
                    if (copy(file, new File(SalaBackdrops.photoDir(this), file.getName()))) copied++;
                }
            }
        }
        Toast.makeText(this, found ? getString(R.string.sala_imported, copied) : getString(R.string.sala_no_usb), Toast.LENGTH_LONG).show();
        showBackgrounds();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) importPhotos();
    }

    private static boolean copy(File from, File to) {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
