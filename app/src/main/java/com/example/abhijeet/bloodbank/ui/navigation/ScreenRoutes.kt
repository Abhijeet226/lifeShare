package com.example.abhijeet.bloodbank.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object SignUp : Screen("signup")
    data object ForgotPassword : Screen("forgot_password")
    data object MainContainer : Screen("main_container") // Bottom nav hosting Home, Emergency, Search, Profile

    data object EmergencyDetail : Screen("emergency_detail/{emergencyId}") {
        fun createRoute(emergencyId: String) = "emergency_detail/$emergencyId"
    }

    data object EmergencyChat : Screen("emergency_chat/{emergencyId}") {
        fun createRoute(emergencyId: String) = "emergency_chat/$emergencyId"
    }

    data object LiveDonorTracking : Screen("live_tracking/{emergencyId}") {
        fun createRoute(emergencyId: String) = "live_tracking/$emergencyId"
    }

    data object CoordinatorVerification : Screen("coordinator_verification")
    data object DonationHistory : Screen("donation_history")
    data object NotificationCenter : Screen("notification_center")
    data object AdminDashboard : Screen("admin_dashboard")
    data object Setting : Screen("setting")
    data object Feedback : Screen("feedback")
    data object About : Screen("about")
    data object UpdateProfile : Screen("update_profile")
    data object UpdatePassword : Screen("update_password")

    data object DonationCertificate : Screen("donation_certificate/{certificateId}") {
        fun createRoute(certificateId: String) = "donation_certificate/$certificateId"
    }
}

enum class BottomNavTab(val route: String, val title: String) {
    Home("tab_home", "Home"),
    Emergency("tab_emergency", "SOS"),
    Search("tab_search", "Search"),
    Profile("tab_profile", "Profile");

    companion object {
        val ALL: List<BottomNavTab> get() = entries
    }
}
