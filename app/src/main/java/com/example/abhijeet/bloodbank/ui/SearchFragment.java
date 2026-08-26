package com.example.abhijeet.bloodbank.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.abhijeet.bloodbank.ApiClient;
import com.example.abhijeet.bloodbank.DataManager;
import com.example.abhijeet.bloodbank.LocationHelper;
import com.example.abhijeet.bloodbank.R;
import com.example.abhijeet.bloodbank.UserProfile;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private MaterialButton btnUseMyLocation, btnToggleCitySearch, btnExpandRadius;
    private View layoutCitySearchBar, scrollRadiusChips;
    private AutoCompleteTextView etFilter;
    private ChipGroup chipGroupBlood, chipGroupRadius;
    private RecyclerView recyclerDonors;
    private ProgressBar pbLoading;
    private View layoutEmpty;
    private TextView tvResultsCount, tvEmptyTitle, tvEmptySubtext;
    private SwipeRefreshLayout swipeRefresh;

    private DonorListAdapter mAdapter;
    private final List<UserProfile> allDonors = new ArrayList<>();
    private final List<UserProfile> filteredDonors = new ArrayList<>();

    private String selectedBloodGroup = "All";
    private int selectedRadiusMeters = 10000; // default 10 km
    private boolean isGpsMode = false;
    private double currentLat = 0.0;
    private double currentLng = 0.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        btnUseMyLocation = view.findViewById(R.id.btn_use_my_location);
        btnToggleCitySearch = view.findViewById(R.id.btn_toggle_city_search);
        btnExpandRadius = view.findViewById(R.id.btn_expand_radius);
        layoutCitySearchBar = view.findViewById(R.id.layout_city_search_bar);
        scrollRadiusChips = view.findViewById(R.id.scroll_radius_chips);
        etFilter = view.findViewById(R.id.et_search_filter);

        if (getContext() != null && etFilter != null) {
            final List<String> cityNames = new ArrayList<>();
            final ArrayAdapter<String> citySuggestionsAdapter = new ArrayAdapter<>(getContext(), R.layout.item_dropdown_popup, cityNames);
            etFilter.setAdapter(citySuggestionsAdapter);
            etFilter.setThreshold(1);
            etFilter.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    String selectedCity = (String) parent.getItemAtPosition(position);
                    applyLocalFilter(selectedCity);
                }
            });

            // Dynamically load canonical cities from database
            ApiClient.getInstance().getCities(new ApiClient.ApiCallback<List<ApiClient.CityModel>>() {
                @Override
                public void onSuccess(List<ApiClient.CityModel> cities) {
                    if (cities != null && !cities.isEmpty() && getContext() != null) {
                        cityNames.clear();
                        for (ApiClient.CityModel c : cities) {
                            if (c.getName() != null && !c.getName().isEmpty()) {
                                cityNames.add(c.getName());
                            }
                        }
                        citySuggestionsAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onError(String error) {}
            });
        }
        chipGroupBlood = view.findViewById(R.id.chip_group_blood);
        chipGroupRadius = view.findViewById(R.id.chip_group_radius);
        recyclerDonors = view.findViewById(R.id.recycler_donors);
        pbLoading = view.findViewById(R.id.pb_search_loading);
        layoutEmpty = view.findViewById(R.id.layout_search_empty);
        tvResultsCount = view.findViewById(R.id.tv_search_results_count);
        tvEmptyTitle = view.findViewById(R.id.tv_empty_title);
        tvEmptySubtext = view.findViewById(R.id.tv_empty_subtext);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_search);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    if (isGpsMode && currentLat != 0.0 && currentLng != 0.0) {
                        loadNearbyDonors();
                    } else {
                        loadDonors();
                    }
                    swipeRefresh.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        }
                    }, 1200);
                }
            });
        }

        recyclerDonors.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new DonorListAdapter(getContext(), filteredDonors);
        recyclerDonors.setAdapter(mAdapter);

        // 1. Blood Group Chip selection
        chipGroupBlood.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {
                Chip chip = group.findViewById(checkedId);
                selectedBloodGroup = (chip != null) ? chip.getText().toString() : "All";
                loadDonors();
            }
        });

        // 2. Search Radius Chip selection
        chipGroupRadius.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ChipGroup group, int checkedId) {
                if (checkedId == R.id.chip_radius_5km) {
                    selectedRadiusMeters = 5000;
                } else if (checkedId == R.id.chip_radius_20km) {
                    selectedRadiusMeters = 20000;
                } else if (checkedId == R.id.chip_radius_30km) {
                    selectedRadiusMeters = 30000;
                } else {
                    selectedRadiusMeters = 10000;
                }
                if (isGpsMode) {
                    loadNearbyDonors();
                }
            }
        });

        // 3. Use My Current Location Click
        btnUseMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSearchModeUI(true);
                acquireLocationAndSearch();
            }
        });

        // 4. City Search Mode Click
        btnToggleCitySearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSearchModeUI(false);
                loadDonors();
            }
        });

        // 5. Expand Radius in Empty State
        btnExpandRadius.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedRadiusMeters < 20000) {
                    chipGroupRadius.check(R.id.chip_radius_20km);
                } else {
                    chipGroupRadius.check(R.id.chip_radius_30km);
                }
            }
        });

        // 6. Search text filter
        etFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyLocalFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        updateSearchModeUI(false);
        loadDonors();

        return view;
    }

    private void updateSearchModeUI(boolean gpsMode) {
        isGpsMode = gpsMode;
        if (getContext() == null || btnUseMyLocation == null || btnToggleCitySearch == null) return;
        int colorActiveBg = com.google.android.material.color.MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorSecondaryContainer, ContextCompat.getColor(getContext(), R.color.status_busy_bg));
        int colorActiveText = com.google.android.material.color.MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSecondaryContainer, ContextCompat.getColor(getContext(), R.color.colorPrimary));
        int colorInactiveText = com.google.android.material.color.MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, ContextCompat.getColor(getContext(), R.color.text_secondary));

        if (gpsMode) {
            btnUseMyLocation.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorActiveBg));
            btnUseMyLocation.setTextColor(colorActiveText);
            btnToggleCitySearch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnToggleCitySearch.setTextColor(colorInactiveText);
            if (scrollRadiusChips != null) scrollRadiusChips.setVisibility(View.VISIBLE);
            if (layoutCitySearchBar != null) layoutCitySearchBar.setVisibility(View.GONE);
        } else {
            btnToggleCitySearch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorActiveBg));
            btnToggleCitySearch.setTextColor(colorActiveText);
            btnUseMyLocation.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnUseMyLocation.setTextColor(colorInactiveText);
            if (scrollRadiusChips != null) scrollRadiusChips.setVisibility(View.GONE);
            if (layoutCitySearchBar != null) layoutCitySearchBar.setVisibility(View.VISIBLE);
        }
    }

    private void acquireLocationAndSearch() {
        if (getContext() == null) return;

        if (!LocationHelper.hasLocationPermission(getContext())) {
            if (getActivity() != null) {
                LocationHelper.requestLocationPermission(getActivity());
            }
            return;
        }

        if (!LocationHelper.isGpsEnabled(getContext())) {
            Toast.makeText(getContext(), "Please turn on location services (GPS) to search nearby donors.", Toast.LENGTH_LONG).show();
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        LocationHelper.getCurrentLocation(getContext(), new LocationHelper.LocationCallback() {
            @Override
            public void onLocationAcquired(double latitude, double longitude) {
                isGpsMode = true;
                currentLat = latitude;
                currentLng = longitude;
                DataManager.getInstance(getContext()).saveLastKnownLocation(latitude, longitude);
                loadNearbyDonors();
            }

            @Override
            public void onLocationFailed(String errorMessage) {
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                loadDonors(); // fallback to city search
            }
        });
    }

    private void loadNearbyDonors() {
        if (getContext() == null) return;
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        DataManager.getInstance(getContext()).fetchNearbyDonors(selectedBloodGroup, currentLat, currentLng, selectedRadiusMeters, new DataManager.DonorCallback() {
            @Override
            public void onDonorsLoaded(List<UserProfile> donors) {
                if (!isAdded()) return;
                pbLoading.setVisibility(View.GONE);
                allDonors.clear();
                allDonors.addAll(donors);
                applyLocalFilter(etFilter.getText().toString());
            }
        });
    }

    private void loadDonors() {
        if (isGpsMode && currentLat != 0.0 && currentLng != 0.0) {
            loadNearbyDonors();
            return;
        }

        if (getContext() == null) return;
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        DataManager.getInstance(getContext()).fetchDonors(selectedBloodGroup, new DataManager.DonorCallback() {
            @Override
            public void onDonorsLoaded(List<UserProfile> donors) {
                if (!isAdded()) return;
                pbLoading.setVisibility(View.GONE);
                allDonors.clear();
                allDonors.addAll(donors);
                applyLocalFilter(etFilter.getText().toString());
            }
        });
    }

    private void applyLocalFilter(String query) {
        filteredDonors.clear();
        String q = query.toLowerCase().trim();

        for (UserProfile donor : allDonors) {
            boolean matchesName = donor.getName().toLowerCase().contains(q);
            boolean matchesCity = donor.getCity().toLowerCase().contains(q);
            if (q.isEmpty() || matchesName || matchesCity) {
                filteredDonors.add(donor);
            }
        }

        mAdapter.notifyDataSetChanged();

        if (filteredDonors.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerDonors.setVisibility(View.GONE);
            tvResultsCount.setText("0 Donors Found");
            if (isGpsMode) {
                tvEmptyTitle.setText("No Donors Within " + (selectedRadiusMeters / 1000) + " km");
                tvEmptySubtext.setText("Try expanding your search radius to find compatible donors.");
                btnExpandRadius.setVisibility(selectedRadiusMeters < 30000 ? View.VISIBLE : View.GONE);
            } else {
                tvEmptyTitle.setText("No Donors Found");
                tvEmptySubtext.setText("Try another blood group or clearing the search filter.");
                btnExpandRadius.setVisibility(View.GONE);
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerDonors.setVisibility(View.VISIBLE);
            tvResultsCount.setText(filteredDonors.size() + " Donors Found" + (isGpsMode ? " (Within " + (selectedRadiusMeters / 1000) + " km)" : ""));
        }
    }

    // Modern Donor List Adapter
    public static class DonorListAdapter extends RecyclerView.Adapter<DonorListAdapter.ViewHolder> {
        private final Context context;
        private final List<UserProfile> list;
        private int expandedPosition = -1;

        public DonorListAdapter(Context context, List<UserProfile> list) {
            this.context = context;
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.list_layout, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
            final UserProfile donor = list.get(position);
            if (donor == null) return;
            final boolean isExpanded = (position == expandedPosition);

            String name = donor.getName() != null ? donor.getName() : "Voluntary Donor";
            String blood = donor.getBloodGroup() != null && !donor.getBloodGroup().isEmpty() ? donor.getBloodGroup() : "O+";
            String city = donor.getCity() != null ? donor.getCity() : "";

            holder.tvName.setText(name);
            holder.tvBloodBadge.setText(blood);

            // No verified badge on donor card
            if (holder.ivVerifiedBadge != null) {
                holder.ivVerifiedBadge.setVisibility(View.GONE);
            }

            if (city.isEmpty()) {
                holder.layoutLocation.setVisibility(View.GONE);
            } else {
                holder.layoutLocation.setVisibility(View.VISIBLE);
                holder.tvCity.setText(city + ", Odisha");
            }

            // Prominent Distance Badge
            if (donor.getDistanceKm() > 0) {
                holder.tvDistance.setVisibility(View.VISIBLE);
                holder.tvDistance.setText("~" + String.format(java.util.Locale.US, "%.1f", donor.getDistanceKm()) + " km away");
            } else {
                holder.tvDistance.setVisibility(View.GONE);
            }

            // Active / Inactive Status
            if (holder.tvStatus != null) {
                if (donor.isAvailable()) {
                    holder.tvStatus.setText("Active");
                    holder.tvStatus.setBackgroundResource(R.drawable.badge_available);
                    holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_available));
                } else {
                    holder.tvStatus.setText("Inactive");
                    holder.tvStatus.setBackgroundResource(R.drawable.badge_busy);
                    holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_busy));
                }
            }

            holder.layoutActionTray.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int prevExpanded = expandedPosition;
                    if (isExpanded) {
                        expandedPosition = -1;
                    } else {
                        expandedPosition = holder.getAdapterPosition();
                    }
                    if (prevExpanded != -1) notifyItemChanged(prevExpanded);
                    if (expandedPosition != -1) notifyItemChanged(expandedPosition);
                }
            });

            holder.btnBadgeContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDonorDetailsDialog(context, donor);
                }
            });

            holder.btnActionProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDonorDetailsDialog(context, donor);
                }
            });

            // 1. Call Action
            holder.btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (donor.getMobile() != null && !donor.getMobile().isEmpty() && !donor.isHideMobileNumber()) {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + donor.getMobile().trim()));
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "Mobile number hidden for privacy", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // 2. SMS Action
            holder.btnSms.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (donor.getMobile() != null && !donor.getMobile().isEmpty() && !donor.isHideMobileNumber()) {
                        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + donor.getMobile().trim()));
                        intent.putExtra("sms_body", "Hello " + donor.getName() + ", urgent requirement for " + donor.getBloodGroup() + " blood in Odisha. Are you available?");
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "Mobile number hidden for privacy", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // 3. WhatsApp Action
            holder.btnWhatsApp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (donor.getMobile() != null && !donor.getMobile().isEmpty() && !donor.isHideMobileNumber()) {
                        String phone = donor.getMobile().trim().replaceAll("[^0-9]", "");
                        if (!phone.startsWith("91") && phone.length() == 10) phone = "91" + phone;
                        String msg = "Hello " + donor.getName() + ", urgent requirement for " + donor.getBloodGroup() + " blood in Odisha. Are you available?";
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=" + phone + "&text=" + Uri.encode(msg)));
                        try {
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "Mobile number hidden for privacy", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        private static void showDonorDetailsDialog(final Context ctx, final UserProfile donor) {
            View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_donor_details, null);

            TextView tvBloodBadge = dialogView.findViewById(R.id.tv_modal_blood_badge);
            TextView tvName = dialogView.findViewById(R.id.tv_modal_donor_name);
            TextView tvId = dialogView.findViewById(R.id.tv_modal_donor_id);
            TextView tvCity = dialogView.findViewById(R.id.tv_modal_donor_city);
            TextView tvGenderStatus = dialogView.findViewById(R.id.tv_modal_donor_gender_status);
            TextView tvPhone = dialogView.findViewById(R.id.tv_modal_donor_phone);

            View btnCall = dialogView.findViewById(R.id.btn_modal_call);
            View btnSms = dialogView.findViewById(R.id.btn_modal_sms);
            View btnWhatsApp = dialogView.findViewById(R.id.btn_modal_whatsapp);
            View btnShare = dialogView.findViewById(R.id.btn_modal_share);
            View btnClose = dialogView.findViewById(R.id.btn_close_donor_modal);

            if (tvBloodBadge != null) tvBloodBadge.setText(donor.getBloodGroup());
            if (tvName != null) tvName.setText(donor.getName());
            if (tvId != null) tvId.setText("Donor ID: " + (donor.getDonorId() != null && !donor.getDonorId().isEmpty() ? donor.getDonorId() : "LifeShare"));
            if (tvCity != null) tvCity.setText(donor.getCity() + ", Odisha");
            if (tvGenderStatus != null) {
                tvGenderStatus.setText((donor.getGender() != null ? donor.getGender() : "") + " • " + (donor.isAvailable() ? "Active" : "Inactive"));
            }
            if (tvPhone != null) {
                tvPhone.setText(donor.isHideMobileNumber() ? "Hidden for privacy" : donor.getMobile());
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
            builder.setView(dialogView);
            final AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            if (btnCall != null) {
                btnCall.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (donor.getMobile() != null && !donor.getMobile().isEmpty() && !donor.isHideMobileNumber()) {
                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + donor.getMobile().trim()));
                            ctx.startActivity(intent);
                        } else {
                            Toast.makeText(ctx, "Mobile number hidden for privacy", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            if (btnSms != null) {
                btnSms.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (donor.getMobile() != null && !donor.getMobile().isEmpty() && !donor.isHideMobileNumber()) {
                            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + donor.getMobile().trim()));
                            intent.putExtra("sms_body", "Hello " + donor.getName() + ", urgent need for " + donor.getBloodGroup() + " blood in Odisha.");
                            ctx.startActivity(intent);
                        } else {
                            Toast.makeText(ctx, "Mobile number hidden for privacy", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            if (btnWhatsApp != null) {
                btnWhatsApp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (donor.getMobile() != null && !donor.getMobile().isEmpty() && !donor.isHideMobileNumber()) {
                            String phone = donor.getMobile().trim().replaceAll("[^0-9]", "");
                            if (!phone.startsWith("91") && phone.length() == 10) phone = "91" + phone;
                            String msg = "Hello " + donor.getName() + ", urgent need for " + donor.getBloodGroup() + " blood in Odisha.";
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=" + phone + "&text=" + Uri.encode(msg)));
                            try {
                                ctx.startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(ctx, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(ctx, "Mobile number hidden for privacy", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            if (btnShare != null) {
                btnShare.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String shareText = "🩸 *LifeShare Verified Donor Profile*\n" +
                                "Name: " + donor.getName() + "\n" +
                                "Blood Group: " + donor.getBloodGroup() + "\n" +
                                "City: " + donor.getCity() + ", Odisha\n" +
                                "Donor ID: " + donor.getDonorId();
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                        ctx.startActivity(Intent.createChooser(shareIntent, "Share Donor Profile"));
                    }
                });
            }

            if (btnClose != null) {
                btnClose.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
            }

            dialog.show();
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvBloodBadge, tvCity, tvDistance, tvStatus;
            ImageView ivVerifiedBadge;
            View layoutLocation, layoutActionTray, btnBadgeContainer;
            View btnCall, btnSms, btnWhatsApp, btnActionProfile;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.name_text);
                tvBloodBadge = itemView.findViewById(R.id.tv_blood_badge);
                tvCity = itemView.findViewById(R.id.tv_donor_city);
                tvDistance = itemView.findViewById(R.id.tv_donor_distance);
                tvStatus = itemView.findViewById(R.id.tv_availability_status);
                ivVerifiedBadge = itemView.findViewById(R.id.iv_donor_verified_badge);
                layoutLocation = itemView.findViewById(R.id.layout_location);
                layoutActionTray = itemView.findViewById(R.id.layout_action_tray);
                btnBadgeContainer = itemView.findViewById(R.id.btn_badge_container);
                btnCall = itemView.findViewById(R.id.btn_action_call);
                btnSms = itemView.findViewById(R.id.btn_action_sms);
                btnWhatsApp = itemView.findViewById(R.id.btn_action_whatsapp);
                btnActionProfile = itemView.findViewById(R.id.btn_action_profile);
            }
        }
    }
}
