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
    private static final String SERVER_BASE = "http://160.187.169.24";
    private static final int    FILE_PICK   = 3001;

    // Views
    private ImageView  btnBack;
    private CardView   tabAddManually, tabBulkUpload;
    private CardView   cardManualForm, cardBulkUpload;
    private EditText   etFacultyName, etFacultyEmail, etFacultyPhone, etFacultyEmpId, etFacultyPassword, etFacultyDept, etFacultyDesig;
    private CardView   btnCreateUser, btnSelectFile, btnUploadCSV;
    private TextView   tvSelectedFile;
    private LinearLayout navDashboard, navSearch, navApprovals, navProfile;

    // Data
    private String adminName = "";
    private String adminId   = "";
    private Uri    selectedCsvUri = null;

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
        etFacultyDept    = findViewById(R.id.etFacultyDept);
        etFacultyDesig   = findViewById(R.id.etFacultyDesig);
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
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Tab switching
        if (tabAddManually != null) tabAddManually.setOnClickListener(v -> switchTab(true));
        if (tabBulkUpload != null) tabBulkUpload.setOnClickListener(v  -> switchTab(false));

        if (btnCreateUser != null) btnCreateUser.setOnClickListener(v  -> createFaculty());
        if (btnSelectFile != null) btnSelectFile.setOnClickListener(v  -> pickCsvFile());
        if (btnUploadCSV != null) btnUploadCSV.setOnClickListener(v   -> uploadCsv());

        // Bottom nav
        if (navDashboard != null) navDashboard.setOnClickListener(v -> {
            Intent i = new Intent(this, AdminActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });
        if (navSearch != null) navSearch.setOnClickListener(v -> {
            Intent i = new Intent(this, AdminSearchActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
        if (navApprovals != null) navApprovals.setOnClickListener(v -> {
            Intent i = new Intent(this, ApprovalsActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
        if (navProfile != null) navProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra("USER_NAME", adminName);
            i.putExtra("EMPLOYEE_ID", adminId);
            startActivity(i);
        });
    }

    private TextView tvTabManual, tvTabBulk;

    private void switchTab(boolean manual) {
        if (tvTabManual == null) tvTabManual = findViewById(R.id.tvTabManual);
        if (tvTabBulk   == null) tvTabBulk   = findViewById(R.id.tvTabBulk);

        if (manual) {
            if (cardManualForm != null) cardManualForm.setVisibility(View.VISIBLE);
            if (cardBulkUpload != null) cardBulkUpload.setVisibility(View.GONE);
            if (tabAddManually != null) tabAddManually.setCardBackgroundColor(Color.parseColor("#1A73E8"));
            if (tabBulkUpload != null) tabBulkUpload.setCardBackgroundColor(Color.parseColor("#E8EEFF"));
            if (tvTabManual != null) tvTabManual.setTextColor(Color.WHITE);
            if (tvTabBulk != null) tvTabBulk.setTextColor(Color.parseColor("#8A93B2"));
        } else {
            if (cardManualForm != null) cardManualForm.setVisibility(View.GONE);
            if (cardBulkUpload != null) cardBulkUpload.setVisibility(View.VISIBLE);
            if (tabBulkUpload != null) tabBulkUpload.setCardBackgroundColor(Color.parseColor("#1A73E8"));
            if (tabAddManually != null) tabAddManually.setCardBackgroundColor(Color.parseColor("#E8EEFF"));
            if (tvTabBulk != null) tvTabBulk.setTextColor(Color.WHITE);
            if (tvTabManual != null) tvTabManual.setTextColor(Color.parseColor("#8A93B2"));
        }
    }

    private void createFaculty() {
        String name     = etFacultyName.getText().toString().trim();
        String email    = etFacultyEmail.getText().toString().trim();
        String phone    = etFacultyPhone.getText().toString().trim();
        String empId    = etFacultyEmpId.getText().toString().trim();
        String password = etFacultyPassword.getText().toString().trim();
        String dept     = etFacultyDept.getText().toString().trim();
        String desig    = etFacultyDesig.getText().toString().trim();


        if (name.isEmpty())     { etFacultyName.setError("Required");     etFacultyName.requestFocus();     return; }
        if (email.isEmpty())    { etFacultyEmail.setError("Required");    etFacultyEmail.requestFocus();    return; }
        if (phone.isEmpty())    { etFacultyPhone.setError("Required");    etFacultyPhone.requestFocus();    return; }
        if (empId.isEmpty())    { etFacultyEmpId.setError("Required");    etFacultyEmpId.requestFocus();    return; }
        if (password.isEmpty()) { etFacultyPassword.setError("Required"); etFacultyPassword.requestFocus(); return; }

        btnCreateUser.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String params = "name="           + URLEncoder.encode(name,     "UTF-8")
                        + "&email="        + URLEncoder.encode(email,    "UTF-8")
                        + "&phoneno="      + URLEncoder.encode(phone,    "UTF-8")
                        + "&empcode="      + URLEncoder.encode(empId,    "UTF-8")
                        + "&password="     + URLEncoder.encode(password, "UTF-8")
                        + "&department="   + URLEncoder.encode(dept,     "UTF-8")
                        + "&designation="  + URLEncoder.encode(desig,    "UTF-8")
                        + "&qualification=" + URLEncoder.encode("",       "UTF-8");

                URL url = new URL(SERVER_BASE + "/VTracker/addemployee.jsp");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                
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
                String serverMsg = "";
                try {
                    JSONObject json = new JSONObject(body);
                    success = json.optBoolean("success", false)
                            || json.optString("status", "").equalsIgnoreCase("success");
                    serverMsg = json.optString("message", "");
                } catch (Exception ignored) {
                    success = body.toLowerCase().contains("success")
                            || body.toLowerCase().contains("added")
                            || body.toLowerCase().contains("inserted");
                }

                final boolean ok = success;
                final String finalMsg = serverMsg;
                final String finalBody = body;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnCreateUser.setEnabled(true);
                    if (ok) {
                        Toast.makeText(this, "Faculty member created successfully!", Toast.LENGTH_LONG).show();
                        clearForm();
                    } else {
                        String displayMsg = !finalMsg.isEmpty() ? finalMsg : finalBody;
                        Toast.makeText(this, "Failed: " + displayMsg, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Create faculty error: " + e.getMessage());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnCreateUser.setEnabled(true);
                    Toast.makeText(this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void clearForm() {
        if (etFacultyName != null) etFacultyName.setText("");
        if (etFacultyEmail != null) etFacultyEmail.setText("");
        if (etFacultyPhone != null) etFacultyPhone.setText("");
        if (etFacultyEmpId != null) etFacultyEmpId.setText("");
        if (etFacultyPassword != null) etFacultyPassword.setText("");
        if (etFacultyDept != null) etFacultyDept.setText("");
        if (etFacultyDesig != null) etFacultyDesig.setText("");
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
            if (tvSelectedFile != null) tvSelectedFile.setText("Selected: " + (path != null ? path : "file"));
        }
    }

    private void uploadCsv() {
        if (selectedCsvUri == null) {
            Toast.makeText(this, "Please select a CSV file first.", Toast.LENGTH_SHORT).show();
            return;
        }
        btnUploadCSV.setEnabled(false);
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                java.io.InputStream is = getContentResolver().openInputStream(selectedCsvUri);
                if (is == null) throw new Exception("Could not open file");
                byte[] csvBytes = new byte[is.available()];
                is.read(csvBytes);
                is.close();

                String boundary = "----Boundary" + System.currentTimeMillis();
                java.net.URL url = new java.net.URL(SERVER_BASE + "/VTracker/bulkaddfaculty.jsp");
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                java.io.DataOutputStream dos = new java.io.DataOutputStream(conn.getOutputStream());
                String LINE = "\r\n";
                String HYPHENS = "--";
                dos.writeBytes(HYPHENS + boundary + LINE);
                dos.writeBytes("Content-Disposition: form-data; name=\"csvfile\"; filename=\"faculty.csv\"" + LINE);
                dos.writeBytes("Content-Type: text/csv" + LINE + LINE);
                dos.write(csvBytes);
                dos.writeBytes(LINE);
                dos.writeBytes(HYPHENS + boundary + HYPHENS + LINE);
                dos.flush(); dos.close();

                int code = conn.getResponseCode();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String body = sb.toString().trim();
                Log.d(TAG, "bulkupload response: " + body);

                int added = 0;
                String errMsg = "";
                try {
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    added  = json.optInt("added", 0);
                    org.json.JSONArray errs = json.optJSONArray("errors");
                    if (errs != null && errs.length() > 0) {
                        StringBuilder eb = new StringBuilder();
                        for (int i = 0; i < Math.min(errs.length(), 3); i++)
                            eb.append("\n• ").append(errs.getString(i));
                        errMsg = eb.toString();
                    }
                } catch (Exception ignored) {}

                final int finalAdded = added;
                final String finalErr = errMsg;
                final String finalBody = body;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnUploadCSV.setEnabled(true);
                    if (finalAdded > 0 || finalBody.toLowerCase().contains("success")) {
                        String msg = finalAdded + " faculty member(s) added successfully.";
                        if (!finalErr.isEmpty()) msg += "\nErrors:" + finalErr;
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        if (tvSelectedFile != null) tvSelectedFile.setText(""); 
                        selectedCsvUri = null;
                    } else {
                        Toast.makeText(this, "Upload failed: " + finalBody, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Bulk upload error: " + e.getMessage());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnUploadCSV.setEnabled(true);
                    Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}