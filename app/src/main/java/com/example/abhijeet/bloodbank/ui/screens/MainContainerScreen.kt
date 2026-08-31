package com.example.abhijeet.bloodbank.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.abhijeet.bloodbank.ui.navigation.BottomNavTab

@Composable
fun MainContainerScreen(
    onNavigateToEmergencyDetail: (String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToCoordinator: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(BottomNavTab.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavTab.ALL.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = getTabIcon(tab, isSelected),
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                BottomNavTab.Home -> HomeScreen(
                    onNavigateToEmergencyDetail = onNavigateToEmergencyDetail,
                    onNavigateToSOSBroadcast = { selectedTab = BottomNavTab.Emergency },
                    onNavigateToSearch = { selectedTab = BottomNavTab.Search },
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToCoordinator = onNavigateToCoordinator
                )
                BottomNavTab.Emergency -> EmergencyScreen(
                    onNavigateToEmergencyDetail = onNavigateToEmergencyDetail
                )
                BottomNavTab.Search -> SearchScreen()
                BottomNavTab.Profile -> ProfileScreen(
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToFeedback = onNavigateToFeedback,
                    onNavigateToAbout = onNavigateToAbout,
                    onLogout = onLogout
                )
            }
        }
    }
}

private fun getTabIcon(tab: BottomNavTab, selected: Boolean): ImageVector {
    return when (tab) {
        BottomNavTab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        BottomNavTab.Emergency -> if (selected) Icons.Filled.AddAlert else Icons.Outlined.AddAlert
        BottomNavTab.Search -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
        BottomNavTab.Profile -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    }
}
