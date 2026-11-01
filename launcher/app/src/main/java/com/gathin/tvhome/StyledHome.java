package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The home screens other than the default: LauncherStyle.WII (a 4x3 page of rounded channels on light grey, the time
 * and date below, sounds while moving), LauncherStyle.ROKU (the classic text menu beside a grid) and
 * LauncherStyle.ROKU_NEW (a skyline header, an icon rail, a featured block and a quick-access grid). MainActivity stays the one Home activity, so the Home key, boot and the system's default-home choice are
 * the same in every style; it hands its lifecycle and keys to this class while a style is chosen.
 */
final class StyledHome {
    private static final String TAG = "StyledHome";
    private static final int WII_COLUMNS = 4, WII_PAGE = 12, CLASSIC_COLUMNS = 4, GRID_COLUMNS = 6;
    /** The grid style's buttons: the top bar first (circle, search, status, settings), then the bottom tabs. */
    private static final int GRID_SEARCH = 1, GRID_TABS = 4, GRID_YELLOW = 0xFFE8B040, GRID_BAR = 0xFF111111;
    private static final int WII_TEXT = 0xFF6F767B;

    /** A style whose screen cannot be built falls back to the default look: a TV must never lose its Home. */
    static StyledHome create(MainActivity activity, Config cfg) {
        String style = LauncherStyle.read(activity);
        if (LauncherStyle.APPLE.equals(style)) return null;
        try {
            StyledHome home = new StyledHome(activity, style, cfg);
            activity.setContentView(home.root);
            return home;
        } catch (RuntimeException e) {
            Log.e(TAG, "could not build " + style + "; back to the default look", e);
            LauncherStyle.save(activity, LauncherStyle.APPLE);
            return null;
        }
    }

    private static final class Cell {
        final FrameLayout view;
        final ImageView art;
        final TextView name;
        AppAdapter.Entry entry;
        int x, y, w, h, section;
        /** A tile that is no app (Metro's Inputs and Settings tiles). */
        Runnable action;
        String key;
        TextView sub, star;
        View dot;

        Cell(FrameLayout view, ImageView art, TextView name) {
            this.view = view;
            this.art = art;
            this.name = name;
        }
    }

    private final MainActivity activity;
    private final boolean wii, classic, modern, metro, grid;
    private final boolean animate;
    private final float density;
    private final int width, height;
    private final AppAdapter model;
    private final FrameLayout root;
    private final Typeface regular, medium, light;
    private final StyleSounds sounds;
    private final List<AppAdapter.Entry> entries = new ArrayList<>();
    private final List<Cell> cells = new ArrayList<>();
    /** The other controls: Inputs and Settings under the channels, or the rows of the side menu. */
    private final List<View> buttons = new ArrayList<>();

    private int cellWidth, cellHeight;
    private final Map<String, RokuSkin.Edges> edgeCache = new HashMap<>();
    private final Map<String, android.graphics.Bitmap> bannerCache = new HashMap<>();
    private FrameLayout classicMenu, spot;
    private ImageView spotArt;
    private TextView spotName, rokuTitle, rokuLabel, rokuCount;
    private View chevronLeft, chevronRight;
    private TextClock classicClock;
    private View classicFade, railShade;
    private android.graphics.Bitmap skyline;
    private final List<TextView> railNames = new ArrayList<>();
    private final TextView[] sectionLabels = new TextView[2];
    private boolean menuOpen, is24, metroEntered;
    private android.widget.HorizontalScrollView metroScroll;
    private FrameLayout metroGrid;
    private final List<AppAdapter.Entry> allEntries = new ArrayList<>();
    private AppUsage usage;
    private AppFavorites favorites;
    private int gridTab;
    private String gridFilter = "";
    private TextView gridSearch, gridOnline, gridEmpty;
    private boolean gridIsOnline;
    private final Map<String, Drawable> blurCache = new HashMap<>();
    private final List<TextView> gridTabs = new ArrayList<>();
    private Typeface metroLabelFace;
    private FrameLayout rokuGrid;
    private ScrollView rokuScroll;
    private View arrowLeft, arrowRight;
    private FrameLayout wiiLayer;
    private final Cell[] peekPrevious = new Cell[WII_PAGE / WII_COLUMNS];
    private final Cell[] peekNext = new Cell[WII_PAGE / WII_COLUMNS];
    private ObjectAnimator pulse;

    private int focus;
    private String focusKey;
    private int buttonFocus = -1;
    private int page;
    private int packageSequence;
    private boolean loaded;

    private StyledHome(MainActivity activity, String style, Config cfg) {
        this.activity = activity;
        wii = LauncherStyle.WII.equals(style);
        classic = LauncherStyle.ROKU.equals(style);
        modern = LauncherStyle.ROKU_NEW.equals(style);
        metro = LauncherStyle.METRO.equals(style);
        grid = LauncherStyle.GRID.equals(style);
        animate = cfg.animations && !cfg.reducedMotion;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        density = metrics.density;
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        regular = UiFont.get(activity, R.string.font_regular);
        medium = UiFont.get(activity, R.string.font_medium);
        light = UiFont.get(activity, R.string.font_light);
        model = new AppAdapter(activity, activity, cfg.columns, cfg);
        Order order = Order.load(Order.file(activity));
        model.setSavedOrder(Order.ranks(order, cfg.dockApps), Order.docked(order, cfg.dockApps));
        root = new FrameLayout(activity);
        // On the Roku screen this is what keeps the scrolling apps out from under the top bar.
        root.setClipChildren(!wii);
        sounds = wii || grid ? new StyleSounds(grid) : null;
        if (wii) buildWii(cfg);
        else if (classic) buildClassic(cfg);
        else if (metro) buildMetro();
        else if (grid) buildGrid(cfg);
        else buildModern();
        ClockFormat.apply(root, cfg.clockFormat);
    }

    // ---- lifecycle, called by MainActivity --------------------------------------------------------

    void onResume() {
        if (sounds != null) sounds.open();
        ChangedPackages changed = activity.getPackageManager().getChangedPackages(packageSequence);
        if (changed != null) packageSequence = changed.getSequenceNumber();
        if (!loaded || changed != null) reload();
        else if (grid) refreshUsage();
        if (grid) updateOnlinePill();
        root.post(this::restoreFocus);
    }

    void onStop() {
        stopPulse();
        if (sounds != null) sounds.close();
    }

    /** Another app needs the memory while Home is in the background; the artwork comes back on resume. */
    void releaseArt() {
        for (Cell cell : cells) cell.art.setImageDrawable(null);
        entries.clear();
        model.clear();
        bannerCache.clear();
        blurCache.clear();
        edgeCache.clear();
        if (spotArt != null) spotArt.setImageDrawable(null);
        loaded = false;
    }

    /** The Home key on Home: back to the first app. */
    void goHome() {
        focus = 0;
        focusKey = null;
        buttonFocus = classic ? 0 : -1;
        if (rokuScroll != null) rokuScroll.scrollTo(0, 0);
        if (metroScroll != null) metroScroll.scrollTo(0, 0);
        if (classic) setMenuOpen(true);
        if (modern) setRailOpen(false);
        if (loaded) restoreFocus();
    }

