package com.example.vtracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// ── Extends BaseActivity so Developer Options check runs here too ──
public class PostVisitActivity extends BaseActivity {

    // ── Constants ──────────────────────────────────────────────────
    private static final int    PERMISSION_REQUEST_CODE        = 1001;
    private static final int    CAMERA_REQUEST_CODE            = 1002;
    private static final int    GALLERY_REQUEST_CODE           = 1003;
    private static final int    CAMERA_PERMISSION_REQUEST_CODE = 1004;
    private static final int    MAX_PHOTOS                     = 5;   // max images per post
    private static final String TAG                            = "PostVisitActivity";

    private static final String API_POST_WORK = "http://160.187.169.14/jspapi/gps/postwork.jsp";

    private static final String LINE_END    = "\r\n";
    private static final String TWO_HYPHENS = "--";

    // ── UI ─────────────────────────────────────────────────────────
    private TextView     tvCoordinates, tvAddress, tvAutoSave;
    private EditText     etVisitNotes;
    private LinearLayout btnTakePhoto, layoutPhotos;
    private android.widget.Button btnSubmitReport;
    private ImageButton  btnRecenter;

    // ── Map ────────────────────────────────────────────────────────
    private MapView              osmMapView;
    private MyLocationNewOverlay locationOverlay;
    private Marker               currentMarker;

    // ── Location ───────────────────────────────────────────────────
    private FusedLocationProviderClient fusedClient;
    private LocationCallback            locationCallback;
    private double                      currentLat = 0.0, currentLng = 0.0;

    // ── Data ───────────────────────────────────────────────────────
    // ★ Changed: List of bitmaps instead of a single capturedBitmap
    private final List<Bitmap> capturedBitmaps = new ArrayList<>();
    private String employeeId = "";
    private String userName   = "";

    // ── Auto-save ──────────────────────────────────────────────────
    private final Handler  autoSaveHandler  = new Handler(Looper.getMainLooper());
    private       Runnable autoSaveRunnable;

    // ──────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1A73E8"));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_post_visit);

        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");
        if (employeeId == null) employeeId = "";
        if (userName   == null) userName   = "";

