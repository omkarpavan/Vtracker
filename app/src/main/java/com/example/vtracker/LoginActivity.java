package com.example.vtracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import androidx.cardview.widget.CardView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

// ── Extends BaseActivity so Developer Options check runs here too ──
public class LoginActivity extends BaseActivity {

    // ── UI References ──────────────────────────────────────────────
    private EditText    etEmployeeId, etPassword;
    private ImageView   ivTogglePassword;
    private CheckBox    cbRememberMe;
    private TextView    tvForgotPassword;
    private CardView    btnSignIn;
    private ProgressBar progressBar;
    private Spinner     spinnerRole;        // ← NEW: Faculty / Admin selector

    // ── State ──────────────────────────────────────────────────────
    private boolean isPasswordVisible = false;

    // ── Volley ─────────────────────────────────────────────────────
    private RequestQueue requestQueue;

    // ── Session Keys ───────────────────────────────────────────────
    public static final String PREF_NAME        = "VTrackerSession";
    public static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    public static final String KEY_EMPLOYEE_ID  = "employeeId";
    public static final String KEY_USER_NAME    = "userName";
    public static final String KEY_USER_EMAIL   = "userEmail";
    public static final String KEY_USER_PHONE   = "userPhone";
    public static final String KEY_IS_ADMIN     = "isAdmin";

    // ── API URLs ───────────────────────────────────────────────────
    // Faculty login API (existing)
    private static final String API_FACULTY =
            "http://160.187.169.14/jspapi/gps/getemployees.jsp?empcode=";

    // Admin login API (new)
    private static final String API_ADMIN =
            "http://192.168.10.25/jspapi/gps/getadmin.jsp?empcode=";

    // ──────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1A73E8"));
        setContentView(R.layout.activity_login);

