package com.example.vtracker;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class HistoryDetailActivity extends BaseActivity {

    private ImageView btnBack;
    private TextView tvTitle, tvDetailStatus, tvDetailUserName, tvDetailTripName, tvDetailTitle, tvDetailDateTime, tvDetailAmount, tvDetailDescription;
    private TextView tvTripLabel, tvSubjectLabel, tvNameLabel, tvAmountLabel, tvDateLabel, tvDescLabel;
    private LinearLayout containerImages;
    private TextView tvImagesHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_history_detail);

        initViews();
        displayDetails();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tvDetailStatus = findViewById(R.id.tvDetailStatus);
        tvDetailUserName = findViewById(R.id.tvDetailUserName);
        tvDetailTripName = findViewById(R.id.tvDetailTripName);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvDetailDateTime = findViewById(R.id.tvDetailDateTime);
        tvDetailAmount = findViewById(R.id.tvDetailAmount);
        tvDetailDescription = findViewById(R.id.tvDetailDescription);
        
        tvTripLabel = findViewById(R.id.tvTripLabel);
        tvSubjectLabel = findViewById(R.id.tvSubjectLabel);
        tvNameLabel = findViewById(R.id.tvNameLabel);
        tvAmountLabel = findViewById(R.id.tvAmountLabel);
        tvDateLabel = findViewById(R.id.tvDateLabel);
        tvDescLabel = findViewById(R.id.tvDescLabel);
        
        containerImages = findViewById(R.id.containerImages);
        tvImagesHeader = findViewById(R.id.tvImagesHeader);

        btnBack.setOnClickListener(v -> finish());
    }

    private void displayDetails() {
        String type = getIntent().getStringExtra("type"); // "history" or "expense"
        String userName = getIntent().getStringExtra("userName");
        String tripName = getIntent().getStringExtra("tripName");
        String location = getIntent().getStringExtra("title"); // Using 'title' for location as per existing code
        String dateTime = getIntent().getStringExtra("dateTime");
        String status = getIntent().getStringExtra("status");
        String description = getIntent().getStringExtra("description");
        ArrayList<String> images = getIntent().getStringArrayListExtra("images");
        String amount = getIntent().getStringExtra("amount");

        if (tvDetailUserName != null) {
            tvDetailUserName.setText(userName != null && !userName.isEmpty() && !userName.equals("null") ? userName : "--");
        }
        
        // Ensure Trip Name is always shown as requested
        if (tvDetailTripName != null) {
            tvDetailTripName.setVisibility(View.VISIBLE);
            if (tvTripLabel != null) tvTripLabel.setVisibility(View.VISIBLE);
            tvDetailTripName.setText(tripName != null && !tripName.isEmpty() && !tripName.equals("null") ? tripName : "--");
        }
        
        // Ensure Location is always shown as requested
        if (tvDetailTitle != null) {
            tvDetailTitle.setVisibility(View.VISIBLE);
            if (tvSubjectLabel != null) tvSubjectLabel.setVisibility(View.VISIBLE);
            tvDetailTitle.setText(location != null && !location.isEmpty() && !location.equals("null") ? location : "--");
        }
        
        if (tvDetailDateTime != null) {
            tvDetailDateTime.setText(dateTime != null && !dateTime.isEmpty() && !dateTime.equals("null") ? dateTime : "--");
        }
        
        if (tvDetailDescription != null) {
            tvDetailDescription.setText(description != null && !description.isEmpty() && !description.equals("null") ? description : "(No description provided)");
        }

        if (status != null && !status.isEmpty() && !status.equals("null") && tvDetailStatus != null) {
            tvDetailStatus.setVisibility(View.VISIBLE);
            tvDetailStatus.setText(status.toUpperCase());
            tvDetailStatus.getBackground().setTint(Color.parseColor(getStatusColor(status)));
        } else if (tvDetailStatus != null) {
            tvDetailStatus.setVisibility(View.GONE);
        }

        if ("expense".equals(type)) {
            tvTitle.setText("Expense Details");
            if (tvAmountLabel != null) tvAmountLabel.setVisibility(View.VISIBLE);
            if (tvDetailAmount != null) {
                tvDetailAmount.setVisibility(View.VISIBLE);
                tvDetailAmount.setText("₹" + (amount != null ? amount : "0"));
            }
            // Always show "Location" label as requested
            if (tvSubjectLabel != null) tvSubjectLabel.setText("Location");
        } else {
            tvTitle.setText("Visit Details");
            if (tvAmountLabel != null) tvAmountLabel.setVisibility(View.GONE);
            if (tvDetailAmount != null) tvDetailAmount.setVisibility(View.GONE);
            if (tvSubjectLabel != null) tvSubjectLabel.setText("Location");
        }

        if (images != null && !images.isEmpty()) {
            tvImagesHeader.setVisibility(View.VISIBLE);
            for (String url : images) {
                ImageView imageView = new ImageView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 32);
                imageView.setLayoutParams(lp);
                imageView.setAdjustViewBounds(true);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                
                String fullUrl = url;
                // Basic check for relative paths
                if (!fullUrl.startsWith("http")) {
                    fullUrl = "http://160.187.169.24" + (fullUrl.startsWith("/") ? "" : "/") + fullUrl;
                }
                
                Glide.with(this)
                        .load(fullUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(imageView);
                
                containerImages.addView(imageView);
            }
        } else {
            tvImagesHeader.setVisibility(View.GONE);
        }
    }

    private String getStatusColor(String status) {
        String lower = status.toLowerCase().trim();
        if (lower.contains("approved") || lower.contains("completed")) return "#34A853";
        if (lower.contains("pending")) return "#FB8C00";
        if (lower.contains("review")) return "#1A73E8";
        if (lower.contains("rejected")) return "#E53935";
        return "#8A93B2";
    }
}
