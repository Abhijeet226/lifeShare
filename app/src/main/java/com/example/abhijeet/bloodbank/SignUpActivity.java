package com.example.abhijeet.bloodbank;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SignUpActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_UP = 9002;

    // Development Flag: Set to true to enforce mandatory live OTP verification before proceeding;
    // Set to false for rapid development/testing.
    public static boolean REQUIRE_OTP_VERIFICATION = false;

    private LinearLayout layoutStep1, layoutStep2, layoutStep3;
    private ProgressBar pbSteps, pbLoading;
    private TextView tvStepLabel, tvStepTitle;
    private MaterialButton btnNext, btnBack, btnGoogleSignup;
    private GoogleSignInClient mGoogleSignInClient;

    // Step 1 Fields
    private EditText etFirstName, etLastName, etDob, etEmail;
    private Spinner spinnerGender;
    private FrameLayout btnPickDob;
    private TextView btnVerifyEmail;

    // Step 2 Fields
    private EditText etBloodGroup, etCity, etPhone;
    private FrameLayout btnPickBloodGroup, btnPickCity;
    private TextView btnVerifyMobile;
    private String selectedCityId = "";

    // Step 3 Fields
    private EditText etPassword, etConfirmPassword;
    private LinearLayout layoutPasswordMatch;
    private android.widget.ImageView ivPasswordMatch;
    private TextView tvPasswordMatch;
    private SwitchMaterial switchDonorPledge;

    // Verification States
    private boolean isEmailVerified = false;
    private boolean isPhoneVerified = false;
    private String lastVerifiedEmail = "";
    private String lastVerifiedMobile = "";
    private String verifiedOtp = null;

    private int currentStep = 1;
    private final int TOTAL_STEPS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_sign_up);

        View topBack = findViewById(R.id.btn_back);
        if (topBack != null) {
            topBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        TextView tvHeader = findViewById(R.id.tv_header_title);
        if (tvHeader != null) {
            tvHeader.setText("Create Account");
        }

        initViews();
        setupPickers();
        setupDatePicker();
        setupLiveWatchers();
        updateStepUI();

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Step Next Button
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validateCurrentStep()) {
                    if (currentStep < TOTAL_STEPS) {
                        currentStep++;
                        updateStepUI();
                    } else {
                        performRegistration();
                    }
                }
            }
        });

        // Step Back Button
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentStep > 1) {
                    currentStep--;
                    updateStepUI();
                } else {
                    finish();
                }
            }
        });

        // Google Sign-In
        btnGoogleSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_GOOGLE_SIGN_UP);
            }
        });
    }

    private void initViews() {
        layoutStep1 = findViewById(R.id.layout_step_1);
        layoutStep2 = findViewById(R.id.layout_step_2);
        layoutStep3 = findViewById(R.id.layout_step_3);
        pbSteps = findViewById(R.id.pb_signup_steps);
        pbLoading = findViewById(R.id.signup_progress);
        tvStepLabel = findViewById(R.id.tv_step_label);
        tvStepTitle = findViewById(R.id.tv_step_title);
        btnNext = findViewById(R.id.btn_wizard_next);
        btnBack = findViewById(R.id.btn_wizard_back);
        btnGoogleSignup = findViewById(R.id.btn_google_signup);

        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        spinnerGender = findViewById(R.id.spinner_gender);
        btnPickDob = findViewById(R.id.btn_pick_dob);
        etDob = findViewById(R.id.et_dob);
        etEmail = findViewById(R.id.email);
        btnVerifyEmail = findViewById(R.id.btn_signup_verify_email);

        etBloodGroup = findViewById(R.id.et_signup_blood_group);
        btnPickBloodGroup = findViewById(R.id.btn_pick_blood_group);
        etPhone = findViewById(R.id.mobile);
        btnVerifyMobile = findViewById(R.id.btn_signup_verify_mobile);
        etCity = findViewById(R.id.et_signup_city);
        btnPickCity = findViewById(R.id.btn_pick_city);

        etPassword = findViewById(R.id.password);
        etConfirmPassword = findViewById(R.id.confirm);
        layoutPasswordMatch = findViewById(R.id.layout_password_match_status);
        ivPasswordMatch = findViewById(R.id.iv_password_match_icon);
        tvPasswordMatch = findViewById(R.id.tv_password_match_status);
        switchDonorPledge = findViewById(R.id.switch_pledge);
    }

    private void setupLiveWatchers() {
        // Real-time Live Password Match
        TextWatcher passwordWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordMatchUI();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        if (etPassword != null) etPassword.addTextChangedListener(passwordWatcher);
        if (etConfirmPassword != null) etConfirmPassword.addTextChangedListener(passwordWatcher);

        // Email changes reset verification status
        if (etEmail != null) {
            etEmail.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String current = s.toString().trim();
                    if (!current.equalsIgnoreCase(lastVerifiedEmail)) {
                        isEmailVerified = false;
                        if (btnVerifyEmail != null) {
                            btnVerifyEmail.setText("Verify OTP");
                            btnVerifyEmail.setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.colorPrimary));
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Phone changes reset verification status
        if (etPhone != null) {
            etPhone.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String current = s.toString().replaceAll("[^0-9]", "").trim();
                    if (!current.equals(lastVerifiedMobile)) {
                        isPhoneVerified = false;
                        if (btnVerifyMobile != null) {
                            btnVerifyMobile.setText("Verify OTP");
                            btnVerifyMobile.setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.colorPrimary));
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Verify Email button trigger
        if (btnVerifyEmail != null) {
            btnVerifyEmail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String email = etEmail.getText().toString().trim();
                    if (!validateEmailFormat(email)) return;
                    showOtpVerificationDialog(email, "EMAIL", btnVerifyEmail);
                }
            });
        }

        // Verify Mobile button trigger
        if (btnVerifyMobile != null) {
            btnVerifyMobile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String rawPhone = etPhone.getText().toString().trim();
                    String digits = extractIndianMobileDigits(rawPhone);
                    if (digits == null) {
                        etPhone.setError("Enter valid 10-digit Indian mobile number (starts with 6, 7, 8, 9)");
                        etPhone.requestFocus();
                        Toast.makeText(SignUpActivity.this, "Please enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9", Toast.LENGTH_LONG).show();
                        return;
                    }
                    showOtpVerificationDialog("+91 " + digits, "PHONE", btnVerifyMobile);
                }
            });
        }
    }

    private void updatePasswordMatchUI() {
        if (layoutPasswordMatch == null || ivPasswordMatch == null || tvPasswordMatch == null) return;
        String p1 = etPassword != null ? etPassword.getText().toString() : "";
        String p2 = etConfirmPassword != null ? etConfirmPassword.getText().toString() : "";

        if (p1.isEmpty() || p2.isEmpty()) {
            layoutPasswordMatch.setVisibility(View.GONE);
        } else if (p1.equals(p2)) {
            layoutPasswordMatch.setVisibility(View.VISIBLE);
            ivPasswordMatch.setImageResource(R.drawable.ic_verified_check);
            ivPasswordMatch.setColorFilter(ContextCompat.getColor(this, R.color.status_available));
            tvPasswordMatch.setText("Passwords match");
            tvPasswordMatch.setTextColor(ContextCompat.getColor(this, R.color.status_available));
        } else {
            layoutPasswordMatch.setVisibility(View.VISIBLE);
            ivPasswordMatch.setImageResource(R.drawable.ic_about);
            ivPasswordMatch.setColorFilter(ContextCompat.getColor(this, R.color.status_busy));
            tvPasswordMatch.setText("Passwords do not match");
            tvPasswordMatch.setTextColor(ContextCompat.getColor(this, R.color.status_busy));
        }
    }

    private void setupPickers() {
        View.OnClickListener bgClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectBloodGroupDialog();
            }
        };
        if (btnPickBloodGroup != null) btnPickBloodGroup.setOnClickListener(bgClickListener);
        if (etBloodGroup != null) etBloodGroup.setOnClickListener(bgClickListener);

        View.OnClickListener cityClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectCityDialog();
            }
        };
        if (btnPickCity != null) btnPickCity.setOnClickListener(cityClickListener);
        if (etCity != null) etCity.setOnClickListener(cityClickListener);
    }

    private void showSelectBloodGroupDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_blood_group, null);
        RadioGroup rg = dialogView.findViewById(R.id.rg_dialog_blood_group);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_bg_cancel);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        String currentBg = etBloodGroup.getText().toString().trim();
        for (int i = 0; i < rg.getChildCount(); i++) {
            View child = rg.getChildAt(i);
            if (child instanceof RadioButton) {
                RadioButton rb = (RadioButton) child;
                if (rb.getTag() != null && rb.getTag().toString().equalsIgnoreCase(currentBg)) {
                    rb.setChecked(true);
                    break;
                }
            }
        }

        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton rb = group.findViewById(checkedId);
                if (rb != null && rb.getTag() != null) {
                    etBloodGroup.setText(rb.getTag().toString());
                }
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showSelectCityDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_city, null);
        RecyclerView rvCities = dialogView.findViewById(R.id.recycler_dialog_cities);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_close_cities);
        ProgressBar pbCities = dialogView.findViewById(R.id.pb_dialog_cities);
        TextView tvEmpty = dialogView.findViewById(R.id.tv_dialog_cities_empty);
        EditText etSearch = dialogView.findViewById(R.id.et_dialog_city_search);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        final List<ApiClient.CityModel> allCities = new ArrayList<>();
        final List<ApiClient.CityModel> displayCities = new ArrayList<>();

        rvCities.setLayoutManager(new LinearLayoutManager(this));
        final RecyclerView.Adapter<CityViewHolder> adapter = new RecyclerView.Adapter<CityViewHolder>() {
            @NonNull
            @Override
            public CityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_city_selection, parent, false);
                return new CityViewHolder(itemView);
            }

            @Override
            public void onBindViewHolder(@NonNull CityViewHolder holder, int position) {
                final ApiClient.CityModel city = displayCities.get(position);
                holder.tvName.setText(city.getName());
                holder.tvSubtitle.setText(city.getStateName() != null ? city.getStateName() + ", India" : "Odisha, India");

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectedCityId = city.getId();
                        etCity.setText(city.getName());
                        dialog.dismiss();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return displayCities.size();
            }
        };
        rvCities.setAdapter(adapter);

        ApiClient.getInstance().getCities(new ApiClient.ApiCallback<List<ApiClient.CityModel>>() {
            @Override
            public void onSuccess(final List<ApiClient.CityModel> cities) {
                pbCities.setVisibility(View.GONE);
                if (cities == null || cities.isEmpty()) {
                    tvEmpty.setText("No canonical cities found in database");
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvCities.setVisibility(View.GONE);
                    return;
                }

                allCities.clear();
                allCities.addAll(cities);
                displayCities.clear();
                displayCities.addAll(cities);

                tvEmpty.setVisibility(View.GONE);
                rvCities.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                pbCities.setVisibility(View.GONE);
                tvEmpty.setText("Failed to load cities: " + error);
                tvEmpty.setVisibility(View.VISIBLE);
                rvCities.setVisibility(View.GONE);
            }
        });

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s.toString().trim().toLowerCase();
                    displayCities.clear();
                    if (query.isEmpty()) {
                        displayCities.addAll(allCities);
                    } else {
                        for (ApiClient.CityModel c : allCities) {
                            if (c.getName() != null && c.getName().toLowerCase().contains(query)) {
                                displayCities.add(c);
                            }
                        }
                    }
                    if (displayCities.isEmpty() && !allCities.isEmpty()) {
                        tvEmpty.setText("No cities matching \"" + s + "\"");
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvCities.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        rvCities.setVisibility(View.VISIBLE);
                    }
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        dialog.show();
    }

    private boolean validateEmailFormat(String email) {
        if (email == null || email.isEmpty()) {
            etEmail.setError("Please enter your email address");
            etEmail.requestFocus();
            Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() || !email.contains(".")) {
            etEmail.setError("Please enter a valid email address (e.g. name@example.com)");
            etEmail.requestFocus();
            Toast.makeText(this, "Please enter a valid email address format (e.g. name@example.com)", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String extractIndianMobileDigits(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "").trim();
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        if (digits.matches("^[6-9]\\d{9}$")) {
            return digits;
        }
        return null;
    }

    private void showOtpVerificationDialog(final String identifier, final String type, final TextView badgeView) {
        Toast.makeText(this, "Sending OTP to " + identifier + "...", Toast.LENGTH_SHORT).show();
        ApiClient.getInstance().sendSignupOtp(identifier, type, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();

                final EditText input = new EditText(SignUpActivity.this);
                input.setHint("Enter 6-digit OTP");
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                input.setPadding(50, 40, 50, 40);

                new AlertDialog.Builder(SignUpActivity.this)
                        .setTitle("Verify " + (type.equals("EMAIL") ? "Email Address" : "Mobile Number"))
                        .setMessage("Please enter the 6-digit OTP sent to " + identifier + (message.contains("Simulated") ? "\n\n(" + message + ")" : ""))
                        .setView(input)
                        .setPositiveButton("Verify OTP", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                final String code = input.getText().toString().trim();
                                if (code.length() < 4) {
                                    Toast.makeText(SignUpActivity.this, "Please enter a valid OTP code", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                ApiClient.getInstance().verifySignupOtp(identifier, code, new ApiClient.ApiCallback<String>() {
                                    @Override
                                    public void onSuccess(String res) {
                                        verifiedOtp = code;
                                        if (type.equals("EMAIL")) {
                                            isEmailVerified = true;
                                            lastVerifiedEmail = identifier;
                                        } else {
                                            isPhoneVerified = true;
                                            lastVerifiedMobile = identifier.replaceAll("[^0-9]", "").trim();
                                        }
                                        if (badgeView != null) {
                                            badgeView.setText("✓ Verified");
                                            badgeView.setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.status_available));
                                        }
                                        Toast.makeText(SignUpActivity.this, "✓ " + (type.equals("EMAIL") ? "Email" : "Mobile") + " verified successfully!", Toast.LENGTH_LONG).show();
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Toast.makeText(SignUpActivity.this, "OTP verification failed: " + error, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SignUpActivity.this, "Failed to send OTP: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupDatePicker() {
        View.OnClickListener dateClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Minimum legal age to donate in India is 18 years; maximum is 65 years.
                Calendar maxCal = Calendar.getInstance();
                maxCal.add(Calendar.YEAR, -18);

                Calendar minCal = Calendar.getInstance();
                minCal.add(Calendar.YEAR, -65);

                int defaultYear = maxCal.get(Calendar.YEAR);
                int defaultMonth = maxCal.get(Calendar.MONTH);
                int defaultDay = maxCal.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog datePickerDialog = new DatePickerDialog(SignUpActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int selectedYear, int selectedMonth, int selectedDay) {
                                String formatted = String.format(Locale.US, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                                etDob.setText(formatted);
                            }
                        }, defaultYear, defaultMonth, defaultDay);

                datePickerDialog.getDatePicker().setMaxDate(maxCal.getTimeInMillis());
                datePickerDialog.getDatePicker().setMinDate(minCal.getTimeInMillis());
                datePickerDialog.show();
            }
        };

        if (btnPickDob != null) {
            btnPickDob.setOnClickListener(dateClickListener);
        }
        if (etDob != null) {
            etDob.setOnClickListener(dateClickListener);
        }
    }

    private void updateStepUI() {
        pbSteps.setProgress(currentStep);

        layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);

        btnBack.setVisibility(currentStep > 1 ? View.VISIBLE : View.GONE);
        btnGoogleSignup.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);

        switch (currentStep) {
            case 1:
                tvStepLabel.setText("Step 1 of 3");
                tvStepTitle.setText("Personal Details");
                btnNext.setText("Continue →");
                break;
            case 2:
                tvStepLabel.setText("Step 2 of 3");
                tvStepTitle.setText("Donor Details (Odisha)");
                btnNext.setText("Continue →");
                break;
            case 3:
                tvStepLabel.setText("Step 3 of 3");
                tvStepTitle.setText("Security & Pledge");
                btnNext.setText("Complete Sign Up");
                break;
        }
    }

    private boolean validateCurrentStep() {
        if (currentStep == 1) {
            String firstName = etFirstName.getText().toString().trim();
            if (firstName.isEmpty()) {
                etFirstName.setError("Please enter your first name");
                etFirstName.requestFocus();
                Toast.makeText(this, "Please enter your first name", Toast.LENGTH_SHORT).show();
                return false;
            }

            String dob = etDob.getText().toString().trim();
            if (dob.isEmpty()) {
                Toast.makeText(this, "Please select your Date of Birth", Toast.LENGTH_SHORT).show();
                return false;
            }

            // Legal Indian Blood Donation Age Check (18 to 65 years)
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date dobDate = sdf.parse(dob);
                if (dobDate != null) {
                    Calendar dobCal = Calendar.getInstance();
                    dobCal.setTime(dobDate);
                    Calendar today = Calendar.getInstance();
                    int age = today.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR);
                    if (today.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
                        age--;
                    }
                    if (age < 18) {
                        etDob.setError("You must be at least 18 years old to donate blood");
                        Toast.makeText(this, "You must be at least 18 years old to register as a voluntary blood donor (National Blood Transfusion Council regulations).", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    if (age > 65) {
                        etDob.setError("Maximum donation age is 65 years");
                        Toast.makeText(this, "The maximum allowable age for voluntary blood donation is 65 years.", Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
            } catch (Exception ignored) {}

            String email = etEmail.getText().toString().trim();
            if (!validateEmailFormat(email)) {
                return false;
            }

            if (REQUIRE_OTP_VERIFICATION && !isEmailVerified) {
                Toast.makeText(this, "Please verify your email address with OTP before continuing", Toast.LENGTH_LONG).show();
                showOtpVerificationDialog(email, "EMAIL", btnVerifyEmail);
                return false;
            }

        } else if (currentStep == 2) {
            String rawPhone = etPhone.getText().toString().trim();
            String digits = extractIndianMobileDigits(rawPhone);
            if (digits == null) {
                etPhone.setError("Enter valid 10-digit Indian mobile number (starts with 6, 7, 8, 9)");
                etPhone.requestFocus();
                Toast.makeText(this, "Please enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9", Toast.LENGTH_LONG).show();
                return false;
            }

            String city = etCity.getText().toString().trim();
            if (city.isEmpty()) {
                Toast.makeText(this, "Please select your city in Odisha", Toast.LENGTH_SHORT).show();
                return false;
            }

            if (REQUIRE_OTP_VERIFICATION && !isPhoneVerified) {
                Toast.makeText(this, "Please verify your mobile number with OTP before continuing", Toast.LENGTH_LONG).show();
                showOtpVerificationDialog("+91 " + digits, "PHONE", btnVerifyMobile);
                return false;
            }

        } else if (currentStep == 3) {
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();
            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                etPassword.requestFocus();
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (!password.equals(confirmPassword)) {
                etConfirmPassword.setError("Passwords do not match");
                etConfirmPassword.requestFocus();
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void performRegistration() {
        final String firstName = etFirstName.getText().toString().trim();
        final String lastName = etLastName.getText().toString().trim();
        final String dob = etDob.getText().toString().trim();
        final String gender = spinnerGender.getSelectedItem() != null ? spinnerGender.getSelectedItem().toString() : "Male";
        final String email = etEmail.getText().toString().trim();
        String rawPhone = etPhone.getText().toString().trim();
        if (!rawPhone.startsWith("+91")) {
            rawPhone = "+91 " + rawPhone;
        }
        final String phone = rawPhone;
        final String bg = etBloodGroup.getText().toString().trim().isEmpty() ? "O+" : etBloodGroup.getText().toString().trim();
        final String city = etCity.getText().toString().trim().isEmpty() ? "Bhubaneswar" : etCity.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();
        final boolean isAvailable = switchDonorPledge.isChecked();

        setLoading(true);

        // Register in MongoDB Atlas backend with canonical cityId and optional verified OTP
        ApiClient.getInstance().register(firstName, lastName, dob, gender, email, password, phone, bg, city, selectedCityId, isAvailable, verifiedOtp, new ApiClient.ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile user) {
                setLoading(false);
                if (ApiClient.getInstance().getAuthToken() != null) {
                    DataManager.getInstance(SignUpActivity.this).saveAuthToken(ApiClient.getInstance().getAuthToken());
                }
                DataManager.getInstance(SignUpActivity.this).saveCurrentUser(user);
                Toast.makeText(SignUpActivity.this, "Welcome to LifeShare, " + user.getName() + "!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(SignUpActivity.this, LogInActivity.class));
                finishAffinity();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                // Save locally and continue
                UserProfile user = new UserProfile(firstName, lastName, dob, gender, email, phone, bg, city, isAvailable);
                if (selectedCityId != null && !selectedCityId.isEmpty()) {
                    user.setCityId(selectedCityId);
                }
                DataManager.getInstance(SignUpActivity.this).saveCurrentUser(user);
                Toast.makeText(SignUpActivity.this, "Registration saved locally! Welcome " + user.getName(), Toast.LENGTH_SHORT).show();
                startActivity(new Intent(SignUpActivity.this, LogInActivity.class));
                finishAffinity();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_UP) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleGoogleSignUpResult(task);
        }
    }

    private void handleGoogleSignUpResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                final String fullName = account.getDisplayName() != null ? account.getDisplayName() : "Google Donor";
                final String email = account.getEmail();
                final String googleId = account.getId();

                setLoading(true);
                ApiClient.getInstance().googleLogin(fullName, email, googleId, new ApiClient.ApiCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile user) {
                        setLoading(false);
                        DataManager.getInstance(SignUpActivity.this).saveCurrentUser(user);
                        Toast.makeText(SignUpActivity.this, "Welcome to LifeShare, " + user.getName() + "!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignUpActivity.this, LogInActivity.class));
                        finishAffinity();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        UserProfile user = new UserProfile(fullName, email, "+91 9820112233", "O+", "Bhubaneswar", true);
                        DataManager.getInstance(SignUpActivity.this).saveCurrentUser(user);
                        Toast.makeText(SignUpActivity.this, "Welcome " + fullName + "!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignUpActivity.this, LogInActivity.class));
                        finishAffinity();
                    }
                });
            }
        } catch (ApiException e) {
            Toast.makeText(this, "Google Sign-Up: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoading(boolean loading) {
        if (pbLoading != null) {
            pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        btnNext.setEnabled(!loading);
    }

    public static class CityViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName;
        public TextView tvSubtitle;

        public CityViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_city_name);
            tvSubtitle = itemView.findViewById(R.id.tv_item_city_subtitle);
        }
    }
}
