package com.example.abhijeet.bloodbank;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

public class SplashActivity extends AppCompatActivity {

    private static final int NOTIF_REQ_CODE = 501;
    private boolean isAnimationFinished = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        NotificationHelper.createNotificationChannels(this);

        View cardLogo = findViewById(R.id.card_splash_logo);
        View glowView = findViewById(R.id.view_splash_glow);
        TextView tvTitle = findViewById(R.id.tv_splash_title);
        TextView tvSubtitle = findViewById(R.id.tv_splash_subtitle);

        Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.splash_pulse);
        Animation fadeInAnim = AnimationUtils.loadAnimation(this, R.anim.splash_fade_in);

        if (cardLogo != null) cardLogo.startAnimation(pulseAnim);
        if (glowView != null) glowView.startAnimation(pulseAnim);
        if (tvTitle != null) tvTitle.startAnimation(fadeInAnim);
        if (tvSubtitle != null) tvSubtitle.startAnimation(fadeInAnim);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                isAnimationFinished = true;
                checkNotificationPermissionAndProceed();
            }
        }, 1600);
    }

    private void checkNotificationPermissionAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                showNotificationPermissionDialog();
                return;
            }
        }
        proceedToNextScreen();
    }

    private void showNotificationPermissionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notification_permission, null);
        MaterialButton btnEnable = dialogView.findViewById(R.id.btn_enable_notifs);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnEnable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                            SplashActivity.this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIF_REQ_CODE
                    );
                }
            }
        });

        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Emergency blood alerts enabled!", Toast.LENGTH_SHORT).show();
                proceedToNextScreen();
            } else {
                Toast.makeText(this, "Notification permission is required to receive live blood alerts in Odisha.", Toast.LENGTH_LONG).show();
                // Prompt again so user enables it
                showNotificationPermissionDialog();
            }
        }
    }

    private void proceedToNextScreen() {
        boolean isLoggedIn = DataManager.getInstance(SplashActivity.this).isLoggedIn();
        if (!isLoggedIn) {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        } else {
            UserProfile user = DataManager.getInstance(SplashActivity.this).getCurrentUser();
            if (user != null && user.isAdmin()) {
                startActivity(new Intent(SplashActivity.this, AdminDashboardActivity.class));
            } else if (user != null && user.isCoordinator()) {
                startActivity(new Intent(SplashActivity.this, CoordinatorVerificationActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, LogInActivity.class));
            }
        }
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
