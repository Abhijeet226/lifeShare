package com.example.abhijeet.bloodbank.data.repository

import kotlin.math.*

object LocationHelper {

    /**
     * Compute Haversine great-circle distance between two GPS coordinates in kilometers
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in KM
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Compute Haversine distance in meters
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return calculateDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    /**
     * Estimate driving ETA in minutes assuming 30 km/h average city transit speed
     */
    fun estimateEtaMinutes(distKm: Double): Int {
        return max(5, round((distKm / 30.0) * 60).toInt())
    }
}
