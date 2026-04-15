package com.example.vtracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddFacultyActivity extends BaseActivity {

    private static final String TAG         = "AddFacultyActivity";
    private static final String SERVER_BASE = "http://160.187.169.14";
    private static final int    FILE_PICK   = 3001;

    // Views
    private ImageView  btnBack;
    private CardView   tabAddManually, tabBulkUpload;
    private CardView   cardManualForm, cardBulkUpload;
    private EditText   etFacultyName, etFacultyEmail, etFacultyPhone, etFacultyEmpId, etFacultyPassword;
    private CardView   btnCreateUser, btnSelectFile, btnUploadCSV;
    private TextView   tvSelectedFile;
    private LinearLayout navDashboard, navSearch, navApprovals, navProfile;

    // Data
    private String adminName = "";
    private String adminId   = "";
    private Uri    selectedCsvUri = null;
    private boolean isManualTab = true;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_add_faculty);

        loadAdminData();
        initViews();
        setListeners();
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

    private void initViews() {
        btnBack          = findViewById(R.id.btnBack);
        tabAddManually   = findViewById(R.id.tabAddManually);
        tabBulkUpload    = findViewById(R.id.tabBulkUpload);
        cardManualForm   = findViewById(R.id.cardManualForm);
        cardBulkUpload   = findViewById(R.id.cardBulkUpload);
        etFacultyName    = findViewById(R.id.etFacultyName);
        etFacultyEmail   = findViewById(R.id.etFacultyEmail);
        etFacultyPhone   = findViewById(R.id.etFacultyPhone);
        etFacultyEmpId   = findViewById(R.id.etFacultyEmpId);
        etFacultyPassword= findViewById(R.id.etFacultyPassword);
        btnCreateUser    = findViewById(R.id.btnCreateUser);
        btnSelectFile    = findViewById(R.id.btnSelectFile);
        btnUploadCSV     = findViewById(R.id.btnUploadCSV);
        tvSelectedFile   = findViewById(R.id.tvSelectedFile);
        navDashboard     = findViewById(R.id.navDashboard);
        navSearch        = findViewById(R.id.navSearch);
        navApprovals     = findViewById(R.id.navApprovals);
        navProfile       = findViewById(R.id.navProfile);
    }

    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Tab switching
        tabAddManually.setOnClickListener(v -> switchTab(true));
        tabBulkUpload.setOnClickListener(v  -> switchTab(false));

        btnCreateUser.setOnClickListener(v  -> createFaculty());
        btnSelectFile.setOnClickListener(v  -> pickCsvFile());
        btnUploadCSV.setOnClickListener(v   -> uploadCsv());

        // Bottom nav
        navDashboard.setOnClickListener(v -> {
            Intent i = new Intent(this, AdminActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });
        navSearch.setOnClickListener(v -> {
            Intent i = new Intent(this, AdminSearchActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
        navApprovals.setOnClickListener(v -> {
            Intent i = new Intent(this, ApprovalsActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
        navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
    }

    private TextView tvTabManual, tvTabBulk;

    private void switchTab(boolean manual) {
        // Lazily find the TextViews inside the CardViews
        if (tvTabManual == null) tvTabManual = tabAddManually.findViewById(android.R.id.text1);
        if (tvTabBulk   == null) tvTabBulk   = tabBulkUpload.findViewById(android.R.id.text1);

        isManualTab = manual;
        if (manual) {
            cardManualForm.setVisibility(View.VISIBLE);
            cardBulkUpload.setVisibility(View.GONE);
            tabAddManually.setCardBackgroundColor(Color.parseColor("#1A73E8"));
            tabBulkUpload.setCardBackgroundColor(Color.parseColor("#E8EEFF"));
        } else {
            cardManualForm.setVisibility(View.GONE);
            cardBulkUpload.setVisibility(View.VISIBLE);
            tabBulkUpload.setCardBackgroundColor(Color.parseColor("#1A73E8"));
            tabAddManually.setCardBackgroundColor(Color.parseColor("#E8EEFF"));
        }
    }

    // ── Create faculty via addemployee.jsp ────────────────────────
    private void createFaculty() {
        String name     = etFacultyName.getText().toString().trim();
        String email    = etFacultyEmail.getText().toString().trim();
        String phone    = etFacultyPhone.getText().toString().trim();
        String empId    = etFacultyEmpId.getText().toString().trim();
        String password = etFacultyPassword.getText().toString().trim();

        if (name.isEmpty())     { etFacultyName.setError("Required");     etFacultyName.requestFocus();     return; }
        if (email.isEmpty())    { etFacultyEmail.setError("Required");    etFacultyEmail.requestFocus();    return; }
        if (phone.isEmpty())    { etFacultyPhone.setError("Required");    etFacultyPhone.requestFocus();    return; }
        if (empId.isEmpty())    { etFacultyEmpId.setError("Required");    etFacultyEmpId.requestFocus();    return; }
        if (password.isEmpty()) { etFacultyPassword.setError("Required"); etFacultyPassword.requestFocus(); return; }

        btnCreateUser.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String params = "name="     + URLEncoder.encode(name,     "UTF-8")
                        + "&email="    + URLEncoder.encode(email,    "UTF-8")
                        + "&phoneno="  + URLEncoder.encode(phone,    "UTF-8")
                        + "&empcode="  + URLEncoder.encode(empId,    "UTF-8")
                        + "&password=" + URLEncoder.encode(password, "UTF-8");

                URL url = new URL(SERVER_BASE + "/jspapi/gps/addemployee.jsp");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.getOutputStream().write(params.getBytes("UTF-8"));

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String body = sb.toString().trim();
                Log.d(TAG, "addemployee response: " + body);

                boolean success = false;
                try {
                    JSONObject json = new JSONObject(body);
                    success = json.optBoolean("success", false)
                            || json.optString("status", "").equalsIgnoreCase("success");
                } catch (Exception ignored) {
                    success = body.toLowerCase().contains("success")
                            || body.toLowerCase().contains("added")
                            || body.toLowerCase().contains("inserted");
                }

                final boolean ok = success;
                runOnUiThread(() -> {
                    btnCreateUser.setEnabled(true);
                    if (ok) {
                        Toast.makeText(this, "Faculty member created successfully!", Toast.LENGTH_LONG).show();
                        clearForm();
                    } else {
                        Toast.makeText(this, "Failed: " + body, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Create faculty error: " + e.getMessage());
                runOnUiThread(() -> {
                    btnCreateUser.setEnabled(true);
                    Toast.makeText(this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void clearForm() {
        etFacultyName.setText("");
        etFacultyEmail.setText("");
        etFacultyPhone.setText("");
        etFacultyEmpId.setText("");
        etFacultyPassword.setText("");
    }

    private void pickCsvFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select CSV File"), FILE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedCsvUri = data.getData();
            String path = selectedCsvUri.getLastPathSegment();
            tvSelectedFile.setText("Selected: " + (path != null ? path : "file"));
        }
    }

    private void uploadCsv() {
        if (selectedCsvUri == null) {
            Toast.makeText(this, "Please select a CSV file first.", Toast.LENGTH_SHORT).show();
            return;
        }
        // TODO: implement CSV upload to server when backend is ready
        Toast.makeText(this, "CSV upload will be available after backend update.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}