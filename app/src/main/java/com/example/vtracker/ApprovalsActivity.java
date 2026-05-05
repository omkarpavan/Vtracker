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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
import java.text.ParseException;
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

public class ApprovalsActivity extends BaseActivity {

    private static final String TAG = "ApprovalsActivity";

    private static final String ALL_POSTS_API    = "http://160.187.169.24/VTracker/getallposts.jsp";
    private static final String ALL_EXPENSES_API = "http://160.187.169.24/VTracker/getallexpenses.jsp";
    private static final String SERVER_BASE      = "http://160.187.169.24";
    private static final String PHOTO_BASE_IP    = "http://160.187.169.24";

    private static final String FILTER_ALL      = "all";
    private static final String FILTER_APPROVED = "approved";
    private static final String FILTER_PENDING  = "pending";

    private DrawerLayout       drawerLayout;
    private View               sideDrawer;
    private SwipeRefreshLayout swipeRefreshLayout;

    private TextView     tvDrawerName, tvDrawerAvatarLetter, tvDrawerRole;
    private TextView     tvApprovedBadge, tvStatApproved, tvStatTotal, tvStatPending;
    private TextView     tvStatTotalLabel, tvStatApprovedLabel, tvStatPendingLabel;
    private TextView     tvError, tvEmptyMsg;
    private LinearLayout containerCards, layoutEmptyState;
    private FrameLayout  loadingOverlay;

    private LinearLayout layoutStatTotal, layoutStatApproved, layoutStatPending;

    private View drawerHeaderProfile, drawerDashboard, drawerSearch, drawerApprovals, drawerLogout;
    private View navDashboard, navSearch, navApprovals, navAddUsers, navProfile;

    private LinearLayout tabVisits, tabExpenses;
    private TextView     tvTabVisits, tvTabExpenses;
    private View         indicatorVisits, indicatorExpenses;

    private String adminName     = "";
    private String adminId       = "";
    private String currentMode   = "visits";
    private String currentFilter = FILTER_ALL;

    // FIX: Track loaded state per mode to avoid double-fetch and blank-on-switch bugs
    private volatile boolean visitsLoaded   = false;
    private volatile boolean expensesLoaded = false;

