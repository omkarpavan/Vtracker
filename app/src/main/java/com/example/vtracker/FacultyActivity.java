package com.example.vtracker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FacultyActivity extends BaseActivity {

    // ── UI ─────────────────────────────────────────────────────────
    private DrawerLayout drawerLayout;
    private LinearLayout sideDrawer;
    private SwipeRefreshLayout swipeRefreshLayout;

    private TextView tvGreeting;
    private TextView tvDrawerName, tvDrawerAvatarLetter, tvDrawerEmployeeId;
    private ImageView ivDrawerPhoto;  // staff photo in drawer
    private TextView tvNotifBadge;          // bell badge in header
    private TextView tvDrawerNotifBadge;    // badge in drawer menu
    private androidx.cardview.widget.CardView cardDrawerNotifBadge;

    private CardView cardPostVisit, cardViewPosts, cardProfile;
    private LinearLayout navHome, navPost, navHistory, navExpenses, navProfile;
    private LinearLayout drawerHeaderProfile;
    private LinearLayout drawerHome, drawerPost, drawerHistory,
            drawerExpenses, drawerProfile, drawerNotifications, drawerLogout;
    private TextView tvSeeAll;
    private ImageView ivMenu, ivNotification;

    // Recent Activity Card 1
    private ImageView ivActivity1Image;
    private TextView  tvActivity1Title, tvActivity1Location, tvActivity1Time;
    private CardView  cardActivity1;

    // Recent Activity Card 2
    private ImageView ivActivity2Image;
    private TextView  tvActivity2Title, tvActivity2Location, tvActivity2Time;
    private CardView  cardActivity2;

    // Empty state
    private LinearLayout layoutNoActivity;

    // ── Data ───────────────────────────────────────────────────────
    private String employeeId = "";
    private String userName   = "";

    // ── Same server as HistoryActivity — UNCHANGED ─────────────────
    private static final String SERVER_BASE    = "http://160.187.169.24";
    private static final String PHOTO_API_BASE = "http://160.187.169.24/counselling_jspapi/StaffPhotos/";

    private final ExecutorService executor        = Executors.newSingleThreadExecutor();
    private final ExecutorService geocodeExecutor = Executors.newFixedThreadPool(3);

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

    @Override
    protected void onResume() {
        super.onResume();
        fetchRecentActivity();

        // If user just came back from NotificationsActivity and read all — hide badge instantly
        if (sNotificationsRead) {
            sNotificationsRead = false;
            if (tvNotifBadge != null) tvNotifBadge.setVisibility(View.GONE);
            if (cardDrawerNotifBadge != null) cardDrawerNotifBadge.setVisibility(View.GONE);
        } else {
            fetchUnreadCount(); // otherwise refresh from server
        }
    }

    // ── Bind views ─────────────────────────────────────────────────
    private void initViews() {
        drawerLayout         = findViewById(R.id.drawerLayout);
        sideDrawer           = findViewById(R.id.sideDrawer);
        swipeRefreshLayout   = findViewById(R.id.swipeRefreshLayout);
        tvGreeting           = findViewById(R.id.tvGreeting);
        tvDrawerName         = findViewById(R.id.tvDrawerName);
        tvDrawerAvatarLetter = findViewById(R.id.tvDrawerAvatarLetter);
        tvDrawerEmployeeId   = findViewById(R.id.tvDrawerEmployeeId);
        ivDrawerPhoto        = findViewById(R.id.ivDrawerPhoto);
        tvNotifBadge         = findViewById(R.id.tvNotifBadge);
        tvDrawerNotifBadge   = findViewById(R.id.tvDrawerNotifBadge);
        cardDrawerNotifBadge = findViewById(R.id.cardDrawerNotifBadge);
        cardPostVisit        = findViewById(R.id.cardPostVisit);
        cardViewPosts        = findViewById(R.id.cardViewPosts);
        cardProfile          = findViewById(R.id.cardProfile);
        tvSeeAll             = findViewById(R.id.tvSeeAll);
        navHome              = findViewById(R.id.navHome);
        navPost              = findViewById(R.id.navPost);
        navHistory           = findViewById(R.id.navHistory);
        navExpenses          = findViewById(R.id.navExpenses);
        navProfile           = findViewById(R.id.navProfile);
        ivMenu               = findViewById(R.id.ivMenu);
        ivNotification       = findViewById(R.id.ivNotification);
        drawerHeaderProfile  = findViewById(R.id.drawerHeaderProfile);
        drawerHome           = findViewById(R.id.drawerHome);
        drawerPost           = findViewById(R.id.drawerPost);
        drawerHistory        = findViewById(R.id.drawerHistory);
        drawerProfile        = findViewById(R.id.drawerProfile);
        drawerExpenses       = findViewById(R.id.drawerExpenses);
        drawerNotifications  = findViewById(R.id.drawerNotifications);
        drawerLogout         = findViewById(R.id.drawerLogout);

        ivActivity1Image    = findViewById(R.id.ivActivity1Image);
        tvActivity1Title    = findViewById(R.id.tvActivity1Title);
        tvActivity1Location = findViewById(R.id.tvActivity1Location);
        tvActivity1Time     = findViewById(R.id.tvActivity1Time);
        cardActivity1       = findViewById(R.id.cardActivity1);

        ivActivity2Image    = findViewById(R.id.ivActivity2Image);
        tvActivity2Title    = findViewById(R.id.tvActivity2Title);
        tvActivity2Location = findViewById(R.id.tvActivity2Location);
        tvActivity2Time     = findViewById(R.id.tvActivity2Time);
        cardActivity2       = findViewById(R.id.cardActivity2);

        layoutNoActivity    = findViewById(R.id.layoutNoActivity);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1A73E8"));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                fetchRecentActivity();
                fetchUnreadCount();
            });
        }
    }

    // ── Load user data ─────────────────────────────────────────────
    private void loadEmployeeData() {
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");

        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
            userName   = prefs.getString(LoginActivity.KEY_USER_NAME, "");
        }

        String displayName = (userName != null && !userName.isEmpty())
                ? userName : employeeId;

        if (displayName != null && !displayName.isEmpty()) {
            tvGreeting.setText("Hello, " + displayName);
            tvDrawerName.setText(displayName);
            tvDrawerAvatarLetter.setText(getAvatarLetter(displayName));
        } else {
            tvGreeting.setText("Hello, Faculty");
            tvDrawerName.setText("Faculty");
            tvDrawerAvatarLetter.setText("F");
        }

        tvDrawerEmployeeId.setText(
                (employeeId != null && !employeeId.isEmpty())
                        ? "ID: " + employeeId : "Employee");

        // Load staff photo into drawer — falls back to letter if not found
        loadDrawerPhoto();
    }

    private void loadDrawerPhoto() {
        if (employeeId == null || employeeId.isEmpty() || ivDrawerPhoto == null) return;

        // Pad empcode to 5 digits (same as AdminActivity)
        String paddedCode = employeeId;
        try {
            paddedCode = String.format("%05d",
                    Integer.parseInt(employeeId.replaceAll("\\D", "")));
        } catch (NumberFormatException ignored) {}

        String photoUrl = PHOTO_API_BASE + paddedCode + ".JPG";
        Log.d("FacultyActivity", "Loading drawer photo: " + photoUrl);

        Glide.with(this)
                .load(photoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .placeholder(android.R.color.transparent)  // show letter while loading
                .error(android.R.color.transparent)         // show letter if not found
                .into(ivDrawerPhoto);
    }

    // ══════════════════════════════════════════════════════════════
    //  Fetch latest 2 records — uses same endpoint as HistoryActivity
    // ══════════════════════════════════════════════════════════════
    private void fetchRecentActivity() {
        if (employeeId == null || employeeId.isEmpty()) {
            showNoActivity();
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        // Pad empcode exactly like HistoryActivity does
        String empCode;
        try {
            empCode = String.format("%05d",
                    Integer.parseInt(employeeId.replaceAll("\\D", "")));
        } catch (NumberFormatException e) {
            empCode = employeeId;
        }

        final String finalEmpCode = empCode;

        // Run on background thread — same pattern as HistoryActivity
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // ── SAME endpoint as HistoryActivity — UNCHANGED ──
                String apiUrl = SERVER_BASE
                        + "/VTracker/gethistory.jsp?empcode=" + finalEmpCode;
                Log.d("FacultyActivity", "Recent activity URL: " + apiUrl);

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

                    String jsonStr = sb.toString().trim();
                    Log.d("FacultyActivity", "Recent activity response length: "
                            + jsonStr.length());

                    // Parse — same logic as HistoryActivity
                    JSONArray array;
                    if (jsonStr.startsWith("[")) {
                        array = new JSONArray(jsonStr);
                    } else {
                        JSONObject root = new JSONObject(jsonStr);
                        String key = root.has("data")    ? "data"
                                : root.has("history") ? "history"
                                : root.has("records") ? "records"
                                : root.keys().next();
                        array = root.getJSONArray(key);
                    }

                    final JSONArray finalArray = array;
                    runOnUiThread(() -> {
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        if (finalArray.length() == 0) {
                            showNoActivity();
                        } else {
                            populateActivityCards(finalArray);
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        showNoActivity();
                    });
                }
            } catch (Exception e) {
                Log.e("FacultyActivity", "fetchRecentActivity error: " + e.getMessage());
                runOnUiThread(() -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    showNoActivity();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ── Populate the two activity cards ───────────────────────────
    private void populateActivityCards(JSONArray records) {
        layoutNoActivity.setVisibility(View.GONE);

        try {
            // ── Card 1 — most recent record ──────────────────────
            if (records.length() > 0) {
                JSONObject r1 = records.getJSONObject(0);
                bindActivityCard(
                        cardActivity1, ivActivity1Image,
                        tvActivity1Title, tvActivity1Location, tvActivity1Time,
                        r1);
                cardActivity1.setVisibility(View.VISIBLE);
                cardActivity1.setOnClickListener(v -> openDetail(r1, tvActivity1Location.getText().toString()));
            } else {
                cardActivity1.setVisibility(View.GONE);
            }

            // ── Card 2 — second most recent ──────────────────────
            if (records.length() > 1) {
                JSONObject r2 = records.getJSONObject(1);
                bindActivityCard(
                        cardActivity2, ivActivity2Image,
                        tvActivity2Title, tvActivity2Location, tvActivity2Time,
                        r2);
                cardActivity2.setVisibility(View.VISIBLE);
                cardActivity2.setOnClickListener(v -> openDetail(r2, tvActivity2Location.getText().toString()));
            } else {
                cardActivity2.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            Log.e("FacultyActivity", "populateActivityCards error: " + e.getMessage());
            showNoActivity();
        }
    }

    // ── Bind one activity card from a JSON record ─────────────────
    private void bindActivityCard(CardView card, ImageView ivImage,
                                  TextView tvTitle, TextView tvLocation,
                                  TextView tvTime, JSONObject obj) {
        // Title — Try Trip Name first (Requested update)
        String tripName = obj.optString("trip_name", "").trim();
        if (tripName.isEmpty() || tripName.equals("null")) {
            // fallback chain, excluding description if possible
            String[] titleKeys = {"title", "subject", "name", "visit_title"};
            for (String k : titleKeys) {
                String v = obj.optString(k, "").trim();
                if (!v.isEmpty() && !v.equals("null")) { tripName = v; break; }
            }
        }
        
        // If still empty, use description as a last resort or "Field Visit"
        if (tripName.isEmpty() || tripName.equals("null")) {
            tripName = obj.optString("description", "").trim();
        }
        
        tvTitle.setText(tripName.isEmpty() || tripName.equals("null") ? "Field Visit" : tripName);

        // Location — reverse geocode lat/lng to place name
        String lat = obj.optString("latitude",  "").trim();
        String lng = obj.optString("longitude", "").trim();
        String loc = obj.optString("location", obj.optString("address", "")).trim();
        if (!loc.isEmpty() && !loc.equals("null")) {
            tvLocation.setText(loc);
        } else if (!lat.isEmpty() && !lng.isEmpty()
                && !lat.equals("null") && !lng.equals("null")) {
            tvLocation.setText(lat + ", " + lng); // placeholder
            reverseGeocode(lat, lng, tvLocation);  // replace async
        } else {
            tvLocation.setText("—");
        }

        // Time
        String[] timeKeys = {"time", "created_at", "visit_date", "date", "submitted_at"};
        String timeVal = "";
        for (String k : timeKeys) {
            String v = obj.optString(k, "").trim();
            if (!v.isEmpty() && !v.equals("null")) { timeVal = v; break; }
        }
        tvTime.setText(formatTime(timeVal));

        // Image — use Glide exactly like HistoryActivity, try images[] array first
        String imageUrl = "";
        JSONArray imgs = obj.optJSONArray("images");
        if (imgs != null && imgs.length() > 0) {
            imageUrl = buildImageUrl(imgs.optString(0, ""));
        }
        if (imageUrl.isEmpty()) {
            // Fallback: scan fields for image-like values
            String[] imgKeys = {"image", "photo", "img", "picture", "thumbnail", "file"};
            for (String k : imgKeys) {
                String v = obj.optString(k, "").trim();
                if (!v.isEmpty() && !v.equals("null") && !v.equals("-")) {
                    imageUrl = buildImageUrl(v);
                    break;
                }
            }
        }

        if (!imageUrl.isEmpty()) {
            // Clear tint completely before Glide loads
            ivImage.clearColorFilter();
            ivImage.setImageDrawable(null);
            Glide.with(this)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(ivImage);
        } else {
            // Grey placeholder — no blue tint
            ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
            ivImage.setColorFilter(Color.parseColor("#C8CDD8"));
        }
    }

    private void openDetail(JSONObject obj, String locationText) {
        Intent intent = new Intent(this, HistoryDetailActivity.class);
        intent.putExtra("type", "history"); // Dashboard recent activity is usually history
        intent.putExtra("userName", userName);
        intent.putExtra("tripName", obj.optString("trip_name", ""));
        intent.putExtra("title", locationText);
        
        String time = obj.optString("time", obj.optString("created_at", ""));
        intent.putExtra("dateTime", formatFullDateTime(time));
        
        intent.putExtra("status", obj.optString("status", "pending"));
        intent.putExtra("description", obj.optString("description", ""));
        
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
        intent.putStringArrayListExtra("images", imageUrls);
        startActivity(intent);
    }

    private String formatFullDateTime(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) return "";
        try {
            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            if (raw.contains("T")) raw = raw.replace("T", " ");
            java.util.Date date = input.parse(raw.length() > 19 ? raw.substring(0, 19) : raw);
            if (date != null) {
                return new java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault()).format(date);
            }
        } catch (Exception ignored) {}
        return raw;
    }

    // ── Fetch unread notification count → update bell badge ───────
    private void fetchUnreadCount() {
        if (employeeId == null || employeeId.isEmpty()) return;
        executor.execute(() -> {
            try {
                String urlStr = SERVER_BASE
                        + "/VTracker/getnotifications.jsp?empcode=" + employeeId;
                HttpURLConnection conn =
                        (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject root  = new JSONObject(sb.toString());
                int unread       = root.optInt("unread", 0);

                runOnUiThread(() -> {
                    if (unread > 0) {
                        String label = unread > 99 ? "99+" : String.valueOf(unread);
                        // Bell badge
                        if (tvNotifBadge != null) {
                            tvNotifBadge.setText(label);
                            tvNotifBadge.setVisibility(View.VISIBLE);
                        }
                        // Drawer badge
                        if (tvDrawerNotifBadge != null) tvDrawerNotifBadge.setText(label);
                        if (cardDrawerNotifBadge != null) cardDrawerNotifBadge.setVisibility(View.VISIBLE);
                    } else {
                        if (tvNotifBadge != null) tvNotifBadge.setVisibility(View.GONE);
                        if (cardDrawerNotifBadge != null) cardDrawerNotifBadge.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                Log.e("FacultyActivity", "fetchUnreadCount error: " + e.getMessage());
            }
        });
    }

    // ── Reverse geocode lat/lng → place name via OpenStreetMap ────
    private void reverseGeocode(String lat, String lng, TextView target) {
        geocodeExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = SERVER_BASE + "/VTracker/geocode.jsp"
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
                Log.d("FacultyActivity", "Geocode: '" + place + "' for " + lat + "," + lng);

                if (!place.isEmpty()) {
                    runOnUiThread(() -> { if (target != null) target.setText(place); });
                }
            } catch (Exception e) {
                Log.e("FacultyActivity", "Geocode failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ── buildImageUrl — images from server are already full URLs ──
    private String buildImageUrl(String path) {
        if (path == null || path.isEmpty() || path.equals("-") || path.equalsIgnoreCase("null")) return "";
        // Full URL — return as-is (post_images table stores complete http:// URLs)
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        // Base64 — return as-is
        if (path.startsWith("data:image")) return path;
        // Relative path — prepend server
        String clean = path.replace("\\", "/");
        if (!clean.startsWith("/")) clean = "/" + clean;
        return SERVER_BASE + clean;
    }

    // ── Empty state ────────────────────────────────────────────────
    private void showNoActivity() {
        cardActivity1.setVisibility(View.GONE);
        cardActivity2.setVisibility(View.GONE);
        layoutNoActivity.setVisibility(View.VISIBLE);
    }

    // ── Format time ────────────────────────────────────────────────
    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            // Handle full timestamp by taking just the date part
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            java.util.Date date  = sdf.parse(datePart);
            java.util.Date today = new java.util.Date();
            long diffDays = (today.getTime() - date.getTime()) / (1000 * 60 * 60 * 24);
            if (diffDays == 0) return "TODAY";
            if (diffDays == 1) return "YESTERDAY";
            if (diffDays < 7)  return diffDays + " DAYS AGO";
            return datePart;
        } catch (Exception e) {
            return raw.length() > 10 ? raw.substring(0, 10) : raw;
        }
    }

    // ── Avatar letter (skip honorifics) ───────────────────────────
    private String getAvatarLetter(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "F";
        String cleaned = fullName
                .replaceAll("(?i)^(Mr\\.|Mrs\\.|Ms\\.|Dr\\.|Prof\\.|Er\\.|Er\\s)\\s*", "")
                .trim();
        return String.valueOf(
                (!cleaned.isEmpty() ? cleaned : fullName).charAt(0)
        ).toUpperCase();
    }

    // ── Drawer helpers ─────────────────────────────────────────────
    private void openDrawer()  { drawerLayout.openDrawer(sideDrawer); }
    private void closeDrawer() { drawerLayout.closeDrawer(sideDrawer); }

    // ── Navigation ─────────────────────────────────────────────────
    private void openPostVisit() {
        closeDrawer();
        Intent i = new Intent(this, PostVisitActivity.class);
        i.putExtra("EMPLOYEE_ID", employeeId);
        i.putExtra("USER_NAME", userName);
        startActivity(i);
    }

    private void openProfile() {
        closeDrawer();
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra("USER_NAME", userName);
        i.putExtra("EMPLOYEE_ID", employeeId);
        startActivity(i);
    }

    private void openHistory() {
        closeDrawer();
        Intent i = new Intent(this, HistoryActivity.class);
        i.putExtra("EMPLOYEE_ID", employeeId);
        i.putExtra("USER_NAME", userName);
        startActivity(i);
    }

    // Static flag set by NotificationsActivity when user views notifications
    static boolean sNotificationsRead = false;

    private void openNotifications() {
        closeDrawer();
        Intent i = new Intent(this, NotificationsActivity.class);
        i.putExtra("EMPLOYEE_ID", employeeId);
        i.putExtra("USER_NAME", userName);
        startActivity(i);
    }

    // ── Logout ─────────────────────────────────────────────────────
    private void confirmLogout() {
        closeDrawer();
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (d, w) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE)
                .edit().clear().apply();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ── Back press ─────────────────────────────────────────────────
    private void handleBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(sideDrawer)) {
                    closeDrawer();
                } else {
                    Toast.makeText(FacultyActivity.this,
                            "Please use Profile › Logout to exit.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ── All click listeners ────────────────────────────────────────
    private void setListeners() {
        ivMenu.setOnClickListener(v -> openDrawer());
        ivNotification.setOnClickListener(v -> openNotifications());

        cardPostVisit.setOnClickListener(v -> openPostVisit());
        cardViewPosts.setOnClickListener(v -> openHistory());
        cardProfile.setOnClickListener(v   -> openProfile());
        tvSeeAll.setOnClickListener(v      -> openHistory());

        navHome.setOnClickListener(v ->
                Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show());
        navPost.setOnClickListener(v    -> openPostVisit());
        navHistory.setOnClickListener(v -> openHistory());
        navExpenses.setOnClickListener(v -> {
            Intent ei = new Intent(this, ExpensesActivity.class);
            ei.putExtra("EMPLOYEE_ID", employeeId);
            ei.putExtra("USER_NAME", userName);
            startActivity(ei);
        });
        navProfile.setOnClickListener(v -> openProfile());

        drawerHeaderProfile.setOnClickListener(v -> openProfile());
        drawerHome.setOnClickListener(v -> {
            closeDrawer();
            Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show();
        });
        drawerPost.setOnClickListener(v          -> openPostVisit());
        drawerHistory.setOnClickListener(v       -> openHistory());
        drawerExpenses.setOnClickListener(v -> {
            closeDrawer();
            Intent ei = new Intent(this, ExpensesActivity.class);
            ei.putExtra("EMPLOYEE_ID", employeeId);
            ei.putExtra("USER_NAME", userName);
            startActivity(ei);
        });
        drawerProfile.setOnClickListener(v       -> openProfile());
        drawerNotifications.setOnClickListener(v -> openNotifications());
        drawerLogout.setOnClickListener(v        -> confirmLogout());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        geocodeExecutor.shutdownNow();
    }
}
