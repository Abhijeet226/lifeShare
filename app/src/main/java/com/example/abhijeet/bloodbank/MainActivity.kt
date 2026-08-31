package com.example.abhijeet.bloodbank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.abhijeet.bloodbank.data.repository.DataManager
import com.example.abhijeet.bloodbank.ui.navigation.LifeShareNavHost
import com.example.abhijeet.bloodbank.ui.theme.LifeShareTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val dataManager = DataManager.getInstance(this)
        val amoledPref = dataManager.darkModePreference == "AMOLED"

        setContent {
            LifeShareTheme(
                amoledMode = amoledPref
            ) {
                val navController = rememberNavController()
                LifeShareNavHost(navController = navController)
            }
        }
    }
}
