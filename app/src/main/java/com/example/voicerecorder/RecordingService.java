package com.example.voicerecorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileDescriptor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {

    public static final String ACTION_START = "start_rec";
    public static final String ACTION_STOP = "stop_rec";
    public static final String ACTION_NEW_RECORD = "com.example.voicerecorder.NEW_RECORD";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_ERROR = "error";
    private static final String CHANNEL_ID = "record_channel";

    private MediaRecorder recorder;
    private long startTs;
    private android.os.Handler timerHandler;
    private Runnable timerTick;
    private ParcelFileDescriptor currentPfd;
    private Uri currentOut;
    private boolean isEmu;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(a)) startRecording();
        else if (ACTION_STOP.equals(a)) stopRecording();
        return START_NOT_STICKY;
    }

    private void startRecording() {
        createChannel();

        Intent stopI = new Intent(this, RecordingService.class).setAction(ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(this, 2, stopI, PendingIntent.FLAG_IMMUTABLE);
        Intent openI = new Intent(this, MainActivity.class);
        PendingIntent piOpen = PendingIntent.getActivity(this, 1, openI, PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setContentTitle("Йде запис аудіо")
                .setContentIntent(piOpen)
                .addAction(0, "Зупинити", piStop)
                .setOngoing(true)
                .build();

        startForeground(1001, n);

        try {
            isEmu = isEmulator();
            currentOut = createOutputSafe(isEmu);
            recorder = new MediaRecorder();

            if (isEmu) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setAudioChannels(1);
                recorder.setAudioEncodingBitRate(12200);
                recorder.setAudioSamplingRate(8000);
            } else {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setAudioChannels(1);
                recorder.setAudioEncodingBitRate(128000);
                recorder.setAudioSamplingRate(44100);
            }

            if ("content".equals(currentOut.getScheme())) {
                currentPfd = getContentResolver().openFileDescriptor(currentOut, "w");
                FileDescriptor fd = currentPfd.getFileDescriptor();
                recorder.setOutputFile(fd);
            } else {
                recorder.setOutputFile(currentOut.getPath());
            }

            int maxMin = getMaxMinutes();
            if (maxMin > 0) recorder.setMaxDuration(maxMin * 60_000);
            recorder.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording();
            });

            recorder.prepare();
            recorder.start();

            startTs = SystemClock.elapsedRealtime();
            if (timerHandler == null) timerHandler = new android.os.Handler(getMainLooper());
            if (timerTick == null) timerTick = () -> {
                long sec = (SystemClock.elapsedRealtime() - startTs) / 1000;
                MainActivity.dispatchTimer(sec);
                int amp = 0;
                try { if (recorder != null) amp = recorder.getMaxAmplitude(); } catch (Exception ignored) {}
                MainActivity.dispatchLevel(amp);
                timerHandler.postDelayed(timerTick, 300);
            };
            timerHandler.post(timerTick);

            MainActivity.dispatchStatus(true);
        } catch (Exception e) {
            MainActivity.dispatchStatus(false);
            stopForeground(true);
            stopSelf();
        }
    }

    private void stopRecording() {
        try {
            long elapsed = SystemClock.elapsedRealtime() - startTs;
            if (elapsed < 1000) {
                try { Thread.sleep(1000 - elapsed); } catch (InterruptedException ignored) {}
            }

            if (timerHandler != null && timerTick != null) timerHandler.removeCallbacks(timerTick);
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                try { recorder.reset(); } catch (Exception ignored) {}
                try { recorder.release(); } catch (Exception ignored) {}
            }
        } finally {
            recorder = null;
            try { if (currentPfd != null) currentPfd.close(); } catch (Exception ignored) {}
            currentPfd = null;

            boolean bad = isBadFile(currentOut);
            if (currentOut != null) {
                if (bad) deleteSilently(currentOut);
                Intent added = new Intent(ACTION_NEW_RECORD);
                added.putExtra(EXTRA_URI, currentOut.toString());
                added.putExtra(EXTRA_ERROR, bad);
                added.setPackage(getPackageName());
                sendBroadcast(added);
            }

            MainActivity.dispatchStatus(false);
            stopForeground(true);
            stopSelf();
        }
    }

    private boolean isBadFile(Uri uri) {
        if (uri == null) return true;
        long size = -1;
        try {
            if ("content".equals(uri.getScheme())) {
                try (AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                    if (afd != null) size = afd.getLength();
                }
                if (size <= 0) {
                    DocumentFile df = DocumentFile.fromSingleUri(this, uri);
                    if (df != null) size = df.length();
                }
            } else {
                File f = new File(uri.getPath());
                size = f.length();
            }
        } catch (Exception ignored) {}
        return size < 1024;
    }

    private void deleteSilently(Uri uri) {
        try {
            if ("content".equals(uri.getScheme())) {
                DocumentFile f = DocumentFile.fromSingleUri(this, uri);
                if (f != null) f.delete();
            } else {
                File f = new File(uri.getPath());
                f.delete();
            }
        } catch (Exception ignored) {}
    }

    private Uri createOutputSafe(boolean amr) {
        String ext = amr ? ".3gp" : ".m4a";
        String name = "REC_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ext;
        try {
            SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            String tree = sp.getString("pref_output_dir", null);
            if (tree != null) {
                Uri uri = Uri.parse(tree);
                boolean has = false;
                for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
                    if (p.getUri().equals(uri) && p.isWritePermission()) { has = true; break; }
                }
                if (has) {
                    DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
                    if (dir != null && dir.isDirectory()) {
                        DocumentFile f = dir.createFile(amr ? "audio/3gpp" : "audio/mp4", name);
                        if (f != null) return f.getUri();
                    }
                } else {
                    sp.edit().remove("pref_output_dir").apply();
                }
            }
        } catch (Throwable ignored) {}
        File d = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
        if (d != null && !d.exists()) d.mkdirs();
        return Uri.fromFile(new File(d, name));
    }

    private int getMaxMinutes() {
        SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String v = sp.getString("pref_max_duration_min", "");
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return 0; }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Запис аудіо", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private boolean isEmulator() {
        String fp = Build.FINGERPRINT;
        String model = Build.MODEL;
        String prod = Build.PRODUCT;
        String hw = Build.HARDWARE;
        return fp != null && (fp.startsWith("generic") || fp.startsWith("android")) ||
                model != null && (model.contains("Emulator") || model.contains("Android SDK built for x86")) ||
                prod != null && (prod.contains("sdk") || prod.contains("emulator")) ||
                hw != null && (hw.contains("ranchu") || hw.contains("goldfish"));
    }
}
