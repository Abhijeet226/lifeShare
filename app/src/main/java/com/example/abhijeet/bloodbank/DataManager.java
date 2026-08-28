package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class DataManager {

    private static final String PREF_NAME = "LifeSharePreferences";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String KEY_AUTH_TOKEN = "jwt_auth_token";
    private static final String KEY_FCM_TOKEN = "fcm_device_token";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_FIRST_NAME = "user_first_name";
    private static final String KEY_USER_LAST_NAME = "user_last_name";
    private static final String KEY_USER_DOB = "user_dob";
    private static final String KEY_USER_GENDER = "user_gender";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_MOBILE = "user_mobile";
    private static final String KEY_USER_BG = "user_blood_group";
    private static final String KEY_USER_CITY = "user_city";
    private static final String KEY_USER_AVAILABLE = "user_is_available";
    private static final String KEY_USER_LAT = "user_last_lat";
    private static final String KEY_USER_LNG = "user_last_lng";
    private static final String KEY_LOCATION_SHARING = "user_location_sharing";
    private static final String KEY_LAST_LOC_TIME = "user_last_location_timestamp";
    private static final String KEY_DONATION_COUNT = "user_donation_count";
    private static final String KEY_LAST_DONATION = "user_last_donation_timestamp";

    private static DataManager instance;
    private final SharedPreferences prefs;
    private final Context context;

    public interface DonorCallback {
        void onDonorsLoaded(List<UserProfile> donors);
    }

    public interface RequestCallback {
        void onRequestsLoaded(List<EmergencyRequest> requests);
    }

    public interface BloodBankCallback {
        void onBloodBanksLoaded(List<BloodBankCenter> bloodBanks);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    private DataManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        ApiClient.getInstance().initFromPrefs(this.context);
        initializeSampleData();
    }

    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }

    private void initializeSampleData() {
        // Pure online architecture: all data is synced directly from MongoDB Atlas backend
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false);
    }

    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply();
        if (!loggedIn) {
            saveAuthToken(null);
            prefs.edit()
                    .remove("KEY_USER_ID")
                    .remove(KEY_USER_NAME)
                    .remove(KEY_USER_FIRST_NAME)
                    .remove(KEY_USER_LAST_NAME)
                    .remove(KEY_USER_EMAIL)
                    .remove(KEY_USER_MOBILE)
                    .remove("KEY_USER_ROLE")
                    .remove("KEY_USER_DONOR_ID")
                    .remove(KEY_AUTH_TOKEN)
                    .putBoolean(KEY_LOGGED_IN, false)
                    .apply();
        }
    }

    public void saveAuthToken(String token) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
        ApiClient.getInstance().setAuthToken(context, token);
    }

    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }

    public void saveFcmToken(String token) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply();
    }

    public String getFcmToken() {
        return prefs.getString(KEY_FCM_TOKEN, null);
    }

    public boolean isBiometricLockEnabled() {
        return prefs.getBoolean("key_biometric_lock", false);
    }

    public void setBiometricLockEnabled(boolean enabled) {
        prefs.edit().putBoolean("key_biometric_lock", enabled).apply();
    }

    public boolean isLocationSharingEnabled() {
        return prefs.getBoolean(KEY_LOCATION_SHARING, true);
    }

    public void setLocationSharingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCATION_SHARING, enabled).apply();
    }

    public void saveLastKnownLocation(double lat, double lng) {
        prefs.edit()
                .putFloat(KEY_USER_LAT, (float) lat)
                .putFloat(KEY_USER_LNG, (float) lng)
                .putLong(KEY_LAST_LOC_TIME, System.currentTimeMillis())
                .apply();

        // Sync with backend if logged in
        if (isLoggedIn()) {
            ApiClient.getInstance().updateLocation(lat, lng, new ApiClient.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {}
                @Override
                public void onError(String errorMessage) {}
            });
        }
    }

    public double[] getLastKnownLocation() {
        if (!prefs.contains(KEY_USER_LAT)) return null;
        return new double[]{
                prefs.getFloat(KEY_USER_LAT, 20.2961f),
                prefs.getFloat(KEY_USER_LNG, 85.8245f)
        };
    }

    public long getLastLocationTimestamp() {
        return prefs.getLong(KEY_LAST_LOC_TIME, 0);
    }

    public int getDonationCount() {
        return prefs.getInt(KEY_DONATION_COUNT, 0);
    }

    public void incrementDonationCount() {
        prefs.edit().putInt(KEY_DONATION_COUNT, getDonationCount() + 1).apply();
    }

    public int getDaysUntilNextDonation() {
        long lastDonation = prefs.getLong(KEY_LAST_DONATION, 0);
        if (lastDonation == 0) return 0;
        long diffMs = System.currentTimeMillis() - lastDonation;
        long daysPassed = diffMs / (1000 * 60 * 60 * 24);
        int remaining = (int) (90 - daysPassed);
        return Math.max(0, remaining);
    }

    public UserProfile getCurrentUser() {
        String firstName = prefs.getString(KEY_USER_FIRST_NAME, "Donor");
        String lastName = prefs.getString(KEY_USER_LAST_NAME, "");
        String name = prefs.getString(KEY_USER_NAME, (firstName + " " + lastName).trim());
        String dob = prefs.getString(KEY_USER_DOB, "2000-01-01");
        String gender = prefs.getString(KEY_USER_GENDER, "Male");
        String email = prefs.getString(KEY_USER_EMAIL, "");
        String mobile = prefs.getString(KEY_USER_MOBILE, "+91 ");
        String bg = prefs.getString(KEY_USER_BG, "O+");
        String city = prefs.getString(KEY_USER_CITY, "Bhubaneswar");
        boolean available = prefs.getBoolean(KEY_USER_AVAILABLE, true);

        UserProfile user = new UserProfile(firstName, lastName, dob, gender, email, mobile, bg, city, available);
        if (name != null && !name.isEmpty()) {
            user.setName(name);
        }
        user.setId(prefs.getString("KEY_USER_ID", ""));
        user.setDonorId(prefs.getString("KEY_USER_DONOR_ID", ""));
        user.setRole(prefs.getString("KEY_USER_ROLE", "DONOR"));
        user.setVerificationStatus(prefs.getString("KEY_USER_VERIFICATION_STATUS", "UNVERIFIED"));
        user.setAccountStatus(prefs.getString("KEY_USER_ACCOUNT_STATUS", "ACTIVE"));
        user.setPhoneVerified(prefs.getBoolean("KEY_USER_PHONE_VERIFIED", false));
        user.setEmailVerified(prefs.getBoolean("KEY_USER_EMAIL_VERIFIED", false));
        user.setDonationsCount(prefs.getInt("KEY_USER_DONATIONS_COUNT", 0));
        user.setEligibleToDonate(prefs.getBoolean("KEY_USER_ELIGIBLE", true));
        user.setDaysRemaining(prefs.getInt("KEY_USER_DAYS_REMAINING", 0));
        user.setNextEligibleDate(prefs.getString("KEY_USER_NEXT_DATE", ""));
        return user;
    }

    public void saveCurrentUser(UserProfile user) {
        if (user == null) return;
        prefs.edit()
                .putString("KEY_USER_ID", user.getId() != null ? user.getId() : "")
                .putString(KEY_USER_NAME, user.getName() != null ? user.getName() : "")
                .putString(KEY_USER_FIRST_NAME, user.getFirstName() != null ? user.getFirstName() : "")
                .putString(KEY_USER_LAST_NAME, user.getLastName() != null ? user.getLastName() : "")
                .putString(KEY_USER_DOB, user.getDob() != null ? user.getDob() : "")
                .putString(KEY_USER_GENDER, user.getGender() != null ? user.getGender() : "Male")
                .putString(KEY_USER_EMAIL, user.getEmail() != null ? user.getEmail() : "")
                .putString(KEY_USER_MOBILE, user.getMobile() != null ? user.getMobile() : "")
                .putString(KEY_USER_BG, user.getBloodGroup() != null ? user.getBloodGroup() : "O+")
                .putString(KEY_USER_CITY, user.getCity() != null ? user.getCity() : "Bhubaneswar")
                .putBoolean(KEY_USER_AVAILABLE, user.isAvailable())
                .putString("KEY_USER_DONOR_ID", user.getDonorId() != null ? user.getDonorId() : "")
                .putString("KEY_USER_ROLE", user.getRole() != null ? user.getRole() : "DONOR")
                .putString("KEY_USER_VERIFICATION_STATUS", user.getVerificationStatus() != null ? user.getVerificationStatus() : "UNVERIFIED")
                .putString("KEY_USER_ACCOUNT_STATUS", user.getAccountStatus() != null ? user.getAccountStatus() : "ACTIVE")
                .putBoolean("KEY_USER_PHONE_VERIFIED", user.isPhoneVerified())
                .putBoolean("KEY_USER_EMAIL_VERIFIED", user.isEmailVerified())
                .putInt("KEY_USER_DONATIONS_COUNT", user.getDonationsCount())
                .putBoolean("KEY_USER_ELIGIBLE", user.isEligibleToDonate())
                .putInt("KEY_USER_DAYS_REMAINING", user.getDaysRemaining())
                .putString("KEY_USER_NEXT_DATE", user.getNextEligibleDate() != null ? user.getNextEligibleDate() : "")
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();

        // Sync with MongoDB backend
        try {
            ApiClient.getInstance().updateProfile(user, new ApiClient.ApiCallback<UserProfile>() {
                @Override
                public void onSuccess(UserProfile result) {}
                @Override
                public void onError(String errorMessage) {}
            });
        } catch (Throwable ignored) {}
    }

    public void loginWithGoogleMock(String name, String email, SimpleCallback callback) {
        setLoggedIn(true);
        UserProfile user = getCurrentUser();
        if (name != null && !name.isEmpty()) user.setName(name);
        if (email != null && !email.isEmpty()) user.setEmail(email);
        saveCurrentUser(user);
        callback.onSuccess();
    }

    // ================= DONORS (GEOSPATIAL & LEGACY) =================

    public void fetchNearbyDonors(String bloodGroup, double latitude, double longitude, int radiusMeters, final DonorCallback callback) {
        ApiClient.getInstance().getNearbyDonors(bloodGroup, latitude, longitude, radiusMeters, new ApiClient.ApiCallback<List<UserProfile>>() {
            @Override
            public void onSuccess(List<UserProfile> donors) {
                if (donors != null) {
                    callback.onDonorsLoaded(donors);
                } else {
                    callback.onDonorsLoaded(new ArrayList<UserProfile>());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onDonorsLoaded(new ArrayList<UserProfile>());
            }
        });
    }

    public void fetchDonors(final String bloodGroup, final DonorCallback callback) {
        ApiClient.getInstance().getDonors(bloodGroup, null, new ApiClient.ApiCallback<List<UserProfile>>() {
            @Override
            public void onSuccess(List<UserProfile> donors) {
                if (donors != null) {
                    callback.onDonorsLoaded(donors);
                } else {
                    callback.onDonorsLoaded(new ArrayList<UserProfile>());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onDonorsLoaded(new ArrayList<UserProfile>());
            }
        });
    }

    public void fetchBloodBanks(String city, final BloodBankCallback callback) {
        ApiClient.getInstance().getBloodBanks(city, new ApiClient.ApiCallback<List<BloodBankCenter>>() {
            @Override
            public void onSuccess(List<BloodBankCenter> bloodBanks) {
                if (bloodBanks != null) {
                    callback.onBloodBanksLoaded(bloodBanks);
                } else {
                    callback.onBloodBanksLoaded(new ArrayList<BloodBankCenter>());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onBloodBanksLoaded(new ArrayList<BloodBankCenter>());
            }
        });
    }

    // ================= EMERGENCY REQUESTS =================

    public void fetchEmergencyRequests(final RequestCallback callback) {
        ApiClient.getInstance().getEmergencyRequests(new ApiClient.ApiCallback<List<EmergencyRequest>>() {
            @Override
            public void onSuccess(List<EmergencyRequest> requests) {
                if (requests != null) {
                    callback.onRequestsLoaded(requests);
                } else {
                    callback.onRequestsLoaded(new ArrayList<EmergencyRequest>());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onRequestsLoaded(new ArrayList<EmergencyRequest>());
            }
        });
    }

    public EmergencyRequest getEmergencyRequestById(String id) {
        return null;
    }

    public void createEmergencyRequest(EmergencyRequest request, SimpleCallback callback) {
        ApiClient.getInstance().createEmergencyRequest(request, new ApiClient.ApiCallback<EmergencyRequest>() {
            @Override
            public void onSuccess(EmergencyRequest result) {
                callback.onSuccess();
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void respondToEmergency(String emergencyId, String responseAction, final SimpleCallback callback) {
        ApiClient.getInstance().respondToEmergency(emergencyId, responseAction, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                callback.onSuccess();
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void deleteEmergencyRequest(String id, final SimpleCallback callback) {
        ApiClient.getInstance().deleteEmergencyRequest(id, new ApiClient.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                callback.onSuccess();
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    private static final String KEY_THEME_MODE = "app_theme_mode";

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyThemeMode();
    }

    public void applyThemeMode() {
        int mode = getThemeMode();
        switch (mode) {
            case THEME_LIGHT:
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
