package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.view.View;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The drawn parts of the Wii-style home. Every proportion below was measured on a 16:9 capture of the original menu
 * and is a share of the screen, so the layout holds at any resolution. Everything is drawn here: no artwork, logo or
 * font of the original is used.
 */
final class WiiSkin {
    private WiiSkin() {}

    static final float TILE_LEFT = .083f, TILE_TOP = .085f, TILE_W = .2035f, TILE_H = .193f;
    static final float PITCH_X = .2105f, PITCH_Y = .2095f;
    static final float BAR_TOP = .70f, LINE_Y = .725f, DIP_Y = .831f;
    static final float DISC = .089f, DISC_CY = .843f, LEFT_CX = .0905f, RIGHT_CX = .9095f;
    static final float ARROW_RIGHT = .935f, ARROW_LEFT = .035f, ARROW_W = .029f, ARROW_H = .09f, ARROW_CY = .386f;
    static final int LINE_BLUE = 0xFF35BEED, TILE_RIM = 0xFFB9B9B9, SELECT_BLUE = 0xFF34BEED, TEXT = 0xFF6F7074;

    private static Bitmap stripes(int first, int second, int rows) {
        Bitmap bitmap = Bitmap.createBitmap(1, rows * 2, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < rows * 2; y++) bitmap.setPixel(0, y, y < rows ? first : second);
        return bitmap;
    }

    /** An empty channel: the same fine horizontal lines as the background, a shade darker. */
    static Drawable emptyChannel(Resources res) {
        BitmapDrawable drawable = new BitmapDrawable(res, stripes(0xFFDADADA, 0xFFD2D2D2, 4));
        drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return drawable;
    }

    /** Off-white, brightest behind the channels, with fine horizontal lines over it. */
    static final class Backdrop extends Drawable {
        private final Paint glow = new Paint();
        private final Paint lines = new Paint();

