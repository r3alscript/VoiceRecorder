package com.example.voicerecorder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

public class SettingsActivity extends AppCompatActivity {

    ActivityResultLauncher<Intent> folderPicker;
    private int containerId = 0x100011;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button back = new Button(this);
        back.setText("Назад");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout container = new FrameLayout(this);
        container.setId(containerId);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = 16;
        root.addView(container, lp);

        setContentView(root);

        folderPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                Uri tree = res.getData().getData();
                if (tree != null) {
                    getContentResolver().takePersistableUriPermission(
                            tree,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    PreferenceManager.getDefaultSharedPreferences(this)
                            .edit().putString("pref_output_dir", tree.toString()).apply();
                }
            }
        });

        getSupportFragmentManager()
                .beginTransaction()
                .replace(containerId, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());

            EditTextPreference maxMin = new EditTextPreference(requireContext());
            maxMin.setKey("pref_max_duration_min");
            maxMin.setTitle("Макс. тривалість запису (хв)");
            maxMin.setDialogTitle("Введіть тривалість…");
            maxMin.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            screen.addPreference(maxMin);

            Preference choose = new Preference(requireContext());
            choose.setKey("pref_output_dir");
            choose.setTitle("Папка збереження (SAF)");
            choose.setSummary("Обрати директорію для .m4a");
            choose.setOnPreferenceClickListener(p -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                ((SettingsActivity) requireActivity()).folderPicker.launch(i);
                return true;
            });
            screen.addPreference(choose);

            setPreferenceScreen(screen);
        }
    }
}
