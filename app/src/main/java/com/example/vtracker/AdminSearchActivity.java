package com.example.vtracker;

import android.app.DatePickerDialog;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminSearchActivity extends BaseActivity {

    private static final String TAG = "AdminSearchActivity";

    // ── API — UNCHANGED ────────────────────────────────────────────
    private static final String ALL_POSTS_API = "http://160.187.169.14/jspapi/gps/getallposts.jsp";
    private static final String SERVER_BASE   = "http://160.187.169.14";
    private static final String PHOTO_BASE_IP = "http://160.187.169.24";

    // ── UI ─────────────────────────────────────────────────────────
    private TextView     tvFromDate, tvToDate, tvResultCount, tvError;
    private EditText     etEmpCode;
    private CardView     btnSearchPosts;
    private ProgressBar  progressBar;
    private LinearLayout containerResults;

    // Bottom nav
    private LinearLayout navDashboard, navSearch, navApprovals, navProfile;

    // ── Data ───────────────────────────────────────────────────────
    private String adminName = "";
    private String adminId   = "";
    private String selectedFromDate = null;   // yyyy-MM-dd for API
    private String selectedToDate   = null;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_admin_search);

        initViews();
        loadAdminData();
        // Auto-load all posts on open (no filters)
        fetchPosts(null, null, null);
    }

    private void initViews() {
        tvFromDate       = findViewById(R.id.tvFromDate);
        tvToDate         = findViewById(R.id.tvToDate);
        tvResultCount    = findViewById(R.id.tvResultCount);
        tvError          = findViewById(R.id.tvError);
        etEmpCode        = findViewById(R.id.etEmpCode);
        btnSearchPosts   = findViewById(R.id.btnSearchPosts);
        progressBar      = findViewById(R.id.progressBar);
        containerResults = findViewById(R.id.containerResults);
        navDashboard     = findViewById(R.id.navDashboard);
        navSearch        = findViewById(R.id.navSearch);
        navApprovals     = findViewById(R.id.navApprovals);
        navProfile       = findViewById(R.id.navProfile);

        // Back button
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Date pickers
        tvFromDate.setOnClickListener(v -> showDatePicker(true));
        tvToDate.setOnClickListener(v   -> showDatePicker(false));

        // Search button
        btnSearchPosts.setOnClickListener(v -> {
            String empCode = etEmpCode.getText().toString().trim();
            fetchPosts(selectedFromDate, selectedToDate,
                    empCode.isEmpty() ? null : empCode);
        });

        // Bottom nav
        navDashboard.setOnClickListener(v -> finish());
        navSearch.setOnClickListener(v    -> { /* already here */ });
        navApprovals.setOnClickListener(v -> {
            Intent i = new Intent(this, ApprovalsActivity.class);
            i.putExtra("USER_NAME",   adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
        navProfile.setOnClickListener(v   -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("USER_NAME",   adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
    }

    private void loadAdminData() {
        adminName = getIntent().getStringExtra("USER_NAME");
        adminId   = getIntent().getStringExtra("EMPLOYEE_ID");
        if (adminName == null || adminName.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    LoginActivity.PREF_NAME, MODE_PRIVATE);
            adminName = prefs.getString(LoginActivity.KEY_USER_NAME,   "Admin");
            adminId   = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
        }
    }

    // ── Date picker ────────────────────────────────────────────────
    private void showDatePicker(boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            // Display format: dd-MM-yyyy
            String display = String.format("%02d-%02d-%04d", day, month + 1, year);
            // API format: yyyy-MM-dd
            String api     = String.format("%04d-%02d-%02d", year, month + 1, day);
            if (isFrom) {
                tvFromDate.setText(display);
                tvFromDate.setTextColor(Color.parseColor("#0D1B4B"));
                selectedFromDate = api;
            } else {
                tvToDate.setText(display);
                tvToDate.setTextColor(Color.parseColor("#0D1B4B"));
                selectedToDate = api;
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ══════════════════════════════════════════════════════════════
    //  Fetch posts — same getallposts.jsp endpoint, params appended
    // ══════════════════════════════════════════════════════════════
    private void fetchPosts(String fromDate, String toDate, String empCode) {
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);
        tvResultCount.setText("");
        containerResults.removeAllViews();

        // Always fetch ALL posts — server ignores filter params,
        // so we filter client-side after receiving the full list
        Log.d(TAG, "Fetching all posts, will filter client-side."
                + " empCode=" + empCode + " from=" + fromDate + " to=" + toDate);

        executor.execute(() -> {
            try {
                String json = httpGet(ALL_POSTS_API);
                runOnUiThread(() -> renderResults(json, fromDate, toDate, empCode));
            } catch (Exception e) {
                Log.e(TAG, "Fetch error: " + e.getMessage());
                runOnUiThread(() -> showError("Network error: " + e.getMessage()));
            }
        });
    }

    private void renderResults(String json, String fromDate, String toDate, String empCode) {
        progressBar.setVisibility(View.GONE);

        if (json == null || json.trim().isEmpty()) {
            showError("No posts found.");
            return;
        }

        try {
            JSONArray array;
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                array = new JSONArray(trimmed);
            } else {
                JSONObject wrapper = new JSONObject(trimmed);
                String key = wrapper.keys().next();
                array = wrapper.getJSONArray(key);
            }

            // ── Client-side filtering ─────────────────────────────
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date dFrom = null, dTo = null;
            try {
                if (fromDate != null && !fromDate.isEmpty()) dFrom = sdf.parse(fromDate);
                if (toDate   != null && !toDate.isEmpty())   dTo   = sdf.parse(toDate);
                // Make dTo inclusive — extend to end of that day
                if (dTo != null) dTo = new Date(dTo.getTime() + 24 * 60 * 60 * 1000 - 1);
            } catch (ParseException ignored) {}

            // Normalise empCode filter — pad to 5 digits if numeric
            String empFilter = "";
            if (empCode != null && !empCode.trim().isEmpty()) {
                try {
                    empFilter = String.format("%05d",
                            Integer.parseInt(empCode.trim().replaceAll("\\D", "")));
                } catch (NumberFormatException e) {
                    empFilter = empCode.trim().toLowerCase();
                }
            }

            List<JSONObject> filtered = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject post = array.getJSONObject(i);

                // ── Empcode filter ────────────────────────────────
                if (!empFilter.isEmpty()) {
                    String postCode = post.optString("empcode", "").trim();
                    try {
                        postCode = String.format("%05d",
                                Integer.parseInt(postCode.replaceAll("\\D", "")));
                    } catch (NumberFormatException e) {
                        postCode = postCode.toLowerCase();
                    }
                    if (!postCode.equals(empFilter)) continue;
                }

                // ── Date range filter ─────────────────────────────
                if (dFrom != null || dTo != null) {
                    String timeStr = post.optString("time", "").trim();
                    // Extract just the date portion (first 10 chars: yyyy-MM-dd)
                    if (timeStr.length() >= 10) timeStr = timeStr.substring(0, 10);
                    try {
                        Date postDate = sdf.parse(timeStr);
                        if (postDate == null) continue;
                        if (dFrom != null && postDate.before(dFrom)) continue;
                        if (dTo   != null && postDate.after(dTo))    continue;
                    } catch (ParseException e) {
                        continue; // skip records with unparseable dates when filtering
                    }
                }

                filtered.add(post);
            }

            if (filtered.isEmpty()) {
                showError("No posts match your search.");
                tvResultCount.setText("0 results");
                return;
            }

            tvResultCount.setText("Showing " + filtered.size() + " result"
                    + (filtered.size() == 1 ? "" : "s"));

            LayoutInflater inflater = LayoutInflater.from(this);
            for (JSONObject post : filtered) {
                String postId  = post.optString("id", post.optString("visit_id", post.optString("gps_id", "")));
                String empcode = post.optString("empcode",     "");
                String desc    = post.optString("description", "");
                String lat     = post.optString("latitude",    "");
                String lng     = post.optString("longitude",   "");
                String time    = post.optString("time",        "");
                String status  = post.optString("status",      "");

                List<String> imgs = new ArrayList<>();
                JSONArray imgArr  = post.optJSONArray("images");
                if (imgArr != null) {
                    for (int j = 0; j < imgArr.length(); j++)
                        imgs.add(imgArr.getString(j));
                }

                View card = inflater.inflate(R.layout.item_post_card, containerResults, false);
                bindCard(card, postId, empcode, desc, lat, lng, time, status, imgs);
                containerResults.addView(card);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            showError("Error reading response.");
        }
    }

    // ── Bind one card — identical logic to AdminActivity ──────────
    private void bindCard(View card, String postId, String empcode, String description,
                          String latitude, String longitude, String time,
                          String status, List<String> imageUrls) {

        ImageView    ivPhoto    = card.findViewById(R.id.ivCardFacultyPhoto);
        TextView     tvName     = card.findViewById(R.id.tvCardFacultyName);
        TextView     tvDept     = card.findViewById(R.id.tvCardDept);
        TextView     tvLocation = card.findViewById(R.id.tvCardLocation);
        TextView     tvDesc     = card.findViewById(R.id.tvCardDescription);
        TextView     tvTime     = card.findViewById(R.id.tvCardTime);
        TextView     tvStatus   = card.findViewById(R.id.tvCardStatus);
        ViewPager2   pager      = card.findViewById(R.id.viewPagerImages);
        LinearLayout dots       = card.findViewById(R.id.layoutDots);
        CardView     btnMap     = card.findViewById(R.id.btnViewMap);
        CardView     btnApprove = card.findViewById(R.id.btnApprove);
        TextView     tvApprove  = card.findViewById(R.id.tvApproveLabel);

        // Pad empcode — UNCHANGED
        String paddedCode = empcode;
        try {
            paddedCode = String.format("%05d", Integer.parseInt(empcode.replaceAll("\\D", "")));
        } catch (NumberFormatException ignored) {}

        tvName.setText(paddedCode);
        tvDept.setText("Emp ID: FAC-" + paddedCode);

        // Status badge
        if (!status.isEmpty()) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(status.toUpperCase());
            tvStatus.getBackground().setTint(Color.parseColor(getStatusColor(status)));
        }

        // Faculty photo — UNCHANGED
        String photoUrl = PHOTO_BASE_IP + "/counselling_jspapi/StaffPhotos/" + paddedCode + ".JPG";
        Glide.with(this)
                .load(photoUrl)
                .apply(new RequestOptions()
                        .transform(new CircleCrop())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(android.R.drawable.ic_menu_myplaces)
                        .error(android.R.drawable.ic_menu_myplaces))
                .into(ivPhoto);

        // Images ViewPager — UNCHANGED
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
        tvTime.setText(formatTime(time));
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
                            Uri.parse("https://maps.google.com/?q=" + latitude + "," + longitude)));
                }
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open map.", Toast.LENGTH_SHORT).show();
            }
        });

        // Approve button — calls server
        final String finalCode   = paddedCode;
        final String finalPostId = postId;
        btnApprove.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Approve Visit")
                        .setMessage("Approve visit by employee " + finalCode + "?")
                        .setPositiveButton("Approve", (d, w) -> callApproveApi(finalPostId, finalCode, btnApprove, tvApprove))
                        .setNegativeButton("Cancel", null)
                        .show()
        );
    }

    private void callApproveApi(String postId, String empcode, CardView btnApprove, TextView tvApprove) {
        btnApprove.setEnabled(false);
        tvApprove.setText("Approving...");
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = SERVER_BASE + "/jspapi/gps/approvepost.jsp"
                        + "?id=" + postId + "&empcode=" + empcode + "&status=approved";
                java.net.URL url = new java.net.URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                String body = sb.toString().trim();
                boolean success = false;
                try { success = new JSONObject(body).optBoolean("success", false); }
                catch (Exception ignored) { success = body.contains("true"); }
                final boolean ok = success;
                runOnUiThread(() -> {
                    if (ok) {
                        btnApprove.setCardBackgroundColor(Color.parseColor("#34A853"));
                        tvApprove.setText("Approved ✓");
                        Toast.makeText(this, "Visit approved!", Toast.LENGTH_SHORT).show();
                    } else {
                        btnApprove.setEnabled(true);
                        tvApprove.setText("Approve");
                        Toast.makeText(this, "Approval failed. Check server.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnApprove.setEnabled(true);
                    tvApprove.setText("Approve");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private String getStatusColor(String status) {
        String s = status.toLowerCase().trim();
        if (s.contains("approved") || s.contains("completed")) return "#34A853";
        if (s.contains("pending"))  return "#FB8C00";
        if (s.contains("review"))   return "#1A73E8";
        if (s.contains("rejected")) return "#E53935";
        return "#8A93B2";
    }

    // ── buildImageUrl — UNCHANGED ──────────────────────────────────
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

    // ── formatTime — UNCHANGED ─────────────────────────────────────
    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("MMM dd, yyyy • hh:mm a",  Locale.getDefault());
            Date d = in.parse(raw);
            return d != null ? out.format(d) : raw;
        } catch (ParseException e) { return raw; }
    }

    // ── httpGet — UNCHANGED ────────────────────────────────────────
    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
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

    private void showError(String msg) {
        progressBar.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ── Image pager adapter — UNCHANGED ───────────────────────────
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
            Glide.with(AdminSearchActivity.this)
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