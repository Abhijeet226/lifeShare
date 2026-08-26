package com.example.abhijeet.bloodbank;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.example.abhijeet.bloodbank.ui.EmergencyFragment;
import com.example.abhijeet.bloodbank.ui.HomeFragment;
import com.example.abhijeet.bloodbank.ui.ProfileFragment;
import com.example.abhijeet.bloodbank.ui.SearchFragment;
import com.google.android.material.navigation.NavigationView;

public class LogInActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public static final int TAB_HOME = 0;
    public static final int TAB_SEARCH = 1;
    public static final int TAB_EMERGENCY = 2;
    public static final int TAB_PROFILE = 3;

    private DrawerLayout mDrawerLayout;
    private View navTabHome, navTabSearch, navTabEmergency, navTabProfile;
    private View indicatorHome, indicatorSearch, indicatorEmergency, indicatorProfile;
    private ImageView ivTabHome, ivTabSearch, ivTabEmergency, ivTabProfile;
    private TextView tvTabHome, tvTabSearch, tvTabEmergency, tvTabProfile;

    private ImageView drawerAvatar;
    private TextView drawerName, drawerEmail;

    private int currentTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        Log.e("LifeShareDebug", "LogInActivity onCreate started");
        try {
            setContentView(R.layout.activity_log_in);
            Log.e("LifeShareDebug", "setContentView activity_log_in successful");

            mDrawerLayout = findViewById(R.id.drawer);
            NavigationView navigationView = findViewById(R.id.nav_view);
            if (navigationView != null) {
                navigationView.setNavigationItemSelectedListener(this);
                View headerView = navigationView.getHeaderView(0);
                if (headerView != null) {
                    drawerAvatar = headerView.findViewById(R.id.profile);
                    drawerName = headerView.findViewById(R.id.name);
                    drawerEmail = headerView.findViewById(R.id.email);
                }
            }

            // Floating Bottom Bar Tabs & Indicators
            navTabHome = findViewById(R.id.nav_tab_home);
            navTabSearch = findViewById(R.id.nav_tab_search);
            navTabEmergency = findViewById(R.id.nav_tab_emergency);
            navTabProfile = findViewById(R.id.nav_tab_profile);

            indicatorHome = findViewById(R.id.indicator_home);
            indicatorSearch = findViewById(R.id.indicator_search);
            indicatorEmergency = findViewById(R.id.indicator_emergency);
            indicatorProfile = findViewById(R.id.indicator_profile);

            ivTabHome = findViewById(R.id.iv_tab_home);
            ivTabSearch = findViewById(R.id.iv_tab_search);
            ivTabEmergency = findViewById(R.id.iv_tab_emergency);
            ivTabProfile = findViewById(R.id.iv_tab_profile);

            tvTabHome = findViewById(R.id.tv_tab_home);
            tvTabSearch = findViewById(R.id.tv_tab_search);
            tvTabEmergency = findViewById(R.id.tv_tab_emergency);
            tvTabProfile = findViewById(R.id.tv_tab_profile);

            if (navTabHome != null) {
                navTabHome.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchToTab(TAB_HOME);
                    }
                });
            }

            if (navTabSearch != null) {
                navTabSearch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchToTab(TAB_SEARCH);
                    }
                });
            }

            if (navTabEmergency != null) {
                navTabEmergency.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchToTab(TAB_EMERGENCY);
                    }
                });
            }

            if (navTabProfile != null) {
                navTabProfile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchToTab(TAB_PROFILE);
                    }
                });
            }

            switchToTab(TAB_HOME);
            loadHeaderUserData();
            NotificationHelper.createNotificationChannels(this);
            checkBiometricLockOnLaunch();
            syncLocationAndFcmToken();
            Log.e("LifeShareDebug", "LogInActivity onCreate completed successfully");
        } catch (Throwable t) {
            Log.e("LifeShareCrash", "FATAL in LogInActivity onCreate", t);
        }
    }

    private void syncLocationAndFcmToken() {
        final DataManager dm = DataManager.getInstance(this);

        // 1. Sync FCM Token
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<String>() {
                        @Override
                        public void onComplete(@NonNull com.google.android.gms.tasks.Task<String> task) {
                            try {
                                if (task.isSuccessful() && task.getResult() != null) {
                                    String token = task.getResult();
                                    dm.saveFcmToken(token);
                                    ApiClient.getInstance().registerDeviceToken(token, null);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // 2. Sync Location if Sharing is enabled
        try {
            if (dm.isLocationSharingEnabled() && LocationHelper.hasLocationPermission(this)) {
                LocationHelper.getCurrentLocation(this, new LocationHelper.LocationCallback() {
                    @Override
                    public void onLocationAcquired(double latitude, double longitude) {
                        try {
                            dm.saveLastKnownLocation(latitude, longitude);
                        } catch (Throwable ignored) {}
                    }

                    @Override
                    public void onLocationFailed(String errorMessage) {}
                });
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // 3. Live Profile & Role Sync from Database
        try {
            if (ApiClient.getInstance().getAuthToken() != null && !ApiClient.getInstance().getAuthToken().isEmpty()) {
                ApiClient.getInstance().getProfile(new ApiClient.ApiCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile user) {
                        if (user != null) {
                            dm.saveCurrentUser(user);
                            loadHeaderUserData();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    private final android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            syncEmergencyNotifications();
            syncHandler.postDelayed(this, 30000);
        }
    };

    private void syncEmergencyNotifications() {
        try {
            final DataManager dm = DataManager.getInstance(this);
            dm.fetchEmergencyRequests(new DataManager.RequestCallback() {
                @Override
                public void onRequestsLoaded(java.util.List<EmergencyRequest> requests) {
                    try {
                        UserProfile currentUser = dm.getCurrentUser();
                        NotificationHelper.checkAndNotifyNewEmergencies(LogInActivity.this, requests, currentUser);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            syncHandler.removeCallbacks(syncRunnable);
            syncHandler.post(syncRunnable);
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            syncHandler.removeCallbacks(syncRunnable);
        } catch (Throwable ignored) {}
    }

    private void checkBiometricLockOnLaunch() {
        try {
            if (DataManager.getInstance(this).isBiometricLockEnabled() && BiometricHelper.isBiometricAvailable(this)) {
                BiometricHelper.showBiometricPrompt(
                        this,
                        "Unlock LifeShare",
                        "Verify your biometric identity to access donor records",
                        new BiometricHelper.BiometricAuthListener() {
                            @Override
                            public void onAuthSuccess() {}

                            @Override
                            public void onAuthFailed(String errorMessage) {}

                            @Override
                            public void onAuthCancelled() {
                                finishAffinity();
                            }
                        }
                );
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void switchToTab(int tabIndex) {
        if (currentTab == tabIndex) return;
        if (isFinishing() || isDestroyed()) return;
        currentTab = tabIndex;

        Fragment fragment;
        switch (tabIndex) {
            case TAB_SEARCH:
                fragment = new SearchFragment();
                break;
            case TAB_EMERGENCY:
                fragment = new EmergencyFragment();
                break;
            case TAB_PROFILE:
                fragment = new ProfileFragment();
                break;
            case TAB_HOME:
            default:
                fragment = new HomeFragment();
                break;
        }

        try {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commitAllowingStateLoss();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        updateBottomNavigationUI(tabIndex);
        WindowHelper.updateStatusBarForTab(this, tabIndex);
    }

    private void updateBottomNavigationUI(int activeIndex) {
        int activeColor = ContextCompat.getColor(this, R.color.colorPrimary);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);

        if (indicatorHome == null || indicatorSearch == null || indicatorEmergency == null || indicatorProfile == null) {
            return;
        }

        // Reset all indicators to transparent
        indicatorHome.setBackgroundResource(android.R.color.transparent);
        indicatorSearch.setBackgroundResource(android.R.color.transparent);
        indicatorEmergency.setBackgroundResource(android.R.color.transparent);
        indicatorProfile.setBackgroundResource(android.R.color.transparent);

        // Reset all icon tints to inactive grey
        ivTabHome.setColorFilter(inactiveColor);
        ivTabSearch.setColorFilter(inactiveColor);
        ivTabEmergency.setColorFilter(inactiveColor);
        ivTabProfile.setColorFilter(inactiveColor);

        // Reset text colors & styles
        tvTabHome.setTextColor(inactiveColor);
        tvTabSearch.setTextColor(inactiveColor);
        tvTabEmergency.setTextColor(inactiveColor);
        tvTabProfile.setTextColor(inactiveColor);

        tvTabHome.setTypeface(null, Typeface.NORMAL);
        tvTabSearch.setTypeface(null, Typeface.NORMAL);
        tvTabEmergency.setTypeface(null, Typeface.NORMAL);
        tvTabProfile.setTypeface(null, Typeface.NORMAL);

        // Highlight active tab with pill background & padding
        switch (activeIndex) {
            case TAB_HOME:
                indicatorHome.setBackgroundResource(R.drawable.bg_tab_indicator_active);
                ivTabHome.setColorFilter(activeColor);
                tvTabHome.setTextColor(activeColor);
                tvTabHome.setTypeface(null, Typeface.BOLD);
                break;
            case TAB_SEARCH:
                indicatorSearch.setBackgroundResource(R.drawable.bg_tab_indicator_active);
                ivTabSearch.setColorFilter(activeColor);
                tvTabSearch.setTextColor(activeColor);
                tvTabSearch.setTypeface(null, Typeface.BOLD);
                break;
            case TAB_EMERGENCY:
                indicatorEmergency.setBackgroundResource(R.drawable.bg_tab_emergency_prominent);
                ivTabEmergency.setColorFilter(android.graphics.Color.WHITE);
                tvTabEmergency.setTextColor(android.graphics.Color.WHITE);
                tvTabEmergency.setTypeface(null, Typeface.BOLD);
                break;
            case TAB_PROFILE:
                indicatorProfile.setBackgroundResource(R.drawable.bg_tab_indicator_active);
                ivTabProfile.setColorFilter(activeColor);
                tvTabProfile.setTextColor(activeColor);
                tvTabProfile.setTypeface(null, Typeface.BOLD);
                break;
        }
    }

    private void loadHeaderUserData() {
        try {
            DataManager dm = DataManager.getInstance(this);
            UserProfile user = dm.getCurrentUser();
            if (user != null) {
                if (drawerName != null && user.getName() != null) drawerName.setText(user.getName());
                if (drawerEmail != null && user.getEmail() != null) drawerEmail.setText(user.getEmail());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.logoutMenu) {
            logout();
        } else if (id == R.id.ProfileMenu) {
            switchToTab(TAB_PROFILE);
        } else if (id == R.id.feedbackMenu) {
            startActivity(new Intent(LogInActivity.this, FeedbackActivity.class));
        } else if (id == R.id.settingsMenu) {
            startActivity(new Intent(LogInActivity.this, SettingActivity.class));
        } else if (id == R.id.aboutMenu) {
            startActivity(new Intent(LogInActivity.this, AboutActivity.class));
        }

        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else if (currentTab != TAB_HOME) {
            switchToTab(TAB_HOME);
        } else {
            showModernExitDialog();
        }
    }

    private void showModernExitDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_exit_app, null);
        com.google.android.material.button.MaterialButton btnStay = dialogView.findViewById(R.id.btn_exit_stay);
        com.google.android.material.button.MaterialButton btnExit = dialogView.findViewById(R.id.btn_exit_confirm);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        final android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnStay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                finishAffinity();
            }
        });

        dialog.show();
    }

    public void logout() {
        DataManager.getInstance(this).setLoggedIn(false);
        finish();
        startActivity(new Intent(this, MainActivity.class));
    }
}
