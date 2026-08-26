package com.example.abhijeet.bloodbank.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.abhijeet.bloodbank.ApiClient;
import com.example.abhijeet.bloodbank.DataManager;
import com.example.abhijeet.bloodbank.LogInActivity;
import com.example.abhijeet.bloodbank.R;
import com.example.abhijeet.bloodbank.UserProfile;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

    private TextView tvGreeting, tvUserName, tvUserBg, tvAvailabilityText, tvTrustBadge;
    private TextView tvEligibilityTitle, tvEligibilitySub;
    private View cardHomeSos, cardFindDonors, cardHomeCooldown, btnToggleStatus;
    private View cardOperationalPortal;
    private TextView tvOperationalTitle, tvOperationalSub;
    private MaterialButton btnOpenPortal;
    private MaterialButton btnPostSos;
    private View btnShareSosWhatsapp;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        try {
            tvGreeting = view.findViewById(R.id.tv_home_greeting);
            tvUserName = view.findViewById(R.id.tv_home_user_name);
            tvUserBg = view.findViewById(R.id.tv_home_user_bg);
            tvAvailabilityText = view.findViewById(R.id.tv_home_availability_text);
            tvTrustBadge = view.findViewById(R.id.tv_home_trust_badge);
            tvEligibilityTitle = view.findViewById(R.id.tv_home_eligibility_title);
            tvEligibilitySub = view.findViewById(R.id.tv_home_eligibility_sub);
            cardHomeSos = view.findViewById(R.id.card_home_sos);
            cardFindDonors = view.findViewById(R.id.card_home_find_donors);
            cardHomeCooldown = view.findViewById(R.id.card_home_cooldown);
            btnPostSos = view.findViewById(R.id.btn_home_post_sos);
            btnToggleStatus = view.findViewById(R.id.btn_home_toggle_status);
            btnShareSosWhatsapp = view.findViewById(R.id.btn_share_sos_whatsapp);
            swipeRefresh = view.findViewById(R.id.swipe_refresh_home);

            cardOperationalPortal = view.findViewById(R.id.card_home_operational_portal);
            tvOperationalTitle = view.findViewById(R.id.tv_home_operational_title);
            tvOperationalSub = view.findViewById(R.id.tv_home_operational_sub);
            btnOpenPortal = view.findViewById(R.id.btn_home_open_portal);

            if (swipeRefresh != null) {
                swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
                swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        loadUserData();
                        swipeRefresh.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                            }
                        }, 800);
                    }
                });
            }

            loadUserData();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        View.OnClickListener goToSearch = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() instanceof LogInActivity) {
                    ((LogInActivity) getActivity()).switchToTab(LogInActivity.TAB_SEARCH);
                }
            }
        };

        View.OnClickListener goToEmergency = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() instanceof LogInActivity) {
                    ((LogInActivity) getActivity()).switchToTab(LogInActivity.TAB_EMERGENCY);
                }
            }
        };

        View.OnClickListener goToProfile = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() instanceof LogInActivity) {
                    ((LogInActivity) getActivity()).switchToTab(LogInActivity.TAB_PROFILE);
                }
            }
        };

        if (cardFindDonors != null) cardFindDonors.setOnClickListener(goToSearch);
        if (cardHomeSos != null) cardHomeSos.setOnClickListener(goToEmergency);
        if (cardHomeCooldown != null) cardHomeCooldown.setOnClickListener(goToProfile);
        if (btnPostSos != null) btnPostSos.setOnClickListener(goToEmergency);
        if (btnToggleStatus != null) btnToggleStatus.setOnClickListener(goToProfile);

        if (btnShareSosWhatsapp != null) {
            btnShareSosWhatsapp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String msg = "*URGENT BLOOD APPEAL - LIFE SHARE*\n\n" +
                            "Patient in critical condition needs blood donors immediately in Odisha.\n" +
                            "All Blood Groups Needed\n" +
                            "Download Life Share App or contact immediate voluntary blood donors to save a life.\n" +
                            "Please forward to your WhatsApp groups & status!";
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, msg);
                    try {
                        startActivity(Intent.createChooser(intent, "Share Emergency SOS Appeal"));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Unable to launch share intent", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserData();
    }

    private void loadUserData() {
        if (getContext() == null) return;
        DataManager dataManager = DataManager.getInstance(getContext());
        UserProfile user = dataManager.getCurrentUser();

        // Time-aware greeting
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            String greeting;
            if (hour < 12) {
                greeting = "Good morning";
            } else if (hour < 17) {
                greeting = "Good afternoon";
            } else {
                greeting = "Good evening";
            }
            if (tvGreeting != null) tvGreeting.setText(greeting);
        } catch (Throwable ignored) {}

        if (user != null) {
            if (tvUserName != null && user.getName() != null) {
                tvUserName.setText(user.getName());
            }
            if (tvUserBg != null && user.getBloodGroup() != null) {
                tvUserBg.setText(user.getBloodGroup().isEmpty() ? "O+" : user.getBloodGroup());
            }
            if (tvAvailabilityText != null) {
                if (user.isAvailable()) {
                    tvAvailabilityText.setText("You are listed as an Active Donor");
                } else {
                    tvAvailabilityText.setText("You are currently marked Unavailable");
                }
            }
            if (tvTrustBadge != null) {
                tvTrustBadge.setVisibility(View.VISIBLE);
                if (user.isAvailable()) {
                    tvTrustBadge.setText("Active");
                    tvTrustBadge.setBackgroundResource(R.drawable.badge_available);
                    tvTrustBadge.setTextColor(Color.parseColor("#2E7D32"));
                } else {
                    tvTrustBadge.setText("Inactive");
                    tvTrustBadge.setBackgroundResource(R.drawable.badge_busy);
                    tvTrustBadge.setTextColor(Color.parseColor("#C62828"));
                }
            }

            // Live 90-day cooldown and eligibility state
            if (tvEligibilityTitle != null) {
                if (user.isEligibleToDonate()) {
                    tvEligibilityTitle.setText("Eligible to Donate");
                    tvEligibilityTitle.setTextColor(Color.parseColor("#2E7D32"));
                    if (tvEligibilitySub != null) {
                        tvEligibilitySub.setText("Ready to respond to emergency SOS requests");
                    }
                } else {
                    tvEligibilityTitle.setText("In Cooldown (" + user.getDaysRemaining() + " days remaining)");
                    tvEligibilityTitle.setTextColor(Color.parseColor("#E65100"));
                    if (tvEligibilitySub != null) {
                        String nextDate = user.getNextEligibleDate();
                        if (nextDate != null && nextDate.length() >= 10) nextDate = nextDate.substring(0, 10);
                        tvEligibilitySub.setText("Next eligible: " + (nextDate != null && !nextDate.isEmpty() ? nextDate : "90 days post-donation"));
                    }
                }
            }

            // Operational Authority Portal Banner for Admin & Coordinator
            if (cardOperationalPortal != null) {
                if (user.isAdmin()) {
                    cardOperationalPortal.setVisibility(View.VISIBLE);
                    if (tvOperationalTitle != null) tvOperationalTitle.setText("Admin Operations Portal");
                    if (tvOperationalSub != null) tvOperationalSub.setText("Manage users, hospitals, coordinators & network");
                    if (btnOpenPortal != null) {
                        btnOpenPortal.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startActivity(new Intent(getContext(), com.example.abhijeet.bloodbank.AdminDashboardActivity.class));
                            }
                        });
                    }
                } else if (user.isCoordinator()) {
                    cardOperationalPortal.setVisibility(View.VISIBLE);
                    if (tvOperationalTitle != null) tvOperationalTitle.setText("Hospital Coordinator Portal");
                    if (tvOperationalSub != null) tvOperationalSub.setText("Verify arrived donors & issue donation certificates");
                    if (btnOpenPortal != null) {
                        btnOpenPortal.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startActivity(new Intent(getContext(), com.example.abhijeet.bloodbank.CoordinatorVerificationActivity.class));
                            }
                        });
                    }
                } else {
                    cardOperationalPortal.setVisibility(View.GONE);
                }
            }
        }
    }
}
