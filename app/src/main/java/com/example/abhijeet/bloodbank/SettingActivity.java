package com.example.abhijeet.bloodbank;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingActivity extends AppCompatActivity {

    private SwitchMaterial switchLocationSharing, switchDonorAvailability, switchBiometric;
    private MaterialButton btnBackendUrl;
    private View layoutThemeSetting;
    private android.widget.TextView tvThemeSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_setting);

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        android.widget.TextView tvTitle = findViewById(R.id.tv_header_title);
        if (tvTitle != null) {
            tvTitle.setText("Settings");
        }

        final DataManager dm = DataManager.getInstance(this);
        final UserProfile currentUser = dm.getCurrentUser();

        switchLocationSharing = findViewById(R.id.switch_location_sharing);
        switchDonorAvailability = findViewById(R.id.switch_donor_availability);
        switchBiometric = findViewById(R.id.switch_biometric);
        btnBackendUrl = findViewById(R.id.btn_backend_url);
        layoutThemeSetting = findViewById(R.id.layout_theme_setting);
        tvThemeSubtitle = findViewById(R.id.tv_theme_subtitle);

        updateThemeSubtitleText(dm.getThemeMode());

        if (layoutThemeSetting != null) {
            layoutThemeSetting.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showThemeSelectionDialog(dm);
                }
            });
        }

        // Initial state
        switchLocationSharing.setChecked(dm.isLocationSharingEnabled());
        switchDonorAvailability.setChecked(currentUser != null && currentUser.isAvailable());
        switchBiometric.setChecked(dm.isBiometricLockEnabled());

        // Location sharing toggle
        switchLocationSharing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dm.setLocationSharingEnabled(isChecked);
                Toast.makeText(SettingActivity.this, "Location Sharing " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
            }
        });

        // Availability toggle
        switchDonorAvailability.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                if (currentUser != null) {
                    currentUser.setAvailable(isChecked);
                    dm.saveCurrentUser(currentUser);
                }
                ApiClient.getInstance().updateAvailability(isChecked, new ApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        Toast.makeText(SettingActivity.this, "Donor availability: " + (isChecked ? "Available" : "Unavailable"), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(SettingActivity.this, "Updated locally", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Biometric toggle
        switchBiometric.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dm.setBiometricLockEnabled(isChecked);
                Toast.makeText(SettingActivity.this, "Biometric Lock " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
            }
        });

        // Server URL configuration dialog
        btnBackendUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showServerUrlDialog();
            }
        });
    }

    private void showServerUrlDialog() {
        final EditText input = new EditText(this);
        input.setText(ApiClient.getInstance().getBaseUrl());
        input.setHint("http://172.28.183.190:5000/api");
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Backend Server URL")
                .setMessage("Enter the REST API endpoint of your Node.js + MongoDB backend:")
                .setView(input)
                .setPositiveButton("Save URL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newUrl = input.getText().toString().trim();
                        if (!newUrl.isEmpty()) {
                            ApiClient.getInstance().saveBaseUrl(SettingActivity.this, newUrl);
                            Toast.makeText(SettingActivity.this, "Server URL updated: " + newUrl, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("Test Ping", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newUrl = input.getText().toString().trim();
                        if (!newUrl.isEmpty()) {
                            ApiClient.getInstance().setBaseUrl(newUrl);
                        }
                        Toast.makeText(SettingActivity.this, "Pinging server...", Toast.LENGTH_SHORT).show();
                        ApiClient.getInstance().checkHealth(new ApiClient.ApiCallback<String>() {
                            @Override
                            public void onSuccess(String result) {
                                new AlertDialog.Builder(SettingActivity.this)
                                        .setTitle("Connection Successful")
                                        .setMessage(result)
                                        .setPositiveButton("OK", null)
                                        .show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                new AlertDialog.Builder(SettingActivity.this)
                                        .setTitle("Connection Failed")
                                        .setMessage(errorMessage)
                                        .setPositiveButton("OK", null)
                                        .show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateThemeSubtitleText(int mode) {
        if (tvThemeSubtitle == null) return;
        switch (mode) {
            case DataManager.THEME_LIGHT:
                tvThemeSubtitle.setText("Light Mode");
                break;
            case DataManager.THEME_DARK:
                tvThemeSubtitle.setText("Dark Mode");
                break;
            case DataManager.THEME_SYSTEM:
            default:
                tvThemeSubtitle.setText("System Default (Follow Android)");
                break;
        }
    }

    private void showThemeSelectionDialog(final DataManager dm) {
        final String[] themes = new String[]{"System Default (Follow Android)", "Light Mode", "Dark Mode"};
        int currentMode = dm.getThemeMode();
        int checkedItem = 0;
        if (currentMode == DataManager.THEME_LIGHT) checkedItem = 1;
        else if (currentMode == DataManager.THEME_DARK) checkedItem = 2;

        new AlertDialog.Builder(this)
                .setTitle("Select App Theme")
                .setSingleChoiceItems(themes, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int selectedMode = DataManager.THEME_SYSTEM;
                        if (which == 1) selectedMode = DataManager.THEME_LIGHT;
                        else if (which == 2) selectedMode = DataManager.THEME_DARK;

                        dm.setThemeMode(selectedMode);
                        updateThemeSubtitleText(selectedMode);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WindowHelper.applyEdgeToEdge(this);
        DataManager dm = DataManager.getInstance(this);
        updateThemeSubtitleText(dm.getThemeMode());
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
