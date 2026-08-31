package com.example.abhijeet.bloodbank

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.data.repository.DataManager
import org.osmdroid.config.Configuration
import java.io.File

class BloodBankApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val dataManager = DataManager.getInstance(this)
        ApiRepository.getInstance().tokenProvider = { dataManager.authToken }

        // OSMDroid configuration
        val ctx: Context = applicationContext
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "LifeShare-Android-Compose/3.0"

        val osmBasePath = File(ctx.cacheDir, "osmdroid")
        val osmTilesPath = File(osmBasePath, "tiles")
        if (!osmTilesPath.exists()) osmTilesPath.mkdirs()
        Configuration.getInstance().osmdroidBasePath = osmBasePath
        Configuration.getInstance().osmdroidTileCache = osmTilesPath
    }
}
