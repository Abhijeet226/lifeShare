package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private Spinner mSearchField;
    private MaterialButton mSearchBtn;
    private EditText etFilterCity;
    private RecyclerView mResultList;
    private ProgressBar pbLoading;
    private View layoutEmptyState;
    private TextView tvResultsCount;

    private DonorAdapter mAdapter;
    private final List<UserProfile> mAllDonors = new ArrayList<>();
    private final List<UserProfile> mFilteredDonors = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_search);

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
            tvHeader.setText("Find Blood Donors");
        }

        mSearchField = findViewById(R.id.search_field);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this, R.array.group, android.R.layout.simple_spinner_dropdown_item
        );
        mSearchField.setAdapter(spinnerAdapter);
        mSearchField.setOnItemSelectedListener(this);

        mSearchBtn = findViewById(R.id.search_btn);
        etFilterCity = findViewById(R.id.et_filter_city);
        pbLoading = findViewById(R.id.pb_search_loading);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvResultsCount = findViewById(R.id.tv_results_count);

        mResultList = findViewById(R.id.result_list);
        mResultList.setHasFixedSize(true);
        mResultList.setLayoutManager(new LinearLayoutManager(this));

        mAdapter = new DonorAdapter(this, mFilteredDonors);
        mResultList.setAdapter(mAdapter);

        mSearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String bloodGroup = mSearchField.getSelectedItem().toString().trim();
                fetchDonors(bloodGroup);
            }
        });

        // Real-time city/name text filtering
        etFilterCity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDonorsLocally(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Initial search for default blood group
        String defaultGroup = mSearchField.getSelectedItem() != null ? mSearchField.getSelectedItem().toString() : "A+";
        fetchDonors(defaultGroup);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchDonors(final String bloodGroup) {
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);

        DataManager.getInstance(this).fetchDonors(bloodGroup, new DataManager.DonorCallback() {
            @Override
            public void onDonorsLoaded(List<UserProfile> donors) {
                pbLoading.setVisibility(View.GONE);
                mAllDonors.clear();
                if (donors != null) {
                    mAllDonors.addAll(donors);
                }
                filterDonorsLocally(etFilterCity.getText().toString());
            }
        });
    }

    private void filterDonorsLocally(String query) {
        mFilteredDonors.clear();
        String filter = query.toLowerCase().trim();

        for (UserProfile donor : mAllDonors) {
            boolean matchesName = donor.getName() != null && donor.getName().toLowerCase().contains(filter);
            boolean matchesCity = donor.getCity() != null && donor.getCity().toLowerCase().contains(filter);
            if (filter.isEmpty() || matchesName || matchesCity) {
                mFilteredDonors.add(donor);
            }
        }

        mAdapter.notifyDataSetChanged();

        if (mFilteredDonors.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            mResultList.setVisibility(View.GONE);
            tvResultsCount.setText("0 Donors Found");
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            mResultList.setVisibility(View.VISIBLE);
            tvResultsCount.setText(mFilteredDonors.size() + " Donors Found");
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String bloodGroup = parent.getItemAtPosition(position).toString();
        fetchDonors(bloodGroup);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    // Modern RecyclerView Adapter
    public static class DonorAdapter extends RecyclerView.Adapter<DonorAdapter.DonorViewHolder> {

        private final Context context;
        private final List<UserProfile> donorList;

        public DonorAdapter(Context context, List<UserProfile> donorList) {
            this.context = context;
            this.donorList = donorList;
        }

        @NonNull
        @Override
        public DonorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.list_layout, parent, false);
            return new DonorViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DonorViewHolder holder, int position) {
            final UserProfile donor = donorList.get(position);

            holder.tvName.setText(donor.getName());
            holder.tvBloodBadge.setText(donor.getBloodGroup().isEmpty() ? "O+" : donor.getBloodGroup());

            if (donor.getCity() == null || donor.getCity().isEmpty()) {
                holder.layoutLocation.setVisibility(View.GONE);
            } else {
                holder.layoutLocation.setVisibility(View.VISIBLE);
                holder.tvCity.setText(donor.getCity());
            }

            if (donor.isAvailable()) {
                holder.tvStatus.setText("Active");
                holder.tvStatus.setBackgroundResource(R.drawable.badge_available);
                holder.tvStatus.setTextColor(context.getResources().getColor(R.color.status_available));
            } else {
                holder.tvStatus.setText("Inactive");
                holder.tvStatus.setBackgroundResource(R.drawable.badge_busy);
                holder.tvStatus.setTextColor(context.getResources().getColor(R.color.status_busy));
            }

            // Direct Call Action
            holder.btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (donor.getMobile() != null && !donor.getMobile().isEmpty()) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:" + donor.getMobile().trim()));
                        context.startActivity(callIntent);
                    } else {
                        Toast.makeText(context, "Mobile number not provided", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Direct SMS Action
            holder.btnSms.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (donor.getMobile() != null && !donor.getMobile().isEmpty()) {
                        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                        smsIntent.setData(Uri.parse("smsto:" + donor.getMobile().trim()));
                        smsIntent.putExtra("sms_body", "Hello " + donor.getName() + ", I found your contact on LifeShare Blood Bank. Urgent need for " + donor.getBloodGroup() + " blood. Are you available to donate?");
                        context.startActivity(smsIntent);
                    } else {
                        Toast.makeText(context, "Mobile number not provided", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Direct WhatsApp Action
            holder.btnWhatsApp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (donor.getMobile() != null && !donor.getMobile().isEmpty()) {
                        String phone = donor.getMobile().trim().replaceAll("[^0-9]", "");
                        if (!phone.startsWith("91") && phone.length() == 10) {
                            phone = "91" + phone;
                        }
                        String message = "Hello " + donor.getName() + ", I found your contact on LifeShare Blood Bank. Urgent need for " + donor.getBloodGroup() + " blood. Are you available to donate?";
                        String url = "https://api.whatsapp.com/send?phone=" + phone + "&text=" + Uri.encode(message);
                        Intent waIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        try {
                            context.startActivity(waIntent);
                        } catch (Exception e) {
                            Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "Mobile number not provided", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return donorList.size();
        }

        public static class DonorViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvBloodBadge, tvCity, tvStatus;
            View layoutLocation;
            View btnCall, btnSms, btnWhatsApp;

            public DonorViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.name_text);
                tvBloodBadge = itemView.findViewById(R.id.tv_blood_badge);
                tvCity = itemView.findViewById(R.id.tv_donor_city);
                tvStatus = itemView.findViewById(R.id.tv_availability_status);
                layoutLocation = itemView.findViewById(R.id.layout_location);

                btnCall = itemView.findViewById(R.id.btn_action_call);
                btnSms = itemView.findViewById(R.id.btn_action_sms);
                btnWhatsApp = itemView.findViewById(R.id.btn_action_whatsapp);
            }
        }
    }
}
