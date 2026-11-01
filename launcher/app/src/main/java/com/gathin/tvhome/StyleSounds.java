package com.gathin.tvhome;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * Navigation sounds, synthesised here so no audio file adds to the APK. Media usage on purpose: it follows the TV's
 * main volume, where the system stream is often muted. On its own thread, so a slow audio HAL never delays the remote.
 */
final class StyleSounds {
    private static final int RATE = 44100;
    static final int TICK = 0, PAGE = 1, SELECT = 2, NOTICE = 3, REFRESH = 4;

    /** A second set of the launcher's own tones: lower and rounder, for the grid style. */
    private final boolean soft;

    StyleSounds(boolean soft) {
        this.soft = soft;
    }

    private HandlerThread thread;
    private Handler handler;
    private final AudioTrack[] tracks = new AudioTrack[5];

    void open() {
        if (thread != null) return;
        thread = new HandlerThread("style-sounds");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(() -> {
            tracks[TICK] = track(soft ? tone(38, 520, 640, 70) : tone(45, 1500, 1500, 60));
            tracks[PAGE] = track(soft ? tone(90, 420, 620, 28) : tone(130, 700, 1250, 18));
            tracks[SELECT] = track(soft ? join(tone(55, 587, 587, 35), tone(120, 784, 784, 22))
                    : join(tone(70, 880, 880, 25), tone(130, 1320, 1320, 18)));
            if (soft) {
                // Opening an app: two short, bright notes. A new tab: a quick rise that lands on a small tap.
                tracks[NOTICE] = track(join(tone(70, 932, 932, 30), tone(150, 1245, 1245, 20)));
                tracks[REFRESH] = track(join(tone(120, 440, 1040, 12), tone(40, 1175, 1175, 70)));
            }
        });
    }

    void play(int sound) {
        if (handler != null) handler.post(() -> {
            AudioTrack track = tracks[sound];
            if (track == null) return;
            try {
                if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.stop();
                track.reloadStaticData();
                track.play();
            } catch (IllegalStateException ignored) {
                // The audio server restarted under us: silence, not a crash on Home.
            }
        });
    }

    void close() {
        if (thread == null) return;
        handler.post(() -> {
            for (int i = 0; i < tracks.length; i++) {
                if (tracks[i] != null) tracks[i].release();
                tracks[i] = null;
            }
        });
        thread.quitSafely();
        thread = null;
        handler = null;
    }

    private static AudioTrack track(short[] pcm) {
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length * 2).build();
            track.write(pcm, 0, pcm.length);
            return track;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A sine sweep with a fast attack and an exponential fade, so it starts and ends without a click. */
    static short[] tone(int ms, float fromHz, float toHz, float decay) {
        int n = RATE * ms / 1000;
        short[] pcm = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double hz = fromHz + (toHz - fromHz) * i / n;
            phase += 2 * Math.PI * hz / RATE;
            double attack = Math.min(1, t / 0.002);
            double release = Math.min(1, (double) (n - 1 - i) / (RATE * 0.004));
            pcm[i] = (short) (Math.sin(phase) * Math.exp(-decay * t) * attack * release * 0.45 * Short.MAX_VALUE);
        }
        return pcm;
    }

    static short[] join(short[] a, short[] b) {
        short[] out = new short[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
