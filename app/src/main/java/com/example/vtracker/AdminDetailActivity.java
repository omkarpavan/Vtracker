package com.example.vtracker;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class AdminDetailActivity extends BaseActivity {

    private ImageView ivBack;
    private TextView tvHeaderTitle, tvStatus, tvTripName, tvLocation, tvDateTime, tvAmount, tvDescription;
    private LinearLayout layoutAmount, containerPhotos;
    private TextView tvPhotosLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_admin_detail);

        initViews();
        displayDetails();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvStatus = findViewById(R.id.tvStatus);
        tvTripName = findViewById(R.id.tvTripName);
        tvLocation = findViewById(R.id.tvLocation);
        tvDateTime = findViewById(R.id.tvDateTime);
        tvAmount = findViewById(R.id.tvAmount);
        tvDescription = findViewById(R.id.tvDescription);
        layoutAmount = findViewById(R.id.layoutAmount);
        containerPhotos = findViewById(R.id.containerPhotos);
        tvPhotosLabel = findViewById(R.id.tvPhotosLabel);

        ivBack.setOnClickListener(v -> finish());
    }

    private void displayDetails() {
        String type = getIntent().getStringExtra("type"); // "history" or "expense"
        String tripName = getIntent().getStringExtra("tripName");
        String location = getIntent().getStringExtra("location");
        String dateTime = getIntent().getStringExtra("dateTime");
        String status = getIntent().getStringExtra("status");
        String description = getIntent().getStringExtra("description");
        String amount = getIntent().getStringExtra("amount");
        ArrayList<String> images = getIntent().getStringArrayListExtra("images");

        tvHeaderTitle.setText("expense".equals(type) ? "Expense Details" : "Visit Details");
        
        tvStatus.setText((status != null && !status.isEmpty()) ? status.toUpperCase() : "PENDING");
        tvStatus.getBackground().setTint(Color.parseColor(getStatusColor(status)));

        tvTripName.setText((tripName != null && !tripName.isEmpty() && !tripName.equals("null")) ? tripName : "(No Trip Name)");
        tvLocation.setText((location != null && !location.isEmpty() && !location.equals("null")) ? location : "Location not available");
        tvDateTime.setText((dateTime != null && !dateTime.isEmpty()) ? dateTime : "--");
        tvDescription.setText((description != null && !description.isEmpty() && !description.equals("null")) ? description : "No description provided.");

        if ("expense".equals(type)) {
            layoutAmount.setVisibility(View.VISIBLE);
            tvAmount.setText("₹ " + (amount != null ? amount : "0.00"));
        } else {
            layoutAmount.setVisibility(View.GONE);
        }

        if (images != null && !images.isEmpty()) {
            tvPhotosLabel.setVisibility(View.VISIBLE);
            containerPhotos.removeAllViews();
            for (String url : images) {
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 24);
                iv.setLayoutParams(lp);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

                String fullUrl = url;
                if (!fullUrl.startsWith("http")) {
                    fullUrl = "http://160.187.169.24" + (fullUrl.startsWith("/") ? "" : "/") + fullUrl;
                }

                Glide.with(this).load(fullUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(iv);

                containerPhotos.addView(iv);
            }
        } else {
            tvPhotosLabel.setVisibility(View.GONE);
        }
    }

    private String getStatusColor(String status) {
        if (status == null) return "#FB8C00";
        String s = status.toLowerCase().trim();
        if (s.contains("approved") || s.contains("completed")) return "#34A853";
        if (s.contains("rejected")) return "#E53935";
        if (s.contains("review")) return "#1A73E8";
        return "#FB8C00";
    }
}
