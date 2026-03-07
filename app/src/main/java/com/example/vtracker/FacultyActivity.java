package com.example.vtracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.cardview.widget.CardView;

// ── Extends BaseActivity so Developer Options check runs here too ──
public class FacultyActivity extends BaseActivity {

    // ── UI References ──────────────────────────────────────────────
    private TextView tvGreeting, tvAvatarLetter;
    private CardView cardPostVisit, cardViewPosts, cardProfile;
    private LinearLayout navHome, navPost, navHistory, navProfile;
    private TextView tvSeeAll;

    // ── Data ───────────────────────────────────────────────────────
    private String employeeId = "";
    private String userName   = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1A73E8"));
        setContentView(R.layout.activity_faculty);

        initViews();
        loadEmployeeData();
        setListeners();
        handleBackPress();
    }

    // ── Init Views ─────────────────────────────────────────────────
    private void initViews() {
        tvGreeting     = findViewById(R.id.tvGreeting);
        tvAvatarLetter = findViewById(R.id.tvAvatarLetter);
        cardPostVisit  = findViewById(R.id.cardPostVisit);
        cardViewPosts  = findViewById(R.id.cardViewPosts);
        cardProfile    = findViewById(R.id.cardProfile);
        tvSeeAll       = findViewById(R.id.tvSeeAll);
        navHome        = findViewById(R.id.navHome);
        navPost        = findViewById(R.id.navPost);
        navHistory     = findViewById(R.id.navHistory);
        navProfile     = findViewById(R.id.navProfile);
    }

    // ── Load Data: Intent first, fallback to Session ───────────────
    private void loadEmployeeData() {
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");

        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
            userName   = prefs.getString(LoginActivity.KEY_USER_NAME,   "");
        }

        String displayName = (userName != null && !userName.isEmpty())
                ? userName : employeeId;

        if (displayName != null && !displayName.isEmpty()) {
            tvGreeting.setText("Hello, " + displayName);
            tvAvatarLetter.setText(getAvatarLetter(displayName));
        } else {
            tvGreeting.setText("Hello, Faculty");
            tvAvatarLetter.setText("F");
        }
    }

    // ── Get Avatar Letter (skip honorifics) ────────────────────────
    private String getAvatarLetter(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "F";

        String cleaned = fullName
                .replaceAll("(?i)^(Mr\\.|Mrs\\.|Ms\\.|Dr\\.|Prof\\.|Er\\.|Er\\s)\\s*", "")
                .trim();

        if (!cleaned.isEmpty()) {
            return String.valueOf(cleaned.charAt(0)).toUpperCase();
        }

        return String.valueOf(fullName.charAt(0)).toUpperCase();
    }

    // ── Navigate to PostVisitActivity ──────────────────────────────
    private void openPostVisit() {
        Intent intent = new Intent(this, PostVisitActivity.class);
        intent.putExtra("EMPLOYEE_ID", employeeId);
        intent.putExtra("USER_NAME",   userName);
        startActivity(intent);
    }

    // ── Navigate to Profile ────────────────────────────────────────
    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("EMPLOYEE_ID", employeeId);
        intent.putExtra("USER_NAME",   userName);
        startActivity(intent);
    }

    // ── Navigate to History ────────────────────────────────────────
    private void openHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        intent.putExtra("EMPLOYEE_ID", employeeId);
        intent.putExtra("USER_NAME",   userName);
        startActivity(intent);
    }

    // ── Handle Back Press (AndroidX) ───────────────────────────────
    private void handleBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(FacultyActivity.this,
                        "Please use the Profile > Logout to exit.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Listeners ──────────────────────────────────────────────────
    private void setListeners() {

        cardPostVisit.setOnClickListener(v -> openPostVisit());

        cardViewPosts.setOnClickListener(v -> openHistory());

        cardProfile.setOnClickListener(v -> openProfile());

        tvSeeAll.setOnClickListener(v -> openHistory());

        navHome.setOnClickListener(v ->
                Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show());

        navPost.setOnClickListener(v -> openPostVisit());

        navHistory.setOnClickListener(v -> openHistory());

        navProfile.setOnClickListener(v -> openProfile());
    }
}