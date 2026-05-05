package com.example.vtracker;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsActivity extends BaseActivity {

    private static final String TAG         = "NotificationsActivity";
    private static final String SERVER_BASE = "http://160.187.169.24";

    // ── Views — IDs match activity_notifications.xml ──────────────
    private ImageView    ivBack;          // your existing stub uses ivBack
    private TextView     tvMarkAllRead;
    private LinearLayout containerNotifications;
    private ProgressBar  progressBar;
    private TextView     tvError;
    private LinearLayout layoutUnreadBanner;
    private TextView     tvUnreadCount;

    private String employeeId = "";
    private int unreadCount = 0;  // track live unread count
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_notifications);

        // ── Bind views ─────────────────────────────────────────────
        // Uses ivBack to match your existing activity_notifications.xml stub
        ivBack                 = findViewById(R.id.ivBack);
        tvMarkAllRead          = findViewById(R.id.tvMarkAllRead);
        containerNotifications = findViewById(R.id.containerNotifications);
        progressBar            = findViewById(R.id.progressBar);
        tvError                = findViewById(R.id.tvError);
        layoutUnreadBanner     = findViewById(R.id.layoutUnreadBanner);
        tvUnreadCount          = findViewById(R.id.tvUnreadCount);

        if (ivBack != null) ivBack.setOnClickListener(v -> finish());
        if (tvMarkAllRead != null) tvMarkAllRead.setOnClickListener(v -> markAllRead());

        // ── Load employee ID ───────────────────────────────────────
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
        }

        fetchNotifications();
    }

    // ── Fetch from getnotifications.jsp ───────────────────────────
    private void fetchNotifications() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (tvError     != null) tvError.setVisibility(View.GONE);
        if (containerNotifications != null) containerNotifications.removeAllViews();

        if (employeeId.isEmpty()) {
            showError("Session expired. Please log in again.");
            return;
        }

        String apiUrl = SERVER_BASE
                + "/VTracker/getnotifications.jsp?empcode=" + employeeId;
        Log.d(TAG, "Fetching: " + apiUrl);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    final String json = sb.toString();
                    runOnUiThread(() -> parseAndDisplay(json));
                } else {
                    runOnUiThread(() -> showError("Server error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "Fetch error: " + e.getMessage());
                runOnUiThread(() -> showError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ── Parse response and build cards ────────────────────────────
    private void parseAndDisplay(String jsonStr) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        try {
            JSONObject root  = new JSONObject(jsonStr);
            boolean success  = root.optBoolean("success", true);

            if (!success) {
                showError("Could not load notifications.");
                return;
            }

            JSONArray items = root.optJSONArray("notifications");
            int unread      = root.optInt("unread", 0);
            unreadCount     = unread;

            if (items == null || items.length() == 0) {
                showError("No notifications yet.\nYou'll be notified when admin approves your visits.");
                return;
            }

            // Build cards first
            if (containerNotifications == null) return;
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < items.length(); i++) {
                JSONObject n = items.getJSONObject(i);
                View card = inflater.inflate(
                        R.layout.item_notification, containerNotifications, false);
                bindNotification(card, n);
                containerNotifications.addView(card);
            }

            // Auto mark all read after cards are inflated — clears dots + calls server
            if (unreadCount > 0) {
                markAllRead();
            }

        } catch (Exception e) {
            showError("Failed to load notifications.");
            Log.e(TAG, "Parse error: " + e.getMessage() + " | json: " + jsonStr);
        }
    }

    // ── Bind one notification card ─────────────────────────────────
    private void bindNotification(View card, JSONObject n) {
        TextView  tvTitle   = card.findViewById(R.id.tvNotifTitle);
        TextView  tvMessage = card.findViewById(R.id.tvNotifMessage);
        TextView  tvTime    = card.findViewById(R.id.tvNotifTime);
        ImageView ivIcon    = card.findViewById(R.id.ivNotifIcon);
        View      dot       = card.findViewById(R.id.viewUnreadDot);

        String title   = n.optString("title",   "Notification");
        String message = n.optString("message", "");
        String time    = n.optString("time",    "");
        int    isRead  = n.optInt("is_read",    0);
        int    notifId = n.optInt("id",          0);

        if (tvTitle   != null) tvTitle.setText(title);
        if (tvMessage != null) tvMessage.setText(message);
        if (tvTime    != null) tvTime.setText(formatTime(time));

        // ── Unread styling ─────────────────────────────────────────
        if (isRead == 0) {
            if (dot     != null) dot.setVisibility(View.VISIBLE);
            if (tvTitle != null) tvTitle.setTextColor(Color.parseColor("#0D1B4B"));
        } else {
            if (dot     != null) dot.setVisibility(View.GONE);
            if (tvTitle != null) tvTitle.setTextColor(Color.parseColor("#8A93B2"));
        }

        // ── Icon color by status ───────────────────────────────────
        String lower = title.toLowerCase();
        if (ivIcon != null) {
            Object parentView = ivIcon.getParent();
            if (lower.contains("approved") || lower.contains("✓")) {
                ivIcon.setColorFilter(Color.parseColor("#34A853"));
                if (parentView instanceof CardView)
                    ((CardView) parentView).setCardBackgroundColor(Color.parseColor("#E6F4EA"));
            } else if (lower.contains("rejected")) {
                ivIcon.setColorFilter(Color.parseColor("#E53935"));
                if (parentView instanceof CardView)
                    ((CardView) parentView).setCardBackgroundColor(Color.parseColor("#FDECEA"));
            }
        }

        // ── Tap to mark as read ────────────────────────────────────
        if (isRead == 0) {
            card.setOnClickListener(v -> {
                if (dot     != null) dot.setVisibility(View.GONE);
                if (tvTitle != null) tvTitle.setTextColor(Color.parseColor("#8A93B2"));
                markOneRead(notifId);
                card.setOnClickListener(null);
            });
        }
    }

    // ── Mark single notification read ─────────────────────────────
    private void markOneRead(int notifId) {
        // Decrement local count and update banner immediately
        unreadCount = Math.max(0, unreadCount - 1);
        updateUnreadBanner();

        executor.execute(() -> {
            try {
                String urlStr = SERVER_BASE
                        + "/VTracker/marknotificationread.jsp?id=" + notifId;
                HttpURLConnection conn =
                        (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "markOneRead error: " + e.getMessage());
            }
        });
    }

    // ── Mark ALL notifications read ────────────────────────────────
    private void markAllRead() {
        unreadCount = 0;
        updateUnreadBanner();
        if (containerNotifications != null) {
            for (int i = 0; i < containerNotifications.getChildCount(); i++) {
                View child  = containerNotifications.getChildAt(i);
                View dot    = child.findViewById(R.id.viewUnreadDot);
                TextView tv = child.findViewById(R.id.tvNotifTitle);
                if (dot != null) dot.setVisibility(View.GONE);
                if (tv  != null) tv.setTextColor(Color.parseColor("#8A93B2"));
                child.setOnClickListener(null);
            }
        }

        executor.execute(() -> {
            try {
                String urlStr = SERVER_BASE
                        + "/VTracker/marknotificationread.jsp?empcode=" + employeeId;
                HttpURLConnection conn =
                        (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "markAllRead error: " + e.getMessage());
            }
        });
    }

    // ── Update the unread banner ───────────────────────────────────
    private void updateUnreadBanner() {
        if (unreadCount > 0) {
            if (layoutUnreadBanner != null) layoutUnreadBanner.setVisibility(View.VISIBLE);
            if (tvUnreadCount != null) {
                tvUnreadCount.setText(unreadCount + (unreadCount == 1 ? " unread" : " unread"));
            }
        } else {
            if (layoutUnreadBanner != null) layoutUnreadBanner.setVisibility(View.GONE);
        }
    }

    // ── Format timestamp → human readable ─────────────────────────
    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date then    = sdf.parse(raw.length() > 19 ? raw.substring(0, 19) : raw);
            if (then == null) return raw;
            long diffMs  = new Date().getTime() - then.getTime();
            long mins    = diffMs / 60000;
            if (mins < 1)   return "Just now";
            if (mins < 60)  return mins + " min ago";
            long hrs = mins / 60;
            if (hrs  < 24)  return hrs  + " hr ago";
            long days = hrs / 24;
            if (days < 7)   return days + " days ago";
            return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(then);
        } catch (Exception e) {
            return raw;
        }
    }

    private void showError(String msg) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (tvError != null) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText(msg);
        }
    }

    @Override
    public void finish() {
        // Tell FacultyActivity to hide bell badge immediately on return
        FacultyActivity.sNotificationsRead = (unreadCount == 0);
        super.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}