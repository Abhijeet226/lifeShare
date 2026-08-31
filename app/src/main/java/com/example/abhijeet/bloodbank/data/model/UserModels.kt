package com.example.abhijeet.bloodbank.data.model

import com.google.gson.annotations.SerializedName

/**
 * Immutable User Profile model
 */
data class UserProfile(
    @SerializedName("_id", alternate = ["id", "userId"])
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val bloodGroup: String = "O+",
    val location: String = "Bhubaneswar",
    val role: String = "DONOR", // "DONOR", "RECIPIENT", "COORDINATOR", "ADMIN"
    val availabilityStatus: String = "AVAILABLE", // "AVAILABLE", "BUSY", "COOLDOWN"
    val isAvailable: Boolean = true,
    val lastDonationDate: String? = null,
    val totalDonations: Int = 0,
    val hospitalId: String? = null,
    val hospitalName: String? = null,
    val isVerified: Boolean = false,
    val accountStatus: String = "ACTIVE",
    val karmaScore: Int = 0,
    val profilePhotoUrl: String? = null,
    val token: String? = null
) {
    val isCoordinator: Boolean
        get() = role.equals("COORDINATOR", ignoreCase = true)

    val isAdmin: Boolean
        get() = role.equals("ADMIN", ignoreCase = true)
}

/**
 * Login / Auth Response
 */
data class AuthResponse(
    val success: Boolean = false,
    val message: String = "",
    val token: String? = null,
    val user: UserProfile? = null
)

/**
 * City Model
 */
data class CityModel(
    val id: String = "",
    val name: String = "",
    val stateName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

/**
 * Blood Bank Center
 */
data class BloodBankCenter(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val availableGroups: List<String> = emptyList()
)
