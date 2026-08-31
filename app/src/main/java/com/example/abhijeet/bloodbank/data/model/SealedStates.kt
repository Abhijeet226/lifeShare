package com.example.abhijeet.bloodbank.data.model

/**
 * Generic Network Result State wrapper for Coroutines & Flow
 */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(val message: String, val statusCode: Int? = null) : NetworkResult<Nothing>
    data object Loading : NetworkResult<Nothing>
}

/**
 * Generic UI State wrapper for Compose ViewModels
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/**
 * Strict 5-Stage Donor Journey State Machine
 */
enum class JourneyState(val displayTitle: String, val stepNumber: Int) {
    NOTIFIED("Request Received", 1),
    VIEWED("Request Viewed", 1),
    ACCEPTED("Emergency Accepted", 2),
    DECLINED("Declined", 2),
    TRAVELLING("Travelling to Hospital", 3),
    ARRIVED("Arrived at Hospital", 4),
    DONATED("Donation Completed", 5),
    CANCELLED("Cancelled", 0),
    COMPLETED("Verified & Certified", 5);

    companion object {
        fun fromString(status: String?): JourneyState {
            return when (status?.uppercase()?.trim()) {
                "NOTIFIED" -> NOTIFIED
                "VIEWED" -> VIEWED
                "ACCEPTED" -> ACCEPTED
                "DECLINED" -> DECLINED
                "TRAVELLING" -> TRAVELLING
                "ARRIVED" -> ARRIVED
                "DONATED" -> DONATED
                "CANCELLED" -> CANCELLED
                "COMPLETED" -> COMPLETED
                else -> NOTIFIED
            }
        }
    }
}

/**
 * Emergency Request Status
 */
enum class EmergencyStatus(val displayName: String) {
    SEARCHING("Searching for Donors"),
    PARTIALLY_ACCEPTED("Partially Accepted"),
    FULFILLED("All Units Fulfilled"),
    RESOLVED("Completed & Resolved"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired");

    companion object {
        fun fromString(status: String?): EmergencyStatus {
            return when (status?.uppercase()?.trim()) {
                "SEARCHING" -> SEARCHING
                "PARTIALLY_ACCEPTED" -> PARTIALLY_ACCEPTED
                "FULFILLED" -> FULFILLED
                "RESOLVED" -> RESOLVED
                "CANCELLED" -> CANCELLED
                "EXPIRED" -> EXPIRED
                else -> SEARCHING
            }
        }
    }
}

/**
 * Blood Groups
 */
enum class BloodGroup(val code: String) {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-");

    companion object {
        val ALL = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    }
}
