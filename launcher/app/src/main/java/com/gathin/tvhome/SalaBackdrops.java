package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/** Sala's backgrounds: scenes painted here, plain colours, the owner's photos, or one that follows the time of day. */
final class SalaBackdrops {
    private SalaBackdrops() {}

    static final String[] SCENES = {"glass", "dusk", "aurora", "hill", "ocean", "pastel", "black", "coffee"};
    static final int[] SCENE_NAMES = {R.string.sala_bg_glass, R.string.sala_bg_dusk, R.string.sala_bg_aurora, R.string.sala_bg_hill, R.string.sala_bg_ocean,
            R.string.sala_bg_pastel, R.string.sala_bg_black, R.string.sala_bg_coffee};
    static final int[] COLORS = {0xFF101820, 0xFF1F2A44, 0xFF2D3A2E, 0xFF3B2A20, 0xFF4A1F2E, 0xFF2B2B2B, 0xFF004D5A, 0xFF5B4A8A};
    static final String AUTO = "auto";

    static File photoDir(Context context) {
        File dir = new File(context.getFilesDir(), "sala_photos");
        dir.mkdirs();
        return dir;
    }

    static List<String> photos(Context context) {
        String[] names = photoDir(context).list();
        List<String> out = new ArrayList<>();
        if (names != null) {
            Arrays.sort(names);
            for (String name : names) out.add("photo:" + name);
        }
        return out;
    }

    /** The choices rotation moves through: the group the current background belongs to. */
    static List<String> group(Context context, String current) {
        if (current.startsWith("photo:")) return photos(context);
        List<String> out = new ArrayList<>();
        if (current.startsWith("color:")) {
            for (int color : COLORS) out.add("color:" + color);
        } else {
            out.addAll(Arrays.asList(SCENES));
        }
        return out;
    }

    static String current(Context context) {
        return SalaData.prefs(context).getString("background", "glass");
    }

    /** The picture for a choice; "auto" picks by the hour. A missing photo falls back to the first scene. */
    static Bitmap paint(Context context, String choice, int w, int h) {
        if (AUTO.equals(choice)) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            choice = hour < 7 ? "ocean" : hour < 17 ? "hill" : hour < 19 ? "dusk" : "aurora";
        }
        if (choice.startsWith("photo:")) {
            File file = new File(photoDir(context), choice.substring(6));
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, Math.min(bounds.outWidth / Math.max(1, w), bounds.outHeight / Math.max(1, h)));
            Bitmap photo = BitmapFactory.decodeFile(file.getPath(), options);
            if (photo != null) return photo;
            choice = "dusk";
        }
        if ("glass".equals(choice)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = w < 480 ? 4 : 1;
            Bitmap wallpaper = BitmapFactory.decodeResource(context.getResources(), R.drawable.sala_glass, options);
            if (wallpaper != null) return wallpaper;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bitmap);
        if (choice.startsWith("color:")) {
            c.drawColor(Integer.parseInt(choice.substring(6)));
            return bitmap;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (choice) {
            case "aurora":
                sky(c, p, w, h, 0xFF03101F, 0xFF06243A, 0xFF0C4A55);
                p.setShader(new RadialGradient(w * .55f, h * .95f, w * .55f, 0xB02AD6A0, 0x002AD6A0, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                break;
            case "hill":
                sky(c, p, w, h, 0xFF6F9CCB, 0xFFA9C8E2, 0xFFDDE7EC);
                ridge(c, p, w, h, .62f, .55f, .64f, .6f, 0xFF4F7A45);
                ridge(c, p, w, h, .74f, .7f, .78f, .72f, 0xFF2F5A2E);
                break;
            case "ocean":
                sky(c, p, w, h, 0xFF0B1C2C, 0xFF153247, 0xFF264F68);
                break;
            case "pastel":
                sky(c, p, w, h, 0xFFF39A8E, 0xFFC79BD0, 0xFF6F86D6);
                break;
            case "black":
                c.drawColor(0xFF050505);
                break;
            case "coffee":
                sky(c, p, w, h, 0xFF3A2413, 0xFF7A4F24, 0xFF2A1A0E);
                ridge(c, p, w, h, .76f, .68f, .8f, .74f, 0xFF1E140B);
                break;
            default:
                sky(c, p, w, h, 0xFF47628A, 0xFFC08A5E, 0xFF5B4A3A);
                ridge(c, p, w, h, .62f, .56f, .66f, .6f, 0xCC3B3A3C);
                ridge(c, p, w, h, .76f, .7f, .8f, .74f, 0xFF1C2A20);
                p.setShader(new LinearGradient(0, h * .72f, 0, h, 0xFF1C2A20, 0xFF060A08, Shader.TileMode.CLAMP));
                c.drawRect(0, h * .78f, w, h, p);
                break;
        }
        return bitmap;
    }

    private static void sky(Canvas c, Paint p, int w, int h, int top, int middle, int bottom) {
        p.setShader(new LinearGradient(0, 0, w * .25f, h, new int[]{top, middle, bottom}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private static void ridge(Canvas c, Paint p, int w, int h, float a, float b, float d, float e, int color) {
        p.setShader(null);
        p.setColor(color);
        Path path = new Path();
        path.moveTo(0, h * a);
        path.cubicTo(w * .22f, h * b, w * .4f, h * d, w * .62f, h * e);
        path.cubicTo(w * .78f, h * (e - .05f), w * .9f, h * a, w, h * b);
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        c.drawPath(path, p);
    }

    /** Rotation, in minutes; 0 is off. Returns whether the background moved on. */
    static boolean rotate(Context context) {
        android.content.SharedPreferences p = SalaData.prefs(context);
        int minutes = p.getInt("rotate", 0);
        String current = current(context);
        if (minutes == 0 || AUTO.equals(current)) return false;
        long now = System.currentTimeMillis();
        if (now - p.getLong("rotated", 0) < minutes * 60000L) return false;
        List<String> group = group(context, current);
        if (group.size() < 2) return false;
        String next = group.get((group.indexOf(current) + 1) % group.size());
        p.edit().putString("background", next).putLong("rotated", now).apply();
        return true;
    }
}
