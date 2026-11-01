package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * Painted parts of the two Roku-style homes. Colours and positions were read from captures of the originals; the
 * skyline is an original drawing made here from code, and no logo or artwork of theirs is used.
 */
final class RokuSkin {
    private RokuSkin() {}

    /** Classic: a violet glow hanging from the top middle of the screen, into near black at the corners. */
    static final class ClassicBackdrop extends Drawable {
        private final Paint paint = new Paint();

        @Override
        protected void onBoundsChange(Rect bounds) {
            RadialGradient gradient = new RadialGradient(0, 0, 1,
                    new int[]{0xFF6F36B8, 0xFF4C2680, 0xFF341C58, 0xFF221438, 0xFF160F22, 0xFF120C1B},
                    new float[]{0f, .3f, .48f, .67f, .86f, 1f}, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.setScale(bounds.width() * .52f, bounds.height() * 1.05f);
            matrix.postTranslate(bounds.width() / 2f, 0);
            gradient.setLocalMatrix(matrix);
            paint.setShader(gradient);
        }

        @Override public void draw(Canvas canvas) { canvas.drawRect(getBounds(), paint); }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** A thin, open chevron: what the menu and the grid use to say there is more on that side. */
    static final class Chevron extends android.view.View {
        private final boolean right;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();

        Chevron(android.content.Context context, boolean right) {
            super(context);
            this.right = right;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(Color.WHITE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float pad = w * .14f;
            stroke.setStrokeWidth(w * .16f);
            shape.reset();
            shape.moveTo(right ? pad : w - pad, pad);
            shape.lineTo(right ? w - pad : pad, h / 2f);
            shape.lineTo(right ? pad : w - pad, h - pad);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(shape, stroke);
        }
    }

    /** What a banner's top and bottom edges look like, so a tile can fill behind it without a visible join. */
    static final class Edges {
        final int top, bottom;
        /** The eighth of the width a 4:3 tile cuts off each side holds only background, no part of the logo. */
        final boolean safeToCrop;

        Edges(int top, int bottom, boolean safeToCrop) {
            this.top = top;
            this.bottom = bottom;
            this.safeToCrop = safeToCrop;
        }
    }

    static Edges edges(Drawable art) {
        Bitmap small = render(art, 64, 36);
        Edges edges = new Edges(average(small, 0), average(small, 35), sidesAreBackground(small));
        small.recycle();
        return edges;
    }

    private static Bitmap render(Drawable art, int w, int h) {
        Drawable.ConstantState state = art.getConstantState();
        Drawable copy = state != null ? state.newDrawable().mutate() : art;
        Rect keep = new Rect(copy.getBounds());
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF1A1030);
        copy.setBounds(0, 0, w, h);
        copy.draw(canvas);
        copy.setBounds(keep);
        return bitmap;
    }

    /** A background changes slowly from pixel to pixel; the edge of a logo or a letter does not. */
    private static boolean sidesAreBackground(Bitmap bitmap) {
        int w = bitmap.getWidth(), cut = w / 8 + 1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < cut; x++) {
                if (jump(bitmap.getPixel(x, y), bitmap.getPixel(x + 1, y)) > 16) return false;
                if (jump(bitmap.getPixel(w - 1 - x, y), bitmap.getPixel(w - 2 - x, y)) > 16) return false;
            }
        }
        return true;
    }

