package com.example.abhijeet.bloodbank.data.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Coordination Chat Message
 */
data class ChatMessage(
    @SerializedName("_id", alternate = ["id"])
    val id: String = "",
    val emergencyId: String = "",
    val senderId: String = "",
    val senderName: String = "LifeShare User",
    val senderRole: String = "DONOR",
    val messageText: String = "",
    val messageType: String = "TEXT", // "TEXT", "ETA_UPDATE", "STATUS_CHANGE", "MILESTONE", "SYSTEM"
    val isSystemEvent: Boolean = false,
    val etaMinutes: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String = "",
    val isMine: Boolean = false,
    val isPending: Boolean = false
)

/**
 * Chat History API Response
 */
data class ChatHistoryResponse(
    val emergency: JsonObject? = null,
    val messages: List<ChatMessage> = emptyList()
)

/**
 * Quick Action Milestone Definition
 */
data class MilestoneAction(
    val id: String,
    val label: String,
    val text: String,
    val iconEmoji: String
) {
    companion object {
        val ALL = listOf(
            MilestoneAction("on_way", "On My Way", "🚗 On my way to the hospital", "🚗"),
            MilestoneAction("in_traffic", "In Traffic", "🚦 Delayed in heavy traffic, still travelling", "🚦"),
            MilestoneAction("reached_gate", "Reached Gate", "🏥 Reached Hospital Main Gate", "🏥"),
            MilestoneAction("at_desk", "At Blood Bank Desk", "📍 Present at Blood Bank Verification Desk", "📍"),
            MilestoneAction("started", "Donation Started", "🩸 Blood donation procedure started", "🩸")
        )
    }
}
