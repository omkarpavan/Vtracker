package com.example.vtracker;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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
    private EditText     etClaimAmount, etExpenseDescription;
    private CardView     btnSubmitExpense;
    private LinearLayout navHome, navPost, navHistory, navExpenses, navProfile;

    private String employeeId = "";
    private String userName   = "";
    private String selectedFromDate = "";
    private String selectedToDate   = "";
    private final List<Bitmap> receiptBitmaps = new ArrayList<>();

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
        btnBack             = findViewById(R.id.btnBack);
        tvFromDate          = findViewById(R.id.tvFromDate);
        tvToDate            = findViewById(R.id.tvToDate);
        btnTakeReceiptPhoto = findViewById(R.id.btnTakeReceiptPhoto);
        layoutReceiptPhotos = findViewById(R.id.layoutReceiptPhotos);
        etClaimAmount       = findViewById(R.id.etClaimAmount);
        etExpenseDescription= findViewById(R.id.etExpenseDescription);
        btnSubmitExpense    = findViewById(R.id.btnSubmitExpense);
        navHome             = findViewById(R.id.navHome);
        navPost             = findViewById(R.id.navPost);
        navHistory          = findViewById(R.id.navHistory);
        navExpenses         = findViewById(R.id.navExpenses);
        navProfile          = findViewById(R.id.navProfile);
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
            i.putExtra("EMPLOYEE_ID", employeeId); i.putExtra("USER_NAME", userName);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(i);
        });
        navPost.setOnClickListener(v -> {
            Intent i = new Intent(this, PostVisitActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId); i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
        navHistory.setOnClickListener(v -> {
            Intent i = new Intent(this, HistoryActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId); i.putExtra("USER_NAME", userName);
            startActivity(i);
        });
        navExpenses.setOnClickListener(v -> { /* already here */ });
        navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("EMPLOYEE_ID", employeeId); i.putExtra("USER_NAME", userName);
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
                        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                        if (i.resolveActivity(getPackageManager()) != null)
                            startActivityForResult(i, CAMERA_REQUEST);
                    } else {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        startActivityForResult(Intent.createChooser(i, "Select Receipt"), GALLERY_REQUEST);
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Bitmap bitmap = null;
        if (requestCode == CAMERA_REQUEST) {
            Bundle extras = data.getExtras();
            if (extras != null) bitmap = (Bitmap) extras.get("data");
        } else if (requestCode == GALLERY_REQUEST && data.getData() != null) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                bitmap = BitmapFactory.decodeStream(is);
            } catch (Exception e) { Log.e(TAG, "Gallery: " + e.getMessage()); }
        }
        if (bitmap != null) addReceiptPhoto(bitmap);
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
        // Use a simple rounded background that already exists in the project
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
        String amount = etClaimAmount.getText().toString().trim();
        String desc   = etExpenseDescription.getText().toString().trim();

        if (selectedFromDate.isEmpty() || selectedToDate.isEmpty()) {
            Toast.makeText(this, "Please select trip dates.", Toast.LENGTH_SHORT).show(); return;
        }
        if (amount.isEmpty() || Double.parseDouble(amount) == 0) {
            etClaimAmount.setError("Enter claim amount."); etClaimAmount.requestFocus(); return;
        }
        if (desc.isEmpty()) {
            etExpenseDescription.setError("Please describe the expense.");
            etExpenseDescription.requestFocus(); return;
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
                conn.setDoInput(true); conn.setDoOutput(true); conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(30000); conn.setReadTimeout(30000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                writeField(dos, boundary, "empcode",     employeeId);
                writeField(dos, boundary, "from_date",   selectedFromDate);
                writeField(dos, boundary, "to_date",     selectedToDate);
                writeField(dos, boundary, "amount",      amount);
                writeField(dos, boundary, "description", desc);

                for (int i = 0; i < photos.size(); i++) {
                    String fn = "receipt_" + System.currentTimeMillis() + "_" + i + ".jpg";
                    dos.writeBytes(TWO_HYPHENS + boundary + LINE_END);
                    dos.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"" + fn + "\"" + LINE_END);
                    dos.writeBytes("Content-Type: image/jpeg" + LINE_END + LINE_END);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    photos.get(i).compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    dos.write(baos.toByteArray());
                    dos.writeBytes(LINE_END);
                }
                dos.writeBytes(TWO_HYPHENS + boundary + TWO_HYPHENS + LINE_END);
                dos.flush(); dos.close();

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
                        Toast.makeText(this, "Expense submitted successfully!", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Failed: " + body, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { pd.dismiss(); Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void writeField(DataOutputStream dos, String boundary, String name, String value) throws Exception {
        dos.writeBytes(TWO_HYPHENS + boundary + LINE_END);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + LINE_END + LINE_END);
        dos.writeBytes(value);
        dos.writeBytes(LINE_END);
    }
}