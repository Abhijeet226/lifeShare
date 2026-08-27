package com.example.abhijeet.bloodbank;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UpdateProfile extends AppCompatActivity {

    private EditText etFirstName, etLastName, etDob, etEmail, etMobile, spinnerBloodGroup, spinnerCity;
    private AutoCompleteTextView spinnerGender;
    private View btnPickDob, layoutBloodGroup, layoutCity;
    private MaterialButton save;
    private ImageView updateProfilePic, btnEditMobileToggle;
    private ProgressBar progressBar;
    private TextView btnVerifyPhone, btnVerifyEmail;
    private static final int PICK_IMAGE = 123;
    private Uri imagePath;

    private String originalPhoneDigits = "";
    private String originalEmail = "";
    private String selectedCityId = null;
    private boolean isPhoneVerified = false;
    private boolean isEmailVerified = false;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imagePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imagePath);
                updateProfilePic.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_update_profile);

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
            tvTitle.setText("Personal Information");
        }

        etFirstName = findViewById(R.id.etFirstNameUpdate);
        etLastName = findViewById(R.id.etLastNameUpdate);
        spinnerGender = findViewById(R.id.spinnerGenderUpdate);
        btnPickDob = findViewById(R.id.btnPickDobUpdate);
        etDob = findViewById(R.id.etDobUpdate);
        etEmail = findViewById(R.id.etEmailUpdate);
        etMobile = findViewById(R.id.etMobileUpdate);
        btnEditMobileToggle = findViewById(R.id.btn_edit_mobile_toggle);
        btnVerifyPhone = findViewById(R.id.btn_update_verify_phone);
        btnVerifyEmail = findViewById(R.id.btn_update_verify_email);
        spinnerBloodGroup = findViewById(R.id.spinner_blood_group_update);
        spinnerCity = findViewById(R.id.spinnerCityUpdate);
        layoutBloodGroup = findViewById(R.id.layout_blood_group_update);
        layoutCity = findViewById(R.id.layout_city_update);
        save = findViewById(R.id.btnSave);
        updateProfilePic = findViewById(R.id.ivProfileUpdate);
        progressBar = findViewById(R.id.pb_update_loading);

        setupDropdowns();
        setupDatePicker();
        populateFields();

        // Custom Dialog Triggers for Blood Group
        View.OnClickListener bgClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectBloodGroupDialog();
            }
        };
        spinnerBloodGroup.setOnClickListener(bgClickListener);
        if (layoutBloodGroup != null) layoutBloodGroup.setOnClickListener(bgClickListener);

        // Custom Dialog Triggers for Odisha City
        View.OnClickListener cityClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectCityDialog();
            }
        };
        spinnerCity.setOnClickListener(cityClickListener);
        if (layoutCity != null) layoutCity.setOnClickListener(cityClickListener);

        // 1. Pencil Icon click enables editing and opens keyboard
        if (btnEditMobileToggle != null) {
            btnEditMobileToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etMobile.setEnabled(true);
                    etMobile.setFocusable(true);
                    etMobile.setFocusableInTouchMode(true);
                    etMobile.setClickable(true);
                    etMobile.setCursorVisible(true);
                    etMobile.requestFocus();
                    if (etMobile.getText() != null) {
                        etMobile.setSelection(etMobile.getText().length());
                    }
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(etMobile, InputMethodManager.SHOW_IMPLICIT);
                    }
                    Toast.makeText(UpdateProfile.this, "Editing mobile number enabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 2. Real-time phone changes watcher
        etMobile.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String currentDigits = s.toString().replaceAll("[^0-9]", "").trim();
                if (!currentDigits.equals(originalPhoneDigits)) {
                    isPhoneVerified = false;
                    btnVerifyPhone.setVisibility(View.VISIBLE);
                    btnVerifyPhone.setText("Verify");
                    btnVerifyPhone.setTextColor(getResources().getColor(R.color.colorPrimary));
                } else {
                    UserProfile u = DataManager.getInstance(UpdateProfile.this).getCurrentUser();
                    if (u != null && u.isPhoneVerified()) {
                        isPhoneVerified = true;
                        btnVerifyPhone.setVisibility(View.GONE);
                    }
                }
            }
        });

        // 3. Real-time email changes watcher
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String current = s.toString().trim();
                if (!current.equalsIgnoreCase(originalEmail)) {
                    isEmailVerified = false;
                    btnVerifyEmail.setVisibility(View.VISIBLE);
                    btnVerifyEmail.setText("Verify");
                    btnVerifyEmail.setTextColor(getResources().getColor(R.color.colorPrimary));
                } else {
                    UserProfile u = DataManager.getInstance(UpdateProfile.this).getCurrentUser();
                    if (u != null && u.isEmailVerified()) {
                        isEmailVerified = true;
                        btnVerifyEmail.setVisibility(View.GONE);
                    }
                }
            }
        });

        if (btnVerifyPhone != null) {
            btnVerifyPhone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isPhoneVerified) {
                        Toast.makeText(UpdateProfile.this, "Mobile number is already verified!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    initiatePhoneVerification();
                }
            });
        }

        if (btnVerifyEmail != null) {
            btnVerifyEmail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isEmailVerified) {
                        Toast.makeText(UpdateProfile.this, "Email address is already verified!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    initiateEmailVerification();
                }
            });
        }

        updateProfilePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Profile Picture"), PICK_IMAGE);
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String firstName = etFirstName.getText().toString().trim();
                String lastName = etLastName.getText().toString().trim();
                String dob = etDob.getText().toString().trim();
                String gender = spinnerGender.getText().toString().trim();
                final String email = etEmail.getText().toString().trim();
                String digits = etMobile.getText().toString().replaceAll("[^0-9]", "").trim();
                if (digits.startsWith("91") && digits.length() == 12) {
                    digits = digits.substring(2);
                }

                if (!digits.matches("^[6-9]\\d{9}$")) {
                    etMobile.setError("Enter valid 10-digit Indian mobile number");
                    etMobile.requestFocus();
                    Toast.makeText(UpdateProfile.this, "Please enter a valid 10-digit Indian mobile number (starting with 6, 7, 8, 9)", Toast.LENGTH_LONG).show();
                    return;
                }
                final String fullMobile = "+91 " + digits;
                String bloodGroup = spinnerBloodGroup.getText().toString().trim();
                String enteredCity = spinnerCity.getText().toString().trim();

                if (gender.isEmpty()) gender = "Male";
                if (bloodGroup.isEmpty()) bloodGroup = "O+";

                if (enteredCity.isEmpty()) {
                    spinnerCity.setError("Please select your city");
                    spinnerCity.requestFocus();
                    Toast.makeText(UpdateProfile.this, "Please select your city from the list", Toast.LENGTH_LONG).show();
                    return;
                }

                final String finalCity = enteredCity;
                final String finalGender = gender;
                final String finalBloodGroup = bloodGroup;

                if (firstName.isEmpty() || email.isEmpty() || digits.isEmpty()) {
                    Toast.makeText(UpdateProfile.this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                // If phone was changed and is not verified yet, prompt user
                if (!digits.equals(originalPhoneDigits) && !isPhoneVerified) {
                    new AlertDialog.Builder(UpdateProfile.this)
                            .setTitle("Verify New Number?")
                            .setMessage("Your new phone number (" + fullMobile + ") has not been verified yet with OTP. Would you like to verify it now?")
                            .setPositiveButton("Verify Now", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    initiatePhoneVerification();
                                }
                            })
                            .setNegativeButton("Save Anyway", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    proceedWithSave(firstName, lastName, dob, finalGender, email, fullMobile, finalBloodGroup, finalCity);
                                }
                            })
                            .show();
                    return;
                }

                proceedWithSave(firstName, lastName, dob, finalGender, email, fullMobile, finalBloodGroup, finalCity);
            }
        });
    }

    private void proceedWithSave(String firstName, String lastName, String dob, String gender, String email, String fullMobile, String bloodGroup, String city) {
        setLoading(true);

        UserProfile existing = DataManager.getInstance(UpdateProfile.this).getCurrentUser();
        final UserProfile userProfile = (existing != null) ? existing : new UserProfile();

        userProfile.setName((firstName + " " + lastName).trim());
        userProfile.setFirstName(firstName);
        userProfile.setLastName(lastName);
        userProfile.setDob(dob);
        userProfile.setGender(gender);
        userProfile.setEmail(email);
        userProfile.setMobile(fullMobile);
        userProfile.setBloodGroup(bloodGroup);
        userProfile.setCity(city);
        if (selectedCityId != null && !selectedCityId.isEmpty()) {
            userProfile.setCityId(selectedCityId);
        }
        userProfile.setPhoneVerified(isPhoneVerified);
        userProfile.setEmailVerified(isEmailVerified);

        ApiClient.getInstance().updateProfile(userProfile, new ApiClient.ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile result) {
                setLoading(false);
                DataManager.getInstance(UpdateProfile.this).saveCurrentUser(result);
                Toast.makeText(UpdateProfile.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                DataManager.getInstance(UpdateProfile.this).saveCurrentUser(userProfile);
                Toast.makeText(UpdateProfile.this, "Profile Saved Locally", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void initiatePhoneVerification() {
        if (isPhoneVerified) {
            Toast.makeText(this, "Mobile number is already verified!", Toast.LENGTH_SHORT).show();
            return;
        }
        String digits = etMobile.getText().toString().replaceAll("[^0-9]", "").trim();
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }

        if (!digits.matches("^[6-9]\\d{9}$")) {
            etMobile.setError("Enter valid 10-digit Indian mobile number");
            etMobile.requestFocus();
            Toast.makeText(this, "Please enter a valid 10-digit Indian mobile number (starting with 6, 7, 8, 9)", Toast.LENGTH_SHORT).show();
            return;
        }
        final String finalDigits = digits;
        final String targetPhone = "+91 " + finalDigits;

        Toast.makeText(this, "Sending OTP to " + targetPhone + "...", Toast.LENGTH_SHORT).show();
        ApiClient.getInstance().sendVerificationOtp("PHONE", targetPhone, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(UpdateProfile.this, message, Toast.LENGTH_LONG).show();

                final EditText input = new EditText(UpdateProfile.this);
                input.setHint("Enter 6-digit OTP");
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setPadding(40, 30, 40, 30);

                new AlertDialog.Builder(UpdateProfile.this)
                        .setTitle("Verify Mobile Number")
                        .setMessage("Enter the 6-digit verification code sent to " + targetPhone)
                        .setView(input)
                        .setPositiveButton("Verify", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String otp = input.getText().toString().trim();
                                if (otp.length() < 4) {
                                    Toast.makeText(UpdateProfile.this, "Please enter a valid OTP", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                verifyPhoneOtp(targetPhone, otp, finalDigits);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(UpdateProfile.this, "Failed to send OTP: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verifyPhoneOtp(final String targetPhone, String otp, final String digits) {
        setLoading(true);
        ApiClient.getInstance().verifyAccountOtp("PHONE", otp, targetPhone, new ApiClient.ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile updatedUser) {
                setLoading(false);
                isPhoneVerified = true;
                originalPhoneDigits = digits;
                etMobile.setText(digits);
                btnVerifyPhone.setText("Verified");
                btnVerifyPhone.setTextColor(getResources().getColor(R.color.status_available));
                DataManager.getInstance(UpdateProfile.this).saveCurrentUser(updatedUser);
                Toast.makeText(UpdateProfile.this, "Phone number verified successfully!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(UpdateProfile.this, "Verification failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void initiateEmailVerification() {
        if (isEmailVerified) {
            Toast.makeText(this, "Email address is already verified!", Toast.LENGTH_SHORT).show();
            return;
        }
        final String targetEmail = etEmail.getText().toString().trim();
        if (targetEmail.isEmpty() || !targetEmail.contains("@")) {
            Toast.makeText(this, "Please enter a valid email address first", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Sending OTP to " + targetEmail + "...", Toast.LENGTH_SHORT).show();
        ApiClient.getInstance().sendVerificationOtp("EMAIL", targetEmail, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(UpdateProfile.this, message, Toast.LENGTH_LONG).show();

                final EditText input = new EditText(UpdateProfile.this);
                input.setHint("Enter 6-digit OTP");
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.setPadding(40, 30, 40, 30);

                new AlertDialog.Builder(UpdateProfile.this)
                        .setTitle("Verify Email Address")
                        .setMessage("Enter the 6-digit verification code sent to " + targetEmail)
                        .setView(input)
                        .setPositiveButton("Verify", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String otp = input.getText().toString().trim();
                                if (otp.length() < 4) {
                                    Toast.makeText(UpdateProfile.this, "Please enter a valid OTP", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                verifyEmailOtp(targetEmail, otp);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(UpdateProfile.this, "Failed to send OTP: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verifyEmailOtp(final String targetEmail, String otp) {
        setLoading(true);
        ApiClient.getInstance().verifyAccountOtp("EMAIL", otp, targetEmail, new ApiClient.ApiCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile updatedUser) {
                setLoading(false);
                isEmailVerified = true;
                originalEmail = targetEmail;
                etEmail.setText(targetEmail);
                btnVerifyEmail.setText("Verified");
                btnVerifyEmail.setTextColor(getResources().getColor(R.color.status_available));
                DataManager.getInstance(UpdateProfile.this).saveCurrentUser(updatedUser);
                Toast.makeText(UpdateProfile.this, "Email verified successfully!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(UpdateProfile.this, "Verification failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupDatePicker() {
        View.OnClickListener dateClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatePickerDialog datePickerDialog = new DatePickerDialog(UpdateProfile.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int selectedYear, int selectedMonth, int selectedDay) {
                                String formatted = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                                etDob.setText(formatted);
                            }
                        }, 2000, 0, 1);
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

    private void setupDropdowns() {
        String[] genders = getResources().getStringArray(R.array.gender_options);
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown_popup, genders);
        spinnerGender.setAdapter(genderAdapter);
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

        String currentBg = spinnerBloodGroup.getText().toString().trim();
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
                    spinnerBloodGroup.setText(rb.getTag().toString());
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
                        spinnerCity.setText(city.getName());
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

    public static class CityViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName;
        public TextView tvSubtitle;

        public CityViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_city_name);
            tvSubtitle = itemView.findViewById(R.id.tv_item_city_subtitle);
        }
    }

    private void populateFields() {
        UserProfile user = DataManager.getInstance(this).getCurrentUser();
        if (user != null) {
            selectedCityId = user.getCityId();
            etFirstName.setText(user.getFirstName());
            etLastName.setText(user.getLastName());
            etDob.setText(user.getDob());
            etEmail.setText(user.getEmail());

            // Extract 10 digits for mobile field
            String rawMobile = user.getMobile() != null ? user.getMobile() : "";
            originalPhoneDigits = rawMobile.replaceAll("[^0-9]", "");
            if (originalPhoneDigits.startsWith("91") && originalPhoneDigits.length() > 10) {
                originalPhoneDigits = originalPhoneDigits.substring(2);
            }
            etMobile.setText(originalPhoneDigits);

            if (etEmail != null) {
                etEmail.setEnabled(false);
                etEmail.setFocusable(false);
                etEmail.setClickable(false);
            }
            if (etMobile != null) {
                etMobile.setEnabled(false);
                etMobile.setFocusable(false);
                etMobile.setFocusableInTouchMode(false);
            }

            originalEmail = user.getEmail() != null ? user.getEmail() : "";
            isPhoneVerified = user.isPhoneVerified();
            isEmailVerified = user.isEmailVerified();

            if (btnVerifyPhone != null) {
                if (isPhoneVerified) {
                    btnVerifyPhone.setVisibility(View.GONE);
                } else {
                    btnVerifyPhone.setVisibility(View.VISIBLE);
                    btnVerifyPhone.setText("Verify");
                    btnVerifyPhone.setTextColor(getResources().getColor(R.color.colorPrimary));
                }
            }

            if (btnVerifyEmail != null) {
                if (isEmailVerified) {
                    btnVerifyEmail.setVisibility(View.GONE);
                } else {
                    btnVerifyEmail.setVisibility(View.VISIBLE);
                    btnVerifyEmail.setText("Verify");
                    btnVerifyEmail.setTextColor(getResources().getColor(R.color.colorPrimary));
                }
            }

            // Gender selection
            spinnerGender.setText(user.getGender() != null && !user.getGender().isEmpty() ? user.getGender() : "Male", false);

            // Blood group selection
            spinnerBloodGroup.setText(user.getBloodGroup() != null && !user.getBloodGroup().isEmpty() ? user.getBloodGroup() : "O+");

            // Odisha city selection
            spinnerCity.setText(user.getCity() != null && !user.getCity().isEmpty() ? user.getCity() : "Bhubaneswar");
        }
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        save.setEnabled(!loading);
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