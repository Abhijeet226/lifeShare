package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;

public class DonationCertificateActivity extends AppCompatActivity {

    // Tab format selection
    private FrameLayout tabFormatA4, tabFormatSocial;
    private TextView tvTabA4, tvTabSocial;
    private View containerA4Certificate, layoutA4Canvas;
    private MaterialCardView cardSocialCertificate;

    // A4 Landscape views
    private TextView tvA4CertId, tvA4DonorName, tvA4BloodGroup, tvA4Date, tvA4Hospital, tvA4VerifiedBy, tvA4CertHash, tvA4TamperBadge;
    private ImageView ivA4QrCode;

    // Social portrait views
    private TextView tvSocialCertId, tvSocialDonorName, tvSocialBloodGroup, tvSocialDate, tvSocialHospital, tvSocialTamperBadge;
    private ImageView ivSocialQrCode;

    // Actions
    private MaterialButton btnPrintPdf, btnShareCertificate;

    private ApiClient apiClient;
    private String certificateId;
    private ApiClient.DonationCertificate loadedCertificate;
    private boolean isA4FormatActive = true;

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

        initViews();
        setupFormatSwitcher();

        if (certificateId != null && !certificateId.isEmpty()) {
            if (tvA4CertId != null) tvA4CertId.setText(certificateId);
            if (tvSocialCertId != null) tvSocialCertId.setText(certificateId);
            loadCertificateDetails();
        } else {
            Toast.makeText(this, "Missing certificate identifier", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        tabFormatA4 = findViewById(R.id.tab_format_a4);
        tabFormatSocial = findViewById(R.id.tab_format_social);
        tvTabA4 = findViewById(R.id.tv_tab_a4);
        tvTabSocial = findViewById(R.id.tv_tab_social);

        containerA4Certificate = findViewById(R.id.container_a4_certificate);
        layoutA4Canvas = findViewById(R.id.layout_a4_canvas);
        cardSocialCertificate = findViewById(R.id.card_social_certificate);

        // A4 views
        tvA4CertId = findViewById(R.id.tv_a4_cert_id);
        tvA4DonorName = findViewById(R.id.tv_a4_donor_name);
        tvA4BloodGroup = findViewById(R.id.tv_a4_blood_group);
        tvA4Date = findViewById(R.id.tv_a4_date);
        tvA4Hospital = findViewById(R.id.tv_a4_hospital);
        tvA4VerifiedBy = findViewById(R.id.tv_a4_verified_by);
        tvA4CertHash = findViewById(R.id.tv_a4_cert_hash);
        tvA4TamperBadge = findViewById(R.id.tv_a4_tamper_badge);
        ivA4QrCode = findViewById(R.id.iv_a4_qr_code);

        // Social views
        tvSocialCertId = findViewById(R.id.tv_social_cert_id);
        tvSocialDonorName = findViewById(R.id.tv_social_donor_name);
        tvSocialBloodGroup = findViewById(R.id.tv_social_blood_group);
        tvSocialDate = findViewById(R.id.tv_social_date);
        tvSocialHospital = findViewById(R.id.tv_social_hospital);
        tvSocialTamperBadge = findViewById(R.id.tv_social_tamper_badge);
        ivSocialQrCode = findViewById(R.id.iv_social_qr_code);

        // Buttons
        btnPrintPdf = findViewById(R.id.btn_print_pdf);
        btnShareCertificate = findViewById(R.id.btn_share_certificate);

        if (btnPrintPdf != null) {
            btnPrintPdf.setOnClickListener(v -> printA4Certificate());
        }

        if (btnShareCertificate != null) {
            btnShareCertificate.setOnClickListener(v -> showShareFormatDialog());
        }
    }

    private void setupFormatSwitcher() {
        if (tabFormatA4 != null) {
            tabFormatA4.setOnClickListener(v -> switchToFormat(true));
        }
        if (tabFormatSocial != null) {
            tabFormatSocial.setOnClickListener(v -> switchToFormat(false));
        }
    }

    private void switchToFormat(boolean isA4) {
        isA4FormatActive = isA4;
        if (isA4) {
            if (tabFormatA4 != null) tabFormatA4.setBackgroundResource(R.drawable.bg_chip_pill_selected);
            if (tabFormatSocial != null) tabFormatSocial.setBackgroundColor(Color.TRANSPARENT);
            if (tvTabA4 != null) tvTabA4.setTextColor(getResources().getColor(R.color.colorPrimary));
            if (tvTabSocial != null) tvTabSocial.setTextColor(getResources().getColor(R.color.text_secondary));

            if (containerA4Certificate != null) containerA4Certificate.setVisibility(View.VISIBLE);
            if (cardSocialCertificate != null) cardSocialCertificate.setVisibility(View.GONE);
        } else {
            if (tabFormatA4 != null) tabFormatA4.setBackgroundColor(Color.TRANSPARENT);
            if (tabFormatSocial != null) tabFormatSocial.setBackgroundResource(R.drawable.bg_chip_pill_selected);
            if (tvTabA4 != null) tvTabA4.setTextColor(getResources().getColor(R.color.text_secondary));
            if (tvTabSocial != null) tvTabSocial.setTextColor(getResources().getColor(R.color.colorPrimary));

            if (containerA4Certificate != null) containerA4Certificate.setVisibility(View.GONE);
            if (cardSocialCertificate != null) cardSocialCertificate.setVisibility(View.VISIBLE);
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

        // Render A4 Certificate
        if (tvA4CertId != null) tvA4CertId.setText(cert.certificateId);
        if (tvA4DonorName != null) tvA4DonorName.setText(cert.donorName);
        if (tvA4BloodGroup != null) tvA4BloodGroup.setText(cert.bloodGroup);
        if (tvA4Hospital != null) tvA4Hospital.setText(cert.hospital);
        if (tvA4VerifiedBy != null) tvA4VerifiedBy.setText(cert.verifiedBy != null ? cert.verifiedBy : "Authorized Coordinator");
        if (tvA4CertHash != null) tvA4CertHash.setText("SHA-256: " + (cert.certificateHash != null ? cert.certificateHash : "Verified"));

        // Render Social Certificate
        if (tvSocialCertId != null) tvSocialCertId.setText(cert.certificateId);
        if (tvSocialDonorName != null) tvSocialDonorName.setText(cert.donorName);
        if (tvSocialBloodGroup != null) tvSocialBloodGroup.setText(cert.bloodGroup);
        if (tvSocialHospital != null) tvSocialHospital.setText(cert.hospital);

        String formattedDate = cert.donationDate;
        if (formattedDate != null && formattedDate.length() >= 10) {
            formattedDate = formattedDate.substring(0, 10);
        }
        if (tvA4Date != null) tvA4Date.setText(formattedDate != null ? formattedDate : "Verified");
        if (tvSocialDate != null) tvSocialDate.setText(formattedDate != null ? formattedDate : "Verified");

        if (tvA4TamperBadge != null) {
            if (cert.isTamperProofValid) {
                tvA4TamperBadge.setText("✓ Cryptographic Integrity Verified");
                tvA4TamperBadge.setTextColor(Color.parseColor("#2E7D32"));
            } else {
                tvA4TamperBadge.setText("⚠ Hash Mismatch Detected");
                tvA4TamperBadge.setTextColor(Color.parseColor("#C62828"));
            }
        }

        // Generate Scannable Verification QR Code
        String verifyUrl = "https://lifeshare-74c2.onrender.com/api/certificates/" + cert.certificateId;
        Bitmap qrBitmap = QrUtils.generateQrCode(verifyUrl, 200, 200);
        if (qrBitmap != null) {
            if (ivA4QrCode != null) ivA4QrCode.setImageBitmap(qrBitmap);
            if (ivSocialQrCode != null) ivSocialQrCode.setImageBitmap(qrBitmap);
        }
    }

    // =========================================================================
    // NATIVE ANDROID A4 PRINT & PDF ENGINE
    // =========================================================================

    private void printA4Certificate() {
        if (layoutA4Canvas == null || loadedCertificate == null) {
            Toast.makeText(this, "Certificate is loading, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) {
            Toast.makeText(this, "Printing service unavailable on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        String jobName = "LifeShare_Certificate_" + loadedCertificate.certificateId;

        PrintAttributes attributes = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();

        printManager.print(jobName, new A4CertificatePrintAdapter(this, layoutA4Canvas), attributes);
    }

    private class A4CertificatePrintAdapter extends PrintDocumentAdapter {
        private final Context context;
        private final View printView;
        private PrintedPdfDocument pdfDocument;

        public A4CertificatePrintAdapter(Context context, View printView) {
            this.context = context;
            this.printView = printView;
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                             CancellationSignal cancellationSignal,
                             LayoutResultCallback callback, Bundle extras) {
            pdfDocument = new PrintedPdfDocument(context, newAttributes);

            if (cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }

            PrintDocumentInfo info = new PrintDocumentInfo.Builder("LifeShare_Certificate.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build();

            callback.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal,
                            WriteResultCallback callback) {
            if (pdfDocument == null) return;

            android.graphics.pdf.PdfDocument.Page page = pdfDocument.startPage(0);
            if (cancellationSignal.isCanceled()) {
                callback.onWriteCancelled();
                pdfDocument.close();
                pdfDocument = null;
                return;
            }

            // Render view onto PDF canvas scaled to A4 Landscape dimensions
            Canvas canvas = page.getCanvas();
            int pageWidth = page.getInfo().getPageWidth();
            int pageHeight = page.getInfo().getPageHeight();

            // Measure & Layout view if needed
            printView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int viewWidth = printView.getMeasuredWidth();
            int viewHeight = printView.getMeasuredHeight();

            float scale = Math.min((float) pageWidth / viewWidth, (float) pageHeight / viewHeight) * 0.95f;
            float leftMargin = (pageWidth - (viewWidth * scale)) / 2f;
            float topMargin = (pageHeight - (viewHeight * scale)) / 2f;

            canvas.save();
            canvas.translate(leftMargin, topMargin);
            canvas.scale(scale, scale);
            printView.draw(canvas);
            canvas.restore();

            pdfDocument.finishPage(page);

            try {
                pdfDocument.writeTo(new FileOutputStream(destination.getFileDescriptor()));
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) {
                callback.onWriteFailed(e.getMessage());
            } finally {
                pdfDocument.close();
                pdfDocument = null;
            }
        }
    }

    // =========================================================================
    // IMAGE EXPORT & SHARING
    // =========================================================================

    private void showShareFormatDialog() {
        CharSequence[] options = {"📜 Official A4 Landscape Image (Full Certificate)", "📱 Social Story Card (Portrait Format)"};
        new AlertDialog.Builder(this)
                .setTitle("Select Certificate Format")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        exportAndShareView(layoutA4Canvas, "A4_Landscape");
                    } else {
                        exportAndShareView(cardSocialCertificate, "Social_Portrait");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportAndShareView(View targetView, String formatLabel) {
        if (targetView == null || loadedCertificate == null) {
            Toast.makeText(this, "Certificate is loading, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int width = targetView.getWidth();
            int height = targetView.getHeight();

            if (width <= 0 || height <= 0) {
                targetView.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                width = targetView.getMeasuredWidth();
                height = targetView.getMeasuredHeight();
                targetView.layout(0, 0, width, height);
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            targetView.draw(canvas);

            File cacheDir = new File(getCacheDir(), "certificates");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String fileName = "LifeShare_Certificate_" + loadedCertificate.certificateId + "_" + formatLabel + ".png";
            File imageFile = new File(cacheDir, fileName);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();

            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );

            String shareCaption = "Proud to receive my Official Blood Donation Certificate (" +
                    loadedCertificate.bloodGroup + ") from " + loadedCertificate.hospital +
                    "! 🩸 Certified by LifeShare Network.";

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
