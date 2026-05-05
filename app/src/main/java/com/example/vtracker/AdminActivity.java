package com.example.vtracker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminActivity extends BaseActivity {

    private static final String TAG           = "AdminActivity";
    private static final String ALL_POSTS_API = "http://160.187.169.24/VTracker/getallposts.jsp";
    private static final String SERVER_BASE   = "http://160.187.169.24";
    private static final String PHOTO_BASE_IP = "http://160.187.169.24";

    private DrawerLayout       drawerLayout;
    private LinearLayout       sideDrawer;
    private SwipeRefreshLayout swipeRefreshLayout;

    private TextView     tvGreeting, tvAvatarLetter;
    private TextView     tvDrawerName, tvDrawerAvatarLetter, tvDrawerRole;
    private TextView     tvTotalPosts, tvPendingCount, tvViewAll;
    private LinearLayout containerPosts, layoutEmptyState;
    private FrameLayout  loadingOverlay;

    private LinearLayout drawerHeaderProfile, drawerDashboard, drawerSearch,
            drawerAllPosts, drawerReports, drawerApprovals, drawerAddFaculty,
            drawerProfile, drawerLogout;
    private LinearLayout navDashboard, navSearch, navApprovals, navAddUsers, navProfile;

    private String adminName = "";
    private String adminId   = "";

    // FIX: track whether we already loaded data this session
    private volatile boolean dataLoadedSuccessfully = false;

    private final AtomicInteger  fetchToken = new AtomicInteger(0);
    private final ExecutorService executor  = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_admin);
        initViews();
        loadAdminData();
        setListeners();
        // FIX: Always load on create — this covers login → dashboard
        loadDashboardData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // FIX: Only auto-reload if previous load failed or no data shown yet
        // This prevents double-fetch on create AND fixes the "blank on login" issue
        if (!dataLoadedSuccessfully) {
            loadDashboardData();
        }
    }

    private void initViews() {
        drawerLayout         = findViewById(R.id.drawerLayout);
        sideDrawer           = findViewById(R.id.sideDrawer);
        swipeRefreshLayout   = findViewById(R.id.swipeRefreshLayout);
        tvGreeting           = findViewById(R.id.tvGreeting);
        tvAvatarLetter       = findViewById(R.id.tvAvatarLetter);
        tvDrawerName         = findViewById(R.id.tvDrawerName);
        tvDrawerAvatarLetter = findViewById(R.id.tvDrawerAvatarLetter);
        tvDrawerRole         = findViewById(R.id.tvDrawerRole);
        tvTotalPosts         = findViewById(R.id.tvTotalPosts);
        tvPendingCount       = findViewById(R.id.tvPendingCount);
        tvViewAll            = findViewById(R.id.tvViewAll);
        containerPosts       = findViewById(R.id.containerPosts);
        layoutEmptyState     = findViewById(R.id.layoutEmptyState);
        loadingOverlay       = findViewById(R.id.loadingOverlay);

        drawerHeaderProfile  = findViewById(R.id.drawerHeaderProfile);
        drawerDashboard      = findViewById(R.id.drawerDashboard);
        drawerSearch         = findViewById(R.id.drawerSearch);
        drawerAllPosts       = findViewById(R.id.drawerAllPosts);
        drawerReports        = findViewById(R.id.drawerReports);
        drawerApprovals      = findViewById(R.id.drawerApprovals);
        drawerAddFaculty     = findViewById(R.id.drawerAddFaculty);
        drawerProfile        = findViewById(R.id.drawerProfile);
        drawerLogout         = findViewById(R.id.drawerLogout);
        navDashboard         = findViewById(R.id.navDashboard);
        navSearch            = findViewById(R.id.navSearch);
        navApprovals         = findViewById(R.id.navApprovals);
        navAddUsers          = findViewById(R.id.navAddUsers);
        navProfile           = findViewById(R.id.navProfile);

        findViewById(R.id.ivMenu).setOnClickListener(v -> openDrawer());

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1A73E8"));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // FIX: Manual swipe-refresh always forces a reload
                dataLoadedSuccessfully = false;
                loadDashboardData();
            });
        }
    }

    private void loadAdminData() {
        adminName = getIntent().getStringExtra("USER_NAME");
        adminId   = getIntent().getStringExtra("EMPLOYEE_ID");
        if (adminName == null || adminName.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
            adminName = prefs.getString(LoginActivity.KEY_USER_NAME, "Admin");
            adminId   = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
        }
        String display = (adminName != null && !adminName.isEmpty()) ? adminName : "Admin";
        String letter  = String.valueOf(display.charAt(0)).toUpperCase();
        tvGreeting.setText(String.format(Locale.getDefault(), "Hello, %s", display));
        tvAvatarLetter.setText(letter);
        tvDrawerName.setText(display);
        tvDrawerAvatarLetter.setText(letter);
        tvDrawerRole.setText((adminId != null && !adminId.isEmpty()) ? String.format(Locale.getDefault(), "ID: %s", adminId) : "Administrator");
    }

    private void setListeners() {
        tvViewAll.setOnClickListener(v -> openSearch());
        navDashboard.setOnClickListener(v -> {
            dataLoadedSuccessfully = false;
            loadDashboardData();
        });
        navSearch.setOnClickListener(v    -> openSearch());
        navApprovals.setOnClickListener(v -> openApprovals());
        navAddUsers.setOnClickListener(v  -> openAddFaculty());
        navProfile.setOnClickListener(v   -> openProfile());
        drawerHeaderProfile.setOnClickListener(v -> openProfile());
        drawerDashboard.setOnClickListener(v  -> {
            closeDrawer();
            dataLoadedSuccessfully = false;
            loadDashboardData();
        });
        drawerSearch.setOnClickListener(v     -> openSearch());
        drawerAllPosts.setOnClickListener(v   -> openSearch());
        drawerReports.setOnClickListener(v    -> { closeDrawer(); Toast.makeText(this, "Reports coming soon", Toast.LENGTH_SHORT).show(); });
        drawerApprovals.setOnClickListener(v  -> openApprovals());
        drawerAddFaculty.setOnClickListener(v -> openAddFaculty());
        drawerProfile.setOnClickListener(v    -> openProfile());
        drawerLogout.setOnClickListener(v     -> showLogoutDialog());
    }

    private void openDrawer()  { drawerLayout.openDrawer(sideDrawer); }
    private void closeDrawer() { drawerLayout.closeDrawer(sideDrawer); }

    private void openSearch() {
        closeDrawer();
        Intent i = new Intent(this, AdminSearchActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    private void openAddFaculty() {
        closeDrawer();
        Intent i = new Intent(this, AddFacultyActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    private void openApprovals() {
        closeDrawer();
        Intent i = new Intent(this, ApprovalsActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    private void openProfile() {
        closeDrawer();
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    private void loadDashboardData() {
        // FIX: Capture token immediately so any running fetch is invalidated
        final int myToken = fetchToken.incrementAndGet();

        showLoadingState();

        executor.execute(() -> {
            // FIX: Check token right after scheduling (before network call)
            if (fetchToken.get() != myToken) return;

            try {
                final String json = httpGet(ALL_POSTS_API);

                // FIX: Check token after network call returns
                if (fetchToken.get() != myToken) return;

                runOnUiThread(() -> {
                    // FIX: Check token again inside UI thread callback
                    if (fetchToken.get() != myToken) return;
                    dismissLoadingState();
                    renderResults(json);
                });
            } catch (Exception e) {
                Log.e(TAG, "loadDashboardData error", e);
                if (fetchToken.get() != myToken) return;
                runOnUiThread(() -> {
                    if (fetchToken.get() != myToken) return;
                    dismissLoadingState();
                    // FIX: Mark as not loaded so onResume will retry
                    dataLoadedSuccessfully = false;
                    showErrorState("Network error. Please try again.");
                });
            }
        });
    }

    private void showLoadingState() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.post(() -> swipeRefreshLayout.setRefreshing(true));
        }
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (containerPosts   != null) containerPosts.removeAllViews();
    }

    private void dismissLoadingState() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    private void showErrorState(String msg) {
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void renderResults(String json) {
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) {
            containerPosts.removeAllViews();
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            tvTotalPosts.setText("0");
            tvPendingCount.setText("0");
            // FIX: Empty result is still a valid "loaded" state
            dataLoadedSuccessfully = true;
            return;
        }

        try {
            JSONArray array = parseToArray(json);
            containerPosts.removeAllViews();

            if (array == null || array.length() == 0) {
                if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
                tvTotalPosts.setText("0");
                tvPendingCount.setText("0");
                dataLoadedSuccessfully = true;
                return;
            }

            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);

            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getJSONObject(i));
            }

            Collections.sort(list, (a, b) -> {
                String ta = a.optString("time", a.optString("created_at", ""));
                String tb = b.optString("time", b.optString("created_at", ""));
                return tb.compareTo(ta);
            });

            tvTotalPosts.setText(String.valueOf(list.size()));

            int pending = 0;
            LayoutInflater inflater = LayoutInflater.from(this);
            for (JSONObject obj : list) {
                String st = obj.optString("status", "");
                if (st.isEmpty() || st.equalsIgnoreCase("null") || st.equalsIgnoreCase("pending"))
                    pending++;
                View card = inflater.inflate(R.layout.item_admin_card, containerPosts, false);
                bindAdminCard(card, obj);
                containerPosts.addView(card);
            }
            tvPendingCount.setText(String.valueOf(pending));

            // FIX: Mark success so onResume won't re-fetch unnecessarily
            dataLoadedSuccessfully = true;

        } catch (Exception e) {
            Log.e(TAG, "renderResults error", e);
            dataLoadedSuccessfully = false;
            showErrorState("Data processing error.");
        }
    }

    private void bindAdminCard(View card, JSONObject obj) {
        String empcode = obj.optString("empcode", "");
        String empName = extractEmpName(obj);
        String trip    = extractTripName(obj);
        String desc    = obj.optString("description", obj.optString("textbox", ""));
        String lat     = obj.optString("latitude", "");
        String lng     = obj.optString("longitude", "");
        String time    = obj.optString("time", obj.optString("created_at", ""));
        String status  = obj.optString("status", "");

        List<String> imgs = new ArrayList<>();
        JSONArray imgArr  = obj.optJSONArray("images");
        if (imgArr != null) {
            for (int j = 0; j < imgArr.length(); j++) imgs.add(imgArr.optString(j));
        }

        ImageView   ivPhoto       = card.findViewById(R.id.ivFacultyPhoto);
        TextView    tvName        = card.findViewById(R.id.tvFacultyName);
        TextView    tvDept        = card.findViewById(R.id.tvFacultyDept);
        TextView    tvStatus      = card.findViewById(R.id.tvStatus);
        TextView    tvTrip        = card.findViewById(R.id.tvTripName);
        TextView    tvCardTime    = card.findViewById(R.id.tvTime);
        FrameLayout layoutPreview = card.findViewById(R.id.layoutImagePreview);
        ImageView   ivPreview     = card.findViewById(R.id.ivPreview);
        TextView    tvImgCount    = card.findViewById(R.id.tvImageCount);

        String paddedCode = empcode;
        try {
            paddedCode = String.format(Locale.getDefault(), "%05d", Integer.parseInt(empcode.replaceAll("\\D", "")));
        } catch (Exception ignored) {}

        if (!empName.isEmpty()) {
            tvName.setText(empName);
            tvDept.setText(String.format(Locale.getDefault(), "Emp ID: FAC-%s", paddedCode));
        } else {
            tvName.setText(String.format(Locale.getDefault(), "Employee: %s", paddedCode));
            tvDept.setText(String.format(Locale.getDefault(), "Emp ID: FAC-%s", paddedCode));
        }

        if (status.isEmpty() || status.equalsIgnoreCase("null")) status = "PENDING";
        tvStatus.setText(status.toUpperCase(Locale.getDefault()));
        tvStatus.getBackground().setTint(Color.parseColor(getStatusColor(status)));

        String photoUrl = PHOTO_BASE_IP + "/counselling_jspapi/StaffPhotos/" + paddedCode + ".JPG";
        Glide.with(this)
                .load(photoUrl)
                .apply(new RequestOptions()
                        .transform(new CircleCrop())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(android.R.drawable.ic_menu_myplaces)
                        .error(android.R.drawable.ic_menu_myplaces))
                .into(ivPhoto);

        String tripDisp = !trip.isEmpty() ? trip : "(No Trip Name)";
        tvTrip.setText(tripDisp);
        tvCardTime.setText(formatTime(time));

        if (!imgs.isEmpty()) {
            layoutPreview.setVisibility(View.VISIBLE);
            String fImg = imgs.get(0);
            if (!fImg.startsWith("http")) fImg = SERVER_BASE + (fImg.startsWith("/") ? "" : "/") + fImg;
            Glide.with(this).load(fImg).centerCrop().into(ivPreview);
            tvImgCount.setVisibility(imgs.size() > 1 ? View.VISIBLE : View.GONE);
            if (imgs.size() > 1) tvImgCount.setText(String.format(Locale.getDefault(), "+%d photos", imgs.size() - 1));
        } else {
            layoutPreview.setVisibility(View.GONE);
        }

        final String fStatus = status;
        final String fTrip   = tripDisp;
        final String fTime   = time;
        final String fDesc   = desc;
        final ArrayList<String> fImgs = new ArrayList<>(imgs);

        card.setOnClickListener(v -> {
            Intent i = new Intent(this, AdminDetailActivity.class);
            i.putExtra("type", "history");
            i.putExtra("tripName", fTrip);
            i.putExtra("location", lat + ", " + lng);
            i.putExtra("dateTime", formatTime(fTime));
            i.putExtra("status", fStatus);
            i.putExtra("description", fDesc);
            i.putStringArrayListExtra("images", fImgs);
            startActivity(i);
        });

        card.findViewById(R.id.btnViewMap).setOnClickListener(v -> {
            try {
                Intent m = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(String.format(Locale.getDefault(), "geo:%s,%s?q=%s,%s", lat, lng, lat, lng)));
                m.setPackage("com.google.android.apps.maps");
                if (m.resolveActivity(getPackageManager()) != null) startActivity(m);
                else startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(String.format(Locale.getDefault(), "https://maps.google.com/?q=%s,%s", lat, lng))));
            } catch (Exception e) {
                Toast.makeText(this, "Maps error", Toast.LENGTH_SHORT).show();
            }
        });

        CardView btnApprove = card.findViewById(R.id.btnApprove);
        TextView tvAppLabel = card.findViewById(R.id.tvApproveLabel);

        if (fStatus.equalsIgnoreCase("approved") || fStatus.equalsIgnoreCase("completed")) {
            btnApprove.setCardBackgroundColor(Color.parseColor("#34A853"));
            tvAppLabel.setText("Approved ✓");
            btnApprove.setEnabled(false);
        } else {
            btnApprove.setEnabled(true);
            final String fCode = empcode;
            final String fId   = obj.optString("id", obj.optString("visit_id", obj.optString("gps_id", "")));
            btnApprove.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Approve")
                            .setMessage("Approve this visit?")
                            .setPositiveButton("Yes", (d, w) -> callApproveApi(fId, fCode, btnApprove, tvAppLabel))
                            .setNegativeButton("No", null)
                            .show());
        }

        card.findViewById(R.id.ivDelete).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Delete")
                        .setMessage("Delete this visit?")
                        .setPositiveButton("Delete", (d, w) -> callDeletePostApi(empcode, fTime, card))
                        .setNegativeButton("Cancel", null)
                        .show());
    }

    private void callApproveApi(String id, String code, CardView btn, TextView tv) {
        btn.setEnabled(false);
        tv.setText("...");
        executor.execute(() -> {
            try {
                String r = httpGet(SERVER_BASE + "/VTracker/approvepost.jsp?id=" + id
                        + "&empcode=" + code + "&status=approved");
                runOnUiThread(() -> {
                    if (r != null && (r.contains("success") || r.contains("true"))) {
                        btn.setCardBackgroundColor(Color.parseColor("#34A853"));
                        tv.setText("Approved ✓");
                    } else {
                        btn.setEnabled(true);
                        tv.setText("Approve");
                        Toast.makeText(this, "Approval failed.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    tv.setText("Approve");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void callDeletePostApi(String code, String time, View card) {
        executor.execute(() -> {
            try {
                String r = httpGet(SERVER_BASE + "/VTracker/deletepost.jsp?empcode=" + code
                        + "&time=" + URLEncoder.encode(time, "UTF-8") + "&admin_id=" + adminId);
                if (r != null && (r.contains("success") || r.contains("deleted") || r.contains("true"))) {
                    runOnUiThread(() -> {
                        containerPosts.removeView(card);
                        Toast.makeText(this, "Deleted successfully.", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Delete failed.", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String extractTripName(JSONObject obj) {
        String v = obj.optString("trip_name", "").trim();
        if (!v.isEmpty() && !v.equalsIgnoreCase("null")) return v;
        return "";
    }

    private String extractEmpName(JSONObject obj) {
        for (String k : new String[]{"name", "empname", "emp_name", "employee_name", "EmpName"}) {
            String v = obj.optString(k, "").trim();
            if (!v.isEmpty() && !v.equalsIgnoreCase("null")) return v;
        }
        return "";
    }

    private JSONArray parseToArray(String json) {
        try {
            String t = json.trim();
            if (t.startsWith("[")) return new JSONArray(t);
            JSONObject w = new JSONObject(t);
            for (String k : new String[]{"data", "posts", "records"}) {
                if (w.has(k)) return w.getJSONArray(k);
            }
            Iterator<String> keys = w.keys();
            while (keys.hasNext()) {
                Object v = w.get(keys.next());
                if (v instanceof JSONArray) return (JSONArray) v;
            }
        } catch (Exception e) {
            Log.e(TAG, "parseToArray error", e);
        }
        return new JSONArray();
    }

    private String getStatusColor(String s) {
        if (s == null) return "#8A93B2";
        s = s.toLowerCase();
        if (s.contains("approved") || s.contains("completed")) return "#34A853";
        if (s.contains("pending"))  return "#FB8C00";
        if (s.contains("rejected")) return "#E53935";
        return "#8A93B2";
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date d = in.parse(raw);
            return d != null ? new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(d) : raw;
        } catch (Exception e) {
            return raw;
        }
    }

    private String httpGet(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        try {
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) {
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder b = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) b.append(l);
                String response = b.toString().trim();
                return response.isEmpty() ? "[]" : response;
            }
            return "[]";
        } finally {
            c.disconnect();
        }
    }

    private void showLogoutDialog() {
        closeDrawer();
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure?")
                .setPositiveButton("Logout", (d, w) -> {
                    LoginActivity.clearSession(this);
                    startActivity(new Intent(this, LoginActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
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
}