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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ── Extends BaseActivity so Developer Options check runs here too ──
public class HistoryActivity extends BaseActivity {

    private LinearLayout containerCards;
    private ProgressBar progressBar;
    private TextView tvError, tvTitle;
    private ImageView btnBack;

    private String employeeId = "";
    private String userName   = "";

    private static final String SERVER_BASE = "http://160.187.169.14";

    private static final String[] IMAGE_KEY_HINTS = {
            "image", "img", "photo", "picture", "pic",
            "thumbnail", "thumb", "avatar", "attachment", "file"
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1A73E8"));
        setContentView(R.layout.activity_history);

        initViews();
        loadEmployeeData();
        fetchHistory();
    }

    private void initViews() {
        containerCards = findViewById(R.id.containerCards);
        progressBar    = findViewById(R.id.progressBar);
        tvError        = findViewById(R.id.tvError);
        tvTitle        = findViewById(R.id.tvTitle);
        btnBack        = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadEmployeeData() {
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");

        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
            userName   = prefs.getString(LoginActivity.KEY_USER_NAME,   "");
        }
    }

    private void fetchHistory() {
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);
        containerCards.removeAllViews();

        String empCode = employeeId.isEmpty() ? "02713" : employeeId;
        String apiUrl  = "http://160.187.169.14/jspapi/gps/gethistory.jsp?empcode=" + empCode;

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
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String jsonStr = sb.toString();
                    runOnUiThread(() -> parseAndDisplay(jsonStr));
                } else {
                    String errMsg = "Server error: " + responseCode;
                    runOnUiThread(() -> showError(errMsg));
                }

            } catch (IOException e) {
                String errMsg = "Network error: " + e.getMessage();
                runOnUiThread(() -> showError(errMsg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void parseAndDisplay(String jsonStr) {
        progressBar.setVisibility(View.GONE);
        try {
            JSONArray array;
            if (jsonStr.trim().startsWith("[")) {
                array = new JSONArray(jsonStr);
            } else {
                JSONObject root = new JSONObject(jsonStr);
                String key = root.has("data")    ? "data"
                        : root.has("history") ? "history"
                        : root.has("records") ? "records"
                        : root.keys().next();
                array = root.getJSONArray(key);
            }

            if (array.length() == 0) {
                showError("No history records found.");
                return;
            }

            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                View card = inflater.inflate(R.layout.item_history_card, containerCards, false);
                bindCard(card, obj);
                containerCards.addView(card);
            }

        } catch (JSONException e) {
            showError("Failed to parse data: " + e.getMessage());
        }
    }

    private void bindCard(View card, JSONObject obj) {
        LinearLayout fieldsLayout = card.findViewById(R.id.fieldsLayout);
        TextView tvCardTitle      = card.findViewById(R.id.tvCardTitle);

        String[] titleKeys = {"title", "subject", "description", "name",
                "visit_title", "issue_title", "location", "place"};
        String titleValue = "";
        for (String k : titleKeys) {
            if (obj.has(k)) {
                titleValue = obj.optString(k, "");
                if (!titleValue.isEmpty()) break;
            }
        }
        if (titleValue.isEmpty() && obj.length() > 0) {
            titleValue = obj.optString(obj.keys().next(), "Record");
        }
        tvCardTitle.setText(titleValue);

        /* ---------- NEW PART : HANDLE MULTIPLE IMAGES ---------- */

        if (obj.has("images")) {
            try {
                JSONArray imgs = obj.getJSONArray("images");

                for (int j = 0; j < imgs.length(); j++) {

                    String value = imgs.getString(j);
                    String fullUrl = value;

                    Log.d("IMAGE_URL", "Image: " + fullUrl);

                    View imgRow = LayoutInflater.from(this)
                            .inflate(R.layout.item_history_image_row, fieldsLayout, false);

                    TextView tvImgLabel = imgRow.findViewById(R.id.tvImgLabel);
                    ImageView imgView   = imgRow.findViewById(R.id.imgField);

                    tvImgLabel.setText("Image " + (j + 1));

                    Glide.with(this)
                            .load(fullUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .centerCrop()
                            .into(imgView);

                    fieldsLayout.addView(imgRow);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        /* ------------------------------------------------------- */

        JSONArray names = obj.names();
        if (names == null) return;

        for (int i = 0; i < names.length(); i++) {
            try {
                String key   = names.getString(i);

                /* ---- SKIP images array because already handled ---- */
                if(key.equals("images")) continue;

                String value = obj.optString(key, "").trim();
                if (value.isEmpty() || value.equals("null")) value = "-";

                if (isImageField(key, value)) {
                    String fullUrl = buildImageUrl(value);

                    Log.d("IMAGE_URL", "Raw: " + value);
                    Log.d("IMAGE_URL", "Built: " + fullUrl);

                    View imgRow = LayoutInflater.from(this)
                            .inflate(R.layout.item_history_image_row, fieldsLayout, false);

                    TextView  tvImgLabel = imgRow.findViewById(R.id.tvImgLabel);
                    ImageView imgView    = imgRow.findViewById(R.id.imgField);

                    tvImgLabel.setText(prettifyKey(key));

                    Glide.with(this)
                            .load(fullUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .centerCrop()
                            .into(imgView);

                    fieldsLayout.addView(imgRow);

                } else {
                    View row = LayoutInflater.from(this)
                            .inflate(R.layout.item_history_field_row, fieldsLayout, false);

                    TextView tvKey   = row.findViewById(R.id.tvFieldKey);
                    TextView tvValue = row.findViewById(R.id.tvFieldValue);

                    tvKey.setText(prettifyKey(key));
                    tvValue.setText(value);

                    if (key.toLowerCase().contains("status")) {
                        String lower = value.toLowerCase();
                        if (lower.contains("submit"))      tvValue.setTextColor(Color.parseColor("#1A73E8"));
                        else if (lower.contains("resolv")) tvValue.setTextColor(Color.parseColor("#34A853"));
                        else if (lower.contains("draft"))  tvValue.setTextColor(Color.parseColor("#FBBC04"));
                        else                               tvValue.setTextColor(Color.parseColor("#555555"));
                    }

                    fieldsLayout.addView(row);
                }

            } catch (JSONException ignored) {}
        }
    }

    private String buildImageUrl(String path) {
        if (path == null || path.equals("-")) return "";

        if (path.startsWith("http://") || path.startsWith("https://")) return path;

        String normalized = path.replace("\\", "/");

        int idx = normalized.indexOf("/jspapi/");
        if (idx != -1) {
            String relativePath = normalized.substring(idx);
            return SERVER_BASE + relativePath;
        }

        int webappsIdx = normalized.indexOf("/webapps/");
        if (webappsIdx != -1) {
            String relativePath = normalized.substring(webappsIdx + "/webapps".length());
            return SERVER_BASE + relativePath;
        }

        String clean = normalized.startsWith("/") ? normalized : "/" + normalized;
        return SERVER_BASE + clean;
    }

    private boolean isImageField(String key, String value) {
        if (value.equals("-")) return false;

        String lowerKey = key.toLowerCase();
        for (String hint : IMAGE_KEY_HINTS) {
            if (lowerKey.contains(hint)) return true;
        }

        String lowerVal = value.toLowerCase();
        return lowerVal.endsWith(".jpg")  || lowerVal.endsWith(".jpeg")
                || lowerVal.endsWith(".png")  || lowerVal.endsWith(".gif")
                || lowerVal.endsWith(".webp") || lowerVal.endsWith(".bmp");
    }

    private String prettifyKey(String key) {
        String s = key.replace("_", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        return s.isEmpty() ? key : Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
    }
}