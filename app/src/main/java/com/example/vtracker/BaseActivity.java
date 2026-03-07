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
                        logoutAndBlock();
                        Intent intent = new Intent(
                                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        startActivity(intent);
                    })
                    .setNegativeButton("Exit App", (dialog, which) -> {
                        logoutAndBlock();
                    })
                    .create();

            devOptionsDialog.show();
        }
    }

    // ── Clear session + close all activities ───────────────────────
    private void logoutAndBlock() {
        // Clear the saved login session
        LoginActivity.clearSession(this);

        Toast.makeText(this,
                "Logged out due to security policy.", Toast.LENGTH_SHORT).show();

        // Go back to login screen, clear entire back stack
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }
}