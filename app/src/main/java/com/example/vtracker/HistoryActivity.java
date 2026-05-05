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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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

    // ── History section views ──────────────────────────────────────
    private LinearLayout sectionHistory;
    private LinearLayout containerCards;
    private ProgressBar  progressBar;
    private TextView     tvError;
    private TextView     tvFromDate, tvToDate;
    private CardView     btnSearchVisits;

    // ── Expenses section views ─────────────────────────────────────
    private LinearLayout sectionExpenses;
    private LinearLayout containerExpenseCards;
    private ProgressBar  progressBarExp;
    private TextView     tvErrorExp;
    private TextView     tvExpFromDate, tvExpToDate;
    private CardView     btnSearchExpenses;
    private TextView     tvTotalApproved, tvTotalPending;

    // ── Tab indicators ─────────────────────────────────────────────
    private TextView tabHistory, tabExpenses;
    private View     indicatorHistory, indicatorExpenses;

    // ── Common header ──────────────────────────────────────────────
    private TextView  tvTitle;
    private ImageView btnBack;

    // ── Swipe Refresh ──────────────────────────────────────────────
    private SwipeRefreshLayout swipeRefreshLayout;

    // ── Bottom Nav ────────────────────────────────────────────────
    private LinearLayout navHome, navPost, navHistory, navExpenses, navProfile;

    // ── State ──────────────────────────────────────────────────────
    private String employeeId = "";
    private String userName   = "";

    private String selectedFromDate    = null;
    private String selectedToDate      = null;
    private String selectedExpFromDate = null;
    private String selectedExpToDate   = null;

    private String activeTab = "history";

    private static final String SERVER_BASE = "http://160.187.169.24";

    private static final String[][] STATUS_COLORS = {
            {"approved",  "#34A853"},
            {"completed", "#34A853"},
            {"pending",   "#FB8C00"},
            {"review",    "#1A73E8"},
            {"rejected",  "#E53935"},
    };

    private final ExecutorService executor        = Executors.newSingleThreadExecutor();
    private final ExecutorService geocodeExecutor = Executors.newFixedThreadPool(4);

    // ──────────────────────────────────────────────────────────────
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

    // ──────────────────────────────────────────────────────────────
    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        btnBack = findViewById(R.id.btnBack);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        tabHistory        = findViewById(R.id.tabHistory);
        tabExpenses       = findViewById(R.id.tabExpenses);
        indicatorHistory  = findViewById(R.id.indicatorHistory);
        indicatorExpenses = findViewById(R.id.indicatorExpenses);

        sectionHistory  = findViewById(R.id.sectionHistory);
        containerCards  = findViewById(R.id.containerCards);
        progressBar     = findViewById(R.id.progressBar);
        tvError         = findViewById(R.id.tvError);
        tvFromDate      = findViewById(R.id.tvFromDate);
        tvToDate        = findViewById(R.id.tvToDate);
        btnSearchVisits = findViewById(R.id.btnSearchVisits);

        sectionExpenses       = findViewById(R.id.sectionExpenses);
        containerExpenseCards = findViewById(R.id.containerExpenseCards);
        progressBarExp        = findViewById(R.id.progressBarExp);
        tvErrorExp            = findViewById(R.id.tvErrorExp);
        tvExpFromDate         = findViewById(R.id.tvExpFromDate);
        tvExpToDate           = findViewById(R.id.tvExpToDate);
        btnSearchExpenses     = findViewById(R.id.btnSearchExpenses);
        tvTotalApproved       = findViewById(R.id.tvTotalApproved);
        tvTotalPending        = findViewById(R.id.tvTotalPending);

        navHome     = findViewById(R.id.navHome);
        navPost     = findViewById(R.id.navPost);
        navHistory  = findViewById(R.id.navHistory);
        navExpenses = findViewById(R.id.navExpenses);
        navProfile  = findViewById(R.id.navProfile);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1A73E8"));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if ("history".equals(activeTab)) {
                    fetchHistory(selectedFromDate, selectedToDate);
                } else {
                    fetchExpenses(selectedExpFromDate, selectedExpToDate);
                }
            });
        }

        tabHistory.setOnClickListener(v -> switchTab("history"));
        tabExpenses.setOnClickListener(v -> {
            switchTab("expenses");
            if (containerExpenseCards.getChildCount() == 0) {
                fetchExpenses(null, null);
            }
        });

        btnBack.setOnClickListener(v -> finish());
        tvFromDate.setOnClickListener(v      -> showDatePicker(true, false));
        tvToDate.setOnClickListener(v        -> showDatePicker(false, false));
        btnSearchVisits.setOnClickListener(v -> fetchHistory(selectedFromDate, selectedToDate));

        tvExpFromDate.setOnClickListener(v        -> showDatePicker(true, true));
        tvExpToDate.setOnClickListener(v          -> showDatePicker(false, true));
        btnSearchExpenses.setOnClickListener(v    -> fetchExpenses(selectedExpFromDate, selectedExpToDate));

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
        navHistory.setOnClickListener(v -> { /* Already here */ });
        navExpenses.setOnClickListener(v -> {
            Intent i = new Intent(this, ExpensesActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId);
            i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
        navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId);
            i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
    }

    // ──────────────────────────────────────────────────────────────
    private void switchTab(String tab) {
        activeTab = tab;
        if ("history".equals(tab)) {
            sectionHistory.setVisibility(View.VISIBLE);
            sectionExpenses.setVisibility(View.GONE);
            tabHistory.setTextColor(Color.parseColor("#1A73E8"));
            tabExpenses.setTextColor(Color.parseColor("#8A93B2"));
            indicatorHistory.setBackgroundColor(Color.parseColor("#1A73E8"));
            indicatorExpenses.setBackgroundColor(Color.parseColor("#E0E0E0"));
        } else {
            sectionHistory.setVisibility(View.GONE);
            sectionExpenses.setVisibility(View.VISIBLE);
            tabHistory.setTextColor(Color.parseColor("#8A93B2"));
            tabExpenses.setTextColor(Color.parseColor("#1A73E8"));
            indicatorHistory.setBackgroundColor(Color.parseColor("#E0E0E0"));
            indicatorExpenses.setBackgroundColor(Color.parseColor("#1A73E8"));
        }
    }

    // ──────────────────────────────────────────────────────────────
    private void loadEmployeeData() {
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");
        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
            userName   = prefs.getString(LoginActivity.KEY_USER_NAME, "");
        }
    }

    // ──────────────────────────────────────────────────────────────
    private void showDatePicker(boolean isFrom, boolean isExp) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            String display = String.format("%02d-%02d-%04d", day, month + 1, year);
            String api     = String.format("%04d-%02d-%02d", year, month + 1, day);
            if (isExp) {
                if (isFrom) { tvExpFromDate.setText(display); tvExpFromDate.setTextColor(Color.parseColor("#0D1B4B")); selectedExpFromDate = api; }
                else        { tvExpToDate.setText(display);   tvExpToDate.setTextColor(Color.parseColor("#0D1B4B"));   selectedExpToDate   = api; }
            } else {
                if (isFrom) { tvFromDate.setText(display); tvFromDate.setTextColor(Color.parseColor("#0D1B4B")); selectedFromDate = api; }
                else        { tvToDate.setText(display);   tvToDate.setTextColor(Color.parseColor("#0D1B4B"));   selectedToDate   = api; }
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ══════════════════════════════════════════════════════════════
    // HISTORY  – fetch & display
    // ══════════════════════════════════════════════════════════════

    private void fetchHistory(String fromDate, String toDate) {
        if (swipeRefreshLayout == null || !swipeRefreshLayout.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        tvError.setVisibility(View.GONE);
        containerCards.removeAllViews();

        if (employeeId == null || employeeId.isEmpty()) {
            showError("User session expired. Please log in again.");
            return;
        }

        String apiUrl = SERVER_BASE + "/VTracker/gethistory.jsp?empcode=" + employeeId;
        Log.d(TAG, "Fetching history: " + apiUrl);

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
                    Log.d(TAG, "History response: " + jsonStr.substring(0, Math.min(300, jsonStr.length())));
                    runOnUiThread(() -> parseAndDisplayHistory(jsonStr, fromDate, toDate));
                } else {
                    runOnUiThread(() -> {
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        showError("Server error: " + responseCode);
                    });
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void parseAndDisplayHistory(String jsonStr, String fromDate, String toDate) {
        progressBar.setVisibility(View.GONE);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        try {
            JSONArray array = extractArray(jsonStr);
            if (array == null) {
                showError("Server error. Please try again later.");
                return;
            }
            if (array.length() > 0) {
                Log.d(TAG, "History sample: " + array.getJSONObject(0).toString());
            }

            List<JSONObject> filtered = filterByDate(array, fromDate, toDate, "time");
            if (filtered.isEmpty()) {
                showError("No records found for the selected range.");
                return;
            }

            LayoutInflater inflater = LayoutInflater.from(this);
            for (JSONObject obj : filtered) {
                View card = inflater.inflate(R.layout.item_history_card, containerCards, false);
                bindHistoryCard(card, obj);
                containerCards.addView(card);
            }

        } catch (JSONException e) {
            showError("Failed to parse data: " + e.getMessage());
            Log.e(TAG, "History parse error", e);
        }
    }

    private void bindHistoryCard(View card, JSONObject obj) {
        FrameLayout  frameImage        = card.findViewById(R.id.frameImage);
        ImageView    ivCardThumbnail   = card.findViewById(R.id.ivCardThumbnail);
        TextView     tvStatusBadge     = card.findViewById(R.id.tvStatusBadge);
        LinearLayout layoutStatusNoImg = card.findViewById(R.id.layoutStatusNoImage);
        TextView     tvStatusNoImg     = card.findViewById(R.id.tvStatusBadgeNoImage);
        TextView     tvCardTitle       = card.findViewById(R.id.tvCardTitle);
        TextView     tvCardDescription = card.findViewById(R.id.tvCardDescription);
        TextView     tvCardDateTime    = card.findViewById(R.id.tvCardDateTime);
        TextView     tvPhotoCount      = card.findViewById(R.id.tvPhotoCount);

        String postName = obj.optString("name", userName).trim();
        if (postName.isEmpty() || postName.equals("null")) postName = userName;

        String tripName = obj.optString("trip_name", "").trim();

        String lat = obj.optString("latitude", "").trim();
        String lng = obj.optString("longitude", "").trim();
        if (!lat.isEmpty() && !lat.equals("null") && !lng.isEmpty() && !lng.equals("null")) {
            tvCardTitle.setText(lat + ", " + lng);
            reverseGeocode(lat, lng, tvCardTitle);
        } else {
            tvCardTitle.setText("Location unavailable");
        }

        String descStr = obj.optString("description", "").trim();
        final String desc = descStr.equals("null") ? "" : descStr;

        // UPDATE: Show trip name instead of description in the history list
        if (!tripName.isEmpty() && !tripName.equals("null")) {
            tvCardDescription.setText(tripName);
        } else {
            tvCardDescription.setText(desc);
        }

        String dateTimeStr = formatDateTime(obj.optString("time", obj.optString("created_at", "")));
        tvCardDateTime.setText(dateTimeStr);

        String status = obj.optString("status", "").trim();
        if (!status.isEmpty() && !status.equals("null")) {
            int tint = Color.parseColor(getStatusColor(status));
            tvStatusBadge.setText(status.toUpperCase());
            tvStatusBadge.getBackground().setTint(tint);
            tvStatusNoImg.setText(status.toUpperCase());
            tvStatusNoImg.getBackground().setTint(tint);
            tvStatusNoImg.setVisibility(View.VISIBLE);
        }

        ArrayList<String> imageUrls = new ArrayList<>();
        JSONArray imgs = obj.optJSONArray("images");
        if (imgs != null) {
            for (int i = 0; i < imgs.length(); i++) {
                String raw = imgs.optString(i, "").trim();
                if (!raw.isEmpty() && !raw.equals("null")) {
                    imageUrls.add(raw.startsWith("http") ? raw : SERVER_BASE + "/" + raw);
                }
            }
        }
        if (imageUrls.isEmpty()) {
            for (String key : new String[]{"image1", "image2", "image3", "image", "photo", "img"}) {
                String raw = obj.optString(key, "").trim();
                if (!raw.isEmpty() && !raw.equals("null") && !raw.equals("-")) {
                    imageUrls.add(raw.startsWith("http") ? raw : SERVER_BASE + "/" + raw);
                }
            }
        }

        if (!imageUrls.isEmpty()) {
            frameImage.setVisibility(View.VISIBLE);
            layoutStatusNoImg.setVisibility(View.GONE);
            if (!status.isEmpty() && !status.equals("null")) tvStatusBadge.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(imageUrls.get(0))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(ivCardThumbnail);
            if (imageUrls.size() > 1) {
                tvPhotoCount.setVisibility(View.VISIBLE);
                tvPhotoCount.setText(imageUrls.size() + " photos");
            }
        } else {
            frameImage.setVisibility(View.GONE);
            if (!status.isEmpty() && !status.equals("null")) layoutStatusNoImg.setVisibility(View.VISIBLE);
        }

        final String finalName = postName;
        final String finalTrip = tripName;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryDetailActivity.class);
            intent.putExtra("type",        "history");
            intent.putExtra("userName",    finalName);
            intent.putExtra("tripName",    finalTrip);
            intent.putExtra("title",       tvCardTitle.getText().toString());
            intent.putExtra("dateTime",    dateTimeStr);
            intent.putExtra("status",      status);
            intent.putExtra("description", desc);
            intent.putStringArrayListExtra("images", imageUrls);
            startActivity(intent);
        });
    }

    // ══════════════════════════════════════════════════════════════
    // EXPENSES  – fetch & display
    // ══════════════════════════════════════════════════════════════

    private void fetchExpenses(String fromDate, String toDate) {
        if (swipeRefreshLayout == null || !swipeRefreshLayout.isRefreshing()) {
            progressBarExp.setVisibility(View.VISIBLE);
        }
        tvErrorExp.setVisibility(View.GONE);
        containerExpenseCards.removeAllViews();
        tvTotalApproved.setText("₹0");
        tvTotalPending.setText("₹0");

        if (employeeId == null || employeeId.isEmpty()) {
            showExpenseError("User session expired. Please log in again.");
            return;
        }

        String apiUrl = SERVER_BASE + "/VTracker/getexpenses.jsp?empcode=" + employeeId;
        Log.d(TAG, "Fetching expenses: " + apiUrl);

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
                    Log.d(TAG, "Expenses response: " + jsonStr.substring(0, Math.min(300, jsonStr.length())));
                    runOnUiThread(() -> parseAndDisplayExpenses(jsonStr, fromDate, toDate));
                } else {
                    runOnUiThread(() -> {
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        showExpenseError("Server error: " + responseCode);
                    });
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    showExpenseError("Network error: " + e.getMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void parseAndDisplayExpenses(String jsonStr, String fromDate, String toDate) {
        progressBarExp.setVisibility(View.GONE);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        try {
            JSONArray array = extractArray(jsonStr);

            // extractArray returns null when server sends {success:false,...}
            if (array == null) {
                showExpenseError("No expense records found.");
                return;
            }

            if (array.length() > 0) {
                Log.d(TAG, "Expenses sample: " + array.getJSONObject(0).toString());
            }
            List<JSONObject> filtered = filterByDate(array, fromDate, toDate, "date");
            if (filtered == null) {
                showExpenseError("No expense records found.");
                return;
            }

            filtered.removeIf(obj -> obj.optInt("is_deleted", 0) == 1);

            if (filtered.isEmpty()) {
                showExpenseError("No expense records found for the selected range.");
                return;
            }

            double totalApproved = 0, totalPending = 0;
            for (JSONObject obj : filtered) {
                String status = obj.optString("status", "").trim().toLowerCase();
                double amt = 0;
                try { amt = Double.parseDouble(obj.optString("amount", "0").trim()); }
                catch (NumberFormatException ignored) {}

                if (status.contains("approved") || status.contains("completed")) totalApproved += amt;
                else if (status.contains("pending"))                              totalPending  += amt;
            }
            tvTotalApproved.setText("₹" + (int) totalApproved);
            tvTotalPending.setText("₹" + (int) totalPending);

            LayoutInflater inflater = LayoutInflater.from(this);
            for (JSONObject obj : filtered) {
                View card = inflater.inflate(R.layout.item_expense_card, containerExpenseCards, false);
                bindExpenseCard(card, obj);
                containerExpenseCards.addView(card);
            }

        } catch (JSONException e) {
            showExpenseError("Failed to parse data: " + e.getMessage());
            Log.e(TAG, "Expenses parse error", e);
        }
    }

    private void bindExpenseCard(View card, JSONObject obj) {
        TextView tvExpenseTitle  = card.findViewById(R.id.tvExpenseTitle);
        TextView tvExpenseAmount = card.findViewById(R.id.tvExpenseAmount);
        TextView tvExpenseDate   = card.findViewById(R.id.tvExpenseDate);
        TextView tvExpenseStatus = card.findViewById(R.id.tvExpenseStatus);
        TextView tvExpenseDesc   = card.findViewById(R.id.tvExpenseDesc);

        // ── Trip name — dedicated field from API ──────────────────
        String tripName = obj.optString("trip_name", "").trim();
        if (tripName.isEmpty() || tripName.equals("null")) {
            tripName = "Expense";
        }
        tvExpenseTitle.setText(tripName);

        // ── Description ───────────────────────────────────────────
        String description = obj.optString("description", "").trim();
        if (description.equals("null")) description = "";
        
        // UPDATE: Show trip name instead of description in the expense list
        if (!tripName.isEmpty() && !tripName.equals("Expense")) {
             tvExpenseDesc.setText(tripName);
             tvExpenseDesc.setVisibility(View.VISIBLE);
        } else if (!description.isEmpty()) {
            tvExpenseDesc.setText(description);
            tvExpenseDesc.setVisibility(View.VISIBLE);
        } else {
            tvExpenseDesc.setVisibility(View.GONE);
        }

        // ── Amount ────────────────────────────────────────────────
        String amount = obj.optString("amount", "0").trim();
        tvExpenseAmount.setText("₹" + amount);

        // ── Date ──────────────────────────────────────────────────
        String dateStr = obj.optString("date", obj.optString("created_at", "")).trim();
        String formattedDate = formatDateTime(dateStr);
        tvExpenseDate.setText(formattedDate);

        // ── Status badge ──────────────────────────────────────────
        String status = obj.optString("status", "").trim();
        if (!status.isEmpty() && !status.equals("null")) {
            tvExpenseStatus.setText(status.toUpperCase());
            tvExpenseStatus.getBackground().setTint(Color.parseColor(getStatusColor(status)));
            tvExpenseStatus.setVisibility(View.VISIBLE);
        } else {
            tvExpenseStatus.setVisibility(View.GONE);
        }

        // ── Images ────────────────────────────────────────────────
        ArrayList<String> images = new ArrayList<>();
        JSONArray imgsArray = obj.optJSONArray("images");
        if (imgsArray != null) {
            for (int i = 0; i < imgsArray.length(); i++) {
                String img = imgsArray.optString(i, "").trim();
                if (!img.isEmpty() && !img.equals("null")) {
                    images.add(img.startsWith("http") ? img : SERVER_BASE + "/" + img);
                }
            }
        }

        // ── Click → detail screen ─────────────────────────────────
        final String finalTripName = tripName;
        final String finalDesc     = description;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryDetailActivity.class);
            intent.putExtra("type",        "expense");
            intent.putExtra("userName",    userName);
            intent.putExtra("tripName",    finalTripName);
            intent.putExtra("title",       "N/A"); // Location not usually available for expenses
            intent.putExtra("dateTime",    formattedDate);
            intent.putExtra("status",      status);
            intent.putExtra("description", finalDesc);
            intent.putExtra("amount",      amount);
            intent.putStringArrayListExtra("images", images);
            startActivity(intent);
        });
    }

    // ══════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Safely extract a JSONArray from the server response.
     * Returns null (instead of throwing) if the server returned an error object
     * like {"success":false,"message":"..."} so the UI can show a friendly message.
     */
    private JSONArray extractArray(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return new JSONArray();
        String trimmed = jsonStr.trim();
        try {
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed);
            }
            // It's a JSON object — check if it's an error response
            JSONObject root = new JSONObject(trimmed);
            if (root.has("success") && !root.optBoolean("success", true)) {
                // Server returned {success:false, message:"..."}
                Log.e(TAG, "Server error: " + root.optString("message", "unknown"));
                return null; // caller will show friendly message
            }
            // It's a wrapper object — unwrap the array
            String key = root.has("data")    ? "data"
                    : root.has("history") ? "history"
                    : root.has("records") ? "records"
                    : root.keys().next();
            return root.getJSONArray(key);
        } catch (JSONException e) {
            Log.e(TAG, "extractArray failed: " + e.getMessage());
            return new JSONArray(); // return empty rather than crash
        }
    }

    private List<JSONObject> filterByDate(JSONArray array, String fromDate, String toDate,
                                          String dateKey) throws JSONException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date dFrom = null, dTo = null;
        try {
            if (fromDate != null && !fromDate.isEmpty()) dFrom = sdf.parse(fromDate);
            if (toDate   != null && !toDate.isEmpty())   dTo   = sdf.parse(toDate);
            if (dTo != null) dTo = new Date(dTo.getTime() + 24L * 60 * 60 * 1000 - 1);
        } catch (ParseException ignored) {}

        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (dFrom != null || dTo != null) {
                String timeStr = obj.optString(dateKey, obj.optString("created_at", "")).trim();
                if (timeStr.length() >= 10) {
                    try {
                        Date postDate = sdf.parse(timeStr.substring(0, 10));
                        if (dFrom != null && postDate != null && postDate.before(dFrom)) continue;
                        if (dTo   != null && postDate != null && postDate.after(dTo))    continue;
                    } catch (ParseException ignored) {}
                }
            }
            out.add(obj);
        }
        return out;
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

    private void reverseGeocode(String lat, String lng, TextView target) {
        geocodeExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = SERVER_BASE + "/VTracker/geocode.jsp?lat=" + lat + "&lng=" + lng;
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String place = json.optString("place", "").trim();
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

    private void showExpenseError(String msg) {
        progressBarExp.setVisibility(View.GONE);
        tvErrorExp.setVisibility(View.VISIBLE);
        tvErrorExp.setText(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        geocodeExecutor.shutdownNow();
    }
}