        initViews();
        initToolbar();
        initOsmMap();
        initLocationClient();
        setupAutoSave();
        setListeners();
    }

    private void initViews() {
        tvCoordinates   = findViewById(R.id.tvCoordinates);
        tvAddress       = findViewById(R.id.tvAddress);
        tvAutoSave      = findViewById(R.id.tvAutoSave);
        etVisitNotes    = findViewById(R.id.etVisitNotes);
        btnTakePhoto    = findViewById(R.id.btnTakePhoto);
        layoutPhotos    = findViewById(R.id.layoutPhotos);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        btnRecenter     = findViewById(R.id.btnRecenter);
        osmMapView      = findViewById(R.id.osmMapView);
    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ── OSMDroid ───────────────────────────────────────────────────
    private void initOsmMap() {
        osmMapView.setTileSource(TileSourceFactory.MAPNIK);
        osmMapView.setMultiTouchControls(true);
        osmMapView.getController().setZoom(15.0);
        osmMapView.getController().setCenter(new GeoPoint(20.5937, 78.9629));
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), osmMapView);
        locationOverlay.enableMyLocation();
        osmMapView.getOverlays().add(locationOverlay);
    }

    private void updateOsmMap(double lat, double lng) {
        GeoPoint point = new GeoPoint(lat, lng);
        osmMapView.getController().animateTo(point);
        osmMapView.getController().setZoom(16.0);
        if (currentMarker != null) osmMapView.getOverlays().remove(currentMarker);
        currentMarker = new Marker(osmMapView);
        currentMarker.setPosition(point);
        currentMarker.setTitle("Your Location");
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        osmMapView.getOverlays().add(currentMarker);
        osmMapView.invalidate();
    }

    // ── Location ───────────────────────────────────────────────────
    private void initLocationClient() {
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) {
                    currentLat = loc.getLatitude();
                    currentLng = loc.getLongitude();
                    updateOsmMap(currentLat, currentLng);
                    updateCoordinatesUI(currentLat, currentLng);
                    reverseGeocode(currentLat, currentLng);
                }
            }
        };
        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                tvCoordinates.setText("Location unavailable");
                tvAddress.setText("Permission denied");
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCameraIntent();
            } else {
                Toast.makeText(this,
                        "Camera permission denied. Cannot take photo.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L).build();
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                currentLat = loc.getLatitude();
                currentLng = loc.getLongitude();
                updateOsmMap(currentLat, currentLng);
                updateCoordinatesUI(currentLat, currentLng);
                reverseGeocode(currentLat, currentLng);
            }
        });
    }

    @SuppressLint("DefaultLocale")
    private void updateCoordinatesUI(double lat, double lng) {
        tvCoordinates.setText(String.format("%.4f° %s, %.4f° %s",
                Math.abs(lat), lat >= 0 ? "N" : "S",
                Math.abs(lng), lng >= 0 ? "E" : "W"));
    }

    private void reverseGeocode(double lat, double lng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    StringBuilder sb = new StringBuilder();
                    if (addr.getThoroughfare() != null) sb.append(addr.getThoroughfare()).append(", ");
                    if (addr.getLocality()     != null) sb.append(addr.getLocality()).append(", ");
                    if (addr.getAdminArea()    != null) sb.append(addr.getAdminArea());
                    String result = sb.toString().trim().replaceAll(",\\s*$", "");
                    runOnUiThread(() -> tvAddress.setText(result.isEmpty() ? "Address unavailable" : result));
                } else {
                    runOnUiThread(() -> tvAddress.setText("Address unavailable"));
                }
            } catch (IOException e) {
                runOnUiThread(() -> tvAddress.setText("Address unavailable"));
            }
        }).start();
    }

    // ── Auto-save ──────────────────────────────────────────────────
    private void setupAutoSave() {
        autoSaveRunnable = () -> {
            String notes = etVisitNotes.getText().toString().trim();
            if (!notes.isEmpty()) {
                getSharedPreferences("draft_visit", MODE_PRIVATE)
                        .edit().putString("notes_draft_" + employeeId, notes).apply();
                tvAutoSave.setText("Saved");
                new Handler(Looper.getMainLooper())
                        .postDelayed(() -> tvAutoSave.setText("Auto-saving"), 2000);
            }
        };
        etVisitNotes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                autoSaveHandler.removeCallbacks(autoSaveRunnable);
                tvAutoSave.setText("Auto-saving...");
                autoSaveHandler.postDelayed(autoSaveRunnable, 1500);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        String draft = getSharedPreferences("draft_visit", MODE_PRIVATE)
                .getString("notes_draft_" + employeeId, "");
        if (!draft.isEmpty()) etVisitNotes.setText(draft);
    }

    // ── Listeners ──────────────────────────────────────────────────
    private void setListeners() {
        btnRecenter.setOnClickListener(v -> {
            if (currentLat != 0.0) {
                osmMapView.getController().animateTo(new GeoPoint(currentLat, currentLng));
                osmMapView.getController().setZoom(16.0);
            }
        });
        btnTakePhoto.setOnClickListener(v -> {
            if (capturedBitmaps.size() >= MAX_PHOTOS) {
                Toast.makeText(this,
                        "Maximum " + MAX_PHOTOS + " photos allowed.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            showPhotoOptions();
        });
        btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    // ── Photo Options ──────────────────────────────────────────────
    private void showPhotoOptions() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add Photo")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"},
                        (d, which) -> { if (which == 0) openCamera(); else openGallery(); })
                .show();
    }

    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            launchCameraIntent();
        }
    }

    private void launchCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        } else {
            Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        // ★ Allow multiple selection from gallery
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Photos"), GALLERY_REQUEST_CODE);
    }

    // ── Activity Result ────────────────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == CAMERA_REQUEST_CODE) {
            // Single photo from camera
            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap bitmap = (Bitmap) extras.get("data");
                if (bitmap != null) addPhoto(bitmap);
            }

        } else if (requestCode == GALLERY_REQUEST_CODE) {

            if (data.getClipData() != null) {
                // ★ Multiple images selected
                int count = data.getClipData().getItemCount();
                int remaining = MAX_PHOTOS - capturedBitmaps.size();
                int toAdd = Math.min(count, remaining);

                if (toAdd < count) {
                    Toast.makeText(this,
                            "Only " + toAdd + " of " + count + " photos added (max " + MAX_PHOTOS + ").",
                            Toast.LENGTH_SHORT).show();
                }

                for (int i = 0; i < toAdd; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    Bitmap bitmap = decodeBitmapFromUri(uri);
                    if (bitmap != null) addPhoto(bitmap);
                }

            } else if (data.getData() != null) {
                // Single image selected
                Bitmap bitmap = decodeBitmapFromUri(data.getData());
                if (bitmap != null) addPhoto(bitmap);
            }
        }
    }

    // ── Decode Bitmap from URI ─────────────────────────────────────
    @Nullable
    private Bitmap decodeBitmapFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Log.e(TAG, "Failed to decode image: " + e.getMessage());
            return null;
        }
    }

    // ── Add Photo to list + grid UI ────────────────────────────────
    private void addPhoto(Bitmap bitmap) {
        capturedBitmaps.add(bitmap);
        addPhotoToGrid(bitmap, capturedBitmaps.size() - 1);
    }

    // ── Render photo thumbnail in the horizontal scroll grid ───────
    private void addPhotoToGrid(Bitmap bitmap, int index) {
        ImageView imageView = new ImageView(this);
        int sizePx = dpToPx(100);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
        params.setMarginEnd(dpToPx(8));
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageBitmap(bitmap);
        imageView.setClipToOutline(true);
        imageView.setBackground(getDrawable(R.drawable.bg_photo_rounded));
        imageView.setTag(index); // store index for removal

        // Long-press to remove a specific photo
        imageView.setOnLongClickListener(v -> {
            int idx = (int) v.getTag();
            if (idx >= 0 && idx < capturedBitmaps.size()) {
                capturedBitmaps.remove(idx);
                layoutPhotos.removeView(imageView);
                // Re-index remaining views
                for (int i = 0; i < layoutPhotos.getChildCount(); i++) {
                    layoutPhotos.getChildAt(i).setTag(i);
                }
                Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        layoutPhotos.addView(imageView);
    }

    // ══════════════════════════════════════════════════════════════
    //  Submit Report
    // ══════════════════════════════════════════════════════════════
    private void submitReport() {
        String notes = etVisitNotes.getText().toString().trim();

        if (currentLat == 0.0 && currentLng == 0.0) {
            Toast.makeText(this, "Waiting for GPS location...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (notes.isEmpty()) {
            etVisitNotes.setError("Visit notes cannot be empty.");
            etVisitNotes.requestFocus();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Submitting report...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Snapshot the list on the main thread before background work
        List<Bitmap> photosToUpload = new ArrayList<>(capturedBitmaps);

        new Thread(() -> {
            try {
                String response = postToServer(
                        employeeId,
                        String.valueOf(currentLat),
                        String.valueOf(currentLng),
                        notes,
                        photosToUpload        // ★ pass the full list
                );

                Log.d(TAG, "postwork response: [" + response + "]");

                runOnUiThread(() -> {
                    progressDialog.dismiss();

                    if (response != null && (
                            response.toLowerCase().contains("success") ||
                                    response.toLowerCase().contains("posted")  ||
                                    response.toLowerCase().contains("inserted")||
                                    response.trim().equals("1"))) {

                        getSharedPreferences("draft_visit", MODE_PRIVATE)
                                .edit().remove("notes_draft_" + employeeId).apply();
                        Toast.makeText(this,
                                "Report submitted successfully!", Toast.LENGTH_SHORT).show();
                        finish();

                    } else {
                        String msg = (response != null && response.startsWith("<"))
                                ? "Server error — check postwork.jsp"
                                : "Response: " + response;
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Submit error: " + e.getMessage());
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  Multipart POST → postwork.jsp
    //
    //  Text fields : empcode, latitude, longitude, textbox
    //  File fields : multiple "photo" parts (one per image)
    //                JSP loops over all file items → inserts each
    //                into post_images table under the same post_id
    // ══════════════════════════════════════════════════════════════
    private String postToServer(String empcode, String latitude, String longitude,
                                String description, List<Bitmap> photos)
            throws IOException {

        String boundary = "----Boundary" + System.currentTimeMillis();
        HttpURLConnection conn = null;
        DataOutputStream dos  = null;

        try {
            URL url = new URL(API_POST_WORK);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);   // slightly longer for multi-image uploads
            conn.setReadTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            dos = new DataOutputStream(conn.getOutputStream());

            // ── Text fields ───────────────────────────────────────
            writeTextField(dos, boundary, "empcode",   empcode);
            writeTextField(dos, boundary, "latitude",  latitude);
            writeTextField(dos, boundary, "longitude", longitude);
            writeTextField(dos, boundary, "textbox",   description);

            // ── Image fields (one part per photo, all named "photo") ──
            // JSP iterates every non-form FileItem, so sending N parts
            // named "photo" results in N rows in post_images table.
            for (int i = 0; i < photos.size(); i++) {
                Bitmap bitmap = photos.get(i);
                String fileName = "photo_" + empcode + "_"
                        + System.currentTimeMillis() + "_" + i + ".jpg";

                dos.writeBytes(TWO_HYPHENS + boundary + LINE_END);
                dos.writeBytes("Content-Disposition: form-data; "
                        + "name=\"photo\"; filename=\"" + fileName + "\"" + LINE_END);
                dos.writeBytes("Content-Type: image/jpeg" + LINE_END);
                dos.writeBytes(LINE_END);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                dos.write(baos.toByteArray());
                dos.writeBytes(LINE_END);
            }

            // ── End boundary ──────────────────────────────────────
            dos.writeBytes(TWO_HYPHENS + boundary + TWO_HYPHENS + LINE_END);
            dos.flush();

            // ── Read response ─────────────────────────────────────
            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP code: " + code);

            InputStream is = (code == HttpURLConnection.HTTP_OK)
                    ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "HTTP " + code + " — no response body";

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line.trim());
            return sb.toString().trim();

        } finally {
            if (dos  != null) try { dos.close(); } catch (IOException ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    // ── Write one plain text field ─────────────────────────────────
    private void writeTextField(DataOutputStream dos, String boundary,
                                String name, String value) throws IOException {
        dos.writeBytes(TWO_HYPHENS + boundary + LINE_END);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + LINE_END);
        dos.writeBytes(LINE_END);
        dos.writeBytes(value);
        dos.writeBytes(LINE_END);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Lifecycle ──────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume(); // ← calls BaseActivity.onResume() → developer options check
        osmMapView.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        osmMapView.onPause();
        if (fusedClient != null && locationCallback != null)
            fusedClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
    }
}