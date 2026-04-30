package com.example.vtracker;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminSearchActivity extends BaseActivity {

    private static final String TAG = "AdminSearchActivity";

    private static final String ALL_POSTS_API    = "http://160.187.169.14/jspapi/gps/getallposts.jsp";
    private static final String ALL_EXPENSES_API = "http://160.187.169.14/jspapi/gps/getallexpenses.jsp";
    private static final String SERVER_BASE      = "http://160.187.169.14";
    private static final String PHOTO_BASE_IP    = "http://160.187.169.24";

    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView           tvFromDate, tvToDate, tvResultCount, tvError;
    private EditText           etEmpCode;
    private CardView           btnSearchPosts;
    private LinearLayout       containerResults;

    private TextView tabHistory, tabExpenses;
    private View     indicatorHistory, indicatorExpenses;

    private LinearLayout navDashboard, navSearch, navApprovals, navAddUsers, navProfile;

    private String adminName        = "";
    private String adminId          = "";
    private String selectedFromDate = null;
    private String selectedToDate   = null;
    private String currentMode      = "visits";

    // FIX: Track whether initial load succeeded
    private volatile boolean dataLoadedSuccessfully = false;

    private final AtomicInteger fetchToken = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_admin_search);
        initViews();
        loadAdminData();
        updateTabUI();
        // FIX: Fetch data immediately on create, not in onResume
        fetchData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // FIX: Only re-fetch if previous load failed — don't double-fetch on create
        if (!dataLoadedSuccessfully) {
            fetchData();
        }
    }

    private void initViews() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        tvFromDate         = findViewById(R.id.tvFromDate);
        tvToDate           = findViewById(R.id.tvToDate);
        tvResultCount      = findViewById(R.id.tvResultCount);
        tvError            = findViewById(R.id.tvError);
        etEmpCode          = findViewById(R.id.etEmpCode);
        btnSearchPosts     = findViewById(R.id.btnSearchPosts);
        containerResults   = findViewById(R.id.containerResults);
        tabHistory         = findViewById(R.id.tabHistory);
        tabExpenses        = findViewById(R.id.tabExpenses);
        indicatorHistory   = findViewById(R.id.indicatorHistory);
        indicatorExpenses  = findViewById(R.id.indicatorExpenses);
        navDashboard       = findViewById(R.id.navDashboard);
        navSearch          = findViewById(R.id.navSearch);
        navApprovals       = findViewById(R.id.navApprovals);
        navAddUsers        = findViewById(R.id.navAddUsers);
        navProfile         = findViewById(R.id.navProfile);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1A73E8"));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // FIX: Manual swipe always forces a re-fetch
                dataLoadedSuccessfully = false;
                fetchData();
            });
        }

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        tvFromDate.setOnClickListener(v -> showDatePicker(true));
        tvToDate.setOnClickListener(v   -> showDatePicker(false));

        tabHistory.setOnClickListener(v -> {
            if ("visits".equals(currentMode)) return; // FIX: No-op if already on tab
            currentMode = "visits";
            dataLoadedSuccessfully = false;
            updateTabUI();
            fetchData();
        });
        tabExpenses.setOnClickListener(v -> {
            if ("expenses".equals(currentMode)) return; // FIX: No-op if already on tab
            currentMode = "expenses";
            dataLoadedSuccessfully = false;
            updateTabUI();
            fetchData();
        });

        btnSearchPosts.setOnClickListener(v -> {
            dataLoadedSuccessfully = false;
            fetchData();
        });

        navDashboard.setOnClickListener(v -> {
            Intent i = new Intent(this, AdminActivity.class);
            i.putExtra("USER_NAME", adminName); i.putExtra("EMPLOYEE_ID", adminId);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i); finish();
        });
        navSearch.setOnClickListener(v -> {
            dataLoadedSuccessfully = false;
            fetchData();
        });
        navAddUsers.setOnClickListener(v -> {
            Intent i = new Intent(this, AddFacultyActivity.class);
            i.putExtra("USER_NAME", adminName); i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
        navApprovals.setOnClickListener(v -> {
            Intent i = new Intent(this, ApprovalsActivity.class);
            i.putExtra("USER_NAME", adminName); i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
        navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("USER_NAME", adminName); i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
    }

    private void loadAdminData() {
        adminName = getIntent().getStringExtra("USER_NAME");
        adminId   = getIntent().getStringExtra("EMPLOYEE_ID");
        if (adminName == null || adminName.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
            adminName = prefs.getString(LoginActivity.KEY_USER_NAME, "Admin");
            adminId   = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
        }
    }

    private void updateTabUI() {
        boolean isVisits = "visits".equals(currentMode);
        tabHistory.setTextColor(Color.parseColor(isVisits ? "#1A73E8" : "#8A93B2"));
        tabExpenses.setTextColor(Color.parseColor(isVisits ? "#8A93B2" : "#1A73E8"));
        indicatorHistory.setBackgroundColor(Color.parseColor(isVisits ? "#1A73E8" : "#E0E0E0"));
        indicatorExpenses.setBackgroundColor(Color.parseColor(isVisits ? "#E0E0E0" : "#1A73E8"));
        TextView tvLabel = findViewById(R.id.tvResultsLabel);
        if (tvLabel != null) tvLabel.setText(isVisits ? "Recent Visits" : "Recent Expenses");
    }

    private void showDatePicker(boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            String display = String.format("%02d-%02d-%04d", day, month + 1, year);
            String api     = String.format("%04d-%02d-%02d", year, month + 1, day);
            if (isFrom) { tvFromDate.setText(display); tvFromDate.setTextColor(Color.parseColor("#0D1B4B")); selectedFromDate = api; }
            else        { tvToDate.setText(display);   tvToDate.setTextColor(Color.parseColor("#0D1B4B"));   selectedToDate   = api; }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void fetchData() {
        // FIX: Snapshot mode and token atomically before any async work
        final String modeSnap  = currentMode;
        final int myToken      = fetchToken.incrementAndGet();
        final String dateKey   = "visits".equals(modeSnap) ? "time" : "date";
        final String api       = "visits".equals(modeSnap) ? ALL_POSTS_API : ALL_EXPENSES_API;
        final String empCode   = etEmpCode.getText().toString().trim();
        final String fromDate  = selectedFromDate;
        final String toDate    = selectedToDate;

        showLoading();

        executor.execute(() -> {
            // FIX: Check token before making network call
            if (fetchToken.get() != myToken) return;

            try {
                String json = httpGet(api);

                // FIX: Check token after network returns
                if (fetchToken.get() != myToken) return;

                runOnUiThread(() -> {
                    // FIX: Final check before touching UI
                    if (fetchToken.get() != myToken) return;

                    hideSwipeRefresh();

                    // FIX: Only render if mode still matches what we fetched
                    if (modeSnap.equals(currentMode)) {
                        renderResults(json, fromDate, toDate,
                                empCode.isEmpty() ? null : empCode,
                                dateKey, "visits".equals(modeSnap));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "fetchData error", e);
                if (fetchToken.get() != myToken) return;
                runOnUiThread(() -> {
                    if (fetchToken.get() != myToken) return;
                    hideSwipeRefresh();
                    // FIX: Mark as failed so onResume can retry
                    dataLoadedSuccessfully = false;
                    showError("Network error. Please try again.");
                });
            }
        });
    }

    private void showLoading() {
        if (swipeRefreshLayout != null)
            swipeRefreshLayout.post(() -> swipeRefreshLayout.setRefreshing(true));
        if (tvError != null) tvError.setVisibility(View.GONE);
        if (tvResultCount != null) tvResultCount.setText("");
        if (containerResults != null) containerResults.removeAllViews();
    }

    private void hideSwipeRefresh() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    private void renderResults(String json, String fromDate, String toDate,
                               String empCode, String dateKey, boolean isVisit) {
        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) {
            if (containerResults != null) containerResults.removeAllViews();
            showError("No data found.");
            if (tvResultCount != null) tvResultCount.setText("0 results");
            // FIX: Empty is a valid loaded state
            dataLoadedSuccessfully = true;
            return;
        }

        try {
            JSONArray array = parseToArray(json);
            if (array == null || array.length() == 0) {
                if (containerResults != null) containerResults.removeAllViews();
                showError("No records found.");
                if (tvResultCount != null) tvResultCount.setText("0 results");
                dataLoadedSuccessfully = true;
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date dFrom = null, dTo = null;
            try {
                if (fromDate != null && !fromDate.isEmpty()) dFrom = sdf.parse(fromDate);
                if (toDate   != null && !toDate.isEmpty())   dTo   = sdf.parse(toDate);
                if (dTo != null) dTo = new Date(dTo.getTime() + 24 * 60 * 60 * 1000 - 1);
            } catch (ParseException ignored) {}

            String empFilter = "";
            if (empCode != null && !empCode.trim().isEmpty()) {
                try { empFilter = String.format(Locale.getDefault(), "%05d", Integer.parseInt(empCode.trim().replaceAll("\\D", ""))); }
                catch (NumberFormatException e) { empFilter = empCode.trim().toLowerCase(); }
            }

            List<JSONObject> filtered = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (!isVisit && obj.optInt("is_deleted", 0) == 1) continue;
                if (!empFilter.isEmpty()) {
                    String postCode = obj.optString("empcode", "").trim();
                    try { postCode = String.format(Locale.getDefault(), "%05d", Integer.parseInt(postCode.replaceAll("\\D", ""))); }
                    catch (NumberFormatException e) { postCode = postCode.toLowerCase(); }
                    if (!postCode.equals(empFilter)) continue;
                }

                if (dFrom != null || dTo != null) {
                    String ts = obj.optString(dateKey, obj.optString("created_at", "")).trim();
                    if (ts.length() < 10) continue;
                    try {
                        Date pd = sdf.parse(ts.substring(0, 10));
                        if (pd == null) continue;
                        if (dFrom != null && pd.before(dFrom)) continue;
                        if (dTo   != null && pd.after(dTo))    continue;
                    } catch (ParseException e) { continue; }
                }
                filtered.add(obj);
            }

            if (filtered.isEmpty()) {
                if (containerResults != null) containerResults.removeAllViews();
                showError("No records match your search.");
                if (tvResultCount != null) tvResultCount.setText("0 results");
                dataLoadedSuccessfully = true;
                return;
            }

            Collections.sort(filtered, (a, b) -> {
                String da = a.optString(dateKey, a.optString("created_at", "")).trim();
                String db = b.optString(dateKey, b.optString("created_at", "")).trim();
                return db.compareTo(da);
            });

            if (containerResults != null) containerResults.removeAllViews();
            if (tvResultCount != null)
                tvResultCount.setText(String.format(Locale.getDefault(), "Showing %d result%s", filtered.size(), (filtered.size() == 1 ? "" : "s")));

            LayoutInflater inflater = LayoutInflater.from(this);
            for (JSONObject obj : filtered) {
                View card = inflater.inflate(R.layout.item_admin_card, containerResults, false);
                bindAdminCard(card, obj, isVisit);
                if (containerResults != null) containerResults.addView(card);
            }

            // FIX: Mark as successfully loaded
            dataLoadedSuccessfully = true;

        } catch (Exception e) {
            Log.e(TAG, "renderResults", e);
            dataLoadedSuccessfully = false;
            showError("Error processing results.");
        }
    }

    private void bindAdminCard(View card, JSONObject obj, boolean isVisit) {
        String empcode = obj.optString("empcode", "");
        String empName = extractEmpName(obj);
        String trip    = extractTripName(obj);
        String desc    = obj.optString("description", obj.optString("textbox", ""));
        String lat     = obj.optString("latitude", "");
        String lng     = obj.optString("longitude", "");
        String time    = isVisit ? obj.optString("time",  obj.optString("created_at", ""))
                : obj.optString("date",  obj.optString("created_at", ""));
        String status  = obj.optString("status", "pending");
        String amount  = obj.optString("amount", "0");

        List<String> imgs = new ArrayList<>();
        JSONArray imgArr  = obj.optJSONArray("images");
        if (imgArr != null) for (int j = 0; j < imgArr.length(); j++) imgs.add(imgArr.optString(j, ""));

        ImageView ivPhoto    = card.findViewById(R.id.ivFacultyPhoto);
        TextView  tvName     = card.findViewById(R.id.tvFacultyName);
        TextView  tvDept     = card.findViewById(R.id.tvFacultyDept);
        TextView  tvStatus   = card.findViewById(R.id.tvStatus);
        TextView  tvTrip     = card.findViewById(R.id.tvTripName);
        TextView  tvCardTime = card.findViewById(R.id.tvTime);
        FrameLayout layoutPreview = card.findViewById(R.id.layoutImagePreview);
        ImageView   ivPreview     = card.findViewById(R.id.ivPreview);
        TextView    tvImgCount    = card.findViewById(R.id.tvImageCount);
        CardView  btnMap     = card.findViewById(R.id.btnViewMap);
        CardView  btnApprove = card.findViewById(R.id.btnApprove);
        TextView  tvApprove  = card.findViewById(R.id.tvApproveLabel);
        ImageView ivDelete   = card.findViewById(R.id.ivDelete);

        String paddedCode = empcode;
        try { paddedCode = String.format(Locale.getDefault(), "%05d", Integer.parseInt(empcode.replaceAll("\\D", ""))); }
        catch (Exception ignored) {}

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

        String tripDisp = !trip.isEmpty() ? trip
                : (isVisit ? "(No Trip Name)" : String.format(Locale.getDefault(), "Expense Claim: ₹%s", amount));
        tvTrip.setText(tripDisp);
        tvCardTime.setText(formatTime(time));

        if (!imgs.isEmpty()) {
            layoutPreview.setVisibility(View.VISIBLE);
            String firstImg = imgs.get(0);
            if (!firstImg.startsWith("http")) firstImg = SERVER_BASE + (firstImg.startsWith("/") ? "" : "/") + firstImg;
            Glide.with(this).load(firstImg).centerCrop().into(ivPreview);
            tvImgCount.setVisibility(imgs.size() > 1 ? View.VISIBLE : View.GONE);
            if (imgs.size() > 1) tvImgCount.setText(String.format(Locale.getDefault(), "+%d photos", imgs.size() - 1));
        } else {
            layoutPreview.setVisibility(View.GONE);
        }

        final String fTrip   = tripDisp;
        final String fDesc   = desc;
        final String fTime   = formatTime(time);
        final String fStatus = status;
        final String fAmt    = amount;
        final ArrayList<String> fUrls = new ArrayList<>(imgs);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminDetailActivity.class);
            intent.putExtra("type", isVisit ? "history" : "expense");
            intent.putExtra("tripName", fTrip);
            intent.putExtra("location", isVisit ? (String.format(Locale.getDefault(), "%s, %s", lat, lng)) : "Expense Submission");
            intent.putExtra("dateTime", fTime);
            intent.putExtra("status", fStatus);
            intent.putExtra("description", fDesc);
            intent.putExtra("amount", fAmt);
            intent.putStringArrayListExtra("images", fUrls);
            startActivity(intent);
        });

        if (isVisit && !lat.isEmpty()) {
            btnMap.setVisibility(View.VISIBLE);
            btnMap.setOnClickListener(v -> {
                try {
                    Intent m = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.getDefault(), "geo:%s,%s?q=%s,%s", lat, lng, lat, lng)));
                    m.setPackage("com.google.android.apps.maps");
                    if (m.resolveActivity(getPackageManager()) != null) startActivity(m);
                    else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.getDefault(), "https://maps.google.com/?q=%s,%s", lat, lng))));
                } catch (Exception e) { Toast.makeText(this, "Cannot open map.", Toast.LENGTH_SHORT).show(); }
            });
        } else {
            btnMap.setVisibility(View.GONE);
        }

        if (fStatus.equalsIgnoreCase("approved") || fStatus.equalsIgnoreCase("completed")) {
            btnApprove.setCardBackgroundColor(Color.parseColor("#34A853"));
            tvApprove.setText("Approved ✓");
            btnApprove.setEnabled(false);
        } else {
            btnApprove.setEnabled(true);
            final String fCode = empcode;
            final String fId = obj.optString("id", obj.optString("visit_id", obj.optString("gps_id", "")));
            btnApprove.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Approve " + (isVisit ? "Visit" : "Expense"))
                            .setMessage(String.format(Locale.getDefault(), "Approve this request by %s?", fCode))
                            .setPositiveButton("Approve", (d, w) -> {
                                if (isVisit) callApproveApi(fId, fCode, btnApprove, tvApprove);
                                else         callApproveExpenseApi(fId, fCode, btnApprove, tvApprove);
                            })
                            .setNegativeButton("Cancel", null)
                            .show());
        }

        if (ivDelete != null) {
            final String fCode = empcode;
            final String fRawTime = time;
            final String fId = obj.optString("id", obj.optString("visit_id", obj.optString("gps_id", "")));
            ivDelete.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete " + (isVisit ? "Visit" : "Expense"))
                            .setMessage("Are you sure you want to delete this record?")
                            .setPositiveButton("Delete", (d, w) -> {
                                if (isVisit) callDeletePostApi(fCode, fRawTime, card);
                                else         callDeleteExpenseApi(fId, card);
                            })
                            .setNegativeButton("Cancel", null)
                            .show());
        }
    }

    private void callApproveApi(String id, String empcode, CardView btn, TextView tv) {
        btn.setEnabled(false); tv.setText("...");
        executor.execute(() -> {
            try {
                String res = httpGet(SERVER_BASE + "/jspapi/gps/approvepost.jsp?id=" + id + "&empcode=" + empcode + "&status=approved");
                runOnUiThread(() -> {
                    if (res != null && (res.contains("true") || res.contains("success"))) {
                        btn.setCardBackgroundColor(Color.parseColor("#34A853")); tv.setText("Approved ✓");
                        Toast.makeText(this, "Visit approved!", Toast.LENGTH_SHORT).show();
                    } else { btn.setEnabled(true); tv.setText("Approve"); }
                });
            } catch (Exception e) { runOnUiThread(() -> { btn.setEnabled(true); tv.setText("Approve"); }); }
        });
    }

    private void callApproveExpenseApi(String id, String empcode, CardView btn, TextView tv) {
        btn.setEnabled(false); tv.setText("...");
        executor.execute(() -> {
            try {
                String res = httpGet(SERVER_BASE + "/jspapi/gps/approveexpense.jsp?id=" + id + "&empcode=" + empcode + "&status=approved");
                runOnUiThread(() -> {
                    if (res != null && (res.contains("true") || res.contains("success"))) {
                        btn.setCardBackgroundColor(Color.parseColor("#34A853")); tv.setText("Approved ✓");
                        Toast.makeText(this, "Expense approved!", Toast.LENGTH_SHORT).show();
                    } else { btn.setEnabled(true); tv.setText("Approve"); }
                });
            } catch (Exception e) { runOnUiThread(() -> { btn.setEnabled(true); tv.setText("Approve"); }); }
        });
    }

    private void callDeletePostApi(String empcode, String time, View card) {
        executor.execute(() -> {
            try {
                String res = httpGet(SERVER_BASE + "/jspapi/gps/deletepost.jsp?empcode=" + empcode
                        + "&time=" + URLEncoder.encode(time, "UTF-8") + "&admin_id=" + adminId);
                runOnUiThread(() -> {
                    if (res != null && (res.contains("true") || res.contains("deleted") || res.contains("success"))) {
                        if (containerResults != null) containerResults.removeView(card);
                        Toast.makeText(this, "Deleted successfully.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) { Log.e(TAG, "Delete post error", e); }
        });
    }

    private void callDeleteExpenseApi(String id, View card) {
        executor.execute(() -> {
            try {
                String res = httpGet(SERVER_BASE + "/jspapi/gps/deleteexpense.jsp?id=" + id + "&admin_id=" + adminId);
                runOnUiThread(() -> {
                    if (res != null && (res.contains("true") || res.contains("deleted") || res.contains("success"))) {
                        if (containerResults != null) containerResults.removeView(card);
                        Toast.makeText(this, "Deleted successfully.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) { Log.e(TAG, "Delete expense error", e); }
        });
    }

    private String extractTripName(JSONObject obj) {
        for (String k : new String[]{"trip_name","tripname","trip","TripName","TRIP_NAME"}) {
            String v = obj.optString(k, "").trim();
            if (!v.isEmpty() && !v.equalsIgnoreCase("null")) return v;
        }
        return "";
    }

    private String extractEmpName(JSONObject obj) {
        for (String k : new String[]{"name","empname","emp_name","employee_name","EmpName"}) {
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
            if (w.has("success") && !w.optBoolean("success", true)) return null;
            for (String k : new String[]{"data","history","records","expenses","posts"})
                if (w.has(k)) return w.getJSONArray(k);
            Iterator<String> it = w.keys();
            while (it.hasNext()) { Object v = w.get(it.next()); if (v instanceof JSONArray) return (JSONArray) v; }
        } catch (Exception e) { Log.e(TAG, "parseToArray", e); }
        return new JSONArray();
    }

    private String getStatusColor(String status) {
        if (status == null) return "#8A93B2";
        String s = status.toLowerCase().trim();
        if (s.contains("approved") || s.contains("completed")) return "#34A853";
        if (s.contains("pending"))  return "#FB8C00";
        if (s.contains("rejected")) return "#E53935";
        return "#8A93B2";
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(raw);
            return d != null ? new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(d) : raw;
        } catch (Exception e) { return raw; }
    }

    private String httpGet(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        try {
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String response = sb.toString().trim();
                return response.isEmpty() ? "[]" : response;
            } else if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                return "[]";
            }
            return "[]";
        } finally { c.disconnect(); }
    }

    private void showError(String msg) {
        if (tvError != null) { tvError.setVisibility(View.VISIBLE); tvError.setText(msg); }
    }

    @Override protected void onDestroy() { super.onDestroy(); executor.shutdownNow(); }
}