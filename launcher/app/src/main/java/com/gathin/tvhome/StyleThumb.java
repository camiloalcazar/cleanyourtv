package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** A small schematic of a home screen style for the picker: drawn here, so it costs no image and shows no app's logo. */
final class StyleThumb extends View {
    private final String style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    StyleThumb(Context context, String style) {
        super(context);
        this.style = style;
    }

    private void box(Canvas c, float l, float t, float r, float b, float radius, int color) {
        paint.setShader(null);
        paint.setColor(color);
        rect.set(l * getWidth(), t * getHeight(), r * getWidth(), b * getHeight());
        c.drawRoundRect(rect, radius * getHeight(), radius * getHeight(), paint);
    }

    private void back(Canvas c, int top, int bottom) {
        paint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
    }

    private void tiles(Canvas c, float l, float t, float w, float h, float gx, float gy, int cols, int rows, float radius) {
        int[] colors = {0xFFE5534B, 0xFF2F81F7, 0xFF3FB950, 0xFFD29922, 0xFF8957E5, 0xFF1F9EAF};
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < cols; k++) {
                float x = l + k * (w + gx), y = t + r * (h + gy);
                box(c, x, y, x + w, y + h, radius, colors[(r * cols + k) % colors.length]);
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        switch (style) {
            case LauncherStyle.WII:
                back(c, 0xFFF0F1F2, 0xFFDFE2E4);
                for (int r = 0; r < 3; r++) for (int k = 0; k < 4; k++) {
                    float x = .08f + k * .21f, y = .08f + r * .21f;
                    box(c, x, y, x + .2f, y + .19f, .025f, r == 0 ? 0xFFFFFFFF : 0xFFD2D5D8);
                }
                box(c, 0, .72f, 1, 1, 0, 0xFFD4D6DC);
                box(c, 0, .72f, 1, .735f, 0, 0xFF35BEED);
                box(c, .04f, .79f, .14f, .96f, .085f, 0xFFF5F5F5);
                box(c, .86f, .79f, .96f, .96f, .085f, 0xFFF5F5F5);
                box(c, .43f, .8f, .57f, .88f, .01f, 0xFFA2A3A6);
                break;
            case LauncherStyle.ROKU:
                back(c, 0xFF5F2E9C, 0xFF140D1E);
                box(c, .08f, .22f, .38f, .29f, .005f, 0xFFDDDDDD);
                box(c, .10f, .33f, .3f, .35f, 0, 0xB0FFFFFF);
                box(c, .10f, .40f, .28f, .42f, 0, 0xB0FFFFFF);
                tiles(c, .42f, .22f, .15f, .2f, .015f, .025f, 3, 3, .005f);
                break;
            case LauncherStyle.ROKU_NEW:
                back(c, 0xFF2C1065, 0xFF0B0724);
                paint.setShader(new LinearGradient(0, 0, 0, getHeight() * .16f, 0xFF3A1DA0, 0xFFE0603E, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, getWidth(), getHeight() * .16f, paint);
                paint.setShader(null);
                for (int i = 0; i < 4; i++) box(c, .05f, .25f + i * .09f, .08f, .3f + i * .09f, .005f, 0xD0FFFFFF);
                tiles(c, .13f, .22f, .13f, .16f, .012f, .02f, 3, 2, .005f);
                tiles(c, .13f, .66f, .1f, .12f, .012f, .02f, 4, 2, .005f);
                box(c, .67f, .22f, .94f, .9f, .005f, 0x80FFFFFF);
                break;
            case LauncherStyle.METRO:
                back(c, 0xFF001A4C, 0xFF001A4C);
                box(c, .09f, .09f, .26f, .15f, 0, 0xFFFFFFFF);
                box(c, .09f, .24f, .27f, .56f, 0, 0xFF3F9AAE);
                box(c, .28f, .24f, .46f, .39f, 0, 0xFF5A35B0);
                box(c, .28f, .41f, .36f, .56f, 0, 0xFF3FA33F);
                box(c, .38f, .41f, .46f, .56f, 0, 0xFFCF6437);
                box(c, .09f, .58f, .27f, .73f, 0, 0xFF3A80E8);
                box(c, .28f, .58f, .46f, .9f, 0, 0xFFB13050);
                box(c, .09f, .75f, .27f, .9f, 0, 0xFF8E1BA0);
                box(c, .52f, .24f, .7f, .56f, 0, 0xFF3FA33F);
                box(c, .71f, .24f, .89f, .39f, 0, 0xFFCF6437);
                box(c, .52f, .58f, .7f, .9f, 0, 0xFF3A80E8);
                box(c, .71f, .41f, .89f, .73f, 0, 0xFF5A35B0);
                break;
            case LauncherStyle.GRID:
                back(c, 0xFF000000, 0xFF000000);
                box(c, 0, 0, 1, .11f, 0, 0xFF111111);
                box(c, .02f, .025f, .055f, .085f, .03f, 0xFFE8B040);
                box(c, .16f, .025f, .75f, .085f, .03f, 0xFF262626);
                box(c, .79f, .03f, .86f, .08f, .025f, 0xFFE8B040);
                tiles(c, 0, .115f, .165f, .29f, .002f, .004f, 6, 3, 0);
                box(c, 0, .9f, 1, 1, 0, 0xFF111111);
                box(c, .1f, .94f, .16f, .96f, 0, 0xFFE8B040);
                break;
            case LauncherStyle.TVMATCH:
                back(c, 0xFFFAFAFA, 0xFFEEEEEE);
                paint.setShader(new LinearGradient(.42f * getWidth(), 0, .58f * getWidth(), 0, 0xFFFD267A, 0xFFFF7854, Shader.TileMode.CLAMP));
                c.drawRoundRect(.42f * getWidth(), .03f * getHeight(), .58f * getWidth(), .09f * getHeight(), 4, 4, paint);
                paint.setShader(null);
                c.save();
                c.rotate(5, getWidth() / 2f, getHeight() / 2f);
                box(c, .34f, .14f, .66f, .84f, .03f, 0xFFE9A0B0);
                c.restore();
                box(c, .34f, .13f, .66f, .84f, .03f, 0xFFE5534B);
                box(c, .34f, .62f, .66f, .84f, .03f, 0xB0301010);
                for (int i = 0; i < 5; i++) box(c, .34f + i * .07f, .88f, .38f + i * .07f, .96f, .04f, 0xFFFFFFFF);
                break;
            case LauncherStyle.SALA:
                back(c, 0xFF47628A, 0xFF1C2A20);
                box(c, .06f, .1f, .3f, .13f, 0, 0xB0FFFFFF);
                box(c, .06f, .17f, .36f, .33f, .01f, 0xFFFFFFFF);
                box(c, .06f, .37f, .34f, .4f, 0, 0xB0FFFFFF);
                for (int i = 0; i < 3; i++) box(c, .68f + i * .09f, .1f, .755f + i * .09f, .17f, .03f, 0x60FFFFFF);
                tiles(c, .06f, .62f, .125f, .22f, .025f, 0, 6, 1, .03f);
                break;
            case LauncherStyle.CINEMA:
                back(c, 0xFF141414, 0xFF141414);
                box(c, .06f, .05f, .09f, .1f, .005f, 0xFFD63A3A);
                box(c, .05f, .2f, .32f, .27f, .005f, 0xFFFFFFFF);
                box(c, .05f, .3f, .36f, .33f, 0, 0x90FFFFFF);
                box(c, .05f, .38f, .16f, .46f, .01f, 0xFFFFFFFF);
                box(c, .5f, .06f, 1f, .54f, 0, 0xFF3A4A68);
                tiles(c, .05f, .6f, .17f, .16f, .012f, .05f, 5, 2, .01f);
                break;
            case LauncherStyle.TUBE:
                back(c, 0xFF0F0F0F, 0xFF0F0F0F);
                for (int i = 0; i < 4; i++) box(c, .03f, .22f + i * .14f, .055f, .27f + i * .14f, .02f, 0xC0FFFFFF);
                box(c, .09f, .06f, .17f, .12f, .03f, 0xFFFFFFFF);
                for (int i = 1; i < 4; i++) box(c, .09f + i * .1f, .06f, .17f + i * .1f, .12f, .03f, 0xFF3A3A3A);
                tiles(c, .09f, .2f, .27f, .28f, .025f, .13f, 3, 2, .025f);
                for (int i = 0; i < 3; i++) box(c, .09f + i * .295f, .5f, .28f + i * .295f, .53f, 0, 0xFFE0E0E0);
                break;
            default:
                back(c, 0xFF000000, 0xFF0A0A12);
                box(c, .06f, .08f, .22f, .16f, .005f, 0xD0FFFFFF);
                box(c, .62f, .08f, .94f, .15f, .035f, 0x50FFFFFF);
                box(c, .05f, .6f, .95f, .85f, .04f, 0xFF1A1A20);
                tiles(c, .08f, .63f, .19f, .19f, .025f, 0, 4, 1, .02f);
                break;
        }
    }
}
