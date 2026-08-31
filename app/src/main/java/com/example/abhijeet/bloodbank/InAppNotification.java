package com.example.abhijeet.bloodbank;

import com.google.gson.JsonObject;
import java.io.Serializable;

public class InAppNotification implements Serializable {
    private String id;
    private String title;
    private String body;
    private String type; // EMERGENCY_REQUEST, DONOR_ACCEPTED, DONOR_ARRIVED, etc.
    private String channel; // EMERGENCY, CHAT, CERTIFICATES, UPDATES
    private String collapseKey;
    private String status; // URGENT, TRAVELLING, ARRIVED, RESOLVED, CANCELLED
    private boolean isRead;
    private String createdAt;
    private String updatedAt;

    // Metadata data payload
    private String requestId;
    private String emergencyId;
    private String chatRoomId;
    private String certificateId;
    private String donorId;
    private String donorName;
    private String patientName;
    private String hospitalName;
    private String bloodGroup;
    private int units;

    // UI state
    private boolean isExpanded = false;

    public InAppNotification() {}

    public InAppNotification(String id, String title, String body, String type, String channel) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.type = type;
        this.channel = channel;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body != null ? body : ""; }
    public void setBody(String body) { this.body = body; }

    public String getType() { return type != null ? type : "SYSTEM"; }
    public void setType(String type) { this.type = type; }

    public String getChannel() { return channel != null ? channel : "UPDATES"; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getCollapseKey() { return collapseKey; }
    public void setCollapseKey(String collapseKey) { this.collapseKey = collapseKey; }

    public String getStatus() { return status != null ? status : "ACTIVE"; }
    public void setStatus(String status) { this.status = status; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getRequestId() { return requestId != null && !requestId.isEmpty() ? requestId : emergencyId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getEmergencyId() { return emergencyId; }
    public void setEmergencyId(String emergencyId) { this.emergencyId = emergencyId; }

    public String getChatRoomId() { return chatRoomId; }
    public void setChatRoomId(String chatRoomId) { this.chatRoomId = chatRoomId; }

    public String getCertificateId() { return certificateId; }
    public void setCertificateId(String certificateId) { this.certificateId = certificateId; }

    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }

    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { isExpanded = expanded; }
}
