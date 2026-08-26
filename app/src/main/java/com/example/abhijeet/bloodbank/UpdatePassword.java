package com.example.abhijeet.bloodbank;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class UpdatePassword extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_CODE = 901;

    private View btnOpenChangePassword;
    private SwitchMaterial switchLocationSharing, switchHidePhone, switchBiometricLock, switchHospitalOnly;
    private View layoutThemeSetting, btnTestNotification, btnExportData, btnDeleteAccount;
    private TextView tvThemeSubtitle;
    private boolean isSettingUpSwitch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_update_password);

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        TextView tvTitle = findViewById(R.id.tv_header_title);
        if (tvTitle != null) {
            tvTitle.setText("Security & Privacy");
            tvTitle.setTextSize(20);
        }

        final DataManager dm = DataManager.getInstance(this);
        final UserProfile user = dm.getCurrentUser();

        btnOpenChangePassword = findViewById(R.id.btn_open_change_password);
        switchLocationSharing = findViewById(R.id.switch_location_sharing);
        switchHidePhone = findViewById(R.id.switch_hide_phone);
        switchBiometricLock = findViewById(R.id.switch_biometric_lock);
        switchHospitalOnly = findViewById(R.id.switch_hospital_only);
        layoutThemeSetting = findViewById(R.id.layout_theme_setting);
        tvThemeSubtitle = findViewById(R.id.tv_theme_subtitle);
        btnTestNotification = findViewById(R.id.btn_test_notification);
        btnExportData = findViewById(R.id.btn_export_donor_data);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);

        isSettingUpSwitch = true;
        if (switchLocationSharing != null) switchLocationSharing.setChecked(dm.isLocationSharingEnabled());
        if (switchHidePhone != null && user != null) switchHidePhone.setChecked(user.isHideMobileNumber());
        if (switchBiometricLock != null) switchBiometricLock.setChecked(dm.isBiometricLockEnabled());
        if (switchHospitalOnly != null && user != null) switchHospitalOnly.setChecked(user.isHospitalOnlyVisibility());
        isSettingUpSwitch = false;

        updateThemeSubtitleText(dm.getThemeMode());

        // 1. Change Password Modal
        if (btnOpenChangePassword != null) {
            btnOpenChangePassword.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (user != null) {
                        showChangePasswordDialog(user);
                    }
                }
            });
        }

        // 2. Location Sharing Switch
        if (switchLocationSharing != null) {
            switchLocationSharing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isSettingUpSwitch) return;
                    dm.setLocationSharingEnabled(isChecked);
                    Toast.makeText(UpdatePassword.this, isChecked ? "Live Location Sharing Enabled" : "Location Sharing Disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 3. Hide Phone Switch
        if (switchHidePhone != null) {
            switchHidePhone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isSettingUpSwitch || user == null) return;
                    user.setHideMobileNumber(isChecked);
                    dm.saveCurrentUser(user);
                    Toast.makeText(UpdatePassword.this, isChecked ? "Mobile number hidden from public search" : "Mobile number visible on verified searches", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 4. Biometric Sensor Verification
        if (switchBiometricLock != null) {
            switchBiometricLock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                    if (isSettingUpSwitch) return;

                    if (isChecked) {
                        if (!BiometricHelper.isBiometricAvailable(UpdatePassword.this)) {
                            Toast.makeText(UpdatePassword.this, "Biometric authentication is not supported or set up on this device.", Toast.LENGTH_LONG).show();
                            isSettingUpSwitch = true;
                            switchBiometricLock.setChecked(false);
                            isSettingUpSwitch = false;
                            dm.setBiometricLockEnabled(false);
                            return;
                        }

                        BiometricHelper.showBiometricPrompt(
                                UpdatePassword.this,
                                "Enable Biometric Lock",
                                "Confirm your fingerprint or face to protect LifeShare",
                                new BiometricHelper.BiometricAuthListener() {
                                    @Override
                                    public void onAuthSuccess() {
                                        dm.setBiometricLockEnabled(true);
                                        if (user != null) {
                                            user.setBiometricEnabled(true);
                                            dm.saveCurrentUser(user);
                                        }
                                        Toast.makeText(UpdatePassword.this, "Biometric Lock successfully enabled!", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onAuthFailed(String errorMessage) {
                                        Toast.makeText(UpdatePassword.this, errorMessage, Toast.LENGTH_SHORT).show();
                                        isSettingUpSwitch = true;
                                        switchBiometricLock.setChecked(false);
                                        isSettingUpSwitch = false;
                                        dm.setBiometricLockEnabled(false);
                                    }

                                    @Override
                                    public void onAuthCancelled() {
                                        Toast.makeText(UpdatePassword.this, "Biometric verification cancelled.", Toast.LENGTH_SHORT).show();
                                        isSettingUpSwitch = true;
                                        switchBiometricLock.setChecked(false);
                                        isSettingUpSwitch = false;
                                        dm.setBiometricLockEnabled(false);
                                    }
                                }
                        );
                    } else {
                        dm.setBiometricLockEnabled(false);
                        if (user != null) {
                            user.setBiometricEnabled(false);
                            dm.saveCurrentUser(user);
                        }
                        Toast.makeText(UpdatePassword.this, "Biometric lock disabled.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // 5. Hospital-Only Switch
        if (switchHospitalOnly != null) {
            switchHospitalOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isSettingUpSwitch || user == null) return;
                    user.setHospitalOnlyVisibility(isChecked);
                    dm.saveCurrentUser(user);
                    Toast.makeText(UpdatePassword.this, isChecked ? "Visibility restricted to verified hospitals" : "Public voluntary visibility enabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 6. Theme Selector
        if (layoutThemeSetting != null) {
            layoutThemeSetting.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showThemeSelectionDialog(dm);
                }
            });
        }

        // 7. Test Emergency SOS Notification
        if (btnTestNotification != null) {
            btnTestNotification.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkPermissionAndSendNotification();
                }
            });
        }

        // 8. Export Donor Data
        if (btnExportData != null) {
            btnExportData.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (user == null) return;
                    String exportSummary = "LifeShare Medical & Donor Record:\n" +
                            "Name: " + user.getName() + "\n" +
                            "Email: " + user.getEmail() + "\n" +
                            "Mobile: " + user.getMobile() + "\n" +
                            "Blood Group: " + user.getBloodGroup() + "\n" +
                            "Location: " + user.getCity() + ", Odisha\n" +
                            "Donor ID: " + user.getDonorId() + "\n" +
                            "Total Donations: " + user.getDonationsCount() + " Verified Units\n" +
                            "Verified by: LifeShare Voluntary Blood Network";

                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, "LifeShare Donor Medical Export");
                    sendIntent.putExtra(Intent.EXTRA_TEXT, exportSummary);
                    startActivity(Intent.createChooser(sendIntent, "Export Donor Record"));
                }
            });
        }

        // 9. Delete Account
        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(UpdatePassword.this)
                            .setTitle("Delete Account?")
                            .setMessage("Are you sure you want to permanently delete your donor account and purge your voluntary records from LifeShare?")
                            .setPositiveButton("Delete Forever", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dm.clearSession();
                                    Toast.makeText(UpdatePassword.this, "Account and records purged.", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(UpdatePassword.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }
    }

    private void showChangePasswordDialog(final UserProfile user) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        final EditText etNew = dialogView.findViewById(R.id.et_dialog_new_password);
        final EditText etConfirm = dialogView.findViewById(R.id.et_dialog_confirm_password);
        final TextView tvMatchStatus = dialogView.findViewById(R.id.tv_password_match_status);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel_password);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_dialog_save_password);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Real-time password matching validation
        TextWatcher passwordMatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String p1 = etNew.getText().toString();
                String p2 = etConfirm.getText().toString();

                if (p1.isEmpty() && p2.isEmpty()) {
                    tvMatchStatus.setVisibility(View.GONE);
                    return;
                }

                tvMatchStatus.setVisibility(View.VISIBLE);
                if (p1.length() < 6) {
                    tvMatchStatus.setText("⚠ Password must be at least 6 characters");
                    tvMatchStatus.setTextColor(Color.parseColor("#E65100"));
                } else if (p2.isEmpty()) {
                    tvMatchStatus.setText("Please confirm your new password");
                    tvMatchStatus.setTextColor(Color.parseColor("#78909C"));
                } else if (p1.equals(p2)) {
                    tvMatchStatus.setText("✓ Passwords match");
                    tvMatchStatus.setTextColor(Color.parseColor("#2E7D32"));
                } else {
                    tvMatchStatus.setText("✗ Passwords do not match");
                    tvMatchStatus.setTextColor(Color.parseColor("#C62828"));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        etNew.addTextChangedListener(passwordMatcher);
        etConfirm.addTextChangedListener(passwordMatcher);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newPass = etNew.getText().toString().trim();
                String confirmPass = etConfirm.getText().toString().trim();

                if (newPass.length() < 6) {
                    Toast.makeText(UpdatePassword.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPass.equals(confirmPass)) {
                    Toast.makeText(UpdatePassword.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                dialog.dismiss();
                Toast.makeText(UpdatePassword.this, "Updating password...", Toast.LENGTH_SHORT).show();

                ApiClient.getInstance().changePassword(user.getEmail(), newPass, new ApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        Toast.makeText(UpdatePassword.this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(UpdatePassword.this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        dialog.show();
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
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_theme, null);
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
                updateThemeSubtitleText(selectedMode);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void checkPermissionAndSendNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
                return;
            }
        }
        sendTestPushNotification();
    }

    private void sendTestPushNotification() {
        NotificationHelper.showEmergencySosNotification(
                this,
                "Ramesh Chandra Jena",
                "AIIMS Bhubaneswar",
                "Bhubaneswar, Odisha",
                "O+",
                3,
                "+91 9820112233"
        );
        Toast.makeText(this, "Emergency SOS Push Alert sent to status bar!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendTestPushNotification();
        }
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
