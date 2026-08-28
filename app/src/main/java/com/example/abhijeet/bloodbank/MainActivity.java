package com.example.abhijeet.bloodbank;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private EditText userName, userPassword;
    private Button login;
    private MaterialButton btnDemoLogin, btnGoogleLogin, btnServerSettings;
    private ProgressBar progressBar;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_main);

        // Check if user is already logged in
        if (DataManager.getInstance(this).isLoggedIn()) {
            UserProfile user = DataManager.getInstance(this).getCurrentUser();
            if (user != null && user.isAdmin()) {
                startActivity(new Intent(MainActivity.this, AdminDashboardActivity.class));
            } else if (user != null && user.isCoordinator()) {
                startActivity(new Intent(MainActivity.this, CoordinatorVerificationActivity.class));
            } else {
                startActivity(new Intent(MainActivity.this, LogInActivity.class));
            }
            finish();
            return;
        }

        userName = findViewById(R.id.username1);
        userPassword = findViewById(R.id.pwd);
        login = findViewById(R.id.button2);
        btnDemoLogin = findViewById(R.id.btn_demo_login);
        btnGoogleLogin = findViewById(R.id.btn_google_login);
        btnServerSettings = findViewById(R.id.btn_server_settings);
        progressBar = findViewById(R.id.login_progress);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Google Sign-In Click
        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                    startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
                }
            });
        }

        // Server Settings Dialog
        if (btnServerSettings != null) {
            btnServerSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showServerSettingsDialog();
                }
            });
        }

        // Quick Demo Login (Skip Auth)
        if (btnDemoLogin != null) {
            btnDemoLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DataManager.getInstance(MainActivity.this).loginWithGoogleMock("Abhijeet Pradhan", "abhijeet@example.com", new DataManager.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(MainActivity.this, "Welcome to LifeShare Demo Mode!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(MainActivity.this, LogInActivity.class));
                            finish();
                        }

                        @Override
                        public void onError(String message) {}
                    });
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleGoogleSignInResult(task);
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                final String name = account.getDisplayName() != null ? account.getDisplayName() : "Google Donor";
                final String email = account.getEmail();
                final String googleId = account.getId();

                setLoading(true);
                ApiClient.getInstance().googleLogin(name, email, googleId, new ApiClient.ApiCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile user) {
                        setLoading(false);
                        DataManager.getInstance(MainActivity.this).saveCurrentUser(user);
                        Toast.makeText(MainActivity.this, "Signed in as " + user.getName() + " with Google!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MainActivity.this, LogInActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        Toast.makeText(MainActivity.this, "Google Sign-In failed: " + (errorMessage != null ? errorMessage : "Unable to verify Google account with server"), Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (ApiException e) {
            // In case Google Play Services cancelled or not configured on device
            Toast.makeText(this, "Google Sign-In: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showServerSettingsDialog() {
        final EditText input = new EditText(this);
        input.setText(ApiClient.getInstance().getBaseUrl());
        input.setHint("http://172.28.183.190:5000/api");
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Backend Server URL")
                .setMessage("Enter the REST API endpoint of your Node.js + MongoDB backend:\n• Wi-Fi Local IP: http://172.28.183.190:5000/api\n• Android Emulator: http://10.0.2.2:5000/api")
                .setView(input)
                .setPositiveButton("Save URL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newUrl = input.getText().toString().trim();
                        if (!newUrl.isEmpty()) {
                            ApiClient.getInstance().saveBaseUrl(MainActivity.this, newUrl);
                            Toast.makeText(MainActivity.this, "Server URL updated to: " + newUrl, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.this, "Pinging server...", Toast.LENGTH_SHORT).show();
                        ApiClient.getInstance().checkHealth(new ApiClient.ApiCallback<String>() {
                            @Override
                            public void onSuccess(String result) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Connection Successful")
                                        .setMessage(result + "\nYour MongoDB Atlas database is LIVE and ready!")
                                        .setPositiveButton("OK", null)
                                        .show();
                            }

                            @Override
                            public void onError(String error) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Connection Failed")
                                        .setMessage("Could not connect to: " + ApiClient.getInstance().getBaseUrl() + "\n\nReason: " + error + "\n\nMake sure your PC and phone are on the same Wi-Fi network.")
                                        .setPositiveButton("OK", null)
                                        .show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void logInActivity(View view) {
        final String email = userName.getText().toString().trim();
        final String password = userPassword.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter your password", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // Connect to MongoDB Backend
        ApiClient.getInstance().login(email, password, new ApiClient.ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile user) {
                setLoading(false);
                if (ApiClient.getInstance().getAuthToken() != null) {
                    DataManager.getInstance(MainActivity.this).saveAuthToken(ApiClient.getInstance().getAuthToken());
                }
                DataManager.getInstance(MainActivity.this).saveCurrentUser(user);
                Toast.makeText(MainActivity.this, "Login Successful! Welcome " + user.getName(), Toast.LENGTH_SHORT).show();
                if (user.isAdmin()) {
                    startActivity(new Intent(MainActivity.this, AdminDashboardActivity.class));
                } else if (user.isCoordinator()) {
                    startActivity(new Intent(MainActivity.this, CoordinatorVerificationActivity.class));
                } else {
                    startActivity(new Intent(MainActivity.this, LogInActivity.class));
                }
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                Toast.makeText(MainActivity.this, errorMessage != null ? errorMessage : "Invalid email or password. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (login != null) {
            login.setEnabled(!loading);
        }
    }

    public void signUpActivity(View view) {
        startActivity(new Intent(MainActivity.this, SignUpActivity.class));
    }

    public void ForgotPassword(View view) {
        startActivity(new Intent(MainActivity.this, ForgotPassword.class));
    }
}
