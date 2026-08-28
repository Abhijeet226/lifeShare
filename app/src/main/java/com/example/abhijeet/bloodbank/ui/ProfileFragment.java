package com.example.abhijeet.bloodbank.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.abhijeet.bloodbank.ApiClient;
import com.example.abhijeet.bloodbank.BloodBankCenter;
import com.example.abhijeet.bloodbank.DataManager;
import com.example.abhijeet.bloodbank.FeedbackActivity;
import com.example.abhijeet.bloodbank.LogInActivity;
import com.example.abhijeet.bloodbank.MainActivity;
import com.example.abhijeet.bloodbank.QrUtils;
import com.example.abhijeet.bloodbank.R;
import com.example.abhijeet.bloodbank.UpdatePassword;
import com.example.abhijeet.bloodbank.UpdateProfile;
import com.example.abhijeet.bloodbank.UserProfile;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class ProfileFragment extends Fragment {

    private TextView tvDonorName, tvBloodGroup, tvDonorId, tvCity, tvStatus, tvMenuProfileSub, tvAvailabilitySub;
    private TextView tvProfileDonationsCount, tvProfileLastDonation, tvProfileEligibilityStatus, tvProfileCooldownSubtext;
    private TextView tvProfileKarmaPoints;
    private View badgeItemFirstDrop, badgeItemRareGuardian, badgeItemSilverSaver, badgeItemGoldHero;
    private MaterialButton btnViewHallOfFame;
    private View btnViewDonationHistory, btnCoordinatorDashboard, btnAdminDashboard;
    private ImageView ivWalletQr;
    private SwitchMaterial switchAvailability;
    private View cardDigitalDonorId, btnShareDonorCard, menuPersonalInfo, menuSecurity, menuThemeSettings, menuNearbyCenters, menuFeedback, btnLogout;
    private TextView tvMenuThemeSub;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvDonorName = view.findViewById(R.id.tv_wallet_donor_name);
        tvBloodGroup = view.findViewById(R.id.tv_wallet_blood_group);
        tvDonorId = view.findViewById(R.id.tv_wallet_donor_id);
        tvCity = view.findViewById(R.id.tv_wallet_city);
        tvStatus = view.findViewById(R.id.tv_wallet_status);
        tvMenuProfileSub = view.findViewById(R.id.tv_menu_profile_sub);
        tvAvailabilitySub = view.findViewById(R.id.tv_availability_subtext);
        ivWalletQr = view.findViewById(R.id.iv_wallet_qr);


        // Phase 3 Views
        tvProfileDonationsCount = view.findViewById(R.id.tv_profile_donations_count);
        tvProfileLastDonation = view.findViewById(R.id.tv_profile_last_donation);
        tvProfileEligibilityStatus = view.findViewById(R.id.tv_profile_eligibility_status);
        tvProfileCooldownSubtext = view.findViewById(R.id.tv_profile_cooldown_subtext);
        btnViewDonationHistory = view.findViewById(R.id.btn_view_donation_history);
        btnCoordinatorDashboard = view.findViewById(R.id.btn_coordinator_dashboard);
        btnAdminDashboard = view.findViewById(R.id.btn_admin_dashboard);

        // Badges & Karma Views
        tvProfileKarmaPoints = view.findViewById(R.id.tv_profile_karma_points);
        badgeItemFirstDrop = view.findViewById(R.id.badge_item_first_drop);
        badgeItemRareGuardian = view.findViewById(R.id.badge_item_rare_guardian);
        badgeItemSilverSaver = view.findViewById(R.id.badge_item_silver_saver);
        badgeItemGoldHero = view.findViewById(R.id.badge_item_gold_hero);
        btnViewHallOfFame = view.findViewById(R.id.btn_view_hall_of_fame);

        if (btnViewHallOfFame != null) {
            btnViewHallOfFame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showHallOfFameBottomSheet();
                }
            });
        }

        if (btnViewDonationHistory != null) {
            btnViewDonationHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getContext(), com.example.abhijeet.bloodbank.DonationHistoryActivity.class));
                }
            });
        }

        if (btnCoordinatorDashboard != null) {
            btnCoordinatorDashboard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getContext(), com.example.abhijeet.bloodbank.CoordinatorVerificationActivity.class));
                }
            });
        }

        if (btnAdminDashboard != null) {
            btnAdminDashboard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getContext(), com.example.abhijeet.bloodbank.AdminDashboardActivity.class));
                }
            });
        }
        switchAvailability = view.findViewById(R.id.switch_frag_availability);
        cardDigitalDonorId = view.findViewById(R.id.card_digital_donor_id);
        btnShareDonorCard = view.findViewById(R.id.btn_share_donor_card);
        menuPersonalInfo = view.findViewById(R.id.menu_personal_info);
        menuSecurity = view.findViewById(R.id.menu_security);
        menuThemeSettings = view.findViewById(R.id.menu_theme_settings);
        tvMenuThemeSub = view.findViewById(R.id.tv_menu_theme_sub);
        menuNearbyCenters = view.findViewById(R.id.menu_nearby_centers);
        menuFeedback = view.findViewById(R.id.menu_feedback);
        btnLogout = view.findViewById(R.id.btn_frag_logout);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_profile);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadProfileData();
                    swipeRefresh.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        }
                    }, 800);
                }
            });
        }

        loadProfileData();

        if (switchAvailability != null) {
            switchAvailability.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (getContext() != null) {
                        DataManager dm = DataManager.getInstance(getContext());
                        UserProfile user = dm.getCurrentUser();
                        user.setAvailable(isChecked);
                        dm.saveCurrentUser(user);
                        if (tvStatus != null) tvStatus.setText(isChecked ? "Active Donor" : "Inactive");
                        if (tvAvailabilitySub != null) tvAvailabilitySub.setText(isChecked ? "Active & discoverable for emergency SOS in Odisha" : "Hidden from donor searches");
                    }
                }
            });
        }

        // 1. Enlarge Pass / QR Dialog on Card Tap
        if (cardDigitalDonorId != null) {
            cardDigitalDonorId.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEnlargedQrDialog();
                }
            });
        }

        // 2. Share Digital Donor Card via Sleek Icon Button
        if (btnShareDonorCard != null) {
            btnShareDonorCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getContext() == null) return;
                    UserProfile u = DataManager.getInstance(getContext()).getCurrentUser();
                    if (u != null) {
                        String shareText = "LifeShare Donor Pass\n" +
                                "Name: " + u.getName() + " (" + u.getBloodGroup() + ")\n" +
                                "Donor ID: " + u.getDonorId() + "\n" +
                                "Status: Active Donor in " + u.getCity() + ", Odisha\n" +
                                "Join the Lifesaving Network: https://lifeshare.odisha.gov.in";

                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "LifeShare Donor Card");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                        startActivity(Intent.createChooser(shareIntent, "Share Donor Card"));
                    }
                }
            });
        }

        // 3. View Donation History Activity
        if (btnViewDonationHistory != null) {
            btnViewDonationHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getContext(), com.example.abhijeet.bloodbank.DonationHistoryActivity.class));
                }
            });
        }


        // 5. Personal Info
        if (menuPersonalInfo != null) {
            menuPersonalInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getContext(), UpdateProfile.class));
                }
            });
        }

        // 6. Security & Privacy Hub
        if (menuSecurity != null) {
            menuSecurity.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getContext(), UpdatePassword.class));
                }
            });
        }

        // 6.5. App Theme & Display
        if (menuThemeSettings != null) {
            menuThemeSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showThemeSelectionDialog();
                }
            });
        }

        // 7. Nearby Centers Directory in Odisha
        if (menuNearbyCenters != null) {
            menuNearbyCenters.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showNearbyCentersDialog();
                }
            });
        }

        // 8. Help & Feedback
        if (menuFeedback != null) {
            menuFeedback.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getContext(), FeedbackActivity.class));
                }
            });
        }

        // 9. Grouped Sign Out
        if (btnLogout != null) {
            btnLogout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() instanceof LogInActivity) {
                        ((LogInActivity) getActivity()).logout();
                    } else if (getContext() != null) {
                        DataManager.getInstance(getContext()).setLoggedIn(false);
                        Intent intent = new Intent(getContext(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        if (getActivity() != null) {
                            getActivity().finish();
                        }
                    }
                }
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileData();
    }

    private void loadProfileData() {
        if (getContext() == null) return;
        DataManager dataManager = DataManager.getInstance(getContext());
        UserProfile user = dataManager.getCurrentUser();

        if (user != null) {
            renderUserUI(user);
        }

        // Fetch fresh server profile (cooldown, last donation date, stats)
        ApiClient.getInstance().getProfile(new ApiClient.ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile freshUser) {
                if (getContext() == null || freshUser == null) return;
                DataManager.getInstance(getContext()).saveCurrentUser(freshUser);
                renderUserUI(freshUser);
            }

            @Override
            public void onError(String errorMessage) {
                // Keep local cache if offline
            }
        });
    }

    private void renderUserUI(UserProfile user) {
        if (user == null || getContext() == null) return;

        if (tvDonorName != null) tvDonorName.setText(user.getName() != null && !user.getName().isEmpty() ? user.getName() : "LifeShare Donor");
        if (tvBloodGroup != null) tvBloodGroup.setText(user.getBloodGroup() != null ? user.getBloodGroup() : "O+");
        if (tvDonorId != null) tvDonorId.setText(user.getDonorId() != null ? user.getDonorId() : "OD-LS-001");
        if (tvCity != null) tvCity.setText((user.getCity() != null ? user.getCity() : "Bhubaneswar") + ", Odisha");

        boolean isUnderCooldown = !user.isEligibleToDonate() || user.getDaysRemaining() > 0;

        if (switchAvailability != null) {
            switchAvailability.setOnCheckedChangeListener(null); // prevent recursive trigger
            if (isUnderCooldown) {
                switchAvailability.setChecked(false);
                switchAvailability.setEnabled(false);
            } else {
                switchAvailability.setEnabled(true);
                switchAvailability.setChecked(user.isAvailable());
            }

            switchAvailability.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (getContext() == null) return;
                    DataManager dm = DataManager.getInstance(getContext());
                    UserProfile u = dm.getCurrentUser();
                    if (!u.isEligibleToDonate() || u.getDaysRemaining() > 0) {
                        switchAvailability.setChecked(false);
                        Toast.makeText(getContext(), "Availability disabled during 90-day cooldown", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    u.setAvailable(isChecked);
                    dm.saveCurrentUser(u);
                    if (tvStatus != null) {
                        tvStatus.setText(isChecked ? "Active" : "Inactive");
                        tvStatus.setTextColor(isChecked ? Color.parseColor("#C8E6C9") : Color.parseColor("#FFCDD2"));
                    }
                    if (tvAvailabilitySub != null) {
                        tvAvailabilitySub.setText(isChecked ? "Active & discoverable for emergency SOS in Odisha" : "Hidden from donor searches");
                    }

                    ApiClient.getInstance().updateAvailability(isChecked, new ApiClient.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {}

                        @Override
                        public void onError(String errorMessage) {}
                    });
                }
            });
        }

        if (tvStatus != null) {
            if (isUnderCooldown) {
                tvStatus.setText("Inactive");
                tvStatus.setTextColor(Color.parseColor("#FFCC80"));
            } else {
                tvStatus.setText(user.isAvailable() ? "Active" : "Inactive");
                tvStatus.setTextColor(user.isAvailable() ? Color.parseColor("#C8E6C9") : Color.parseColor("#FFCDD2"));
            }
        }

        if (tvAvailabilitySub != null) {
            if (isUnderCooldown) {
                tvAvailabilitySub.setText("Unavailable • Under 90-day cooldown (" + user.getDaysRemaining() + " days remaining)");
            } else {
                tvAvailabilitySub.setText(user.isAvailable() ? "Active & discoverable for emergency SOS in Odisha" : "Hidden from donor searches");
            }
        }

        // Phase 3: Donations Record & 90-Day Cooldown Status
        if (tvProfileDonationsCount != null) {
            tvProfileDonationsCount.setText(user.getDonationsCount() + " Completed");
        }
        if (tvProfileLastDonation != null) {
            String lastDate = user.getLastDonationDate();
            if (lastDate != null && !lastDate.isEmpty() && !lastDate.equalsIgnoreCase("null")) {
                if (lastDate.length() >= 10) lastDate = lastDate.substring(0, 10);
                tvProfileLastDonation.setText(lastDate);
            } else {
                tvProfileLastDonation.setText("None recorded");
            }
        }
        if (tvProfileEligibilityStatus != null) {
            if (!isUnderCooldown) {
                tvProfileEligibilityStatus.setText("Eligible to Donate");
                tvProfileEligibilityStatus.setTextColor(Color.parseColor("#2E7D32"));
                if (tvProfileCooldownSubtext != null) {
                    tvProfileCooldownSubtext.setText("Ready to respond to emergency blood requests");
                }
            } else {
                tvProfileEligibilityStatus.setText("In Cooldown (" + user.getDaysRemaining() + " days remaining)");
                tvProfileEligibilityStatus.setTextColor(Color.parseColor("#E65100"));
                if (tvProfileCooldownSubtext != null) {
                    String nextDate = user.getNextEligibleDate();
                    if (nextDate != null && nextDate.length() >= 10) nextDate = nextDate.substring(0, 10);
                    tvProfileCooldownSubtext.setText("Next eligible: " + (nextDate != null && !nextDate.isEmpty() ? nextDate : "After 90 days"));
                }
            }
        }

        if (btnCoordinatorDashboard != null) {
            btnCoordinatorDashboard.setVisibility(user.isCoordinator() ? View.VISIBLE : View.GONE);
        }
        if (btnAdminDashboard != null) {
            btnAdminDashboard.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);
        }

        if (tvMenuProfileSub != null) {
            tvMenuProfileSub.setText(user.getEmail() != null ? user.getEmail() : user.getMobile());
        }

        // Karma & Milestone Badges
        if (tvProfileKarmaPoints != null) {
            tvProfileKarmaPoints.setText(user.getKarmaPoints() + " Karma");
        }

        List<ApiClient.DonorBadge> badges = user.getBadges();
        boolean hasFirstDrop = false, hasRareGuardian = false, hasSilverSaver = false, hasGoldHero = false;
        if (badges != null) {
            for (ApiClient.DonorBadge b : badges) {
                if (b == null || b.badgeId == null) continue;
                if ("BADGE_FIRST_DROP".equalsIgnoreCase(b.badgeId)) hasFirstDrop = true;
                if ("BADGE_RARE_GUARDIAN".equalsIgnoreCase(b.badgeId)) hasRareGuardian = true;
                if ("BADGE_SILVER_SAVER".equalsIgnoreCase(b.badgeId)) hasSilverSaver = true;
                if ("BADGE_GOLD_HERO".equalsIgnoreCase(b.badgeId)) hasGoldHero = true;
            }
        }
        if (user.getDonationsCount() >= 1) hasFirstDrop = true;
        if (user.getDonationsCount() >= 3) hasSilverSaver = true;
        if (user.getDonationsCount() >= 5) hasGoldHero = true;
        if (user.getBloodGroup() != null && user.getBloodGroup().contains("-") && user.getDonationsCount() >= 1) hasRareGuardian = true;

        if (badgeItemFirstDrop != null) badgeItemFirstDrop.setAlpha(hasFirstDrop ? 1.0f : 0.35f);
        if (badgeItemRareGuardian != null) badgeItemRareGuardian.setAlpha(hasRareGuardian ? 1.0f : 0.35f);
        if (badgeItemSilverSaver != null) badgeItemSilverSaver.setAlpha(hasSilverSaver ? 1.0f : 0.35f);
        if (badgeItemGoldHero != null) badgeItemGoldHero.setAlpha(hasGoldHero ? 1.0f : 0.35f);

        // Generate crisp pure white QR with high contrast and explicit USERID tag
        try {
            String qrPayload = "LIFESHARE:DONOR|USERID=" + (user.getId() != null ? user.getId() : "") +
                    "|NAME=" + (user.getName() != null ? user.getName() : "Donor") +
                    "|BG=" + (user.getBloodGroup() != null ? user.getBloodGroup() : "O+") +
                    "|CITY=" + (user.getCity() != null ? user.getCity() : "Odisha") +
                    "|CODE=" + (user.getDonorId() != null ? user.getDonorId() : "OD-01") +
                    "|TEL=" + (user.getMobile() != null ? user.getMobile() : "");

            Bitmap qrBitmap = QrUtils.generateQrCode(qrPayload, 300, 300);
            if (qrBitmap != null && ivWalletQr != null) {
                ivWalletQr.setImageBitmap(qrBitmap);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void showEnlargedQrDialog() {
        if (getContext() == null) return;
        UserProfile user = DataManager.getInstance(getContext()).getCurrentUser();
        if (user == null) user = new UserProfile();

        String donorName = user.getName() != null ? user.getName() : "LifeShare Donor";
        String donorBg = user.getBloodGroup() != null ? user.getBloodGroup() : "O+";
        String donorId = user.getDonorId() != null ? user.getDonorId() : "OD-LS-001";
        String donorCity = user.getCity() != null ? user.getCity() : "Bhubaneswar";
        String donorMobile = user.getMobile() != null ? user.getMobile() : "";

        String qrPayload = "LIFESHARE:ODISHA|ID=" + donorId +
                "|NAME=" + donorName +
                "|BG=" + donorBg +
                "|CITY=" + donorCity +
                "|STATUS=" + (user.isPhoneVerified() ? "VERIFIED" : "UNVERIFIED") +
                "|TEL=" + donorMobile;

        Bitmap bitmap = QrUtils.generateQrCode(qrPayload, 600, 600);

        ImageView iv = new ImageView(getContext());
        iv.setPadding(40, 40, 40, 40);
        if (bitmap != null) {
            iv.setImageBitmap(bitmap);
        }

        new AlertDialog.Builder(getContext())
                .setTitle(donorName + " (" + donorBg + ")")
                .setMessage("Donor Pass: " + donorId + " • " + (user.isPhoneVerified() ? "Verified" : "Unverified"))
                .setView(iv)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showThemeSelectionDialog() {
        if (getContext() == null) return;
        final DataManager dm = DataManager.getInstance(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_theme, null);
        final RadioGroup rg = dialogView.findViewById(R.id.rg_dialog_theme);
        final RadioButton rbSystem = dialogView.findViewById(R.id.rb_theme_system);
        final RadioButton rbLight = dialogView.findViewById(R.id.rb_theme_light);
        final RadioButton rbDark = dialogView.findViewById(R.id.rb_theme_dark);
        final View btnCancel = dialogView.findViewById(R.id.btn_dialog_theme_cancel);
        final View btnApply = dialogView.findViewById(R.id.btn_dialog_theme_apply);

        int currentMode = dm.getThemeMode();
        if (currentMode == DataManager.THEME_LIGHT) {
            rbLight.setChecked(true);
        } else if (currentMode == DataManager.THEME_DARK) {
            rbDark.setChecked(true);
        } else {
            rbSystem.setChecked(true);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedMode = DataManager.THEME_SYSTEM;
                int checkedId = rg.getCheckedRadioButtonId();
                if (checkedId == R.id.rb_theme_light) {
                    selectedMode = DataManager.THEME_LIGHT;
                } else if (checkedId == R.id.rb_theme_dark) {
                    selectedMode = DataManager.THEME_DARK;
                }

                dm.setThemeMode(selectedMode);
                if (tvMenuThemeSub != null) {
                    if (selectedMode == DataManager.THEME_LIGHT) tvMenuThemeSub.setText("Light Mode");
                    else if (selectedMode == DataManager.THEME_DARK) tvMenuThemeSub.setText("Dark Mode");
                    else tvMenuThemeSub.setText("System Default (Follow Android)");
                }
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showNearbyCentersDialog() {
        if (getContext() == null) return;
        final Context ctx = getContext();

        final View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_nearby_centers, null);
        final RecyclerView recyclerView = dialogView.findViewById(R.id.recycler_dialog_centers);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_dialog_close_centers);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (recyclerView != null) {
            recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(ctx));
        }

        dialog.show();

        DataManager.getInstance(ctx).fetchBloodBanks(null, new DataManager.BloodBankCallback() {
            @Override
            public void onBloodBanksLoaded(final List<BloodBankCenter> centers) {
                if (getActivity() == null || getActivity().isFinishing() || recyclerView == null) return;
                recyclerView.setAdapter(new RecyclerView.Adapter<CenterViewHolder>() {
                    @NonNull
                    @Override
                    public CenterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_center, parent, false);
                        return new CenterViewHolder(item);
                    }

                    @Override
                    public void onBindViewHolder(@NonNull CenterViewHolder holder, int position) {
                        final BloodBankCenter center = centers.get(position);
                        holder.tvName.setText(center.getName());
                        holder.tvCityTag.setText(center.getCity());
                        holder.tvType.setText(center.getType());
                        holder.tvTiming.setText(center.getTimings() != null ? center.getTimings() : "24x7 Emergency Service");

                        holder.btnCall.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (center.getPhone() != null && !center.getPhone().isEmpty()) {
                                    Intent intent = new Intent(Intent.ACTION_DIAL);
                                    intent.setData(Uri.parse("tel:" + center.getPhone()));
                                    startActivity(intent);
                                } else {
                                    Toast.makeText(getContext(), "Contact number unavailable", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                    @Override
                    public int getItemCount() {
                        return centers != null ? centers.size() : 0;
                    }
                });
            }
        });
    }

    private void showHallOfFameBottomSheet() {
        if (getContext() == null) return;

        final BottomSheetDialog bottomSheet = new BottomSheetDialog(getContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.layout_bottom_sheet_hall_of_fame, null);
        bottomSheet.setContentView(sheetView);

        final ProgressBar pb = sheetView.findViewById(R.id.pb_hall_of_fame);
        final LinearLayout container = sheetView.findViewById(R.id.container_leaderboard_list);
        View btnClose = sheetView.findViewById(R.id.btn_close_hall_of_fame);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> bottomSheet.dismiss());
        }

        ApiClient.getInstance().getLeaderboard(new ApiClient.ApiCallback<List<ApiClient.LeaderboardItem>>() {
            @Override
            public void onSuccess(List<ApiClient.LeaderboardItem> items) {
                if (getContext() == null) return;
                if (pb != null) pb.setVisibility(View.GONE);
                if (container != null) {
                    container.setVisibility(View.VISIBLE);
                    container.removeAllViews();

                    if (items == null || items.isEmpty()) {
                        TextView emptyTv = new TextView(getContext());
                        emptyTv.setText("No public voluntary records yet. Be the first hero!");
                        emptyTv.setTextColor(Color.parseColor("#9E9E9E"));
                        emptyTv.setPadding(0, 20, 0, 20);
                        container.addView(emptyTv);
                        return;
                    }

                    for (ApiClient.LeaderboardItem item : items) {
                        LinearLayout row = new LinearLayout(getContext());
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setPadding(0, 14, 0, 14);
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        // Rank Circle
                        TextView tvRank = new TextView(getContext());
                        tvRank.setText("#" + item.rank);
                        tvRank.setTextSize(13f);
                        tvRank.setTypeface(null, android.graphics.Typeface.BOLD);
                        tvRank.setTextColor(item.rank <= 3 ? Color.parseColor("#C62828") : Color.parseColor("#757575"));
                        tvRank.setWidth(80);
                        row.addView(tvRank);

                        // Name + City
                        LinearLayout nameCol = new LinearLayout(getContext());
                        nameCol.setOrientation(LinearLayout.VERTICAL);
                        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                        nameCol.setLayoutParams(nameLp);

                        TextView tvName = new TextView(getContext());
                        tvName.setText(item.displayName != null ? item.displayName : "Voluntary Hero");
                        tvName.setTextSize(14f);
                        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                        tvName.setTextColor(Color.parseColor("#212121"));
                        nameCol.addView(tvName);

                        TextView tvSub = new TextView(getContext());
                        tvSub.setText((item.city != null ? item.city : "Odisha") + " • " + item.bloodGroup + " • " + item.donationsCount + " donations");
                        tvSub.setTextSize(11.5f);
                        tvSub.setTextColor(Color.parseColor("#757575"));
                        nameCol.addView(tvSub);

                        row.addView(nameCol);

                        // Karma Badge Pill
                        TextView tvKarma = new TextView(getContext());
                        tvKarma.setText(item.karmaPoints + " Karma");
                        tvKarma.setTextSize(12f);
                        tvKarma.setTypeface(null, android.graphics.Typeface.BOLD);
                        tvKarma.setTextColor(Color.parseColor("#D97706"));
                        row.addView(tvKarma);

                        container.addView(row);

                        View divider = new View(getContext());
                        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                        divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
                        container.addView(divider);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (pb != null) pb.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Failed to load Hall of Fame: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheet.show();
    }

    private static class CenterViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCityTag, tvType, tvTiming;
        View btnCall;

        CenterViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_center_name);
            tvCityTag = itemView.findViewById(R.id.tv_center_city_tag);
            tvType = itemView.findViewById(R.id.tv_center_type);
            tvTiming = itemView.findViewById(R.id.tv_center_timing);
            btnCall = itemView.findViewById(R.id.btn_center_call);
        }
    }
}
