package com.example.abhijeet.bloodbank.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.abhijeet.bloodbank.ApiClient;
import com.example.abhijeet.bloodbank.DataManager;
import com.example.abhijeet.bloodbank.LogInActivity;
import com.example.abhijeet.bloodbank.NotificationCenterActivity;
import com.example.abhijeet.bloodbank.R;
import com.example.abhijeet.bloodbank.UserProfile;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import java.util.List;

public class HomeFragment extends Fragment {

    private TextView tvGreeting, tvUserName, tvUserBg, tvAvailabilityText, tvTrustBadge;
    private TextView tvEligibilityTitle, tvEligibilitySub;
    private View cardHomeSos, cardFindDonors, cardHomeCamps, cardHomeCooldown, btnToggleStatus;
    private View cardOperationalPortal;
    private TextView tvOperationalTitle, tvOperationalSub;
    private MaterialButton btnOpenPortal;
    private MaterialButton btnPostSos;
    private View btnShareSosWhatsapp;
    private SwipeRefreshLayout swipeRefresh;
    private android.widget.FrameLayout btnNotificationBell;
    private TextView tvNotifBadge;

    // Interactive Compatibility Matrix Views
    private TextView chipCompatOneg, chipCompatOpos, chipCompatAneg, chipCompatApos;
    private TextView chipCompatBneg, chipCompatBpos, chipCompatAbneg, chipCompatAbpos;
    private TextView tvCompatDonateTo, tvCompatReceiveFrom, tvCompatTraitDesc;
    private String selectedCompatGroup = "O-";

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
            cardHomeCamps = view.findViewById(R.id.card_home_camps);
            cardHomeCooldown = view.findViewById(R.id.card_home_cooldown);
            btnPostSos = view.findViewById(R.id.btn_home_post_sos);
            btnToggleStatus = view.findViewById(R.id.btn_home_toggle_status);
            btnShareSosWhatsapp = view.findViewById(R.id.btn_share_sos_whatsapp);
            swipeRefresh = view.findViewById(R.id.swipe_refresh_home);

            cardOperationalPortal = view.findViewById(R.id.card_home_operational_portal);
            tvOperationalTitle = view.findViewById(R.id.tv_home_operational_title);
            tvOperationalSub = view.findViewById(R.id.tv_home_operational_sub);
            btnOpenPortal = view.findViewById(R.id.btn_home_open_portal);

            btnNotificationBell = view.findViewById(R.id.btn_home_notification_bell);
            tvNotifBadge = view.findViewById(R.id.tv_home_notif_badge);

