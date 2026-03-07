package com.example.vtracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

// ── Extends BaseActivity so Developer Options check runs here too ──
public class ProfileActivity extends BaseActivity {

    private static final String TAG = "ProfileActivity";

    // ── APIs ───────────────────────────────────────────────────────
    private static final String EMPLOYEE_API_BASE =
            "http://160.187.169.14/jspapi/gps/getemployees.jsp?empcode=";
    private static final String PHOTO_API_BASE =
            "http://160.187.169.24/counselling_jspapi/StaffPhotos/";

    // ── UI ─────────────────────────────────────────────────────────
    private ImageView   ivProfilePhoto;
    private ImageButton btnBack, btnEdit;
    private TextView    tvAvatarLetter, tvName, tvQualification;
    private TextView    tvEmpCode, tvEmail, tvPhone;
    private CardView    cardNotifications, cardPrivacy, cardLogout;
    private FrameLayout loadingOverlay;

    // ── Data ───────────────────────────────────────────────────────
    private String empCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1A73E8"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        setContentView(R.layout.activity_profile);

        initViews();
        handleBackPress();
        loadEmpCode();
        fetchEmployeeProfile(empCode);
        setListeners();
    }

    // ── Init Views ─────────────────────────────────────────────────
    private void initViews() {
        ivProfilePhoto    = findViewById(R.id.ivProfilePhoto);
        tvAvatarLetter    = findViewById(R.id.tvAvatarLetter);
        btnBack           = findViewById(R.id.btnBack);
        btnEdit           = findViewById(R.id.btnEdit);
        tvName            = findViewById(R.id.tvName);
        tvQualification   = findViewById(R.id.tvQualification);
        tvEmpCode         = findViewById(R.id.tvEmpCode);
        tvEmail           = findViewById(R.id.tvEmail);
        tvPhone           = findViewById(R.id.tvPhone);
        cardNotifications = findViewById(R.id.cardNotifications);
        cardPrivacy       = findViewById(R.id.cardPrivacy);
        cardLogout        = findViewById(R.id.cardLogout);
        loadingOverlay    = findViewById(R.id.loadingOverlay);
    }

    // ── Load EmpCode ───────────────────────────────────────────────
    private void loadEmpCode() {
        empCode = getIntent().getStringExtra("EMPLOYEE_ID");

        if (empCode == null || empCode.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE);
            empCode = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "00001");
        }

        if (empCode.isEmpty()) empCode = "00001";
    }

    // ── Handle Back Press ─────────────────────────────────────────
    private void handleBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    // ── Fetch Employee Profile ────────────────────────────────────
    private void fetchEmployeeProfile(String code) {
        showLoading(true);

        new Thread(() -> {
            try {
                String paddedCode = String.format("%05d",
                        Integer.parseInt(code.replaceAll("\\D", "")));

                String apiUrl = EMPLOYEE_API_BASE + paddedCode;
                Log.d(TAG, "Fetching: " + apiUrl);

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    Log.d(TAG, "Response: " + sb);
                    parseAndDisplayEmployee(sb.toString(), paddedCode);
                } else {
                    Log.e(TAG, "HTTP error: " + responseCode);
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this,
                                "Failed to load profile (HTTP " + responseCode + ")",
                                Toast.LENGTH_SHORT).show();
                        showFallbackUi(code);
                    });
                }
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error fetching profile", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    showFallbackUi(code);
                });
            }
        }).start();
    }

    // ── Parse JSON and Update UI ──────────────────────────────────
    private void parseAndDisplayEmployee(String json, String paddedCode) {
        try {
            JSONObject obj = new JSONObject(json);

            String name          = obj.optString("name",          "");
            String email         = obj.optString("email",         "");
            String phone         = obj.optString("phoneno",       "");
            String qualification = obj.optString("qualification", "");
            String fetchedCode   = obj.optString("empcode",       paddedCode);

            String displayEmail = (email.equals("0") || email.isEmpty())
                    ? "Not Available" : email;

            SharedPreferences.Editor editor = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE).edit();
            editor.putString(LoginActivity.KEY_USER_NAME,  name);
            editor.putString(LoginActivity.KEY_USER_EMAIL, displayEmail);
            editor.putString(LoginActivity.KEY_USER_PHONE, phone);
            editor.apply();

            runOnUiThread(() -> {
                showLoading(false);
                tvName.setText(name.isEmpty() ? "Unknown" : name);
                tvQualification.setText(qualification);
                tvEmpCode.setText(fetchedCode);
                tvEmail.setText(displayEmail);
                tvPhone.setText(phone.isEmpty() ? "Not Available" : phone);
                tvAvatarLetter.setText(getAvatarLetter(name));
                loadProfilePhoto(fetchedCode);
            });

        } catch (JSONException e) {
            Log.e(TAG, "JSON parse error", e);
            runOnUiThread(() -> {
                showLoading(false);
                showFallbackUi(paddedCode);
            });
        }
    }

    // ── Get Avatar Letter (skip honorifics) ───────────────────────
    private String getAvatarLetter(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "E";

        String cleaned = fullName
                .replaceAll("(?i)^(Mr\\.|Mrs\\.|Ms\\.|Dr\\.|Prof\\.|Er\\.|Er\\s)\\s*", "")
                .trim();

        if (!cleaned.isEmpty()) {
            return String.valueOf(cleaned.charAt(0)).toUpperCase();
        }

        return String.valueOf(fullName.charAt(0)).toUpperCase();
    }

    // ── Load Profile Photo (Circular via Glide) ───────────────────
    private void loadProfilePhoto(String fetchedCode) {
        String photoUrl = PHOTO_API_BASE + fetchedCode + ".JPG";
        Log.d(TAG, "Photo URL: " + photoUrl);

        Glide.with(this)
                .load(photoUrl)
                .apply(new RequestOptions()
                        .transform(new CircleCrop())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .timeout(10000)
                        .placeholder(android.R.drawable.ic_menu_myplaces)
                        .error(android.R.drawable.ic_menu_myplaces))
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            com.bumptech.glide.load.engine.GlideException e,
                            Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            boolean isFirstResource) {
                        Log.e(TAG, "Photo failed: " + photoUrl
                                + " | " + (e != null ? e.getMessage() : "unknown"));
                        runOnUiThread(() -> {
                            ivProfilePhoto.setVisibility(View.GONE);
                            tvAvatarLetter.setVisibility(View.VISIBLE);
                        });
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            android.graphics.drawable.Drawable resource,
                            Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource,
                            boolean isFirstResource) {
                        Log.d(TAG, "Photo loaded: " + photoUrl);
                        runOnUiThread(() -> {
                            ivProfilePhoto.setVisibility(View.VISIBLE);
                            tvAvatarLetter.setVisibility(View.GONE);
                        });
                        return false;
                    }
                })
                .into(ivProfilePhoto);
    }

    // ── Fallback UI (from session cache) ─────────────────────────
    private void showFallbackUi(String code) {
        SharedPreferences prefs = getSharedPreferences(
                LoginActivity.PREF_NAME, MODE_PRIVATE);
        String cachedName  = prefs.getString(LoginActivity.KEY_USER_NAME,  "");
        String cachedEmail = prefs.getString(LoginActivity.KEY_USER_EMAIL, "");
        String cachedPhone = prefs.getString(LoginActivity.KEY_USER_PHONE, "");

        tvName.setText(cachedName.isEmpty()   ? "Employee"      : cachedName);
        tvQualification.setText("");
        tvEmpCode.setText(code);
        tvEmail.setText(cachedEmail.isEmpty() ? "Not Available" : cachedEmail);
        tvPhone.setText(cachedPhone.isEmpty() ? "Not Available" : cachedPhone);

        tvAvatarLetter.setText(getAvatarLetter(cachedName));
        ivProfilePhoto.setVisibility(View.GONE);
        tvAvatarLetter.setVisibility(View.VISIBLE);
    }

    // ── Show / Hide Loading ───────────────────────────────────────
    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // ── Listeners ────────────────────────────────────────────────
    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnEdit.setOnClickListener(v ->
                Toast.makeText(this, "Edit profile coming soon",
                        Toast.LENGTH_SHORT).show());

        cardNotifications.setOnClickListener(v ->
                Toast.makeText(this, "Notifications",
                        Toast.LENGTH_SHORT).show());

        cardPrivacy.setOnClickListener(v ->
                Toast.makeText(this, "Privacy & Security",
                        Toast.LENGTH_SHORT).show());

        cardLogout.setOnClickListener(v -> showLogoutDialog());
    }

    // ── Logout Dialog ─────────────────────────────────────────────
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    LoginActivity.clearSession(this);
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}