package com.example.abhijeet.bloodbank.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.abhijeet.bloodbank.data.model.UserProfile
import com.google.gson.Gson

/**
 * Thread-safe persistent session and preferences manager in Kotlin
 */
class DataManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "LifeShareAppPrefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_PROFILE = "current_user_profile"
        private const val KEY_LAST_LAT = "last_known_lat"
        private const val KEY_LAST_LNG = "last_known_lng"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DARK_MODE = "dark_mode_preference" // "SYSTEM", "LIGHT", "DARK", "AMOLED"

        @Volatile
        private var instance: DataManager? = null

        fun getInstance(context: Context): DataManager {
            return instance ?: synchronized(this) {
                instance ?: DataManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var authToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    val isLoggedIn: Boolean
        get() = !authToken.isNullOrBlank()

    fun saveCurrentUser(user: UserProfile) {
        val json = gson.toJson(user)
        prefs.edit().putString(KEY_USER_PROFILE, json).apply()
        if (!user.token.isNullOrBlank()) {
            authToken = user.token
        }
    }

    fun getCurrentUser(): UserProfile? {
        val json = prefs.getString(KEY_USER_PROFILE, null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun setLastKnownLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_LAST_LAT, lat.toFloat())
            .putFloat(KEY_LAST_LNG, lng.toFloat())
            .apply()
    }

    fun getLastKnownLocation(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_LAST_LAT) || !prefs.contains(KEY_LAST_LNG)) return null
        val lat = prefs.getFloat(KEY_LAST_LAT, 0f).toDouble()
        val lng = prefs.getFloat(KEY_LAST_LNG, 0f).toDouble()
        return Pair(lat, lng)
    }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var darkModePreference: String
        get() = prefs.getString(KEY_DARK_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_DARK_MODE, value).apply()

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