            if (btnNotificationBell != null) {
                btnNotificationBell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getContext(), NotificationCenterActivity.class);
                        startActivity(intent);
                    }
                });
            }

            initCompatibilityMatrix(view);

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
        if (cardHomeCamps != null) {
            cardHomeCamps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBloodCampsDialog();
                }
            });
        }
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
        refreshUnreadNotificationCount();
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

            if (user.getBloodGroup() != null && !user.getBloodGroup().isEmpty()) {
                selectCompatibilityGroup(user.getBloodGroup());
            }
        }
    }

    private void initCompatibilityMatrix(View view) {
        chipCompatOneg = view.findViewById(R.id.chip_compat_oneg);
        chipCompatOpos = view.findViewById(R.id.chip_compat_opos);
        chipCompatAneg = view.findViewById(R.id.chip_compat_aneg);
        chipCompatApos = view.findViewById(R.id.chip_compat_apos);
        chipCompatBneg = view.findViewById(R.id.chip_compat_bneg);
        chipCompatBpos = view.findViewById(R.id.chip_compat_bpos);
        chipCompatAbneg = view.findViewById(R.id.chip_compat_abneg);
        chipCompatAbpos = view.findViewById(R.id.chip_compat_abpos);

        tvCompatDonateTo = view.findViewById(R.id.tv_compat_donate_to);
        tvCompatReceiveFrom = view.findViewById(R.id.tv_compat_receive_from);
        tvCompatTraitDesc = view.findViewById(R.id.tv_compat_trait_desc);

        View.OnClickListener clickListener = v -> {
            int id = v.getId();
            if (id == R.id.chip_compat_oneg) selectCompatibilityGroup("O-");
            else if (id == R.id.chip_compat_opos) selectCompatibilityGroup("O+");
            else if (id == R.id.chip_compat_aneg) selectCompatibilityGroup("A-");
            else if (id == R.id.chip_compat_apos) selectCompatibilityGroup("A+");
            else if (id == R.id.chip_compat_bneg) selectCompatibilityGroup("B-");
            else if (id == R.id.chip_compat_bpos) selectCompatibilityGroup("B+");
            else if (id == R.id.chip_compat_abneg) selectCompatibilityGroup("AB-");
            else if (id == R.id.chip_compat_abpos) selectCompatibilityGroup("AB+");
        };

        if (chipCompatOneg != null) chipCompatOneg.setOnClickListener(clickListener);
        if (chipCompatOpos != null) chipCompatOpos.setOnClickListener(clickListener);
        if (chipCompatAneg != null) chipCompatAneg.setOnClickListener(clickListener);
        if (chipCompatApos != null) chipCompatApos.setOnClickListener(clickListener);
        if (chipCompatBneg != null) chipCompatBneg.setOnClickListener(clickListener);
        if (chipCompatBpos != null) chipCompatBpos.setOnClickListener(clickListener);
        if (chipCompatAbneg != null) chipCompatAbneg.setOnClickListener(clickListener);
        if (chipCompatAbpos != null) chipCompatAbpos.setOnClickListener(clickListener);

        selectCompatibilityGroup("O-");
    }

    private void selectCompatibilityGroup(String group) {
        if (group == null) group = "O-";
        selectedCompatGroup = group.toUpperCase();

        resetChip(chipCompatOneg, "O-".equals(selectedCompatGroup));
        resetChip(chipCompatOpos, "O+".equals(selectedCompatGroup));
        resetChip(chipCompatAneg, "A-".equals(selectedCompatGroup));
        resetChip(chipCompatApos, "A+".equals(selectedCompatGroup));
        resetChip(chipCompatBneg, "B-".equals(selectedCompatGroup));
        resetChip(chipCompatBpos, "B+".equals(selectedCompatGroup));
        resetChip(chipCompatAbneg, "AB-".equals(selectedCompatGroup));
        resetChip(chipCompatAbpos, "AB+".equals(selectedCompatGroup));

        if (tvCompatDonateTo == null || tvCompatReceiveFrom == null || tvCompatTraitDesc == null) return;

        switch (selectedCompatGroup) {
            case "O-":
                tvCompatDonateTo.setText("All Blood Groups (Universal Red Cell Donor)");
                tvCompatReceiveFrom.setText("O- only");
                tvCompatTraitDesc.setText("Universal Red Blood Cell Donor: Essential for emergency trauma when patient blood group is unverified.");
                break;
            case "O+":
                tvCompatDonateTo.setText("O+, A+, B+, AB+");
                tvCompatReceiveFrom.setText("O+, O-");
                tvCompatTraitDesc.setText("Most In-Demand Group: Transfused in over 38% of hospital emergency cases.");
                break;
            case "A-":
                tvCompatDonateTo.setText("A-, A+, AB-, AB+");
                tvCompatReceiveFrom.setText("A-, O-");
                tvCompatTraitDesc.setText("Rare Negative Group: Vital for oncology, obstetrics and pediatric surgeries.");
                break;
            case "A+":
                tvCompatDonateTo.setText("A+, AB+");
                tvCompatReceiveFrom.setText("A+, A-, O+, O-");
                tvCompatTraitDesc.setText("High-Frequency Lifesaver: Constant demand in planned surgeries and ongoing platelet requirements.");
                break;
            case "B-":
                tvCompatDonateTo.setText("B-, B+, AB-, AB+");
                tvCompatReceiveFrom.setText("B-, O-");
                tvCompatTraitDesc.setText("Rare Guardian Group: Crucial backup for complex emergency interventions in Odisha.");
                break;
            case "B+":
                tvCompatDonateTo.setText("B+, AB+");
                tvCompatReceiveFrom.setText("B+, B-, O+, O-");
                tvCompatTraitDesc.setText("Extremely Active Group: One of the most prevalent and requested groups in India.");
                break;
            case "AB-":
                tvCompatDonateTo.setText("AB-, AB+");
                tvCompatReceiveFrom.setText("AB-, A-, B-, O-");
                tvCompatTraitDesc.setText("Rarest Blood Group (<1% of population): Specialized lifesaver for matching patients.");
                break;
            case "AB+":
                tvCompatDonateTo.setText("AB+ only");
                tvCompatReceiveFrom.setText("All Blood Groups (Universal Red Cell Recipient)");
                tvCompatTraitDesc.setText("Universal Recipient & Universal Plasma Donor: Your plasma is universally compatible with all patients!");
                break;
            default:
                tvCompatDonateTo.setText("Matching compatible groups");
                tvCompatReceiveFrom.setText("Matching compatible groups");
                tvCompatTraitDesc.setText("Consult transfusion medicine guidelines before clinical transfusion.");
                break;
        }
    }

    private void resetChip(TextView chip, boolean isSelected) {
        if (chip == null || getContext() == null) return;
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_pill_selected);
            chip.setTextColor(Color.WHITE);
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_pill_unselected);
            chip.setTextColor(Color.parseColor("#757575"));
        }
    }

    private void showBloodCampsDialog() {
        if (getContext() == null) return;
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_blood_camps_hub, null);
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        FrameLayout btnClose = dialogView.findViewById(R.id.btn_camps_hub_close);
        MaterialButton btnDone = dialogView.findViewById(R.id.btn_camps_hub_done);
        final ProgressBar pb = dialogView.findViewById(R.id.pb_camps_loading);
        final LinearLayout layoutEmpty = dialogView.findViewById(R.id.layout_camps_empty);
        final TextView tvEmptyMsg = dialogView.findViewById(R.id.tv_camps_empty_msg);
        final LinearLayout container = dialogView.findViewById(R.id.container_blood_camps);

        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        UserProfile user = DataManager.getInstance(getContext()).getCurrentUser();
        String cityId = user != null ? user.getCityId() : null;

        ApiClient apiClient = ApiClient.getInstance();
        apiClient.getBloodCamps(cityId, "UPCOMING", new ApiClient.ApiCallback<List<ApiClient.BloodCampItem>>() {
            @Override
            public void onSuccess(final List<ApiClient.BloodCampItem> camps) {
                if (pb != null) pb.setVisibility(View.GONE);
                if (camps == null || camps.isEmpty()) {
                    if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                    return;
                }

                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                if (container != null && getContext() != null) {
                    container.removeAllViews();
                    LayoutInflater inflater = LayoutInflater.from(getContext());

                    for (final ApiClient.BloodCampItem camp : camps) {
                        View card = inflater.inflate(R.layout.item_blood_camp, container, false);
                        TextView tvOrg = card.findViewById(R.id.tv_camp_organizer);
                        TextView tvTarget = card.findViewById(R.id.tv_camp_target_units);
                        TextView tvTitle = card.findViewById(R.id.tv_camp_title);
                        TextView tvDate = card.findViewById(R.id.tv_camp_datetime);
                        TextView tvVenue = card.findViewById(R.id.tv_camp_venue);
                        final TextView tvRsvpCount = card.findViewById(R.id.tv_camp_rsvp_count);
                        MaterialButton btnNav = card.findViewById(R.id.btn_camp_navigate);
                        final MaterialButton btnRsvp = card.findViewById(R.id.btn_camp_rsvp);

                        if (tvOrg != null) tvOrg.setText(camp.organizerName != null ? camp.organizerName : "Hospital Drive");
                        if (tvTarget != null) tvTarget.setText("Goal: " + camp.targetUnits + " Units");
                        if (tvTitle != null) tvTitle.setText(camp.title != null ? camp.title : "Community Blood Drive");
                        if (tvVenue != null) tvVenue.setText(camp.venueAddress != null ? camp.venueAddress : "Bhubaneswar");

                        String dateStr = camp.startDate != null ? camp.startDate.replace("T", " • ").replace(".000Z", "") : "Upcoming Weekend";
                        if (tvDate != null) tvDate.setText(dateStr);

                        if (tvRsvpCount != null) {
                            tvRsvpCount.setText(camp.rsvpCount + (camp.rsvpCount == 1 ? " Donor Attending" : " Donors Attending"));
                        }

                        if (btnRsvp != null) {
                            if (camp.isUserRsvped) {
                                btnRsvp.setText("Attending ✓");
                                btnRsvp.setBackgroundColor(getResources().getColor(R.color.hospital_teal));
                            } else {
                                btnRsvp.setText("I'm Attending");
                                btnRsvp.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                            }

                            btnRsvp.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    ApiClient.getInstance().toggleCampRsvp(camp.id, new ApiClient.ApiCallback<JsonObject>() {
                                        @Override
                                        public void onSuccess(JsonObject result) {
                                            boolean rsvped = result.has("isUserRsvped") && result.get("isUserRsvped").getAsBoolean();
                                            int count = result.has("rsvpCount") ? result.get("rsvpCount").getAsInt() : 0;
                                            camp.isUserRsvped = rsvped;
                                            camp.rsvpCount = count;

                                            if (rsvped) {
                                                btnRsvp.setText("Attending ✓");
                                                btnRsvp.setBackgroundColor(getResources().getColor(R.color.hospital_teal));
                                                Toast.makeText(getContext(), "RSVP confirmed! We look forward to seeing you.", Toast.LENGTH_SHORT).show();
                                            } else {
                                                btnRsvp.setText("I'm Attending");
                                                btnRsvp.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                                                Toast.makeText(getContext(), "RSVP cancelled", Toast.LENGTH_SHORT).show();
                                            }

                                            if (tvRsvpCount != null) {
                                                tvRsvpCount.setText(count + (count == 1 ? " Donor Attending" : " Donors Attending"));
                                            }
                                        }

                                        @Override
                                        public void onError(String errorMessage) {
                                            Toast.makeText(getContext(), "RSVP failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            });
                        }

                        if (btnNav != null) {
                            btnNav.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    String uriStr = "geo:" + camp.latitude + "," + camp.longitude + "?q=" + Uri.encode(camp.venueAddress != null ? camp.venueAddress : camp.title);
                                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriStr));
                                    mapIntent.setPackage("com.google.android.apps.maps");
                                    try {
                                        startActivity(mapIntent);
                                    } catch (Exception e) {
                                        Intent genericIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriStr));
                                        try {
                                            startActivity(genericIntent);
                                        } catch (Exception ex) {
                                            Toast.makeText(getContext(), "Unable to open navigation map", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                            });
                        }

                        container.addView(card);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (pb != null) pb.setVisibility(View.GONE);
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                if (tvEmptyMsg != null) tvEmptyMsg.setText("Failed to load camps: " + errorMessage);
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void refreshUnreadNotificationCount() {
        ApiClient.getInstance().getNotifications("ALL", 1, new ApiClient.ApiCallback<ApiClient.NotificationListResponse>() {
            @Override
            public void onSuccess(ApiClient.NotificationListResponse response) {
                if (!isAdded() || getContext() == null) return;
                if (tvNotifBadge != null) {
                    if (response != null && response.unreadCount > 0) {
                        tvNotifBadge.setText(response.unreadCount > 99 ? "99+" : String.valueOf(response.unreadCount));
                        tvNotifBadge.setVisibility(View.VISIBLE);
                    } else {
                        tvNotifBadge.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {}
        });
    }
}
