package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;
    private static final int TYPE_SYSTEM = 3;

    private final Context context;
    private final List<ChatMessage> messages;

    public ChatAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg == null) return TYPE_SENT;
        if (msg.isSystemEvent()) {
            return TYPE_SYSTEM;
        } else if (msg.isSelf()) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_SENT) {
            View v = inflater.inflate(R.layout.item_chat_message_sent, parent, false);
            return new SentViewHolder(v);
        } else if (viewType == TYPE_SYSTEM) {
            View v = inflater.inflate(R.layout.item_chat_message_system, parent, false);
            return new SystemViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_chat_message_received, parent, false);
            return new ReceivedViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        if (msg == null) return;

        if (holder instanceof SentViewHolder) {
            SentViewHolder h = (SentViewHolder) holder;
            h.tvText.setText(msg.getMessageText());
            h.tvTime.setText(msg.getFormattedTime());
        } else if (holder instanceof ReceivedViewHolder) {
            ReceivedViewHolder h = (ReceivedViewHolder) holder;
            h.tvText.setText(msg.getMessageText());
            h.tvSender.setText(msg.getSenderName());
            h.tvTime.setText(msg.getFormattedTime());

            String role = msg.getSenderRole();
            if ("COORDINATOR".equalsIgnoreCase(role)) {
                h.tvRoleBadge.setText("HOSPITAL STAFF");
                h.tvRoleBadge.setBackgroundResource(R.drawable.badge_pill_urgent);
                h.tvRoleBadge.setTextColor(Color.WHITE);
                h.tvRoleBadge.setVisibility(View.VISIBLE);
            } else if ("REQUESTER".equalsIgnoreCase(role)) {
                h.tvRoleBadge.setText("SOS REQUESTER");
                h.tvRoleBadge.setBackgroundResource(R.drawable.badge_busy);
                h.tvRoleBadge.setTextColor(Color.WHITE);
                h.tvRoleBadge.setVisibility(View.VISIBLE);
            } else if ("ADMIN".equalsIgnoreCase(role)) {
                h.tvRoleBadge.setText("ADMIN");
                h.tvRoleBadge.setBackgroundResource(R.drawable.badge_pill_gray);
                h.tvRoleBadge.setTextColor(Color.DKGRAY);
                h.tvRoleBadge.setVisibility(View.VISIBLE);
            } else {
                h.tvRoleBadge.setText("DONOR");
                h.tvRoleBadge.setBackgroundResource(R.drawable.badge_available);
                h.tvRoleBadge.setTextColor(Color.parseColor("#1B5E20"));
                h.tvRoleBadge.setVisibility(View.VISIBLE);
            }
        } else if (holder instanceof SystemViewHolder) {
            SystemViewHolder h = (SystemViewHolder) holder;
            h.tvText.setText(msg.getMessageText());
            h.tvTime.setText(msg.getFormattedTime());
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView tvText, tvTime;
        SentViewHolder(View v) {
            super(v);
            tvText = v.findViewById(R.id.tv_chat_text_sent);
            tvTime = v.findViewById(R.id.tv_chat_time_sent);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvRoleBadge, tvText, tvTime;
        ReceivedViewHolder(View v) {
            super(v);
            tvSender = v.findViewById(R.id.tv_chat_sender_name);
            tvRoleBadge = v.findViewById(R.id.tv_chat_role_badge);
            tvText = v.findViewById(R.id.tv_chat_text_received);
            tvTime = v.findViewById(R.id.tv_chat_time_received);
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvText, tvTime;
        SystemViewHolder(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_system_icon);
            tvText = v.findViewById(R.id.tv_chat_system_text);
            tvTime = v.findViewById(R.id.tv_chat_system_time);
        }
    }
}
