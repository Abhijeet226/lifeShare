package com.example.abhijeet.bloodbank;

import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "LifeShareFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Registration Token: " + token);

        DataManager dm = DataManager.getInstance(getApplicationContext());
        dm.saveFcmToken(token);

        if (dm.isLoggedIn()) {
            ApiClient.getInstance().registerDeviceToken(token, new ApiClient.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Log.d(TAG, "FCM token synced with LifeShare backend.");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.w(TAG, "Failed to sync FCM token: " + errorMessage);
                }
            });
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message Received from: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        String notificationType = data.containsKey("notificationType") ? data.get("notificationType") : "GENERAL";
        String requestId = data.containsKey("requestId") ? data.get("requestId") : "";
        String certificateId = data.containsKey("certificateId") ? data.get("certificateId") : "";
        String bloodGroup = data.containsKey("bloodGroup") ? data.get("bloodGroup") : "Blood";
        String hospital = data.containsKey("hospital") ? data.get("hospital") : "Hospital";
        String urgency = data.containsKey("urgency") ? data.get("urgency") : "URGENT";
        String units = data.containsKey("units") ? data.get("units") : "1";

        String title = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getTitle() : null;
        String body = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getBody() : null;

        if (title == null || title.isEmpty()) {
            if ("EMERGENCY_REQUEST".equals(notificationType)) {
                title = "Urgent " + bloodGroup + " Blood Needed!";
                body = "Critical need for " + units + " unit(s) at " + hospital + ". Tap to respond.";
            } else if ("DONOR_RESPONSE".equals(notificationType)) {
                title = "Donor Responded!";
                body = "A voluntary donor has responded to your emergency request at " + hospital + ".";
            } else if ("DONATION_VERIFIED".equals(notificationType)) {
                title = "Blood Donation Verified!";
                body = "Your voluntary blood donation at " + hospital + " has been certified. Tap to view certificate.";
            } else {
                title = "LifeShare Notification";
                body = "New update received on LifeShare voluntary blood network.";
            }
        }

        NotificationHelper.showFcmPushNotification(
                getApplicationContext(),
                title,
                body,
                notificationType,
                requestId,
                certificateId
        );
    }
}
