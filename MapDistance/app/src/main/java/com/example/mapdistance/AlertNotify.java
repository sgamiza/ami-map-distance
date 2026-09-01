package com.example.mapdistance;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * 到达 / 间隔提醒：按提醒种类用各自铃声/语音，约 1 分钟。点「知道了」、划掉或回到 App 立刻停。
 */
public final class AlertNotify {
    public static final String ACTION_STOP = "com.example.mapdistance.STOP_ALERT";
    public static final String EXTRA_STOP = "stop_alert";
    private static final String CH = "goal_ring";
    private static final int NID = 88;
    private static final long RING_MS = 60_000L;
    private static final long SPEAK_EVERY_MS = 12_000L;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<Job> queue = new ArrayDeque<>();
    private static boolean playing;
    private static MediaPlayer player;
    private static Ringtone ringtone;
    private static TextToSpeech tts;
    private static String speakPending = "";
    private static boolean ttsReady;
    private static Runnable stopAt;
    private static Runnable speakLoop;
    private static PowerManager.WakeLock wake;

    private static final class Job {
        final String head;
        final String text;
        final AlertKind kind;

        Job(String head, String text, AlertKind kind) {
            this.head = head;
            this.text = text;
            this.kind = kind;
        }
    }

    private AlertNotify() {}

    public static void show(Context c, String title, String text) {
        show(c, title, text, null);
    }

