package com.example.abhijeet.bloodbank;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationHelper {

    public static final String CHANNEL_EMERGENCY_ID = "lifeshare_emergency_sos";
    public static final String CHANNEL_EMERGENCY_NAME = "Emergency SOS Alerts";
    public static final String CHANNEL_GENERAL_ID = "lifeshare_general";
    public static final String CHANNEL_GENERAL_NAME = "LifeShare Updates";

    private static final String PREF_NOTIFICATIONS = "lifeshare_notif_prefs";
    private static final String KEY_SEEN_SOS_IDS = "seen_sos_ids";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            // 1. Emergency Channel (High Priority with Vibration & Red LED)
            NotificationChannel emergencyChannel = new NotificationChannel(
                    CHANNEL_EMERGENCY_ID,
                    CHANNEL_EMERGENCY_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            emergencyChannel.setDescription("Instant urgent blood need alerts matching your blood group and Odisha region");
            emergencyChannel.enableLights(true);
            emergencyChannel.setLightColor(Color.RED);
            emergencyChannel.enableVibration(true);
            emergencyChannel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            manager.createNotificationChannel(emergencyChannel);

            // 2. General Channel
            NotificationChannel generalChannel = new NotificationChannel(
                    CHANNEL_GENERAL_ID,
                    CHANNEL_GENERAL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            generalChannel.setDescription("LifeShare donor updates and reminders");
            manager.createNotificationChannel(generalChannel);
        }
    }

    public static void showEmergencySosNotification(
            Context context,
            String patientName,
            String hospital,
            String city,
            String bloodGroup,
            int unitsNeeded,
            String contactPhone
    ) {
        showEmergencySosNotification(context, patientName, hospital, city, bloodGroup, unitsNeeded, contactPhone, false);
    }

    public static void showEmergencySosNotification(
            Context context,
            String patientName,
            String hospital,
            String city,
            String bloodGroup,
            int unitsNeeded,
            String contactPhone,
            boolean isDirectBloodMatch
    ) {
        createNotificationChannels(context);

        Intent openAppIntent = new Intent(context, LogInActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context, 101, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action: Quick Call
        PendingIntent callPendingIntent = null;
        if (contactPhone != null && !contactPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contactPhone.trim()));
            callPendingIntent = PendingIntent.getActivity(
                    context, 102, callIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        String locationStr = city.isEmpty() ? "Odisha" : city;
        String title = isDirectBloodMatch
                ? "MATCH: Your " + bloodGroup + " Blood is Needed in " + locationStr + "!"
                : "URGENT: " + bloodGroup + " Blood Needed in " + locationStr + "!";

        String content = patientName + " at " + hospital + " needs " + unitsNeeded + (unitsNeeded == 1 ? " unit" : " units") + " urgently.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_EMERGENCY_ID)
                .setSmallIcon(R.drawable.ic_nav_emergency)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content + "\nTap to respond immediately or contact hospital coordinator."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(Color.parseColor("#C62828"))
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        if (callPendingIntent != null) {
            builder.addAction(R.drawable.ic_call, "Call Coordinator", callPendingIntent);
        }
        builder.addAction(R.drawable.ic_nav_emergency, "View SOS Hub", contentPendingIntent);

        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify((int) System.currentTimeMillis(), builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void checkAndNotifyNewEmergencies(Context context, List<EmergencyRequest> requests, UserProfile currentUser) {
        if (context == null || requests == null || requests.isEmpty()) return;

        SharedPreferences sp = context.getSharedPreferences(PREF_NOTIFICATIONS, Context.MODE_PRIVATE);
        Set<String> seenIds = new HashSet<>(sp.getStringSet(KEY_SEEN_SOS_IDS, new HashSet<String>()));
        boolean hasNew = false;

        String userCity = (currentUser != null && currentUser.getCity() != null) ? currentUser.getCity().trim() : "";
        String userEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail().trim() : "";
        String userMobile = (currentUser != null && currentUser.getMobile() != null) ? currentUser.getMobile().trim().replaceAll("[^0-9]", "") : "";
        String userBlood = (currentUser != null && currentUser.getBloodGroup() != null) ? currentUser.getBloodGroup().trim() : "";

        for (EmergencyRequest req : requests) {
            if (req == null || req.getId() == null || req.getId().isEmpty()) continue;

            if (!seenIds.contains(req.getId())) {
                seenIds.add(req.getId());
                hasNew = true;

                // Don't alert the poster for their own SOS
                String contactNum = req.getContactNumber() != null ? req.getContactNumber().replaceAll("[^0-9]", "") : "";
                boolean isSelf = (!userEmail.isEmpty() && req.getPostedBy() != null && userEmail.equalsIgnoreCase(req.getPostedBy()))
                        || (!userMobile.isEmpty() && !contactNum.isEmpty() && userMobile.equals(contactNum));

                if (!isSelf) {
                    String reqCity = req.getCity() != null ? req.getCity() : "";
                    String reqHospital = req.getHospital() != null ? req.getHospital() : "";
                    String reqBlood = req.getBloodGroup() != null ? req.getBloodGroup() : "";

                    // 1. Area / Location Match
                    boolean matchesArea = userCity.isEmpty()
                            || reqCity.isEmpty()
                            || reqCity.equalsIgnoreCase(userCity)
                            || reqHospital.toLowerCase().contains(userCity.toLowerCase());

                    // 2. Blood Group Compatibility Match
                    boolean isCompatible = isBloodCompatible(userBlood, reqBlood);
                    boolean isDirectMatch = !userBlood.isEmpty() && userBlood.equalsIgnoreCase(reqBlood);

                    if (matchesArea && isCompatible) {
                        showEmergencySosNotification(
                                context,
                                req.getPatientName() != null ? req.getPatientName() : "Patient",
                                reqHospital,
                                reqCity,
                                reqBlood,
                                req.getUnitsNeeded(),
                                req.getContactNumber() != null ? req.getContactNumber() : "",
                                isDirectMatch
                        );
                    }
                }
            }
        }

        if (hasNew) {
            sp.edit().putStringSet(KEY_SEEN_SOS_IDS, seenIds).apply();
        }
    }

    /**
     * Checks if donor blood group can donate to requested recipient blood group
     */
    public static boolean isBloodCompatible(String donorGroup, String recipientGroup) {
        if (donorGroup == null || donorGroup.isEmpty() || recipientGroup == null || recipientGroup.isEmpty()) {
            return true; // If unspecified, allow alert
        }
        String d = donorGroup.trim().toUpperCase();
        String r = recipientGroup.trim().toUpperCase();

        if (d.equals(r)) return true; // Direct match

        // O- is universal donor
        if (d.equals("O-")) return true;

        // O+ can donate to O+, A+, B+, AB+
        if (d.equals("O+") && (r.equals("O+") || r.equals("A+") || r.equals("B+") || r.equals("AB+"))) return true;

        // A- can donate to A-, A+, AB-, AB+
        if (d.equals("A-") && (r.equals("A-") || r.equals("A+") || r.equals("AB-") || r.equals("AB+"))) return true;

        // A+ can donate to A+, AB+
        if (d.equals("A+") && (r.equals("A+") || r.equals("AB+"))) return true;

        // B- can donate to B-, B+, AB-, AB+
        if (d.equals("B-") && (r.equals("B-") || r.equals("B+") || r.equals("AB-") || r.equals("AB+"))) return true;

        // B+ can donate to B+, AB+
        if (d.equals("B+") && (r.equals("B+") || r.equals("AB+"))) return true;

        // AB- can donate to AB-, AB+
        if (d.equals("AB-") && (r.equals("AB-") || r.equals("AB+"))) return true;

        // AB+ can donate to AB+
        return d.equals("AB+") && r.equals("AB+");
    }

    public static void showFcmPushNotification(
            Context context,
            String title,
            String message,
            String notificationType,
            String requestId
    ) {
        showFcmPushNotification(context, title, message, notificationType, requestId, null);
    }

    public static void showFcmPushNotification(
            Context context,
            String title,
            String message,
            String notificationType,
            String requestId,
            String certificateId
    ) {
        createNotificationChannels(context);

        Intent intent;
        if ("DONATION_VERIFIED".equalsIgnoreCase(notificationType)) {
            if (certificateId != null && !certificateId.isEmpty()) {
                intent = new Intent(context, DonationCertificateActivity.class);
                intent.putExtra("certificate_id", certificateId);
            } else {
                intent = new Intent(context, DonationHistoryActivity.class);
            }
        } else if (requestId != null && !requestId.isEmpty()) {
            intent = new Intent(context, EmergencyDetailActivity.class);
            intent.putExtra("emergency_id", requestId);
        } else {
            intent = new Intent(context, LogInActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channelId = "EMERGENCY_REQUEST".equals(notificationType) ? CHANNEL_EMERGENCY_ID : CHANNEL_GENERAL_ID;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_nav_emergency)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority("EMERGENCY_REQUEST".equals(notificationType) ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_DEFAULT)
                .setColor(Color.parseColor("#C62828"))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify((int) System.currentTimeMillis(), builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