    private static int jump(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b))
                + Math.abs(Color.blue(a) - Color.blue(b));
    }

    /**
     * A banner for a tall shape, whole and across the full width. Above and below it the banner's own first and last
     * rows of pixels carry on to the edges, so there is no join at all, whatever colours the banner's edges have.
     */
    static final class ExtendedBanner extends Drawable {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

        ExtendedBanner(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            float scale = (float) bounds.width() / bitmap.getWidth();
            matrix.setScale(scale, scale);
            matrix.postTranslate(bounds.left, bounds.top + (bounds.height() - bitmap.getHeight() * scale) / 2);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
        }

        @Override public void draw(Canvas canvas) { canvas.drawRect(getBounds(), paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /**
     * The banner enlarged and blurred, for the background of a tile the banner does not fill. Made from its top and
     * bottom rows only: they meet the banner with no visible line, and the logo in its middle leaves no smudge.
     */
    static Drawable blurred(android.content.res.Resources res, Drawable art) {
        Bitmap wide = render(art, 32, 18);
        Bitmap few = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < 5; x++) {
            int from = x * 32 / 5, to = (x + 1) * 32 / 5;
            int top = stripe(wide, from, to, 0), bottom = stripe(wide, from, to, 17);
            few.setPixel(x, 0, top);
            few.setPixel(x, 1, top);
            few.setPixel(x, 2, bottom);
            few.setPixel(x, 3, bottom);
        }
        Bitmap soft = Bitmap.createScaledBitmap(few, 40, 40, true);
        wide.recycle();
        few.recycle();
        android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(res, soft);
        drawable.setFilterBitmap(true);
        return drawable;
    }

    private static int stripe(Bitmap bitmap, int from, int to, int y) {
        int r = 0, g = 0, b = 0, n = to - from;
        for (int x = from; x < to; x++) {
            int c = bitmap.getPixel(x, y);
            r += Color.red(c);
            g += Color.green(c);
            b += Color.blue(c);
        }
        return Color.rgb(r / n, g / n, b / n);
    }

    /** The banner as an opaque bitmap, at the size the side panel shows it. */
    static Bitmap bannerBitmap(Drawable art) {
        int w = 640;
        int intrinsicW = art.getIntrinsicWidth(), intrinsicH = art.getIntrinsicHeight();
        int h = intrinsicW > 0 && intrinsicH > 0 ? Math.max(1, w * intrinsicH / intrinsicW) : 360;
        return render(art, w, h);
    }

    private static int average(Bitmap bitmap, int y) {
        int r = 0, g = 0, b = 0, w = bitmap.getWidth();
        for (int x = 0; x < w; x++) {
            int c = bitmap.getPixel(x, y);
            r += Color.red(c);
            g += Color.green(c);
            b += Color.blue(c);
        }
        return Color.rgb(r / w, g / w, b / w);
    }

    /**
     * New style: a dusk skyline over water, as a band across the top of the screen. Built once. The waterline is at
     * 175/1080 of the screen, the reflection fades out by 345/1080.
     */
    static Bitmap skyline(int w, int screenH) {
        float s = screenH / 1080f;
        int h = Math.round(345 * s), horizon = Math.round(175 * s);
        Bitmap land = Bitmap.createBitmap(w, horizon, Bitmap.Config.ARGB_8888);
        paintLand(new Canvas(land), w, horizon, s);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawBitmap(land, 0, 0, null);

        // The water: the land, upside down and squashed, over a violet that fades into the page.
        Paint water = new Paint();
        water.setShader(new LinearGradient(0, horizon, 0, h, new int[]{0xFF6A2BB4, 0xFF4A1F96, 0x002C1065},
                new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, horizon, w, h, water);
        Bitmap mirrored;
        Matrix flip = new Matrix();
        flip.setScale(1, -.85f);
        mirrored = Bitmap.createBitmap(land, 0, 0, w, horizon, flip, true);
        Paint reflection = new Paint();
        reflection.setAlpha(96);
        canvas.drawBitmap(mirrored, 0, horizon, reflection);
        mirrored.recycle();
        land.recycle();

        Paint fade = new Paint();
        fade.setShader(new LinearGradient(0, horizon, 0, h, new int[]{0x00000000, 0x002C1065 | 0xB4000000, 0xFF2C1065},
                new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, horizon, w, h, fade);
        Paint ripple = new Paint();
        ripple.setColor(0x24000018);
        for (float y = horizon + 3 * s; y < h; y += 7 * s) canvas.drawRect(0, y, w, y + 2 * s, ripple);
        Paint line = new Paint();
        line.setColor(0xFFD2479C);
        canvas.drawRect(0, horizon - s, w, horizon + s, line);
        return out;
    }

    private static void paintLand(Canvas canvas, int w, int h, float s) {
        Paint sky = new Paint();
        sky.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF1E1468, 0xFF3A1DA0, 0xFF7A2FC4, 0xFFC4409F, 0xFFF0653E},
                new float[]{0f, .32f, .6f, .84f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, sky);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(w * .78f, h, h * 2.6f, 0xD0FF9A3C, 0x00FF7A3C, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, glow);
        glow.setShader(new RadialGradient(w * .32f, h, h * 2.2f, 0xA0FF5FA0, 0x00FF5FA0, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, glow);

        Random random = new Random(11);
        Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 90; i++) {
            star.setColor(Color.argb(60 + random.nextInt(170), 255, 255, 255));
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h * .55f, (.7f + random.nextFloat() * .9f) * s, star);
        }

        // Clouds: bundles of soft ovals.
        Paint cloud = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[][] banks = {{.04f, .30f, 1.3f}, {.30f, .22f, 1f}, {.52f, .34f, 1.5f}, {.72f, .20f, 1.1f}, {.92f, .36f, 1.3f}};
        for (float[] bank : banks) {
            for (int i = 0; i < 9; i++) {
                float cx = w * bank[0] + (random.nextFloat() - .5f) * 170 * s * bank[2];
                float cy = h * bank[1] + (random.nextFloat() - .3f) * 34 * s;
                float r = (12 + random.nextFloat() * 20) * s * bank[2];
                cloud.setColor(i % 3 == 0 ? 0x605A35C8 : 0x60472AAE);
                canvas.drawOval(new RectF(cx - r * 1.5f, cy - r, cx + r * 1.5f, cy + r), cloud);
            }
        }

        // Far hills.
        Paint hills = new Paint(Paint.ANTI_ALIAS_FLAG);
        hills.setColor(0xFF5B2AA8);
        Path hill = new Path();
        hill.moveTo(0, h);
        for (float x = 0; x <= w; x += 60 * s) {
            hill.lineTo(x, h - (6 + random.nextFloat() * 20) * s);
        }
        hill.lineTo(w, h);
        hill.close();
        canvas.drawPath(hill, hills);

        Paint block = new Paint();
        Paint lit = new Paint();
        float x = 0;
        while (x < w) {
            float share = x / w;
            // Taller around a downtown, low along the rest of the shore, open water under the sunset.
            float tall = share > .46f && share < .66f ? 92 : share < .18f ? 48 : share > .70f && share < .86f ? 6 : 26;
            float bw = (14 + random.nextFloat() * 28) * s;
            float bh = (8 + random.nextFloat() * tall + (tall > 40 ? 18 : 0)) * s;
            block.setColor(random.nextBoolean() ? 0xFF1E1058 : 0xFF2A1670);
            canvas.drawRect(x, h - bh, x + bw, h, block);
            if (bh > 40 * s && random.nextInt(3) == 0) canvas.drawRect(x + bw / 2 - s, h - bh - 14 * s, x + bw / 2 + s, h - bh, block);
            for (float wy = h - bh + 4 * s; wy < h - 4 * s; wy += 8 * s) {
                for (float wx = x + 3 * s; wx < x + bw - 4 * s; wx += 7 * s) {
                    if (random.nextInt(4) != 0) continue;
                    lit.setColor(random.nextInt(3) == 0 ? 0xFF7FE0FF : 0xFFFFC873);
                    canvas.drawRect(wx, wy, wx + 2.5f * s, wy + 3 * s, lit);
                }
            }
            x += bw + random.nextFloat() * 5 * s;
        }

        // A plain suspension bridge across the open water.
        Paint bridge = new Paint(Paint.ANTI_ALIAS_FLAG);
        bridge.setColor(0xFF2A1670);
        bridge.setStyle(Paint.Style.STROKE);
        bridge.setStrokeWidth(2.2f * s);
        float left = w * .71f, right = w * .86f, deck = h - 14 * s, tower = h - 42 * s;
        canvas.drawLine(left, deck, right, deck, bridge);
        float t1 = left + (right - left) * .3f, t2 = left + (right - left) * .7f;
        canvas.drawLine(t1, h, t1, tower, bridge);
        canvas.drawLine(t2, h, t2, tower, bridge);
        Path cable = new Path();
        cable.moveTo(left, deck);
        cable.quadTo((left + t1) / 2, deck - 6 * s, t1, tower);
        cable.quadTo((t1 + t2) / 2, deck + 8 * s, t2, tower);
        cable.quadTo((t2 + right) / 2, deck - 6 * s, right, deck);
        canvas.drawPath(cable, bridge);
    }
}
