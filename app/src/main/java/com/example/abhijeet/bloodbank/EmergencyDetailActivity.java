package com.example.abhijeet.bloodbank;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class EmergencyDetailActivity extends AppCompatActivity {

    private String emergencyId = "";
    private TextView tvBloodGroup, tvPatient, tvUnits, tvUrgency, tvHospital, tvHospitalAddress, tvDistance, tvStatus;
    private MaterialButton btnNavigateHospital;

    // Donor Journey Tracker Components
    private MaterialCardView cardDonorJourney;
    private TextView iconStepNotified, iconStepAccepted, iconStepTravelling, iconStepArrived, iconStepVerified;
    private TextView tvStepTitleNotified, tvStepTitleAccepted, tvStepTitleTravelling, tvStepTitleArrived, tvStepTitleVerified;
    private TextView tvStepTimeNotified, tvStepTimeAccepted, tvStepTimeTravelling, tvStepTimeArrived, tvStepTimeVerified;

    private View layoutResponseButtons, layoutJourneyActions, layoutVerificationPending;
    private MaterialButton btnAccept, btnDecline, btnStartJourney, btnMarkArrived, btnCallCoordinator;
    private TextView tvDonorFeedback;

    // Requester View Components
    private MaterialCardView cardRequesterMetrics;
    private TextView tvStatNotified, tvStatAccepted, tvStatRemaining, tvNoDonorsYet;
    private LinearLayout layoutAcceptedDonorsList;
    private MaterialButton btnCancel;

    private ProgressBar progressBar;
    private View layoutDetailSkeleton, layoutDetailContent;

    private EmergencyRequest currentEmergency = null;
    private String coordinatorPhone = "";
    private boolean isRequester = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_emergency_detail);

        View btnBack = findViewById(R.id.btn_floating_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (getIntent().getData() != null) {
            android.net.Uri data = getIntent().getData();
            String queryId = data.getQueryParameter("id");
            if (queryId != null && !queryId.isEmpty()) {
                emergencyId = queryId;
            } else if (data.getLastPathSegment() != null && !data.getLastPathSegment().isEmpty() && !"emergency".equalsIgnoreCase(data.getLastPathSegment())) {
                emergencyId = data.getLastPathSegment();
            }
        }
        if (emergencyId == null || emergencyId.isEmpty()) {
            emergencyId = getIntent().getStringExtra("emergency_id");
        }
        if (emergencyId == null || emergencyId.isEmpty()) {
            emergencyId = getIntent().getStringExtra("requestId");
        }
        if (emergencyId == null || emergencyId.isEmpty()) {
            emergencyId = getIntent().getStringExtra("request_id");
        }
        if (emergencyId == null || emergencyId.isEmpty()) {
            Toast.makeText(this, "Emergency request ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadEmergencyDetails();
    }

    private void initViews() {
        tvBloodGroup = findViewById(R.id.tv_detail_blood_group);
        tvPatient = findViewById(R.id.tv_detail_patient);
        tvUnits = findViewById(R.id.tv_detail_units);
        tvUrgency = findViewById(R.id.tv_detail_urgency);
        tvHospital = findViewById(R.id.tv_detail_hospital);
        tvHospitalAddress = findViewById(R.id.tv_detail_hospital_address);
        tvDistance = findViewById(R.id.tv_detail_distance);
        tvStatus = findViewById(R.id.tv_detail_status);
        btnNavigateHospital = findViewById(R.id.btn_navigate_hospital);

        // Journey Steps
        cardDonorJourney = findViewById(R.id.card_donor_journey);
        iconStepNotified = findViewById(R.id.icon_step_notified);
        iconStepAccepted = findViewById(R.id.icon_step_accepted);
        iconStepTravelling = findViewById(R.id.icon_step_travelling);
        iconStepArrived = findViewById(R.id.icon_step_arrived);
        iconStepVerified = findViewById(R.id.icon_step_verified);

        tvStepTitleNotified = findViewById(R.id.tv_step_title_notified);
        tvStepTitleAccepted = findViewById(R.id.tv_step_title_accepted);
        tvStepTitleTravelling = findViewById(R.id.tv_step_title_travelling);
        tvStepTitleArrived = findViewById(R.id.tv_step_title_arrived);
        tvStepTitleVerified = findViewById(R.id.tv_step_title_verified);

        tvStepTimeNotified = findViewById(R.id.tv_step_time_notified);
        tvStepTimeAccepted = findViewById(R.id.tv_step_time_accepted);
        tvStepTimeTravelling = findViewById(R.id.tv_step_time_travelling);
        tvStepTimeArrived = findViewById(R.id.tv_step_time_arrived);
        tvStepTimeVerified = findViewById(R.id.tv_step_time_verified);

        layoutResponseButtons = findViewById(R.id.layout_response_buttons);
        layoutJourneyActions = findViewById(R.id.layout_journey_actions);
        layoutVerificationPending = findViewById(R.id.layout_verification_pending);
        btnAccept = findViewById(R.id.btn_detail_accept);
        btnDecline = findViewById(R.id.btn_detail_decline);
        btnStartJourney = findViewById(R.id.btn_detail_start_journey);
        btnMarkArrived = findViewById(R.id.btn_detail_mark_arrived);
        btnCallCoordinator = findViewById(R.id.btn_call_coordinator);
        tvDonorFeedback = findViewById(R.id.tv_donor_feedback);

        // Requester View
        cardRequesterMetrics = findViewById(R.id.card_requester_metrics);
        tvStatNotified = findViewById(R.id.tv_stat_notified);
        tvStatAccepted = findViewById(R.id.tv_stat_accepted);
        tvStatRemaining = findViewById(R.id.tv_stat_remaining);
        layoutAcceptedDonorsList = findViewById(R.id.layout_accepted_donors_list);
        tvNoDonorsYet = findViewById(R.id.tv_no_donors_yet);
        btnCancel = findViewById(R.id.btn_detail_cancel);

        progressBar = findViewById(R.id.pb_emergency_detail);
        layoutDetailSkeleton = findViewById(R.id.layout_detail_skeleton);
        layoutDetailContent = findViewById(R.id.layout_detail_content);

        // Navigation button click
        btnNavigateHospital.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentEmergency != null) {
                    launchNavigationToHospital(
                            currentEmergency.getHospitalLatitude(),
                            currentEmergency.getHospitalLongitude(),
                            currentEmergency.getHospital()
                    );
                }
            }
        });

        // Call Coordinator
        btnCallCoordinator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (coordinatorPhone != null && !coordinatorPhone.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + coordinatorPhone.trim()));
                    startActivity(intent);
                } else {
                    Toast.makeText(EmergencyDetailActivity.this, "Coordinator phone number not provided", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Accept SOS
        btnAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitInitialResponse("ACCEPTED");
            }
        });

        // Decline SOS
        btnDecline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitInitialResponse("DECLINED");
            }
        });

        // Start Journey & Navigate
        btnStartJourney.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitJourneyAction("TRAVELLING", true);
            }
        });

        // I Have Arrived
        btnMarkArrived.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitJourneyAction("ARRIVED", false);
            }
        });

        // Cancel SOS (Requester Only)
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmCancelEmergency();
            }
        });
    }

    private void loadEmergencyDetails() {
        if (layoutDetailSkeleton != null) layoutDetailSkeleton.setVisibility(View.VISIBLE);
        if (layoutDetailContent != null) layoutDetailContent.setVisibility(View.GONE);

        ApiClient.getInstance().getEmergencyDetail(emergencyId, new ApiClient.ApiCallback<ApiClient.EmergencyDetailResponse>() {
            @Override
            public void onSuccess(ApiClient.EmergencyDetailResponse detail) {
                if (detail != null && detail.emergency != null) {
                    currentEmergency = detail.emergency;
                    bindData(detail);
                } else {
                    if (layoutDetailSkeleton != null) layoutDetailSkeleton.setVisibility(View.GONE);
                    Toast.makeText(EmergencyDetailActivity.this, "This emergency request is no longer active.", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onError(String error) {
                if (layoutDetailSkeleton != null) layoutDetailSkeleton.setVisibility(View.GONE);
                Toast.makeText(EmergencyDetailActivity.this, "Failed to load details: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindData(ApiClient.EmergencyDetailResponse detail) {
        if (layoutDetailSkeleton != null) layoutDetailSkeleton.setVisibility(View.GONE);
        if (layoutDetailContent != null) {
            layoutDetailContent.setAlpha(0f);
            layoutDetailContent.setVisibility(View.VISIBLE);
            layoutDetailContent.animate().alpha(1f).setDuration(250).start();
        }

        EmergencyRequest req = detail.emergency;

        tvBloodGroup.setText(req.getBloodGroup());
        tvPatient.setText(req.getPatientName());
        tvUnits.setText(req.getUnitsRequired() + (req.getUnitsRequired() == 1 ? " Unit Required" : " Units Required"));
        tvUrgency.setText(req.getUrgency());

        tvHospital.setText(req.getHospital());
        if (req.getHospitalAddress() != null && !req.getHospitalAddress().isEmpty()) {
            tvHospitalAddress.setText(req.getHospitalAddress());
            tvHospitalAddress.setVisibility(View.VISIBLE);
        } else {
            tvHospitalAddress.setVisibility(View.GONE);
        }

        // Use server-side user-level status marker
        tvStatus.setText(req.getStatusDisplay());
        coordinatorPhone = req.getContactNumber();

        UserProfile currentUser = DataManager.getInstance(this).getCurrentUser();
        isRequester = detail.isRequester || (currentUser != null && !req.getPostedBy().isEmpty() && req.getPostedBy().equalsIgnoreCase(currentUser.getEmail()));

        if (isRequester) {
            // Requester View
            cardRequesterMetrics.setVisibility(View.VISIBLE);
            cardDonorJourney.setVisibility(View.GONE);

            tvStatNotified.setText(detail.notifiedCount > 0 ? String.valueOf(detail.notifiedCount) : "10+");
            tvStatAccepted.setText(String.valueOf(detail.acceptedCount));
            tvStatRemaining.setText(String.valueOf(detail.remainingUnits));

            populateAcceptedDonors(detail.acceptedDonors);
        } else {
            // Donor View with Visual Tracker
            cardRequesterMetrics.setVisibility(View.GONE);
            cardDonorJourney.setVisibility(View.VISIBLE);

            bindDonorJourneyTracker(detail.myJourney);
        }
    }

    private void bindDonorJourneyTracker(ApiClient.DonorJourneyInfo journey) {
        String journeyStatus = journey != null ? journey.status : "NOTIFIED";

        // Reset all steps to inactive defaults
        resetStep(iconStepNotified, tvStepTitleNotified, tvStepTimeNotified, "✓", "Request Received", "Matched with emergency");
        resetStep(iconStepAccepted, tvStepTitleAccepted, tvStepTimeAccepted, "2", "Emergency Accepted", "Awaiting response");
        resetStep(iconStepTravelling, tvStepTitleTravelling, tvStepTimeTravelling, "3", "Travelling to Hospital", "Not started yet");
        resetStep(iconStepArrived, tvStepTitleArrived, tvStepTimeArrived, "4", "Arrived at Hospital", "Pending arrival");
        resetStep(iconStepVerified, tvStepTitleVerified, tvStepTimeVerified, "5", "Donation Verification", "Requires medical staff certification");

        layoutResponseButtons.setVisibility(View.GONE);
        layoutJourneyActions.setVisibility(View.GONE);
        btnStartJourney.setVisibility(View.GONE);
        btnMarkArrived.setVisibility(View.GONE);
        layoutVerificationPending.setVisibility(View.GONE);
        tvDonorFeedback.setVisibility(View.GONE);

        if ("NOTIFIED".equalsIgnoreCase(journeyStatus) || "VIEWED".equalsIgnoreCase(journeyStatus)) {
            setStepActive(iconStepNotified, tvStepTitleNotified, tvStepTimeNotified, "✓", "Request Received", "Tap below to respond");
            layoutResponseButtons.setVisibility(View.VISIBLE);

        } else if ("ACCEPTED".equalsIgnoreCase(journeyStatus)) {
            setStepCompleted(iconStepNotified, tvStepTitleNotified, tvStepTimeNotified, "✓", "Request Received", "Notified");
            setStepActive(iconStepAccepted, tvStepTitleAccepted, tvStepTimeAccepted, "✓", "Emergency Accepted", "Ready to depart");

            layoutJourneyActions.setVisibility(View.VISIBLE);
            btnStartJourney.setVisibility(View.VISIBLE);
            tvDonorFeedback.setVisibility(View.VISIBLE);
            tvDonorFeedback.setText("You accepted this request. Please tap 'Start Journey' when you leave.");
            tvDonorFeedback.setTextColor(ContextCompat.getColor(this, R.color.status_available));

        } else if ("TRAVELLING".equalsIgnoreCase(journeyStatus)) {
            setStepCompleted(iconStepNotified, tvStepTitleNotified, tvStepTimeNotified, "✓", "Request Received", "Notified");
            setStepCompleted(iconStepAccepted, tvStepTitleAccepted, tvStepTimeAccepted, "✓", "Emergency Accepted", "Accepted");
            setStepActive(iconStepTravelling, tvStepTitleTravelling, tvStepTimeTravelling, "→", "Travelling to Hospital", "In transit to destination");

            layoutJourneyActions.setVisibility(View.VISIBLE);
            btnMarkArrived.setVisibility(View.VISIBLE);
            tvDonorFeedback.setVisibility(View.VISIBLE);
            tvDonorFeedback.setText("Status: In transit. Hospital coordinator expects your arrival.");
            tvDonorFeedback.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));

        } else if ("ARRIVED".equalsIgnoreCase(journeyStatus)) {
            setStepCompleted(iconStepNotified, tvStepTitleNotified, tvStepTimeNotified, "✓", "Request Received", "Notified");
            setStepCompleted(iconStepAccepted, tvStepTitleAccepted, tvStepTimeAccepted, "✓", "Emergency Accepted", "Accepted");
            setStepCompleted(iconStepTravelling, tvStepTitleTravelling, tvStepTimeTravelling, "✓", "Travelling to Hospital", "Completed");
            setStepCompleted(iconStepArrived, tvStepTitleArrived, tvStepTimeArrived, "✓", "Arrived at Hospital", "At blood bank counter");
            setStepActive(iconStepVerified, tvStepTitleVerified, tvStepTimeVerified, "•", "Donation Verification", "Pending hospital certification");

            layoutJourneyActions.setVisibility(View.VISIBLE);
            layoutVerificationPending.setVisibility(View.VISIBLE);

        } else if ("DONATED".equalsIgnoreCase(journeyStatus) || "COMPLETED".equalsIgnoreCase(journeyStatus)) {
            setStepCompleted(iconStepNotified, tvStepTitleNotified, tvStepTimeNotified, "✓", "Request Received", "Completed");
            setStepCompleted(iconStepAccepted, tvStepTitleAccepted, tvStepTimeAccepted, "✓", "Emergency Accepted", "Completed");
            setStepCompleted(iconStepTravelling, tvStepTitleTravelling, tvStepTimeTravelling, "✓", "Travelling to Hospital", "Completed");
            setStepCompleted(iconStepArrived, tvStepTitleArrived, tvStepTimeArrived, "✓", "Arrived at Hospital", "Completed");
            setStepCompleted(iconStepVerified, tvStepTitleVerified, tvStepTimeVerified, "✓", "Donation Verified", "Officially Certified");

            tvDonorFeedback.setVisibility(View.VISIBLE);
            tvDonorFeedback.setText("Thank you! Your blood donation has been officially verified.");
            tvDonorFeedback.setTextColor(ContextCompat.getColor(this, R.color.status_available));

        } else if ("DECLINED".equalsIgnoreCase(journeyStatus)) {
            tvDonorFeedback.setVisibility(View.VISIBLE);
            tvDonorFeedback.setText("You have declined this request.");
            tvDonorFeedback.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        }
    }

    private void resetStep(TextView icon, TextView title, TextView subtitle, String symbol, String titleText, String subtitleText) {
        icon.setText(symbol);
        icon.setBackgroundResource(R.drawable.badge_busy);
        icon.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        title.setText(titleText);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        subtitle.setText(subtitleText);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
    }

    private void setStepActive(TextView icon, TextView title, TextView subtitle, String symbol, String titleText, String subtitleText) {
        icon.setText(symbol);
        icon.setBackgroundResource(R.drawable.badge_blood_group);
        icon.setTextColor(Color.WHITE);
        title.setText(titleText);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        subtitle.setText(subtitleText);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
    }

    private void setStepCompleted(TextView icon, TextView title, TextView subtitle, String symbol, String titleText, String subtitleText) {
        icon.setText(symbol);
        icon.setBackgroundResource(R.drawable.badge_available);
        icon.setTextColor(ContextCompat.getColor(this, R.color.status_available));
        title.setText(titleText);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        subtitle.setText(subtitleText);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
    }

    private void submitInitialResponse(final String action) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        btnAccept.setEnabled(false);
        btnDecline.setEnabled(false);

        ApiClient.getInstance().respondToEmergency(emergencyId, action, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String message) {
                btnAccept.setEnabled(true);
                btnDecline.setEnabled(true);
                Toast.makeText(EmergencyDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                loadEmergencyDetails(); // Always refresh from authoritative server state
            }

            @Override
            public void onError(String error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                btnAccept.setEnabled(true);
                btnDecline.setEnabled(true);
                Toast.makeText(EmergencyDetailActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void submitJourneyAction(final String action, final boolean launchNavigation) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        btnStartJourney.setEnabled(false);
        btnMarkArrived.setEnabled(false);

        ApiClient.getInstance().updateJourneyStatus(emergencyId, action, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String message) {
                btnStartJourney.setEnabled(true);
                btnMarkArrived.setEnabled(true);
                Toast.makeText(EmergencyDetailActivity.this, message, Toast.LENGTH_SHORT).show();

                if (launchNavigation && currentEmergency != null) {
                    launchNavigationToHospital(
                            currentEmergency.getHospitalLatitude(),
                            currentEmergency.getHospitalLongitude(),
                            currentEmergency.getHospital()
                    );
                }

                loadEmergencyDetails(); // Always refresh from server
            }

            @Override
            public void onError(String error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                btnStartJourney.setEnabled(true);
                btnMarkArrived.setEnabled(true);
                Toast.makeText(EmergencyDetailActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void populateAcceptedDonors(List<ApiClient.AcceptedDonorItem> donors) {
        layoutAcceptedDonorsList.removeAllViews();

        if (donors == null || donors.isEmpty()) {
            tvNoDonorsYet.setVisibility(View.VISIBLE);
            return;
        }

        tvNoDonorsYet.setVisibility(View.GONE);
        for (ApiClient.AcceptedDonorItem d : donors) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.list_layout, layoutAcceptedDonorsList, false);

            TextView tvAvatar = itemView.findViewById(R.id.tv_blood_badge);
            TextView tvName = itemView.findViewById(R.id.name_text);
            TextView tvCity = itemView.findViewById(R.id.tv_donor_city);
            ImageView ivBadge = itemView.findViewById(R.id.iv_donor_verified_badge);

            if (tvAvatar != null) {
                tvAvatar.setText(d.bloodGroup != null ? d.bloodGroup : "🩸");
            }
            if (tvName != null) {
                tvName.setText(d.name != null ? d.name : "Voluntary Donor");
            }

            // Format Journey Status badge
            String statusBadge = "Accepted";
            int badgeColor = ContextCompat.getColor(this, R.color.status_available);
            if ("TRAVELLING".equalsIgnoreCase(d.journeyStatus)) {
                statusBadge = "Travelling to Hospital";
                badgeColor = ContextCompat.getColor(this, R.color.colorPrimary);
            } else if ("ARRIVED".equalsIgnoreCase(d.journeyStatus)) {
                statusBadge = "Arrived at Hospital";
                badgeColor = Color.parseColor("#388E3C");
            } else if ("DONATED".equalsIgnoreCase(d.journeyStatus) || "COMPLETED".equalsIgnoreCase(d.journeyStatus)) {
                statusBadge = "Donated (Verified)";
                badgeColor = Color.parseColor("#1565C0");
            }

            if (tvCity != null) {
                tvCity.setText(statusBadge);
                tvCity.setTextColor(badgeColor);
            }

            if (tvDistance != null) {
                tvDistance.setVisibility(View.GONE); // No raw donor GPS exposed
            }

            if (ivBadge != null) {
                boolean isVerified = "DONOR_VERIFIED".equalsIgnoreCase(d.verificationStatus) || "PHONE_VERIFIED".equalsIgnoreCase(d.verificationStatus);
                ivBadge.setVisibility(isVerified ? View.VISIBLE : View.GONE);
            }

            layoutAcceptedDonorsList.addView(itemView);
        }
    }

    private void launchNavigationToHospital(double lat, double lng, String hospitalName) {
        if (lat == 0.0 && lng == 0.0) {
            Toast.makeText(this, "Hospital GPS coordinates not available for direct navigation", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Intent 1: Google Maps Navigation
            Uri navUri = Uri.parse("google.navigation:q=" + lat + "," + lng);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, navUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
                return;
            }
        } catch (Throwable ignored) {}

        // Intent 2 Fallback: Generic Geo URI
        try {
            String label = Uri.encode(hospitalName != null ? hospitalName : "Hospital");
            Uri fallbackUri = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + label + ")");
            Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, fallbackUri);
            startActivity(fallbackIntent);
        } catch (Throwable t) {
            Toast.makeText(this, "No map navigation app installed on device", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmCancelEmergency() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Emergency Request?")
                .setMessage("Are you sure you want to cancel this emergency request? Responding donors will be notified.")
                .setPositiveButton("Yes, Cancel Request", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ApiClient.getInstance().deleteEmergencyRequest(emergencyId, new ApiClient.ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                Toast.makeText(EmergencyDetailActivity.this, "Emergency request cancelled.", Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(EmergencyDetailActivity.this, "Error cancelling: " + error, Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }
                })
                .setNegativeButton("Keep Active", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
