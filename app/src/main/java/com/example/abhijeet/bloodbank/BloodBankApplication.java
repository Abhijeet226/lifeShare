package com.example.abhijeet.bloodbank;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class BloodBankApplication extends Application {

    private static final String TAG = "LifeShareApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // Global crash guard to prevent unexpected terminations
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                Log.e(TAG, "Uncaught exception in " + thread.getName() + ": " + throwable.getMessage(), throwable);
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });

        // Apply user saved Theme Mode (System Default, Light, or Dark)
        try {
            DataManager.getInstance(this).applyThemeMode();
        } catch (Throwable t) {
            Log.w(TAG, "Theme mode init error", t);
        }

        // Apply Android Material You Dynamic Colors across all activities
        try {
            com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this);
        } catch (Throwable t) {
            Log.w(TAG, "Dynamic colors init error", t);
        }

        // Initialize notification channels safely
        try {
            NotificationHelper.createNotificationChannels(this);
        } catch (Throwable t) {
            Log.w(TAG, "Notification channel init error", t);
        }
    }
}