    private final AtomicInteger fetchToken = new AtomicInteger(0);
    private final List<JSONObject> allDataList = new ArrayList<>();
    private final ExecutorService  executor    = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_admin_approvals);

        initViews();
        loadAdminData();
        setListeners();
        updateTabUI();
        updateStatHighlight();
        // FIX: Always fetch on create — covers first open and login redirect
        fetchData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // FIX: Only re-fetch if current mode wasn't loaded yet (e.g. after a failed load)
        boolean currentModeLoaded = "visits".equals(currentMode) ? visitsLoaded : expensesLoaded;
        if (!currentModeLoaded) {
            fetchData();
        }
    }

    private void initViews() {
        drawerLayout         = findViewById(R.id.drawerLayout);
        sideDrawer           = findViewById(R.id.sideDrawer);
        swipeRefreshLayout   = findViewById(R.id.swipeRefreshLayout);

        tvDrawerName         = findViewById(R.id.tvDrawerName);
        tvDrawerAvatarLetter = findViewById(R.id.tvDrawerAvatarLetter);
        tvDrawerRole         = findViewById(R.id.tvDrawerRole);
        tvApprovedBadge      = findViewById(R.id.tvApprovedBadge);
        tvStatApproved       = findViewById(R.id.tvStatApproved);
        tvStatTotal          = findViewById(R.id.tvStatTotal);
        tvStatPending        = findViewById(R.id.tvStatPending);
        tvStatTotalLabel     = findViewById(R.id.tvStatTotalLabel);
        tvStatApprovedLabel  = findViewById(R.id.tvStatApprovedLabel);
        tvStatPendingLabel   = findViewById(R.id.tvStatPendingLabel);
        tvError              = findViewById(R.id.tvError);
        tvEmptyMsg           = findViewById(R.id.tvEmptyMsg);
        containerCards       = findViewById(R.id.containerCards);
        layoutEmptyState     = findViewById(R.id.layoutEmptyState);
        loadingOverlay       = findViewById(R.id.loadingOverlay);

        layoutStatTotal    = findViewById(R.id.layoutStatTotal);
        layoutStatApproved = findViewById(R.id.layoutStatApproved);
        layoutStatPending  = findViewById(R.id.layoutStatPending);

        drawerHeaderProfile = findViewById(R.id.drawerHeaderProfile);
        drawerDashboard     = findViewById(R.id.drawerDashboard);
        drawerSearch        = findViewById(R.id.drawerSearch);
        drawerApprovals     = findViewById(R.id.drawerApprovals);
        drawerLogout        = findViewById(R.id.drawerLogout);

        navDashboard = findViewById(R.id.navDashboard);
        navSearch    = findViewById(R.id.navSearch);
        navApprovals = findViewById(R.id.navApprovals);
        navAddUsers  = findViewById(R.id.navAddUsers);
        navProfile   = findViewById(R.id.navProfile);

        tabVisits         = findViewById(R.id.tabVisits);
        tabExpenses       = findViewById(R.id.tabExpenses);
        tvTabVisits       = findViewById(R.id.tvTabVisits);
        tvTabExpenses     = findViewById(R.id.tvTabExpenses);
        indicatorVisits   = findViewById(R.id.indicatorVisits);
        indicatorExpenses = findViewById(R.id.indicatorExpenses);

        ImageView ivBack = findViewById(R.id.ivMenu);
        if (ivBack != null) {
            ivBack.setImageResource(R.drawable.ic_arrow_back);
            ivBack.setOnClickListener(v -> finish());
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1A73E8"));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // FIX: Force-refresh clears the loaded flag for current mode
                if ("visits".equals(currentMode)) visitsLoaded = false;
                else expensesLoaded = false;
                fetchData();
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
        String letter  = display.isEmpty() ? "A" : String.valueOf(display.charAt(0)).toUpperCase();
        if (tvDrawerName         != null) tvDrawerName.setText(display);
        if (tvDrawerAvatarLetter != null) tvDrawerAvatarLetter.setText(letter);
        if (tvDrawerRole         != null) tvDrawerRole.setText(
                adminId != null && !adminId.isEmpty() ? String.format(Locale.getDefault(), "ID: %s", adminId) : "Administrator");
    }

    private void setListeners() {
        if (navDashboard != null) navDashboard.setOnClickListener(v -> goToDashboard());
        if (navSearch    != null) navSearch.setOnClickListener(v    -> goToSearch());
        if (navApprovals != null) navApprovals.setOnClickListener(v -> {
            // FIX: Reset to visits tab and clear loaded state to force fresh fetch
            currentMode   = "visits";
            currentFilter = FILTER_ALL;
            visitsLoaded  = false;
            updateTabUI();
            updateStatHighlight();
            fetchData();
        });
        if (navAddUsers  != null) navAddUsers.setOnClickListener(v  -> goToAddFaculty());
        if (navProfile   != null) navProfile.setOnClickListener(v   -> goToProfile());

        if (drawerHeaderProfile != null) drawerHeaderProfile.setOnClickListener(v -> { closeDrawer(); goToProfile(); });
        if (drawerDashboard     != null) drawerDashboard.setOnClickListener(v     -> goToDashboard());
        if (drawerSearch        != null) drawerSearch.setOnClickListener(v        -> goToSearch());
        if (drawerLogout        != null) drawerLogout.setOnClickListener(v        -> showLogoutDialog());

        tabVisits.setOnClickListener(v -> {
            if ("visits".equals(currentMode)) return; // FIX: No-op if already on this tab
            currentMode   = "visits";
            currentFilter = FILTER_ALL;
            // FIX: Clear list immediately so old expenses don't flash while visits load
            allDataList.clear();
            updateTabUI();
            updateStatHighlight();
            fetchData(); // Will fetch fresh; visitsLoaded may be true if loaded before
        });

        tabExpenses.setOnClickListener(v -> {
            if ("expenses".equals(currentMode)) return; // FIX: No-op if already on this tab
            currentMode   = "expenses";
            currentFilter = FILTER_ALL;
            // FIX: Clear list immediately so old visits don't flash while expenses load
            allDataList.clear();
            updateTabUI();
            updateStatHighlight();
            fetchData();
        });

        if (layoutStatTotal != null) {
            layoutStatTotal.setOnClickListener(v -> {
                currentFilter = FILTER_ALL;
                updateStatHighlight();
                applyFilterAndRender();
            });
        }
        if (layoutStatApproved != null) {
            layoutStatApproved.setOnClickListener(v -> {
                currentFilter = FILTER_APPROVED;
                updateStatHighlight();
                applyFilterAndRender();
            });
        }
        if (layoutStatPending != null) {
            layoutStatPending.setOnClickListener(v -> {
                currentFilter = FILTER_PENDING;
                updateStatHighlight();
                applyFilterAndRender();
            });
        }
    }

    private void updateTabUI() {
        if ("visits".equals(currentMode)) {
            tvTabVisits.setTextColor(Color.parseColor("#1A73E8"));
            tvTabExpenses.setTextColor(Color.parseColor("#8A93B2"));
            indicatorVisits.setBackgroundColor(Color.parseColor("#1A73E8"));
            indicatorExpenses.setBackgroundColor(Color.TRANSPARENT);
            if (tvStatTotalLabel != null) tvStatTotalLabel.setText("Total Posts");
        } else {
            tvTabVisits.setTextColor(Color.parseColor("#8A93B2"));
            tvTabExpenses.setTextColor(Color.parseColor("#1A73E8"));
            indicatorVisits.setBackgroundColor(Color.TRANSPARENT);
            indicatorExpenses.setBackgroundColor(Color.parseColor("#1A73E8"));
            if (tvStatTotalLabel != null) tvStatTotalLabel.setText("Total Expenses");
        }
    }

    private void updateStatHighlight() {
        int activeColor   = Color.parseColor("#E8F0FE");
        int inactiveColor = Color.TRANSPARENT;
        if (layoutStatTotal    != null) layoutStatTotal.setBackgroundColor(
                FILTER_ALL.equals(currentFilter)      ? activeColor : inactiveColor);
        if (layoutStatApproved != null) layoutStatApproved.setBackgroundColor(
                FILTER_APPROVED.equals(currentFilter) ? activeColor : inactiveColor);
        if (layoutStatPending  != null) layoutStatPending.setBackgroundColor(
                FILTER_PENDING.equals(currentFilter)  ? activeColor : inactiveColor);
    }

    private void fetchData() {
        // FIX: Snapshot the current mode at the moment fetch is triggered
        final String modeAtFetch = currentMode;
        // FIX: Increment token — any prior running fetch with a different token will abort
        final int myToken = fetchToken.incrementAndGet();

        // FIX: Clear the list and UI immediately when a new fetch starts
        allDataList.clear();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.post(() -> swipeRefreshLayout.setRefreshing(true));
        }

        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (tvError          != null) tvError.setVisibility(View.GONE);
        if (containerCards   != null) containerCards.removeAllViews();

        String api = "visits".equals(modeAtFetch) ? ALL_POSTS_API : ALL_EXPENSES_API;

        executor.execute(() -> {
            // FIX: Check token immediately before doing any network work
            if (fetchToken.get() != myToken) return;

            try {
                String json = httpGet(api);

                // FIX: Check token after network call — mode may have changed while waiting
                if (fetchToken.get() != myToken) return;

                runOnUiThread(() -> {
                    // FIX: Final token check inside UI thread — belt and suspenders
                    if (fetchToken.get() != myToken) return;

                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

                    // FIX: Only render if mode still matches what we fetched for
                    if (modeAtFetch.equals(currentMode)) {
                        processJson(json, modeAtFetch);
                    }
                });
            } catch (Exception e) {
                if (fetchToken.get() != myToken) return;
                runOnUiThread(() -> {
                    if (fetchToken.get() != myToken) return;
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    // FIX: Mark load as failed so onResume can retry
                    if ("visits".equals(modeAtFetch))    visitsLoaded   = false;
                    else                                  expensesLoaded = false;
                    showError("Network error. Please check your connection.");
                });
            }
        });
    }

    // FIX: Accept modeAtFetch so we update the correct flag
    private void processJson(String json, String modeAtFetch) {
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) {
            updateCounts(0, 0, 0);
            showEmpty(emptyLabel());
            // FIX: Mark as loaded (empty is a valid result)
            if ("visits".equals(modeAtFetch)) visitsLoaded   = true;
            else                               expensesLoaded = true;
            return;
        }
        try {
            JSONArray all = parseToArray(json);
            if (all == null || all.length() == 0) {
                updateCounts(0, 0, 0);
                showEmpty(emptyLabel());
                if ("visits".equals(modeAtFetch)) visitsLoaded   = true;
                else                               expensesLoaded = true;
                return;
            }

            int totalCount    = all.length();
            int approvedCount = 0;
            int pendingCount  = 0;

            for (int i = 0; i < all.length(); i++) {
                JSONObject obj = all.optJSONObject(i);
                if (obj == null) continue;
                if (!"visits".equals(modeAtFetch) && obj.optInt("is_deleted", 0) == 1) continue;
                allDataList.add(obj);
                String status = obj.optString("status", "").trim().toLowerCase();
                if (status.contains("approved") || status.contains("completed")) approvedCount++;
                else pendingCount++;
            }

            updateCounts(totalCount, approvedCount, pendingCount);
            applyFilterAndRender();

            // FIX: Mark as successfully loaded
            if ("visits".equals(modeAtFetch)) visitsLoaded   = true;
            else                               expensesLoaded = true;

        } catch (Exception e) {
            // FIX: Mark as not loaded on parse error
            if ("visits".equals(modeAtFetch)) visitsLoaded   = false;
            else                               expensesLoaded = false;
            showError("Data processing error.");
        }
    }

    private void applyFilterAndRender() {
        if (containerCards   != null) containerCards.removeAllViews();
        if (tvError          != null) tvError.setVisibility(View.GONE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);

        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject obj : allDataList) {
            String status    = obj.optString("status", "").trim().toLowerCase();
            boolean isApproved = status.contains("approved") || status.contains("completed");
            boolean isPending  = !isApproved;

            if      (FILTER_ALL.equals(currentFilter))                       filtered.add(obj);
            else if (FILTER_APPROVED.equals(currentFilter) && isApproved)    filtered.add(obj);
            else if (FILTER_PENDING.equals(currentFilter)  && isPending)     filtered.add(obj);
        }

        if (filtered.isEmpty()) {
            showEmpty(emptyLabel());
            return;
        }

        String dateKey = "visits".equals(currentMode) ? "time" : "date";
        Collections.sort(filtered, (o1, o2) -> {
            String d1 = o1.optString(dateKey, o1.optString("created_at", "")).trim();
            String d2 = o2.optString(dateKey, o2.optString("created_at", "")).trim();
            return d2.compareTo(d1);
        });

        LayoutInflater inflater = LayoutInflater.from(this);
        for (JSONObject obj : filtered) {
            View card = inflater.inflate(R.layout.item_admin_card, containerCards, false);
            bindAdminCard(card, obj, "visits".equals(currentMode));
            containerCards.addView(card);
        }
    }

    private String extractTripName(JSONObject obj) {
        String[] keys = {"trip_name", "tripname", "trip", "TripName", "TRIP_NAME"};
        for (String key : keys) {
            String val = obj.optString(key, "").trim();
            if (!val.isEmpty() && !val.equalsIgnoreCase("null")) return val;
        }
        return "";
    }

    private String extractEmpName(JSONObject obj) {
        String[] keys = {"name", "empname", "emp_name", "employee_name", "EmpName"};
        for (String key : keys) {
            String val = obj.optString(key, "").trim();
            if (!val.isEmpty() && !val.equalsIgnoreCase("null")) return val;
        }
        return "";
    }

    private void bindAdminCard(View card, JSONObject obj, boolean isVisit) {
        String empcode = obj.optString("empcode", "");
        String empName = extractEmpName(obj);
        String trip    = extractTripName(obj);
        String desc    = obj.optString("description", obj.optString("textbox", ""));
        String lat     = obj.optString("latitude", "");
        String lng     = obj.optString("longitude", "");
        String time   = isVisit
                ? obj.optString("time", obj.optString("created_at", ""))
                : obj.optString("date", obj.optString("created_at", ""));
        String status = obj.optString("status", "pending");
        String amount = obj.optString("amount", "0");

        List<String> imgs = new ArrayList<>();
        JSONArray imgArr  = obj.optJSONArray("images");
        if (imgArr != null) {
            for (int j = 0; j < imgArr.length(); j++) imgs.add(imgArr.optString(j));
        }

        ImageView ivPhoto    = card.findViewById(R.id.ivFacultyPhoto);
        TextView  tvName     = card.findViewById(R.id.tvFacultyName);
        TextView  tvDept     = card.findViewById(R.id.tvFacultyDept);
        TextView  tvStatus   = card.findViewById(R.id.tvStatus);
        TextView  tvTrip     = card.findViewById(R.id.tvTripName);
        TextView  tvCardTime = card.findViewById(R.id.tvTime);
        TextView  tvLoc      = card.findViewById(R.id.tvCardLocation);

        FrameLayout layoutPreview = card.findViewById(R.id.layoutImagePreview);
        ImageView   ivPreview     = card.findViewById(R.id.ivPreview);
        TextView    tvImgCount    = card.findViewById(R.id.tvImageCount);

        CardView  btnMap     = card.findViewById(R.id.btnViewMap);
        CardView  btnApprove = card.findViewById(R.id.btnApprove);
        TextView  tvApprove  = card.findViewById(R.id.tvApproveLabel);
        ImageView ivDelete   = card.findViewById(R.id.ivDelete);

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

        if (status == null || status.isEmpty() || status.equalsIgnoreCase("null")) status = "PENDING";
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

        String tripDisp = !trip.isEmpty() ? trip
                : (isVisit ? "(No Trip Name)" : String.format(Locale.getDefault(), "Expense Claim: ₹%s", amount));
        tvTrip.setText(tripDisp);

        String fTime = formatTime(time);
        tvCardTime.setText(fTime);

        if (tvLoc != null) {
            if (isVisit && !lat.isEmpty()) {
                tvLoc.setVisibility(View.VISIBLE);
                tvLoc.setText(String.format(Locale.getDefault(), "%s, %s", lat, lng));
                reverseGeocode(lat, lng, tvLoc);
            } else {
                tvLoc.setVisibility(View.GONE);
            }
        }

        if (!imgs.isEmpty()) {
            layoutPreview.setVisibility(View.VISIBLE);
            String firstImg = imgs.get(0);
            if (!firstImg.startsWith("http"))
                firstImg = SERVER_BASE + (firstImg.startsWith("/") ? "" : "/") + firstImg;
            Glide.with(this).load(firstImg).centerCrop().into(ivPreview);
            if (imgs.size() > 1) {
                tvImgCount.setVisibility(View.VISIBLE);
                tvImgCount.setText(String.format(Locale.getDefault(), "+%d photos", imgs.size() - 1));
            } else {
                tvImgCount.setVisibility(View.GONE);
            }
        } else {
            layoutPreview.setVisibility(View.GONE);
        }

        final String finalTrip        = tripDisp;
        final String finalDesc        = desc;
        final ArrayList<String> fUrls = new ArrayList<>(imgs);
        final String fStatus          = status;
        final String fAmt             = amount;
        final String locText          = (tvLoc != null && tvLoc.getVisibility() == View.VISIBLE)
                ? tvLoc.getText().toString() : (isVisit ? "Visit" : "Expense Claim");

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminDetailActivity.class);
            intent.putExtra("type", isVisit ? "history" : "expense");
            intent.putExtra("tripName", finalTrip);
            intent.putExtra("location", locText);
            intent.putExtra("dateTime", fTime);
            intent.putExtra("status", fStatus);
            intent.putExtra("description", finalDesc);
            intent.putExtra("amount", fAmt);
            intent.putStringArrayListExtra("images", fUrls);
            startActivity(intent);
        });

        if (isVisit && !lat.isEmpty()) {
            btnMap.setVisibility(View.VISIBLE);
            btnMap.setOnClickListener(v -> {
                try {
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(String.format(Locale.getDefault(), "geo:%s,%s?q=%s,%s", lat, lng, lat, lng)));
                    mapIntent.setPackage("com.google.android.apps.maps");
                    if (mapIntent.resolveActivity(getPackageManager()) != null) startActivity(mapIntent);
                    else startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(String.format(Locale.getDefault(), "https://maps.google.com/?q=%s,%s", lat, lng))));
                } catch (Exception e) {
                    Toast.makeText(this, "Cannot open map.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnMap.setVisibility(View.GONE);
        }

        if (status.equalsIgnoreCase("approved") || status.equalsIgnoreCase("completed")) {
            btnApprove.setCardBackgroundColor(Color.parseColor("#34A853"));
            tvApprove.setText("Approved ✓");
            btnApprove.setEnabled(false);
        } else {
            btnApprove.setEnabled(true);
            final String fCode = empcode;
            final String fId   = obj.optString("id", obj.optString("visit_id", obj.optString("gps_id", "")));
            btnApprove.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Approve Request")
                            .setMessage(String.format(Locale.getDefault(), "Approve this %s by %s?", (isVisit ? "visit" : "expense"), fCode))
                            .setPositiveButton("Approve", (d, w) -> {
                                if (isVisit) callApproveApi(fId, fCode, btnApprove, tvApprove, obj);
                                else         callApproveExpenseApi(fId, fCode, btnApprove, tvApprove, obj);
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );
        }

        if (ivDelete != null) {
            final String fCode    = empcode;
            final String fRawTime = time;
            final String fId      = obj.optString("id", obj.optString("visit_id", obj.optString("gps_id", "")));
            ivDelete.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Record")
                            .setMessage("Are you sure you want to delete this?")
                            .setPositiveButton("Delete", (d, w) -> {
                                if (isVisit) callDeletePostApi(fCode, fRawTime, card);
                                else         callDeleteExpenseApi(fId, card);
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );
        }
    }

    private void callApproveApi(String id, String empcode, CardView btn, TextView tv, JSONObject obj) {
        btn.setEnabled(false);
        tv.setText("Approving...");
        executor.execute(() -> {
            try {
                String url = SERVER_BASE + "/VTracker/approvepost.jsp?id=" + id
                        + "&empcode=" + empcode + "&status=approved";
                String res = httpGet(url);
                runOnUiThread(() -> {
                    if (res != null && (res.contains("true") || res.contains("success"))) {
                        try { obj.put("status", "approved"); } catch (Exception ignored) {}
                        btn.setCardBackgroundColor(Color.parseColor("#34A853"));
                        tv.setText("Approved ✓");
                        Toast.makeText(this, "Visit approved!", Toast.LENGTH_SHORT).show();
                        refreshCountsAndRender();
                    } else {
                        btn.setEnabled(true);
                        tv.setText("Approve");
                        Toast.makeText(this, "Approval failed.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    tv.setText("Approve");
                    Toast.makeText(this, "Error. Please try again.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void callApproveExpenseApi(String id, String empcode, CardView btn, TextView tv, JSONObject obj) {
        btn.setEnabled(false);
        tv.setText("Approving...");
        executor.execute(() -> {
            try {
                String url = SERVER_BASE + "/VTracker/approveexpense.jsp?id=" + id
                        + "&empcode=" + empcode + "&status=approved";
                String res = httpGet(url);
                runOnUiThread(() -> {
                    if (res != null && (res.contains("true") || res.contains("success"))) {
                        try { obj.put("status", "approved"); } catch (Exception ignored) {}
                        btn.setCardBackgroundColor(Color.parseColor("#34A853"));
                        tv.setText("Approved ✓");
                        Toast.makeText(this, "Expense approved!", Toast.LENGTH_SHORT).show();
                        refreshCountsAndRender();
                    } else {
                        btn.setEnabled(true);
                        tv.setText("Approve");
                        Toast.makeText(this, "Approval failed.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    tv.setText("Approve");
                    Toast.makeText(this, "Error. Please try again.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateCounts(int total, int approved, int pending) {
        if (tvStatTotal    != null) tvStatTotal.setText(String.valueOf(total));
        if (tvStatApproved != null) tvStatApproved.setText(String.valueOf(approved));
        if (tvStatPending  != null) tvStatPending.setText(String.valueOf(pending));
        if (tvApprovedBadge != null) {
            String label = "visits".equals(currentMode) ? " posts approved" : " approved";
            tvApprovedBadge.setText(String.format(Locale.getDefault(), "%d%s", approved, label));
        }
    }

    private void refreshCountsAndRender() {
        int total = allDataList.size(), approved = 0, pending = 0;
        for (JSONObject o : allDataList) {
            String s = o.optString("status", "").trim().toLowerCase();
            if (s.contains("approved") || s.contains("completed")) approved++;
            else pending++;
        }
        updateCounts(total, approved, pending);
        applyFilterAndRender();
    }

    private String emptyLabel() {
        if (FILTER_ALL.equals(currentFilter))      return String.format(Locale.getDefault(), "%s records not found", currentMode);
        if (FILTER_APPROVED.equals(currentFilter)) return String.format(Locale.getDefault(), "No approved %s", currentMode);
        return String.format(Locale.getDefault(), "No pending %s", currentMode);
    }

    private JSONArray parseToArray(String json) {
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) return new JSONArray(trimmed);
            JSONObject wrapper = new JSONObject(trimmed);
            if (wrapper.has("data"))     return wrapper.getJSONArray("data");
            if (wrapper.has("expenses")) return wrapper.getJSONArray("expenses");
            if (wrapper.has("posts"))    return wrapper.getJSONArray("posts");
            if (wrapper.keys().hasNext()) {
                Object val = wrapper.get(wrapper.keys().next());
                if (val instanceof JSONArray) return (JSONArray) val;
            }
        } catch (Exception e) {
            Log.e(TAG, "parseToArray error: " + e.getMessage());
        }
        return new JSONArray();
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
                    if (!result.isEmpty()) runOnUiThread(() -> { if (target != null) target.setText(result); });
                }
            } catch (Exception ignored) {}
        });
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat in = raw.contains(".")
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.getDefault())
                    : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date d = in.parse(raw);
            return d != null
                    ? new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(d)
                    : raw;
        } catch (ParseException e) {
            return raw;
        }
    }

    private String getStatusColor(String status) {
        if (status == null) return "#FB8C00";
        String s = status.toLowerCase().trim();
        if (s.contains("approved") || s.contains("completed")) return "#34A853";
        if (s.contains("pending"))  return "#FB8C00";
        if (s.contains("review"))   return "#1A73E8";
        if (s.contains("rejected")) return "#E53935";
        return "#FB8C00";
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

    private void callDeletePostApi(String empcode, String time, View cardView) {
        executor.execute(() -> {
            try {
                String urlStr = SERVER_BASE + "/VTracker/deletepost.jsp?empcode=" + empcode
                        + "&time=" + URLEncoder.encode(time, "UTF-8") + "&admin_id=" + adminId;
                String body = httpGet(urlStr);
                boolean ok  = body != null && (body.contains("true") || body.contains("deleted") || body.contains("success"));
                runOnUiThread(() -> {
                    if (ok) {
                        allDataList.removeIf(o -> o.optString("time", "").equals(time)
                                && o.optString("empcode", "").equals(empcode));
                        if (containerCards != null) containerCards.removeView(cardView);
                        Toast.makeText(this, "Deleted successfully.", Toast.LENGTH_SHORT).show();
                        refreshCountsAndRender();
                    } else {
                        Toast.makeText(this, "Delete failed.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error. Please try again.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void callDeleteExpenseApi(String expId, View cardView) {
        executor.execute(() -> {
            try {
                String urlStr = SERVER_BASE + "/VTracker/deleteexpense.jsp?id=" + expId
                        + "&admin_id=" + adminId;
                String body = httpGet(urlStr);
                boolean ok  = body != null && (body.contains("true") || body.contains("deleted") || body.contains("success"));
                runOnUiThread(() -> {
                    if (ok) {
                        allDataList.removeIf(o -> o.optString("id", "").equals(expId));
                        if (containerCards != null) containerCards.removeView(cardView);
                        Toast.makeText(this, "Deleted successfully.", Toast.LENGTH_SHORT).show();
                        refreshCountsAndRender();
                    } else {
                        Toast.makeText(this, "Delete failed.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error. Please try again.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showError(String msg) {
        if (tvError != null) { tvError.setVisibility(View.VISIBLE); tvError.setText(msg); }
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
    }

    private void showEmpty(String msg) {
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        if (tvEmptyMsg       != null) tvEmptyMsg.setText(msg);
    }

    private void openDrawer()  { if (drawerLayout != null && sideDrawer != null) drawerLayout.openDrawer(sideDrawer); }
    private void closeDrawer() { if (drawerLayout != null && sideDrawer != null) drawerLayout.closeDrawer(sideDrawer); }

    private void goToDashboard() {
        closeDrawer();
        Intent i = new Intent(this, AdminActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    private void goToSearch() {
        closeDrawer();
        Intent i = new Intent(this, AdminSearchActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    private void goToAddFaculty() {
        closeDrawer();
        Intent i = new Intent(this, AddFacultyActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
    }

    private void goToProfile() {
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra("USER_NAME", adminName);
        i.putExtra("EMPLOYEE_ID", adminId);
        startActivity(i);
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
        if (drawerLayout != null && sideDrawer != null && drawerLayout.isDrawerOpen(sideDrawer))
            closeDrawer();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}