package com.example.abhijeet.bloodbank;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private NestedScrollView scrollContent;
    private TextView tvStatUsers, tvStatDonors, tvStatHospitals, tvStatDonations;
    private TextView tvSectionTitle;
    private MaterialButton btnAddHospital, btnSwitchToDonor;
    private FrameLayout btnAdminProfile;
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
                    updateUserStatus(user.id, "ACTIVE");
                }
            });
        }

        if (btnSuspend != null) {
            btnSuspend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    updateUserStatus(user.id, "SUSPENDED");
                }
            });
        }

        if (btnBlock != null) {
            btnBlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    updateUserStatus(user.id, "BLOCKED");
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
                    updateUserStatus(user.id, "ACTIVE");
                }
            });
        }

        if (cardSuspended != null) {
            cardSuspended.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    updateUserStatus(user.id, "SUSPENDED");
                }
            });
        }

        if (cardBlocked != null) {
            cardBlocked.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    updateUserStatus(user.id, "BLOCKED");
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void updateUserStatus(String userId, final String newStatus) {
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
                    updateUserRole(user.id, "DONOR");
                }
            });
        }

        if (cardCoord != null) {
            cardCoord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    updateUserRole(user.id, "COORDINATOR");
                }
            });
        }

        if (cardAdmin != null) {
            cardAdmin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    updateUserRole(user.id, "ADMIN");
                }
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void updateUserRole(String userId, final String newRole) {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);

        apiClient.updateAdminUserRole(userId, newRole, null, new ApiClient.ApiCallback<JsonObject>() {
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
        new AlertDialog.Builder(this)
                .setTitle("Unassign Coordinator")
                .setMessage("Are you sure you want to unassign " + coordinatorName + " from " + hospital.name + "?\n\nTheir coordinator privileges will be revoked and their tenure performance will be archived.")
                .setPositiveButton("Unassign & Archive", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
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
                })
                .setNegativeButton("Cancel", null)
                .show();
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
    }
}
