package com.example.abhijeet.bloodbank;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class DonationHistoryActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalDonations;
    private LinearLayout layoutEmpty, containerItems;
    private ApiClient apiClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_donation_history);

        apiClient = ApiClient.getInstance();
        apiClient.initFromPrefs(this);

        swipeRefresh = findViewById(R.id.swipe_refresh_donations);
        tvTotalDonations = findViewById(R.id.tv_history_total_donations);
        layoutEmpty = findViewById(R.id.layout_history_empty);
        containerItems = findViewById(R.id.container_donation_items);
        View btnBack = findViewById(R.id.btn_floating_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadDonationHistory();
                }
            });
        }

        loadDonationHistory();
    }

    private void loadDonationHistory() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getDonationHistory(new ApiClient.ApiCallback<List<ApiClient.DonationHistoryItem>>() {
            @Override
            public void onSuccess(List<ApiClient.DonationHistoryItem> result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                renderDonationHistory(result);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(DonationHistoryActivity.this, "Failed to load donations: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderDonationHistory(List<ApiClient.DonationHistoryItem> items) {
        if (containerItems == null) return;
        containerItems.removeAllViews();

        if (items == null || items.isEmpty()) {
            if (tvTotalDonations != null) tvTotalDonations.setText("🩸 0 Donations Completed");
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        if (tvTotalDonations != null) {
            tvTotalDonations.setText("🩸 " + items.size() + " Verified Donation" + (items.size() > 1 ? "s" : "") + " Completed");
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final ApiClient.DonationHistoryItem item : items) {
            View view = inflater.inflate(R.layout.item_donation_history, containerItems, false);

            TextView tvDate = view.findViewById(R.id.tv_item_donation_date);
            TextView tvBadge = view.findViewById(R.id.tv_item_verified_badge);
            TextView tvBlood = view.findViewById(R.id.tv_item_blood_group);
            TextView tvHospital = view.findViewById(R.id.tv_item_hospital_name);
            TextView tvUnits = view.findViewById(R.id.tv_item_units_donated);
            MaterialButton btnCertificate = view.findViewById(R.id.btn_item_view_certificate);

            if (tvDate != null) {
                String dateStr = item.donationDate;
                if (dateStr != null && dateStr.length() >= 10) {
                    dateStr = dateStr.substring(0, 10);
                }
                tvDate.setText(dateStr != null ? dateStr : "Verified Donation");
            }
            if (tvBadge != null) tvBadge.setText(item.status != null ? item.status : "VERIFIED");
            if (tvBlood != null) tvBlood.setText(item.bloodGroup != null ? item.bloodGroup : "O+");
            if (tvHospital != null) tvHospital.setText(item.hospital != null ? item.hospital : "Authorized Medical Center");
            if (tvUnits != null) tvUnits.setText(item.unitsDonated + " Unit" + (item.unitsDonated > 1 ? "s" : "") + " Whole Blood Donated");

            if (btnCertificate != null) {
                btnCertificate.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (item.certificateId != null && !item.certificateId.isEmpty()) {
                            Intent intent = new Intent(DonationHistoryActivity.this, DonationCertificateActivity.class);
                            intent.putExtra("certificate_id", item.certificateId);
                            startActivity(intent);
                        } else {
                            Toast.makeText(DonationHistoryActivity.this, "Certificate ID not available", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            containerItems.addView(view);
        }
    }
}
