package com.example.vtracker;

import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    private AlertDialog devOptionsDialog = null;

    // ══════════════════════════════════════════════════════════════
    //  Called on every onResume() across all child activities.
    //  If Developer Options is ON → show blocking dialog → logout.
    // ══════════════════════════════════════════════════════════════
    @Override
    protected void onResume() {
        super.onResume();
        checkDeveloperOptions();
    }

    private void checkDeveloperOptions() {
        int devOptions = Settings.Global.getInt(
                getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);

        if (devOptions == 1) {
            // Avoid stacking multiple dialogs
            if (devOptionsDialog != null && devOptionsDialog.isShowing()) return;

            devOptionsDialog = new AlertDialog.Builder(this)
                    .setTitle("⚠️ Security Restriction")
                    .setMessage(
                            "Developer Options has been enabled on your device.\n\n" +
                                    "For security reasons, you have been logged out.\n\n" +
                                    "Please disable Developer Options to use this app.")
                    .setCancelable(false)
                    .setPositiveButton("Go to Settings", (dialog, which) -> {
                        // Clear session and redirect to settings, then close app tasks
                        LoginActivity.clearSession(this);
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        startActivity(intent);
                        finishAffinity();
                    })
                    .setNegativeButton("Exit App", (dialog, which) -> {
                        // Clear session and exit the app completely
                        LoginActivity.clearSession(this);
                        finishAffinity();
                        // Optional: force exit to ensure process is killed
                        System.exit(0);
                    })
                    .create();

            devOptionsDialog.show();
        }
    }
}