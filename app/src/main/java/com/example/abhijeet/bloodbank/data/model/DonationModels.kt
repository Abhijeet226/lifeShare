package com.example.abhijeet.bloodbank.data.model

import com.google.gson.annotations.SerializedName

/**
 * Digital Tamper-Proof Donation Certificate
 */
data class DonationCertificate(
    val certificateId: String = "",
    val donorName: String = "",
    val bloodGroup: String = "O+",
    val unitsDonated: Int = 1,
    val hospital: String = "",
    val donationDate: String = "",
    val verifiedAt: String = "",
    val status: String = "CERTIFIED",
    val attendingDoctor: String = "Attending Medical Officer",
    val doctorRegistrationNo: String = "",
    val verifiedBy: String = "",
    val certificateHash: String = "",
    val isTamperProofValid: Boolean = true
)

/**
 * Donation History Record
 */
data class DonationHistoryItem(
    @SerializedName("_id", alternate = ["id"])
    val id: String = "",
    val certificateId: String = "",
    val donorName: String = "",
    val donorBloodGroup: String = "O+",
    val hospital: String = "",
    val unitsDonated: Int = 1,
    val verifiedAt: String = "",
    val status: String = "COMPLETED"
)

/**
 * 90-Day Medical Eligibility Check
 */
data class EligibilityStatus(
    val isEligible: Boolean = true,
    val daysRemaining: Int = 0,
    val nextEligibleDate: String? = null
)

/**
 * In-App Notification Record
 */
data class InAppNotification(
    @SerializedName("_id", alternate = ["id"])
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val notificationType: String = "GENERAL",
    val requestId: String? = null,
    val donorId: String? = null,
    val createdAt: String = "",
    val isRead: Boolean = false
)
