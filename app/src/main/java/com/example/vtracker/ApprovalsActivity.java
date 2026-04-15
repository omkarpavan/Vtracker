package com.example.vtracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApprovalsActivity extends BaseActivity {

    private static final String TAG = "ApprovalsActivity";

    // ── API — UNCHANGED ────────────────────────────────────────────
    private static final String ALL_POSTS_API = "http://160.187.169.14/jspapi/gps/getallposts.jsp";
    private static final String SERVER_BASE   = "http://160.187.169.14";
    private static final String PHOTO_BASE_IP = "http://160.187.169.24";

    // ── UI ─────────────────────────────────────────────────────────
    private DrawerLayout drawerLayout;
    private LinearLayout sideDrawer;

    private TextView     tvDrawerName, tvDrawerAvatarLetter, tvDrawerRole;
    private TextView     tvApprovedBadge, tvStatApproved, tvStatTotal, tvStatPending;
    private TextView     tvError;
    private LinearLayout containerCards, layoutEmptyState;
    private FrameLayout  loadingOverlay;

    // Drawer items
    private LinearLayout drawerHeaderProfile, drawerDashboard, drawerSearch,
            drawerAllPosts, drawerReports, drawerApprovals,
            drawerProfile, drawerLogout;

    // Bottom nav
    private LinearLayout navDashboard, navSearch, navApprovals, navProfile;

    // ── Data ───────────────────────────────────────────────────────
    private String adminName = "";
    private String adminId   = "";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ── onCreate ───────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_admin_approvals);

        initViews();
        loadAdminData();
        setListeners();
        fetchAndFilterApproved();
    }

    // ── Bind views ─────────────────────────────────────────────────
    private void initViews() {
        drawerLayout         = findViewById(R.id.drawerLayout);
        sideDrawer           = findViewById(R.id.sideDrawer);
        tvDrawerName         = findViewById(R.id.tvDrawerName);
        tvDrawerAvatarLetter = findViewById(R.id.tvDrawerAvatarLetter);
        tvDrawerRole         = findViewById(R.id.tvDrawerRole);
        tvApprovedBadge      = findViewById(R.id.tvApprovedBadge);
        tvStatApproved       = findViewById(R.id.tvStatApproved);
        tvStatTotal          = findViewById(R.id.tvStatTotal);
        tvStatPending        = findViewById(R.id.tvStatPending);
        tvError              = findViewById(R.id.tvError);
        containerCards       = findViewById(R.id.containerCards);
        layoutEmptyState     = findViewById(R.id.layoutEmptyState);
        loadingOverlay       = findViewById(R.id.loadingOverlay);
        drawerHeaderProfile  = findViewById(R.id.drawerHeaderProfile);
        drawerDashboard      = findViewById(R.id.drawerDashboard);
        drawerSearch         = findViewById(R.id.drawerSearch);
        drawerAllPosts       = findViewById(R.id.drawerAllPosts);
        drawerReports        = findViewById(R.id.drawerReports);
        drawerApprovals      = findViewById(R.id.drawerApprovals);
        drawerProfile        = findViewById(R.id.drawerProfile);
        drawerLogout         = findViewById(R.id.drawerLogout);
        navDashboard         = findViewById(R.id.navDashboard);
        navSearch            = findViewById(R.id.navSearch);
        navApprovals         = findViewById(R.id.navApprovals);
        navProfile           = findViewById(R.id.navProfile);

        // Hamburger menu
        findViewById(R.id.ivMenu).setOnClickListener(v -> openDrawer());
    }

    // ── Load admin name ────────────────────────────────────────────
    private void loadAdminData() {
        adminName = getIntent().getStringExtra("USER_NAME");
        adminId   = getIntent().getStringExtra("EMPLOYEE_ID");

        if (adminName == null || adminName.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE);
            adminName = prefs.getString(LoginActivity.KEY_USER_NAME,   "Admin");
            adminId   = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
        }

        String display = (adminName != null && !adminName.isEmpty()) ? adminName : "Admin";
        String letter  = String.valueOf(display.charAt(0)).toUpperCase();

        tvDrawerName.setText(display);
        tvDrawerAvatarLetter.setText(letter);
        tvDrawerRole.setText(adminId != null && !adminId.isEmpty()
                ? "ID: " + adminId : "Administrator");
    }

    // ── Drawer ─────────────────────────────────────────────────────
    private void openDrawer()  { drawerLayout.openDrawer(sideDrawer); }
    private void closeDrawer() { drawerLayout.closeDrawer(sideDrawer); }

    // ── All listeners ──────────────────────────────────────────────
    private void setListeners() {

        // Bottom nav
        navDashboard.setOnClickListener(v -> goToDashboard());
        navSearch.setOnClickListener(v    -> goToSearch());
        navApprovals.setOnClickListener(v -> { /* already here */ });
        navProfile.setOnClickListener(v   -> goToProfile());

        // Drawer header
        drawerHeaderProfile.setOnClickListener(v -> { closeDrawer(); goToProfile(); });

        // Drawer items
        drawerDashboard.setOnClickListener(v -> goToDashboard());
        drawerSearch.setOnClickListener(v    -> goToSearch());
        drawerAllPosts.setOnClickListener(v  -> goToSearch());
        drawerReports.setOnClickListener(v   -> {
            closeDrawer();
            Toast.makeText(this, "Reports coming soon", Toast.LENGTH_SHORT).show();
        });
        drawerApprovals.setOnClickListener(v -> {
            closeDrawer();
            Toast.makeText(this, "Already on Approvals", Toast.LENGTH_SHORT).show();
        });
        drawerProfile.setOnClickListener(v  -> { closeDrawer(); goToProfile(); });
        drawerLogout.setOnClickListener(v   -> showLogoutDialog());
    }

    // ── Navigation helpers ─────────────────────────────────────────
    private void goToDashboard() {
        closeDrawer();
        Intent i = new Intent(this, AdminActivity.class);
        i.putExtra("USER_NAME",   adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void goToSearch() {
        closeDrawer();
        Intent i = new Intent(this, AdminSearchActivity.class);
        i.putExtra("USER_NAME",   adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    private void goToProfile() {
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra("USER_NAME",   adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    // ══════════════════════════════════════════════════════════════
    //  Fetch ALL posts then filter approved client-side
    // ══════════════════════════════════════════════════════════════
    private void fetchAndFilterApproved() {
        loadingOverlay.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        containerCards.removeAllViews();

        executor.execute(() -> {
            try {
                String json = httpGet(ALL_POSTS_API);   // ← same endpoint, UNCHANGED
                Log.d(TAG, "Response length: " + (json != null ? json.length() : 0));
                runOnUiThread(() -> processJson(json));
            } catch (Exception e) {
                Log.e(TAG, "Fetch error: " + e.getMessage());
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    showError("Network error: " + e.getMessage());
                });
            }
        });
    }

    private void processJson(String json) {
        loadingOverlay.setVisibility(View.GONE);

        if (json == null || json.trim().isEmpty()) {
            showError("No data received from server.");
            return;
        }

        try {
            JSONArray all;
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                all = new JSONArray(trimmed);
            } else {
                JSONObject wrapper = new JSONObject(trimmed);
                String key = wrapper.keys().next();
                all = wrapper.getJSONArray(key);
            }

            // ── Filter: keep only approved/completed posts ────────
            List<JSONObject> approved = new ArrayList<>();
            int totalPending = 0;

            for (int i = 0; i < all.length(); i++) {
                JSONObject post   = all.getJSONObject(i);
                String status     = post.optString("status", "").trim().toLowerCase();

                if (status.contains("approved") || status.contains("completed")) {
                    approved.add(post);
                } else if (status.contains("pending") || status.isEmpty()) {
                    totalPending++;
                }
            }

            // Update stats
            tvStatTotal.setText(String.valueOf(all.length()));
            tvStatApproved.setText(String.valueOf(approved.size()));
            tvStatPending.setText(String.valueOf(totalPending));
            tvApprovedBadge.setText(approved.size() + " approved");

            if (approved.isEmpty()) {
                layoutEmptyState.setVisibility(View.VISIBLE);
                return;
            }

            // Inflate approved cards
            LayoutInflater inflater = LayoutInflater.from(this);
            for (JSONObject post : approved) {
                String empcode = post.optString("empcode",     "");
                String desc    = post.optString("description", "");
                String lat     = post.optString("latitude",    "");
                String lng     = post.optString("longitude",   "");
                String time    = post.optString("time",        "");

                List<String> imgs = new ArrayList<>();
                JSONArray imgArr  = post.optJSONArray("images");
                if (imgArr != null) {
                    for (int j = 0; j < imgArr.length(); j++)
                        imgs.add(imgArr.getString(j));
                }

                View card = inflater.inflate(
                        R.layout.item_approved_card, containerCards, false);
                bindApprovedCard(card, empcode, desc, lat, lng, time, imgs);
                containerCards.addView(card);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            showError("Error reading response: " + e.getMessage());
        }
    }

    // ── Bind one approved card ─────────────────────────────────────
    private void bindApprovedCard(View card, String empcode, String description,
                                  String latitude, String longitude,
                                  String time, List<String> imageUrls) {

        ImageView    ivPhoto    = card.findViewById(R.id.ivCardFacultyPhoto);
        TextView     tvName     = card.findViewById(R.id.tvCardFacultyName);
        TextView     tvDept     = card.findViewById(R.id.tvCardDept);
        TextView     tvLocation = card.findViewById(R.id.tvCardLocation);
        TextView     tvDesc     = card.findViewById(R.id.tvCardDescription);
        TextView     tvTime     = card.findViewById(R.id.tvCardTime);
        TextView     tvTimestamp = card.findViewById(R.id.tvCardTimestamp);
        ViewPager2   pager      = card.findViewById(R.id.viewPagerImages);
        LinearLayout dots       = card.findViewById(R.id.layoutDots);
        CardView     btnMap     = card.findViewById(R.id.btnViewMap);

        // Pad empcode — UNCHANGED
        String paddedCode = empcode;
        try {
            paddedCode = String.format("%05d",
                    Integer.parseInt(empcode.replaceAll("\\D", "")));
        } catch (NumberFormatException ignored) {}

        tvName.setText(paddedCode);
        tvDept.setText("Emp ID: FAC-" + paddedCode);

        // Faculty photo — UNCHANGED
        String photoUrl = PHOTO_BASE_IP + "/counselling_jspapi/StaffPhotos/"
                + paddedCode + ".JPG";
        Glide.with(this)
                .load(photoUrl)
                .apply(new RequestOptions()
                        .transform(new CircleCrop())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(android.R.drawable.ic_menu_myplaces)
                        .error(android.R.drawable.ic_menu_myplaces))
                .into(ivPhoto);

        // Images — UNCHANGED
        if (!imageUrls.isEmpty()) {
            pager.setVisibility(View.VISIBLE);
            pager.setAdapter(new ImagePagerAdapter(imageUrls));
            if (imageUrls.size() > 1) {
                dots.setVisibility(View.VISIBLE);
                setupDots(dots, imageUrls.size(), 0);
                pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override public void onPageSelected(int pos) {
                        setupDots(dots, imageUrls.size(), pos);
                    }
                });
            }
        } else {
            pager.setVisibility(View.GONE);
            dots.setVisibility(View.GONE);
        }

        tvDesc.setText(description.isEmpty() ? "(No description)" : description);

        String formatted = formatTime(time);
        tvTime.setText(formatted);
        tvTimestamp.setText(formatted);

        tvLocation.setText(latitude + ", " + longitude);
        reverseGeocode(latitude, longitude, tvLocation);

        // Map button — UNCHANGED
        btnMap.setOnClickListener(v -> {
            try {
                Intent mapIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:" + latitude + "," + longitude
                                + "?q=" + latitude + "," + longitude));
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://maps.google.com/?q="
                                    + latitude + "," + longitude)));
                }
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open map.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void setupDots(LinearLayout layout, int count, int active) {
        layout.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            int size = dpToPx(i == active ? 8 : 6);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            p.setMargins(4, 0, 4, 0);
            dot.setLayoutParams(p);
            dot.setBackground(getDrawable(android.R.drawable.presence_online));
            dot.getBackground().setTint(i == active ? 0xFF1A73E8 : 0xFFCCCCCC);
            layout.addView(dot);
        }
    }

    private void reverseGeocode(String lat, String lng, TextView target) {
        executor.execute(() -> {
            try {
                double dLat = Double.parseDouble(lat);
                double dLng = Double.parseDouble(lng);
                Geocoder geo = new Geocoder(this, Locale.getDefault());
                List<Address> list = geo.getFromLocation(dLat, dLng, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    StringBuilder sb = new StringBuilder();
                    if (a.getThoroughfare() != null) sb.append(a.getThoroughfare()).append(" • ");
                    if (a.getLocality()     != null) sb.append(a.getLocality());
                    String result = sb.toString().replaceAll(" • $", "").trim();
                    if (!result.isEmpty()) runOnUiThread(() -> target.setText(result));
                }
            } catch (Exception ignored) {}
        });
    }

    // formatTime — UNCHANGED
    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("MMM dd, yyyy • hh:mm a",  Locale.getDefault());
            Date d = in.parse(raw);
            return d != null ? out.format(d) : raw;
        } catch (ParseException e) { return raw; }
    }

    // httpGet — UNCHANGED
    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP " + code + " ← " + urlStr);
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString().trim();
            }
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // buildImageUrl — UNCHANGED
    private String buildImageUrl(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.replace("\\", "/");
        if (normalized.contains("localhost") || normalized.contains("127.0.0.1")
                || normalized.contains("192.168.")) {
            int slashIdx = normalized.indexOf("/", 8);
            if (slashIdx != -1) normalized = normalized.substring(slashIdx);
        }
        if (normalized.startsWith("http")) return normalized;
        String clean = normalized.startsWith("/") ? normalized : "/" + normalized;
        return SERVER_BASE + clean;
    }

    private void showError(String msg) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
        layoutEmptyState.setVisibility(View.GONE);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showLogoutDialog() {
        closeDrawer();
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (d, w) -> {
                    LoginActivity.clearSession(this);
                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(sideDrawer)) closeDrawer();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // Image pager adapter — UNCHANGED
    private class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.VH> {
        private final List<String> urls;
        ImagePagerAdapter(List<String> urls) { this.urls = urls; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Glide.with(ApprovalsActivity.this)
                    .load(buildImageUrl(urls.get(pos)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(h.iv);
        }

        @Override public int getItemCount() { return urls.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView iv;
            VH(ImageView iv) { super(iv); this.iv = iv; }
        }
    }
}