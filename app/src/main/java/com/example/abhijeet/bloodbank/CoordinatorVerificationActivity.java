package com.example.abhijeet.bloodbank;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class CoordinatorVerificationActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private NestedScrollView scrollContent;
    private TextView tvSummaryCount, tvHospitalSub, tvSectionTitle;
    private MaterialCardView cardScannerBanner;
    private LinearLayout layoutEmpty, containerDonors;
    private View layoutCoordSkeleton;
    private TextView tvEmptyMessage;
    private MaterialButton btnScanDonorQr, btnEnrollWalkin, btnSwitchToDonor;
    private FrameLayout btnCoordinatorProfile;

    // Floating Bottom Bar Views
    private View cardFloatingBottomBar;
    private LinearLayout tabCoordQueue, tabCoordEmergencies, tabCoordHistory;
    private FrameLayout pillCoordQueue, pillCoordEmergencies, pillCoordHistory;
    private ImageView ivCoordQueue, ivCoordEmergencies, ivCoordHistory;
    private TextView tvCoordQueue, tvCoordEmergencies, tvCoordHistory;

    private ApiClient apiClient;

    private enum CoordTab { QUEUE, EMERGENCIES, HISTORY }
    private CoordTab currentTab = CoordTab.QUEUE;

    private List<ApiClient.PendingVerificationItem> currentLoadedItems = new ArrayList<>();
    private List<ApiClient.AdminEmergencyItem> currentEmergencies = new ArrayList<>();
    private List<ApiClient.CoordinatorHistoryItem> currentHistory = new ArrayList<>();

    private boolean isBottomBarHidden = false;
    private final Handler scrollIdleHandler = new Handler(Looper.getMainLooper());
    private final Runnable revealBottomBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (cardFloatingBottomBar != null && isBottomBarHidden) {
                isBottomBarHidden = false;
                cardFloatingBottomBar.animate()
                        .translationY(0)
                        .setDuration(240)
                        .start();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_coordinator_verification);

        apiClient = ApiClient.getInstance();
        apiClient.initFromPrefs(this);

        initViews();
        setupListeners();
        setupScrollAutoHide();
        handleIncomingIntent(getIntent());
        loadAllData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
        switchTab(currentTab);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        String targetTab = intent.getStringExtra("tab");
        if (targetTab == null && intent.getData() != null) {
            targetTab = intent.getData().getQueryParameter("tab");
        }

        if ("emergencies".equalsIgnoreCase(targetTab)) {
            currentTab = CoordTab.EMERGENCIES;
        } else if ("history".equalsIgnoreCase(targetTab)) {
            currentTab = CoordTab.HISTORY;
        } else {
            currentTab = CoordTab.QUEUE;
        }

        String donorName = intent.getStringExtra("donor_name");
        if (donorName != null && !donorName.isEmpty()) {
            Toast.makeText(this, "Donor Arrived: " + donorName, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scrollIdleHandler.removeCallbacks(revealBottomBarRunnable);
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipe_refresh_coordinator);
        scrollContent = findViewById(R.id.scroll_coord_content);

        tvSummaryCount = findViewById(R.id.tv_coord_summary_count);
        tvHospitalSub = findViewById(R.id.tv_coord_hospital_sub);
        tvSectionTitle = findViewById(R.id.tv_coord_section_title);
        cardScannerBanner = findViewById(R.id.card_coord_scanner_banner);

        layoutEmpty = findViewById(R.id.layout_coord_empty);
        containerDonors = findViewById(R.id.container_pending_donors);
        layoutCoordSkeleton = findViewById(R.id.layout_coord_skeleton);
        tvEmptyMessage = findViewById(R.id.tv_coord_empty_message);

        btnScanDonorQr = findViewById(R.id.btn_scan_donor_qr);
        btnEnrollWalkin = findViewById(R.id.btn_coord_enroll_walkin);
        btnSwitchToDonor = findViewById(R.id.btn_coord_switch_to_donor);
        btnCoordinatorProfile = findViewById(R.id.btn_coordinator_profile);

        // Floating Bottom Bar
        cardFloatingBottomBar = findViewById(R.id.card_coord_floating_bottom_bar);
        tabCoordQueue = findViewById(R.id.tab_coord_queue);
        tabCoordEmergencies = findViewById(R.id.tab_coord_emergencies);
        tabCoordHistory = findViewById(R.id.tab_coord_history);

        pillCoordQueue = findViewById(R.id.pill_coord_queue);
        pillCoordEmergencies = findViewById(R.id.pill_coord_emergencies);
        pillCoordHistory = findViewById(R.id.pill_coord_history);

        ivCoordQueue = findViewById(R.id.iv_coord_queue);
        ivCoordEmergencies = findViewById(R.id.iv_coord_emergencies);
        ivCoordHistory = findViewById(R.id.iv_coord_history);

        tvCoordQueue = findViewById(R.id.tv_coord_queue);
        tvCoordEmergencies = findViewById(R.id.tv_coord_emergencies);
        tvCoordHistory = findViewById(R.id.tv_coord_history);

        UserProfile user = DataManager.getInstance(this).getCurrentUser();
        if (user != null && tvHospitalSub != null) {
            tvHospitalSub.setText(user.getCity() != null ? user.getCity() + " • Hospital Desk" : "Hospital Verification Desk");
        }
    }

    private void setupListeners() {
        if (btnSwitchToDonor != null) {
            btnSwitchToDonor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(CoordinatorVerificationActivity.this, LogInActivity.class));
                }
            });
        }

        if (btnCoordinatorProfile != null) {
            btnCoordinatorProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(CoordinatorVerificationActivity.this, ProfileActivity.class));
                }
            });
        }

        if (btnScanDonorQr != null) {
            btnScanDonorQr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startQrScanner();
                }
            });
        }

        if (btnEnrollWalkin != null) {
            btnEnrollWalkin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEnrollWalkinDialog();
                }
            });
        }

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadAllData();
                }
            });
        }

        // Floating Bottom Bar Tabs
        tabCoordQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(CoordTab.QUEUE);
            }
        });

        tabCoordEmergencies.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(CoordTab.EMERGENCIES);
            }
        });

        tabCoordHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(CoordTab.HISTORY);
            }
        });
    }

    private void setupScrollAutoHide() {
        if (scrollContent == null || cardFloatingBottomBar == null) return;

        scrollContent.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                int dy = scrollY - oldScrollY;

                // Cancel pending idle timer
                scrollIdleHandler.removeCallbacks(revealBottomBarRunnable);

                if (dy > 8 && !isBottomBarHidden) {
                    // Scrolling down -> Hide floating bottom bar smoothly
                    isBottomBarHidden = true;
                    cardFloatingBottomBar.animate()
                            .translationY(cardFloatingBottomBar.getHeight() + 80)
                            .setDuration(200)
                            .start();
                } else if (dy < -8 && isBottomBarHidden) {
                    // Scrolling up -> Reveal floating bottom bar
                    isBottomBarHidden = false;
                    cardFloatingBottomBar.animate()
                            .translationY(0)
                            .setDuration(200)
                            .start();
                } else if (scrollY <= 0 && isBottomBarHidden) {
                    // Reached top -> Ensure visible
                    isBottomBarHidden = false;
                    cardFloatingBottomBar.animate()
                            .translationY(0)
                            .setDuration(200)
                            .start();
                }

                // Auto reveal after user stops scrolling (idle debounce 500ms)
                scrollIdleHandler.postDelayed(revealBottomBarRunnable, 500);
            }
        });
    }

    private void switchTab(CoordTab tab) {
        currentTab = tab;

        // Reset bottom pill backgrounds
        pillCoordQueue.setBackgroundResource(0);
        pillCoordEmergencies.setBackgroundResource(0);
        pillCoordHistory.setBackgroundResource(0);

        int colorPrimary = getResources().getColor(R.color.colorPrimary);
        int colorInactive = getResources().getColor(R.color.text_secondary);

        ivCoordQueue.setImageTintList(ColorStateList.valueOf(colorInactive));
        ivCoordEmergencies.setImageTintList(ColorStateList.valueOf(colorInactive));
        ivCoordHistory.setImageTintList(ColorStateList.valueOf(colorInactive));

        tvCoordQueue.setTextColor(colorInactive);
        tvCoordEmergencies.setTextColor(colorInactive);
        tvCoordHistory.setTextColor(colorInactive);

        if (tab == CoordTab.QUEUE) {
            pillCoordQueue.setBackgroundResource(R.drawable.bg_tab_indicator_active);
            ivCoordQueue.setImageTintList(ColorStateList.valueOf(colorPrimary));
            tvCoordQueue.setTextColor(colorPrimary);

            if (cardScannerBanner != null) cardScannerBanner.setVisibility(View.VISIBLE);
            if (tvSectionTitle != null) tvSectionTitle.setText("Donors in Queue & En Route");
            loadPendingVerifications();
        } else if (tab == CoordTab.EMERGENCIES) {
            pillCoordEmergencies.setBackgroundResource(R.drawable.bg_tab_indicator_active);
            ivCoordEmergencies.setImageTintList(ColorStateList.valueOf(colorPrimary));
            tvCoordEmergencies.setTextColor(colorPrimary);

            if (cardScannerBanner != null) cardScannerBanner.setVisibility(View.GONE);
            if (tvSectionTitle != null) tvSectionTitle.setText("Hospital Active Emergencies");
            loadHospitalEmergencies();
        } else if (tab == CoordTab.HISTORY) {
            pillCoordHistory.setBackgroundResource(R.drawable.bg_tab_indicator_active);
            ivCoordHistory.setImageTintList(ColorStateList.valueOf(colorPrimary));
            tvCoordHistory.setTextColor(colorPrimary);

            if (cardScannerBanner != null) cardScannerBanner.setVisibility(View.GONE);
            if (tvSectionTitle != null) tvSectionTitle.setText("Verified Donation History");
            loadHistory();
        }
    }

    private void loadAllData() {
        switchTab(currentTab);
    }

    // =========================================================================
    // TAB 1: ARRIVED & EN ROUTE DONORS QUEUE
    // =========================================================================

    private void loadPendingVerifications() {
        if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.VISIBLE);
        if (containerDonors != null) containerDonors.setVisibility(View.GONE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getCoordinatorPendingVerifications(new ApiClient.ApiCallback<List<ApiClient.PendingVerificationItem>>() {
            @Override
            public void onSuccess(List<ApiClient.PendingVerificationItem> result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
                currentLoadedItems = result != null ? result : new ArrayList<ApiClient.PendingVerificationItem>();
                if (currentTab == CoordTab.QUEUE) renderPendingVerifications(currentLoadedItems);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
                Toast.makeText(CoordinatorVerificationActivity.this, "Failed to load queue: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderPendingVerifications(List<ApiClient.PendingVerificationItem> items) {
        if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
        if (containerDonors == null) return;
        containerDonors.removeAllViews();
        containerDonors.setVisibility(View.VISIBLE);

        if (items == null || items.isEmpty()) {
            if (tvSummaryCount != null) tvSummaryCount.setText("0 Donors in queue");
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setText("No donors currently waiting or en route");
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        int arrivedCount = 0;
        for (ApiClient.PendingVerificationItem it : items) {
            if ("ARRIVED".equalsIgnoreCase(it.status)) arrivedCount++;
        }

        if (tvSummaryCount != null) {
            tvSummaryCount.setText(arrivedCount + " At Desk • " + (items.size() - arrivedCount) + " En Route");
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final ApiClient.PendingVerificationItem item : items) {
            View view = inflater.inflate(R.layout.item_pending_verification, containerDonors, false);

            TextView tvStatusBadge = view.findViewById(R.id.tv_item_status_badge);
            TextView tvArrivedTime = view.findViewById(R.id.tv_item_arrived_time);
            TextView tvDonorBlood = view.findViewById(R.id.tv_item_donor_blood);
            TextView tvDonorName = view.findViewById(R.id.tv_item_donor_name);
            TextView tvDonorContact = view.findViewById(R.id.tv_item_donor_contact);
            TextView tvPatientInfo = view.findViewById(R.id.tv_item_patient_info);
            TextView tvHospital = view.findViewById(R.id.tv_item_hospital);

            MaterialButton btnVerify = view.findViewById(R.id.btn_item_verify_donation);
            LinearLayout layoutContactActions = view.findViewById(R.id.layout_item_contact_actions);
            MaterialButton btnCallDonor = view.findViewById(R.id.btn_item_call_donor);
            MaterialButton btnWhatsappDonor = view.findViewById(R.id.btn_item_whatsapp_donor);

            boolean isArrived = "ARRIVED".equalsIgnoreCase(item.status);

            if (tvStatusBadge != null) {
                if (isArrived) {
                    tvStatusBadge.setText("At Hospital Desk");
                    tvStatusBadge.setTextColor(getResources().getColor(R.color.hospital_teal));
                } else {
                    tvStatusBadge.setText("On The Way");
                    tvStatusBadge.setTextColor(getResources().getColor(R.color.cooldown_orange));
                }
            }

            if (tvArrivedTime != null) {
                String timeStr = isArrived ? item.arrivedAt : item.createdAt;
                if (timeStr != null && timeStr.length() >= 16) {
                    timeStr = timeStr.substring(0, 10) + " " + timeStr.substring(11, 16);
                }
                tvArrivedTime.setText(timeStr != null ? timeStr : (isArrived ? "Arrived" : "Accepted"));
            }

            if (tvDonorBlood != null) tvDonorBlood.setText(item.donorBloodGroup != null ? item.donorBloodGroup : "O+");
            if (tvDonorName != null) tvDonorName.setText(item.donorName != null ? item.donorName : "Voluntary Donor");
            if (tvDonorContact != null) {
                tvDonorContact.setText(item.donorMobile != null && !item.donorMobile.isEmpty() ? item.donorMobile : "Contact hidden");
            }
            if (tvPatientInfo != null) {
                tvPatientInfo.setText("Patient: " + item.patientName + " (" + item.unitsFulfilled + "/" + item.unitsRequired + " fulfilled)");
            }
            if (tvHospital != null) tvHospital.setText(item.hospital != null ? item.hospital : "Hospital");

            if (isArrived) {
                if (btnVerify != null) {
                    btnVerify.setVisibility(View.VISIBLE);
                    btnVerify.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showConfirmVerificationDialog(item);
                        }
                    });
                }
                if (layoutContactActions != null) layoutContactActions.setVisibility(View.GONE);
            } else {
                // En route donor: direct call & WhatsApp communication
                if (btnVerify != null) btnVerify.setVisibility(View.GONE);
                if (layoutContactActions != null) layoutContactActions.setVisibility(View.VISIBLE);

                if (btnCallDonor != null) {
                    btnCallDonor.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (item.donorMobile != null && !item.donorMobile.isEmpty()) {
                                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.donorMobile.trim()));
                                startActivity(callIntent);
                            } else {
                                Toast.makeText(CoordinatorVerificationActivity.this, "Donor phone number is not available", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }

                if (btnWhatsappDonor != null) {
                    btnWhatsappDonor.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (item.donorMobile != null && !item.donorMobile.isEmpty()) {
                                try {
                                    String cleanPhone = item.donorMobile.replaceAll("[^0-9]", "");
                                    String msg = "Hello " + item.donorName + ", this is the LifeShare Blood Bank Desk at " + item.hospital + ". Thank you for volunteering to donate blood for patient " + item.patientName + ". Please let us know your ETA or if you need directions!";
                                    String url = "https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + URLEncoder.encode(msg, "UTF-8");
                                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    startActivity(i);
                                } catch (Exception e) {
                                    Toast.makeText(CoordinatorVerificationActivity.this, "Could not open WhatsApp: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(CoordinatorVerificationActivity.this, "Donor phone number is not available", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }

            containerDonors.addView(view);
        }
    }

    private void startQrScanner() {
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Scan Donor QR Code on Donor Screen");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.setOrientationLocked(true); // Locked to vertical portrait mode
            integrator.initiateScan();
        } catch (Throwable t) {
            Toast.makeText(this, "Scanner error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                handleScannedDonorQr(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleScannedDonorQr(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            Toast.makeText(this, "Scanned code was empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String targetUserId = null;
        String targetDonorCode = null;
        String targetName = null;

        if (rawValue.contains("|")) {
            String[] parts = rawValue.split("\\|");
            for (String part : parts) {
                if (part.startsWith("USERID=")) {
                    targetUserId = part.substring("USERID=".length()).trim();
                } else if (part.startsWith("ID=")) {
                    targetUserId = part.substring("ID=".length()).trim();
                } else if (part.startsWith("CODE=")) {
                    targetDonorCode = part.substring("CODE=".length()).trim();
                } else if (part.startsWith("NAME=")) {
                    targetName = part.substring("NAME=".length()).trim();
                }
            }
        } else {
            targetUserId = rawValue.trim();
        }

        ApiClient.PendingVerificationItem matched = null;
        for (ApiClient.PendingVerificationItem item : currentLoadedItems) {
            if (targetUserId != null && !targetUserId.isEmpty() && targetUserId.equalsIgnoreCase(item.donorId)) {
                matched = item;
                break;
            }
            if (targetDonorCode != null && !targetDonorCode.isEmpty() && targetDonorCode.equalsIgnoreCase(item.donorId)) {
                matched = item;
                break;
            }
            if (targetName != null && !targetName.isEmpty() && item.donorName != null && item.donorName.equalsIgnoreCase(targetName)) {
                matched = item;
                break;
            }
        }

        if (matched != null) {
            Toast.makeText(this, "Donor matched: " + matched.donorName, Toast.LENGTH_SHORT).show();
            showConfirmVerificationDialog(matched);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Donor Not Found in Arrived Queue")
                    .setMessage("Scanned QR details:\n" + (targetName != null ? targetName : rawValue) + "\n\nThis donor is not currently marked as 'ARRIVED' for an active emergency at this hospital.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showConfirmVerificationDialog(final ApiClient.PendingVerificationItem item) {
        if (item == null || item.requestId == null || item.donorId == null) {
            Toast.makeText(this, "Invalid request or donor identifier", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_verify_donation_doctor, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvDonorSummary = dialogView.findViewById(R.id.tv_verify_donor_summary);
        TextView tvPatientSummary = dialogView.findViewById(R.id.tv_verify_patient_summary);
        final EditText etDoctorName = dialogView.findViewById(R.id.et_doctor_name);
        final EditText etDoctorRegNo = dialogView.findViewById(R.id.et_doctor_reg_no);
        final EditText etUnits = dialogView.findViewById(R.id.et_units_donated);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_verify);
        View btnSubmit = dialogView.findViewById(R.id.btn_confirm_verify_submit);

        if (tvDonorSummary != null) {
            tvDonorSummary.setText("Donor: " + (item.donorName != null ? item.donorName : "Voluntary Donor") + " (" + (item.donorBloodGroup != null ? item.donorBloodGroup : "O+") + ")");
        }
        if (tvPatientSummary != null) {
            tvPatientSummary.setText("Patient: " + (item.patientName != null ? item.patientName : "Emergency Patient") + " • " + (item.hospital != null ? item.hospital : "Hospital"));
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String docName = etDoctorName != null ? etDoctorName.getText().toString().trim() : "";
                String docReg = etDoctorRegNo != null ? etDoctorRegNo.getText().toString().trim() : "";
                String unitsStr = etUnits != null ? etUnits.getText().toString().trim() : "1";

                if (docName.isEmpty()) {
                    if (etDoctorName != null) etDoctorName.setError("Doctor name required");
                    return;
                }
                if (docReg.isEmpty()) {
                    if (etDoctorRegNo != null) etDoctorRegNo.setError("Registration number required");
                    return;
                }

                int units = 1;
                try {
                    units = Integer.parseInt(unitsStr);
                } catch (Exception ignored) {}

                dialog.dismiss();
                performVerification(item, docName, docReg, units);
            });
        }

        dialog.show();
    }

    private void performVerification(final ApiClient.PendingVerificationItem item, String doctorName, String doctorRegNo, int units) {
        if (item.requestId == null || item.donorId == null) {
            Toast.makeText(this, "Invalid request or donor identifier", Toast.LENGTH_SHORT).show();
            return;
        }

        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.verifyDonation(item.requestId, item.donorId, doctorName, doctorRegNo, units, new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                String certId = result.has("certificateId") ? result.get("certificateId").getAsString() : "";
                Toast.makeText(CoordinatorVerificationActivity.this, "Donation verified by " + doctorName + "! Certificate: " + certId, Toast.LENGTH_LONG).show();
                loadPendingVerifications();
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(CoordinatorVerificationActivity.this, "Verification failed: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEnrollWalkinDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_enroll_walkin_donor, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        final EditText etName = dialogView.findViewById(R.id.et_walkin_name);
        final EditText etMobile = dialogView.findViewById(R.id.et_walkin_mobile);
        final EditText etBloodGroup = dialogView.findViewById(R.id.et_walkin_blood_group);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_walkin);
        View btnSubmit = dialogView.findViewById(R.id.btn_submit_walkin);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String name = etName != null ? etName.getText().toString().trim() : "";
                String mobile = etMobile != null ? etMobile.getText().toString().trim() : "";
                String blood = etBloodGroup != null ? etBloodGroup.getText().toString().trim().toUpperCase() : "O+";

                if (name.isEmpty()) {
                    if (etName != null) etName.setError("Full name required");
                    return;
                }
                if (mobile.length() < 10) {
                    if (etMobile != null) etMobile.setError("Valid 10-digit mobile required");
                    return;
                }

                dialog.dismiss();
                if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

                apiClient.coordinatorOnboardDonor(name, mobile, blood, "Male", new ApiClient.ApiCallback<JsonObject>() {
                    @Override
                    public void onSuccess(JsonObject result) {
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        Toast.makeText(CoordinatorVerificationActivity.this, "Donor " + name + " (" + blood + ") enrolled & verified in rescue pool!", Toast.LENGTH_LONG).show();
                        loadAllData();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        Toast.makeText(CoordinatorVerificationActivity.this, "Enrollment failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        dialog.show();
    }

    // =========================================================================
    // TAB 2: HOSPITAL EMERGENCIES
    // =========================================================================

    private void loadHospitalEmergencies() {
        if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.VISIBLE);
        if (containerDonors != null) containerDonors.setVisibility(View.GONE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getCoordinatorHospitalEmergencies(new ApiClient.ApiCallback<List<ApiClient.AdminEmergencyItem>>() {
            @Override
            public void onSuccess(List<ApiClient.AdminEmergencyItem> emergencies) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
                currentEmergencies = emergencies != null ? emergencies : new ArrayList<ApiClient.AdminEmergencyItem>();
                if (currentTab == CoordTab.EMERGENCIES) renderHospitalEmergencies(currentEmergencies);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
                Toast.makeText(CoordinatorVerificationActivity.this, "Error loading emergencies: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderHospitalEmergencies(List<ApiClient.AdminEmergencyItem> emergencies) {
        if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
        if (containerDonors == null) return;
        containerDonors.removeAllViews();
        containerDonors.setVisibility(View.VISIBLE);

        if (emergencies == null || emergencies.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setText("No active emergency SOS requests for this hospital");
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (ApiClient.AdminEmergencyItem item : emergencies) {
            View view = inflater.inflate(R.layout.item_admin_emergency, containerDonors, false);

            TextView tvBlood = view.findViewById(R.id.tv_admin_em_blood);
            TextView tvPatient = view.findViewById(R.id.tv_admin_em_patient);
            TextView tvHospital = view.findViewById(R.id.tv_admin_em_hospital);
            TextView tvUrgency = view.findViewById(R.id.tv_admin_em_urgency_badge);
            TextView tvStatus = view.findViewById(R.id.tv_admin_em_status_badge);
            TextView tvFulfillment = view.findViewById(R.id.tv_admin_em_fulfillment);
            TextView tvTime = view.findViewById(R.id.tv_admin_em_time);

            if (tvBlood != null) tvBlood.setText(item.bloodGroup != null ? item.bloodGroup : "O+");
            if (tvPatient != null) tvPatient.setText(item.patientName != null ? item.patientName : "Patient");
            if (tvHospital != null) tvHospital.setText(item.hospital != null ? item.hospital : "Hospital");
            if (tvUrgency != null) tvUrgency.setText(item.urgency != null ? item.urgency : "CRITICAL");

            // Human-friendly status marker
            if (tvStatus != null) {
                String rawStatus = item.status != null ? item.status : "ACTIVE";
                if ("ACTIVE".equalsIgnoreCase(rawStatus)) {
                    tvStatus.setText("Seeking Donors");
                    tvStatus.setTextColor(getResources().getColor(R.color.colorPrimary));
                } else if ("PARTIAL".equalsIgnoreCase(rawStatus)) {
                    tvStatus.setText("Partially Fulfilled");
                    tvStatus.setTextColor(getResources().getColor(R.color.cooldown_orange));
                } else if ("FULFILLED".equalsIgnoreCase(rawStatus)) {
                    tvStatus.setText("Fulfilled");
                    tvStatus.setTextColor(getResources().getColor(R.color.status_available));
                } else {
                    tvStatus.setText("Closed");
                    tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
                }
            }

            if (tvFulfillment != null) {
                tvFulfillment.setText("Fulfillment: " + item.unitsFulfilled + " / " + item.unitsRequired + " Units");
            }
            if (tvTime != null) {
                String t = item.createdAt;
                if (t != null && t.length() >= 16) t = t.substring(0, 10) + " " + t.substring(11, 16);
                tvTime.setText(t != null ? t : "");
            }

            containerDonors.addView(view);
        }
    }

    // =========================================================================
    // TAB 3: VERIFICATION HISTORY
    // =========================================================================

    private void loadHistory() {
        if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.VISIBLE);
        if (containerDonors != null) containerDonors.setVisibility(View.GONE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getCoordinatorHistory(new ApiClient.ApiCallback<List<ApiClient.CoordinatorHistoryItem>>() {
            @Override
            public void onSuccess(List<ApiClient.CoordinatorHistoryItem> history) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
                currentHistory = history != null ? history : new ArrayList<ApiClient.CoordinatorHistoryItem>();
                if (currentTab == CoordTab.HISTORY) renderHistory(currentHistory);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
                Toast.makeText(CoordinatorVerificationActivity.this, "Error loading history: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderHistory(List<ApiClient.CoordinatorHistoryItem> history) {
        if (layoutCoordSkeleton != null) layoutCoordSkeleton.setVisibility(View.GONE);
        if (containerDonors == null) return;
        containerDonors.removeAllViews();
        containerDonors.setVisibility(View.VISIBLE);

        if (history == null || history.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setText("No verified donations on record for this desk yet");
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (ApiClient.CoordinatorHistoryItem item : history) {
            View view = inflater.inflate(R.layout.item_coordinator_history, containerDonors, false);

            TextView tvBlood = view.findViewById(R.id.tv_coord_hist_blood);
            TextView tvDonor = view.findViewById(R.id.tv_coord_hist_donor);
            TextView tvCert = view.findViewById(R.id.tv_coord_hist_cert);
            TextView tvUnits = view.findViewById(R.id.tv_coord_hist_units);
            TextView tvTime = view.findViewById(R.id.tv_coord_hist_time);

            if (tvBlood != null) tvBlood.setText(item.donorBloodGroup != null ? item.donorBloodGroup : "O+");
            if (tvDonor != null) tvDonor.setText(item.donorName != null ? item.donorName : "Donor");
            if (tvCert != null) tvCert.setText("Cert: " + (item.certificateId != null ? item.certificateId : ""));
            if (tvUnits != null) tvUnits.setText(item.unitsDonated + " Unit" + (item.unitsDonated > 1 ? "s" : "") + " Donated");
            if (tvTime != null) {
                String t = item.verifiedAt;
                if (t != null && t.length() >= 16) t = t.substring(0, 10) + " " + t.substring(11, 16);
                tvTime.setText(t != null ? t : "");
            }

            containerDonors.addView(view);
        }
    }
}
