package com.example.abhijeet.bloodbank;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;

public class DonationCertificateActivity extends AppCompatActivity {

    private TextView tvCertId, tvDonorName, tvBloodGroup, tvDate, tvHospital, tvVerifiedBy, tvCertHash, tvTamperBadge;
    private MaterialCardView cardDigitalCertificate;
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

        if (getIntent().getData() != null) {
            Uri data = getIntent().getData();
            String queryId = data.getQueryParameter("id");
            if (queryId != null && !queryId.isEmpty()) {
                certificateId = queryId;
            } else if (data.getLastPathSegment() != null && !data.getLastPathSegment().isEmpty() && !"certificate".equalsIgnoreCase(data.getLastPathSegment())) {
                certificateId = data.getLastPathSegment();
            }
        }
        if (certificateId == null || certificateId.isEmpty()) {
            certificateId = getIntent().getStringExtra("certificate_id");
        }
        if (certificateId == null || certificateId.isEmpty()) {
            certificateId = getIntent().getStringExtra("certificateId");
        }

        // Header setup
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        TextView tvTitle = findViewById(R.id.tv_header_title);
        if (tvTitle != null) {
            tvTitle.setText("Donation Certificate");
        }

        cardDigitalCertificate = findViewById(R.id.card_digital_certificate);
        tvCertId = findViewById(R.id.tv_cert_id);
        tvDonorName = findViewById(R.id.tv_cert_donor_name);
        tvBloodGroup = findViewById(R.id.tv_cert_blood_group);
        tvDate = findViewById(R.id.tv_cert_date);
        tvHospital = findViewById(R.id.tv_cert_hospital);
        tvVerifiedBy = findViewById(R.id.tv_cert_verified_by);
        tvCertHash = findViewById(R.id.tv_cert_hash);
        tvTamperBadge = findViewById(R.id.tv_cert_tamper_badge);
        btnShareCertificate = findViewById(R.id.btn_share_certificate);

        if (btnShareCertificate != null) {
            btnShareCertificate.setOnClickListener(v -> shareCertificateAsImage());
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
                tvTamperBadge.setText("✓ Authoritative Server Integrity Verified");
                tvTamperBadge.setTextColor(Color.parseColor("#2E7D32"));
            } else {
                tvTamperBadge.setText("⚠ Cryptographic Hash Mismatch Detected");
                tvTamperBadge.setTextColor(Color.parseColor("#C62828"));
            }
        }
    }

    private void shareCertificateAsImage() {
        if (cardDigitalCertificate == null) {
            Toast.makeText(this, "Certificate is loading, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 1. Create a high-resolution Bitmap from the Certificate Card View
            int width = cardDigitalCertificate.getWidth();
            int height = cardDigitalCertificate.getHeight();

            if (width <= 0 || height <= 0) {
                cardDigitalCertificate.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                width = cardDigitalCertificate.getMeasuredWidth();
                height = cardDigitalCertificate.getMeasuredHeight();
                cardDigitalCertificate.layout(0, 0, width, height);
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE); // Ensure clean solid white backdrop
            cardDigitalCertificate.draw(canvas);

            // 2. Save to Cache Directory for FileProvider sharing
            File cacheDir = new File(getCacheDir(), "certificates");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            String fileName = "LifeShare_Certificate_" + (certificateId != null ? certificateId : "Verified") + ".png";
            File imageFile = new File(cacheDir, fileName);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();

            // 3. Obtain secure Content Uri via FileProvider
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );

            // 4. Launch Image Share Intent
            String donorName = loadedCertificate != null && loadedCertificate.donorName != null ? loadedCertificate.donorName : "LifeShare Donor";
            String shareCaption = "Proud to share my Official Blood Donation Certificate (" + (loadedCertificate != null ? loadedCertificate.bloodGroup : "Blood Donor") + ") with LifeShare! 🩸 Saving lives together.";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareCaption);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Donation Certificate"));

        } catch (Exception e) {
            Toast.makeText(this, "Failed to export certificate image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
