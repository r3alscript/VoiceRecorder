package com.example.voicerecorder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static MainActivity instance;

    private TextView statusText;
    private TextView timerText;
    private TextView levelText;
    private Button btnStart;
    private Button btnStop;
    private Button btnSettings;
    private RecyclerView rv;
    private RecordsAdapter adapter;
    private MediaPlayer player;
    private RecordsAdapter.RecordItem current;
    private ActivityResultLauncher<String> micPerm;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver newRecReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            boolean error = intent.getBooleanExtra(RecordingService.EXTRA_ERROR, false);
            String s = intent.getStringExtra(RecordingService.EXTRA_URI);
            if (s == null) { loadRecordsSafe(); return; }
            Uri u = Uri.parse(s);
            if (error) {
                Toast.makeText(context, "Запис не збережено: немає сигналу.", Toast.LENGTH_LONG).show();
                loadRecordsSafe();
                return;
            }
            String name = null;
            if ("content".equals(u.getScheme())) {
                DocumentFile f = DocumentFile.fromSingleUri(context, u);
                if (f != null) name = f.getName();
            } else {
                File f = new File(u.getPath());
                name = f.getName();
            }
            if (name == null) { loadRecordsSafe(); return; }
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapterUriAt(i).equals(u.toString())) return;
            }
            adapter.addFirst(new RecordsAdapter.RecordItem(name, u));
            rv.scrollToPosition(0);
        }
    };

    private String adapterUriAt(int pos) {
        try {
            java.lang.reflect.Field f = RecordsAdapter.class.getDeclaredField("items");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<RecordsAdapter.RecordItem> items = (List<RecordsAdapter.RecordItem>) f.get(adapter);
            return items.get(pos).uri.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        instance = this;

        micPerm = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startRecording();
            else toast("Потрібен доступ до мікрофона");
        });

        adapter = new RecordsAdapter(new RecordsAdapter.Listener() {
            @Override
            public void onPlayPauseClicked(RecordsAdapter.RecordItem item, Button playBtn) {
                handlePlayPause(item, playBtn);
            }
            @Override
            public void onDeleteClicked(RecordsAdapter.RecordItem item) {
                deleteRecord(item);
            }
        });
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                micPerm.launch(Manifest.permission.RECORD_AUDIO);
            }
        });

        btnStop.setOnClickListener(v -> stopRecording());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        setRecordingUi(false);
        timerText.setText("00:00");
        levelText.setText("Рівень: 0%");
        statusText.setText("Готово");
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(RecordingService.ACTION_NEW_RECORD);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(newRecReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(newRecReceiver, f);
        receiverRegistered = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (receiverRegistered) {
            try { unregisterReceiver(newRecReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecordsSafe();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Диктофон");
        title.setTextSize(22f);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(16f);
        root.addView(statusText);

        timerText = new TextView(this);
        timerText.setTextSize(32f);
        root.addView(timerText);

        levelText = new TextView(this);
        levelText.setTextSize(14f);
        root.addView(levelText);

        btnStart = new Button(this);
        btnStart.setText("Старт");
        root.addView(btnStart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        btnStop = new Button(this);
        btnStop.setText("Стоп");
        LinearLayout.LayoutParams lpStop = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpStop.topMargin = 12;
        root.addView(btnStop, lpStop);

        btnSettings = new Button(this);
        btnSettings.setText("Налаштування");
        LinearLayout.LayoutParams lpSett = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSett.topMargin = 12;
        root.addView(btnSettings, lpSett);

        TextView my = new TextView(this);
        my.setText("Мої записи");
        my.setTextSize(16f);
        LinearLayout.LayoutParams lpMy = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpMy.topMargin = 16;
        root.addView(my, lpMy);

        rv = new RecyclerView(this);
        LinearLayout.LayoutParams lpRv = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lpRv.topMargin = 8;
        root.addView(rv, lpRv);

        return root;
    }

    private void startRecording() {
        Intent i = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void stopRecording() {
        Intent i = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP);
        startService(i);
    }

    private void setRecordingUi(boolean recording) {
        btnStart.setEnabled(!recording);
        btnStop.setEnabled(recording);
        statusText.setText(recording ? "Йде запис…" : "Зупинено");
        if (!recording) {
            timerText.setText("00:00");
            levelText.setText("Рівень: 0%");
        }
    }

    private void loadRecordsSafe() {
        try {
            List<RecordsAdapter.RecordItem> list = new ArrayList<>();
            SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            String tree = sp.getString("pref_output_dir", null);
            boolean loaded = false;
            if (tree != null) {
                Uri uri = Uri.parse(tree);
                if (hasPersistedPermission(uri)) {
                    try {
                        DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
                        if (dir != null && dir.isDirectory()) {
                            for (DocumentFile f : dir.listFiles()) {
                                String n = f.getName();
                                if (f.isFile() && n != null && (n.endsWith(".m4a") || n.endsWith(".3gp"))) {
                                    list.add(new RecordsAdapter.RecordItem(n, f.getUri()));
                                }
                            }
                            loaded = true;
                        }
                    } catch (Throwable t) {
                        loaded = false;
                    }
                }
                if (!loaded) {
                    sp.edit().remove("pref_output_dir").apply();
                }
            }
            if (!loaded) {
                File d = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
                if (d != null && d.isDirectory()) {
                    File[] all = d.listFiles();
                    if (all != null) {
                        for (File f : all) {
                            String n = f.getName();
                            if (n != null && (n.endsWith(".m4a") || n.endsWith(".3gp"))) {
                                list.add(new RecordsAdapter.RecordItem(n, Uri.fromFile(f)));
                            }
                        }
                    }
                }
            }
            adapter.setData(list);
        } catch (Throwable t) {
            adapter.setData(new ArrayList<>());
        }
    }

    private boolean hasPersistedPermission(Uri uri) {
        for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && (p.isReadPermission() || p.isWritePermission())) return true;
        }
        return false;
    }

    private void deleteRecord(RecordsAdapter.RecordItem item) {
        if (current != null && item.uri.equals(current.uri)) stopPlayback(null);
        boolean ok = false;
        try {
            if ("content".equals(item.uri.getScheme())) {
                DocumentFile f = DocumentFile.fromSingleUri(this, item.uri);
                if (f != null) ok = f.delete();
            } else {
                File f = new File(item.uri.getPath());
                ok = f.delete();
            }
        } catch (Throwable ignored) {}
        loadRecordsSafe();
        Toast.makeText(this, ok ? "Видалено" : "Не вдалося видалити", Toast.LENGTH_SHORT).show();
    }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_SHORT).show(); }

    public static void dispatchStatus(boolean recording) {
        if (instance == null) return;
        instance.runOnUiThread(() -> instance.setRecordingUi(recording));
        if (!recording && instance != null) instance.runOnUiThread(instance::loadRecordsSafe);
    }

    public static void dispatchTimer(long seconds) {
        if (instance == null) return;
        long m = seconds / 60;
        long s = seconds % 60;
        String t = (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
        instance.runOnUiThread(() -> instance.timerText.setText(t));
    }

    public static void dispatchLevel(int amp) {
        if (instance == null) return;
        int pct = 0;
        if (amp > 0) {
            if (amp > 32767) amp = 32767;
            pct = (int) (amp * 100L / 32767L);
        }
        int show = Math.max(0, Math.min(100, pct));
        instance.runOnUiThread(() -> instance.levelText.setText("Рівень: " + show + "%"));
    }

    private void handlePlayPause(RecordsAdapter.RecordItem item, Button playBtn) {
        try {
            if (current == null || !current.uri.equals(item.uri)) {
                stopPlayback(playBtn);
                current = item;
                player = new MediaPlayer();
                if ("content".equals(item.uri.getScheme())) player.setDataSource(this, item.uri);
                else player.setDataSource(item.uri.getPath());
                player.setOnCompletionListener(mp -> stopPlayback(playBtn));
                player.prepare();
                player.start();
                playBtn.setText("Пауза");
            } else if (player != null && player.isPlaying()) {
                player.pause();
                playBtn.setText("Відтворити");
            } else if (player != null) {
                player.start();
                playBtn.setText("Пауза");
            }
        } catch (IOException e) {
            stopPlayback(playBtn);
            toast("Помилка відтворення");
        }
    }

    private void stopPlayback(Button playBtn) {
        try { if (player != null && player.isPlaying()) player.stop(); } catch (Exception ignored) {}
        try { if (player != null) player.release(); } catch (Exception ignored) {}
        player = null;
        current = null;
        if (playBtn != null) playBtn.setText("Відтворити");
    }
}