    /** D-pad directions go through {@link StyleNav}, so a press moves one step and never falls through. */
    boolean onKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        boolean arrow = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        // A press while the screen is being rebuilt, with the focus not where this class left it, only puts the focus
        // back. What the viewer sees selected is the only thing OK may ever open.
        if ((ok || arrow) && loaded && !focusInSync()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) restoreFocus();
            return true;
        }
        int dx = 0, dy = 0;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: dx = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dx = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: dy = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dy = 1; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // Metro's press: the tile gives way under the finger.
                if (metro && animate && focus < cells.size()) {
                    float scale = event.getAction() == KeyEvent.ACTION_DOWN ? .96f : 1.04f;
                    cells.get(focus).view.animate().scaleX(scale).scaleY(scale).setDuration(90).start();
                }
                return false;
            case KeyEvent.KEYCODE_MENU:
                // The options key: on every style a direct way into the settings, as the classic corner advertises.
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    if (grid && buttonFocus < 0 && focus < cells.size()) toggleFavorite(cells.get(focus));
                    else activity.styledLauncherSettings();
                }
                return true;
            default: return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) move(dx, dy);
        return true;
    }

    // ---- focus ------------------------------------------------------------------------------------

    private void move(int dx, int dy) {
        if (buttonFocus >= 0) {
            moveInButtons(dx, dy);
            return;
        }
        int next = modern || metro ? StyleNav.nearest(cellRects(), focus, dx, dy)
                : StyleNav.move(focus, entries.size(), wii ? WII_COLUMNS : grid ? GRID_COLUMNS : CLASSIC_COLUMNS, wii ? WII_PAGE : 0, dx, dy);
        if (next != StyleNav.OUT) {
            boolean turned = wii && next / WII_PAGE != page;
            focusTile(next);
            play(turned ? StyleSounds.PAGE : StyleSounds.TICK);
        } else if (wii && dy > 0) {
            focusButton(focus % WII_COLUMNS < WII_COLUMNS / 2 ? 0 : 1);
            play(StyleSounds.TICK);
        } else if (grid) {
            if (dy != 0) play(StyleSounds.TICK);
            if (dy < 0) focusButton(GRID_SEARCH);
            else if (dy > 0) focusButton(GRID_TABS + gridTab);
        } else if ((classic || modern) && dx < 0) {
            if (classic) setMenuOpen(true);
            else setRailOpen(true);
            focusButton(0);
            if (modern) dimOthers();
        }
    }

    private boolean focusInSync() {
        int cell = wii ? focus % WII_PAGE : focus;
        View expected = buttonFocus >= 0 && buttonFocus < buttons.size() ? buttons.get(buttonFocus)
                : cell < cells.size() ? cells.get(cell).view : null;
        return expected == null || activity.getCurrentFocus() == expected;
    }

    private int[][] cellRects() {
        int[][] rects = new int[cells.size()][];
        for (int i = 0; i < rects.length; i++) {
            Cell c = cells.get(i);
            rects[i] = new int[]{c.x, c.y, c.w, c.h};
        }
        return rects;
    }

    private void moveInButtons(int dx, int dy) {
        if (grid) {
            boolean top = buttonFocus < GRID_TABS;
            if (dx != 0) {
                int next = buttonFocus + dx;
                if (next >= (top ? 0 : GRID_TABS) && next < (top ? GRID_TABS : buttons.size())) {
                    focusButton(next);
                    play(StyleSounds.TICK);
                }
            } else if (top && dy > 0) {
                if (entries.isEmpty()) focusButton(GRID_TABS + gridTab);
                else focusTile(Math.min(focus, entries.size() - 1));
                play(StyleSounds.TICK);
            } else if (!top && dy < 0) {
                if (entries.isEmpty()) focusButton(GRID_SEARCH);
                else focusTile(Math.min(focus, entries.size() - 1));
                play(StyleSounds.TICK);
            }
            return;
        }
        if (wii) {
            if (dy < 0 && !entries.isEmpty()) {
                int start = page * WII_PAGE;
                int inPage = Math.min(start + WII_PAGE, entries.size()) - start;
                int lastRow = (inPage - 1) / WII_COLUMNS;
                int column = buttonFocus == 0 ? 0 : WII_COLUMNS - 1;
                focusTile(start + Math.min(lastRow * WII_COLUMNS + column, inPage - 1));
                play(StyleSounds.TICK);
            } else if (dx != 0) {
                int next = buttonFocus + dx;
                if (next >= 0 && next < buttons.size()) {
                    focusButton(next);
                    play(StyleSounds.TICK);
                }
            }
            return;
        }
        if (dx > 0 && !entries.isEmpty()) {
            if (classic) setMenuOpen(false);
            else setRailOpen(false);
            focusTile(Math.min(focus, entries.size() - 1));
        } else if (dy != 0) {
            int next = buttonFocus + dy;
            if (next >= 0 && next < buttons.size()) focusButton(next);
        }
    }

    private void focusTile(int index) {
        buttonFocus = -1;
        focus = index;
        focusKey = metro ? cells.get(index).key : entries.get(index).component.flattenToShortString();
        if (wii && index / WII_PAGE != page) {
            int direction = index / WII_PAGE > page ? 1 : -1;
            showPage(index / WII_PAGE);
            if (animate) {
                wiiLayer.setTranslationX(direction * WII_COLUMNS * width * WiiSkin.PITCH_X);
                wiiLayer.animate().translationX(0).setDuration(280)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            }
        }
        Cell cell = cells.get(wii ? index % WII_PAGE : index);
        cell.view.requestFocus();
        if (wii) return;
        if (metro) {
            keepVisibleSideways(cell);
            return;
        }
        keepVisible(cell);
        updateSidePanel(cell);
        if (classic) {
            rokuLabel.setText(cell.entry.label);
            rokuCount.setText(activity.getString(R.string.style_item_of, index + 1, entries.size()));
        } else if (modern) {
            dimOthers();
        }
    }

    private void focusButton(int index) {
        buttonFocus = index;
        buttons.get(index).requestFocus();
    }

    private void restoreFocus() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (classic) layoutClassic();
        if (buttonFocus >= 0) {
            focusButton(buttonFocus);
            if (modern) dimOthers();
        } else if (metro) {
            if (!cells.isEmpty()) {
                int index = -1;
                for (int i = 0; i < cells.size(); i++) if (cells.get(i).key.equals(focusKey)) index = i;
                focusTile(index >= 0 ? index : Math.min(focus, cells.size() - 1));
            }
        } else if (entries.isEmpty()) focusButton(grid ? GRID_TABS + gridTab : 0);
        else {
            int index = indexOf(focusKey);
            focusTile(index >= 0 ? index : Math.min(focus, entries.size() - 1));
        }
    }

    private int indexOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    private void play(int sound) {
        if (sounds != null) sounds.play(sound);
    }

    // ---- apps -------------------------------------------------------------------------------------

    private void reload() {
        List<ResolveInfo> leanback = activity.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        model.reload(Collections.<Cards.Card>emptyList(), leanback);
        entries.clear();
        for (int i = 0; i < model.appCount(); i++) {
            AppAdapter.Entry entry = model.entryAt(i);
            if (entry.kind != AppAdapter.Entry.EMPTY) entries.add(entry);
        }
        loaded = true;
        if (wii) {
            page = Math.min(page, entries.isEmpty() ? 0 : (entries.size() - 1) / WII_PAGE);
            showPage(page);
        } else if (classic) {
            fillClassic();
        } else if (metro) {
            fillMetro();
        } else if (grid) {
            allEntries.clear();
            allEntries.addAll(entries);
            showGridView();
        } else {
            fillModern();
        }
    }

    private void showPage(int number) {
        page = number;
        for (int slot = 0; slot < cells.size(); slot++) {
            int index = number * WII_PAGE + slot;
            bind(cells.get(slot), index < entries.size() ? entries.get(index) : null);
        }
        boolean hasPrevious = number > 0, hasNext = (number + 1) * WII_PAGE < entries.size();
        for (int row = 0; row < peekNext.length; row++) {
            int previous = (number - 1) * WII_PAGE + row * WII_COLUMNS + WII_COLUMNS - 1;
            int next = (number + 1) * WII_PAGE + row * WII_COLUMNS;
            peekPrevious[row].view.setVisibility(hasPrevious ? View.VISIBLE : View.GONE);
            peekNext[row].view.setVisibility(hasNext ? View.VISIBLE : View.GONE);
            if (hasPrevious) bind(peekPrevious[row], entries.get(previous));
            if (hasNext) bind(peekNext[row], next < entries.size() ? entries.get(next) : null);
            peekPrevious[row].view.setFocusable(false);
            peekNext[row].view.setFocusable(false);
        }
        arrowLeft.setVisibility(hasPrevious ? View.VISIBLE : View.INVISIBLE);
        arrowRight.setVisibility(hasNext ? View.VISIBLE : View.INVISIBLE);
    }

    private void bind(Cell cell, AppAdapter.Entry entry) {
        cell.entry = entry;
        if (!wii) {
            showArt(cell.art, cell.view, entry);
            cell.art.setPaddingRelative(0, 0, 0, 0);
            boolean named = entry.banner == null;
            cell.name.setText(named ? entry.label : "");
            cell.name.setVisibility(named ? View.VISIBLE : View.GONE);
            if (named) cell.art.setPaddingRelative(cell.h / 4, cell.h / 8, cell.h / 4, cell.h * 3 / 8);
            cell.view.setFocusable(true);
            cell.view.setContentDescription(entry.label);
            return;
        }
        boolean banner = entry != null && entry.banner != null;
        boolean icon = entry != null && !banner && entry.icon != null;
        cell.art.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        int inset = icon ? cellHeight / 5 : 0;
        cell.art.setPaddingRelative(inset, inset / 2, inset, icon ? inset * 3 / 2 : 0);
        cell.art.setImageDrawable(banner ? entry.banner : icon ? entry.icon : null);
        cell.name.setText(entry == null || banner ? "" : entry.label);
        cell.name.setVisibility(entry == null || banner ? View.GONE : View.VISIBLE);
        cell.view.setFocusable(entry != null);
        cell.view.setContentDescription(entry == null ? null : entry.label);
        cell.view.setBackground(entry == null ? emptyChannel() : tileBackground());
        cell.view.setForeground(entry != null && cell.view.isFocused() ? ring() : wii ? wiiEdge() : null);
    }

    private void open(Cell cell) {
        if (cell.action != null) {
            cell.action.run();
            return;
        }
        if (cell.entry == null) return;
        play(grid ? StyleSounds.NOTICE : StyleSounds.SELECT);
        model.launch(cell.entry);
    }

    // ---- shared views -----------------------------------------------------------------------------

    private int dp(float value) {
        return Math.round(value * density);
    }

    private FrameLayout.LayoutParams at(int w, int h, int left, int top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }

    private GradientDrawable rounded(int fill, int strokeColor, float strokeDp, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) shape.setStroke(dp(strokeDp), strokeColor);
        return shape;
    }

    private float cornerDp() {
        return wii ? 12.5f : metro || grid ? 0 : 3;
    }

    private Drawable ring() {
        if (wii) return rounded(Color.TRANSPARENT, WiiSkin.SELECT_BLUE, 3, cornerDp() - 1.5f);
        if (grid) return rounded(Color.TRANSPARENT, GRID_YELLOW, 3, 0);
        if (classic) return rounded(Color.TRANSPARENT, 0xFFD6D4DA, 5, .5f);
        // White, with a dark hairline inside it so that it also shows on a white banner.
        android.graphics.drawable.LayerDrawable frame = new android.graphics.drawable.LayerDrawable(new Drawable[]{
                rounded(Color.TRANSPARENT, Color.WHITE, 3, 1.5f), rounded(Color.TRANSPARENT, 0xFF2A1458, 1, 0)});
        frame.setLayerInset(1, dp(3), dp(3), dp(3), dp(3));
        return frame;
    }

    private GradientDrawable wiiEdge() {
        return rounded(Color.TRANSPARENT, WiiSkin.TILE_RIM, 1.5f, cornerDp() - .75f);
    }

    private GradientDrawable tileBackground() {
        return rounded(wii ? Color.WHITE : 0xFF2E1A52, 0, 0, cornerDp());
    }

    private Drawable emptyChannel() {
        return WiiSkin.emptyChannel(activity.getResources());
    }

    private Cell newCell(int w, int h) {
        FrameLayout card = new FrameLayout(activity);
        card.setClickable(true);
        final int radius = dp(cornerDp());
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                // The originals have no drop shadow; Z only lifts the focused tile over its neighbours.
                outline.setAlpha(0f);
            }
        });
        card.setClipToOutline(true);
        ImageView art = new ImageView(activity);
        card.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView name = new TextView(activity);
        name.setTextColor(wii ? WII_TEXT : Color.WHITE);
        name.setTextSize(15);
        name.setTypeface(medium);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        nameLp.bottomMargin = h / 10;
        card.addView(name, nameLp);
        Cell cell = new Cell(card, art, name);
        card.setOnClickListener(v -> open(cell));
        final float grown = classic ? 1.1f : 1.04f;
        card.setOnFocusChangeListener((v, focused) -> {
            // Also on a cell a page turn has just emptied: it was focused, and must not stay enlarged.
            Drawable ring = focused && (cell.entry != null || cell.action != null) ? ring() : null;
            v.setForeground(ring != null ? ring : wii ? wiiEdge() : null);
            if (wii) {
                stopPulse();
                if (ring != null && animate) startPulse(ring);
            }
            v.setTranslationZ(focused ? dp(6) : 0);
            float scale = focused ? grown : 1f;
            if (animate) v.animate().scaleX(scale).scaleY(scale).setDuration(130).start();
            else {
                v.setScaleX(scale);
                v.setScaleY(scale);
            }
        });
        return cell;
    }

    /** The selected channel's ring breathes while the channel stays selected. */
    private void startPulse(Drawable ring) {
        pulse = ObjectAnimator.ofInt(ring, "alpha", 255, 130);
        pulse.setDuration(650);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) pulse.cancel();
        pulse = null;
    }

    private void click(View view, Runnable action) {
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnClickListener(v -> {
            play(StyleSounds.SELECT);
            action.run();
        });
    }

    // ---- Wii --------------------------------------------------------------------------------------

    private void buildWii(Config cfg) {
        root.setBackground(new WiiSkin.Backdrop());
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFFEBEBEB));
        cellWidth = Math.round(width * WiiSkin.TILE_W);
        cellHeight = Math.round(height * WiiSkin.TILE_H);
        wiiLayer = new FrameLayout(activity);
        wiiLayer.setClipChildren(false);
        root.addView(wiiLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int slot = 0; slot < WII_PAGE; slot++) {
            Cell cell = newCell(cellWidth, cellHeight);
            cells.add(cell);
            wiiLayer.addView(cell.view, at(cellWidth, cellHeight,
                    wiiX(slot % WII_COLUMNS), wiiY(slot / WII_COLUMNS)));
            bind(cell, null);
        }
        // The neighbouring pages show a column at the screen's edges.
        for (int row = 0; row < WII_PAGE / WII_COLUMNS; row++) {
            peekPrevious[row] = peekCell(-1, row);
            peekNext[row] = peekCell(WII_COLUMNS, row);
        }

        int barTop = Math.round(height * WiiSkin.BAR_TOP);
        root.addView(new WiiSkin.Bar(activity, width, height, ClockFormat.is24(activity, cfg.clockFormat), medium),
                at(width, height - barTop, 0, barTop));

        arrowLeft = wiiArrow(false, WiiSkin.ARROW_LEFT);
        arrowRight = wiiArrow(true, WiiSkin.ARROW_RIGHT);

        View inputs = wiiDisc(WiiSkin.LEFT_CX, activity.getString(R.string.inputs), null, activity.getString(R.string.inputs));
        inputs.setId(R.id.btnInputs);
        click(inputs, activity::styledInputs);
        View settings = wiiDisc(WiiSkin.RIGHT_CX, null, activity.getDrawable(R.drawable.ic_settings),
                activity.getString(R.string.settings));
        click(settings, activity::styledLauncherSettings);
        buttons.add(inputs);
        buttons.add(settings);
    }

    private int wiiX(int column) {
        return Math.round(width * (WiiSkin.TILE_LEFT + column * WiiSkin.PITCH_X));
    }

    private int wiiY(int row) {
        return Math.round(height * (WiiSkin.TILE_TOP + row * WiiSkin.PITCH_Y));
    }

    private Cell peekCell(int column, int row) {
        Cell cell = newCell(cellWidth, cellHeight);
        cell.view.setClickable(false);
        wiiLayer.addView(cell.view, at(cellWidth, cellHeight, wiiX(column), wiiY(row)));
        return cell;
    }

    private View wiiArrow(boolean right, float left) {
        WiiSkin.Arrow arrow = new WiiSkin.Arrow(activity, right);
        int w = Math.round(width * WiiSkin.ARROW_W), h = Math.round(height * WiiSkin.ARROW_H);
        arrow.setVisibility(View.INVISIBLE);
        root.addView(arrow, at(w, h, Math.round(width * left), Math.round(height * WiiSkin.ARROW_CY) - h / 2));
        return arrow;
    }

    private View wiiDisc(float centerX, String text, Drawable icon, String description) {
        float radius = width * WiiSkin.DISC / 2;
        int box = Math.round(radius * 2.5f);
        WiiSkin.Disc disc = new WiiSkin.Disc(activity, radius, text, icon, medium);
        disc.setContentDescription(description);
        disc.setOnFocusChangeListener((v, focused) -> {
            float scale = focused ? 1.06f : 1f;
            if (animate) v.animate().scaleX(scale).scaleY(scale).setDuration(130).start();
            else {
                v.setScaleX(scale);
                v.setScaleY(scale);
            }
            v.invalidate();
        });
        root.addView(disc, at(box, box, Math.round(width * centerX) - box / 2, Math.round(height * WiiSkin.DISC_CY) - box / 2));
        return disc;
    }

    // ---- both Roku homes ---------------------------------------------------------------------------

    /** Positions below are in pixels of a 1920x1080 capture of the original; these scale them to this screen. */
    private int px(float value) {
        return Math.round(value * width / 1920f);
    }

    private int py(float value) {
        return Math.round(value * height / 1080f);
    }

    private TextView text(String value, float heightPx, int color, Typeface face, int gravity) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(heightPx));
        view.setTextColor(color);
        view.setTypeface(face);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setGravity(gravity);
        return view;
    }

    private void buildScroller(int left, int top, int w, int h, int paddingTop) {
        rokuScroll = new ScrollView(activity);
        rokuScroll.setClipToPadding(false);
        rokuScroll.setClipChildren(false);
        rokuScroll.setVerticalScrollBarEnabled(false);
        rokuScroll.setSmoothScrollingEnabled(animate);
        rokuScroll.setFocusable(false);
        rokuScroll.setPadding(0, paddingTop, 0, py(30));
        rokuGrid = new FrameLayout(activity);
        rokuGrid.setClipChildren(false);
        rokuScroll.addView(rokuGrid, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(rokuScroll, at(w, h, left, top));
    }

    private void brand(String title, int titleLeft) {
        root.addView(text("TV", 50, Color.WHITE, medium, Gravity.CENTER_VERTICAL), at(px(90), py(70), px(100), py(50)));
        rokuTitle = text("•   " + title, 46, Color.WHITE, light, Gravity.CENTER_VERTICAL);
        root.addView(rokuTitle, at(px(420), py(70), titleLeft, py(50)));
    }

    /** Keeps a tile, and the room its focus frame grows into, inside the list's window. */
    private void keepVisible(Cell cell) {
        int window = rokuScroll.getHeight();
        if (window <= 0) return;
        int margin = py(16);
        int top = rokuScroll.getPaddingTop() + cell.y;
        int y = rokuScroll.getScrollY();
        // The top block always returns the list to its very start, so the first rows sit where they began.
        if (cell.y == 0 || cell.section == 0 && modern) y = 0;
        else if (top - margin < y) y = top - margin;
        else if (top + cell.h + margin > y + window) y = top + cell.h + margin - window;
        y = Math.max(0, y);
        if (animate) rokuScroll.smoothScrollTo(0, y);
        else rokuScroll.scrollTo(0, y);
    }

    private void place(Cell cell, int x, int y, int w, int h) {
        cell.x = x;
        cell.y = y;
        cell.w = w;
        cell.h = h;
        cell.view.setLayoutParams(at(w, h, x, y));
    }

    /**
     * A tile's art fills the tile, cropped at the sides, on a background of the banner's own edge colours. A banner
     * with its logo in the part that would be cropped stays whole, carried on past its edges, with no join either.
     */
    private void showArt(ImageView art, View back, AppAdapter.Entry entry) {
        if (entry.banner == null) {
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageDrawable(entry.icon);
            back.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF3A2466, 0xFF21123F}));
            return;
        }
        RokuSkin.Edges edges = edgeCache.get(entry.pkg);
        if (edges == null) {
            edges = RokuSkin.edges(entry.banner);
            edgeCache.put(entry.pkg, edges);
        }
        if (!edges.safeToCrop) {
            showExtended(art, back, entry);
            return;
        }
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageDrawable(entry.banner);
        back.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{edges.top, edges.bottom}));
    }

    /** A shape too tall to crop into: the banner whole, carried on past its edges (RokuSkin.ExtendedBanner). */
    private void showExtended(ImageView art, View back, AppAdapter.Entry entry) {
        if (entry.banner == null) {
            showArt(art, back, entry);
            return;
        }
        android.graphics.Bitmap bitmap = bannerCache.get(entry.pkg);
        if (bitmap == null) {
            bitmap = RokuSkin.bannerBitmap(entry.banner);
            bannerCache.put(entry.pkg, bitmap);
        }
        art.setScaleType(ImageView.ScaleType.FIT_XY);
        art.setImageDrawable(new RokuSkin.ExtendedBanner(bitmap));
        back.setBackground(null);
    }

    private void updateSidePanel(Cell cell) {
        if (spot == null || cell.entry == null) return;
        showExtended(spotArt, spot, cell.entry);
        // A banner already says what the app is; only an app with a bare icon needs its name written out.
        spotName.setText(cell.entry.banner == null ? cell.entry.label : "");
    }

    private void buildSidePanel(int x, int y, int w, int h) {
        spot = new FrameLayout(activity);
        spotArt = new ImageView(activity);
        spot.addView(spotArt, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        spotName = text("", 44, Color.WHITE, medium, Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.bottomMargin = py(56);
        spot.addView(spotName, lp);
        root.addView(spot, at(w, h, x, y));
    }

    // ---- Metro ----------------------------------------------------------------------------------------

    /** One group, six columns by four rows with no hole left: the sizes of the Windows 8 screens, mixed. */
    private static final int[][] METRO_GROUP = {{2, 2}, {2, 1}, {2, 1}, {2, 1}, {1, 1}, {1, 1}, {1, 1}, {1, 1}, {2, 1},
            {2, 2}, {1, 1}, {1, 1}, {1, 1}, {1, 1}};

    private void buildMetro() {
        root.setBackground(new MetroSkin.Backdrop());
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(MetroSkin.BACKGROUND));
        Typeface thin = activity.getResources().getFont(R.font.selawik_light);
        metroLabelFace = activity.getResources().getFont(R.font.selawik_regular);
        root.addView(text(activity.getString(R.string.style_home), 78, Color.WHITE, thin, Gravity.CENTER_VERTICAL),
                at(px(800), py(110), px(MetroSkin.LEFT), py(57)));
        TextClock time = new TextClock(activity);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("H:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(38));
        time.setTextColor(Color.WHITE);
        time.setTypeface(thin);
        time.setSingleLine(true);
        time.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(time, at(px(500), py(70), px(1355), py(77)));

        metroScroll = new android.widget.HorizontalScrollView(activity);
        metroScroll.setClipChildren(false);
        metroScroll.setClipToPadding(false);
        metroScroll.setHorizontalScrollBarEnabled(false);
        metroScroll.setSmoothScrollingEnabled(animate);
        metroScroll.setFocusable(false);
        metroGrid = new FrameLayout(activity);
        metroGrid.setClipChildren(false);
        metroScroll.addView(metroGrid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(metroScroll, at(width, height - py(200), 0, py(200)));
    }

    private void fillMetro() {
        metroGrid.removeAllViews();
        cells.clear();
        // Inputs and the settings stay in the first screenful, between the apps, as plain coloured tiles.
        int apps = entries.size(), total = apps + 3, utilitiesAt = Math.min(3, apps);
        int[][] spans = new int[total][];
        for (int i = 0; i < total; i++) {
            // The Inputs tile and the two settings tiles take the group's 2x1 and two squares in the 4th to 6th places.
            spans[i] = METRO_GROUP[i % METRO_GROUP.length];
        }
        int[][] places = StyleNav.pack(spans, MetroSkin.ROWS, MetroSkin.GROUP_COLUMNS);
        int app = 0, rightEdge = 0, groups = 0;
        for (int i = 0; i < total; i++) {
            int column = places[i][0], row = places[i][1];
            int group = column / MetroSkin.GROUP_COLUMNS;
            if (group >= groups) {
                // A small heading above each group, as the screens have.
                groups = group + 1;
                TextView heading = text(activity.getString(group == 0 ? R.string.style_group_main : R.string.style_group_apps),
                        28, Color.WHITE, metroLabelFace, Gravity.CENTER_VERTICAL);
                metroGrid.addView(heading, at(px(500), py(40), px(MetroSkin.LEFT + column * MetroSkin.PITCH + group * MetroSkin.GROUP_GAP),
                        py(MetroSkin.TOP - 46) - py(200)));
            }
            int w = px(spans[i][0] * MetroSkin.PITCH - (MetroSkin.PITCH - MetroSkin.UNIT));
            int h = py(spans[i][1] * MetroSkin.PITCH - (MetroSkin.PITCH - MetroSkin.UNIT));
            int x = px(MetroSkin.LEFT + column * MetroSkin.PITCH + group * MetroSkin.GROUP_GAP);
            int y = py(MetroSkin.TOP + row * MetroSkin.PITCH) - py(200);
            Cell cell = newCell(w, h);
            place(cell, x, y, w, h);
            cells.add(cell);
            metroGrid.addView(cell.view);
            rightEdge = Math.max(rightEdge, x + w);
            metroLabel(cell, h);
            if (i >= utilitiesAt && i < utilitiesAt + 3) bindMetroUtility(cell, i - utilitiesAt);
            else bindMetroApp(cell, entries.get(app++), i);
            if (animate && !metroEntered) {
                // Windows 8's entrance: the tiles slide in from the right, column by column.
                cell.view.setTranslationX(px(180));
                cell.view.setAlpha(0f);
                cell.view.animate().translationX(0).alpha(1f).setStartDelay(column * 22L).setDuration(320)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            }
        }
        metroEntered = true;
        View end = new View(activity);
        metroGrid.addView(end, at(px(MetroSkin.LEFT), 1, rightEdge, 0));
    }

    private void metroLabel(Cell cell, int h) {
        cell.name.setGravity(Gravity.START | Gravity.BOTTOM);
        cell.name.setTypeface(metroLabelFace);
        cell.name.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(20));
        cell.name.setPadding(px(13), 0, px(10), py(9));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cell.name.getLayoutParams();
        lp.bottomMargin = 0;
        cell.name.setLayoutParams(lp);
        cell.name.setVisibility(View.VISIBLE);
    }

    private void bindMetroUtility(Cell cell, int which) {
        int[] icons = {R.drawable.ic_input, R.drawable.ic_settings, R.drawable.ic_livetv};
        int[] labels = {R.string.inputs, R.string.settings, R.string.style_tv_settings};
        Runnable[] actions = {activity::styledInputs, activity::styledLauncherSettings, activity::styledTvSettings};
        cell.action = actions[which];
        cell.key = "utility:" + which;
        cell.view.setBackground(metroTint(MetroSkin.COLORS[which]));
        cell.art.setImageResource(icons[which]);
        cell.art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = py(44);
        cell.art.setPadding(pad, pad, pad, pad);
        cell.name.setText(labels[which]);
        cell.name.setTextColor(Color.WHITE);
        cell.view.setFocusable(true);
        cell.view.setContentDescription(activity.getString(labels[which]));
        if (which == 0) cell.view.setId(R.id.btnInputs);
    }

    /** Flat, with the barely visible left-to-right lift the tiles have on the original. */
    private static Drawable metroTint(int color) {
        int lighter = android.graphics.Color.rgb(Math.min(255, Color.red(color) + 12),
                Math.min(255, Color.green(color) + 12), Math.min(255, Color.blue(color) + 12));
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{color, lighter});
    }

    /**
     * An app's own banner fills the tile. A wide tile is about the banner's shape; square ones would have to lose most
     * of the logo to a crop, so there the banner stays whole and carries on past its edges, with no join.
     */
    private void bindMetroApp(Cell cell, AppAdapter.Entry entry, int index) {
        cell.entry = entry;
        cell.key = entry.component.flattenToShortString();
        cell.view.setFocusable(true);
        cell.view.setContentDescription(entry.label);
        cell.name.setText(entry.label);
        if (entry.banner == null) {
            cell.view.setBackground(metroTint(MetroSkin.COLORS[index % MetroSkin.COLORS.length]));
            cell.art.setImageDrawable(entry.icon);
            cell.art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = cell.h / 4;
            cell.art.setPadding(pad, pad, pad, pad);
            cell.name.setTextColor(Color.WHITE);
            return;
        }
        RokuSkin.Edges edges = edgeCache.get(entry.pkg);
        if (edges == null) {
            edges = RokuSkin.edges(entry.banner);
            edgeCache.put(entry.pkg, edges);
        }
        boolean wide = (float) cell.w / cell.h > 1.6f;
        if (wide) {
            cell.art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cell.art.setImageDrawable(entry.banner);
            cell.view.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{edges.top, edges.bottom}));
        } else {
            showExtended(cell.art, cell.view, entry);
        }
        // A banner already carries the app's name, often along its bottom edge, where a label would land on top of it.
        cell.name.setVisibility(View.GONE);
    }

    private void keepVisibleSideways(Cell cell) {
        int window = metroScroll.getWidth();
        if (window <= 0) return;
        int x = metroScroll.getScrollX();
        int margin = px(MetroSkin.LEFT);
        if (cell.x - px(40) < x) x = cell.x <= margin ? 0 : cell.x - margin;
        else if (cell.x + cell.w + px(40) > x + window) x = cell.x + cell.w + margin - window;
        if (animate) metroScroll.smoothScrollTo(x, 0);
        else metroScroll.scrollTo(x, 0);
    }

    // ---- the 6-column grid -----------------------------------------------------------------------------

    private void buildGrid(Config cfg) {
        root.setBackgroundColor(Color.BLACK);
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        favorites = new AppFavorites(activity, cfg.dockApps);
        usage = new AppUsage(activity);
        // Measured on the approved mock-up, scaled from its 1470 pixels of width.
        int top = py(120), bottom = py(103);
        buildScroller(0, top, width, height - top - bottom, 0);
        rokuScroll.setPadding(0, 0, 0, 0);
        View topBar = new View(activity);
        topBar.setBackgroundColor(GRID_BAR);
        root.addView(topBar, at(width, top, 0, 0));
        View bottomBar = new View(activity);
        bottomBar.setBackgroundColor(GRID_BAR);
        root.addView(bottomBar, at(width, bottom, 0, height - bottom));

        // The launcher's own mark: a yellow TV on black, with the style's name beside it.
        ImageView circle = new ImageView(activity);
        circle.setImageResource(R.drawable.ic_livetv);
        circle.setColorFilter(GRID_YELLOW);
        circle.setPadding(py(13), py(13), py(13), py(13));
        gridFocusLook(circle, Color.BLACK, 70);
        root.addView(circle, at(py(70), py(70), px(47), py(25)));
        click(circle, () -> activity.styledQuickMenu(circle));
        circle.setContentDescription(activity.getString(R.string.settings));

        gridSearch = gridChip(R.drawable.ic_search, activity.getString(R.string.style_search_hint));
        root.addView(gridSearch, at(px(1173), py(68), px(300), py(26)));
        root.addView(text(activity.getString(R.string.style_grid_name), 38, Color.WHITE,
                Typeface.create(medium, Typeface.BOLD), Gravity.CENTER_VERTICAL), at(px(170), py(70), px(128), py(25)));
        click(gridSearch, this::askGridSearch);

        gridOnline = text("", 28, GRID_YELLOW, medium, Gravity.CENTER);
        root.addView(gridOnline, at(px(146), py(50), px(1497), py(35)));
        click(gridOnline, activity::styledNetwork);
        gridOnline.setOnFocusChangeListener((v, focused) -> paintOnlinePill());

        ImageView gear = new ImageView(activity);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setPadding(py(12), py(12), py(12), py(12));
        gear.setContentDescription(activity.getString(R.string.settings));
        gridFocusLook(gear, GRID_BAR, 60);
        root.addView(gear, at(py(60), py(60), px(1824), py(30)));
        click(gear, activity::styledLauncherSettings);

        TextClock time = new TextClock(activity);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("H:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(28));
        time.setTextColor(0xFFCCCCCC);
        time.setTypeface(regular);
        time.setSingleLine(true);
        time.setGravity(Gravity.CENTER);
        root.addView(time, at(px(170), py(50), px(1650), py(35)));

        buttons.add(circle);
        buttons.add(gridSearch);
        buttons.add(gridOnline);
        buttons.add(gear);

        int[] labels = {R.string.style_tab_apps, R.string.style_tab_recent, R.string.style_tab_favorites, R.string.inputs};
        for (int i = 0; i < labels.length; i++) {
            TextView tab = text(activity.getString(labels[i]), 27, 0xFF9A9A9A, regular, Gravity.CENTER);
            root.addView(tab, at(width / labels.length, bottom, i * width / labels.length, height - bottom));
            gridTabs.add(tab);
            buttons.add(tab);
            final int which = i;
            if (i == 3) {
                tab.setId(R.id.btnInputs);
                click(tab, activity::styledInputs);
            } else {
                tab.setFocusable(true);
                tab.setClickable(true);
                tab.setOnClickListener(v -> selectGridTab(which));
            }
            tab.setOnFocusChangeListener((v, focused) -> {
                tab.setBackground(focused ? rounded(Color.TRANSPARENT, GRID_YELLOW, 2, 0) : null);
            });
        }
        gridEmpty = text("", 30, 0xFF9A9A9A, regular, Gravity.CENTER);
        gridEmpty.setVisibility(View.GONE);
        root.addView(gridEmpty, at(width, py(60), 0, height / 2 - py(30)));
        paintGridTabs();
    }

    private void gridFocusLook(View view, int fill, int sizePx) {
        float radius = py(sizePx) / 2f / density;
        view.setBackground(rounded(fill, 0, 0, radius));
        view.setOnFocusChangeListener((v, focused) -> v.setBackground(
                focused ? rounded(fill, focusEdge(fill), 2.5f, radius) : rounded(fill, 0, 0, radius)));
    }

    /** The yellow circle takes a white edge: yellow would not show on it. */
    private static int focusEdge(int fill) {
        return fill == GRID_YELLOW ? Color.WHITE : GRID_YELLOW;
    }

    private TextView gridChip(int icon, String label) {
        TextView chip = text(label, 28, 0xFF8F8F8F, regular, Gravity.CENTER_VERTICAL);
        chip.setPadding(px(30), 0, px(18), 0);
        gridFocusLook(chip, 0xFF212121, 68);
        return chip;
    }

    private void updateOnlinePill() {
        android.net.ConnectivityManager manager = activity.getSystemService(android.net.ConnectivityManager.class);
        gridIsOnline = manager != null && manager.getActiveNetwork() != null;
        gridOnline.setText(gridIsOnline ? R.string.style_online : R.string.style_offline);
        paintOnlinePill();
    }

    private void paintOnlinePill() {
        int color = gridIsOnline ? GRID_YELLOW : 0xFF8F8F8F;
        boolean focused = gridOnline.isFocused();
        gridOnline.setTextColor(focused ? Color.BLACK : color);
        gridOnline.setBackground(rounded(focused ? color : Color.TRANSPARENT, color, 1, py(25) / density));
    }

    private void askGridSearch() {
        android.widget.EditText field = new android.widget.EditText(activity);
        field.setSingleLine(true);
        field.setText(gridFilter);
        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.style_search)
                .setView(field)
                .setPositiveButton(android.R.string.ok, (d, w) -> applyGridFilter(field.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, (d, w) -> applyGridFilter(""))
                .show();
    }

    private void applyGridFilter(String filter) {
        gridFilter = filter;
        gridSearch.setText(filter.isEmpty() ? activity.getString(R.string.style_search_hint) : filter);
        focus = 0;
        showGridView();
        buttonFocus = entries.isEmpty() ? GRID_SEARCH : -1;
        root.post(this::restoreFocus);
    }

    private void selectGridTab(int tab) {
        if (tab == gridTab && tab != 1) return;
        play(StyleSounds.REFRESH);
        gridTab = tab;
        focus = 0;
        focusKey = null;
        paintGridTabs();
        showGridView();
    }

    private void paintGridTabs() {
        int[] icons = {R.drawable.ic_grid, R.drawable.ic_recent, R.drawable.ic_star, R.drawable.ic_input};
        int[] labels = {R.string.style_tab_apps, R.string.style_tab_recent, R.string.style_tab_favorites, R.string.inputs};
        for (int i = 0; i < gridTabs.size(); i++) {
            int color = i == gridTab ? GRID_YELLOW : 0xFF9A9A9A;
            TextView tab = gridTabs.get(i);
            tab.setTextColor(color);
            tab.setTypeface(i == gridTab ? Typeface.create(medium, Typeface.BOLD) : regular);
            Drawable icon = activity.getDrawable(icons[i]).mutate();
            icon.setBounds(0, 0, py(30), py(30));
            icon.setTint(color);
            android.text.SpannableString text = new android.text.SpannableString("  " + activity.getString(labels[i]));
            text.setSpan(new android.text.style.ImageSpan(icon, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1, 0);
            tab.setText(text);
        }
    }

    /** Every app, the ones used so far with the latest first, or the favourites; narrowed by the search. */
    private void showGridView() {
        usage = new AppUsage(activity);
        entries.clear();
        Locale locale = Locale.getDefault();
        String needle = gridFilter.toLowerCase(locale);
        for (AppAdapter.Entry entry : allEntries) {
            if (!needle.isEmpty() && !entry.label.toLowerCase(locale).contains(needle)) continue;
            if (gridTab == 1 && usage.lastUsed(entry.pkg) <= 0) continue;
            if (gridTab == 2 && !favorites.has(entry.pkg)) continue;
            entries.add(entry);
        }
        if (gridTab == 1) {
            Collections.sort(entries, (a, b) -> Long.compare(usage.lastUsed(b.pkg), usage.lastUsed(a.pkg)));
        }
        rokuGrid.removeAllViews();
        cells.clear();
        int pitch = width / GRID_COLUMNS;
        int side = pitch - Math.max(1, px(2));
        for (int i = 0; i < entries.size(); i++) {
            Cell cell = gridCell(side);
            place(cell, i % GRID_COLUMNS * pitch + 1, i / GRID_COLUMNS * pitch, side, side);
            // The tiles at the edges grow inwards, so their whole yellow frame stays on screen.
            int column = i % GRID_COLUMNS;
            cell.view.setPivotX(column == 0 ? 0 : column == GRID_COLUMNS - 1 ? side : side / 2f);
            cell.view.setPivotY(i < GRID_COLUMNS ? 0 : side / 2f);
            cells.add(cell);
            rokuGrid.addView(cell.view);
            bindGrid(cell, entries.get(i));
        }
        rokuScroll.scrollTo(0, 0);
        boolean empty = entries.isEmpty() && !allEntries.isEmpty();
        gridEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) gridEmpty.setText(gridTab == 2 ? R.string.style_empty_favorites : R.string.style_empty_recent);
    }

    /** A square tile: the banner, a dark gradient rising from below, the name, when it was last used, the marks. */
    private Cell gridCell(int side) {
        Cell cell = newCell(side, side);
        View shade = new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xE6000000}));
        cell.view.addView(shade, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, side * 11 / 20, Gravity.BOTTOM));
        cell.name.setGravity(Gravity.START);
        cell.name.setTypeface(Typeface.create(medium, Typeface.BOLD));
        cell.name.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(27));
        cell.name.setTextColor(Color.WHITE);
        cell.name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams nameLp = (FrameLayout.LayoutParams) cell.name.getLayoutParams();
        nameLp.gravity = Gravity.BOTTOM;
        nameLp.bottomMargin = py(40);
        nameLp.leftMargin = px(14);
        nameLp.rightMargin = px(10);
        cell.name.setLayoutParams(nameLp);
        cell.sub = text("", 22, 0xFFD0D0D0, regular, Gravity.START);
        FrameLayout.LayoutParams subLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        subLp.bottomMargin = py(12);
        subLp.leftMargin = px(14);
        cell.view.addView(cell.sub, subLp);
        cell.dot = new View(activity);
        GradientDrawable green = new GradientDrawable();
        green.setShape(GradientDrawable.OVAL);
        green.setColor(0xFF2ED573);
        cell.dot.setBackground(green);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(py(14), py(14), Gravity.BOTTOM);
        dotLp.leftMargin = px(14);
        dotLp.bottomMargin = py(50);
        cell.view.addView(cell.dot, dotLp);
        cell.star = text("\u2605", 30, GRID_YELLOW, regular, Gravity.CENTER);
        FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        starLp.topMargin = py(10);
        starLp.rightMargin = px(12);
        cell.view.addView(cell.star, starLp);
        cell.view.setOnLongClickListener(v -> {
            toggleFavorite(cell);
            return true;
        });
        return cell;
    }

    private void bindGrid(Cell cell, AppAdapter.Entry entry) {
        cell.entry = entry;
        cell.key = entry.component.flattenToShortString();
        cell.view.setFocusable(true);
        cell.view.setContentDescription(entry.label);
        if (entry.banner != null) {
            // The whole banner, centred across the width, never stretched or cropped, on a blur of itself.
            Drawable blur = blurCache.get(entry.pkg);
            if (blur == null) {
                blur = RokuSkin.blurred(activity.getResources(), entry.banner);
                blurCache.put(entry.pkg, blur);
            }
            cell.view.setBackground(blur.getConstantState().newDrawable().mutate());
            cell.art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.art.setImageDrawable(entry.banner);
        } else {
            cell.view.setBackgroundColor(0xFF232323);
            cell.art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.art.setImageDrawable(entry.icon);
            cell.art.setPadding(cell.w / 4, cell.w / 6, cell.w / 4, cell.w / 3);
        }
        cell.name.setText(entry.label);
        cell.name.setVisibility(View.VISIBLE);
        showGridMarks(cell);
    }

    private void showGridMarks(Cell cell) {
        long now = System.currentTimeMillis();
        long then = usage.lastUsed(cell.entry.pkg);
        String ago = AppUsage.ago(now, then);
        cell.sub.setText(ago == null ? activity.getString(R.string.style_never) : ago);
        boolean recent = AppUsage.recent(now, then);
        cell.dot.setVisibility(recent ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cell.name.getLayoutParams();
        lp.leftMargin = recent ? px(36) : px(14);
        cell.name.setLayoutParams(lp);
        cell.star.setVisibility(favorites.has(cell.entry.pkg) ? View.VISIBLE : View.GONE);
    }

    /** Back on Home: an app may just have been used, so the times and the order of Recent may have changed. */
    private void refreshUsage() {
        if (gridTab == 1) {
            showGridView();
            return;
        }
        usage = new AppUsage(activity);
        for (Cell cell : cells) if (cell.entry != null) showGridMarks(cell);
    }

    private void toggleFavorite(Cell cell) {
        if (cell.entry == null) return;
        boolean added = favorites.toggle(cell.entry.pkg);
        play(StyleSounds.SELECT);
        android.widget.Toast.makeText(activity, activity.getString(
                added ? R.string.style_favorite_added : R.string.style_favorite_removed, cell.entry.label),
                android.widget.Toast.LENGTH_SHORT).show();
        if (gridTab == 2 && !added) {
            int keep = focus;
            showGridView();
            focus = Math.max(0, Math.min(keep, entries.size() - 1));
            focusKey = null;
            root.post(this::restoreFocus);
        } else {
            showGridMarks(cell);
        }
    }

    // ---- classic Roku ---------------------------------------------------------------------------------

    /**
     * Two states, as on the original. Menu open: the menu on the left and three columns of large tiles. Focus on an
     * app: the menu folds away behind a chevron, the tiles shrink to four columns and a panel opens on the right.
     */
    private void buildClassic(Config cfg) {
        root.setBackground(new RokuSkin.ClassicBackdrop());
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF160F22));
        // The list stops under the line with the app's name and the count; the first row starts at 240 as measured.
        int clipTop = py(228);
        buildScroller(0, clipTop, width, height - clipTop, py(240) - clipTop);

        brand(activity.getString(R.string.style_home), px(186));
        classicClock = new TextClock(activity);
        classicClock.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(32));
        classicClock.setTextColor(Color.WHITE);
        classicClock.setTypeface(light);
        classicClock.setSingleLine(true);
        classicClock.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(classicClock, at(px(700), py(70), px(1120), py(50)));
        is24 = ClockFormat.is24(activity, cfg.clockFormat);

        rokuLabel = text("", 28, Color.WHITE, light, Gravity.CENTER_VERTICAL);
        root.addView(rokuLabel, at(px(640), py(44), px(192), py(182)));
        rokuCount = text("", 28, Color.WHITE, light, Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(rokuCount, at(px(400), py(44), px(706), py(182)));

        buildSidePanel(px(1216), py(240), px(525), py(735));

        classicFade = new View(activity);
        classicFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00120C1B, 0xB0120C1B}));
        root.addView(classicFade, at(px(1160), py(130), px(760), height - py(130)));

        chevronLeft = new RokuSkin.Chevron(activity, false);
        root.addView(chevronLeft, at(px(32), py(58), px(102), py(250)));
        chevronRight = new RokuSkin.Chevron(activity, true);
        root.addView(chevronRight, at(px(26), py(50), px(1792), py(248)));

        classicMenu = new FrameLayout(activity);
        root.addView(classicMenu, new FrameLayout.LayoutParams(px(760), height));
        click(classicRow(0, R.string.style_home), () -> {
            focus = 0;
            rokuScroll.scrollTo(0, 0);
            setMenuOpen(false);
            if (!entries.isEmpty()) focusTile(0);
        });
        View inputs = classicRow(1, R.string.inputs);
        inputs.setId(R.id.btnInputs);
        click(inputs, activity::styledInputs);
        click(classicRow(2, R.string.settings), activity::styledLauncherSettings);
        click(classicRow(3, R.string.style_tv_settings), activity::styledTvSettings);
        menuOpen = true;
        buttonFocus = 0;
    }

    private View classicRow(int index, int label) {
        TextView row = text(activity.getString(label), 34, Color.WHITE, regular, Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(px(32), 0, px(16), 0);
        row.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                GradientDrawable pill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0xFFE3E3E3, 0xFFCFCFCF});
                pill.setCornerRadius(dp(2));
                pill.setStroke(dp(1), 0xFF9A9A9A);
                row.setBackground(pill);
            } else {
                row.setBackground(null);
            }
            row.setTextColor(focused ? 0xFF2A1458 : Color.WHITE);
            row.setTypeface(focused ? medium : regular);
        });
        classicMenu.addView(row, at(px(567), py(80), px(154), py(232) + index * py(72)));
        buttons.add(row);
        return row;
    }

    private void setMenuOpen(boolean open) {
        menuOpen = open;
        layoutClassic();
    }

    private void layoutClassic() {
        boolean open = menuOpen;
        classicMenu.setVisibility(open ? View.VISIBLE : View.GONE);
        chevronLeft.setVisibility(open ? View.GONE : View.VISIBLE);
        chevronRight.setVisibility(open ? View.VISIBLE : View.GONE);
        classicFade.setVisibility(open ? View.VISIBLE : View.GONE);
        spot.setVisibility(open ? View.GONE : View.VISIBLE);
        rokuLabel.setVisibility(open ? View.GONE : View.VISIBLE);
        rokuTitle.setVisibility(open ? View.GONE : View.VISIBLE);
        String time = is24 ? "H:mm" : "h:mm a";
        String pattern = open ? time + "   '|   " + activity.getString(R.string.style_options).replace("'", "''") + "  ✱'" : time;
        classicClock.setFormat12Hour(pattern);
        classicClock.setFormat24Hour(pattern);
        FrameLayout.LayoutParams clockLp = (FrameLayout.LayoutParams) classicClock.getLayoutParams();
        clockLp.leftMargin = (open ? px(1740) : px(1820)) - clockLp.width;
        classicClock.setLayoutParams(clockLp);
        FrameLayout.LayoutParams countLp = (FrameLayout.LayoutParams) rokuCount.getLayoutParams();
        countLp.leftMargin = (open ? px(1740) : px(1106)) - countLp.width;
        countLp.topMargin = open ? py(174) : py(182);
        rokuCount.setLayoutParams(countLp);
        int columns = open ? 3 : CLASSIC_COLUMNS;
        int x0 = open ? px(805) : px(192), y0 = 0;
        int w = open ? px(292) : px(212), h = open ? py(220) : py(159);
        int pitchX = open ? px(315) : px(236), pitchY = open ? py(242) : py(183);
        for (int i = 0; i < cells.size(); i++) {
            place(cells.get(i), x0 + i % columns * pitchX, y0 + i / columns * pitchY, w, h);
        }
        if (open) rokuCount.setText(activity.getString(R.string.style_apps_count, entries.size()));
    }

    // ---- new Roku -------------------------------------------------------------------------------------

    private void buildModern() {
        GradientDrawable page = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2C1065, 0xFF2C1065, 0xFF1D0C4C, 0xFF0B0724});
        root.setBackground(page);
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF1D0C4C));
        ImageView sky = new ImageView(activity);
        skyline = RokuSkin.skyline(width, height);
        sky.setImageBitmap(skyline);
        sky.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(sky, at(width, skyline.getHeight(), 0, 0));

        int clipTop = py(178);
        buildScroller(0, clipTop, px(1240), height - clipTop, 0);
        buildSidePanel(px(1294), py(234), px(524), py(734));
        spot.setAlpha(.55f);

        brand(activity.getString(R.string.style_home), px(186));
        TextClock time = new TextClock(activity);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("H:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, py(34));
        time.setTextColor(Color.WHITE);
        time.setTypeface(light);
        time.setSingleLine(true);
        time.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(time, at(px(400), py(70), px(1418), py(50)));

        railShade = new View(activity);
        railShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF6160A3C, 0xE0160A3C, 0x00160A3C}));
        railShade.setVisibility(View.GONE);
        root.addView(railShade, at(px(620), height, 0, 0));
        click(railRow(0, R.drawable.ic_home_outline, R.string.style_home), () -> {
            focus = 0;
            rokuScroll.scrollTo(0, 0);
            if (!entries.isEmpty()) focusTile(0);
        });
        View inputs = railRow(1, R.drawable.ic_input, R.string.inputs);
        inputs.setId(R.id.btnInputs);
        click(inputs, activity::styledInputs);
        click(railRow(2, R.drawable.ic_settings, R.string.settings), activity::styledLauncherSettings);
        click(railRow(3, R.drawable.ic_livetv, R.string.style_tv_settings), activity::styledTvSettings);
    }

    /** A rail row is only its icon until the rail takes the focus; then it unfolds with the label beside it. */
    private View railRow(int index, int icon, int label) {
        FrameLayout row = new FrameLayout(activity);
        ImageView image = new ImageView(activity);
        image.setImageResource(icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(px(48), py(48), Gravity.CENTER_VERTICAL);
        imageLp.leftMargin = px(13);
        row.addView(image, imageLp);
        TextView name = text(activity.getString(label), 30, Color.WHITE, regular, Gravity.CENTER_VERTICAL);
        name.setVisibility(View.GONE);
        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        nameLp.leftMargin = px(84);
        row.addView(name, nameLp);
        railNames.add(name);
        row.setOnFocusChangeListener((v, focused) -> {
            row.setBackground(focused ? rounded(Color.WHITE, 0, 0, 30) : null);
            name.setTextColor(focused ? 0xFF2A1458 : Color.WHITE);
            image.setColorFilter(focused ? 0xFF2A1458 : Color.WHITE);
        });
        image.setColorFilter(Color.WHITE);
        root.addView(row, at(px(74), py(68), px(84), py(236) + index * py(84)));
        buttons.add(row);
        return row;
    }

    private void setRailOpen(boolean open) {
        railShade.setVisibility(open ? View.VISIBLE : View.GONE);
        for (int i = 0; i < buttons.size(); i++) {
            View row = buttons.get(i);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) row.getLayoutParams();
            lp.width = open ? px(400) : px(74);
            row.setLayoutParams(lp);
            railNames.get(i).setVisibility(open ? View.VISIBLE : View.GONE);
        }
    }

    /** The section holding the focus stays bright; the other one, and the side panel, step back. */
    private void dimOthers() {
        int section = buttonFocus >= 0 || cells.isEmpty() ? -1 : cells.get(Math.min(focus, cells.size() - 1)).section;
        for (Cell cell : cells) cell.view.setAlpha(cell.section == section ? 1f : .5f);
        for (int i = 0; i < sectionLabels.length; i++) {
            if (sectionLabels[i] != null) sectionLabels[i].setAlpha(i == section ? 1f : .6f);
        }
    }

    private void fillModern() {
        rokuGrid.removeAllViews();
        cells.clear();
        int top = -py(178);
        int featured = Math.min(entries.size(), 5);
        sectionLabels[0] = modernLabel(R.string.style_for_you, py(182) + top);
        for (int i = 0; i < featured; i++) {
            Cell cell = newCell(px(316), py(178));
            cells.add(cell);
            cell.section = 0;
            if (i == 0) place(cell, px(234), py(234) + top, px(256), py(383));
            else place(cell, px(514) + (i - 1) % 2 * px(340), py(234) + (i - 1) / 2 * py(202) + top, px(316), py(178));
            rokuGrid.addView(cell.view);
            bind(cell, entries.get(i));
            if (i == 0) showExtended(cell.art, cell.view, entries.get(i));
        }
        sectionLabels[1] = null;
        if (entries.size() > featured) sectionLabels[1] = modernLabel(R.string.style_quick_access, py(665) + top);
        for (int i = featured; i < entries.size(); i++) {
            int n = i - featured;
            Cell cell = newCell(px(216), py(162));
            cells.add(cell);
            cell.section = 1;
            place(cell, px(234) + n % 4 * px(240), py(725) + n / 4 * py(186) + top, px(216), py(162));
            rokuGrid.addView(cell.view);
            bind(cell, entries.get(i));
        }
    }

    private TextView modernLabel(int label, int y) {
        TextView view = text(activity.getString(label), 27, Color.WHITE, light, Gravity.CENTER_VERTICAL);
        rokuGrid.addView(view, at(px(800), py(40), px(234), y));
        return view;
    }

    private void fillClassic() {
        rokuGrid.removeAllViews();
        cells.clear();
        for (AppAdapter.Entry entry : entries) {
            Cell cell = newCell(px(292), py(220));
            cells.add(cell);
            rokuGrid.addView(cell.view);
            bind(cell, entry);
        }
        layoutClassic();
    }
}
