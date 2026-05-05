package com.example.vtracker;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpensesActivity extends BaseActivity {

    private static final String TAG             = "ExpensesActivity";
    private static final String SERVER_BASE     = "http://160.187.169.24";
    private static final int    CAMERA_REQUEST  = 2001;
    private static final int    GALLERY_REQUEST = 2002;
    private static final int    MAX_PHOTOS      = 5;
    private static final String LINE_END        = "\r\n";
    private static final String TWO_HYPHENS     = "--";

    private ImageView    btnBack;
    private TextView     tvFromDate, tvToDate;
    private LinearLayout btnTakeReceiptPhoto, layoutReceiptPhotos;
    private EditText     etTripName;
    private EditText     etClaimAmount, etExpenseDescription;
    private CardView     btnSubmitExpense;
    private LinearLayout navHome, navPost, navHistory, navExpenses, navProfile;

    private String employeeId       = "";
    private String userName         = "";
    private String selectedFromDate = "";
    private String selectedToDate   = "";

    private final List<Bitmap> receiptBitmaps = new ArrayList<>();
    private Uri  currentPhotoUri  = null;
    private File currentPhotoFile = null;

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_expenses);

        loadUserData();
        initViews();
        setListeners();
        updateBottomNavUI();
    }

    // ─────────────────────────────────────────────────────────────
    private void loadUserData() {
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");
        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
            userName   = prefs.getString(LoginActivity.KEY_USER_NAME, "");
        }
    }

    // ─────────────────────────────────────────────────────────────
    private void initViews() {
        btnBack              = findViewById(R.id.btnBack);
        tvFromDate           = findViewById(R.id.tvFromDate);
        tvToDate             = findViewById(R.id.tvToDate);
        btnTakeReceiptPhoto  = findViewById(R.id.btnTakeReceiptPhoto);
        layoutReceiptPhotos  = findViewById(R.id.layoutReceiptPhotos);
        etTripName           = findViewById(R.id.etTripName);
        etClaimAmount        = findViewById(R.id.etClaimAmount);
        etExpenseDescription = findViewById(R.id.etExpenseDescription);
        btnSubmitExpense     = findViewById(R.id.btnSubmitExpense);
        navHome              = findViewById(R.id.navHome);
        navPost              = findViewById(R.id.navPost);
        navHistory           = findViewById(R.id.navHistory);
        navExpenses          = findViewById(R.id.navExpenses);
        navProfile           = findViewById(R.id.navProfile);
    }

    // ─────────────────────────────────────────────────────────────
    private void updateBottomNavUI() {
        if (navExpenses == null) return;
        for (int i = 0; i < navExpenses.getChildCount(); i++) {
            View child = navExpenses.getChildAt(i);
            if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(Color.parseColor("#1A73E8"));
            } else if (child instanceof TextView) {
                ((TextView) child).setTextColor(Color.parseColor("#1A73E8"));
                ((TextView) child).setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());
        tvFromDate.setOnClickListener(v -> showDatePicker(true));
        tvToDate.setOnClickListener(v   -> showDatePicker(false));

        btnTakeReceiptPhoto.setOnClickListener(v -> {
            if (receiptBitmaps.size() >= MAX_PHOTOS) {
                Toast.makeText(this, "Max " + MAX_PHOTOS + " photos allowed.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            showPhotoOptions();
        });

        btnSubmitExpense.setOnClickListener(v -> submitExpense());

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
        navHistory.setOnClickListener(v -> {
            Intent i = new Intent(this, HistoryActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId);
            i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
        navExpenses.setOnClickListener(v -> { /* already here */ });
        navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId);
            i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
    }

    // ─────────────────────────────────────────────────────────────
    private void showDatePicker(boolean isFrom) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            String display = String.format("%02d-%02d-%04d", day, month + 1, year);
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

    // ─────────────────────────────────────────────────────────────
    private void showPhotoOptions() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add Receipt Photo")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (d, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        startActivityForResult(
                                Intent.createChooser(i, "Select Receipts"), GALLERY_REQUEST);
                    }
                }).show();
    }

    // ─────────────────────────────────────────────────────────────
    private void launchCamera() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            currentPhotoFile = File.createTempFile("RECEIPT_" + timeStamp, ".jpg", storageDir);
            currentPhotoUri  = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    currentPhotoFile);

            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, currentPhotoUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            } else {
                Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "launchCamera error: " + e.getMessage());
            Toast.makeText(this, "Could not open camera: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == CAMERA_REQUEST) {
            Bitmap bitmap = null;
            try {
                if (currentPhotoFile != null && currentPhotoFile.exists()) {
                    bitmap = decodeSampledBitmap(currentPhotoFile.getAbsolutePath(), 1024, 1024);
                } else if (currentPhotoUri != null) {
                    bitmap = decodeBitmapFromUri(currentPhotoUri);
                }
            } catch (Exception e) {
                Log.e(TAG, "Camera result error: " + e.getMessage());
                Toast.makeText(this, "Failed to load photo.", Toast.LENGTH_SHORT).show();
            }
            if (bitmap != null) addReceiptPhoto(bitmap);

        } else if (requestCode == GALLERY_REQUEST && data != null) {
            if (data.getClipData() != null) {
                int count     = data.getClipData().getItemCount();
                int remaining = MAX_PHOTOS - receiptBitmaps.size();
                int toAdd     = Math.min(count, remaining);
                for (int i = 0; i < toAdd; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    Bitmap b = decodeBitmapFromUri(uri);
                    if (b != null) addReceiptPhoto(b);
                }
            } else if (data.getData() != null) {
                Bitmap b = decodeBitmapFromUri(data.getData());
                if (b != null) addReceiptPhoto(b);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nullable
    private Bitmap decodeBitmapFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            int inSampleSize = 1;
            int reqW = 1024, reqH = 1024;
            if (opts.outHeight > reqH || opts.outWidth > reqW) {
                int halfH = opts.outHeight / 2;
                int halfW = opts.outWidth  / 2;
                while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW)
                    inSampleSize *= 2;
            }
            opts.inSampleSize    = inSampleSize;
            opts.inJustDecodeBounds = false;
            is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
            is.close();
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "decodeBitmapFromUri error", e);
            return null;
        }
    }

    private Bitmap decodeSampledBitmap(String filePath, int reqW, int reqH) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, opts);

        int inSampleSize = 1;
        if (opts.outHeight > reqH || opts.outWidth > reqW) {
            int halfH = opts.outHeight / 2;
            int halfW = opts.outWidth  / 2;
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW)
                inSampleSize *= 2;
        }
        opts.inSampleSize    = inSampleSize;
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, opts);
    }

    // ─────────────────────────────────────────────────────────────
    private void addReceiptPhoto(Bitmap bitmap) {
        receiptBitmaps.add(bitmap);

        int dp100 = Math.round(100 * getResources().getDisplayMetrics().density);
        int dp8   = Math.round(8   * getResources().getDisplayMetrics().density);

        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp100, dp100);
        p.setMarginEnd(dp8);
        iv.setLayoutParams(p);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bitmap);
        iv.setBackgroundColor(Color.parseColor("#E8EEFF"));

        iv.setOnLongClickListener(v -> {
            int index = layoutReceiptPhotos.indexOfChild(iv);
            if (index != -1) {
                receiptBitmaps.remove(index);
                layoutReceiptPhotos.removeView(iv);
                Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        layoutReceiptPhotos.addView(iv);
    }

    // ─────────────────────────────────────────────────────────────
    // SUBMIT  –  sends trip_name and description as separate fields
    // ─────────────────────────────────────────────────────────────
    private void submitExpense() {
        String tripName = etTripName.getText().toString().trim();
        String amount   = etClaimAmount.getText().toString().trim();
        String desc     = etExpenseDescription.getText().toString().trim();

        // ── Validation ────────────────────────────────────────────
        if (tripName.isEmpty()) {
            etTripName.setError("Please enter a trip name.");
            etTripName.requestFocus();
            return;
        }
        if (selectedFromDate.isEmpty()) {
            Toast.makeText(this, "Please select a From date.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedToDate.isEmpty()) {
            Toast.makeText(this, "Please select a To date.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount.isEmpty()) {
            etClaimAmount.setError("Enter claim amount.");
            etClaimAmount.requestFocus();
            return;
        }
        try {
            if (Double.parseDouble(amount) <= 0) {
                etClaimAmount.setError("Amount must be greater than 0.");
                etClaimAmount.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            etClaimAmount.setError("Invalid amount.");
            etClaimAmount.requestFocus();
            return;
        }
        if (desc.isEmpty()) {
            etExpenseDescription.setError("Please describe the expense.");
            etExpenseDescription.requestFocus();
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Submitting expense...");
        pd.setCancelable(false);
        pd.show();

        // Capture final copies for the background thread
        final String       finalEmpId    = employeeId;
        final String       finalName     = userName;       // employee name
        final String       finalTripName = tripName;       // trip name — separate field
        final String       finalFromDate = selectedFromDate;
        final String       finalToDate   = selectedToDate;
        final String       finalAmount   = amount;
        final String       finalDesc     = desc;           // plain description — no "Trip:" prefix
        final List<Bitmap> photos        = new ArrayList<>(receiptBitmaps);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String boundary = "----Boundary" + System.currentTimeMillis();
                URL url = new URL(SERVER_BASE + "/VTracker/submitexpense.jsp");

                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("Content-Type",
                        "multipart/form-data; boundary=" + boundary);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

                // ── Text fields ───────────────────────────────────
                writeField(dos, boundary, "empcode",     finalEmpId);
                writeField(dos, boundary, "name",        finalName);      // employee name
                writeField(dos, boundary, "trip_name",   finalTripName);  // trip name
                writeField(dos, boundary, "from_date",   finalFromDate);
                writeField(dos, boundary, "to_date",     finalToDate);
                writeField(dos, boundary, "amount",      finalAmount);
                writeField(dos, boundary, "description", finalDesc);

                // ── Image fields ──────────────────────────────────
                for (int i = 0; i < photos.size(); i++) {
                    String fn = "receipt_" + System.currentTimeMillis() + "_" + i + ".jpg";
                    dos.writeBytes(TWO_HYPHENS + boundary + LINE_END);
                    dos.writeBytes("Content-Disposition: form-data; name=\"photo\"; "
                            + "filename=\"" + fn + "\"" + LINE_END);
                    dos.writeBytes("Content-Type: image/jpeg" + LINE_END + LINE_END);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    photos.get(i).compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    dos.write(baos.toByteArray());
                    dos.writeBytes(LINE_END);
                }

                // ── End boundary ──────────────────────────────────
                dos.writeBytes(TWO_HYPHENS + boundary + TWO_HYPHENS + LINE_END);
                dos.flush();
                dos.close();

                // ── Read response ─────────────────────────────────
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "HTTP response code: " + responseCode);

                InputStream is = (responseCode == 200)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                String body = sb.toString().trim();
                Log.d(TAG, "submitexpense response: " + body);

                // ── Parse success ─────────────────────────────────
                boolean ok = false;
                try {
                    JSONObject jsonObj = new JSONObject(body);
                    ok = jsonObj.optBoolean("success", false);
                    if (!ok) {
                        String msg = jsonObj.optString("message", "Unknown error");
                        Log.e(TAG, "Server error: " + msg);
                    }
                } catch (Exception ignored) {
                    ok = body.toLowerCase().contains("\"success\":true");
                }

                final boolean success  = ok;
                final String  respBody = body;
                runOnUiThread(() -> {
                    pd.dismiss();
                    if (success) {
                        Toast.makeText(this,
                                "Expense submitted successfully!", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Submission failed: " + respBody, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "submitExpense thread error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(this,
                            "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────
    /** Write a single text field into the multipart body. */
    private void writeField(DataOutputStream dos, String boundary,
                            String name, String value) throws Exception {
        dos.writeBytes(TWO_HYPHENS + boundary + LINE_END);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\""
                + LINE_END + LINE_END);
        dos.write(value.getBytes("UTF-8"));
        dos.writeBytes(LINE_END);
    }
}