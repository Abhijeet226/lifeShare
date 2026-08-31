package com.example.abhijeet.bloodbank.data.model

import com.google.gson.annotations.SerializedName

/**
 * Immutable Kotlin domain model for Emergency Requests
 */
data class EmergencyRequest(
    @SerializedName("_id", alternate = ["id", "requestId"])
    val id: String = "",
    val patientName: String = "",
    val hospital: String = "",
    val hospitalId: String? = null,
    val hospitalAddress: String = "",
    val bloodGroup: String = "O+",
    val unitsRequired: Int = 1,
    val unitsFulfilled: Int = 0,
    val acceptedCount: Int = 0,
    val urgency: String = "URGENT",
    val status: String = "SEARCHING",
    val contactNumber: String = "",
    val postedBy: String = "",
    val city: String = "Bhubaneswar",
    val createdAt: String = "",
    val hospitalLatitude: Double = 20.2289,
    val hospitalLongitude: Double = 85.7770,
    val distanceKm: Double = 0.0,
    val timeAgo: String = "Just now"
) {
    val statusDisplay: String
        get() = EmergencyStatus.fromString(status).displayName

    val isCritical: Boolean
        get() = urgency.equals("CRITICAL", ignoreCase = true) || urgency.equals("HIGH", ignoreCase = true)
}

/**
 * Donor Journey State snapshot
 */
data class DonorJourneyInfo(
    val status: String = "NOTIFIED",
    val handshakeCode: String? = null,
    val etaMinutes: Int = 0,
    val distanceKm: Double = 0.0,
    val isPendingVerification: Boolean = false,
    val viewedAt: String? = null,
    val acceptedAt: String? = null,
    val travellingAt: String? = null,
    val arrivedAt: String? = null,
    val completedAt: String? = null
)

/**
 * Accepted Donor entry for requester view
 */
data class AcceptedDonorItem(
    val donorId: String = "",
    val name: String = "Voluntary Donor",
    val phone: String = "",
    val bloodGroup: String = "O+",
    val journeyStatus: String = "ACCEPTED",
    val journeyStatusDisplay: String = "Accepted",
    val handshakeCode: String? = null,
    val acceptedAt: String = "",
    val travellingAt: String? = null,
    val arrivedAt: String? = null
)

/**
 * Live Tracking Info for OSMDroid Map & Coordination
 */
data class DonorTrackInfo(
    val donorId: String = "",
    val name: String = "Donor",
    val phone: String = "",
    val bloodGroup: String = "O+",
    val journeyStatus: String = "ACCEPTED",
    val journeyStatusDisplay: String = "On The Way",
    val latitude: Double = 20.2961,
    val longitude: Double = 85.8245,
    val distanceKm: Double = 0.0,
    val etaMinutes: Int = 10
)

/**
 * Full Tracking Response from /tracking endpoint
 */
data class EmergencyTrackingResponse(
    val emergencyId: String = "",
    val patientName: String = "",
    val hospital: String = "",
    val hospitalAddress: String = "",
    val hospitalLat: Double = 20.2289,
    val hospitalLng: Double = 85.7770,
    val unitsRequired: Int = 1,
    val acceptedCount: Int = 0,
    val unitsFulfilled: Int = 0,
    val donors: List<DonorTrackInfo> = emptyList()
)

/**
 * Full Emergency Detail payload
 */
data class EmergencyDetailResponse(
    val emergency: EmergencyRequest? = null,
    val myJourney: DonorJourneyInfo? = null,
    val isRequester: Boolean = false,
    val notifiedCount: Int = 0,
    val acceptedCount: Int = 0,
    val remainingUnits: Int = 1,
    val acceptedDonors: List<AcceptedDonorItem> = emptyList()
)

/**
 * Coordinator Verification Queue Item
 */
data class PendingVerificationItem(
    val responseId: String = "",
    val requestId: String = "",
    val patientName: String = "Patient",
    val hospital: String = "Hospital",
    val bloodGroup: String = "O+",
    val unitsRequired: Int = 1,
    val unitsFulfilled: Int = 0,
    val donorId: String = "",
    val donorName: String = "Voluntary Donor",
    val donorMobile: String = "",
    val donorBloodGroup: String = "O+",
    val donorVerificationStatus: String = "UNVERIFIED",
    val handshakeCode: String = "",
    val arrivedAt: String = "",
    val createdAt: String = "",
    val status: String = "ARRIVED"
)
