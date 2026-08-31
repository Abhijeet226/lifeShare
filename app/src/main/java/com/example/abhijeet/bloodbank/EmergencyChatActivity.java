package com.example.abhijeet.bloodbank;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EmergencyChatActivity extends AppCompatActivity {

    private String emergencyId = "";
    private TextView tvPatientName, tvHospitalName, tvBloodBadge, tvDistanceEta, tvDestinationAddress, tvFleetTransitSummary, tvEmpty;
    private View btnBack, cardNavigation;
    private MaterialButton btnNavigateMaps, btnSend;
    private MaterialButton chipOnWay, chipInTraffic, chipReachedGate, chipAtDesk, chipDonationStarted;
    private EditText etInput;
    private ProgressBar pbLoading;
    private RecyclerView recyclerMessages;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<ChatMessage> pendingOfflineQueue = new ArrayList<>();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPollingActive = false;

    private double destLat = 0.0;
    private double destLng = 0.0;
    private String destinationHospitalName = "Hospital";

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPollingActive) {
                fetchMessagesSilently();
                fetchFleetTrackingSilently();
                pollHandler.postDelayed(this, 3500); // 3.5 seconds polling interval
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_emergency_chat);

        emergencyId = getIntent().getStringExtra("emergency_id");
        if (emergencyId == null || emergencyId.isEmpty()) {
            emergencyId = getIntent().getStringExtra("requestId");
        }
        if (emergencyId == null || emergencyId.isEmpty()) {
            Toast.makeText(this, "Emergency reference not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
        loadChatInitial();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_chat_back);
        tvPatientName = findViewById(R.id.tv_chat_patient_name);
        tvHospitalName = findViewById(R.id.tv_chat_hospital_name);
        tvBloodBadge = findViewById(R.id.tv_chat_blood_badge);
        tvDistanceEta = findViewById(R.id.tv_chat_distance_eta);
        tvDestinationAddress = findViewById(R.id.tv_chat_destination_address);
        tvFleetTransitSummary = findViewById(R.id.tv_chat_fleet_transit_summary);
        cardNavigation = findViewById(R.id.card_chat_navigation);
        btnNavigateMaps = findViewById(R.id.btn_chat_navigate_maps);

        chipOnWay = findViewById(R.id.chip_action_on_way);
        chipInTraffic = findViewById(R.id.chip_action_in_traffic);
        chipReachedGate = findViewById(R.id.chip_action_reached_gate);
        chipAtDesk = findViewById(R.id.chip_action_at_desk);
        chipDonationStarted = findViewById(R.id.chip_action_donation_started);

        etInput = findViewById(R.id.et_chat_input);
        btnSend = findViewById(R.id.btn_chat_send);
        pbLoading = findViewById(R.id.pb_chat_loading);
        tvEmpty = findViewById(R.id.tv_chat_empty);

        recyclerMessages = findViewById(R.id.recycler_chat_messages);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(lm);

        chatAdapter = new ChatAdapter(this, messageList);
        recyclerMessages.setAdapter(chatAdapter);
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTextMessage();
            }
        });

        // Google Maps Turn-by-Turn Action
        btnNavigateMaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchMapsNavigation();
            }
        });

        if (cardNavigation != null) {
            cardNavigation.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent trackIntent = new Intent(EmergencyChatActivity.this, LiveDonorTrackingActivity.class);
                    trackIntent.putExtra("emergency_id", emergencyId);
                    startActivity(trackIntent);
                }
            });
        }

        // Quick Action Milestone Chips
        if (chipOnWay != null) {
            chipOnWay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendQuickStatus("🚗 On my way to the hospital", "MILESTONE", null);
                }
            });
        }

        if (chipInTraffic != null) {
            chipInTraffic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendQuickStatus("🚦 Delayed in heavy traffic, still travelling", "MILESTONE", null);
                }
            });
        }

        if (chipReachedGate != null) {
            chipReachedGate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendQuickStatus("🏥 Reached Hospital Main Gate", "MILESTONE", null);
                }
            });
        }

        if (chipAtDesk != null) {
            chipAtDesk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendQuickStatus("📍 Present at Blood Bank Verification Desk", "MILESTONE", null);
                }
            });
        }

        if (chipDonationStarted != null) {
            chipDonationStarted.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendQuickStatus("🩸 Blood donation procedure started", "MILESTONE", null);
                }
            });
        }
    }

    private void launchMapsNavigation() {
        if (destLat != 0.0 && destLng != 0.0) {
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destLat + "," + destLng + "&mode=d");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to browser Google Maps
                Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + destLat + "," + destLng);
                startActivity(new Intent(Intent.ACTION_VIEW, webUri));
            }
        } else if (!destinationHospitalName.isEmpty()) {
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(destinationHospitalName));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Hospital destination location unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadChatInitial() {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);

        ApiClient.getInstance().getChatHistory(emergencyId, new ApiClient.ApiCallback<ApiClient.ChatHistoryResponse>() {
            @Override
            public void onSuccess(ApiClient.ChatHistoryResponse result) {
                if (isFinishing() || isDestroyed()) return;
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);

                if (result != null) {
                    if (result.emergency != null) {
                        bindEmergencyHeader(result.emergency);
                    }
                    if (result.messages != null) {
                        messageList.clear();
                        messageList.addAll(result.messages);
                        chatAdapter.notifyDataSetChanged();
                        scrollToBottom();
                    }
                }
                updateEmptyState();
                flushPendingOfflineMessages();
                fetchFleetTrackingSilently();
            }

            @Override
            public void onError(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                Toast.makeText(EmergencyChatActivity.this, errorMessage != null ? errorMessage : "Unable to load chat", Toast.LENGTH_LONG).show();
                if (errorMessage != null && errorMessage.toLowerCase().contains("accept")) {
                    finish();
                    return;
                }
                updateEmptyState();
            }
        });
    }

    private void fetchMessagesSilently() {
        ApiClient.getInstance().getChatHistory(emergencyId, new ApiClient.ApiCallback<ApiClient.ChatHistoryResponse>() {
            @Override
            public void onSuccess(ApiClient.ChatHistoryResponse result) {
                if (isFinishing() || isDestroyed() || result == null) return;
                if (result.messages != null && result.messages.size() != messageList.size()) {
                    messageList.clear();
                    messageList.addAll(result.messages);
                    chatAdapter.notifyDataSetChanged();
                    scrollToBottom();
                    updateEmptyState();
                }
                flushPendingOfflineMessages();
            }

            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void fetchFleetTrackingSilently() {
        ApiClient.getInstance().getEmergencyTracking(emergencyId, new ApiClient.ApiCallback<ApiClient.EmergencyTrackingResponse>() {
            @Override
            public void onSuccess(ApiClient.EmergencyTrackingResponse tracking) {
                if (isFinishing() || isDestroyed() || tracking == null) return;
                updateMultiDonorFleetBanner(tracking);
            }

            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void updateMultiDonorFleetBanner(ApiClient.EmergencyTrackingResponse tracking) {
        if (tvFleetTransitSummary == null) return;

        List<ApiClient.DonorTrackInfo> activeEnRoute = new ArrayList<>();
        if (tracking.donors != null) {
            for (ApiClient.DonorTrackInfo d : tracking.donors) {
                if ("TRAVELLING".equalsIgnoreCase(d.journeyStatus) || "ACCEPTED".equalsIgnoreCase(d.journeyStatus)) {
                    activeEnRoute.add(d);
                }
            }
        }

        if (activeEnRoute.isEmpty()) {
            tvFleetTransitSummary.setVisibility(View.GONE);
            return;
        }

        // Sort by shortest ETA ascending
        Collections.sort(activeEnRoute, new Comparator<ApiClient.DonorTrackInfo>() {
            @Override
            public int compare(ApiClient.DonorTrackInfo o1, ApiClient.DonorTrackInfo o2) {
                return Integer.compare(o1.etaMinutes, o2.etaMinutes);
            }
        });

        StringBuilder sb = new StringBuilder();
        if (activeEnRoute.size() == 1) {
            ApiClient.DonorTrackInfo d = activeEnRoute.get(0);
            String name = d.name != null ? d.name.split(" ")[0] : "Donor";
            sb.append("🚗 Transit: ").append(name).append(" (").append(d.bloodGroup != null ? d.bloodGroup : "O+").append(")");
            if (d.etaMinutes > 0) {
                sb.append(" ~").append(d.etaMinutes).append("m away");
            }
            if (d.distanceKm > 0) {
                sb.append(" (").append(String.format("%.1f km", d.distanceKm)).append(")");
            }
        } else {
            sb.append("🚗 Fleet (").append(activeEnRoute.size()).append(" En Route): ");
            for (int i = 0; i < activeEnRoute.size(); i++) {
                ApiClient.DonorTrackInfo d = activeEnRoute.get(i);
                String name = d.name != null ? d.name.split(" ")[0] : "Donor";
                sb.append(name).append(" (").append(d.bloodGroup != null ? d.bloodGroup : "O+").append(")");
                if (d.etaMinutes > 0) {
                    sb.append(" ~").append(d.etaMinutes).append("m");
                }
                if (i < activeEnRoute.size() - 1) {
                    sb.append(" • ");
                }
            }
        }

        tvFleetTransitSummary.setText(sb.toString());
        tvFleetTransitSummary.setVisibility(View.VISIBLE);
    }

    private void bindEmergencyHeader(JsonObject em) {
        String patient = em.has("patientName") ? em.get("patientName").getAsString() : "Emergency Patient";
        String hospital = em.has("hospital") ? em.get("hospital").getAsString() : "Hospital";
        String bg = em.has("bloodGroup") ? em.get("bloodGroup").getAsString() : "O+";
        String address = em.has("hospitalAddress") ? em.get("hospitalAddress").getAsString() : "";
        String status = em.has("status") ? em.get("status").getAsString() : "ACTIVE";

        destinationHospitalName = hospital;
        tvPatientName.setText(patient);
        tvHospitalName.setText(hospital);
        tvBloodBadge.setText(bg);
        if (!address.isEmpty()) {
            tvDestinationAddress.setText(address);
        } else {
            tvDestinationAddress.setText(hospital);
        }

        if ("RESOLVED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status) || "FULFILLED".equalsIgnoreCase(status)) {
            if (etInput != null) {
                etInput.setEnabled(false);
                etInput.setHint("Coordination closed (Request " + status.toLowerCase() + ")");
            }
            if (btnSend != null) btnSend.setEnabled(false);
            if (chipOnWay != null) chipOnWay.setEnabled(false);
            if (chipInTraffic != null) chipInTraffic.setEnabled(false);
            if (chipReachedGate != null) chipReachedGate.setEnabled(false);
            if (chipAtDesk != null) chipAtDesk.setEnabled(false);
            if (chipDonationStarted != null) chipDonationStarted.setEnabled(false);
        }

        if (em.has("hospitalCoordinates") && em.get("hospitalCoordinates").isJsonObject()) {
            JsonObject coords = em.getAsJsonObject("hospitalCoordinates");
            destLat = coords.has("latitude") ? coords.get("latitude").getAsDouble() : 0.0;
            destLng = coords.has("longitude") ? coords.get("longitude").getAsDouble() : 0.0;
        }

        // Compute local distance & estimated drive time if user has known location
        double[] userLoc = DataManager.getInstance(this).getLastKnownLocation();
        if (userLoc != null && destLat != 0.0 && destLng != 0.0) {
            double distKm = LocationHelper.calculateDistanceKm(userLoc[0], userLoc[1], destLat, destLng);
            int estDriveMins = Math.max(5, (int) Math.round((distKm / 30.0) * 60)); // assume 30 km/h city average
            tvDistanceEta.setText(hospital + " • " + String.format("%.1f km", distKm) + " (ETA ~" + estDriveMins + "m)");
        } else {
            tvDistanceEta.setText(hospital);
        }
    }

    private void sendTextMessage() {
        String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) return;

        etInput.setText("");

        // Optimistic local update
        final ChatMessage localMsg = new ChatMessage(text, "TEXT", true);
        messageList.add(localMsg);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
        updateEmptyState();

        ApiClient.getInstance().sendChatMessage(emergencyId, text, "TEXT", new ApiClient.ApiCallback<ChatMessage>() {
            @Override
            public void onSuccess(ChatMessage result) {
                if (result != null) {
                    int idx = messageList.indexOf(localMsg);
                    if (idx != -1) {
                        messageList.set(idx, result);
                        chatAdapter.notifyItemChanged(idx);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                pendingOfflineQueue.add(localMsg);
                Toast.makeText(EmergencyChatActivity.this, "Network offline: Message queued for auto-resend", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendQuickStatus(final String statusText, final String messageType, @Nullable final Integer etaMins) {
        double[] userLoc = DataManager.getInstance(this).getLastKnownLocation();
        Double lat = userLoc != null ? userLoc[0] : null;
        Double lng = userLoc != null ? userLoc[1] : null;

        final ChatMessage localMsg = new ChatMessage(statusText, messageType != null ? messageType : "MILESTONE", true);
        messageList.add(localMsg);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
        updateEmptyState();

        ApiClient.getInstance().sendChatEta(emergencyId, etaMins, lat, lng, statusText, new ApiClient.ApiCallback<ChatMessage>() {
            @Override
            public void onSuccess(ChatMessage result) {
                if (result != null) {
                    int idx = messageList.indexOf(localMsg);
                    if (idx != -1) {
                        messageList.set(idx, result);
                        chatAdapter.notifyItemChanged(idx);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                pendingOfflineQueue.add(localMsg);
                Toast.makeText(EmergencyChatActivity.this, "Network offline: Milestone queued for auto-resend", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void flushPendingOfflineMessages() {
        if (pendingOfflineQueue.isEmpty()) return;

        List<ChatMessage> queueCopy = new ArrayList<>(pendingOfflineQueue);
        pendingOfflineQueue.clear();

        for (final ChatMessage msg : queueCopy) {
            String text = msg.getMessageText();
            String type = msg.getMessageType() != null ? msg.getMessageType() : "TEXT";

            ApiClient.getInstance().sendChatMessage(emergencyId, text, type, new ApiClient.ApiCallback<ChatMessage>() {
                @Override
                public void onSuccess(ChatMessage result) {
                    int idx = messageList.indexOf(msg);
                    if (idx != -1 && result != null) {
                        messageList.set(idx, result);
                        chatAdapter.notifyItemChanged(idx);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    pendingOfflineQueue.add(msg); // re-queue on failure
                }
            });
        }
    }

    private void scrollToBottom() {
        if (!messageList.isEmpty()) {
            recyclerMessages.smoothScrollToPosition(messageList.size() - 1);
        }
    }

    private void updateEmptyState() {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(messageList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPollingActive = true;
        pollHandler.postDelayed(pollRunnable, 3500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPollingActive = false;
        pollHandler.removeCallbacks(pollRunnable);
    }
}
