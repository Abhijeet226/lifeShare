package com.example.abhijeet.bloodbank.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.abhijeet.bloodbank.ui.screens.*

@Composable
fun LifeShareNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.MainContainer.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.MainContainer.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(Screen.MainContainer.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MainContainer.route) {
            MainContainerScreen(
                onNavigateToEmergencyDetail = { id -> navController.navigate(Screen.EmergencyDetail.createRoute(id)) },
                onNavigateToSOSBroadcast = { navController.navigate(BottomNavTab.Emergency.route) },
                onNavigateToNotifications = { navController.navigate(Screen.NotificationCenter.route) },
                onNavigateToHistory = { navController.navigate(Screen.DonationHistory.route) },
                onNavigateToCoordinator = { navController.navigate(Screen.CoordinatorVerification.route) },
                onNavigateToSettings = { navController.navigate(Screen.Setting.route) },
                onNavigateToFeedback = { navController.navigate(Screen.Feedback.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.EmergencyDetail.route,
            arguments = listOf(navArgument("emergencyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId").orEmpty()
            EmergencyDetailScreen(
                emergencyId = emergencyId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { id -> navController.navigate(Screen.EmergencyChat.createRoute(id)) },
                onNavigateToTracking = { id -> navController.navigate(Screen.LiveDonorTracking.createRoute(id)) },
                onNavigateToCertificate = { id -> navController.navigate(Screen.DonationCertificate.createRoute(id)) }
            )
        }

        composable(
            route = Screen.EmergencyChat.route,
            arguments = listOf(navArgument("emergencyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId").orEmpty()
            EmergencyChatScreen(
                emergencyId = emergencyId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTracking = { id -> navController.navigate(Screen.LiveDonorTracking.createRoute(id)) }
            )
        }

        composable(
            route = Screen.LiveDonorTracking.route,
            arguments = listOf(navArgument("emergencyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId").orEmpty()
            LiveDonorTrackingScreen(
                emergencyId = emergencyId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { id -> navController.navigate(Screen.EmergencyChat.createRoute(id)) }
            )
        }

        composable(Screen.CoordinatorVerification.route) {
            CoordinatorVerificationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DonationHistory.route) {
            DonationHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCertificate = { certId -> navController.navigate(Screen.DonationCertificate.createRoute(certId)) }
            )
        }

        composable(
            route = Screen.DonationCertificate.route,
            arguments = listOf(navArgument("certificateId") { type = NavType.StringType })
        ) { backStackEntry ->
            val certId = backStackEntry.arguments?.getString("certificateId").orEmpty()
            DonationCertificateScreen(
                certificateId = certId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NotificationCenter.route) {
            NotificationCenterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Setting.route) {
            SettingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Feedback.route) {
            FeedbackScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