    public static void show(Context c, String title, String text, AlertKind kind) {
        if (c == null || text == null || text.isEmpty()) {
            return;
        }
        Context app = c.getApplicationContext();
        String head = title == null || title.isEmpty() ? "运动提醒" : title;
        Job job = new Job(head, text, kind);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> enqueue(app, job));
            return;
        }
        enqueue(app, job);
    }

    public static void stop(Context c) {
        if (c == null) {
            return;
        }
        Context app = c.getApplicationContext();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> {
                queue.clear();
                playing = false;
                stopInternal(app, true);
            });
            return;
        }
        queue.clear();
        playing = false;
        stopInternal(app, true);
    }

    private static void enqueue(Context app, Job job) {
        queue.addLast(job);
        if (!playing) {
            playNext(app);
        }
    }

    private static void playNext(Context app) {
        Job job = queue.pollFirst();
        if (job == null) {
            playing = false;
            return;
        }
        playing = true;
        showOnMain(app, job.head, job.text, job.kind);
    }

    private static void showOnMain(Context app, String head, String text, AlertKind kind) {
        stopInternal(app, false);
        ensureChannel(app);
        speakPending = (head + "。" + text).replace(",", "");
        boolean ring = Prefs.kindRing(app, kind);
        boolean voice = Prefs.kindVoice(app, kind);
        boolean nag = ring || voice;
        Uri tone = Prefs.kindRingtone(app, kind);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        Intent open = new Intent(app, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        open.putExtra(EXTRA_STOP, true);
        PendingIntent openPi = PendingIntent.getActivity(app, 8, open, flags);

        Intent stop = new Intent(app, StopReceiver.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getBroadcast(app, 9, stop, flags);

        String extra = nag
                ? "点「知道了」立刻停；不管的话大约一分钟停下。"
                : "点「知道了」关掉。";
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(head)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text + "\n" + extra))
                .setOngoing(nag)
                .setAutoCancel(!nag)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .setDeleteIntent(stopPi)
                .addAction(0, "知道了", stopPi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(nag ? RING_MS : 8000L);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NID, b.build());
        }

        if (nag) {
            grabWake(app);
            askFocus(app);
            if (ring) {
                startRing(app, tone);
            }
            startVibrate(app, true);
            if (voice) {
                startSpeak(app);
            }
            stopAt = () -> {
                stopInternal(app, true);
                playing = false;
                playNext(app);
            };
            main.postDelayed(stopAt, RING_MS);
        } else {
            startVibrate(app, false);
            stopAt = () -> {
                stopInternal(app, true);
                playing = false;
                playNext(app);
            };
            main.postDelayed(stopAt, 8000L);
        }
    }

    private static void stopInternal(Context app, boolean clearNotif) {
        if (stopAt != null) {
            main.removeCallbacks(stopAt);
            stopAt = null;
        }
        if (speakLoop != null) {
            main.removeCallbacks(speakLoop);
            speakLoop = null;
        }
        try {
            if (player != null) {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        try {
            if (ringtone != null) {
                if (ringtone.isPlaying()) {
                    ringtone.stop();
                }
            }
        } catch (Exception ignored) {
        }
        ringtone = null;
        try {
            Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.cancel();
            }
        } catch (Exception ignored) {
        }
        try {
            if (tts != null) {
                tts.stop();
            }
        } catch (Exception ignored) {
        }
        dropFocus(app);
        releaseWake();
        if (clearNotif) {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NID);
            }
        }
    }

    private static void startRing(Context app, Uri chosen) {
        java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
        if (chosen != null) {
            uris.add(chosen);
        }
        uris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        uris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        uris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        for (Uri uri : uris) {
            if (uri == null) {
                continue;
            }
            if (startPlayer(app, uri)) {
                return;
            }
        }
        Uri last = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (last == null) {
            last = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        if (last == null) {
            return;
        }
        try {
            ringtone = RingtoneManager.getRingtone(app, last);
            if (ringtone == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 28) {
                ringtone.setLooping(true);
            }
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            ringtone.play();
        } catch (Exception ignored) {
            ringtone = null;
        }
    }

    private static boolean startPlayer(Context app, Uri uri) {
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            mp.setDataSource(app, uri);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mp.setAudioAttributes(attrs);
            mp.setLooping(true);
            mp.setVolume(1f, 1f);
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
            mp.prepare();
            mp.start();
            player = mp;
            return true;
        } catch (Exception e) {
            try {
                if (mp != null) {
                    mp.release();
                }
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private static void startVibrate(Context app, boolean loop) {
        try {
            Vibrator v = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (loop) {
                long[] pattern = new long[]{0, 600, 250, 600, 800};
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    v.vibrate(pattern, 0);
                }
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
        } catch (Exception ignored) {
        }
    }

    private static void startSpeak(Context app) {
        if (tts == null) {
            tts = new TextToSpeech(app, status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    int lang = tts.setLanguage(Locale.CHINA);
                    if (lang == TextToSpeech.LANG_MISSING_DATA
                            || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.CHINESE);
                    }
                    tts.setSpeechRate(0.95f);
                    speakNow();
                }
            });
        } else {
            speakNow();
        }
        speakLoop = new Runnable() {
            @Override
            public void run() {
                speakNow();
                if (speakLoop != null) {
                    main.postDelayed(this, SPEAK_EVERY_MS);
                }
            }
        };
        main.postDelayed(speakLoop, SPEAK_EVERY_MS);
    }

    private static void speakNow() {
        if (!ttsReady || tts == null || speakPending == null || speakPending.isEmpty()) {
            return;
        }
        try {
            tts.speak(speakPending, TextToSpeech.QUEUE_FLUSH, null, "ami-goal");
        } catch (Exception ignored) {
        }
    }

    private static void grabWake(Context app) {
        releaseWake();
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                return;
            }
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mapdistance:goal");
            wake.setReferenceCounted(false);
            wake.acquire(RING_MS + 3000L);
        } catch (Exception ignored) {
            wake = null;
        }
    }

    private static void releaseWake() {
        try {
            if (wake != null && wake.isHeld()) {
                wake.release();
            }
        } catch (Exception ignored) {
        }
        wake = null;
    }

    private static void askFocus(Context app) {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } catch (Exception ignored) {
        }
    }

    private static void dropFocus(Context app) {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.abandonAudioFocus(null);
            }
        } catch (Exception ignored) {
        }
    }

    private static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CH, "到达提醒（铃声）", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("走到设定距离或步数时响铃、语音播报，约一分钟。点知道了即停。");
        ch.enableVibration(true);
        ch.setSound(null, null);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(ch);
        }
    }

    public static class StopReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            stop(context);
        }
    }
}
