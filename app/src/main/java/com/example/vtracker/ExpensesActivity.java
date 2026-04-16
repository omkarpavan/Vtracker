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
    private static final String SERVER_BASE     = "http://160.187.169.14";
    private static final int    CAMERA_REQUEST  = 2001;
    private static final int    GALLERY_REQUEST = 2002;
    private static final int    MAX_PHOTOS      = 5;
    private static final String LINE_END        = "\r\n";
    private static final String TWO_HYPHENS     = "--";

    private ImageView    btnBack;
    private TextView     tvFromDate, tvToDate;
    private LinearLayout btnTakeReceiptPhoto, layoutReceiptPhotos;
    private EditText     etTripName;           // ✅ NEW: Trip Name field
    private EditText     etClaimAmount, etExpenseDescription;
    private CardView     btnSubmitExpense;
    private LinearLayout navHome, navPost, navHistory, navExpenses, navProfile;

    private String employeeId = "";
    private String userName   = "";
    private String selectedFromDate = "";
    private String selectedToDate   = "";
    private final List<Bitmap> receiptBitmaps = new ArrayList<>();

    // ✅ Fix: Store camera photo URI & File to read full-res image after capture
    private Uri    currentPhotoUri  = null;
    private File   currentPhotoFile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_expenses);

        loadUserData();
        initViews();
        setListeners();
    }

    private void loadUserData() {
        employeeId = getIntent().getStringExtra("EMPLOYEE_ID");
        userName   = getIntent().getStringExtra("USER_NAME");
        if (employeeId == null || employeeId.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE);
            employeeId = prefs.getString(LoginActivity.KEY_EMPLOYEE_ID, "");
            userName   = prefs.getString(LoginActivity.KEY_USER_NAME, "");
        }
    }

    private void initViews() {
        btnBack              = findViewById(R.id.btnBack);
        tvFromDate           = findViewById(R.id.tvFromDate);
        tvToDate             = findViewById(R.id.tvToDate);
        btnTakeReceiptPhoto  = findViewById(R.id.btnTakeReceiptPhoto);
        layoutReceiptPhotos  = findViewById(R.id.layoutReceiptPhotos);
        etTripName           = findViewById(R.id.etTripName);          // ✅ NEW
        etClaimAmount        = findViewById(R.id.etClaimAmount);
        etExpenseDescription = findViewById(R.id.etExpenseDescription);
        btnSubmitExpense     = findViewById(R.id.btnSubmitExpense);
        navHome              = findViewById(R.id.navHome);
        navPost              = findViewById(R.id.navPost);
        navHistory           = findViewById(R.id.navHistory);
        navExpenses          = findViewById(R.id.navExpenses);
        navProfile           = findViewById(R.id.navProfile);
    }

    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());
        tvFromDate.setOnClickListener(v -> showDatePicker(true));
        tvToDate.setOnClickListener(v   -> showDatePicker(false));

        btnTakeReceiptPhoto.setOnClickListener(v -> {
            if (receiptBitmaps.size() >= MAX_PHOTOS) {
                Toast.makeText(this, "Max " + MAX_PHOTOS + " photos.", Toast.LENGTH_SHORT).show();
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

    private void showPhotoOptions() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Add Receipt Photo")
                .setItems(new String[]{"Take Photo", "Choose from Gallery"}, (d, which) -> {
                    if (which == 0) {
                        launchCamera();   // ✅ Fixed camera launch
                    } else {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        startActivityForResult(
                                Intent.createChooser(i, "Select Receipt"), GALLERY_REQUEST);
                    }
                }).show();
    }

    // ✅ Fix: Create a temp file and pass its FileProvider URI to the camera
    private void launchCamera() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String imageFileName = "RECEIPT_" + timeStamp + ".jpg";

            // Save to app's external Pictures dir (no WRITE_EXTERNAL_STORAGE needed on API 29+)
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            currentPhotoFile = File.createTempFile(imageFileName, ".jpg", storageDir);

            currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    currentPhotoFile
            );

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        Bitmap bitmap = null;

        if (requestCode == CAMERA_REQUEST) {
            // ✅ Fix: Read full-res image from the file we saved via FileProvider URI
            try {
                if (currentPhotoFile != null && currentPhotoFile.exists()) {
                    bitmap = decodeSampledBitmap(currentPhotoFile.getAbsolutePath(), 1024, 1024);
                } else if (currentPhotoUri != null) {
                    InputStream is = getContentResolver().openInputStream(currentPhotoUri);
                    bitmap = BitmapFactory.decodeStream(is);
                }
            } catch (Exception e) {
                Log.e(TAG, "Camera result error: " + e.getMessage());
                Toast.makeText(this, "Failed to load photo.", Toast.LENGTH_SHORT).show();
            }

        } else if (requestCode == GALLERY_REQUEST && data != null && data.getData() != null) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                bitmap = BitmapFactory.decodeStream(is);
            } catch (Exception e) {
                Log.e(TAG, "Gallery error: " + e.getMessage());
            }
        }

        if (bitmap != null) addReceiptPhoto(bitmap);
    }

    // ✅ Helper: decode large images efficiently without OOM
    private Bitmap decodeSampledBitmap(String filePath, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        int inSampleSize = 1;
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            int halfHeight = options.outHeight / 2;
            int halfWidth  = options.outWidth  / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth  / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        options.inSampleSize    = inSampleSize;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

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

        final int idx = receiptBitmaps.size() - 1;
        iv.setOnLongClickListener(v -> {
            receiptBitmaps.remove(idx);
            layoutReceiptPhotos.removeView(iv);
            Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
            return true;
        });

        layoutReceiptPhotos.addView(iv);
    }

    private void submitExpense() {
        // ✅ NEW: Read & validate Trip Name
        String tripName = etTripName.getText().toString().trim();
        String amount   = etClaimAmount.getText().toString().trim();
        String desc     = etExpenseDescription.getText().toString().trim();

        // Validation
        if (tripName.isEmpty()) {
            etTripName.setError("Please enter a trip name.");
            etTripName.requestFocus();
            return;
        }
        if (selectedFromDate.isEmpty() || selectedToDate.isEmpty()) {
            Toast.makeText(this, "Please select trip dates.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount.isEmpty() || Double.parseDouble(amount) == 0) {
            etClaimAmount.setError("Enter claim amount.");
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

        final List<Bitmap> photos = new ArrayList<>(receiptBitmaps);
        new Thread(() -> {
            try {
                String boundary = "----Boundary" + System.currentTimeMillis();
                URL url = new URL(SERVER_BASE + "/jspapi/gps/submitexpense.jsp");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("Content-Type",
                        "multipart/form-data; boundary=" + boundary);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                writeField(dos, boundary, "empcode",     employeeId);
                writeField(dos, boundary, "trip_name",   tripName);   // ✅ NEW: send trip name
                writeField(dos, boundary, "from_date",   selectedFromDate);
                writeField(dos, boundary, "to_date",     selectedToDate);
                writeField(dos, boundary, "amount",      amount);
                writeField(dos, boundary, "description", desc);

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

                dos.writeBytes(TWO_HYPHENS + boundary + TWO_HYPHENS + LINE_END);
                dos.flush();
                dos.close();

                int code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String body = sb.toString().trim();
                Log.d(TAG, "submitexpense: " + body);

                boolean ok = false;
                try { ok = new JSONObject(body).optBoolean("success", false); }
                catch (Exception ignored) { ok = body.contains("success"); }

                final boolean success = ok;
                runOnUiThread(() -> {
                    pd.dismiss();
                    if (success) {
                        Toast.makeText(this, "Expense submitted successfully!",
                                Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Failed: " + body, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void writeField(DataOutputStream dos, String boundary,
                            String name, String value) throws Exception {
        dos.writeBytes(TWO_HYPHENS + boundary + LINE_END);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\""
                + LINE_END + LINE_END);
        dos.writeBytes(value);
        dos.writeBytes(LINE_END);
    }
}