package com.example.abhijeet.bloodbank;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class NotificationCenterActivity extends AppCompatActivity implements NotificationAdapter.OnNotificationActionListener {

    private ImageButton btnBack, btnMarkAllRead, btnClearAll;
    private TextView tvUnreadSummary;
    private ChipGroup chipGroupChannels;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvNotifications;
    private LinearLayout layoutEmpty;

    private NotificationAdapter adapter;
    private final List<InAppNotification> notificationList = new ArrayList<>();
    private String selectedChannel = "ALL";
    private int unreadCount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_notification_center);

        initViews();
        setupRecyclerView();
        setupListeners();
        loadNotifications();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_notif_back);
        btnMarkAllRead = findViewById(R.id.btn_mark_all_read);
        btnClearAll = findViewById(R.id.btn_clear_all);
        tvUnreadSummary = findViewById(R.id.tv_unread_summary);
        chipGroupChannels = findViewById(R.id.chip_group_channels);
        swipeRefresh = findViewById(R.id.swipe_refresh_notifications);
        rvNotifications = findViewById(R.id.rv_notifications);
        layoutEmpty = findViewById(R.id.layout_notif_empty);
    }

    private void setupRecyclerView() {
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this, notificationList, this);
        rvNotifications.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        swipeRefresh.setOnRefreshListener(this::loadNotifications);

        chipGroupChannels.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_channel_all) {
                selectedChannel = "ALL";
            } else if (checkedId == R.id.chip_channel_emergency) {
                selectedChannel = "EMERGENCY";
            } else if (checkedId == R.id.chip_channel_chat) {
                selectedChannel = "CHAT";
            } else if (checkedId == R.id.chip_channel_certificates) {
                selectedChannel = "CERTIFICATES";
            } else if (checkedId == R.id.chip_channel_updates) {
                selectedChannel = "UPDATES";
            }
            loadNotifications();
        });

        btnMarkAllRead.setOnClickListener(v -> {
            if (notificationList.isEmpty() || unreadCount == 0) {
                Toast.makeText(this, "All notifications are already read", Toast.LENGTH_SHORT).show();
                return;
            }
            ApiClient.getInstance().markAllNotificationsRead(selectedChannel, new ApiClient.ApiCallback<Integer>() {
                @Override
                public void onSuccess(Integer remainingUnread) {
                    unreadCount = remainingUnread != null ? remainingUnread : 0;
                    for (InAppNotification notif : notificationList) {
                        notif.setRead(true);
                    }
                    adapter.notifyDataSetChanged();
                    updateUnreadHeader();
                    Toast.makeText(NotificationCenterActivity.this, "Marked all as read", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(NotificationCenterActivity.this, "Failed to mark as read: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnClearAll.setOnClickListener(v -> {
            if (notificationList.isEmpty()) {
                Toast.makeText(this, "No notifications to clear", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Clear All Notifications")
                    .setMessage("Are you sure you want to clear your notifications?")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        ApiClient.getInstance().clearAllNotifications(selectedChannel, new ApiClient.ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                notificationList.clear();
                                adapter.notifyDataSetChanged();
                                unreadCount = 0;
                                updateUnreadHeader();
                                checkEmptyState();
                                Toast.makeText(NotificationCenterActivity.this, "Notifications cleared", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Toast.makeText(NotificationCenterActivity.this, "Failed to clear: " + errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadNotifications() {
        swipeRefresh.setRefreshing(true);
        ApiClient.getInstance().getNotifications(selectedChannel, 1, new ApiClient.ApiCallback<ApiClient.NotificationListResponse>() {
            @Override
            public void onSuccess(ApiClient.NotificationListResponse response) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefresh.setRefreshing(false);
                notificationList.clear();
                if (response != null && response.notifications != null) {
                    notificationList.addAll(response.notifications);
                    unreadCount = response.unreadCount;
                }
                adapter.notifyDataSetChanged();
                updateUnreadHeader();
                checkEmptyState();
            }

            @Override
            public void onError(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;
                swipeRefresh.setRefreshing(false);
                checkEmptyState();
            }
        });
    }

    private void updateUnreadHeader() {
        if (unreadCount > 0) {
            tvUnreadSummary.setText(unreadCount + " unread alert" + (unreadCount == 1 ? "" : "s"));
            tvUnreadSummary.setTextColor(getColor(R.color.brand_primary));
        } else {
            tvUnreadSummary.setText("All caught up");
            tvUnreadSummary.setTextColor(getColor(R.color.text_secondary));
        }
    }

    private void checkEmptyState() {
        boolean isEmpty = notificationList.isEmpty();
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvNotifications.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onNotificationClick(InAppNotification item) {
        if (item == null) return;

        // Mark as read
        if (!item.isRead()) {
            item.setRead(true);
            adapter.notifyDataSetChanged();
            ApiClient.getInstance().markNotificationRead(item.getId(), new ApiClient.ApiCallback<Integer>() {
                @Override
                public void onSuccess(Integer remainingUnread) {
                    unreadCount = remainingUnread != null ? remainingUnread : Math.max(0, unreadCount - 1);
                    updateUnreadHeader();
                }

                @Override
                public void onError(String errorMessage) {}
            });
        }

        // Deep-link routing based on channel / payload
        String channel = item.getChannel();
        String requestId = item.getRequestId();

        if ("EMERGENCY".equalsIgnoreCase(channel) || (requestId != null && !requestId.isEmpty())) {
            if ("TRAVELLING".equalsIgnoreCase(item.getStatus()) || "ACCEPTED".equalsIgnoreCase(item.getStatus())) {
                Intent mapIntent = new Intent(this, LiveDonorTrackingActivity.class);
                mapIntent.putExtra("emergency_id", requestId);
                startActivity(mapIntent);
            } else {
                Intent detailIntent = new Intent(this, EmergencyDetailActivity.class);
                detailIntent.putExtra("emergency_id", requestId);
                detailIntent.putExtra("patient_name", item.getPatientName());
                detailIntent.putExtra("hospital_name", item.getHospitalName());
                detailIntent.putExtra("blood_group", item.getBloodGroup());
                detailIntent.putExtra("units_required", item.getUnits());
                startActivity(detailIntent);
            }
        } else if ("CHAT".equalsIgnoreCase(channel) || (item.getChatRoomId() != null && !item.getChatRoomId().isEmpty())) {
            onOpenChat(item);
        } else if ("CERTIFICATES".equalsIgnoreCase(channel)) {
            Intent certIntent = new Intent(this, ProfileActivity.class);
            startActivity(certIntent);
        }
    }

    @Override
    public void onOpenChat(InAppNotification item) {
        if (item == null) return;
        String reqId = item.getRequestId();
        Intent chatIntent = new Intent(this, EmergencyChatActivity.class);
        chatIntent.putExtra("emergency_id", reqId);
        chatIntent.putExtra("patient_name", item.getPatientName() != null ? item.getPatientName() : "Emergency Patient");
        chatIntent.putExtra("hospital_name", item.getHospitalName() != null ? item.getHospitalName() : "Hospital");
        startActivity(chatIntent);
    }

    @Override
    public void onNotificationDismiss(InAppNotification item, int position) {
        if (item == null || position < 0 || position >= notificationList.size()) return;

        final InAppNotification removedItem = notificationList.remove(position);
        adapter.notifyItemRemoved(position);
        checkEmptyState();

        ApiClient.getInstance().deleteNotification(removedItem.getId(), new ApiClient.ApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer remainingUnread) {
                unreadCount = remainingUnread != null ? remainingUnread : unreadCount;
                updateUnreadHeader();
            }

            @Override
            public void onError(String errorMessage) {}
        });

        Snackbar.make(rvNotifications, "Notification dismissed", Snackbar.LENGTH_LONG)
                .setAction("Undo", v -> {
                    notificationList.add(position, removedItem);
                    adapter.notifyItemInserted(position);
                    checkEmptyState();
                })
                .show();
    }
}