        // ── Auto-login if session exists ───────────────────────────
        if (isSessionActive()) {
            restoreSession();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);
        initViews();
        setListeners();
    }

    // ── Check Active Session ───────────────────────────────────────
    private boolean isSessionActive() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // ── Restore Session → Skip Login ───────────────────────────────
    private void restoreSession() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        boolean isAdmin   = prefs.getBoolean(KEY_IS_ADMIN, false);
        String employeeId = prefs.getString(KEY_EMPLOYEE_ID, "");
        String userName   = prefs.getString(KEY_USER_NAME, "");
        String userEmail  = prefs.getString(KEY_USER_EMAIL, "");
        String userPhone  = prefs.getString(KEY_USER_PHONE, "");

        Class<?> destination = isAdmin ? AdminActivity.class : FacultyActivity.class;

        Intent intent = new Intent(this, destination);
        intent.putExtra("EMPLOYEE_ID", employeeId);
        intent.putExtra("USER_NAME",   userName);
        intent.putExtra("USER_EMAIL",  userEmail);
        intent.putExtra("USER_PHONE",  userPhone);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Save Session ───────────────────────────────────────────────
    private void saveSession(String employeeId, String name,
                             String email, String phone, boolean isAdmin) {
        SharedPreferences.Editor editor =
                getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMPLOYEE_ID,   employeeId);
        editor.putString(KEY_USER_NAME,     name);
        editor.putString(KEY_USER_EMAIL,    email);
        editor.putString(KEY_USER_PHONE,    phone);
        editor.putBoolean(KEY_IS_ADMIN,     isAdmin);
        editor.apply();
    }

    // ── Clear Session (called on Logout / dev-options block) ───────
    public static void clearSession(android.content.Context context) {
        context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    // ── Init Views ─────────────────────────────────────────────────
    private void initViews() {
        etEmployeeId     = findViewById(R.id.etEmployeeId);
        etPassword       = findViewById(R.id.etPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        cbRememberMe     = findViewById(R.id.cbRememberMe);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnSignIn        = findViewById(R.id.btnSignIn);
        progressBar      = findViewById(R.id.progressBar);
        spinnerRole      = findViewById(R.id.spinnerRole); // ← NEW

        // ── Spinner adapter ────────────────────────────────────────
        // The spinner is also populated via @array/role_options in XML,
        // but we set it here programmatically as a safe fallback.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Faculty", "Admin"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);
    }

    // ── Listeners ──────────────────────────────────────────────────
    private void setListeners() {
        btnSignIn.setOnClickListener(v -> handleLogin());

        ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        tvForgotPassword.setOnClickListener(v ->
                Toast.makeText(this,
                        "Please contact your administrator to reset your password.",
                        Toast.LENGTH_LONG).show()
        );
    }

    // ══════════════════════════════════════════════════════════════
    //  Login Logic
    //
    //  Reads the "Login As" spinner:
    //    "Faculty" → calls API_FACULTY (getemployees.jsp) → FacultyActivity
    //    "Admin"   → calls API_ADMIN   (getadmin.jsp)     → AdminActivity
    // ══════════════════════════════════════════════════════════════
    private void handleLogin() {
        String employeeId  = etEmployeeId.getText().toString().trim();
        String password    = etPassword.getText().toString().trim();
        String selectedRole = spinnerRole.getSelectedItem().toString(); // "Faculty" or "Admin"
        boolean isAdminRole = selectedRole.equals("Admin");

        // ── Validation ─────────────────────────────────────────────
        if (TextUtils.isEmpty(employeeId)) {
            etEmployeeId.setError("Employee ID is required");
            etEmployeeId.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        // ── Pick the correct API based on role ─────────────────────
        String url = (isAdminRole ? API_ADMIN : API_FACULTY) + employeeId;

        StringRequest request = new StringRequest(
                Request.Method.GET,
                url,

                // ── Success ────────────────────────────────────────
                response -> {
                    setLoading(false);
                    try {
                        // Fix for malformed JSON that some JSP responses return
                        String fixedResponse = response
                                .replace("\"password\"", ",\"password\"")
                                .replace(",,\"password\"", ",\"password\"");

                        JSONObject json = new JSONObject(fixedResponse);

                        String apiEmpCode  = json.optString("empcode",  "");
                        String apiPassword = json.optString("password", "");
                        String apiName     = json.optString("name",     employeeId);
                        String apiEmail    = json.optString("email",    "");
                        String apiPhone    = json.optString("phoneno",  "");

                        if (apiEmpCode.equalsIgnoreCase(employeeId)
                                && apiPassword.equals(password)) {

                            // Save session with the correct isAdmin flag
                            saveSession(employeeId, apiName, apiEmail, apiPhone, isAdminRole);

                            // Navigate to the matching dashboard
                            Class<?> destination = isAdminRole
                                    ? AdminActivity.class
                                    : FacultyActivity.class;

                            String displayName = apiName.isEmpty() ? employeeId : apiName;
                            navigateTo(destination,
                                    "Welcome, " + displayName + "!",
                                    json,
                                    isAdminRole);

                        } else {
                            Toast.makeText(this,
                                    "Invalid " + selectedRole + " ID or Password.",
                                    Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        Toast.makeText(this,
                                "Response error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                },

                // ── Error ──────────────────────────────────────────
                error -> {
                    setLoading(false);
                    String errorMsg = "Network error. Please try again.";
                    if (error.networkResponse != null) {
                        errorMsg = "Server error: " + error.networkResponse.statusCode;
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                }
        );

        requestQueue.add(request);
    }

    // ── Navigation ─────────────────────────────────────────────────
    private void navigateTo(Class<?> destination, String welcomeMessage,
                            JSONObject userData, boolean isAdmin) {
        Toast.makeText(this, welcomeMessage, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(LoginActivity.this, destination);
        intent.putExtra("EMPLOYEE_ID", etEmployeeId.getText().toString().trim());

        if (userData != null) {
            intent.putExtra("USER_NAME",  userData.optString("name",    ""));
            intent.putExtra("USER_EMAIL", userData.optString("email",   ""));
            intent.putExtra("USER_PHONE", userData.optString("phoneno", ""));
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Loading State ──────────────────────────────────────────────
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSignIn.setEnabled(!loading);
        btnSignIn.setAlpha(loading ? 0.6f : 1.0f);
    }

    // ── Toggle Password Visibility ─────────────────────────────────
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            etPassword.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_visibility);
        } else {
            etPassword.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT |
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivTogglePassword.setImageResource(R.drawable.ic_visibility_off);
        }

        etPassword.setSelection(etPassword.getText().length());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (requestQueue != null) requestQueue.cancelAll(this);
    }
}