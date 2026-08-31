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
    public static final String CHANNEL_CHAT_ID = "lifeshare_chat";
    public static final String CHANNEL_CHAT_NAME = "Emergency Coordination Chat";
    public static final String CHANNEL_CERTIFICATES_ID = "lifeshare_certificates";
    public static final String CHANNEL_CERTIFICATES_NAME = "Donation Certificates & Karma";
    public static final String CHANNEL_GENERAL_ID = "lifeshare_general";
    public static final String CHANNEL_GENERAL_NAME = "Blood Drives & Updates";

    private static final String PREF_NOTIFICATIONS = "lifeshare_notif_prefs";
    private static final String KEY_SEEN_SOS_IDS = "seen_sos_ids";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            // 1. Emergency Channel (Max Priority with Vibration & Red LED)
            NotificationChannel emergencyChannel = new NotificationChannel(
                    CHANNEL_EMERGENCY_ID,
                    CHANNEL_EMERGENCY_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            emergencyChannel.setDescription("Instant urgent blood need alerts matching your blood group and region");
            emergencyChannel.enableLights(true);
            emergencyChannel.setLightColor(Color.RED);
            emergencyChannel.enableVibration(true);
            emergencyChannel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            manager.createNotificationChannel(emergencyChannel);

            // 2. Chat Coordination Channel (High Priority with subtle sound/vibrate)
            NotificationChannel chatChannel = new NotificationChannel(
                    CHANNEL_CHAT_ID,
                    CHANNEL_CHAT_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            chatChannel.setDescription("Direct messages and ETA transit updates between donors and coordinators");
            chatChannel.enableLights(true);
            chatChannel.setLightColor(Color.BLUE);
            chatChannel.enableVibration(true);
            manager.createNotificationChannel(chatChannel);

            // 3. Certificates & Milestones Channel
            NotificationChannel certChannel = new NotificationChannel(
                    CHANNEL_CERTIFICATES_ID,
                    CHANNEL_CERTIFICATES_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            certChannel.setDescription("Verified blood donation certificates, Karma points, and 90-day cooldown alerts");
            certChannel.enableLights(true);
            certChannel.setLightColor(Color.GREEN);
            manager.createNotificationChannel(certChannel);

            // 4. Blood Drives & General Updates Channel
            NotificationChannel generalChannel = new NotificationChannel(
                    CHANNEL_GENERAL_ID,
                    CHANNEL_GENERAL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            generalChannel.setDescription("LifeShare voluntary blood donation camp announcements and tips");
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
        showEmergencySosNotification(context, patientName, hospital, city, bloodGroup, unitsNeeded, contactPhone, false, null);
    }

    public static void showEmergencySosNotification(
            Context context,
            String patientName,
            String hospital,
            String city,
            String bloodGroup,
            int unitsNeeded,
            String contactPhone,
            String requestId
    ) {
        showEmergencySosNotification(context, patientName, hospital, city, bloodGroup, unitsNeeded, contactPhone, false, requestId);
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
        showEmergencySosNotification(context, patientName, hospital, city, bloodGroup, unitsNeeded, contactPhone, isDirectBloodMatch, null);
    }

    public static void showEmergencySosNotification(
            Context context,
            String patientName,
            String hospital,
            String city,
            String bloodGroup,
            int unitsNeeded,
            String contactPhone,
            boolean isDirectBloodMatch,
            String requestId
    ) {
        createNotificationChannels(context);

        Intent openAppIntent;
        if (requestId != null && !requestId.isEmpty()) {
            openAppIntent = new Intent(context, EmergencyDetailActivity.class);
            openAppIntent.putExtra("emergency_id", requestId);
            openAppIntent.setData(Uri.parse("lifeshare://emergency/" + requestId));
        } else {
            openAppIntent = new Intent(context, LogInActivity.class);
        }
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
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content + "\nTap to view details and respond immediately."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setColor(Color.parseColor("#C62828"))
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        if (callPendingIntent != null) {
            builder.addAction(R.drawable.ic_call, "Call Coordinator", callPendingIntent);
        }
        builder.addAction(R.drawable.ic_nav_emergency, "View SOS Details", contentPendingIntent);

        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                int notifId = (requestId != null && !requestId.isEmpty()) ? ("sos_" + requestId).hashCode() : 1001;
                manager.notify(notifId, builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void markEmergencyAsSeen(Context context, String requestId) {
        if (context == null || requestId == null || requestId.isEmpty()) return;
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NOTIFICATIONS, Context.MODE_PRIVATE);
            Set<String> seenIds = new HashSet<>(sp.getStringSet(KEY_SEEN_SOS_IDS, new HashSet<String>()));
            seenIds.add(requestId);
            sp.edit().putStringSet(KEY_SEEN_SOS_IDS, seenIds).apply();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void checkAndNotifyNewEmergencies(Context context, List<EmergencyRequest> requests, UserProfile currentUser) {
        if (context == null || requests == null || requests.isEmpty()) return;

        // 1. Guardrail: If user is unavailable or under 90-day cooldown, suppress emergency alerts
        if (currentUser != null) {
            if (!currentUser.isAvailable() || !currentUser.isEligibleToDonate() || currentUser.getDaysRemaining() > 0) {
                return;
            }
        }

        SharedPreferences sp = context.getSharedPreferences(PREF_NOTIFICATIONS, Context.MODE_PRIVATE);
        Set<String> seenIds = new HashSet<>(sp.getStringSet(KEY_SEEN_SOS_IDS, new HashSet<String>()));
        boolean hasNew = false;

        String userId = (currentUser != null && currentUser.getId() != null) ? currentUser.getId().trim() : "";
        String userCity = (currentUser != null && currentUser.getCity() != null) ? currentUser.getCity().trim() : "";
        String userEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail().trim() : "";
        String userMobile = (currentUser != null && currentUser.getMobile() != null) ? currentUser.getMobile().trim().replaceAll("[^0-9]", "") : "";
        String userBlood = (currentUser != null && currentUser.getBloodGroup() != null) ? currentUser.getBloodGroup().trim() : "";

        for (EmergencyRequest req : requests) {
            if (req == null || req.getId() == null || req.getId().isEmpty()) continue;

            if (!seenIds.contains(req.getId())) {
                seenIds.add(req.getId());
                hasNew = true;

                // Robust Multi-Factor Self-Exclusion Check
                String contactNum = req.getContactNumber() != null ? req.getContactNumber().replaceAll("[^0-9]", "") : "";
                String reqPostedBy = req.getPostedBy() != null ? req.getPostedBy().trim() : "";

                boolean isSelf = (!userId.isEmpty() && userId.equalsIgnoreCase(reqPostedBy))
                        || (!userEmail.isEmpty() && userEmail.equalsIgnoreCase(reqPostedBy))
                        || (!userMobile.isEmpty() && !contactNum.isEmpty() && (userMobile.endsWith(contactNum) || contactNum.endsWith(userMobile)));

                if (!isSelf) {
                    String reqCity = req.getCity() != null ? req.getCity() : "";
                    String reqHospital = req.getHospital() != null ? req.getHospital() : "";
                    String reqBlood = req.getBloodGroup() != null ? req.getBloodGroup() : "";

                    // Location / Area Matching: matches user's city or hospital name includes city
                    boolean matchesArea = userCity.isEmpty()
                            || reqCity.isEmpty()
                            || reqCity.equalsIgnoreCase(userCity)
                            || reqHospital.toLowerCase().contains(userCity.toLowerCase());

                    // Transfusion Compatibility Check
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
                                isDirectMatch,
                                req.getId()
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
        showFcmPushNotification(context, title, message, notificationType, requestId, null, null);
    }

    public static void showFcmPushNotification(
            Context context,
            String title,
            String message,
            String notificationType,
            String requestId,
            String certificateId
    ) {
        showFcmPushNotification(context, title, message, notificationType, requestId, certificateId, null);
    }

    public static void showFcmPushNotification(
            Context context,
            String title,
            String message,
            String notificationType,
            String requestId,
            String certificateId,
            java.util.Map<String, String> extraData
    ) {
        createNotificationChannels(context);

        Intent intent;
        String type = notificationType != null ? notificationType.toUpperCase() : "GENERAL";

        if ("DONATION_VERIFIED".equals(type) || "CERTIFICATE_ISSUED".equals(type)) {
            if (certificateId != null && !certificateId.isEmpty()) {
                intent = new Intent(context, DonationCertificateActivity.class);
                intent.putExtra("certificate_id", certificateId);
                intent.setData(Uri.parse("lifeshare://certificate/" + certificateId));
            } else {
                intent = new Intent(context, DonationHistoryActivity.class);
                intent.setData(Uri.parse("lifeshare://history"));
            }
        } else if ("DONOR_ARRIVED".equals(type) || "DONOR_REACHED".equals(type) || "COORDINATOR_ALERT".equals(type) || "DONATION_PENDING".equals(type)) {
            intent = new Intent(context, CoordinatorVerificationActivity.class);
            intent.putExtra("tab", "queue");
            if (extraData != null) {
                if (extraData.containsKey("donorId")) intent.putExtra("donor_id", extraData.get("donorId"));
                if (extraData.containsKey("donorName")) intent.putExtra("donor_name", extraData.get("donorName"));
                if (extraData.containsKey("responseId")) intent.putExtra("response_id", extraData.get("responseId"));
                if (extraData.containsKey("requestId")) intent.putExtra("request_id", extraData.get("requestId"));
            }
            intent.setData(Uri.parse("lifeshare://coordinator?tab=queue"));
        } else if ("COORDINATOR_ONBOARDED".equals(type) || "COORDINATOR_ASSIGNED".equals(type)) {
            intent = new Intent(context, CoordinatorVerificationActivity.class);
            intent.setData(Uri.parse("lifeshare://coordinator"));
        } else if ("CHAT_MESSAGE".equals(type) || "CHAT_ALERT".equals(type)) {
            intent = new Intent(context, EmergencyChatActivity.class);
            if (requestId != null && !requestId.isEmpty()) {
                intent.putExtra("emergency_id", requestId);
            }
            if (extraData != null) {
                if (extraData.containsKey("patientName")) intent.putExtra("patient_name", extraData.get("patientName"));
                if (extraData.containsKey("hospitalName")) intent.putExtra("hospital_name", extraData.get("hospitalName"));
            }
            intent.setData(Uri.parse("lifeshare://chat" + (requestId != null ? ("?id=" + requestId) : "")));
        } else if (requestId != null && !requestId.isEmpty()) {
            intent = new Intent(context, EmergencyDetailActivity.class);
            intent.putExtra("emergency_id", requestId);
            intent.setData(Uri.parse("lifeshare://emergency/" + requestId));
        } else {
            intent = new Intent(context, LogInActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channelId = CHANNEL_GENERAL_ID;
        int priority = NotificationCompat.PRIORITY_DEFAULT;

        if ("EMERGENCY_REQUEST".equals(type) || "DONOR_ACCEPTED".equals(type) || "DONOR_ARRIVED".equals(type) || "EMERGENCY_RESOLVED".equals(type) || "EMERGENCY_CANCELLED".equals(type)) {
            channelId = CHANNEL_EMERGENCY_ID;
            priority = NotificationCompat.PRIORITY_MAX;
        } else if ("CHAT_MESSAGE".equals(type) || "CHAT_ALERT".equals(type)) {
            channelId = CHANNEL_CHAT_ID;
            priority = NotificationCompat.PRIORITY_HIGH;
        } else if ("DONATION_VERIFIED".equals(type) || "CERTIFICATE_ISSUED".equals(type) || "COOLDOWN_EXPIRED".equals(type)) {
            channelId = CHANNEL_CERTIFICATES_ID;
            priority = NotificationCompat.PRIORITY_DEFAULT;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_nav_emergency)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setColor(Color.parseColor("#C62828"))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                String notifKey = (requestId != null && !requestId.isEmpty())
                        ? ("fcm_" + requestId)
                        : ((certificateId != null && !certificateId.isEmpty()) ? ("fcm_cert_" + certificateId) : ("fcm_type_" + type));
                int notifId = notifKey.hashCode();
                manager.notify(notifId, builder.build());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
