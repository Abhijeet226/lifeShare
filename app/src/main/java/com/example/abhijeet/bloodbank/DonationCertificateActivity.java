package com.example.abhijeet.bloodbank;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

public class DonationCertificateActivity extends AppCompatActivity {

    private TextView tvCertId, tvDonorName, tvBloodGroup, tvDate, tvHospital, tvVerifiedBy, tvCertHash, tvTamperBadge;
    private MaterialButton btnShareCertificate;
    private ApiClient apiClient;
    private String certificateId;
    private ApiClient.DonationCertificate loadedCertificate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_donation_certificate);

        apiClient = ApiClient.getInstance();
        apiClient.initFromPrefs(this);

        certificateId = getIntent().getStringExtra("certificate_id");

        tvCertId = findViewById(R.id.tv_cert_id);
        tvDonorName = findViewById(R.id.tv_cert_donor_name);
        tvBloodGroup = findViewById(R.id.tv_cert_blood_group);
        tvDate = findViewById(R.id.tv_cert_date);
        tvHospital = findViewById(R.id.tv_cert_hospital);
        tvVerifiedBy = findViewById(R.id.tv_cert_verified_by);
        tvCertHash = findViewById(R.id.tv_cert_hash);
        tvTamperBadge = findViewById(R.id.tv_cert_tamper_badge);
        btnShareCertificate = findViewById(R.id.btn_share_certificate);

        View btnBack = findViewById(R.id.btn_floating_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (btnShareCertificate != null) {
            btnShareCertificate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shareCertificateText();
                }
            });
        }

        if (certificateId != null && !certificateId.isEmpty()) {
            if (tvCertId != null) tvCertId.setText(certificateId);
            loadCertificateDetails();
        } else {
            Toast.makeText(this, "Missing certificate identifier", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadCertificateDetails() {
        apiClient.getCertificate(certificateId, new ApiClient.ApiCallback<ApiClient.DonationCertificate>() {
            @Override
            public void onSuccess(ApiClient.DonationCertificate cert) {
                loadedCertificate = cert;
                renderCertificate(cert);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(DonationCertificateActivity.this, "Failed to load certificate: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderCertificate(ApiClient.DonationCertificate cert) {
        if (cert == null) return;

        if (tvCertId != null) tvCertId.setText(cert.certificateId);
        if (tvDonorName != null) tvDonorName.setText(cert.donorName);
        if (tvBloodGroup != null) tvBloodGroup.setText(cert.bloodGroup);
        if (tvHospital != null) tvHospital.setText(cert.hospital);
        if (tvVerifiedBy != null) tvVerifiedBy.setText(cert.verifiedBy);
        if (tvCertHash != null) tvCertHash.setText(cert.certificateHash);

        if (tvDate != null) {
            String d = cert.donationDate;
            if (d != null && d.length() >= 10) d = d.substring(0, 10);
            tvDate.setText(d != null ? d : "Verified");
        }

        if (tvTamperBadge != null) {
            if (cert.isTamperProofValid) {
                tvTamperBadge.setText("Cryptographic Server Integrity Verified");
                tvTamperBadge.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                tvTamperBadge.setText("Hash Mismatch Detected");
                tvTamperBadge.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        }
    }

    private void shareCertificateText() {
        if (loadedCertificate == null) return;

        String shareText = "LIFE SHARE BLOOD DONATION CERTIFICATE\n" +
                "Certificate ID: " + loadedCertificate.certificateId + "\n" +
                "Donor: " + loadedCertificate.donorName + "\n" +
                "Blood Group: " + loadedCertificate.bloodGroup + "\n" +
                "Hospital: " + loadedCertificate.hospital + "\n" +
                "Verified by: " + loadedCertificate.verifiedBy + "\n" +
                "Status: Certified Voluntary Blood Donation\n" +
                "LifeShare — Saving Lives Together";

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share Donation Certificate"));
    }
}