        Backdrop() {
            lines.setShader(new BitmapShader(stripes(0x00000000, 0x0A000000, 4),
                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            glow.setShader(new RadialGradient(bounds.width() / 2f, bounds.height() * .45f, bounds.width() * .62f,
                    0xFFF6F6F6, 0xFFE8E8E8, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRect(getBounds(), glow);
            canvas.drawRect(getBounds(), lines);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** The bottom of the screen: the strip under the blue line, the seven-segment clock and the date. */
    static final class Bar extends View implements Runnable {
        private static final int[] DIGITS = {
                0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
                0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111};
        private final int screenW, screenH, top;
        private final boolean is24;
        private final Paint segment = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path[][] segments = new Path[4][7];
        private final RectF[] colon = new RectF[2];
        private Bitmap strip;
        private String dateText = "";
        private int dateDay = -1;

        Bar(Context context, int screenW, int screenH, boolean is24, Typeface medium) {
            super(context);
            this.screenW = screenW;
            this.screenH = screenH;
            this.is24 = is24;
            top = Math.round(screenH * BAR_TOP);
            segment.setColor(0xFFA2A3A6);
            small.setColor(0xFF85868A);
            small.setTypeface(medium);
            small.setTextSize(screenH * .034f);
            date.setColor(TEXT);
            date.setTypeface(medium);
            date.setTextSize(screenH * .054f);
            date.setTextAlign(Paint.Align.CENTER);
            buildClock();
        }

        private float y(float share) {
            return screenH * share - top;
        }

        /** Digit lefts, as measured: the two groups sit either side of the colon, on the middle of the screen. */
        private void buildClock() {
            float h = screenH * .074f, w = screenW * .0286f, t = screenW * .0046f, gap = t * .22f;
            float top = y(.731f);
            float[] lefts = {.4323f, .4641f, .5120f, .5432f};
            for (int d = 0; d < 4; d++) {
                float x = screenW * lefts[d];
                float mid = top + h / 2, bottom = top + h, right = x + w;
                segments[d][0] = bar(x + gap, top + t / 2, right - gap, top + t / 2, t);
                segments[d][1] = bar(right - t / 2, top + gap, right - t / 2, mid - gap, t);
                segments[d][2] = bar(right - t / 2, mid + gap, right - t / 2, bottom - gap, t);
                segments[d][3] = bar(x + gap, bottom - t / 2, right - gap, bottom - t / 2, t);
                segments[d][4] = bar(x + t / 2, mid + gap, x + t / 2, bottom - gap, t);
                segments[d][5] = bar(x + t / 2, top + gap, x + t / 2, mid - gap, t);
                segments[d][6] = bar(x + gap, mid, right - gap, mid, t);
            }
            float cx = screenW / 2f, dot = t * .46f;
            colon[0] = new RectF(cx - dot, top + h * .32f - dot, cx + dot, top + h * .32f + dot);
            colon[1] = new RectF(cx - dot, top + h * .68f - dot, cx + dot, top + h * .68f + dot);
        }

        /** One segment: a bar with pointed ends, so neighbours meet at a corner with a hair of space between. */
        private static Path bar(float x1, float y1, float x2, float y2, float t) {
            Path path = new Path();
            float half = t / 2;
            if (y1 == y2) {
                path.moveTo(x1, y1);
                path.lineTo(x1 + half, y1 - half);
                path.lineTo(x2 - half, y1 - half);
                path.lineTo(x2, y1);
                path.lineTo(x2 - half, y1 + half);
                path.lineTo(x1 + half, y1 + half);
            } else {
                path.moveTo(x1, y1);
                path.lineTo(x1 + half, y1 + half);
                path.lineTo(x1 + half, y2 - half);
                path.lineTo(x1, y2);
                path.lineTo(x1 - half, y2 - half);
                path.lineTo(x1 - half, y1 + half);
            }
            path.close();
            return path;
        }

        /** Flat, then an S down to the dip under the clock, flat under it, and the mirror image back up. */
        private Path edge(int w) {
            float flat = y(LINE_Y), dip = y(DIP_Y);
            Path edge = new Path();
            edge.moveTo(-4, flat);
            edge.lineTo(w * .17f, flat);
            edge.cubicTo(w * .235f, flat, w * .27f, dip, w * .355f, dip);
            edge.lineTo(w * .645f, dip);
            edge.cubicTo(w * .73f, dip, w * .765f, flat, w * .83f, flat);
            edge.lineTo(w + 4, flat);
            return edge;
        }

        /** Drawn once into a bitmap: the shadow takes many strokes, and it never changes. */
        private Bitmap paintStrip(int w, int h) {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Path edge = edge(w);
            Path body = new Path(edge);
            body.lineTo(w + 4, h + 4);
            body.lineTo(-4, h + 4);
            body.close();
            canvas.save();
            canvas.clipPath(body);
            Paint fill = new Paint();
            fill.setShader(new LinearGradient(0, y(LINE_Y), 0, h, new int[]{0xFFD3D4DA, 0xFFD4D6DC, 0xFFCED0D8},
                    new float[]{0f, .72f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, fill);

            // The shadow under the line: darkest at the line, gone a tenth of the screen below it. Blurred only here,
            // on this software canvas, where the blur works on every Android version.
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setStyle(Paint.Style.STROKE);
            shade.setStrokeWidth(screenH * .05f);
            shade.setColor(Color.argb(92, 0x30, 0x30, 0x40));
            shade.setMaskFilter(new BlurMaskFilter(screenH * .042f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawPath(edge, shade);

            // The pockets the two round buttons sit in: open towards the screen's edge.
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(screenH * .0028f);
            rim.setColor(0xFFE2E3E7);
            float pTop = y(.758f), pBottom = y(.956f), corner = (pBottom - pTop) / 2;
            canvas.drawRoundRect(-corner * 2, pTop, w * .151f, pBottom, corner, corner, rim);
            canvas.drawRoundRect(w * .849f, pTop, w + corner * 2, pBottom, corner, corner, rim);
            rim.setColor(0x24303040);
            canvas.drawRoundRect(-corner * 2, pTop + rim.getStrokeWidth(), w * .151f, pBottom + rim.getStrokeWidth(), corner, corner, rim);
            canvas.drawRoundRect(w * .849f, pTop + rim.getStrokeWidth(), w + corner * 2, pBottom + rim.getStrokeWidth(), corner, corner, rim);

            paintCard(canvas);
            canvas.restore();

            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(screenH * .0034f);
            line.setColor(LINE_BLUE);
            canvas.drawPath(edge, line);
            return bitmap;
        }

        /** A blank memory card, next to the left button. */
        private void paintCard(Canvas canvas) {
            float w = screenW * .038f, h = screenH * .09f;
            float x = screenW * .2075f - w / 2, yTop = y(.88f) - h / 2, cut = w * .24f;
            Path card = new Path();
            card.moveTo(x, yTop);
            card.lineTo(x + w - cut, yTop);
            card.lineTo(x + w, yTop + cut);
            card.lineTo(x + w, yTop + h);
            card.lineTo(x, yTop + h);
            card.close();
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFFF3F3F4);
            canvas.drawPath(card, paint);
            paint.setColor(0xFFA9AAAE);
            canvas.drawRect(x, yTop + h * .8f, x + w, yTop + h, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(screenH * .0028f);
            paint.setColor(0xFF8E8F93);
            canvas.drawPath(card, paint);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            if (w > 0 && h > 0) strip = paintStrip(w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (strip != null) canvas.drawBitmap(strip, 0, 0, null);
            Calendar now = Calendar.getInstance();
            int hour = now.get(is24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
            if (!is24 && hour == 0) hour = 12;
            int minute = now.get(Calendar.MINUTE);
            // A leading zero is left dark on the 12-hour clock, as on a real segment display.
            if (is24 || hour >= 10) digit(canvas, 0, hour / 10);
            digit(canvas, 1, hour % 10);
            digit(canvas, 2, minute / 10);
            digit(canvas, 3, minute % 10);
            if (now.get(Calendar.SECOND) % 2 == 0) {
                canvas.drawRect(colon[0], segment);
                canvas.drawRect(colon[1], segment);
            }
            if (!is24) {
                String mark = DateFormatSymbols.getInstance().getAmPmStrings()[now.get(Calendar.AM_PM)];
                canvas.drawText(mark, screenW * .594f, y(.805f), small);
            }
            int day = now.get(Calendar.DAY_OF_YEAR);
            if (day != dateDay) {
                dateDay = day;
                dateText = dateText(now.getTime());
            }
            canvas.drawText(dateText, screenW / 2f, y(.905f), date);
        }

        private void digit(Canvas canvas, int position, int value) {
            int bits = DIGITS[value];
            for (int s = 0; s < 7; s++) {
                if ((bits >> s & 1) != 0) canvas.drawPath(segments[position][s], segment);
            }
        }

        /** "Sáb 31/10": the day's short name, then day and month in the order this language writes them. */
        static String dateText(Date date) {
            Locale locale = Locale.getDefault();
            String name = new SimpleDateFormat("EEE", locale).format(date).replace(".", "");
            if (!name.isEmpty()) name = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
            return name + " " + new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dM"), locale).format(date);
        }

        @Override
        public void run() {
            invalidate();
            postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            removeCallbacks(this);
            if (visibility == VISIBLE) run();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(this);
            if (strip != null) strip.recycle();
            strip = null;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (strip == null && getWidth() > 0) strip = paintStrip(getWidth(), getHeight());
        }
    }

    /** A round button: a soft halo, a bevelled disc lit from the top left, a thin blue ring. */
    static final class Disc extends View {
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String text;
        private final Drawable icon;
        private final float radius;

        Disc(Context context, float radius, String text, Drawable icon, Typeface medium) {
            super(context);
            this.radius = radius;
            this.text = text;
            this.icon = icon;
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(LINE_BLUE);
            label.setTypeface(medium);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(radius * .34f);
            if (icon != null) icon.setColorFilter(new PorterDuffColorFilter(0xFFA3A4A8, PorterDuff.Mode.SRC_IN));
            setFocusable(true);
            setClickable(true);
            setDefaultFocusHighlightEnabled(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float cx = w / 2f, cy = h / 2f;
            disc.setShader(new RadialGradient(cx - radius * .4f, cy - radius * .05f, radius * 1.55f,
                    new int[]{0xFFEEEEEE, 0xFFE6E6E6, 0xFFD2D2D3, 0xFFC2C2C4}, new float[]{0f, .25f, .6f, 1f},
                    Shader.TileMode.CLAMP));
            halo.setShader(new RadialGradient(cx, cy + radius * .07f, radius * 1.17f,
                    new int[]{0xFFA4A5AB, 0xFFBDBEC4, 0xFFD2D3D8, 0x00D2D3D8}, new float[]{.82f, .90f, .97f, 1f},
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy + radius * .07f, radius * 1.17f, halo);
            canvas.drawCircle(cx, cy, radius, disc);
            boolean focused = isFocused();
            ring.setStrokeWidth(radius * (focused ? .085f : .046f));
            canvas.drawCircle(cx, cy, radius, ring);
            if (icon != null) {
                int half = Math.round(radius * .48f);
                icon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
                icon.draw(canvas);
            }
            if (text != null) {
                float baseline = cy + label.getTextSize() * .35f;
                label.setColor(0xCCFFFFFF);
                canvas.drawText(text, cx, baseline + radius * .018f, label);
                label.setColor(focused ? 0xFF7E7F83 : 0xFF9FA0A4);
                canvas.drawText(text, cx, baseline, label);
            }
        }
    }

    /** The page arrow: a blue-rimmed triangle with a pale, glassy fill. */
    static final class Arrow extends View {
        private final boolean right;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();

        Arrow(Context context, boolean right) {
            super(context);
            this.right = right;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeJoin(Paint.Join.ROUND);
            rim.setColor(0xFF1C74D8);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float inset = h * .05f;
            rim.setStrokeWidth(h * .045f);
            shape.reset();
            shape.moveTo(right ? inset : w - inset, inset);
            shape.lineTo(right ? w - inset : inset, h / 2f);
            shape.lineTo(right ? inset : w - inset, h - inset);
            shape.close();
            fill.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFE9F8FF, 0xFF8FD8FB, 0xFFD6F2FF},
                    new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(shape, fill);
            canvas.drawPath(shape, rim);
        }
    }
}
