package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationActionListener {
        void onNotificationClick(InAppNotification notification);
        void onNotificationDismiss(InAppNotification notification, int position);
        void onOpenChat(InAppNotification notification);
    }

    private final Context context;
    private final List<InAppNotification> notificationList;
    private final OnNotificationActionListener listener;

    public NotificationAdapter(Context context, List<InAppNotification> notificationList, OnNotificationActionListener listener) {
        this.context = context;
        this.notificationList = notificationList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InAppNotification item = notificationList.get(position);

        holder.tvTitle.setText(item.getTitle());
        holder.tvBody.setText(item.getBody());
        holder.tvTime.setText(formatTime(item.getUpdatedAt() != null && !item.getUpdatedAt().isEmpty() ? item.getUpdatedAt() : item.getCreatedAt()));

        // Unread Indicator Dot
        holder.viewUnreadDot.setVisibility(item.isRead() ? View.GONE : View.VISIBLE);
        holder.cardNotification.setAlpha(item.isRead() ? 0.85f : 1.0f);

        // Configure Category Icon & Theme
        configureChannelVisuals(holder, item);

        // Configure Status Pill
        configureStatusPill(holder, item);

        // Expanded details & mini-timeline
        boolean isExpanded = item.isExpanded();
        holder.layoutExpandedDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.ivChevron.setImageResource(isExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);

        // Fill expanded context if available
        if (item.getPatientName() != null && !item.getPatientName().isEmpty()) {
            holder.tvContextPatient.setText("Patient: " + item.getPatientName() + (item.getBloodGroup() != null && !item.getBloodGroup().isEmpty() ? " (" + item.getBloodGroup() + ")" : ""));
            holder.tvContextPatient.setVisibility(View.VISIBLE);
        } else {
            holder.tvContextPatient.setVisibility(View.GONE);
        }

        if (item.getHospitalName() != null && !item.getHospitalName().isEmpty()) {
            holder.tvContextHospital.setText("Hospital: " + item.getHospitalName());
            holder.tvContextHospital.setVisibility(View.VISIBLE);
        } else {
            holder.tvContextHospital.setVisibility(View.GONE);
        }

        // Action Buttons
        if ("CHAT".equalsIgnoreCase(item.getChannel())) {
            holder.btnChat.setVisibility(View.VISIBLE);
            holder.btnChat.setOnClickListener(v -> {
                if (listener != null) listener.onOpenChat(item);
            });
        } else if ("EMERGENCY".equalsIgnoreCase(item.getChannel()) &&
                ("ACCEPTED".equalsIgnoreCase(item.getStatus()) || "TRAVELLING".equalsIgnoreCase(item.getStatus()) || "ARRIVED".equalsIgnoreCase(item.getStatus()))) {
            holder.btnChat.setVisibility(View.VISIBLE);
            holder.btnChat.setOnClickListener(v -> {
                if (listener != null) listener.onOpenChat(item);
            });
        } else {
            holder.btnChat.setVisibility(View.GONE);
        }

        // Primary Action text
        if ("EMERGENCY".equalsIgnoreCase(item.getChannel())) {
            if ("TRAVELLING".equalsIgnoreCase(item.getStatus()) || "ACCEPTED".equalsIgnoreCase(item.getStatus())) {
                holder.btnPrimaryAction.setText("Track Live Route");
            } else if ("RESOLVED".equalsIgnoreCase(item.getStatus()) || "CANCELLED".equalsIgnoreCase(item.getStatus())) {
                holder.btnPrimaryAction.setText("View Summary");
            } else {
                holder.btnPrimaryAction.setText("View Emergency");
            }
        } else if ("CERTIFICATES".equalsIgnoreCase(item.getChannel())) {
            holder.btnPrimaryAction.setText("View Certificate");
        } else {
            holder.btnPrimaryAction.setText("Open");
        }

        // Card Click / Expand / Collapse
        View.OnClickListener toggleExpandListener = v -> {
            item.setExpanded(!item.isExpanded());
            notifyItemChanged(holder.getAdapterPosition());
        };

        holder.ivChevron.setOnClickListener(toggleExpandListener);
        holder.cardNotification.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(item);
            }
        });

        holder.btnPrimaryAction.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(item);
            }
        });

        holder.btnDismiss.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationDismiss(item, holder.getAdapterPosition());
            }
        });
    }

    private void configureChannelVisuals(ViewHolder holder, InAppNotification item) {
        String channel = item.getChannel();
        if ("EMERGENCY".equalsIgnoreCase(channel)) {
            holder.ivIcon.setImageResource(R.drawable.ic_notif_emergency);
            holder.layoutIconBg.setBackgroundResource(R.drawable.bg_circle_rose_soft);
        } else if ("CHAT".equalsIgnoreCase(channel)) {
            holder.ivIcon.setImageResource(R.drawable.ic_notif_chat);
            holder.layoutIconBg.setBackgroundResource(R.drawable.bg_circle_rose_soft);
        } else if ("CERTIFICATES".equalsIgnoreCase(channel)) {
            holder.ivIcon.setImageResource(R.drawable.ic_notif_verified);
            holder.layoutIconBg.setBackgroundResource(R.drawable.bg_circle_rose_soft);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_notif_updates);
            holder.layoutIconBg.setBackgroundResource(R.drawable.bg_circle_rose_soft);
        }
    }

    private void configureStatusPill(ViewHolder holder, InAppNotification item) {
        String status = item.getStatus();
        if (status == null || status.isEmpty() || "ACTIVE".equalsIgnoreCase(status)) {
            if ("EMERGENCY".equalsIgnoreCase(item.getChannel())) {
                holder.tvStatusPill.setText("URGENT");
                holder.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_danger);
                holder.tvStatusPill.setVisibility(View.VISIBLE);
            } else {
                holder.tvStatusPill.setVisibility(View.GONE);
            }
            return;
        }

        holder.tvStatusPill.setVisibility(View.VISIBLE);
        if ("TRAVELLING".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status)) {
            holder.tvStatusPill.setText("EN ROUTE");
            holder.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_urgent);
        } else if ("ARRIVED".equalsIgnoreCase(status)) {
            holder.tvStatusPill.setText("AT HOSPITAL");
            holder.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_urgent);
        } else if ("RESOLVED".equalsIgnoreCase(status) || "FULFILLED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
            holder.tvStatusPill.setText("RESOLVED");
            holder.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_fulfilled);
        } else if ("CANCELLED".equalsIgnoreCase(status)) {
            holder.tvStatusPill.setText("CANCELLED");
            holder.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_fulfilled);
        } else {
            holder.tvStatusPill.setText(status.toUpperCase());
            holder.tvStatusPill.setBackgroundResource(R.drawable.bg_pill_danger);
        }
    }

    private String formatTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "Just now";
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date d = iso.parse(dateStr);
            if (d == null) return "Recent";
            long diffMs = System.currentTimeMillis() - d.getTime();
            long mins = diffMs / (1000 * 60);
            if (mins < 1) return "Just now";
            if (mins < 60) return mins + "m ago";
            long hours = mins / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            return days + "d ago";
        } catch (Exception e) {
            return "Recent";
        }
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardNotification;
        FrameLayout layoutIconBg;
        ImageView ivIcon, ivChevron;
        TextView tvTitle, tvBody, tvTime, tvStatusPill;
        View viewUnreadDot;
        LinearLayout layoutExpandedDetails, layoutContextInfo;
        TextView tvContextPatient, tvContextHospital;
        MaterialButton btnDismiss, btnChat, btnPrimaryAction;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardNotification = itemView.findViewById(R.id.card_notification);
            layoutIconBg = itemView.findViewById(R.id.layout_notif_icon_bg);
            ivIcon = itemView.findViewById(R.id.iv_notif_icon);
            ivChevron = itemView.findViewById(R.id.iv_chevron);
            tvTitle = itemView.findViewById(R.id.tv_notif_title);
            tvBody = itemView.findViewById(R.id.tv_notif_body);
            tvTime = itemView.findViewById(R.id.tv_notif_time);
            tvStatusPill = itemView.findViewById(R.id.tv_notif_status_pill);
            viewUnreadDot = itemView.findViewById(R.id.view_unread_dot);
            layoutExpandedDetails = itemView.findViewById(R.id.layout_expanded_details);
            layoutContextInfo = itemView.findViewById(R.id.layout_context_info);
            tvContextPatient = itemView.findViewById(R.id.tv_context_patient);
            tvContextHospital = itemView.findViewById(R.id.tv_context_hospital);
            btnDismiss = itemView.findViewById(R.id.btn_notif_dismiss);
            btnChat = itemView.findViewById(R.id.btn_notif_chat);
            btnPrimaryAction = itemView.findViewById(R.id.btn_notif_primary_action);
        }
    }
}
