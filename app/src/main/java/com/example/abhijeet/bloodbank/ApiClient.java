package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {

    public static final String DEFAULT_BASE_URL = "https://lifeshare-74c2.onrender.com/api";
    private static final String PREF_SERVER_URL = "custom_server_base_url";
    private static final String PREF_AUTH_TOKEN = "jwt_auth_token";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static ApiClient instance;
    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;
    private String baseUrl = DEFAULT_BASE_URL;
    private String authToken = null;

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private ApiClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public void initFromPrefs(Context context) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("LifeSharePrefs", Context.MODE_PRIVATE);
            String savedUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_BASE_URL);
            setBaseUrl(savedUrl);
            this.authToken = prefs.getString(PREF_AUTH_TOKEN, null);
        }
    }

    public void setAuthToken(Context context, String token) {
        this.authToken = token;
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("LifeSharePrefs", Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_AUTH_TOKEN, token).apply();
        }
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setBaseUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            this.baseUrl = url.trim();
            if (this.baseUrl.endsWith("/")) {
                this.baseUrl = this.baseUrl.substring(0, this.baseUrl.length() - 1);
            }
        }
    }

    public void saveBaseUrl(Context context, String url) {
        setBaseUrl(url);
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("LifeSharePrefs", Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_SERVER_URL, this.baseUrl).apply();
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ================= HEALTH CHECK =================

    public void checkHealth(final ApiCallback<String> callback) {
        get("/health", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String db = body.has("database") ? body.get("database").getAsString() : "Connected";
                callback.onSuccess("Connected to " + db);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ================= AUTH =================

    private UserProfile parseUserProfile(JsonObject u) {
        String name = optString(u, "name", "");
        String firstName = optString(u, "firstName", "");
        String lastName = optString(u, "lastName", "");
        String dob = optString(u, "dob", "");
        String gender = optString(u, "gender", "Male");
        String email = optString(u, "email", "");
        String mobile = optString(u, "mobile", "+91 ");
        String bloodGroup = optString(u, "bloodGroup", "O+");
        String city = optString(u, "city", "Bhubaneswar");
        boolean isAvailable = !u.has("isAvailable") || u.get("isAvailable").isJsonNull() || u.get("isAvailable").getAsBoolean();

        UserProfile user = new UserProfile(firstName, lastName, dob, gender, email, mobile, bloodGroup, city, isAvailable);
        user.setId(optString(u, "id", optString(u, "_id", "")));
        if (!name.isEmpty()) {
            user.setName(name);
        }
        if (u.has("cityId") && !u.get("cityId").isJsonNull()) {
            user.setCityId(u.get("cityId").getAsString());
        }
        user.setDonorId(optString(u, "donorId", ""));
        user.setVerificationStatus(optString(u, "verificationStatus", "UNVERIFIED"));
        user.setAccountStatus(optString(u, "accountStatus", "ACTIVE"));

        if (u.has("hideMobileNumber") && !u.get("hideMobileNumber").isJsonNull()) {
            user.setHideMobileNumber(u.get("hideMobileNumber").getAsBoolean());
        }
        if (u.has("phoneVerified") && !u.get("phoneVerified").isJsonNull()) {
            user.setPhoneVerified(u.get("phoneVerified").getAsBoolean());
        }
        if (u.has("emailVerified") && !u.get("emailVerified").isJsonNull()) {
            user.setEmailVerified(u.get("emailVerified").getAsBoolean());
        }
        user.setRole(optString(u, "role", "DONOR"));
        user.setDonationsCount(optInt(u, "donationsCount", 0));
        user.setKarmaPoints(optInt(u, "karmaPoints", 0));
        user.setLastDonationDate(optString(u, "lastDonationDate", ""));

        if (u.has("badges") && u.get("badges").isJsonArray()) {
            java.util.List<DonorBadge> badgeList = new java.util.ArrayList<>();
            for (JsonElement el : u.getAsJsonArray("badges")) {
                DonorBadge b = gson.fromJson(el, DonorBadge.class);
                if (b != null) badgeList.add(b);
            }
            user.setBadges(badgeList);
        }

        if (u.has("eligibility") && u.get("eligibility").isJsonObject()) {
            JsonObject el = u.getAsJsonObject("eligibility");
            if (el.has("isEligible")) {
                user.setEligibleToDonate(el.get("isEligible").getAsBoolean());
            }
            user.setDaysRemaining(optInt(el, "daysRemaining", 0));
            user.setNextEligibleDate(optString(el, "nextEligibleDate", ""));
        }
        if (!user.isEligibleToDonate() || user.getDaysRemaining() > 0) {
            user.setAvailable(false);
        }
        return user;
    }

    public void register(String firstName, String lastName, String dob, String gender, String email, String password, String mobile, String bloodGroup, String city, boolean isAvailable, final ApiCallback<UserProfile> callback) {
        register(firstName, lastName, dob, gender, email, password, mobile, bloodGroup, city, null, isAvailable, null, callback);
    }

    public void register(String firstName, String lastName, String dob, String gender, String email, String password, String mobile, String bloodGroup, String city, String cityId, boolean isAvailable, final ApiCallback<UserProfile> callback) {
        register(firstName, lastName, dob, gender, email, password, mobile, bloodGroup, city, cityId, isAvailable, null, callback);
    }

    public void register(String firstName, String lastName, String dob, String gender, String email, String password, String mobile, String bloodGroup, String city, boolean isAvailable, String otp, final ApiCallback<UserProfile> callback) {
        register(firstName, lastName, dob, gender, email, password, mobile, bloodGroup, city, null, isAvailable, otp, callback);
    }

    public void register(String firstName, String lastName, String dob, String gender, String email, String password, String mobile, String bloodGroup, String city, String cityId, boolean isAvailable, String otp, final ApiCallback<UserProfile> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("firstName", firstName);
        json.addProperty("lastName", lastName);
        json.addProperty("name", (firstName + " " + lastName).trim());
        json.addProperty("dob", dob);
        json.addProperty("gender", gender);
        json.addProperty("email", email);
        json.addProperty("password", password);
        json.addProperty("mobile", mobile);
        json.addProperty("bloodGroup", bloodGroup);
        json.addProperty("city", city);
        if (cityId != null && !cityId.isEmpty()) {
            json.addProperty("cityId", cityId);
        }
        json.addProperty("isAvailable", isAvailable);
        if (otp != null && !otp.isEmpty()) {
            json.addProperty("otp", otp);
        }

        post("/auth/register", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("token")) {
                    authToken = body.get("token").getAsString();
                }
                if (body.has("user")) {
                    JsonObject u = body.getAsJsonObject("user");
                    callback.onSuccess(parseUserProfile(u));
                } else {
                    callback.onError("Registration response invalid");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void login(String email, String password, final ApiCallback<UserProfile> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("email", email);
        json.addProperty("password", password);

        post("/auth/login", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("token")) {
                    authToken = body.get("token").getAsString();
                }
                if (body.has("user")) {
                    JsonObject u = body.getAsJsonObject("user");
                    callback.onSuccess(parseUserProfile(u));
                } else {
                    callback.onError("Login response invalid");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void googleLogin(String name, String email, String googleId, final ApiCallback<UserProfile> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("email", email);
        json.addProperty("googleId", googleId);

        post("/auth/google-login", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("token")) {
                    authToken = body.get("token").getAsString();
                }
                if (body.has("user")) {
                    JsonObject u = body.getAsJsonObject("user");
                    callback.onSuccess(parseUserProfile(u));
                } else {
                    callback.onError("Google Sign-In response invalid");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ================= FORGOT PASSWORD & OTP =================

    public void sendForgotPasswordOtp(String email, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("email", email);

        post("/auth/forgot-password", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String message = body.has("message") ? body.get("message").getAsString() : "OTP sent to your email";
                callback.onSuccess(message);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void verifyResetOtp(String email, String otp, String newPassword, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("email", email);
        json.addProperty("otp", otp);
        json.addProperty("newPassword", newPassword);

        post("/auth/verify-reset-otp", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String msg = body.has("message") ? body.get("message").getAsString() : "Password reset successfully";
                callback.onSuccess(msg);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void sendSignupOtp(String identifier, String type, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("identifier", identifier);
        json.addProperty("type", type);

        post("/auth/send-signup-otp", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String msg = body.has("message") ? body.get("message").getAsString() : "Verification OTP sent";
                callback.onSuccess(msg);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void verifySignupOtp(String identifier, String otp, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("identifier", identifier);
        json.addProperty("otp", otp);

        post("/auth/verify-signup-otp", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String msg = body.has("message") ? body.get("message").getAsString() : "OTP verified successfully";
                callback.onSuccess(msg);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void sendVerificationOtp(String type, final ApiCallback<String> callback) {
        sendVerificationOtp(type, null, callback);
    }

    public void sendVerificationOtp(String type, String targetIdentifier, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type); // "PHONE" or "EMAIL"
        if (targetIdentifier != null && !targetIdentifier.isEmpty()) {
            json.addProperty("targetIdentifier", targetIdentifier);
        }

        post("/auth/send-verification-otp", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String msg = body.has("message") ? body.get("message").getAsString() : "OTP sent successfully";
                callback.onSuccess(msg);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void verifyAccountOtp(final String type, String otp, final ApiCallback<UserProfile> callback) {
        verifyAccountOtp(type, otp, null, callback);
    }

    public void verifyAccountOtp(final String type, String otp, String targetIdentifier, final ApiCallback<UserProfile> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("otp", otp);
        if (targetIdentifier != null && !targetIdentifier.isEmpty()) {
            json.addProperty("targetIdentifier", targetIdentifier);
        }

        post("/auth/verify-account-otp", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("user") && body.get("user").isJsonObject()) {
                    UserProfile user = parseUserProfile(body.getAsJsonObject("user"));
                    callback.onSuccess(user);
                } else {
                    callback.onError("Failed to parse verified user");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ================= PROFILE & LOCATION =================

    public void getProfile(final ApiCallback<UserProfile> callback) {
        get("/users/me", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("user") && body.get("user").isJsonObject()) {
                    UserProfile user = parseUserProfile(body.getAsJsonObject("user"));
                    callback.onSuccess(user);
                } else {
                    callback.onError("Failed to parse user profile");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void updateProfile(UserProfile user, final ApiCallback<UserProfile> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("name", user.getName());
        json.addProperty("firstName", user.getFirstName());
        json.addProperty("lastName", user.getLastName());
        json.addProperty("dob", user.getDob());
        json.addProperty("gender", user.getGender());
        json.addProperty("mobile", user.getMobile());
        json.addProperty("city", user.getCity());
        if (user.getCityId() != null && !user.getCityId().isEmpty()) {
            json.addProperty("cityId", user.getCityId());
        }
        json.addProperty("bloodGroup", user.getBloodGroup());
        json.addProperty("isAvailable", user.isAvailable());
        json.addProperty("hideMobileNumber", user.isHideMobileNumber());
        json.addProperty("biometricEnabled", user.isBiometricEnabled());
        json.addProperty("hospitalOnlyVisibility", user.isHospitalOnlyVisibility());

        put("/users/profile", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("user") && body.get("user").isJsonObject()) {
                    UserProfile updated = parseUserProfile(body.getAsJsonObject("user"));
                    callback.onSuccess(updated);
                } else {
                    callback.onSuccess(user);
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void updateLocation(double latitude, double longitude, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("latitude", latitude);
        json.addProperty("longitude", longitude);

        put("/users/location", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess("Location updated");
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void updateAvailability(boolean isAvailable, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("isAvailable", isAvailable);

        put("/users/availability", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess("Availability updated");
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void changePassword(String email, String newPassword, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("email", email);
        json.addProperty("newPassword", newPassword);

        put("/users/change-password", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess("Password updated successfully");
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void registerDeviceToken(String token, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("token", token);
        json.addProperty("platform", "ANDROID");

        post("/device-tokens", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (callback != null) callback.onSuccess("FCM token registered");
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }

    // ================= DONORS SEARCH (GEOSPATIAL & LEGACY) =================

    public void getNearbyDonors(String bloodGroup, double latitude, double longitude, int radiusMeters, final ApiCallback<List<UserProfile>> callback) {
        String endpoint = "/donors/nearby?latitude=" + latitude + "&longitude=" + longitude + "&radius=" + radiusMeters;
        if (bloodGroup != null && !bloodGroup.isEmpty() && !bloodGroup.equalsIgnoreCase("All")) {
            endpoint += "&bloodGroup=" + bloodGroup;
        }

        get(endpoint, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<UserProfile> list = new ArrayList<>();
                if (body.has("donors")) {
                    JsonArray arr = body.getAsJsonArray("donors");
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        UserProfile u = parseUserProfile(obj);
                        if (obj.has("distanceKm")) {
                            u.setDistanceKm(obj.get("distanceKm").getAsDouble());
                        }
                        if (obj.has("distanceMeters")) {
                            u.setDistanceMeters(obj.get("distanceMeters").getAsInt());
                        }
                        list.add(u);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getDonors(String bloodGroup, String city, final ApiCallback<List<UserProfile>> callback) {
        String endpoint = "/donors?bloodGroup=" + (bloodGroup != null ? bloodGroup : "All");
        if (city != null && !city.isEmpty()) {
            endpoint += "&city=" + city;
        }

        get(endpoint, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<UserProfile> list = new ArrayList<>();
                if (body.has("donors")) {
                    JsonArray arr = body.getAsJsonArray("donors");
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        list.add(parseUserProfile(obj));
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ================= EMERGENCY SOS =================

    public void createEmergencyRequest(EmergencyRequest request, final ApiCallback<EmergencyRequest> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("patientName", request.getPatientName());
        json.addProperty("hospital", request.getHospital());
        if (request.getHospitalId() != null && !request.getHospitalId().isEmpty()) {
            json.addProperty("hospitalId", request.getHospitalId());
        }
        if (request.getHospitalAddress() != null && !request.getHospitalAddress().isEmpty()) {
            json.addProperty("hospitalAddress", request.getHospitalAddress());
        }
        if (request.getHospitalLatitude() != 0.0 && request.getHospitalLongitude() != 0.0) {
            json.addProperty("hospitalLatitude", request.getHospitalLatitude());
            json.addProperty("hospitalLongitude", request.getHospitalLongitude());
        }
        json.addProperty("city", request.getCity());
        if (request.getCityId() != null && !request.getCityId().isEmpty()) {
            json.addProperty("cityId", request.getCityId());
        }
        json.addProperty("bloodGroup", request.getBloodGroup());
        json.addProperty("unitsRequired", request.getUnitsRequired());
        json.addProperty("unitsNeeded", request.getUnitsRequired());
        json.addProperty("contactNumber", request.getContactNumber());
        json.addProperty("postedBy", request.getPostedBy());
        json.addProperty("urgency", request.getUrgency());

        if (request.hasLocation()) {
            json.addProperty("latitude", request.getLatitude());
            json.addProperty("longitude", request.getLongitude());
        }

        post("/emergency/create", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("request") && body.getAsJsonObject("request").has("_id")) {
                    request.setId(body.getAsJsonObject("request").get("_id").getAsString());
                }
                callback.onSuccess(request);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getEmergencyRequests(final ApiCallback<List<EmergencyRequest>> callback) {
        get("/emergency/list", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<EmergencyRequest> list = new ArrayList<>();
                if (body.has("requests")) {
                    JsonArray arr = body.getAsJsonArray("requests");
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        EmergencyRequest req = new EmergencyRequest(
                                optString(obj, "_id", optString(obj, "id", "")),
                                optString(obj, "patientName", "Patient"),
                                optString(obj, "hospital", "Hospital"),
                                optString(obj, "city", "Bhubaneswar"),
                                optString(obj, "bloodGroup", "O+"),
                                optInt(obj, "unitsRequired", optInt(obj, "unitsNeeded", 1)),
                                optString(obj, "contactNumber", ""),
                                optString(obj, "postedBy", "")
                        );
                        req.setHospitalId(optString(obj, "hospitalId", ""));
                        req.setHospitalName(optString(obj, "hospitalName", optString(obj, "hospital", "")));
                        req.setHospitalAddress(optString(obj, "hospitalAddress", ""));
                        req.setAcceptedCount(optInt(obj, "acceptedCount", 0));
                        req.setUnitsFulfilled(optInt(obj, "unitsFulfilled", 0));
                        req.setUrgency(optString(obj, "urgency", "URGENT"));
                        req.setStatus(optString(obj, "status", "SEARCHING"));
                        req.setFulfilled(obj.has("isFulfilled") && obj.get("isFulfilled").getAsBoolean());
                        list.add(req);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public static class DonorJourneyInfo {
        public String status = "NOTIFIED";
        public String viewedAt;
        public String acceptedAt;
        public String travellingAt;
        public String arrivedAt;
        public String donatedAt;
        public String completedAt;
        public boolean isAccepted;
        public boolean canStartJourney;
        public boolean canMarkArrived;
        public boolean isPendingVerification;
        public boolean isCompleted;
    }

    public static class AcceptedDonorItem {
        public String donorId;
        public String name;
        public String bloodGroup;
        public String verificationStatus;
        public String journeyStatus;
        public String acceptedAt;
        public String travellingAt;
        public String arrivedAt;
    }

    public static class EmergencyDetailResponse {
        public EmergencyRequest emergency;
        public int unitsRequired;
        public int acceptedCount;
        public int remainingUnits;
        public int notifiedCount;
        public int responseCount;
        public boolean isFulfilled;
        public boolean isRequester;
        public String userResponseStatus;
        public DonorJourneyInfo myJourney;
        public List<AcceptedDonorItem> acceptedDonors = new ArrayList<>();
    }

    public void getEmergencyDetail(String emergencyId, final ApiCallback<EmergencyDetailResponse> callback) {
        get("/emergency/" + emergencyId, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                try {
                    if (body.has("emergency") && body.get("emergency").isJsonObject()) {
                        JsonObject obj = body.getAsJsonObject("emergency");
                        EmergencyRequest req = new EmergencyRequest(
                                optString(obj, "_id", optString(obj, "id", "")),
                                optString(obj, "patientName", "Patient"),
                                optString(obj, "hospital", "Hospital"),
                                optString(obj, "city", "Bhubaneswar"),
                                optString(obj, "bloodGroup", "O+"),
                                optInt(obj, "unitsRequired", optInt(obj, "unitsNeeded", 1)),
                                optString(obj, "contactNumber", ""),
                                optString(obj, "postedBy", "")
                        );
                        req.setHospitalId(optString(obj, "hospitalId", ""));
                        req.setHospitalName(optString(obj, "hospitalName", optString(obj, "hospital", "")));
                        req.setHospitalAddress(optString(obj, "hospitalAddress", ""));
                        req.setAcceptedCount(optInt(obj, "acceptedCount", 0));
                        req.setUnitsFulfilled(optInt(obj, "unitsFulfilled", 0));
                        req.setUrgency(optString(obj, "urgency", "URGENT"));
                        req.setStatus(optString(obj, "status", "SEARCHING"));
                        req.setFulfilled(obj.has("isFulfilled") && obj.get("isFulfilled").getAsBoolean());

                        if (obj.has("hospitalLocation") && obj.get("hospitalLocation").isJsonObject()) {
                            JsonObject hl = obj.getAsJsonObject("hospitalLocation");
                            if (hl.has("coordinates") && hl.get("coordinates").isJsonArray()) {
                                JsonArray coords = hl.getAsJsonArray("coordinates");
                                if (coords.size() >= 2) {
                                    req.setHospitalLongitude(coords.get(0).getAsDouble());
                                    req.setHospitalLatitude(coords.get(1).getAsDouble());
                                }
                            }
                        }

                        EmergencyDetailResponse detail = new EmergencyDetailResponse();
                        detail.emergency = req;
                        detail.isRequester = body.has("isRequester") && body.get("isRequester").getAsBoolean();

                        if (body.has("stats") && body.get("stats").isJsonObject()) {
                            JsonObject stats = body.getAsJsonObject("stats");
                            detail.unitsRequired = optInt(stats, "unitsRequired", req.getUnitsRequired());
                            detail.acceptedCount = optInt(stats, "acceptedCount", req.getAcceptedCount());
                            detail.remainingUnits = optInt(stats, "remainingUnits", req.getRemainingUnits());
                            detail.notifiedCount = optInt(stats, "notifiedCount", 0);
                            detail.responseCount = optInt(stats, "responseCount", 0);
                            detail.isFulfilled = stats.has("isFulfilled") && stats.get("isFulfilled").getAsBoolean();
                        }

                        if (body.has("userResponseStatus") && !body.get("userResponseStatus").isJsonNull()) {
                            detail.userResponseStatus = body.get("userResponseStatus").getAsString();
                        }

                        if (body.has("myJourney") && body.get("myJourney").isJsonObject()) {
                            JsonObject mj = body.getAsJsonObject("myJourney");
                            DonorJourneyInfo journey = new DonorJourneyInfo();
                            journey.status = optString(mj, "status", "NOTIFIED");
                            journey.viewedAt = optString(mj, "viewedAt", null);
                            journey.acceptedAt = optString(mj, "acceptedAt", null);
                            journey.travellingAt = optString(mj, "travellingAt", null);
                            journey.arrivedAt = optString(mj, "arrivedAt", null);
                            journey.donatedAt = optString(mj, "donatedAt", null);
                            journey.completedAt = optString(mj, "completedAt", null);
                            journey.isAccepted = mj.has("isAccepted") && mj.get("isAccepted").getAsBoolean();
                            journey.canStartJourney = mj.has("canStartJourney") && mj.get("canStartJourney").getAsBoolean();
                            journey.canMarkArrived = mj.has("canMarkArrived") && mj.get("canMarkArrived").getAsBoolean();
                            journey.isPendingVerification = mj.has("isPendingVerification") && mj.get("isPendingVerification").getAsBoolean();
                            journey.isCompleted = mj.has("isCompleted") && mj.get("isCompleted").getAsBoolean();
                            detail.myJourney = journey;
                        }

                        if (body.has("acceptedDonors") && body.get("acceptedDonors").isJsonArray()) {
                            JsonArray donorsArr = body.getAsJsonArray("acceptedDonors");
                            for (JsonElement el : donorsArr) {
                                if (!el.isJsonObject()) continue;
                                JsonObject dObj = el.getAsJsonObject();
                                AcceptedDonorItem item = new AcceptedDonorItem();
                                item.donorId = optString(dObj, "donorId", "");
                                item.name = optString(dObj, "name", "Voluntary Donor");
                                item.bloodGroup = optString(dObj, "bloodGroup", req.getBloodGroup());
                                item.verificationStatus = optString(dObj, "verificationStatus", "UNVERIFIED");
                                item.journeyStatus = optString(dObj, "journeyStatus", "ACCEPTED");
                                item.acceptedAt = optString(dObj, "acceptedAt", null);
                                item.travellingAt = optString(dObj, "travellingAt", null);
                                item.arrivedAt = optString(dObj, "arrivedAt", null);
                                detail.acceptedDonors.add(item);
                            }
                        }

                        callback.onSuccess(detail);
                    } else {
                        callback.onError("Emergency details not found");
                    }
                } catch (Exception e) {
                    callback.onError("Failed to parse emergency details: " + e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void createHospitalEmergency(JsonObject json, final ApiCallback<JsonObject> callback) {
        post("/emergencies", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess(body);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void updateJourneyStatus(String emergencyId, String action, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("action", action); // "VIEWED", "TRAVELLING", "ARRIVED", "CANCELLED"

        post("/emergencies/" + emergencyId + "/journey", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String msg = body.has("message") ? body.get("message").getAsString() : "Journey updated";
                callback.onSuccess(msg);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void respondToEmergency(String emergencyId, String responseAction, final ApiCallback<String> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("response", responseAction); // "ACCEPTED", "DECLINED", "TRAVELLING", "ARRIVED"

        post("/emergencies/" + emergencyId + "/respond", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                String msg = body.has("message") ? body.get("message").getAsString() : "Response recorded";
                callback.onSuccess(msg);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void deleteEmergencyRequest(String id, final ApiCallback<Void> callback) {
        delete("/emergency/" + id, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess(null);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ================= HOSPITALS & BLOOD BANKS =================

    public void getNearbyHospitals(double latitude, double longitude, int radiusMeters, final ApiCallback<List<HospitalModel>> callback) {
        String endpoint = "/hospitals/nearby?latitude=" + latitude + "&longitude=" + longitude + "&radius=" + radiusMeters;
        get(endpoint, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<HospitalModel> list = new ArrayList<>();
                if (body.has("hospitals")) {
                    JsonArray arr = body.getAsJsonArray("hospitals");
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        HospitalModel h = new HospitalModel(
                                optString(obj, "id", optString(obj, "_id", "")),
                                optString(obj, "name", ""),
                                optString(obj, "address", ""),
                                optString(obj, "city", "Bhubaneswar"),
                                optString(obj, "phone", ""),
                                optString(obj, "cityId", "")
                        );
                        if (obj.has("distanceKm")) {
                            h.setDistanceKm(obj.get("distanceKm").getAsDouble());
                        }
                        list.add(h);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getHospitals(String city, final ApiCallback<List<HospitalModel>> callback) {
        String endpoint = "/hospitals" + (city != null && !city.isEmpty() ? "?city=" + city : "");
        get(endpoint, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<HospitalModel> list = new ArrayList<>();
                if (body.has("hospitals")) {
                    JsonArray arr = body.getAsJsonArray("hospitals");
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        list.add(new HospitalModel(
                                optString(obj, "id", optString(obj, "_id", "")),
                                optString(obj, "name", ""),
                                optString(obj, "address", ""),
                                optString(obj, "city", "Bhubaneswar"),
                                optString(obj, "phone", ""),
                                optString(obj, "cityId", "")
                        ));
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ================= CANONICAL CITIES =================

    public void getCities(final ApiCallback<List<CityModel>> callback) {
        get("/cities", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<CityModel> list = new ArrayList<>();
                if (body.has("cities") && body.get("cities").isJsonArray()) {
                    JsonArray arr = body.getAsJsonArray("cities");
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        list.add(new CityModel(
                                optString(obj, "id", optString(obj, "cityId", optString(obj, "_id", ""))),
                                optString(obj, "name", ""),
                                optString(obj, "stateName", "Odisha"),
                                obj.has("latitude") && !obj.get("latitude").isJsonNull() ? obj.get("latitude").getAsDouble() : 0.0,
                                obj.has("longitude") && !obj.get("longitude").isJsonNull() ? obj.get("longitude").getAsDouble() : 0.0
                        ));
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getCityById(String cityId, final ApiCallback<CityModel> callback) {
        if (cityId == null || cityId.trim().isEmpty()) {
            callback.onError("City ID is required");
            return;
        }
        get("/cities/" + cityId, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("city") && body.get("city").isJsonObject()) {
                    JsonObject obj = body.getAsJsonObject("city");
                    CityModel city = new CityModel(
                            optString(obj, "id", optString(obj, "cityId", optString(obj, "_id", ""))),
                            optString(obj, "name", ""),
                            optString(obj, "stateName", "Odisha"),
                            obj.has("latitude") && !obj.get("latitude").isJsonNull() ? obj.get("latitude").getAsDouble() : 0.0,
                            obj.has("longitude") && !obj.get("longitude").isJsonNull() ? obj.get("longitude").getAsDouble() : 0.0
                    );
                    callback.onSuccess(city);
                } else {
                    callback.onError("City details not found");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // ================= PHASE 3: DONATION HISTORY & CERTIFICATION =================

    public void getDonationHistory(final ApiCallback<List<DonationHistoryItem>> callback) {
        get("/donations/my-history", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<DonationHistoryItem> list = new ArrayList<>();
                if (body.has("donations") && body.get("donations").isJsonArray()) {
                    JsonArray arr = body.getAsJsonArray("donations");
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        DonationHistoryItem item = new DonationHistoryItem();
                        item.id = optString(obj, "id", optString(obj, "_id", ""));
                        item.certificateId = optString(obj, "certificateId", "");
                        item.hospital = optString(obj, "hospital", "Hospital");
                        item.bloodGroup = optString(obj, "bloodGroup", "O+");
                        item.unitsDonated = optInt(obj, "unitsDonated", 1);
                        item.donationDate = optString(obj, "donationDate", "");
                        item.status = optString(obj, "status", "VERIFIED");
                        item.verifiedAt = optString(obj, "verifiedAt", "");
                        list.add(item);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getCertificate(String certificateId, final ApiCallback<DonationCertificate> callback) {
        get("/donations/certificate/" + certificateId, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                if (body.has("certificate") && body.get("certificate").isJsonObject()) {
                    JsonObject c = body.getAsJsonObject("certificate");
                    DonationCertificate cert = new DonationCertificate();
                    cert.certificateId = optString(c, "certificateId", certificateId);
                    cert.donorName = optString(c, "donorName", "Voluntary Donor");
                    cert.bloodGroup = optString(c, "bloodGroup", "O+");
                    cert.unitsDonated = optInt(c, "unitsDonated", 1);
                    cert.hospital = optString(c, "hospital", "Hospital");
                    cert.donationDate = optString(c, "donationDate", "");
                    cert.verifiedAt = optString(c, "verifiedAt", "");
                    cert.status = optString(c, "status", "VERIFIED");
                    cert.attendingDoctor = optString(c, "attendingDoctor", "Attending Medical Officer");
                    cert.doctorRegistrationNo = optString(c, "doctorRegistrationNo", "");
                    cert.verifiedBy = optString(c, "verifiedBy", "LifeShare Medical Authority");
                    cert.certificateHash = optString(c, "certificateHash", "");
                    cert.isTamperProofValid = !c.has("isTamperProofValid") || c.get("isTamperProofValid").getAsBoolean();
                    callback.onSuccess(cert);
                } else {
                    callback.onError("Certificate data unavailable");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getHospitalDoctors(String hospitalId, final ApiCallback<List<HospitalDoctor>> callback) {
        get("/hospitals/" + hospitalId + "/doctors", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                try {
                    List<HospitalDoctor> list = new ArrayList<>();
                    if (body.has("doctors") && body.get("doctors").isJsonArray()) {
                        for (JsonElement el : body.getAsJsonArray("doctors")) {
                            HospitalDoctor doc = gson.fromJson(el, HospitalDoctor.class);
                            if (doc != null) list.add(doc);
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse doctors: " + e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void addHospitalDoctor(String hospitalId, String name, String regNo, String designation, String department, final ApiCallback<HospitalDoctor> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("registrationNumber", regNo);
        json.addProperty("designation", designation != null && !designation.isEmpty() ? designation : "Medical Officer");
        json.addProperty("department", department != null && !department.isEmpty() ? department : "Blood Transfusion Unit");

        post("/hospitals/" + hospitalId + "/doctors", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                try {
                    if (body.has("doctor") && body.get("doctor").isJsonObject()) {
                        HospitalDoctor doc = gson.fromJson(body.getAsJsonObject("doctor"), HospitalDoctor.class);
                        callback.onSuccess(doc);
                    } else {
                        callback.onError("Failed to parse added doctor");
                    }
                } catch (Exception e) {
                    callback.onError("Doctor addition failed: " + e.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void verifyDonation(String emergencyId, String donorId, String doctorName, String doctorRegNo, int units, final ApiCallback<JsonObject> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("donorId", donorId);
        if (doctorName != null && !doctorName.isEmpty()) json.addProperty("doctorName", doctorName);
        if (doctorRegNo != null && !doctorRegNo.isEmpty()) json.addProperty("doctorRegistrationNo", doctorRegNo);
        json.addProperty("unitsDonated", units > 0 ? units : 1);

        post("/emergencies/" + emergencyId + "/verify-donation", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess(body);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void verifyDonation(String emergencyId, String donorId, final ApiCallback<JsonObject> callback) {
        verifyDonation(emergencyId, donorId, "Attending Medical Officer", "", 1, callback);
    }

    public void getCoordinatorPendingVerifications(final ApiCallback<List<PendingVerificationItem>> callback) {
        get("/emergencies/coordinator/pending-verifications", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<PendingVerificationItem> list = new ArrayList<>();
                if (body.has("pendingVerifications") && body.get("pendingVerifications").isJsonArray()) {
                    JsonArray arr = body.getAsJsonArray("pendingVerifications");
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        PendingVerificationItem item = new PendingVerificationItem();
                        item.responseId = optString(obj, "responseId", "");
                        item.requestId = optString(obj, "requestId", "");
                        item.patientName = optString(obj, "patientName", "Patient");
                        item.hospital = optString(obj, "hospital", "Hospital");
                        item.bloodGroup = optString(obj, "bloodGroup", "O+");
                        item.unitsRequired = optInt(obj, "unitsRequired", 1);
                        item.unitsFulfilled = optInt(obj, "unitsFulfilled", 0);
                        item.donorId = optString(obj, "donorId", "");
                        item.donorName = optString(obj, "donorName", "Voluntary Donor");
                        item.donorMobile = optString(obj, "donorMobile", "");
                        item.donorBloodGroup = optString(obj, "donorBloodGroup", "O+");
                        item.donorVerificationStatus = optString(obj, "donorVerificationStatus", "UNVERIFIED");
                        item.arrivedAt = optString(obj, "arrivedAt", "");
                        item.createdAt = optString(obj, "createdAt", "");
                        item.status = optString(obj, "status", "ARRIVED");
                        list.add(item);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getCoordinatorHospitalEmergencies(final ApiCallback<List<AdminEmergencyItem>> callback) {
        get("/emergencies/coordinator/hospital-emergencies", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<AdminEmergencyItem> list = new ArrayList<>();
                if (body.has("emergencies") && body.get("emergencies").isJsonArray()) {
                    for (JsonElement el : body.getAsJsonArray("emergencies")) {
                        AdminEmergencyItem item = gson.fromJson(el, AdminEmergencyItem.class);
                        if (item != null) list.add(item);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getCoordinatorHistory(final ApiCallback<List<CoordinatorHistoryItem>> callback) {
        get("/emergencies/coordinator/history", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                List<CoordinatorHistoryItem> list = new ArrayList<>();
                if (body.has("history") && body.get("history").isJsonArray()) {
                    for (JsonElement el : body.getAsJsonArray("history")) {
                        CoordinatorHistoryItem item = gson.fromJson(el, CoordinatorHistoryItem.class);
                        if (item != null) list.add(item);
                    }
                }
                callback.onSuccess(list);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private String optString(JsonObject obj, String key, String def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return def;
    }

    private int optInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    // ================= HTTP UTILITIES WITH BEARER AUTH =================

    private interface InternalCallback {
        void onSuccess(JsonObject body);
        void onError(String error);
    }

    private Request.Builder newAuthenticatedBuilder() {
        Request.Builder builder = new Request.Builder();
        if (authToken != null && !authToken.trim().isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + authToken.trim());
        }
        return builder;
    }

    private void get(String endpoint, final InternalCallback callback) {
        Request request = newAuthenticatedBuilder()
                .url(baseUrl + endpoint)
                .get()
                .build();
        execute(request, callback);
    }

    private void delete(String endpoint, final InternalCallback callback) {
        Request request = newAuthenticatedBuilder()
                .url(baseUrl + endpoint)
                .delete()
                .build();
        execute(request, callback);
    }

    private void post(String endpoint, String jsonBody, final InternalCallback callback) {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = newAuthenticatedBuilder()
                .url(baseUrl + endpoint)
                .post(body)
                .build();
        execute(request, callback);
    }

    private void put(String endpoint, String jsonBody, final InternalCallback callback) {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = newAuthenticatedBuilder()
                .url(baseUrl + endpoint)
                .put(body)
                .build();
        execute(request, callback);
    }

    private void execute(Request request, final InternalCallback callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull final IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("Network unreachable (" + baseUrl + "): " + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull final Response response) throws IOException {
                final String responseStr = response.body() != null ? response.body().string() : "{}";
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JsonObject obj = gson.fromJson(responseStr, JsonObject.class);
                            if (response.isSuccessful() && (obj == null || !obj.has("success") || obj.get("success").getAsBoolean())) {
                                callback.onSuccess(obj != null ? obj : new JsonObject());
                            } else {
                                String msg = (obj != null && obj.has("message")) ? obj.get("message").getAsString() : "Server returned code " + response.code();
                                callback.onError(msg);
                            }
                        } catch (Exception parseErr) {
                            if (response.isSuccessful()) {
                                callback.onSuccess(new JsonObject());
                            } else {
                                callback.onError("Invalid server response from " + baseUrl);
                            }
                        }
                    }
                });
            }
        });
    }

    public static class HospitalModel {
        private final String id;
        private final String name;
        private final String address;
        private final String city;
        private final String phone;
        private String cityId = null;
        private double distanceKm = 0.0;

        public HospitalModel(String id, String name, String address, String city, String phone) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.city = city;
            this.phone = phone;
        }

        public HospitalModel(String id, String name, String address, String city, String phone, String cityId) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.city = city;
            this.phone = phone;
            this.cityId = cityId;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getAddress() { return address; }
        public String getCity() { return city; }
        public String getPhone() { return phone; }
        public String getCityId() { return cityId; }
        public void setCityId(String cityId) { this.cityId = cityId; }
        public double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }

        @Override
        public String toString() {
            if (distanceKm > 0.0) {
                return name + " (~" + distanceKm + " km)";
            }
            return name + (city != null && !city.isEmpty() ? " (" + city + ")" : "");
        }
    }

    public static class DonationHistoryItem {
        public String id;
        public String certificateId;
        public String hospital;
        public String bloodGroup;
        public int unitsDonated;
        public String donationDate;
        public String status;
        public String verifiedAt;
    }

    public static class DonorBadge {
        public String badgeId;
        public String name;
        public String iconKey;
        public String description;
        public String awardedAt;
    }

    public static class LeaderboardItem {
        public int rank;
        public String id;
        public String displayName;
        public String bloodGroup;
        public String city;
        public int donationsCount;
        public int karmaPoints;
        public int badgeCount;
        public DonorBadge topBadge;
    }

    public static class HospitalDoctor {
        public String id;
        public String name;
        public String designation;
        public String registrationNumber;
        public String department;
        public String phone;
        public String email;
    }

    public static class DonationCertificate {
        public String certificateId;
        public String donorName;
        public String bloodGroup;
        public int unitsDonated;
        public String hospital;
        public String donationDate;
        public String verifiedAt;
        public String status;
        public String attendingDoctor;
        public String doctorRegistrationNo;
        public String verifiedBy;
        public String certificateHash;
        public boolean isTamperProofValid = true;
    }

    public static class PendingVerificationItem {
        public String responseId;
        public String requestId;
        public String patientName;
        public String hospital;
        public String bloodGroup;
        public int unitsRequired;
        public int unitsFulfilled;
        public String donorId;
        public String donorName;
        public String donorMobile;
        public String donorBloodGroup;
        public String donorVerificationStatus;
        public String arrivedAt;
        public String createdAt;
        public String status;
    }

    public static class CoordinatorHistoryItem {
        public String id;
        public String certificateId;
        public String donorName;
        public String donorBloodGroup;
        public String hospital;
        public int unitsDonated;
        public String verifiedAt;
        public String status;
    }

    public static class CityModel {
        private final String id;
        private final String name;
        private final String stateName;
        private final double latitude;
        private final double longitude;

        public CityModel(String id, String name, String stateName, double latitude, double longitude) {
            this.id = id;
            this.name = name;
            this.stateName = stateName;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getStateName() { return stateName; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }

        @Override
        public String toString() {
            return name + (stateName != null && !stateName.isEmpty() ? " (" + stateName + ")" : "");
        }
    }

    // =========================================================================
    // PHASE 4: ADMIN OPERATIONS APIS & DTOs
    // =========================================================================

    public static class AdminStats {
        public int totalUsers;
        public int activeDonors;
        public int coordinators;
        public int hospitals;
        public int activeEmergencies;
        public int verifiedDonations;
        public int totalEmergencies;
        public int fulfilledEmergencies;
        public int fulfillmentRate = 100;
        public int avgResponseMinutes = 18;
    }

    public static class AdminUserItem {
        public String id;
        public String name;
        public String email;
        public String mobile;
        public String bloodGroup;
        public String role;
        public String accountStatus;
        public String verificationStatus;
        public String city;
        public String cityId;
        public String hospital;
        public String hospitalId;
        public boolean isAvailable;
        public int donationsCount;
        public String createdAt;
    }

    public static class AdminHospitalItem {
        public String id;
        public String name;
        public String address;
        public String phone;
        public String city;
        public String cityId;
        public boolean verified;
        public boolean emergencySupport;
        public List<AdminCoordinatorRef> coordinators;

        public static class AdminCoordinatorRef {
            public String id;
            public String name;
            public String email;
            public String mobile;
            public String accountStatus;
        }
    }

    public static class HospitalCoordinatorsResponse {
        public String hospitalName;
        public List<ActiveCoordinatorItem> activeCoordinators;
        public List<ExCoordinatorItem> exCoordinators;
    }

    public static class ActiveCoordinatorItem {
        public String id;
        public String name;
        public String email;
        public String mobile;
        public String role;
        public String status;
    }

    public static class ExCoordinatorItem {
        public String id;
        public String name;
        public String email;
        public String mobile;
        public String staffId;
        public String assignedAt;
        public String revokedAt;
        public String reason;
        public int donationsVerifiedCount;
    }

    public static class CoordinatorVerificationsResponse {
        public int count;
        public List<CoordinatorVerificationRecord> verifications;
    }

    public static class CoordinatorVerificationRecord {
        public String certificateId;
        public String donorName;
        public String bloodGroup;
        public int unitsDonated;
        public String hospital;
        public String patientName;
        public String verifiedAt;
    }

    public static class AdminEmergencyItem {
        public String id;
        public String patientName;
        public String hospital;
        public String bloodGroup;
        public int unitsRequired;
        public int unitsFulfilled;
        public int acceptedCount;
        public String status;
        public boolean isFulfilled;
        public String urgency;
        public String createdAt;
    }

    public static class AdminAuditLogItem {
        public String id;
        public String action;
        public String actionDisplay;
        public String detailsDisplay;
        public String actorRole;
        public String entityType;
        public String createdAt;
    }

    public static class BloodCampItem {
        public String id;
        public String title;
        public String organizerName;
        public String hospitalName;
        public String hospitalId;
        public String venueAddress;
        public String cityName;
        public String cityId;
        public double latitude;
        public double longitude;
        public String startDate;
        public String endDate;
        public int targetUnits;
        public int collectedUnits;
        public String contactPhone;
        public String status;
        public int rsvpCount;
        public boolean isUserRsvped;
    }

    public void getAdminStats(final ApiCallback<AdminStats> callback) {
        get("/admin/stats", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    JsonObject statsObj = response.getAsJsonObject("stats");
                    AdminStats stats = gson.fromJson(statsObj, AdminStats.class);
                    callback.onSuccess(stats != null ? stats : new AdminStats());
                } catch (Exception e) {
                    callback.onError("Failed to parse admin stats: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void triggerCooldownScan(final ApiCallback<JsonObject> callback) {
        post("/admin/cooldown-scan", "{}", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getAdminUsers(String search, String role, String accountStatus, int page, int limit, final ApiCallback<List<AdminUserItem>> callback) {
        StringBuilder query = new StringBuilder("/admin/users?page=").append(page).append("&limit=").append(limit);
        if (search != null && !search.trim().isEmpty()) {
            query.append("&search=").append(java.net.URLEncoder.encode(search.trim()));
        }
        if (role != null && !role.trim().isEmpty() && !role.equalsIgnoreCase("ALL")) {
            query.append("&role=").append(role.trim().toUpperCase());
        }
        if (accountStatus != null && !accountStatus.trim().isEmpty() && !accountStatus.equalsIgnoreCase("ALL")) {
            query.append("&accountStatus=").append(accountStatus.trim().toUpperCase());
        }

        get(query.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    List<AdminUserItem> list = new ArrayList<>();
                    if (response.has("users") && response.get("users").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("users")) {
                            AdminUserItem item = gson.fromJson(el, AdminUserItem.class);
                            if (item != null) list.add(item);
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse users list: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getAdminUser(String userId, final ApiCallback<AdminUserItem> callback) {
        get("/admin/users/" + userId, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    JsonObject userObj = response.has("user") ? response.getAsJsonObject("user") : response;
                    AdminUserItem item = gson.fromJson(userObj, AdminUserItem.class);
                    callback.onSuccess(item);
                } catch (Exception e) {
                    callback.onError("Failed to parse user details: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void updateAdminUserStatus(String userId, String status, final ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("status", status);

        patch("/admin/users/" + userId + "/status", body.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void updateAdminUserRole(String userId, String role, String hospitalId, final ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("role", role);
        if (hospitalId != null && !hospitalId.trim().isEmpty()) {
            body.addProperty("hospitalId", hospitalId.trim());
        }

        patch("/admin/users/" + userId + "/role", body.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void updateAdminUserVerification(String userId, String verificationStatus, final ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("verificationStatus", verificationStatus);

        patch("/admin/users/" + userId + "/verification", body.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getAdminHospitals(final ApiCallback<List<AdminHospitalItem>> callback) {
        get("/admin/hospitals", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    List<AdminHospitalItem> list = new ArrayList<>();
                    if (response.has("hospitals") && response.get("hospitals").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("hospitals")) {
                            AdminHospitalItem item = gson.fromJson(el, AdminHospitalItem.class);
                            if (item != null) list.add(item);
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse hospitals: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void createAdminHospital(String name, String address, String cityId, double lat, double lng, String phone, final ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("address", address);
        if (cityId != null) body.addProperty("cityId", cityId);
        body.addProperty("latitude", lat);
        body.addProperty("longitude", lng);
        if (phone != null) body.addProperty("phone", phone);

        post("/admin/hospitals", body.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void deleteAdminHospital(String hospitalId, final ApiCallback<JsonObject> callback) {
        delete("/admin/hospitals/" + hospitalId, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void onboardCoordinator(String name, String email, String mobile, String hospitalId, String staffId, final ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("email", email);
        body.addProperty("mobile", mobile);
        body.addProperty("hospitalId", hospitalId);
        if (staffId != null && !staffId.trim().isEmpty()) {
            body.addProperty("staffId", staffId.trim());
        }

        post("/admin/coordinators/onboard", body.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void assignAdminCoordinator(String hospitalId, String coordinatorId, final ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("coordinatorId", coordinatorId);

        post("/admin/hospitals/" + hospitalId + "/coordinators", body.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void unassignHospitalCoordinator(String hospitalId, String coordinatorId, String reason, final ApiCallback<JsonObject> callback) {
        JsonObject body = new JsonObject();
        if (reason != null && !reason.trim().isEmpty()) {
            body.addProperty("reason", reason.trim());
        }

        post("/admin/hospitals/" + hospitalId + "/coordinators/" + coordinatorId + "/unassign", body.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getHospitalCoordinators(String hospitalId, final ApiCallback<HospitalCoordinatorsResponse> callback) {
        get("/admin/hospitals/" + hospitalId + "/coordinators", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    HospitalCoordinatorsResponse res = gson.fromJson(response, HospitalCoordinatorsResponse.class);
                    callback.onSuccess(res);
                } catch (Exception e) {
                    callback.onError("Failed to parse coordinators: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getCoordinatorVerifications(String coordinatorId, final ApiCallback<CoordinatorVerificationsResponse> callback) {
        get("/admin/coordinators/" + coordinatorId + "/verifications", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    CoordinatorVerificationsResponse res = gson.fromJson(response, CoordinatorVerificationsResponse.class);
                    callback.onSuccess(res);
                } catch (Exception e) {
                    callback.onError("Failed to parse verifications: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getAdminEmergencies(final ApiCallback<List<AdminEmergencyItem>> callback) {
        get("/admin/emergencies?page=1&limit=50", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    List<AdminEmergencyItem> list = new ArrayList<>();
                    if (response.has("emergencies") && response.get("emergencies").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("emergencies")) {
                            AdminEmergencyItem item = gson.fromJson(el, AdminEmergencyItem.class);
                            if (item != null) list.add(item);
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse emergencies: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getAdminAuditLogs(final ApiCallback<List<AdminAuditLogItem>> callback) {
        get("/admin/audit-logs?page=1&limit=50", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    List<AdminAuditLogItem> list = new ArrayList<>();
                    if (response.has("logs") && response.get("logs").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("logs")) {
                            AdminAuditLogItem item = gson.fromJson(el, AdminAuditLogItem.class);
                            if (item != null) list.add(item);
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse audit logs: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getLeaderboard(final ApiCallback<List<LeaderboardItem>> callback) {
        get("/users/leaderboard", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    List<LeaderboardItem> list = new ArrayList<>();
                    if (response.has("leaderboard") && response.get("leaderboard").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("leaderboard")) {
                            LeaderboardItem item = gson.fromJson(el, LeaderboardItem.class);
                            if (item != null) list.add(item);
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse leaderboard: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void coordinatorOnboardDonor(String name, String mobile, String bloodGroup, String gender, final ApiCallback<JsonObject> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("mobile", mobile);
        json.addProperty("bloodGroup", bloodGroup);
        if (gender != null && !gender.isEmpty()) json.addProperty("gender", gender);

        post("/users/coordinator-onboard-donor", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess(body);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void getBloodCamps(String cityId, String status, final ApiCallback<List<BloodCampItem>> callback) {
        StringBuilder query = new StringBuilder("/camps?");
        if (cityId != null && !cityId.trim().isEmpty()) {
            query.append("cityId=").append(cityId.trim()).append("&");
        }
        if (status != null && !status.trim().isEmpty()) {
            query.append("status=").append(status.trim().toUpperCase());
        }

        get(query.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    List<BloodCampItem> list = new ArrayList<>();
                    if (response.has("camps") && response.get("camps").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("camps")) {
                            BloodCampItem item = gson.fromJson(el, BloodCampItem.class);
                            if (item != null) list.add(item);
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse donation camps: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void createBloodCamp(JsonObject campData, final ApiCallback<JsonObject> callback) {
        post("/camps", campData.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void toggleCampRsvp(String campId, final ApiCallback<JsonObject> callback) {
        post("/camps/" + campId + "/rsvp", "{}", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void downloadExportCsv(String endpoint, final ApiCallback<String> callback) {
        Request request = newAuthenticatedBuilder()
                .url(baseUrl + endpoint)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull final IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("Failed to download report: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull final Response response) throws IOException {
                final String csvData = response.body() != null ? response.body().string() : "";
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (response.isSuccessful() && !csvData.isEmpty()) {
                            callback.onSuccess(csvData);
                        } else {
                            callback.onError("Failed to generate CSV report (Code " + response.code() + ")");
                        }
                    }
                });
            }
        });
    }

    private void patch(String endpoint, String jsonBody, final InternalCallback callback) {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = newAuthenticatedBuilder()
                .url(baseUrl + endpoint)
                .patch(body)
                .build();
        execute(request, callback);
    }

    public static class ChatHistoryResponse {
        public JsonObject emergency;
        public List<ChatMessage> messages = new ArrayList<>();
    }

    public void getChatHistory(String emergencyId, final ApiCallback<ChatHistoryResponse> callback) {
        get("/chat/" + emergencyId + "/messages", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    ChatHistoryResponse res = new ChatHistoryResponse();
                    if (response.has("emergency") && response.get("emergency").isJsonObject()) {
                        res.emergency = response.getAsJsonObject("emergency");
                    }
                    if (response.has("messages") && response.get("messages").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("messages")) {
                            ChatMessage msg = gson.fromJson(el, ChatMessage.class);
                            if (msg != null) res.messages.add(msg);
                        }
                    }
                    callback.onSuccess(res);
                } catch (Exception e) {
                    callback.onError("Failed to parse chat messages: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void sendChatMessage(String emergencyId, String messageText, String messageType, final ApiCallback<ChatMessage> callback) {
        JsonObject json = new JsonObject();
        json.addProperty("messageText", messageText);
        json.addProperty("messageType", messageType != null ? messageType : "TEXT");

        post("/chat/" + emergencyId + "/messages", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    if (response.has("message") && response.get("message").isJsonObject()) {
                        ChatMessage msg = gson.fromJson(response.getAsJsonObject("message"), ChatMessage.class);
                        callback.onSuccess(msg);
                    } else {
                        callback.onError("Invalid message response");
                    }
                } catch (Exception e) {
                    callback.onError("Failed to parse sent message: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void sendChatEta(String emergencyId, Integer etaMinutes, Double lat, Double lng, String customStatus, final ApiCallback<ChatMessage> callback) {
        JsonObject json = new JsonObject();
        if (etaMinutes != null) json.addProperty("etaMinutes", etaMinutes);
        if (lat != null && lng != null) {
            json.addProperty("latitude", lat);
            json.addProperty("longitude", lng);
        }
        if (customStatus != null && !customStatus.isEmpty()) {
            json.addProperty("customStatus", customStatus);
        }

        post("/chat/" + emergencyId + "/eta", json.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    if (response.has("message") && response.get("message").isJsonObject()) {
                        ChatMessage msg = gson.fromJson(response.getAsJsonObject("message"), ChatMessage.class);
                        callback.onSuccess(msg);
                    } else {
                        callback.onError("Invalid ETA response");
                    }
                } catch (Exception e) {
                    callback.onError("Failed to parse ETA message: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void getBloodBanks(String city, final ApiCallback<List<BloodBankCenter>> callback) {
        String endpoint = "/bloodbanks";
        if (city != null && !city.isEmpty() && !"All".equalsIgnoreCase(city)) {
            endpoint += "?city=" + Uri.encode(city);
        }
        get(endpoint, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                try {
                    List<BloodBankCenter> list = new ArrayList<>();
                    if (response.has("bloodBanks") && response.get("bloodBanks").isJsonArray()) {
                        for (JsonElement el : response.getAsJsonArray("bloodBanks")) {
                            JsonObject b = el.getAsJsonObject();
                            String id = b.has("id") && !b.get("id").isJsonNull() ? b.get("id").getAsString() : "";
                            String name = b.has("name") && !b.get("name").isJsonNull() ? b.get("name").getAsString() : "Blood Bank";
                            String address = b.has("address") && !b.get("address").isJsonNull() ? b.get("address").getAsString() : "";
                            String bCity = b.has("city") && !b.get("city").isJsonNull() ? b.get("city").getAsString() : "Odisha";
                            String phone = b.has("contactNumber") && !b.get("contactNumber").isJsonNull() ? b.get("contactNumber").getAsString() : "";
                            String timings = b.has("timings") && !b.get("timings").isJsonNull() ? b.get("timings").getAsString() : "24x7 Open";
                            String type = b.has("type") && !b.get("type").isJsonNull() ? b.get("type").getAsString() : "Blood Bank";
                            double lat = 0.0;
                            double lng = 0.0;
                            if (b.has("location") && b.get("location").isJsonObject()) {
                                JsonObject loc = b.getAsJsonObject("location");
                                if (loc.has("coordinates") && loc.get("coordinates").isJsonArray()) {
                                    JsonArray coords = loc.getAsJsonArray("coordinates");
                                    if (coords.size() >= 2) {
                                        lng = coords.get(0).getAsDouble();
                                        lat = coords.get(1).getAsDouble();
                                    }
                                }
                            }
                            list.add(new BloodBankCenter(id, name, address, bCity, phone, timings, lat, lng, type));
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError("Failed to parse blood banks: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    // ==========================================
    // NOTIFICATIONS API
    // ==========================================

    public static class NotificationListResponse {
        public int totalCount = 0;
        public int unreadCount = 0;
        public java.util.Map<String, Integer> channelUnread = new java.util.HashMap<>();
        public List<InAppNotification> notifications = new ArrayList<>();
    }

    public void getNotifications(String channel, int page, final ApiCallback<NotificationListResponse> callback) {
        String path = "/notifications?page=" + page;
        if (channel != null && !channel.isEmpty() && !"ALL".equalsIgnoreCase(channel)) {
            path += "&channel=" + channel;
        }

        get(path, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                try {
                    NotificationListResponse response = new NotificationListResponse();
                    response.totalCount = optInt(body, "totalCount", 0);
                    response.unreadCount = optInt(body, "unreadCount", 0);

                    if (body.has("channelUnread") && body.get("channelUnread").isJsonObject()) {
                        JsonObject cu = body.getAsJsonObject("channelUnread");
                        for (java.util.Map.Entry<String, JsonElement> entry : cu.entrySet()) {
                            response.channelUnread.put(entry.getKey(), entry.getValue().getAsInt());
                        }
                    }

                    if (body.has("notifications") && body.get("notifications").isJsonArray()) {
                        JsonArray arr = body.getAsJsonArray("notifications");
                        for (JsonElement el : arr) {
                            if (!el.isJsonObject()) continue;
                            JsonObject obj = el.getAsJsonObject();
                            InAppNotification item = new InAppNotification();
                            item.setId(optString(obj, "_id", optString(obj, "id", "")));
                            item.setTitle(optString(obj, "title", "Notification"));
                            item.setBody(optString(obj, "body", ""));
                            item.setType(optString(obj, "type", "SYSTEM"));
                            item.setChannel(optString(obj, "channel", "UPDATES"));
                            item.setCollapseKey(optString(obj, "collapseKey", null));
                            item.setStatus(optString(obj, "status", "ACTIVE"));
                            item.setRead(obj.has("isRead") && obj.get("isRead").getAsBoolean());
                            item.setCreatedAt(optString(obj, "createdAt", ""));
                            item.setUpdatedAt(optString(obj, "updatedAt", ""));

                            if (obj.has("data") && obj.get("data").isJsonObject()) {
                                JsonObject d = obj.getAsJsonObject("data");
                                item.setRequestId(optString(d, "requestId", optString(d, "emergencyId", "")));
                                item.setEmergencyId(optString(d, "emergencyId", ""));
                                item.setChatRoomId(optString(d, "chatRoomId", ""));
                                item.setCertificateId(optString(d, "certificateId", ""));
                                item.setDonorName(optString(d, "donorName", ""));
                                item.setPatientName(optString(d, "patientName", ""));
                                item.setHospitalName(optString(d, "hospitalName", optString(d, "hospital", "")));
                                item.setBloodGroup(optString(d, "bloodGroup", ""));
                                item.setUnits(optInt(d, "units", 1));
                            }
                            response.notifications.add(item);
                        }
                    }
                    callback.onSuccess(response);
                } catch (Exception e) {
                    callback.onError("Failed to parse notifications: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void markNotificationRead(String notificationId, final ApiCallback<Integer> callback) {
        patch("/notifications/" + notificationId + "/read", "{}", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                int unread = optInt(body, "unreadCount", 0);
                callback.onSuccess(unread);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void markAllNotificationsRead(String channel, final ApiCallback<Integer> callback) {
        JsonObject payload = new JsonObject();
        if (channel != null && !channel.isEmpty()) {
            payload.addProperty("channel", channel);
        }
        patch("/notifications/mark-all-read", payload.toString(), new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                int unread = optInt(body, "unreadCount", 0);
                callback.onSuccess(unread);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void deleteNotification(String notificationId, final ApiCallback<Integer> callback) {
        delete("/notifications/" + notificationId, new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                int unread = optInt(body, "unreadCount", 0);
                callback.onSuccess(unread);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void clearAllNotifications(String channel, final ApiCallback<Void> callback) {
        delete("/notifications/clear-all", new InternalCallback() {
            @Override
            public void onSuccess(JsonObject body) {
                callback.onSuccess(null);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }
}
