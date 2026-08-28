package com.example.abhijeet.bloodbank;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class LocationHelper {

    public static final int LOCATION_PERMISSION_REQUEST_CODE = 801;

    public interface LocationCallback {
        void onLocationAcquired(double latitude, double longitude);
        void onLocationFailed(String errorMessage);
    }

    public static boolean hasLocationPermission(Context context) {
        if (context == null) return false;
        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }

    public static void requestLocationPermission(Activity activity) {
        if (activity == null) return;
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    public static boolean isGpsEnabled(Context context) {
        if (context == null) return false;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    @SuppressLint("MissingPermission")
    public static void getCurrentLocation(final Context context, final LocationCallback callback) {
        if (context == null || callback == null) return;

        if (!hasLocationPermission(context)) {
            callback.onLocationFailed("Location permission is required to acquire your GPS coordinates.");
            return;
        }

        if (!isGpsEnabled(context)) {
            callback.onLocationFailed("Please turn on location services (GPS) to search nearby donors.");
            return;
        }

        final FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);
        CancellationTokenSource cts = new CancellationTokenSource();

        // 1. Try high accuracy current location
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location loc = task.getResult();
                            callback.onLocationAcquired(loc.getLatitude(), loc.getLongitude());
                        } else {
                            // 2. Fallback to last known location
                            fusedClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                                @Override
                                public void onComplete(@NonNull Task<Location> lastTask) {
                                    if (lastTask.isSuccessful() && lastTask.getResult() != null) {
                                        Location lastLoc = lastTask.getResult();
                                        callback.onLocationAcquired(lastLoc.getLatitude(), lastLoc.getLongitude());
                                    } else {
                                        callback.onLocationFailed("Unable to obtain GPS fix. Please verify location settings.");
                                    }
                                }
                            });
                        }
                    }
                });
    }

    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius in kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round((R * c) * 10.0) / 10.0;
    }
}
