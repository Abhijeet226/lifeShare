package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.widget.ImageView;
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

    private TextView tvCertId, tvDonorName, tvBloodGroup, tvDate, tvHospital, tvCertDoctor, tvVerifiedBy, tvCertHash, tvTamperBadge;
    private ImageView ivCertQrCode;
    private MaterialCardView cardDigitalCertificate;
    private MaterialButton btnPrintPdf, btnShareCertificate;

    private ApiClient apiClient;
    private String certificateId;
    private ApiClient.DonationCertificate loadedCertificate;
    private Bitmap verificationQrBitmap;

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
        ImageView btnHeaderPrint = findViewById(R.id.btn_header_print);
        if (btnHeaderPrint != null) {
            btnHeaderPrint.setOnClickListener(v -> printA4Certificate());
        }
        ImageView btnHeaderShare = findViewById(R.id.btn_header_share);
        if (btnHeaderShare != null) {
            btnHeaderShare.setOnClickListener(v -> shareMobileCertificateImage());
        }

        initViews();

        if (certificateId != null && !certificateId.isEmpty()) {
            if (tvCertId != null) tvCertId.setText(certificateId);
            loadCertificateDetails();
        } else {
            Toast.makeText(this, "Missing certificate identifier", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        cardDigitalCertificate = findViewById(R.id.card_digital_certificate);
        tvCertId = findViewById(R.id.tv_cert_id);
        tvDonorName = findViewById(R.id.tv_cert_donor_name);
        tvBloodGroup = findViewById(R.id.tv_cert_blood_group);
        tvDate = findViewById(R.id.tv_cert_date);
        tvHospital = findViewById(R.id.tv_cert_hospital);
        tvCertDoctor = findViewById(R.id.tv_cert_doctor);
        tvVerifiedBy = findViewById(R.id.tv_cert_verified_by);
        tvCertHash = findViewById(R.id.tv_cert_hash);
        tvTamperBadge = findViewById(R.id.tv_cert_tamper_badge);
        ivCertQrCode = findViewById(R.id.iv_cert_qr_code);
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
        if (tvVerifiedBy != null) tvVerifiedBy.setText(cert.verifiedBy != null ? cert.verifiedBy : "Hospital Coordinator");
        if (tvCertHash != null) tvCertHash.setText("SHA-256: " + (cert.certificateHash != null ? cert.certificateHash : "Verified"));

        if (tvCertDoctor != null) {
            String docText = (cert.attendingDoctor != null && !cert.attendingDoctor.isEmpty()) ? cert.attendingDoctor : "Attending Medical Officer";
            if (cert.doctorRegistrationNo != null && !cert.doctorRegistrationNo.isEmpty()) {
                docText += " (Reg: " + cert.doctorRegistrationNo + ")";
            }
            tvCertDoctor.setText(docText);
        }

        String formattedDate = cert.donationDate;
        if (formattedDate != null && formattedDate.length() >= 10) {
            formattedDate = formattedDate.substring(0, 10);
        }
        if (tvDate != null) tvDate.setText(formattedDate != null ? formattedDate : "Verified");

        if (tvTamperBadge != null) {
            if (cert.isTamperProofValid) {
                tvTamperBadge.setText("✓ Authoritative Server Integrity Verified");
                tvTamperBadge.setTextColor(Color.parseColor("#2E7D32"));
            } else {
                tvTamperBadge.setText("⚠ Hash Mismatch Detected");
                tvTamperBadge.setTextColor(Color.parseColor("#C62828"));
            }
        }

        // Generate Scannable Verification QR Code
        String verifyUrl = "https://lifeshare-74c2.onrender.com/api/certificates/" + cert.certificateId;
        verificationQrBitmap = QrUtils.generateQrCode(verifyUrl, 240, 240);
        if (verificationQrBitmap != null && ivCertQrCode != null) {
            ivCertQrCode.setImageBitmap(verificationQrBitmap);
        }
    }

    // =========================================================================
    // AUTOMATIC A4 LANDSCAPE PRINT & VECTOR PDF ENGINE
    // =========================================================================

    private void printA4Certificate() {
        if (loadedCertificate == null) {
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

        printManager.print(jobName, new A4LandscapeVectorPrintAdapter(this, loadedCertificate, verificationQrBitmap), attributes);
    }

    private static class A4LandscapeVectorPrintAdapter extends PrintDocumentAdapter {
        private final Context context;
        private final ApiClient.DonationCertificate cert;
        private final Bitmap qrBitmap;
        private PrintedPdfDocument pdfDocument;

        public A4LandscapeVectorPrintAdapter(Context context, ApiClient.DonationCertificate cert, Bitmap qrBitmap) {
            this.context = context;
            this.cert = cert;
            this.qrBitmap = qrBitmap;
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

            Canvas canvas = page.getCanvas();
            int w = page.getInfo().getPageWidth();  // 842 points (A4 landscape)
            int h = page.getInfo().getPageHeight(); // 595 points (A4 landscape)

            // 1. Background Ivory Canvas
            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.parseColor("#FFFEFB"));
            canvas.drawRect(0, 0, w, h, bgPaint);

            // 2. Double Ornate Border: Gold Outer, Crimson Inner
            Paint goldBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            goldBorderPaint.setStyle(Paint.Style.STROKE);
            goldBorderPaint.setColor(Color.parseColor("#D4AF37"));
            goldBorderPaint.setStrokeWidth(3.5f);
            canvas.drawRoundRect(new RectF(24, 24, w - 24, h - 24), 8, 8, goldBorderPaint);

            Paint crimsonBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            crimsonBorderPaint.setStyle(Paint.Style.STROKE);
            crimsonBorderPaint.setColor(Color.parseColor("#C62828"));
            crimsonBorderPaint.setStrokeWidth(1.5f);
            canvas.drawRoundRect(new RectF(32, 32, w - 32, h - 32), 4, 4, crimsonBorderPaint);

            // 3. Top Header
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextAlign(Paint.Align.CENTER);

            // Org Header
            textPaint.setColor(Color.parseColor("#C62828"));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(13);
            textPaint.setLetterSpacing(0.12f);
            canvas.drawText("LIFESHARE VOLUNTARY DONOR NETWORK", w / 2f, 68, textPaint);

            // Sub-header
            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(9.5f);
            textPaint.setLetterSpacing(0.05f);
            canvas.drawText("AUTHORITATIVE NATIONAL VOLUNTARY BLOOD DONATION REGISTRY", w / 2f, 84, textPaint);

            // 4. Main Certificate Title
            textPaint.setColor(Color.parseColor("#0F172A"));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(22);
            textPaint.setLetterSpacing(0.08f);
            canvas.drawText("CERTIFICATE OF BLOOD DONATION", w / 2f, 126, textPaint);

            // Subtitle
            textPaint.setColor(Color.parseColor("#64748B"));
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(11);
            textPaint.setLetterSpacing(0.02f);
            canvas.drawText("This is to proudly certify and commend", w / 2f, 148, textPaint);

            // 5. Donor Full Name
            textPaint.setColor(Color.parseColor("#C62828"));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextSize(26);
            canvas.drawText(cert.donorName != null ? cert.donorName : "Honored Donor", w / 2f, 188, textPaint);

            // 6. Commendation Statement
            TextPaint bodyTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            bodyTextPaint.setColor(Color.parseColor("#334155"));
            bodyTextPaint.setTextSize(11);
            bodyTextPaint.setTypeface(Typeface.DEFAULT);

            String bodyText = "in sincere recognition of voluntary and selfless blood donation to save precious human lives during critical medical emergencies. Your humanitarian act reflects the highest spirit of civic duty and community service.";
            StaticLayout bodyLayout = new StaticLayout(bodyText, bodyTextPaint, w - 160, Layout.Alignment.ALIGN_CENTER, 1.2f, 0, false);
            canvas.save();
            canvas.translate(80, 204);
            bodyLayout.draw(canvas);
            canvas.restore();

            // 7. Metadata Box Strip
            float metaBoxTop = 262;
            float metaBoxBottom = 320;
            Paint metaBgPaint = new Paint();
            metaBgPaint.setColor(Color.parseColor("#F8FAFC"));
            canvas.drawRoundRect(new RectF(60, metaBoxTop, w - 60, metaBoxBottom), 8, 8, metaBgPaint);

            Paint metaBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            metaBorderPaint.setStyle(Paint.Style.STROKE);
            metaBorderPaint.setColor(Color.parseColor("#E2E8F0"));
            metaBorderPaint.setStrokeWidth(1);
            canvas.drawRoundRect(new RectF(60, metaBoxTop, w - 60, metaBoxBottom), 8, 8, metaBorderPaint);

            // 4 Metadata Columns
            float col1 = 140; // Blood Group
            float col2 = 290; // Date
            float col3 = 490; // Hospital
            float col4 = 700; // Certificate ID

            Paint metaLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            metaLabelPaint.setColor(Color.parseColor("#94A3B8"));
            metaLabelPaint.setTextSize(8.5f);
            metaLabelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            metaLabelPaint.setTextAlign(Paint.Align.CENTER);

            Paint metaValPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            metaValPaint.setColor(Color.parseColor("#0F172A"));
            metaValPaint.setTextSize(12);
            metaValPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            metaValPaint.setTextAlign(Paint.Align.CENTER);

            // Blood Group
            canvas.drawText("BLOOD GROUP", col1, metaBoxTop + 22, metaLabelPaint);
            Paint bgValPaint = new Paint(metaValPaint);
            bgValPaint.setColor(Color.parseColor("#C62828"));
            bgValPaint.setTextSize(14);
            canvas.drawText(cert.bloodGroup != null ? cert.bloodGroup : "O+", col1, metaBoxTop + 44, bgValPaint);

            // Date
            String dt = cert.donationDate != null && cert.donationDate.length() >= 10 ? cert.donationDate.substring(0, 10) : "Verified";
            canvas.drawText("DONATION DATE", col2, metaBoxTop + 22, metaLabelPaint);
            canvas.drawText(dt, col2, metaBoxTop + 44, metaValPaint);

            // Hospital
            String hosp = cert.hospital != null ? cert.hospital : "Capital Hospital";
            if (hosp.length() > 28) hosp = hosp.substring(0, 26) + "...";
            canvas.drawText("HOSPITAL / CENTER", col3, metaBoxTop + 22, metaLabelPaint);
            canvas.drawText(hosp, col3, metaBoxTop + 44, metaValPaint);

            // Certificate ID
            canvas.drawText("CERTIFICATE ID", col4, metaBoxTop + 22, metaLabelPaint);
            Paint idPaint = new Paint(metaValPaint);
            idPaint.setTypeface(Typeface.MONOSPACE);
            idPaint.setTextSize(10);
            canvas.drawText(cert.certificateId != null ? cert.certificateId : "CERT-LS", col4, metaBoxTop + 44, idPaint);

            // 8. Signatures & Verification QR Code Section
            float signY = 460;

            // Left Signature (Attending Medical Officer)
            Paint signLinePaint = new Paint();
            signLinePaint.setColor(Color.parseColor("#64748B"));
            signLinePaint.setStrokeWidth(1.2f);
            canvas.drawLine(80, signY, 280, signY, signLinePaint);

            Paint signTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            signTextPaint.setColor(Color.parseColor("#0F172A"));
            signTextPaint.setTextSize(11);
            signTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            String docTitle = (cert.attendingDoctor != null && !cert.attendingDoctor.isEmpty()) ? cert.attendingDoctor : "Attending Medical Officer";
            canvas.drawText(docTitle, 80, signY + 16, signTextPaint);

            Paint signSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            signSubPaint.setColor(Color.parseColor("#64748B"));
            signSubPaint.setTextSize(9);
            String docSub = (cert.doctorRegistrationNo != null && !cert.doctorRegistrationNo.isEmpty())
                    ? "Reg No: " + cert.doctorRegistrationNo + " • Medical Officer"
                    : "Attending Medical Officer";
            canvas.drawText(docSub, 80, signY + 30, signSubPaint);

            // Center Verification QR Code
            if (qrBitmap != null) {
                float qrSize = 64;
                float qrLeft = (w - qrSize) / 2f;
                float qrTop = signY - 48;
                canvas.drawBitmap(Bitmap.createScaledBitmap(qrBitmap, (int) qrSize, (int) qrSize, true), qrLeft, qrTop, null);

                Paint qrTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                qrTextPaint.setColor(Color.parseColor("#94A3B8"));
                qrTextPaint.setTextSize(7.5f);
                qrTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                qrTextPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Scan to Verify Online", w / 2f, qrTop + qrSize + 12, qrTextPaint);
            }

            // Right Signature (Hospital Coordinator / LifeShare Audit)
            canvas.drawLine(w - 280, signY, w - 80, signY, signLinePaint);

            Paint dirSignText = new Paint(signTextPaint);
            dirSignText.setTextAlign(Paint.Align.RIGHT);
            String coordTitle = (cert.verifiedBy != null && !cert.verifiedBy.isEmpty()) ? cert.verifiedBy : "Hospital Coordinator";
            canvas.drawText(coordTitle, w - 80, signY + 16, dirSignText);

            Paint dirSignSub = new Paint(signSubPaint);
            dirSignSub.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Auditing Coordinator • LifeShare Network", w - 80, signY + 30, dirSignSub);

            // 9. Cryptographic Audit Seal Bottom Strip
            float bottomY = h - 42;
            Paint hashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            hashPaint.setColor(Color.parseColor("#94A3B8"));
            hashPaint.setTextSize(8);
            hashPaint.setTypeface(Typeface.MONOSPACE);
            canvas.drawText("SHA-256: " + (cert.certificateHash != null ? cert.certificateHash : "Verified"), 60, bottomY, hashPaint);

            Paint tamperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tamperPaint.setColor(Color.parseColor("#2E7D32"));
            tamperPaint.setTextSize(9);
            tamperPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            tamperPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("✓ Authoritative Server Integrity Verified", w - 60, bottomY, tamperPaint);

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
    // MOBILE / WHATSAPP HIGH-RES IMAGE SHARING
    // =========================================================================

    private void shareMobileCertificateImage() {
        if (cardDigitalCertificate == null || loadedCertificate == null) {
            Toast.makeText(this, "Certificate is loading, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
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
            canvas.drawColor(Color.WHITE);
            cardDigitalCertificate.draw(canvas);

            File cacheDir = new File(getCacheDir(), "certificates");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String fileName = "LifeShare_Certificate_" + loadedCertificate.certificateId + ".png";
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
                    "! 🩸 Certified by LifeShare Voluntary Network. #LifeShare #BloodDonor";

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
