package com.example.abhijeet.bloodbank;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

public class ForgotPassword extends AppCompatActivity {

    private EditText emailEditText, otpEditText, newPasswordEditText;
    private MaterialButton sendOtpButton, verifyOtpButton;
    private LinearLayout otpSectionLayout;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_forgot_password);

        View topBack = findViewById(R.id.btn_back);
        if (topBack != null) {
            topBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        android.widget.TextView tvHeader = findViewById(R.id.tv_header_title);
        if (tvHeader != null) {
            tvHeader.setText("Password Recovery");
        }

        emailEditText = findViewById(R.id.etPasswordEmail);
        otpEditText = findViewById(R.id.et_otp_code);
        newPasswordEditText = findViewById(R.id.et_instant_new_pwd);
        sendOtpButton = findViewById(R.id.btnPasswordReset);
        verifyOtpButton = findViewById(R.id.btn_verify_otp);
        otpSectionLayout = findViewById(R.id.layout_otp_section);
        progressBar = findViewById(R.id.pb_forgot_loading);

        // STEP 1: Request 6-digit OTP
        sendOtpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = emailEditText.getText().toString().trim();
                if (email.isEmpty()) {
                    Toast.makeText(ForgotPassword.this, "Please enter your registered email", Toast.LENGTH_SHORT).show();
                    return;
                }

                setLoading(true);
                ApiClient.getInstance().sendForgotPasswordOtp(email, new ApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String resultMessage) {
                        setLoading(false);
                        Toast.makeText(ForgotPassword.this, resultMessage, Toast.LENGTH_LONG).show();
                        otpSectionLayout.setVisibility(View.VISIBLE);
                        sendOtpButton.setText("Resend OTP Code");
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        Toast.makeText(ForgotPassword.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        // Open OTP section for manual / offline test entry
                        otpSectionLayout.setVisibility(View.VISIBLE);
                    }
                });
            }
        });

        // STEP 2: Verify OTP and Reset Password
        verifyOtpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = emailEditText.getText().toString().trim();
                final String otp = otpEditText.getText().toString().trim();
                final String newPassword = newPasswordEditText.getText().toString().trim();

                if (email.isEmpty()) {
                    Toast.makeText(ForgotPassword.this, "Please enter your email", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (otp.length() != 6) {
                    Toast.makeText(ForgotPassword.this, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (newPassword.length() < 6) {
                    Toast.makeText(ForgotPassword.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                setLoading(true);
                ApiClient.getInstance().verifyResetOtp(email, otp, newPassword, new ApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String resultMessage) {
                        setLoading(false);
                        Toast.makeText(ForgotPassword.this, resultMessage, Toast.LENGTH_LONG).show();
                        finish();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        Toast.makeText(ForgotPassword.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        sendOtpButton.setEnabled(!loading);
        verifyOtpButton.setEnabled(!loading);
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
