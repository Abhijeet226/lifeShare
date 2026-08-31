package com.example.abhijeet.bloodbank.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.abhijeet.bloodbank.ApiClient;
import com.example.abhijeet.bloodbank.DataManager;
import com.example.abhijeet.bloodbank.EmergencyDetailActivity;
import com.example.abhijeet.bloodbank.EmergencyRequest;
import com.example.abhijeet.bloodbank.LocationHelper;
import com.example.abhijeet.bloodbank.NotificationCenterActivity;
import com.example.abhijeet.bloodbank.NotificationHelper;
import com.example.abhijeet.bloodbank.R;
import com.example.abhijeet.bloodbank.UserProfile;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class EmergencyFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar pbLoading;
    private View layoutEmpty;
    private MaterialButton btnOpenPostSos;
    private SwipeRefreshLayout swipeRefresh;
    private android.widget.FrameLayout btnNotifBell;
    private TextView tvNotifBadge;

    private EmergencyAdapter mAdapter;
    private final List<EmergencyRequest> requestList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emergency, container, false);

        recyclerView = view.findViewById(R.id.recycler_emergency_requests);
        pbLoading = view.findViewById(R.id.pb_emergency_loading);
        layoutEmpty = view.findViewById(R.id.layout_emergency_empty);
        btnOpenPostSos = view.findViewById(R.id.btn_open_post_sos);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_emergency);
        btnNotifBell = view.findViewById(R.id.btn_emergency_notif_bell);
        tvNotifBadge = view.findViewById(R.id.tv_emergency_notif_badge);

        if (btnNotifBell != null) {
            btnNotifBell.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), NotificationCenterActivity.class);
                startActivity(intent);
            });
        }

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadRequests();
                    swipeRefresh.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        }
                    }, 800);
                }
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new EmergencyAdapter(getContext(), requestList, new OnActionClickListener() {
            @Override
            public void onDeleteRequested(EmergencyRequest req) {
                deleteBroadcast(req);
            }

            @Override
            public void onViewRequested(EmergencyRequest req) {
                Intent intent = new Intent(getContext(), EmergencyDetailActivity.class);
                intent.putExtra("emergency_id", req.getId());
                intent.putExtra("patient_name", req.getPatientName());
                intent.putExtra("hospital_name", req.getHospital());
                intent.putExtra("hospital_address", req.getHospitalAddress());
                intent.putExtra("city", req.getCity());
                intent.putExtra("blood_group", req.getBloodGroup());
                intent.putExtra("units_required", req.getUnitsRequired());
                intent.putExtra("contact_number", req.getContactNumber());
                intent.putExtra("urgency", req.getUrgency());
                intent.putExtra("status", req.getStatus());
                intent.putExtra("hospital_lat", req.getHospitalLatitude());
                intent.putExtra("hospital_lng", req.getHospitalLongitude());
                startActivity(intent);
            }
        });
        recyclerView.setAdapter(mAdapter);

        btnOpenPostSos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPostEmergencyDialog();
            }
        });

        loadRequests();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRequests();
        refreshUnreadNotificationBadge();
    }

    private void loadRequests() {
        if (getContext() == null) return;
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        DataManager.getInstance(getContext()).fetchEmergencyRequests(new DataManager.RequestCallback() {
            @Override
            public void onRequestsLoaded(List<EmergencyRequest> requests) {
                if (!isAdded()) return;
                pbLoading.setVisibility(View.GONE);
                requestList.clear();
                requestList.addAll(requests);
                mAdapter.notifyDataSetChanged();

                if (requestList.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void showPostEmergencyDialog() {
        if (getContext() == null) return;
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_post_emergency, null);

        final EditText etPatient = dialogView.findViewById(R.id.et_sos_patient);
        final EditText etHospital = dialogView.findViewById(R.id.et_sos_hospital);
        final EditText actvCity = dialogView.findViewById(R.id.actv_sos_city);
        final EditText actvBg = dialogView.findViewById(R.id.actv_sos_bg);
        final EditText etUnits = dialogView.findViewById(R.id.et_sos_units);
        final EditText etContact = dialogView.findViewById(R.id.et_sos_contact);
        final RadioGroup rgUrgency = dialogView.findViewById(R.id.rg_urgency);
        final MaterialButton btnAttachGps = dialogView.findViewById(R.id.btn_attach_gps_sos);
        final MaterialButton btnPost = dialogView.findViewById(R.id.btn_dialog_post_sos);

        final double[] attachedCoordinates = new double[]{0.0, 0.0}; // [lat, lng]

        final String[] selectedCityId = new String[]{""};
        final String[] selectedHospitalId = new String[]{""};
        final String[] selectedHospitalAddress = new String[]{""};
        final boolean[] isVerifiedHospital = new boolean[]{false};
        final boolean[] isProgrammaticUpdate = new boolean[]{false};

        // 1. Setup City Selection - Tap triggers dynamic canonical city modal from Database
        final com.google.android.material.textfield.TextInputLayout tilCity = dialogView.findViewById(R.id.til_sos_city);
        actvCity.setFocusable(false);
        actvCity.setClickable(true);
        View.OnClickListener cityClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isVerifiedHospital[0]) {
                    Toast.makeText(getContext(), "City is automatically locked to verified hospital's location. Edit hospital name to change city.", Toast.LENGTH_SHORT).show();
                    return;
                }
                showSelectCityDialog(actvCity, selectedCityId);
            }
        };
        actvCity.setOnClickListener(cityClickListener);
        if (tilCity != null) {
            tilCity.setOnClickListener(cityClickListener);
        }

        // 2. Setup Blood Group Selection - Tap triggers custom Material 3 dialog
        final com.google.android.material.textfield.TextInputLayout tilBg = dialogView.findViewById(R.id.til_sos_bg);
        actvBg.setFocusable(false);
        actvBg.setClickable(true);
        View.OnClickListener bgClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectBloodGroupDialog(actvBg);
            }
        };
        actvBg.setOnClickListener(bgClickListener);
        if (tilBg != null) {
            tilBg.setOnClickListener(bgClickListener);
        }
        actvBg.setText("O+");

        final UserProfile currentUser = DataManager.getInstance(getContext()).getCurrentUser();
        if (currentUser != null) {
            String mobile = currentUser.getMobile();
            if (mobile != null) {
                mobile = mobile.replace("+91", "").trim();
                etContact.setText(mobile);
            }
            if (currentUser.getCity() != null && !currentUser.getCity().isEmpty()) {
                actvCity.setText(currentUser.getCity());
            }
            if (currentUser.getCityId() != null && !currentUser.getCityId().isEmpty()) {
                selectedCityId[0] = currentUser.getCityId();
            }
            if (currentUser.getBloodGroup() != null && !currentUser.getBloodGroup().isEmpty()) {
                actvBg.setText(currentUser.getBloodGroup());
            }
        }

        final MaterialButton btnSelectHospital = dialogView.findViewById(R.id.btn_select_verified_hospital);
        final TextView tvVerifiedBadge = dialogView.findViewById(R.id.tv_verified_hospital_badge);

        // If user manually edits hospital name, unlock city and clear verified status
        etHospital.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isProgrammaticUpdate[0]) {
                    return;
                }
                if (isVerifiedHospital[0]) {
                    isVerifiedHospital[0] = false;
                    selectedHospitalId[0] = "";
                    selectedHospitalAddress[0] = "";
                    actvCity.setEnabled(true);
                    actvCity.setClickable(true);
                    actvCity.setFocusable(false);
                    tvVerifiedBadge.setVisibility(View.GONE);
                    btnSelectHospital.setStrokeColorResource(R.color.colorPrimary);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 3. Select Verified Hospital Formatted Picker (Live from Database)
        btnSelectHospital.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getContext() == null) return;

                View hospitalDialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_hospital, null);
                RecyclerView rvHospitals = hospitalDialogView.findViewById(R.id.recycler_dialog_hospitals);
                MaterialButton btnCloseHospital = hospitalDialogView.findViewById(R.id.btn_dialog_close_hospitals);
                ProgressBar pbHospitals = hospitalDialogView.findViewById(R.id.pb_dialog_hospitals);
                TextView tvEmpty = hospitalDialogView.findViewById(R.id.tv_dialog_hospitals_empty);
                EditText etSearch = hospitalDialogView.findViewById(R.id.et_dialog_hospital_search);

                final AlertDialog hospitalDialog = new AlertDialog.Builder(getContext())
                        .setView(hospitalDialogView)
                        .create();

                if (hospitalDialog.getWindow() != null) {
                    hospitalDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                btnCloseHospital.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hospitalDialog.dismiss();
                    }
                });

                final List<ApiClient.HospitalModel> allHospitals = new ArrayList<>();
                final List<ApiClient.HospitalModel> displayHospitals = new ArrayList<>();

                rvHospitals.setLayoutManager(new LinearLayoutManager(getContext()));
                final RecyclerView.Adapter<HospitalViewHolder> adapter = new RecyclerView.Adapter<HospitalViewHolder>() {
                    @NonNull
                    @Override
                    public HospitalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_verified_hospital, parent, false);
                        return new HospitalViewHolder(itemView);
                    }

                    @Override
                    public void onBindViewHolder(@NonNull HospitalViewHolder holder, int position) {
                        final ApiClient.HospitalModel hospital = displayHospitals.get(position);
                        holder.tvName.setText(hospital.getName());
                        String addressText = hospital.getAddress() != null ? hospital.getAddress() : "";
                        if (hospital.getCity() != null && !hospital.getCity().isEmpty()) {
                            addressText += (addressText.isEmpty() ? "" : " • ") + hospital.getCity();
                        }
                        holder.tvAddress.setText(addressText);

                        holder.itemView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                selectedHospitalId[0] = hospital.getId();
                                selectedHospitalAddress[0] = hospital.getAddress();
                                isVerifiedHospital[0] = true;

                                isProgrammaticUpdate[0] = true;
                                etHospital.setText(hospital.getName());
                                isProgrammaticUpdate[0] = false;

                                // Canonical City Lookup: resolve from cityId into main city collection
                                String hospCityId = hospital.getCityId();
                                if (hospCityId != null && !hospCityId.isEmpty()) {
                                    ApiClient.getInstance().getCityById(hospCityId, new ApiClient.ApiCallback<ApiClient.CityModel>() {
                                        @Override
                                        public void onSuccess(ApiClient.CityModel canonicalCity) {
                                            selectedCityId[0] = canonicalCity.getId();
                                            actvCity.setText(canonicalCity.getName());
                                            actvCity.setEnabled(false);
                                            tvVerifiedBadge.setVisibility(View.VISIBLE);
                                            tvVerifiedBadge.setText("✓ Verified Hospital: " + hospital.getName() + " (" + canonicalCity.getName() + ")");
                                        }

                                        @Override
                                        public void onError(String error) {
                                            if (hospital.getCity() != null && !hospital.getCity().isEmpty()) {
                                                actvCity.setText(hospital.getCity());
                                                actvCity.setEnabled(false);
                                                tvVerifiedBadge.setVisibility(View.VISIBLE);
                                                tvVerifiedBadge.setText("✓ Verified Hospital: " + hospital.getName() + " (" + hospital.getCity() + ")");
                                            }
                                        }
                                    });
                                } else if (hospital.getCity() != null && !hospital.getCity().isEmpty()) {
                                    actvCity.setText(hospital.getCity());
                                    actvCity.setEnabled(false); // Locked to verified hospital's city
                                    tvVerifiedBadge.setVisibility(View.VISIBLE);
                                    tvVerifiedBadge.setText("✓ Verified Hospital: " + hospital.getName() + " (" + hospital.getCity() + ")");
                                }

                                btnSelectHospital.setStrokeColorResource(R.color.status_available);
                                hospitalDialog.dismiss();
                            }
                        });
                    }

                    @Override
                    public int getItemCount() {
                        return displayHospitals.size();
                    }
                };
                rvHospitals.setAdapter(adapter);

                // Fetch dynamic hospitals directly from MongoDB database
                ApiClient.getInstance().getHospitals(null, new ApiClient.ApiCallback<List<ApiClient.HospitalModel>>() {
                    @Override
                    public void onSuccess(final List<ApiClient.HospitalModel> hospitals) {
                        pbHospitals.setVisibility(View.GONE);
                        if (hospitals == null || hospitals.isEmpty()) {
                            tvEmpty.setText("No verified hospitals found in database");
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvHospitals.setVisibility(View.GONE);
                            return;
                        }

                        allHospitals.clear();
                        allHospitals.addAll(hospitals);
                        displayHospitals.clear();
                        displayHospitals.addAll(hospitals);

                        tvEmpty.setVisibility(View.GONE);
                        rvHospitals.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(String error) {
                        pbHospitals.setVisibility(View.GONE);
                        tvEmpty.setText("Failed to load hospitals from database: " + error);
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvHospitals.setVisibility(View.GONE);
                    }
                });

                // Live search text watcher
                if (etSearch != null) {
                    etSearch.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            String query = s.toString().trim().toLowerCase();
                            displayHospitals.clear();
                            if (query.isEmpty()) {
                                displayHospitals.addAll(allHospitals);
                            } else {
                                for (ApiClient.HospitalModel h : allHospitals) {
                                    boolean matchName = h.getName() != null && h.getName().toLowerCase().contains(query);
                                    boolean matchCity = h.getCity() != null && h.getCity().toLowerCase().contains(query);
                                    boolean matchAddress = h.getAddress() != null && h.getAddress().toLowerCase().contains(query);
                                    if (matchName || matchCity || matchAddress) {
                                        displayHospitals.add(h);
                                    }
                                }
                            }
                            if (displayHospitals.isEmpty() && !allHospitals.isEmpty()) {
                                tvEmpty.setText("No hospitals matching \"" + s + "\"");
                                tvEmpty.setVisibility(View.VISIBLE);
                                rvHospitals.setVisibility(View.GONE);
                            } else {
                                tvEmpty.setVisibility(View.GONE);
                                rvHospitals.setVisibility(View.VISIBLE);
                            }
                            adapter.notifyDataSetChanged();
                        }

                        @Override
                        public void afterTextChanged(Editable s) {}
                    });
                }

                hospitalDialog.show();
            }
        });

        // 4. Attach GPS Button
        btnAttachGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!LocationHelper.hasLocationPermission(getContext())) {
                    if (getActivity() != null) LocationHelper.requestLocationPermission(getActivity());
                    return;
                }
                btnAttachGps.setText("Acquiring GPS fix...");
                LocationHelper.getCurrentLocation(getContext(), new LocationHelper.LocationCallback() {
                    @Override
                    public void onLocationAcquired(double latitude, double longitude) {
                        attachedCoordinates[0] = latitude;
                        attachedCoordinates[1] = longitude;
                        btnAttachGps.setText("Location Attached (" + String.format("%.3f, %.3f", latitude, longitude) + ")");
                        btnAttachGps.setStrokeColorResource(R.color.status_available);
                    }

                    @Override
                    public void onLocationFailed(String errorMessage) {
                        btnAttachGps.setText("Attach My Current GPS Location");
                        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 5. Post SOS with Strict Validation
        btnPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String patient = etPatient.getText().toString().trim();
                String hospital = etHospital.getText().toString().trim();
                String enteredCity = actvCity.getText().toString().trim();
                String unitsStr = etUnits.getText().toString().trim();
                String rawContact = etContact.getText().toString().trim().replaceAll("[^0-9]", "");
                String bg = actvBg.getText().toString().trim();

                if (patient.isEmpty()) {
                    etPatient.setError("Patient name is required");
                    etPatient.requestFocus();
                    return;
                }

                if (hospital.isEmpty()) {
                    etHospital.setError("Hospital name is required");
                    etHospital.requestFocus();
                    return;
                }

                if (enteredCity.isEmpty()) {
                    actvCity.setError("Please select a city");
                    actvCity.requestFocus();
                    Toast.makeText(getContext(), "Please select a city from the list", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Strict Phone Validation: 10 Digits
                if (rawContact.length() != 10) {
                    etContact.setError("Please enter a valid 10-digit mobile number");
                    etContact.requestFocus();
                    return;
                }

                int units = 1;
                try {
                    units = Integer.parseInt(unitsStr);
                } catch (Exception ignored) {}

                String urgency = "URGENT";
                int checkedUrgencyId = rgUrgency.getCheckedRadioButtonId();
                if (checkedUrgencyId == R.id.rb_urgency_critical) {
                    urgency = "CRITICAL";
                } else if (checkedUrgencyId == R.id.rb_urgency_normal) {
                    urgency = "NORMAL";
                }

                final String finalPatient = patient;
                final String finalHospital = hospital;
                final String finalCity = enteredCity;
                final String finalBg = bg.isEmpty() ? "O+" : bg;
                final int finalUnits = units;
                final String finalContact = "+91 " + rawContact;
                final String postedByEmail = currentUser != null ? currentUser.getEmail() : "";

                EmergencyRequest newReq = new EmergencyRequest(
                        "REQ-" + (System.currentTimeMillis() % 10000),
                        patient,
                        hospital,
                        finalCity,
                        finalBg,
                        units,
                        finalContact,
                        postedByEmail
                );
                newReq.setUrgency(urgency);
                if (selectedCityId[0] != null && !selectedCityId[0].isEmpty()) {
                    newReq.setCityId(selectedCityId[0]);
                }
                if (selectedHospitalId[0] != null && !selectedHospitalId[0].isEmpty()) {
                    newReq.setHospitalId(selectedHospitalId[0]);
                    newReq.setHospitalAddress(selectedHospitalAddress[0]);
                    newReq.setAuthoritativeHospital(true);
                }
                if (attachedCoordinates[0] != 0.0 || attachedCoordinates[1] != 0.0) {
                    newReq.setLatitude(attachedCoordinates[0]);
                    newReq.setLongitude(attachedCoordinates[1]);
                }

                DataManager.getInstance(getContext()).createEmergencyRequest(newReq, new DataManager.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Emergency SOS Broadcasted successfully! Matching donors are being alerted...", Toast.LENGTH_LONG).show();
                            NotificationHelper.markEmergencyAsSeen(getContext(), newReq.getId());
                        }
                        dialog.dismiss();
                        loadRequests();
                    }

                    @Override
                    public void onError(String message) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Error broadcasting request: " + message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        dialog.show();
    }

    private void deleteBroadcast(final EmergencyRequest req) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Resolve & Delete SOS?")
                .setMessage("Are you sure you want to remove this emergency broadcast? This will remove it from all nearby donor feeds.")
                .setPositiveButton("Resolve & Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DataManager.getInstance(getContext()).deleteEmergencyRequest(req.getId(), new DataManager.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                if (getContext() != null) {
                                    Toast.makeText(getContext(), "Emergency SOS resolved & removed.", Toast.LENGTH_SHORT).show();
                                    loadRequests();
                                }
                            }

                            @Override
                            public void onError(String message) {
                                loadRequests();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public interface OnActionClickListener {
        void onDeleteRequested(EmergencyRequest req);
        void onViewRequested(EmergencyRequest req);
    }

    // Emergency Adapter
    public static class EmergencyAdapter extends RecyclerView.Adapter<EmergencyAdapter.ViewHolder> {
        private final Context context;
        private final List<EmergencyRequest> list;
        private final OnActionClickListener listener;

        public EmergencyAdapter(Context context, List<EmergencyRequest> list, OnActionClickListener listener) {
            this.context = context;
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_emergency_request, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final EmergencyRequest req = list.get(position);
            if (req == null) return;

            String bg = req.getBloodGroup() != null ? req.getBloodGroup() : "O+";
            String patient = req.getPatientName() != null ? req.getPatientName() : "Patient";
            String hospital = req.getHospital() != null ? req.getHospital() : "Hospital";
            String city = req.getCity() != null ? req.getCity() : "";
            final String contact = req.getContactNumber() != null ? req.getContactNumber() : "";
            String postedBy = req.getPostedBy() != null ? req.getPostedBy() : "";

            holder.tvBloodGroup.setText(bg);
            holder.tvPatientName.setText(patient);
            holder.tvHospitalCity.setText(hospital + (!city.isEmpty() ? " • " + city : ""));
            holder.tvUnits.setText(req.getUnitsNeeded() + (req.getUnitsNeeded() == 1 ? " Unit" : " Units"));

            // Check if current user is owner of this broadcast
            UserProfile currentUser = DataManager.getInstance(context).getCurrentUser();
            boolean isOwnBroadcast = false;
            if (currentUser != null) {
                String myEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";
                String myMobile = currentUser.getMobile() != null ? currentUser.getMobile().replaceAll("[^0-9]", "") : "";
                String reqMobileDigits = contact.replaceAll("[^0-9]", "");

                boolean matchesEmail = !postedBy.isEmpty() && postedBy.equalsIgnoreCase(myEmail);
                boolean matchesMobile = !reqMobileDigits.isEmpty() && !myMobile.isEmpty() && reqMobileDigits.equals(myMobile);
                isOwnBroadcast = matchesEmail || matchesMobile;
            }

            if (isOwnBroadcast) {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) {
                            listener.onDeleteRequested(req);
                        }
                    }
                });
            } else {
                holder.btnDelete.setVisibility(View.GONE);
            }

            holder.btnView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onViewRequested(req);
                    }
                }
            });

            holder.btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!contact.isEmpty()) {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact));
                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvBloodGroup, tvPatientName, tvHospitalCity, tvUnits;
            MaterialButton btnView, btnCall, btnDelete;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBloodGroup = itemView.findViewById(R.id.tv_req_blood_group);
                tvPatientName = itemView.findViewById(R.id.tv_req_patient_name);
                tvHospitalCity = itemView.findViewById(R.id.tv_req_hospital_city);
                tvUnits = itemView.findViewById(R.id.tv_req_units);
                btnView = itemView.findViewById(R.id.btn_req_view);
                btnCall = itemView.findViewById(R.id.btn_req_call);
                btnDelete = itemView.findViewById(R.id.btn_req_delete);
            }
        }
    }

    public static class HospitalViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress;

        public HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_hospital_name);
            tvAddress = itemView.findViewById(R.id.tv_item_hospital_address);
        }
    }

    private void showSelectCityDialog(final EditText actvCity, final String[] selectedCityId) {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_city, null);
        RecyclerView rvCities = dialogView.findViewById(R.id.recycler_dialog_cities);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_close_cities);
        ProgressBar pbCities = dialogView.findViewById(R.id.pb_dialog_cities);
        TextView tvEmpty = dialogView.findViewById(R.id.tv_dialog_cities_empty);
        EditText etSearch = dialogView.findViewById(R.id.et_dialog_city_search);

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
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

        rvCities.setLayoutManager(new LinearLayoutManager(getContext()));
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
                        selectedCityId[0] = city.getId();
                        actvCity.setText(city.getName());
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
                tvEmpty.setText("Failed to load cities from database: " + error);
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

    private void showSelectBloodGroupDialog(final EditText actvBg) {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_blood_group, null);
        RadioGroup rg = dialogView.findViewById(R.id.rg_dialog_blood_group);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_bg_cancel);

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        String currentBg = actvBg.getText().toString().trim();
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
                    actvBg.setText(rb.getTag().toString());
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

    public static class CityViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName;
        public TextView tvSubtitle;

        public CityViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_city_name);
            tvSubtitle = itemView.findViewById(R.id.tv_item_city_subtitle);
        }
    }

    private void refreshUnreadNotificationBadge() {
        ApiClient.getInstance().getNotifications("ALL", 1, new ApiClient.ApiCallback<ApiClient.NotificationListResponse>() {
            @Override
            public void onSuccess(ApiClient.NotificationListResponse response) {
                if (!isAdded() || getContext() == null) return;
                if (tvNotifBadge != null) {
                    if (response != null && response.unreadCount > 0) {
                        tvNotifBadge.setText(response.unreadCount > 99 ? "99+" : String.valueOf(response.unreadCount));
                        tvNotifBadge.setVisibility(View.VISIBLE);
                    } else {
                        tvNotifBadge.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {}
        });
    }
}
