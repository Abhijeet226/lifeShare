package com.example.abhijeet.bloodbank;

public class ChatMessage {
    private String id;
    private String emergencyRequestId;
    private String senderId;
    private String senderName;
    private String senderRole;
    private String messageType;
    private String messageText;
    private Integer etaMinutes;
    private Double distanceKm;
    private boolean isSelf;
    private String createdAt;

    public ChatMessage() {}

    public ChatMessage(String messageText, String messageType, boolean isSelf) {
        this.messageText = messageText;
        this.messageType = messageType;
        this.isSelf = isSelf;
        this.senderName = "You";
        this.senderRole = "DONOR";
        this.createdAt = String.valueOf(System.currentTimeMillis());
    }

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getEmergencyRequestId() { return emergencyRequestId != null ? emergencyRequestId : ""; }
    public void setEmergencyRequestId(String emergencyRequestId) { this.emergencyRequestId = emergencyRequestId; }

    public String getSenderId() { return senderId != null ? senderId : ""; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName != null ? senderName : "User"; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole != null ? senderRole : "DONOR"; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getMessageType() { return messageType != null ? messageType : "TEXT"; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getMessageText() { return messageText != null ? messageText : ""; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public Integer getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Integer etaMinutes) { this.etaMinutes = etaMinutes; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public boolean isSelf() { return isSelf; }
    public void setSelf(boolean self) { isSelf = self; }

    public String getCreatedAt() { return createdAt != null ? createdAt : ""; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isSystemEvent() {
        return "ETA_UPDATE".equalsIgnoreCase(messageType) ||
                "STATUS_CHANGE".equalsIgnoreCase(messageType) ||
                "LOCATION_UPDATE".equalsIgnoreCase(messageType);
    }

    public String getFormattedTime() {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            if (createdAt.contains("T")) {
                int tIdx = createdAt.indexOf('T');
                if (createdAt.length() >= tIdx + 6) {
                    return createdAt.substring(tIdx + 1, tIdx + 6);
                }
            }
            long millis = Long.parseLong(createdAt);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
            return sdf.format(new java.util.Date(millis));
        } catch (Exception e) {
            return "";
        }
    }
}
