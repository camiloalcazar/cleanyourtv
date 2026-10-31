package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The Metro home's measured numbers, in pixels of a 1920x1080 screen: a Windows 8 Start screen capture at 683x384,
 * scaled up. Its tile is 60 wide with a gap of 4, a group gap of 28, 4 rows, starting at (60, 92).
 */
final class MetroSkin {
    private MetroSkin() {}

    static final float LEFT = 169f, TOP = 259f, UNIT = 168.7f, PITCH = 179.9f, GROUP_GAP = 67.7f;
    static final int ROWS = 4, GROUP_COLUMNS = 6;
    static final int BACKGROUND = 0xFF001A4C;
    /** The flat tile colours of the Windows 8 screens: teal, violet, blue, green, orange, magenta, crimson. */
    static final int[] COLORS = {0xFF3F9AAE, 0xFF5A35B0, 0xFF3A80E8, 0xFF3FA33F, 0xFFCF6437, 0xFF8E1BA0, 0xFFB13050};

    /** A deep navy page with faint concentric rings in the top right corner; drawn here, not taken from anywhere. */
    static final class Backdrop extends Drawable {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawColor(BACKGROUND);
            float h = b.height(), cx = b.width() * .90f, cy = h * .02f;
            ring.setStyle(Paint.Style.STROKE);
            float[] radii = {.10f, .17f, .25f, .34f, .44f};
            float[] widths = {.022f, .012f, .018f, .008f, .014f};
            for (int i = 0; i < radii.length; i++) {
                ring.setStrokeWidth(h * widths[i]);
                ring.setColor(0x1C3F86C8);
                canvas.drawCircle(cx, cy, h * radii[i], ring);
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
