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

public class AdminActivity extends BaseActivity {

    private static final String TAG = "AdminActivity";

    // ── API: fetches ALL posts from all faculty — no empcode needed ──
    private static final String ALL_POSTS_API = "http://160.187.169.14/jspapi/gps/getallposts.jsp";
    private static final String PHOTO_BASE    = "http://160.187.169.24/counselling_jspapi/StaffPhotos/";

    // ── UI ─────────────────────────────────────────────────────────
    private TextView     tvGreeting, tvAvatarLetter, tvPostCount;
    private LinearLayout containerPosts, layoutEmptyState;
    private FrameLayout  loadingOverlay;
    private ImageView    btnLogout;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ──────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1A73E8"));
        setContentView(R.layout.activity_admin);
        initViews();
        loadAdminGreeting();
        loadAllPosts();           // ← auto-load on open, no search needed
    }

    private void initViews() {
        tvGreeting       = findViewById(R.id.tvGreeting);
        tvAvatarLetter   = findViewById(R.id.tvAvatarLetter);
        containerPosts   = findViewById(R.id.containerPosts);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        loadingOverlay   = findViewById(R.id.loadingOverlay);
        tvPostCount      = findViewById(R.id.tvPostCount);
        btnLogout        = findViewById(R.id.btnLogout);
    }

    private void loadAdminGreeting() {
        String name = getIntent().getStringExtra("USER_NAME");
        if (name == null || name.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
            name = prefs.getString(LoginActivity.KEY_USER_NAME, "Admin");
        }
        String display = (name == null || name.isEmpty()) ? "Admin" : name;
        tvGreeting.setText("Hello, " + display);
        tvAvatarLetter.setText(String.valueOf(display.charAt(0)).toUpperCase());

        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    // ══════════════════════════════════════════════════════════════
    //  Fetch all posts — called once on screen open
    // ══════════════════════════════════════════════════════════════
    private void loadAllPosts() {
        showLoading(true);
        layoutEmptyState.setVisibility(View.GONE);
        containerPosts.removeAllViews();

        executor.execute(() -> {
            try {
                String json = httpGet(ALL_POSTS_API);
                Log.d(TAG, "All posts response: " + json);
                runOnUiThread(() -> renderResults(json));
            } catch (Exception e) {
                Log.e(TAG, "Fetch error: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    showEmpty("Network error: " + e.getMessage());
                });
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  Parse JSON and render cards
    //
    //  Expected format — same structure as gethistory.jsp but for
    //  ALL employees:
    //  [
    //    {
    //      "empcode":     "02713",
    //      "description": "library",
    //      "latitude":    "16.2333264",
    //      "longitude":   "80.5484609",
    //      "time":        "2026-03-06 15:36:54.0",
    //      "images":      ["http://...jpg", ...]
    //    }, ...
    //  ]
    // ══════════════════════════════════════════════════════════════
    private void renderResults(String json) {
        showLoading(false);

        if (json == null || json.trim().isEmpty()) {
            showEmpty("No posts available.");
            tvPostCount.setText("0 posts");
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

            if (array.length() == 0) {
                showEmpty("No posts found.");
                tvPostCount.setText("0 posts");
                return;
            }

            tvPostCount.setText(array.length() + (array.length() == 1 ? " post" : " posts"));
            layoutEmptyState.setVisibility(View.GONE);

            LayoutInflater inflater = LayoutInflater.from(this);

            for (int i = 0; i < array.length(); i++) {
                JSONObject post = array.getJSONObject(i);

                // empcode is per-post now since this returns all faculty
                String empcode = post.optString("empcode",     "");
                String desc    = post.optString("description", "");
                String lat     = post.optString("latitude",    "");
                String lng     = post.optString("longitude",   "");
                String time    = post.optString("time",        "");

                List<String> imgs = new ArrayList<>();
                JSONArray imgArr  = post.optJSONArray("images");
                if (imgArr != null) {
                    for (int j = 0; j < imgArr.length(); j++) {
                        imgs.add(imgArr.getString(j));
                    }
                }

                View card = inflater.inflate(R.layout.item_post_card, containerPosts, false);
                bindCard(card, empcode, desc, lat, lng, time, imgs);
                containerPosts.addView(card);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage() + "\nRaw:\n" + json);
            showEmpty("Error reading response: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Bind one post card
    // ══════════════════════════════════════════════════════════════
    private void bindCard(View card,
                          String empcode,
                          String description,
                          String latitude,
                          String longitude,
                          String time,
                          List<String> imageUrls) {

        ImageView    ivPhoto    = card.findViewById(R.id.ivCardFacultyPhoto);
        TextView     tvName     = card.findViewById(R.id.tvCardFacultyName);
        TextView     tvDept     = card.findViewById(R.id.tvCardDept);
        TextView     tvLocation = card.findViewById(R.id.tvCardLocation);
        TextView     tvDesc     = card.findViewById(R.id.tvCardDescription);
        TextView     tvTime     = card.findViewById(R.id.tvCardTime);
        ViewPager2   pager      = card.findViewById(R.id.viewPagerImages);
        LinearLayout dots       = card.findViewById(R.id.layoutDots);
        CardView     btnMap     = card.findViewById(R.id.btnViewMap);
        CardView     btnApprove = card.findViewById(R.id.btnApprove);
        TextView     tvApprove  = card.findViewById(R.id.tvApproveLabel);

        // ── Faculty header: photo + emp code ──────────────────────
        // Pad empcode for photo URL  e.g. "2713" → "02713"
        String paddedCode = empcode;
        try {
            paddedCode = String.format("%05d", Integer.parseInt(empcode.replaceAll("\\D", "")));
        } catch (NumberFormatException ignored) {}

        tvName.setText(paddedCode);
        tvDept.setText("Faculty");

        Glide.with(this)
                .load(PHOTO_BASE + paddedCode + ".JPG")
                .apply(new RequestOptions()
                        .transform(new CircleCrop())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(android.R.drawable.ic_menu_myplaces)
                        .error(android.R.drawable.ic_menu_myplaces))
                .into(ivPhoto);

        // ── Images ────────────────────────────────────────────────
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
        }

        // ── Text ──────────────────────────────────────────────────
        tvDesc.setText(description.isEmpty() ? "(No description)" : description);
        tvTime.setText(formatTime(time));
        tvLocation.setText(latitude + ", " + longitude);
        reverseGeocode(latitude, longitude, tvLocation);

        // ── View Map ──────────────────────────────────────────────
        final String finalCode = paddedCode;
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

        // ── Approve ───────────────────────────────────────────────
        btnApprove.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Approve Visit")
                        .setMessage("Approve this visit by employee " + finalCode + "?")
                        .setPositiveButton("Approve", (d, w) -> {
                            btnApprove.setCardBackgroundColor(Color.parseColor("#34A853"));
                            tvApprove.setText("Approved ✓");
                            btnApprove.setEnabled(false);
                            Toast.makeText(this, "Visit approved!", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );
    }

    // ── Dot indicators ─────────────────────────────────────────────
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

    // ── Reverse geocode ────────────────────────────────────────────
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

    // ── Format "2026-03-06 15:36:54.0" → "Mar 06, 2026 • 03:36 PM" ─
    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
            Date d = in.parse(raw);
            return d != null ? out.format(d) : raw;
        } catch (ParseException e) { return raw; }
    }

    // ── HTTP GET (background thread only) ─────────────────────────
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
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
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

    private void showLoading(boolean show) {
        runOnUiThread(() -> loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    private void showEmpty(String msg) {
        layoutEmptyState.setVisibility(View.VISIBLE);
        if (layoutEmptyState.getChildCount() > 2)
            ((TextView) layoutEmptyState.getChildAt(2)).setText(msg);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showLogoutDialog() {
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
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ══════════════════════════════════════════════════════════════
    //  ViewPager2 adapter for post images
    // ══════════════════════════════════════════════════════════════
    private class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.VH> {

        private final List<String> urls;
        ImagePagerAdapter(List<String> urls) { this.urls = urls; }

        @NonNull
        @Override
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
            Glide.with(AdminActivity.this)
                    .load(urls.get(pos))
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