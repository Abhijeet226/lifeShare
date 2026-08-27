package com.example.abhijeet.bloodbank;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final int RC_CAMERA_PERMISSION = 2001;

    private SwipeRefreshLayout swipeRefresh;
    private NestedScrollView scrollContent;
    private TextView tvStatUsers, tvStatDonors, tvStatHospitals, tvStatDonations;
    private TextView tvSectionTitle;
    private MaterialButton btnAddHospital, btnSwitchToDonor;
    private FrameLayout btnAdminProfile, btnAdminScanQr;
    private FrameLayout layoutSearchBar;
    private EditText etSearch;
    private LinearLayout containerItems, layoutEmpty;
    private View layoutAdminSkeleton;
    private TextView tvEmptyMessage;

    // Floating Bottom Bar Views
    private View cardFloatingBottomBar;
    private LinearLayout tabNavUsers, tabNavHospitals, tabNavEmergencies, tabNavAudit;
    private FrameLayout pillNavUsers, pillNavHospitals, pillNavEmergencies, pillNavAudit;
    private ImageView ivNavUsers, ivNavHospitals, ivNavEmergencies, ivNavAudit;
    private TextView tvNavUsers, tvNavHospitals, tvNavEmergencies, tvNavAudit;

    private ApiClient apiClient;
    private int savedScrollPosition = 0;

    private enum AdminTab { USERS, HOSPITALS, EMERGENCIES, AUDIT }
    private AdminTab currentTab = AdminTab.USERS;

    private List<ApiClient.AdminUserItem> loadedUsers = new ArrayList<>();
    private List<ApiClient.AdminHospitalItem> loadedHospitals = new ArrayList<>();
    private List<ApiClient.AdminEmergencyItem> loadedEmergencies = new ArrayList<>();
    private List<ApiClient.AdminAuditLogItem> loadedAuditLogs = new ArrayList<>();

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
        setContentView(R.layout.activity_admin_dashboard);

        apiClient = ApiClient.getInstance();
        apiClient.initFromPrefs(this);

        initViews();
        setupListeners();
        setupScrollAutoHide();
        loadAllData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scrollIdleHandler.removeCallbacks(revealBottomBarRunnable);
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipe_refresh_admin);
        scrollContent = findViewById(R.id.scroll_admin_content);

        tvStatUsers = findViewById(R.id.tv_stat_users);
        tvStatDonors = findViewById(R.id.tv_stat_donors);
        tvStatHospitals = findViewById(R.id.tv_stat_hospitals);
        tvStatDonations = findViewById(R.id.tv_stat_donations);

        tvSectionTitle = findViewById(R.id.tv_admin_current_section_title);
        btnAddHospital = findViewById(R.id.btn_admin_add_hospital);
        btnSwitchToDonor = findViewById(R.id.btn_switch_to_donor);
        btnAdminProfile = findViewById(R.id.btn_admin_profile);
        btnAdminScanQr = findViewById(R.id.btn_admin_scan_qr);

        layoutSearchBar = findViewById(R.id.layout_admin_search_bar);
        etSearch = findViewById(R.id.et_admin_search);

        layoutAdminSkeleton = findViewById(R.id.layout_admin_skeleton);
        containerItems = findViewById(R.id.container_admin_items);
        layoutEmpty = findViewById(R.id.layout_admin_empty);
        tvEmptyMessage = findViewById(R.id.tv_admin_empty_message);

        // Floating Bottom Bar
        cardFloatingBottomBar = findViewById(R.id.card_admin_floating_bottom_bar);
        tabNavUsers = findViewById(R.id.tab_nav_users);
        tabNavHospitals = findViewById(R.id.tab_nav_hospitals);
        tabNavEmergencies = findViewById(R.id.tab_nav_emergencies);
        tabNavAudit = findViewById(R.id.tab_nav_audit);

        pillNavUsers = findViewById(R.id.pill_nav_users);
        pillNavHospitals = findViewById(R.id.pill_nav_hospitals);
        pillNavEmergencies = findViewById(R.id.pill_nav_emergencies);
        pillNavAudit = findViewById(R.id.pill_nav_audit);

        ivNavUsers = findViewById(R.id.iv_nav_users);
        ivNavHospitals = findViewById(R.id.iv_nav_hospitals);
        ivNavEmergencies = findViewById(R.id.iv_nav_emergencies);
        ivNavAudit = findViewById(R.id.iv_nav_audit);

        tvNavUsers = findViewById(R.id.tv_nav_users);
        tvNavHospitals = findViewById(R.id.tv_nav_hospitals);
        tvNavEmergencies = findViewById(R.id.tv_nav_emergencies);
        tvNavAudit = findViewById(R.id.tv_nav_audit);
    }

    private void setupListeners() {
        if (btnSwitchToDonor != null) {
            btnSwitchToDonor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminDashboardActivity.this, LogInActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (btnAdminScanQr != null) {
            btnAdminScanQr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startAdminQrScanner();
                }
            });
        }

        if (btnAdminProfile != null) {
            btnAdminProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminDashboardActivity.this, ProfileActivity.class);
                    startActivity(intent);
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

        if (btnAddHospital != null) {
            btnAddHospital.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRegisterHospitalDialog();
                }
            });
        }

        // Floating Bottom Bar Tab Selection
        tabNavUsers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(AdminTab.USERS);
            }
        });

        tabNavHospitals.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(AdminTab.HOSPITALS);
            }
        });

        tabNavEmergencies.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(AdminTab.EMERGENCIES);
            }
        });

        tabNavAudit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(AdminTab.AUDIT);
            }
        });

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (currentTab == AdminTab.USERS) {
                        loadUsers(s.toString());
                    } else if (currentTab == AdminTab.HOSPITALS) {
                        filterHospitals(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
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
                    // Scrolling down -> Hide floating bottom bar
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

    private void showAdminAccountDialog() {
        UserProfile user = DataManager.getInstance(this).getCurrentUser();
        String name = user != null && user.getName() != null ? user.getName() : "Super Administrator";
        String email = user != null && user.getEmail() != null ? user.getEmail() : "admin@lifeshare.in";
        String mobile = user != null && user.getMobile() != null ? user.getMobile() : "+91 9999999999";

        final String[] options = {
                "Refresh Operations Data",
                "Switch to Donor App",
                "Log Out"
        };

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage(email + " • " + mobile + "\nRole: SUPER ADMIN\nStatus: ACTIVE")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            loadAllData();
                            Toast.makeText(AdminDashboardActivity.this, "Data refreshed", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            startActivity(new Intent(AdminDashboardActivity.this, LogInActivity.class));
                        } else if (which == 2) {
                            performLogout();
                        }
                    }
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private void performLogout() {
        DataManager.getInstance(this).setLoggedIn(false);
        DataManager.getInstance(this).saveAuthToken("");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void switchTab(AdminTab tab) {
        currentTab = tab;

        // Reset bottom pill backgrounds
        pillNavUsers.setBackgroundResource(0);
        pillNavHospitals.setBackgroundResource(0);
        pillNavEmergencies.setBackgroundResource(0);
        pillNavAudit.setBackgroundResource(0);

        int colorPrimary = getResources().getColor(R.color.colorPrimary);
        int colorInactive = getResources().getColor(R.color.text_secondary);

        ivNavUsers.setImageTintList(ColorStateList.valueOf(colorInactive));
        ivNavHospitals.setImageTintList(ColorStateList.valueOf(colorInactive));
        ivNavEmergencies.setImageTintList(ColorStateList.valueOf(colorInactive));
        ivNavAudit.setImageTintList(ColorStateList.valueOf(colorInactive));

        tvNavUsers.setTextColor(colorInactive);
        tvNavHospitals.setTextColor(colorInactive);
        tvNavEmergencies.setTextColor(colorInactive);
        tvNavAudit.setTextColor(colorInactive);

        if (tab == AdminTab.USERS) {
            pillNavUsers.setBackgroundResource(R.drawable.bg_tab_indicator_active);
            ivNavUsers.setImageTintList(ColorStateList.valueOf(colorPrimary));
            tvNavUsers.setTextColor(colorPrimary);

            if (tvSectionTitle != null) tvSectionTitle.setText("User Management");
            if (btnAddHospital != null) btnAddHospital.setVisibility(View.GONE);
            if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.VISIBLE);
            etSearch.setHint("Search users by name, email, or phone...");
            loadUsers(etSearch.getText().toString());
        } else if (tab == AdminTab.HOSPITALS) {
            pillNavHospitals.setBackgroundResource(R.drawable.bg_tab_indicator_active);
            ivNavHospitals.setImageTintList(ColorStateList.valueOf(colorPrimary));
            tvNavHospitals.setTextColor(colorPrimary);

            if (tvSectionTitle != null) tvSectionTitle.setText("Hospital Network");
            if (btnAddHospital != null) btnAddHospital.setVisibility(View.VISIBLE);
            if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.VISIBLE);
            etSearch.setHint("Search hospitals by name...");
            loadHospitals();
        } else if (tab == AdminTab.EMERGENCIES) {
            pillNavEmergencies.setBackgroundResource(R.drawable.bg_tab_indicator_active);
            ivNavEmergencies.setImageTintList(ColorStateList.valueOf(colorPrimary));
            tvNavEmergencies.setTextColor(colorPrimary);

            if (tvSectionTitle != null) tvSectionTitle.setText("Live Emergency SOS Oversight");
            if (btnAddHospital != null) btnAddHospital.setVisibility(View.GONE);
            if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.GONE);
            loadEmergencies();
        } else if (tab == AdminTab.AUDIT) {
            pillNavAudit.setBackgroundResource(R.drawable.bg_tab_indicator_active);
            ivNavAudit.setImageTintList(ColorStateList.valueOf(colorPrimary));
            tvNavAudit.setTextColor(colorPrimary);

            if (tvSectionTitle != null) tvSectionTitle.setText("Security & Audit Stream");
            if (btnAddHospital != null) btnAddHospital.setVisibility(View.GONE);
            if (layoutSearchBar != null) layoutSearchBar.setVisibility(View.GONE);
            loadAuditLogs();
        }
    }

    private void loadAllData() {
        loadStats();
        switchTab(currentTab);
    }

    private void loadStats() {
        apiClient.getAdminStats(new ApiClient.ApiCallback<ApiClient.AdminStats>() {
            @Override
            public void onSuccess(ApiClient.AdminStats stats) {
                if (tvStatUsers != null) tvStatUsers.setText(String.valueOf(stats.totalUsers));
                if (tvStatDonors != null) tvStatDonors.setText(String.valueOf(stats.activeDonors));
                if (tvStatHospitals != null) tvStatHospitals.setText(String.valueOf(stats.hospitals));
                if (tvStatDonations != null) tvStatDonations.setText(String.valueOf(stats.verifiedDonations));
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AdminDashboardActivity.this, "Failed to load stats: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =========================================================================
    // TAB 1: USERS
    // =========================================================================

    private void loadUsers(String search) {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.VISIBLE);
        if (containerItems != null) containerItems.setVisibility(View.GONE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getAdminUsers(search, null, null, 1, 50, new ApiClient.ApiCallback<List<ApiClient.AdminUserItem>>() {
            @Override
            public void onSuccess(List<ApiClient.AdminUserItem> users) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                loadedUsers = users != null ? users : new ArrayList<ApiClient.AdminUserItem>();
                if (currentTab == AdminTab.USERS) renderUsers(loadedUsers);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                Toast.makeText(AdminDashboardActivity.this, "Error loading users: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderUsers(List<ApiClient.AdminUserItem> users) {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
        if (containerItems == null) return;
        containerItems.removeAllViews();
        containerItems.setVisibility(View.VISIBLE);

        if (users == null || users.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setText("No users found");
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final ApiClient.AdminUserItem user : users) {
            View view = inflater.inflate(R.layout.item_admin_user, containerItems, false);

            TextView tvBlood = view.findViewById(R.id.tv_admin_user_blood);
            TextView tvName = view.findViewById(R.id.tv_admin_user_name);
            TextView tvEmail = view.findViewById(R.id.tv_admin_user_email);
            TextView tvRoleBadge = view.findViewById(R.id.tv_admin_user_role_badge);
            TextView tvStatusBadge = view.findViewById(R.id.tv_admin_user_status_badge);
            MaterialButton btnManageStatus = view.findViewById(R.id.btn_admin_manage_status);
            MaterialButton btnManageRole = view.findViewById(R.id.btn_admin_manage_role);

            if (tvBlood != null) tvBlood.setText(user.bloodGroup != null ? user.bloodGroup : "O+");
            if (tvName != null) tvName.setText(user.name != null ? user.name : "User");
            if (tvEmail != null) tvEmail.setText((user.email != null ? user.email : "") + " • " + (user.mobile != null ? user.mobile : ""));
            if (tvRoleBadge != null) tvRoleBadge.setText(user.role != null ? user.role : "DONOR");
            if (tvStatusBadge != null) {
                tvStatusBadge.setText(user.accountStatus != null ? user.accountStatus : "ACTIVE");
                if ("SUSPENDED".equalsIgnoreCase(user.accountStatus)) {
                    tvStatusBadge.setTextColor(getResources().getColor(R.color.cooldown_orange));
                } else if ("BLOCKED".equalsIgnoreCase(user.accountStatus)) {
                    tvStatusBadge.setTextColor(getResources().getColor(R.color.status_busy));
                } else {
                    tvStatusBadge.setTextColor(getResources().getColor(R.color.status_available));
                }
            }

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showUserActionSheet(user);
                }
            });

            if (btnManageStatus != null) {
                btnManageStatus.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showUserActionSheet(user);
                    }
                });
            }

            if (btnManageRole != null) {
                btnManageRole.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showChangeRoleDialog(user);
                    }
                });
            }

            containerItems.addView(view);
        }
        restoreSavedScrollPosition();
    }

    private void showUserActionSheet(final ApiClient.AdminUserItem user) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_actions, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView tvBlood = dialogView.findViewById(R.id.tv_dialog_user_blood);
        TextView tvName = dialogView.findViewById(R.id.tv_dialog_user_name);
        TextView tvContact = dialogView.findViewById(R.id.tv_dialog_user_contact);
        TextView tvRoleBadge = dialogView.findViewById(R.id.tv_dialog_user_role_badge);

        MaterialButton btnActive = dialogView.findViewById(R.id.btn_action_activate);
        MaterialButton btnSuspend = dialogView.findViewById(R.id.btn_action_suspend);
        MaterialButton btnBlock = dialogView.findViewById(R.id.btn_action_block);
        MaterialButton btnPromoteCoord = dialogView.findViewById(R.id.btn_action_promote_coordinator);
        MaterialButton btnViewHistory = dialogView.findViewById(R.id.btn_action_view_history);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_user_action_cancel);

        if (tvBlood != null) tvBlood.setText(user.bloodGroup != null ? user.bloodGroup : "O+");
        if (tvName != null) tvName.setText(user.name != null ? user.name : "User");
        if (tvContact != null) tvContact.setText((user.email != null ? user.email : "") + " • " + (user.mobile != null ? user.mobile : ""));
        if (tvRoleBadge != null) tvRoleBadge.setText(user.role != null ? user.role : "DONOR");

        boolean isCoordinator = "COORDINATOR".equalsIgnoreCase(user.role);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.role);

        if (btnPromoteCoord != null) {
            if (isCoordinator) {
                btnPromoteCoord.setText("Change Role / Hospital Assignment");
            } else if (isAdmin) {
                btnPromoteCoord.setText("Change System Role");
            } else {
                btnPromoteCoord.setText("Assign to Hospital as Coordinator");
            }
        }

        if (btnViewHistory != null) {
            if (isCoordinator) {
                btnViewHistory.setText("View Coordinator Verification History");
            } else if (isAdmin) {
                btnViewHistory.setText("View Operational Audit Trail");
            } else {
                btnViewHistory.setText("View Lifetime Blood Donations");
            }
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnActive != null) {
            btnActive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Reactivate Account",
                            "Account Status",
                            "Reactivate account access and restore privileges for " + user.name + " (" + user.email + ")?",
                            "Activate",
                            false,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserStatus(user.id, "ACTIVE");
                                }
                            }
                    );
                }
            });
        }

        if (btnSuspend != null) {
            btnSuspend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Suspend Account",
                            "Security Restriction",
                            "Suspend " + user.name + " (" + user.email + ")? The user will be temporarily restricted from broadcasting SOS requests.",
                            "Suspend User",
                            true,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserStatus(user.id, "SUSPENDED");
                                }
                            }
                    );
                }
            });
        }

        if (btnBlock != null) {
            btnBlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Block Account",
                            "Permanent Security Action",
                            "Permanently block " + user.name + " (" + user.email + ") from accessing LifeShare operations?",
                            "Block User",
                            true,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserStatus(user.id, "BLOCKED");
                                }
                            }
                    );
                }
            });
        }

        if (btnPromoteCoord != null) {
            btnPromoteCoord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showChangeRoleDialog(user);
                }
            });
        }

        if (btnViewHistory != null) {
            btnViewHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if ("COORDINATOR".equalsIgnoreCase(user.role)) {
                        showCoordinatorVerificationsDialog(user.id, user.name);
                    } else {
                        showCoordinatorVerificationsDialog(user.id, user.name);
                    }
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void showChangeStatusDialog(final ApiClient.AdminUserItem user) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_status, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView tvUser = dialogView.findViewById(R.id.tv_dialog_status_user);
        View cardActive = dialogView.findViewById(R.id.card_status_opt_active);
        View cardSuspended = dialogView.findViewById(R.id.card_status_opt_suspended);
        View cardBlocked = dialogView.findViewById(R.id.card_status_opt_blocked);
        ImageView ivCheckActive = dialogView.findViewById(R.id.iv_status_check_active);
        ImageView ivCheckSuspended = dialogView.findViewById(R.id.iv_status_check_suspended);
        ImageView ivCheckBlocked = dialogView.findViewById(R.id.iv_status_check_blocked);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_status_cancel);

        if (tvUser != null) tvUser.setText("Updating status for " + user.name + " (" + user.email + ")");

        String cur = user.accountStatus != null ? user.accountStatus.toUpperCase() : "ACTIVE";
        if ("SUSPENDED".equals(cur) && ivCheckSuspended != null) ivCheckSuspended.setVisibility(View.VISIBLE);
        else if ("BLOCKED".equals(cur) && ivCheckBlocked != null) ivCheckBlocked.setVisibility(View.VISIBLE);
        else if (ivCheckActive != null) ivCheckActive.setVisibility(View.VISIBLE);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (cardActive != null) {
            cardActive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Reactivate Account",
                            "Account Status",
                            "Reactivate account access and restore privileges for " + user.name + "?",
                            "Activate",
                            false,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserStatus(user.id, "ACTIVE");
                                }
                            }
                    );
                }
            });
        }

        if (cardSuspended != null) {
            cardSuspended.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Suspend Account",
                            "Security Restriction",
                            "Suspend " + user.name + "? The user will be temporarily restricted from broadcasting SOS requests.",
                            "Suspend User",
                            true,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserStatus(user.id, "SUSPENDED");
                                }
                            }
                    );
                }
            });
        }

        if (cardBlocked != null) {
            cardBlocked.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Block Account",
                            "Permanent Security Action",
                            "Permanently block " + user.name + " (" + user.email + ") from accessing LifeShare operations?",
                            "Block User",
                            true,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserStatus(user.id, "BLOCKED");
                                }
                            }
                    );
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void updateUserStatus(String userId, final String newStatus) {
        if (scrollContent != null) savedScrollPosition = scrollContent.getScrollY();
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.updateAdminUserStatus(userId, newStatus, new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(AdminDashboardActivity.this, "Status updated to " + newStatus, Toast.LENGTH_SHORT).show();
                loadAllData();
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(AdminDashboardActivity.this, "Failed to update status: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showChangeRoleDialog(final ApiClient.AdminUserItem user) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_role, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView tvUser = dialogView.findViewById(R.id.tv_dialog_role_user);
        View cardDonor = dialogView.findViewById(R.id.card_role_opt_donor);
        View cardCoord = dialogView.findViewById(R.id.card_role_opt_coordinator);
        View cardAdmin = dialogView.findViewById(R.id.card_role_opt_admin);
        ImageView ivCheckDonor = dialogView.findViewById(R.id.iv_role_check_donor);
        ImageView ivCheckCoord = dialogView.findViewById(R.id.iv_role_check_coordinator);
        ImageView ivCheckAdmin = dialogView.findViewById(R.id.iv_role_check_admin);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_role_cancel);

        if (tvUser != null) tvUser.setText("Updating role for " + user.name + " (" + user.email + ")");

        String cur = user.role != null ? user.role.toUpperCase() : "DONOR";
        if ("COORDINATOR".equals(cur) && ivCheckCoord != null) ivCheckCoord.setVisibility(View.VISIBLE);
        else if ("ADMIN".equals(cur) && ivCheckAdmin != null) ivCheckAdmin.setVisibility(View.VISIBLE);
        else if (ivCheckDonor != null) ivCheckDonor.setVisibility(View.VISIBLE);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (cardDonor != null) {
            cardDonor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Demote to Standard Donor",
                            "Role Modification",
                            "Revoke coordinator/admin privileges and unassign " + user.name + " (" + user.email + ") from any hospital?",
                            "Demote to Donor",
                            true,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserRole(user.id, "DONOR", null);
                                }
                            }
                    );
                }
            });
        }

        if (cardCoord != null) {
            cardCoord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showSelectHospitalDialog(new HospitalSelectCallback() {
                        @Override
                        public void onHospitalSelected(final ApiClient.AdminHospitalItem hospital) {
                            showAdminConfirmationDialog(
                                    "Appoint Hospital Coordinator",
                                    "Coordinator Authorization",
                                    "Appoint " + user.name + " (" + user.email + ") as official Coordinator for " + hospital.name + " (" + (hospital.city != null ? hospital.city : "") + ")?",
                                    "Appoint Coordinator",
                                    false,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            updateUserRole(user.id, "COORDINATOR", hospital.id);
                                        }
                                    }
                            );
                        }
                    });
                }
            });
        }

        if (cardAdmin != null) {
            cardAdmin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Promote to Super Administrator",
                            "Elevate Permissions",
                            "Grant full administrative control and security oversight to " + user.name + " (" + user.email + ")?",
                            "Promote Admin",
                            false,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserRole(user.id, "ADMIN", null);
                                }
                            }
                    );
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void updateUserRole(String userId, final String newRole, final String hospitalId) {
        if (scrollContent != null) savedScrollPosition = scrollContent.getScrollY();
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.updateAdminUserRole(userId, newRole, hospitalId, new ApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(AdminDashboardActivity.this, "Role updated to " + newRole, Toast.LENGTH_SHORT).show();
                loadAllData();
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(AdminDashboardActivity.this, "Failed to update role: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    // =========================================================================
    // SEARCHABLE HOSPITAL SELECTOR DIALOG
    // =========================================================================

    private interface HospitalSelectCallback {
        void onHospitalSelected(ApiClient.AdminHospitalItem hospital);
    }

    private void showSelectHospitalDialog(final HospitalSelectCallback callback) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_hospital, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        final EditText etSearchHosp = dialogView.findViewById(R.id.et_dialog_hospital_search);
        final ProgressBar pb = dialogView.findViewById(R.id.pb_dialog_hospitals);
        final TextView tvEmpty = dialogView.findViewById(R.id.tv_dialog_hospitals_empty);
        final RecyclerView recycler = dialogView.findViewById(R.id.recycler_dialog_hospitals);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_dialog_close_hospitals);

        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (recycler != null) recycler.setLayoutManager(new LinearLayoutManager(this));

        final List<ApiClient.AdminHospitalItem> allHospitals = new ArrayList<>(loadedHospitals);
        final List<ApiClient.AdminHospitalItem> displayHospitals = new ArrayList<>();

        final Runnable populateAdapter = new Runnable() {
            @Override
            public void run() {
                if (pb != null) pb.setVisibility(View.GONE);
                if (displayHospitals.isEmpty()) {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    if (recycler != null) recycler.setVisibility(View.GONE);
                } else {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    if (recycler != null) {
                        recycler.setVisibility(View.VISIBLE);
                        recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                            @NonNull
                            @Override
                            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_verified_hospital, parent, false);
                                return new RecyclerView.ViewHolder(v) {};
                            }

                            @Override
                            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                                final ApiClient.AdminHospitalItem h = displayHospitals.get(position);
                                TextView tvName = holder.itemView.findViewById(R.id.tv_item_hospital_name);
                                TextView tvAddress = holder.itemView.findViewById(R.id.tv_item_hospital_address);
                                if (tvName != null) tvName.setText(h.name != null ? h.name : "Hospital");
                                if (tvAddress != null) tvAddress.setText((h.address != null ? h.address : "") + " • " + (h.city != null ? h.city : ""));

                                holder.itemView.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        dialog.dismiss();
                                        if (callback != null) callback.onHospitalSelected(h);
                                    }
                                });
                            }

                            @Override
                            public int getItemCount() {
                                return displayHospitals.size();
                            }
                        });
                    }
                }
            }
        };

        if (allHospitals.isEmpty()) {
            if (pb != null) pb.setVisibility(View.VISIBLE);
            apiClient.getAdminHospitals(new ApiClient.ApiCallback<List<ApiClient.AdminHospitalItem>>() {
                @Override
                public void onSuccess(List<ApiClient.AdminHospitalItem> result) {
                    if (result != null) {
                        loadedHospitals = result;
                        allHospitals.clear();
                        allHospitals.addAll(result);
                        displayHospitals.clear();
                        displayHospitals.addAll(result);
                    }
                    populateAdapter.run();
                }

                @Override
                public void onError(String errorMessage) {
                    if (pb != null) pb.setVisibility(View.GONE);
                    if (tvEmpty != null) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Failed to load hospitals: " + errorMessage);
                    }
                }
            });
        } else {
            displayHospitals.addAll(allHospitals);
            populateAdapter.run();
        }

        if (etSearchHosp != null) {
            etSearchHosp.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s.toString().toLowerCase().trim();
                    displayHospitals.clear();
                    if (query.isEmpty()) {
                        displayHospitals.addAll(allHospitals);
                    } else {
                        for (ApiClient.AdminHospitalItem h : allHospitals) {
                            if ((h.name != null && h.name.toLowerCase().contains(query))
                                    || (h.city != null && h.city.toLowerCase().contains(query))
                                    || (h.address != null && h.address.toLowerCase().contains(query))) {
                                displayHospitals.add(h);
                            }
                        }
                    }
                    populateAdapter.run();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    // =========================================================================
    // THEMED CONFIRMATION DIALOG HELPER
    // =========================================================================

    private void showAdminConfirmationDialog(
            String title,
            String subtitle,
            String message,
            String confirmText,
            boolean isDestructive,
            final Runnable onConfirm
    ) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_confirm, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView tvTitle = dialogView.findViewById(R.id.tv_confirm_title);
        TextView tvSub = dialogView.findViewById(R.id.tv_confirm_subtitle);
        TextView tvMsg = dialogView.findViewById(R.id.tv_confirm_message);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_confirm_cancel);
        MaterialButton btnAction = dialogView.findViewById(R.id.btn_confirm_action);
        ImageView ivIcon = dialogView.findViewById(R.id.iv_confirm_icon);
        FrameLayout iconBg = dialogView.findViewById(R.id.layout_confirm_icon_bg);

        if (tvTitle != null && title != null) tvTitle.setText(title);
        if (tvSub != null && subtitle != null) tvSub.setText(subtitle);
        if (tvMsg != null && message != null) tvMsg.setText(message);
        if (btnAction != null && confirmText != null) btnAction.setText(confirmText);

        if (isDestructive) {
            if (btnAction != null) {
                btnAction.setBackgroundColor(Color.parseColor("#C62828"));
                btnAction.setTextColor(Color.WHITE);
            }
            if (ivIcon != null) {
                ivIcon.setImageResource(R.drawable.ic_emergency_sos);
                ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#C62828")));
            }
            if (iconBg != null) {
                iconBg.setBackgroundColor(Color.parseColor("#26C62828"));
            }
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnAction != null) {
            btnAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    // =========================================================================
    // IN-PERSON QR CODE SCANNER ONBOARDING FLOW
    // =========================================================================

    private void startAdminQrScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, RC_CAMERA_PERMISSION);
            return;
        }

        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.setPrompt("Scan LifeShare Digital Pass QR to Onboard Coordinator");
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.setOrientationLocked(true);
            integrator.initiateScan();
        } catch (Throwable t) {
            Toast.makeText(this, "Scanner error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAdminQrScanner();
            } else {
                Toast.makeText(this, "Camera permission is required to scan Digital Passes", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                handleAdminScannedQr(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleAdminScannedQr(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            Toast.makeText(this, "Scanned QR code was empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String targetUserId = null;
        String targetName = null;
        String targetMobile = null;
        String targetBlood = null;

        if (rawValue.contains("|")) {
            String[] parts = rawValue.split("\\|");
            for (String part : parts) {
                if (part.startsWith("USERID=") || part.startsWith("ID=")) {
                    targetUserId = part.substring(part.indexOf('=') + 1).trim();
                } else if (part.startsWith("NAME=")) {
                    targetName = part.substring("NAME=".length()).trim();
                } else if (part.startsWith("BG=")) {
                    targetBlood = part.substring("BG=".length()).trim();
                } else if (part.startsWith("MOBILE=")) {
                    targetMobile = part.substring("MOBILE=".length()).trim();
                }
            }
        } else if (rawValue.startsWith("lifeshare:donor:")) {
            targetUserId = rawValue.substring("lifeshare:donor:".length()).trim();
        } else {
            targetUserId = rawValue.trim();
        }

        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        if (targetUserId != null && !targetUserId.isEmpty() && targetUserId.length() == 24) {
            final String finalUserId = targetUserId;
            final String finalName = targetName;
            final String finalMobile = targetMobile;
            final String finalBlood = targetBlood;

            apiClient.getAdminUser(targetUserId, new ApiClient.ApiCallback<ApiClient.AdminUserItem>() {
                @Override
                public void onSuccess(ApiClient.AdminUserItem user) {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (user != null) {
                        showScannedCoordinatorOnboardDialog(user);
                    } else {
                        Toast.makeText(AdminDashboardActivity.this, "User record not found", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    ApiClient.AdminUserItem fallback = new ApiClient.AdminUserItem();
                    fallback.id = finalUserId;
                    fallback.name = finalName != null ? finalName : "Scanned Candidate";
                    fallback.mobile = finalMobile != null ? finalMobile : "";
                    fallback.bloodGroup = finalBlood != null ? finalBlood : "O+";
                    fallback.role = "DONOR";
                    fallback.accountStatus = "ACTIVE";
                    showScannedCoordinatorOnboardDialog(fallback);
                }
            });
        } else {
            String searchQuery = targetMobile != null ? targetMobile : (targetName != null ? targetName : rawValue.trim());
            apiClient.getAdminUsers(searchQuery, null, null, 1, 10, new ApiClient.ApiCallback<List<ApiClient.AdminUserItem>>() {
                @Override
                public void onSuccess(List<ApiClient.AdminUserItem> users) {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (users != null && !users.isEmpty()) {
                        showScannedCoordinatorOnboardDialog(users.get(0));
                    } else {
                        Toast.makeText(AdminDashboardActivity.this, "No registered LifeShare account found for scanned QR", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    Toast.makeText(AdminDashboardActivity.this, "Failed to resolve scanned pass: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showScannedCoordinatorOnboardDialog(final ApiClient.AdminUserItem candidate) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_scan_onboard, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView tvBlood = dialogView.findViewById(R.id.tv_scanned_blood_group);
        TextView tvName = dialogView.findViewById(R.id.tv_scanned_user_name);
        TextView tvContact = dialogView.findViewById(R.id.tv_scanned_user_contact);
        TextView tvCurrentRole = dialogView.findViewById(R.id.tv_scanned_current_role);
        MaterialCardView cardSelectHospital = dialogView.findViewById(R.id.card_select_assigned_hospital);
        final TextView tvAssignedHospital = dialogView.findViewById(R.id.tv_assigned_hospital_name);
        final EditText etStaffId = dialogView.findViewById(R.id.et_scanned_staff_id);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_scanned_onboard_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_scanned_onboard_confirm);

        final ApiClient.AdminHospitalItem[] selectedHospital = new ApiClient.AdminHospitalItem[1];

        if (tvBlood != null) tvBlood.setText(candidate.bloodGroup != null ? candidate.bloodGroup : "O+");
        if (tvName != null) tvName.setText(candidate.name != null ? candidate.name : "Candidate");
        if (tvContact != null) tvContact.setText((candidate.email != null ? candidate.email : "") + " • " + (candidate.mobile != null ? candidate.mobile : ""));
        if (tvCurrentRole != null) tvCurrentRole.setText(candidate.role != null ? candidate.role : "DONOR");

        if (cardSelectHospital != null) {
            cardSelectHospital.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSelectHospitalDialog(new HospitalSelectCallback() {
                        @Override
                        public void onHospitalSelected(ApiClient.AdminHospitalItem hospital) {
                            selectedHospital[0] = hospital;
                            if (tvAssignedHospital != null) {
                                tvAssignedHospital.setText(hospital.name + " (" + (hospital.city != null ? hospital.city : "") + ")");
                                tvAssignedHospital.setTextColor(getResources().getColor(R.color.colorPrimary));
                            }
                        }
                    });
                }
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedHospital[0] == null) {
                        Toast.makeText(AdminDashboardActivity.this, "Please select an assigned hospital for this coordinator", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final String staffId = etStaffId != null && etStaffId.getText() != null ? etStaffId.getText().toString().trim() : "";

                    dialog.dismiss();
                    showAdminConfirmationDialog(
                            "Confirm Physical Appointment",
                            "In-Person Verification",
                            "Authorize " + candidate.name + " as official Coordinator at " + selectedHospital[0].name + "?\n\nPermissions and dispatch notification will be updated immediately.",
                            "Authorize Coordinator",
                            false,
                            new Runnable() {
                                @Override
                                public void run() {
                                    updateUserRole(candidate.id, "COORDINATOR", selectedHospital[0].id);
                                }
                            }
                    );
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    // =========================================================================
    // TAB 2: HOSPITALS
    // =========================================================================

    private void loadHospitals() {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.VISIBLE);
        if (containerItems != null) containerItems.setVisibility(View.GONE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getAdminHospitals(new ApiClient.ApiCallback<List<ApiClient.AdminHospitalItem>>() {
            @Override
            public void onSuccess(List<ApiClient.AdminHospitalItem> hospitals) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                loadedHospitals = hospitals != null ? hospitals : new ArrayList<ApiClient.AdminHospitalItem>();
                if (currentTab == AdminTab.HOSPITALS) renderHospitals(loadedHospitals);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                Toast.makeText(AdminDashboardActivity.this, "Error loading hospitals: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterHospitals(String query) {
        if (query == null || query.trim().isEmpty()) {
            renderHospitals(loadedHospitals);
            return;
        }
        List<ApiClient.AdminHospitalItem> filtered = new ArrayList<>();
        for (ApiClient.AdminHospitalItem h : loadedHospitals) {
            if (h.name != null && h.name.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(h);
            }
        }
        renderHospitals(filtered);
    }

    private void renderHospitals(List<ApiClient.AdminHospitalItem> hospitals) {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
        if (containerItems == null) return;
        containerItems.removeAllViews();
        containerItems.setVisibility(View.VISIBLE);

        if (hospitals == null || hospitals.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setText("No hospitals found");
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final ApiClient.AdminHospitalItem hospital : hospitals) {
            View view = inflater.inflate(R.layout.item_admin_hospital, containerItems, false);

            TextView tvName = view.findViewById(R.id.tv_admin_hosp_name);
            TextView tvAddress = view.findViewById(R.id.tv_admin_hosp_address);
            TextView tvCoordinators = view.findViewById(R.id.tv_admin_hosp_coordinators);
            MaterialButton btnManageCoordinators = view.findViewById(R.id.btn_admin_manage_coordinators);

            if (tvName != null) tvName.setText(hospital.name != null ? hospital.name : "Hospital");
            if (tvAddress != null) tvAddress.setText((hospital.address != null ? hospital.address : "") + " • " + (hospital.city != null ? hospital.city : ""));

            if (tvCoordinators != null) {
                if (hospital.coordinators != null && !hospital.coordinators.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Active Coordinators (").append(hospital.coordinators.size()).append("): ");
                    for (int i = 0; i < hospital.coordinators.size(); i++) {
                        sb.append(hospital.coordinators.get(i).name);
                        if (i < hospital.coordinators.size() - 1) sb.append(", ");
                    }
                    tvCoordinators.setText(sb.toString());
                } else {
                    tvCoordinators.setText("Coordinators: None currently active");
                }
            }

            if (btnManageCoordinators != null) {
                btnManageCoordinators.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showManageHospitalCoordinatorsDialog(hospital);
                    }
                });
            }

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showManageHospitalCoordinatorsDialog(hospital);
                }
            });

            containerItems.addView(view);
        }
        restoreSavedScrollPosition();
    }

    private void showManageHospitalCoordinatorsDialog(final ApiClient.AdminHospitalItem hospital) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_hospital_coordinators, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView tvHospName = dialogView.findViewById(R.id.tv_manage_hospital_name);
        MaterialButton btnAdd = dialogView.findViewById(R.id.btn_hospital_add_coordinator);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_manage_coordinators_close);
        final LinearLayout containerActive = dialogView.findViewById(R.id.container_active_coordinators);
        final LinearLayout containerEx = dialogView.findViewById(R.id.container_ex_coordinators);
        final TextView tvNoActive = dialogView.findViewById(R.id.tv_no_active_coordinators);
        final TextView tvNoEx = dialogView.findViewById(R.id.tv_no_ex_coordinators);

        if (tvHospName != null) tvHospName.setText(hospital.name);

        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnAdd != null) {
            btnAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showOnboardCoordinatorDialog(hospital);
                }
            });
        }

        // Fetch coordinators with active & ex-coordinators archive
        apiClient.getHospitalCoordinators(hospital.id, new ApiClient.ApiCallback<ApiClient.HospitalCoordinatorsResponse>() {
            @Override
            public void onSuccess(ApiClient.HospitalCoordinatorsResponse res) {
                if (res == null) return;
                LayoutInflater inflater = LayoutInflater.from(AdminDashboardActivity.this);

                // 1. Render Active Coordinators
                if (containerActive != null) {
                    containerActive.removeAllViews();
                    if (res.activeCoordinators != null && !res.activeCoordinators.isEmpty()) {
                        if (tvNoActive != null) tvNoActive.setVisibility(View.GONE);
                        for (final ApiClient.ActiveCoordinatorItem coord : res.activeCoordinators) {
                            View cv = inflater.inflate(R.layout.item_hospital_coordinator, containerActive, false);
                            TextView tvName = cv.findViewById(R.id.tv_coord_item_name);
                            TextView tvContact = cv.findViewById(R.id.tv_coord_item_contact);
                            TextView tvStatus = cv.findViewById(R.id.tv_coord_item_status);
                            MaterialButton btnHist = cv.findViewById(R.id.btn_coord_item_history);
                            MaterialButton btnUnassign = cv.findViewById(R.id.btn_coord_item_unassign);

                            if (tvName != null) tvName.setText(coord.name != null ? coord.name : "Coordinator");
                            if (tvContact != null) tvContact.setText((coord.email != null ? coord.email : "") + " • " + (coord.mobile != null ? coord.mobile : ""));
                            if (tvStatus != null) tvStatus.setText(coord.status != null ? coord.status : "ACTIVE");

                            if (btnHist != null) {
                                btnHist.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        showCoordinatorVerificationsDialog(coord.id, coord.name);
                                    }
                                });
                            }

                            if (btnUnassign != null) {
                                btnUnassign.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        confirmUnassignCoordinator(hospital, coord.id, coord.name, dialog);
                                    }
                                });
                            }

                            containerActive.addView(cv);
                        }
                    } else {
                        if (tvNoActive != null) tvNoActive.setVisibility(View.VISIBLE);
                    }
                }

                // 2. Render Historical Ex-Coordinators
                if (containerEx != null) {
                    containerEx.removeAllViews();
                    if (res.exCoordinators != null && !res.exCoordinators.isEmpty()) {
                        if (tvNoEx != null) tvNoEx.setVisibility(View.GONE);
                        for (final ApiClient.ExCoordinatorItem ex : res.exCoordinators) {
                            View ev = inflater.inflate(R.layout.item_ex_coordinator, containerEx, false);
                            TextView tvName = ev.findViewById(R.id.tv_ex_coord_name);
                            TextView tvContact = ev.findViewById(R.id.tv_ex_coord_contact);
                            TextView tvTenure = ev.findViewById(R.id.tv_ex_coord_tenure);
                            TextView tvReason = ev.findViewById(R.id.tv_ex_coord_reason);
                            TextView tvCount = ev.findViewById(R.id.tv_ex_coord_donations_count);
                            MaterialButton btnHist = ev.findViewById(R.id.btn_ex_coord_history);

                            if (tvName != null) tvName.setText(ex.name != null ? ex.name : "Ex-Coordinator");
                            if (tvContact != null) tvContact.setText((ex.email != null ? ex.email : "") + " • " + (ex.mobile != null ? ex.mobile : ""));
                            
                            String assignedStr = ex.assignedAt != null && ex.assignedAt.length() >= 10 ? ex.assignedAt.substring(0, 10) : "Start";
                            String revokedStr = ex.revokedAt != null && ex.revokedAt.length() >= 10 ? ex.revokedAt.substring(0, 10) : "Revoked";
                            if (tvTenure != null) tvTenure.setText("Tenure: " + assignedStr + " to " + revokedStr);

                            if (tvReason != null) tvReason.setText("Reason: " + (ex.reason != null ? ex.reason : "Unassigned"));
                            if (tvCount != null) tvCount.setText("Donations Certified During Tenure: " + ex.donationsVerifiedCount);

                            if (btnHist != null) {
                                btnHist.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        showCoordinatorVerificationsDialog(ex.id, ex.name);
                                    }
                                });
                            }

                            containerEx.addView(ev);
                        }
                    } else {
                        if (tvNoEx != null) tvNoEx.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AdminDashboardActivity.this, "Failed to load coordinators: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void confirmUnassignCoordinator(final ApiClient.AdminHospitalItem hospital, final String coordinatorId, final String coordinatorName, final AlertDialog parentDialog) {
        showAdminConfirmationDialog(
                "Unassign Coordinator",
                "Revoke Hospital Assignment",
                "Are you sure you want to unassign " + coordinatorName + " from " + hospital.name + "?\n\nTheir coordinator privileges will be revoked and their tenure performance will be archived in the security registry.",
                "Unassign & Archive",
                true,
                new Runnable() {
                    @Override
                    public void run() {
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
                        apiClient.unassignHospitalCoordinator(hospital.id, coordinatorId, "Unassigned by Super Admin", new ApiClient.ApiCallback<JsonObject>() {
                            @Override
                            public void onSuccess(JsonObject result) {
                                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                                if (parentDialog != null) parentDialog.dismiss();
                                Toast.makeText(AdminDashboardActivity.this, "Coordinator unassigned successfully", Toast.LENGTH_SHORT).show();
                                loadAllData();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                                Toast.makeText(AdminDashboardActivity.this, "Failed to unassign: " + errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );
    }

    private void showOnboardCoordinatorDialog(final ApiClient.AdminHospitalItem hospital) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_onboard_coordinator, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        TextView tvHosp = view.findViewById(R.id.tv_dialog_onboard_hospital);
        final com.google.android.material.textfield.TextInputEditText etName = view.findViewById(R.id.et_onboard_name);
        final com.google.android.material.textfield.TextInputEditText etEmail = view.findViewById(R.id.et_onboard_email);
        final com.google.android.material.textfield.TextInputEditText etMobile = view.findViewById(R.id.et_onboard_mobile);
        final com.google.android.material.textfield.TextInputEditText etStaffId = view.findViewById(R.id.et_onboard_staff_id);
        MaterialButton btnCancel = view.findViewById(R.id.btn_onboard_cancel);
        MaterialButton btnSubmit = view.findViewById(R.id.btn_onboard_submit);

        if (tvHosp != null) tvHosp.setText("Hospital: " + hospital.name);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String name = etName != null && etName.getText() != null ? etName.getText().toString().trim() : "";
                    String email = etEmail != null && etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                    String mobile = etMobile != null && etMobile.getText() != null ? etMobile.getText().toString().trim() : "";
                    String staffId = etStaffId != null && etStaffId.getText() != null ? etStaffId.getText().toString().trim() : "";

                    if (name.isEmpty() || email.isEmpty() || mobile.isEmpty()) {
                        Toast.makeText(AdminDashboardActivity.this, "Please enter name, email, and mobile number", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dialog.dismiss();
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

                    apiClient.onboardCoordinator(name, email, mobile, hospital.id, staffId, new ApiClient.ApiCallback<JsonObject>() {
                        @Override
                        public void onSuccess(JsonObject result) {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                            String tempPass = result.has("tempPassword") ? result.get("tempPassword").getAsString() : "";
                            
                            new AlertDialog.Builder(AdminDashboardActivity.this)
                                    .setTitle("Coordinator Onboarded Successfully")
                                    .setMessage("Account provisioned for " + name + " (" + mobile + ") at " + hospital.name + ".\n\nTemporary Password: " + tempPass + "\n\nCredentials have been dispatched via SMS.")
                                    .setPositiveButton("OK", null)
                                    .show();

                            loadAllData();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                            Toast.makeText(AdminDashboardActivity.this, "Onboarding failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void showCoordinatorVerificationsDialog(String coordinatorId, String coordinatorName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_coordinator_history, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_coord_hist_title);
        final TextView tvSub = dialogView.findViewById(R.id.tv_dialog_coord_hist_sub);
        final LinearLayout container = dialogView.findViewById(R.id.container_coord_verifications);
        final TextView tvNoVerifications = dialogView.findViewById(R.id.tv_no_coord_verifications);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_dialog_coord_hist_close);

        if (tvTitle != null) tvTitle.setText(coordinatorName != null ? coordinatorName : "Coordinator");

        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        apiClient.getCoordinatorVerifications(coordinatorId, new ApiClient.ApiCallback<ApiClient.CoordinatorVerificationsResponse>() {
            @Override
            public void onSuccess(ApiClient.CoordinatorVerificationsResponse res) {
                if (res == null) return;
                if (tvSub != null) tvSub.setText("Lifetime Verified Donations: " + res.count);

                if (container != null) {
                    container.removeAllViews();
                    if (res.verifications != null && !res.verifications.isEmpty()) {
                        if (tvNoVerifications != null) tvNoVerifications.setVisibility(View.GONE);
                        LayoutInflater inflater = LayoutInflater.from(AdminDashboardActivity.this);

                        for (ApiClient.CoordinatorVerificationRecord rec : res.verifications) {
                            View iv = inflater.inflate(R.layout.item_coordinator_history, container, false);
                            TextView tvBlood = iv.findViewById(R.id.tv_coord_hist_blood);
                            TextView tvDonor = iv.findViewById(R.id.tv_coord_hist_donor);
                            TextView tvCert = iv.findViewById(R.id.tv_coord_hist_cert);
                            TextView tvUnits = iv.findViewById(R.id.tv_coord_hist_units);
                            TextView tvTime = iv.findViewById(R.id.tv_coord_hist_time);

                            if (tvBlood != null) tvBlood.setText(rec.bloodGroup != null ? rec.bloodGroup : "O+");
                            if (tvDonor != null) tvDonor.setText(rec.donorName != null ? rec.donorName : "Donor");
                            if (tvCert != null) tvCert.setText("Cert: " + (rec.certificateId != null ? rec.certificateId : ""));
                            if (tvUnits != null) tvUnits.setText(rec.unitsDonated + " Unit(s) • For Patient: " + (rec.patientName != null ? rec.patientName : ""));
                            if (tvTime != null) {
                                String t = rec.verifiedAt;
                                if (t != null && t.length() >= 16) t = t.substring(0, 10) + " " + t.substring(11, 16);
                                tvTime.setText(t != null ? t : "");
                            }

                            container.addView(iv);
                        }
                    } else {
                        if (tvNoVerifications != null) tvNoVerifications.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AdminDashboardActivity.this, "Failed to load verifications: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void showRegisterHospitalDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_register_hospital, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        final com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.et_reg_hosp_name);
        final com.google.android.material.textfield.TextInputEditText etAddress = dialogView.findViewById(R.id.et_reg_hosp_address);
        final com.google.android.material.textfield.TextInputEditText etPhone = dialogView.findViewById(R.id.et_reg_hosp_phone);
        final com.google.android.material.textfield.TextInputEditText etLat = dialogView.findViewById(R.id.et_reg_hosp_lat);
        final com.google.android.material.textfield.TextInputEditText etLng = dialogView.findViewById(R.id.et_reg_hosp_lng);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_reg_hosp_cancel);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btn_reg_hosp_submit);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String name = etName != null && etName.getText() != null ? etName.getText().toString().trim() : "";
                    String address = etAddress != null && etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
                    String phone = etPhone != null && etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
                    
                    double lat = 20.2961;
                    double lng = 85.8245;
                    try {
                        if (etLat != null && etLat.getText() != null && !etLat.getText().toString().trim().isEmpty()) {
                            lat = Double.parseDouble(etLat.getText().toString().trim());
                        }
                        if (etLng != null && etLng.getText() != null && !etLng.getText().toString().trim().isEmpty()) {
                            lng = Double.parseDouble(etLng.getText().toString().trim());
                        }
                    } catch (Exception ignored) {}

                    if (name.isEmpty() || address.isEmpty()) {
                        Toast.makeText(AdminDashboardActivity.this, "Hospital Name and Address are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dialog.dismiss();
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

                    apiClient.createAdminHospital(name, address, null, lat, lng, phone, new ApiClient.ApiCallback<JsonObject>() {
                        @Override
                        public void onSuccess(JsonObject result) {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                            Toast.makeText(AdminDashboardActivity.this, "Hospital registered successfully!", Toast.LENGTH_LONG).show();
                            loadHospitals();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                            Toast.makeText(AdminDashboardActivity.this, "Failed to register: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    // =========================================================================
    // TAB 3: EMERGENCIES
    // =========================================================================

    private void loadEmergencies() {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.VISIBLE);
        if (containerItems != null) containerItems.setVisibility(View.GONE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getAdminEmergencies(new ApiClient.ApiCallback<List<ApiClient.AdminEmergencyItem>>() {
            @Override
            public void onSuccess(List<ApiClient.AdminEmergencyItem> emergencies) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                loadedEmergencies = emergencies != null ? emergencies : new ArrayList<ApiClient.AdminEmergencyItem>();
                if (currentTab == AdminTab.EMERGENCIES) renderEmergencies(loadedEmergencies);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                Toast.makeText(AdminDashboardActivity.this, "Error loading emergencies: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderEmergencies(List<ApiClient.AdminEmergencyItem> emergencies) {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
        if (containerItems == null) return;
        containerItems.removeAllViews();
        containerItems.setVisibility(View.VISIBLE);

        if (emergencies == null || emergencies.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setText("No active emergency SOS requests");
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (ApiClient.AdminEmergencyItem item : emergencies) {
            View view = inflater.inflate(R.layout.item_admin_emergency, containerItems, false);

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
            
            if (tvStatus != null) {
                String st = item.status != null ? item.status : "ACTIVE";
                if ("ACTIVE".equalsIgnoreCase(st)) {
                    tvStatus.setText("Seeking Donors");
                    tvStatus.setTextColor(getResources().getColor(R.color.colorPrimary));
                } else if ("PARTIAL".equalsIgnoreCase(st)) {
                    tvStatus.setText("Partially Fulfilled");
                    tvStatus.setTextColor(getResources().getColor(R.color.cooldown_orange));
                } else if ("FULFILLED".equalsIgnoreCase(st)) {
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

            containerItems.addView(view);
        }
        restoreSavedScrollPosition();
    }

    // =========================================================================
    // TAB 4: AUDIT LOGS
    // =========================================================================

    private void loadAuditLogs() {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.VISIBLE);
        if (containerItems != null) containerItems.setVisibility(View.GONE);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.getAdminAuditLogs(new ApiClient.ApiCallback<List<ApiClient.AdminAuditLogItem>>() {
            @Override
            public void onSuccess(List<ApiClient.AdminAuditLogItem> logs) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                loadedAuditLogs = logs != null ? logs : new ArrayList<ApiClient.AdminAuditLogItem>();
                if (currentTab == AdminTab.AUDIT) renderAuditLogs(loadedAuditLogs);
            }

            @Override
            public void onError(String errorMessage) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
                Toast.makeText(AdminDashboardActivity.this, "Error loading audit logs: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderAuditLogs(List<ApiClient.AdminAuditLogItem> logs) {
        if (layoutAdminSkeleton != null) layoutAdminSkeleton.setVisibility(View.GONE);
        if (containerItems == null) return;
        containerItems.removeAllViews();
        containerItems.setVisibility(View.VISIBLE);

        if (logs == null || logs.isEmpty()) {
            if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
            if (tvEmptyMessage != null) tvEmptyMessage.setText("No audit log events recorded yet");
            return;
        }

        if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (ApiClient.AdminAuditLogItem log : logs) {
            View view = inflater.inflate(R.layout.item_admin_audit_log, containerItems, false);

            TextView tvAction = view.findViewById(R.id.tv_audit_action_badge);
            TextView tvTime = view.findViewById(R.id.tv_audit_time);
            TextView tvDetails = view.findViewById(R.id.tv_audit_details);
            TextView tvActor = view.findViewById(R.id.tv_audit_actor);

            if (tvAction != null) {
                String act = log.actionDisplay != null && !log.actionDisplay.isEmpty() ? log.actionDisplay : (log.action != null ? log.action.replace("_", " ") : "Security Event");
                tvAction.setText(act);
            }
            if (tvActor != null) tvActor.setText("Actor Role: " + (log.actorRole != null ? log.actorRole : "SYSTEM"));
            if (tvDetails != null) {
                String det = log.detailsDisplay != null && !log.detailsDisplay.isEmpty() ? log.detailsDisplay : ("Target: " + (log.entityType != null ? log.entityType : "System"));
                tvDetails.setText(det);
            }
            if (tvTime != null) {
                String t = log.createdAt;
                if (t != null && t.length() >= 16) t = t.substring(0, 10) + " " + t.substring(11, 16);
                tvTime.setText(t != null ? t : "");
            }

            containerItems.addView(view);
        }
        restoreSavedScrollPosition();
    }

    private void restoreSavedScrollPosition() {
        if (savedScrollPosition > 0 && scrollContent != null) {
            final int pos = savedScrollPosition;
            scrollContent.post(new Runnable() {
                @Override
                public void run() {
                    if (scrollContent != null) scrollContent.scrollTo(0, pos);
                }
            });
            savedScrollPosition = 0;
        }
    }
}
