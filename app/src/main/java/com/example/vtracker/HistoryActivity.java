package com.example.vtracker;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends BaseActivity {

    private static final String TAG = "HistoryActivity";

    private LinearLayout containerCards;
    private ProgressBar  progressBar;
    private TextView     tvError, tvTitle;
    private ImageView    btnBack;
    private TextView     tvFromDate, tvToDate;
    private CardView     btnSearchVisits;
    private LinearLayout navHome, navPost, navHistory, navProfile;

    private String employeeId = "";
    private String userName   = "";
    private String selectedFromDate = null;
    private String selectedToDate   = null;

    private static final String SERVER_BASE = "http://160.187.169.14";

    private static final String[][] STATUS_COLORS = {
            {"approved",  "#34A853"},
            {"completed", "#34A853"},
            {"pending",   "#FB8C00"},
            {"review",    "#1A73E8"},
            {"rejected",  "#E53935"},
    };

    private final ExecutorService executor        = Executors.newSingleThreadExecutor();
    private final ExecutorService geocodeExecutor = Executors.newFixedThreadPool(4); // parallel geocoding

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_history);
        initViews();
        loadEmployeeData();
        fetchHistory(null, null);
    }

    private void initViews() {
        containerCards  = findViewById(R.id.containerCards);
        progressBar     = findViewById(R.id.progressBar);
        tvError         = findViewById(R.id.tvError);
        tvTitle         = findViewById(R.id.tvTitle);
        btnBack         = findViewById(R.id.btnBack);
        tvFromDate      = findViewById(R.id.tvFromDate);
        tvToDate        = findViewById(R.id.tvToDate);
        btnSearchVisits = findViewById(R.id.btnSearchVisits);
        navHome         = findViewById(R.id.navHome);
        navPost         = findViewById(R.id.navPost);
        navHistory      = findViewById(R.id.navHistory);
        navProfile      = findViewById(R.id.navProfile);

        btnBack.setOnClickListener(v -> finish());
        tvFromDate.setOnClickListener(v -> showDatePicker(true));
        tvToDate.setOnClickListener(v   -> showDatePicker(false));
        btnSearchVisits.setOnClickListener(v -> fetchHistory(selectedFromDate, selectedToDate));

        navHome.setOnClickListener(v -> {
            Intent i = new Intent(this, FacultyActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId);
            i.putExtra("USER_NAME", userName);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });
        navPost.setOnClickListener(v -> {
            Intent i = new Intent(this, PostVisitActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId);
            i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
        navHistory.setOnClickListener(v -> { });
        navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId);
            i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
    }

    private void loadEmployeeData() {
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");
        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
            userName   = prefs.getString(LoginActivity.KEY_USER_NAME,   "");
        }
    }

    private void showDatePicker(boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            String date    = String.format("%02d-%02d-%04d", day, month + 1, year);
            String apiDate = String.format("%04d-%02d-%02d", year, month + 1, day);
            if (isFrom) {
                tvFromDate.setText(date);
                tvFromDate.setTextColor(Color.parseColor("#0D1B4B"));
                selectedFromDate = apiDate;
            } else {
                tvToDate.setText(date);
                tvToDate.setTextColor(Color.parseColor("#0D1B4B"));
                selectedToDate = apiDate;
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void fetchHistory(String fromDate, String toDate) {
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);
        containerCards.removeAllViews();

        if (employeeId == null || employeeId.isEmpty()) {
            showError("User session expired. Please log in again.");
            return;
        }

        String apiUrl = SERVER_BASE + "/jspapi/gps/gethistory.jsp?empcode=" + employeeId;
        Log.d(TAG, "Fetching: " + apiUrl);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String jsonStr = sb.toString();
                    Log.d(TAG, "Response first 300: " + jsonStr.substring(0, Math.min(300, jsonStr.length())));
                    runOnUiThread(() -> parseAndDisplay(jsonStr, fromDate, toDate));
                } else {
                    runOnUiThread(() -> showError("Server error: " + responseCode));
                }
            } catch (IOException e) {
                runOnUiThread(() -> showError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void parseAndDisplay(String jsonStr, String fromDate, String toDate) {
        progressBar.setVisibility(View.GONE);
        try {
            JSONArray array;
            String trimmed = jsonStr.trim();
            if (trimmed.startsWith("[")) {
                array = new JSONArray(trimmed);
            } else {
                JSONObject root = new JSONObject(trimmed);
                String key = root.has("data") ? "data"
                        : root.has("history") ? "history"
                        : root.has("records") ? "records"
                        : root.keys().next();
                array = root.getJSONArray(key);
            }

            if (array.length() > 0) {
                Log.d("HistoryDEBUG", "SAMPLE: " + array.getJSONObject(0).toString());
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date dFrom = null, dTo = null;
            try {
                if (fromDate != null && !fromDate.isEmpty()) dFrom = sdf.parse(fromDate);
                if (toDate   != null && !toDate.isEmpty())   dTo   = sdf.parse(toDate);
                if (dTo != null) dTo = new Date(dTo.getTime() + 24 * 60 * 60 * 1000 - 1);
            } catch (ParseException ignored) {}

            List<JSONObject> filtered = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (dFrom != null || dTo != null) {
                    String timeStr = obj.optString("time", obj.optString("created_at", "")).trim();
                    if (timeStr.length() >= 10) {
                        try {
                            Date postDate = sdf.parse(timeStr.substring(0, 10));
                            if (dFrom != null && postDate != null && postDate.before(dFrom)) continue;
                            if (dTo   != null && postDate != null && postDate.after(dTo))    continue;
                        } catch (ParseException ignored) {}
                    }
                }
                filtered.add(obj);
            }

            if (filtered.isEmpty()) {
                showError("No records found for the selected range.");
                return;
            }

            LayoutInflater inflater = LayoutInflater.from(this);
            for (JSONObject obj : filtered) {
                View card = inflater.inflate(R.layout.item_history_card, containerCards, false);
                bindCard(card, obj);
                containerCards.addView(card);
            }

        } catch (JSONException e) {
            showError("Failed to parse data: " + e.getMessage());
            Log.e(TAG, "Parse error on: " + jsonStr);
        }
    }

    private void bindCard(View card, JSONObject obj) {

        // Views
        FrameLayout   frameImage         = card.findViewById(R.id.frameImage);
        ImageView     ivCardThumbnail    = card.findViewById(R.id.ivCardThumbnail);
        TextView      tvStatusBadge      = card.findViewById(R.id.tvStatusBadge);        // on image
        LinearLayout  layoutStatusNoImg  = card.findViewById(R.id.layoutStatusNoImage);
        TextView      tvStatusNoImg      = card.findViewById(R.id.tvStatusBadgeNoImage); // below header
        TextView      tvCardTitle        = card.findViewById(R.id.tvCardTitle);
        TextView      tvCardDescription  = card.findViewById(R.id.tvCardDescription);
        TextView      tvCardDateTime     = card.findViewById(R.id.tvCardDateTime);
        TextView      tvPhotoCount       = card.findViewById(R.id.tvPhotoCount);

        // ── Location for title (reverse geocode async, fallback to lat/lng) ──
        String lat = obj.optString("latitude", "").trim();
        String lng = obj.optString("longitude", "").trim();
        if (!lat.isEmpty() && !lng.isEmpty() && !lat.equals("null") && !lng.equals("null")) {
            tvCardTitle.setText(lat + ", " + lng); // placeholder until geocode returns
            reverseGeocode(lat, lng, tvCardTitle);
        } else {
            tvCardTitle.setText("Location unavailable");
        }

        // ── Description ───────────────────────────────────────────
        String desc = obj.optString("description", "").trim();
        if (desc.isEmpty() || desc.equals("null")) desc = "";
        tvCardDescription.setText(desc);

        // ── Date / Time ───────────────────────────────────────────
        String dateValue = obj.optString("time", obj.optString("created_at", ""));
        tvCardDateTime.setText(formatDateTime(dateValue));

        // ── Status ────────────────────────────────────────────────
        String status = obj.optString("status", "").trim();
        if (!status.isEmpty() && !status.equals("null")) {
            int tintColor = Color.parseColor(getStatusColor(status));
            // Will show on image if image exists, otherwise show below header
            tvStatusBadge.setText(status.toUpperCase());
            tvStatusBadge.getBackground().setTint(tintColor);
            tvStatusNoImg.setText(status.toUpperCase());
            tvStatusNoImg.getBackground().setTint(tintColor);
            tvStatusNoImg.setVisibility(View.VISIBLE);
        }

        // ── Images ────────────────────────────────────────────────
        // Images come from gethistory.jsp as full URLs in images[] array
        List<String> imageUrls = new ArrayList<>();
        JSONArray imgs = obj.optJSONArray("images");
        if (imgs != null) {
            for (int i = 0; i < imgs.length(); i++) {
                String raw = imgs.optString(i, "").trim();
                if (!raw.isEmpty() && !raw.equals("null")) {
                    imageUrls.add(raw); // already full URL from server
                }
            }
        }
        // Fallback for flat fields
        if (imageUrls.isEmpty()) {
            for (String key : new String[]{"image1","image2","image3","image","photo","img"}) {
                String raw = obj.optString(key, "").trim();
                if (!raw.isEmpty() && !raw.equals("null") && !raw.equals("-")) {
                    imageUrls.add(raw.startsWith("http") ? raw : SERVER_BASE + "/" + raw);
                }
            }
        }

        Log.d("HistoryDEBUG", "Images count: " + imageUrls.size()
                + (imageUrls.isEmpty() ? "" : " → " + imageUrls.get(0)));

        if (!imageUrls.isEmpty()) {
            // Show full-width image frame
            frameImage.setVisibility(View.VISIBLE);
            layoutStatusNoImg.setVisibility(View.GONE);

            // Show status badge ON the image
            if (!status.isEmpty() && !status.equals("null")) {
                tvStatusBadge.setVisibility(View.VISIBLE);
            }

            // Load image with Glide — NO color filter, NO tint
            ivCardThumbnail.clearColorFilter();
            ivCardThumbnail.setColorFilter(null);
            Glide.with(this)
                    .load(imageUrls.get(0))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(ivCardThumbnail);

            // Photo count
            if (imageUrls.size() > 1) {
                tvPhotoCount.setVisibility(View.VISIBLE);
                tvPhotoCount.setText(imageUrls.size() + " photos");
            }
        } else {
            // No image — hide image frame, show status badge below header row
            frameImage.setVisibility(View.GONE);
            if (!status.isEmpty() && !status.equals("null")) {
                layoutStatusNoImg.setVisibility(View.VISIBLE);
            }
        }
    }

    private String getStatusColor(String status) {
        String lower = status.toLowerCase().trim();
        for (String[] pair : STATUS_COLORS) {
            if (lower.contains(pair[0])) return pair[1];
        }
        return "#8A93B2";
    }

    private String formatDateTime(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) return "";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            if (raw.contains("T")) raw = raw.replace("T", " ");
            Date date = input.parse(raw.length() > 19 ? raw.substring(0, 19) : raw);
            if (date != null) {
                return new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(date);
            }
        } catch (Exception ignored) {}
        return raw;
    }

    // ── Reverse geocode — calls geocode.jsp on our server (avoids HTTPS issues) ──
    private void reverseGeocode(String lat, String lng, TextView target) {
        geocodeExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = SERVER_BASE + "/jspapi/gps/geocode.jsp"
                        + "?lat=" + lat + "&lng=" + lng;
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String place = json.optString("place", "").trim();
                Log.d(TAG, "Geocode result: '" + place + "' for " + lat + "," + lng);

                if (!place.isEmpty()) {
                    runOnUiThread(() -> { if (target != null) target.setText(place); });
                }
            } catch (Exception e) {
                Log.e(TAG, "Geocode failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void showError(String msg) {
        progressBar.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        geocodeExecutor.shutdownNow();
    }
}