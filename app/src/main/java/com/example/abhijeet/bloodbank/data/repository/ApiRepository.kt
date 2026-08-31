package com.example.abhijeet.bloodbank.data.repository

import com.example.abhijeet.bloodbank.data.model.*
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Idiomatic Kotlin Coroutines Network Repository
 */
class ApiRepository private constructor() {

    companion object {
        const val BASE_URL = "https://lifeshare-74c2.onrender.com/api"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: ApiRepository? = null

        fun getInstance(): ApiRepository {
            return instance ?: synchronized(this) {
                instance ?: ApiRepository().also { instance = it }
            }
        }
    }

    private val gson = GsonBuilder().setLenient().create()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            requestBuilder.addHeader("Accept", "application/json")
            requestBuilder.addHeader("User-Agent", "LifeShare-Android/3.0-Compose")

            // Inject bearer token if available
            val token = tokenProvider?.invoke()
            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    var tokenProvider: (() -> String?)? = null

    private suspend fun executeRequest(request: Request): NetworkResult<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            val json = try {
                val element = JsonParser.parseString(responseBody)
                if (element.isJsonObject) {
                    element.asJsonObject
                } else if (element.isJsonArray) {
                    val obj = JsonObject()
                    obj.add("data", element.asJsonArray)
                    obj.add("emergencies", element.asJsonArray)
                    obj.add("donors", element.asJsonArray)
                    obj.add("notifications", element.asJsonArray)
                    obj
                } else {
                    JsonObject()
                }
            } catch (e: Exception) {
                val obj = JsonObject()
                obj.addProperty("raw", responseBody)
                obj
            }

            if (response.isSuccessful) {
                NetworkResult.Success(json)
            } else {
                val errorMsg = if (json.has("message")) json.get("message").asString else "HTTP Error ${response.code}"
                NetworkResult.Error(errorMsg, response.code)
            }
        } catch (e: IOException) {
            NetworkResult.Error(e.localizedMessage ?: "Network connection failed. Please check internet.", -1)
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "An unexpected error occurred.", -1)
        }
    }

    // ==========================================
    // AUTH & PROFILE ENDPOINTS
    // ==========================================

    suspend fun login(emailOrMobile: String, password: String): NetworkResult<AuthResponse> {
        val payload = JsonObject().apply {
            addProperty("email", emailOrMobile)
            addProperty("password", password)
        }
        val request = Request.Builder()
            .url("$BASE_URL/auth/login")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val token = if (json.has("token")) json.get("token").asString else null
                val user = if (json.has("user") && json.get("user").isJsonObject) {
                    gson.fromJson(json.getAsJsonObject("user"), UserProfile::class.java).copy(token = token)
                } else null
                NetworkResult.Success(AuthResponse(true, "Login successful", token, user))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun register(user: UserProfile, password: String): NetworkResult<AuthResponse> {
        val payload = JsonObject().apply {
            addProperty("name", user.name)
            addProperty("email", user.email)
            addProperty("password", password)
            addProperty("mobileNumber", user.mobileNumber)
            addProperty("bloodGroup", user.bloodGroup)
            addProperty("location", user.location)
            addProperty("role", user.role)
        }
        val request = Request.Builder()
            .url("$BASE_URL/auth/register")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val token = if (json.has("token")) json.get("token").asString else null
                val createdUser = if (json.has("user") && json.get("user").isJsonObject) {
                    gson.fromJson(json.getAsJsonObject("user"), UserProfile::class.java).copy(token = token)
                } else null
                NetworkResult.Success(AuthResponse(true, "Registration successful", token, createdUser))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun getProfile(): NetworkResult<UserProfile> {
        val request = Request.Builder().url("$BASE_URL/users/me").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val userObj = if (json.has("user")) json.getAsJsonObject("user") else json
                val user = gson.fromJson(userObj, UserProfile::class.java)
                NetworkResult.Success(user)
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun updateProfile(user: UserProfile): NetworkResult<UserProfile> {
        val payload = gson.toJsonTree(user).asJsonObject
        val request = Request.Builder()
            .url("$BASE_URL/users/me")
            .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val userObj = if (json.has("user")) json.getAsJsonObject("user") else json
                NetworkResult.Success(gson.fromJson(userObj, UserProfile::class.java))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    // ==========================================
    // EMERGENCY SOS & MULTI-DONOR ENDPOINTS
    // ==========================================

    suspend fun getEmergencies(city: String? = null, bloodGroup: String? = null): NetworkResult<List<EmergencyRequest>> {
        val urlBuilder = "$BASE_URL/emergencies".toHttpUrlOrNull()?.newBuilder() ?: return NetworkResult.Error("Invalid URL")
        if (!city.isNullOrBlank()) urlBuilder.addQueryParameter("city", city)
        if (!bloodGroup.isNullOrBlank()) urlBuilder.addQueryParameter("bloodGroup", bloodGroup)

        val request = Request.Builder().url(urlBuilder.build()).get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val list = mutableListOf<EmergencyRequest>()
                val arr = when {
                    json.has("emergencies") && json.get("emergencies").isJsonArray -> json.getAsJsonArray("emergencies")
                    json.has("data") && json.get("data").isJsonArray -> json.getAsJsonArray("data")
                    else -> null
                }
                arr?.forEach { elem ->
                    if (elem.isJsonObject) list.add(gson.fromJson(elem, EmergencyRequest::class.java))
                }
                NetworkResult.Success(list)
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun getEmergencyDetail(emergencyId: String): NetworkResult<EmergencyDetailResponse> {
        val request = Request.Builder().url("$BASE_URL/emergencies/$emergencyId").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val emergency = if (json.has("emergency") && json.get("emergency").isJsonObject) {
                    gson.fromJson(json.getAsJsonObject("emergency"), EmergencyRequest::class.java)
                } else null

                val myJourney = if (json.has("myJourney") && json.get("myJourney").isJsonObject) {
                    gson.fromJson(json.getAsJsonObject("myJourney"), DonorJourneyInfo::class.java)
                } else null

                val isRequester = json.has("isRequester") && json.get("isRequester").asBoolean
                val notifiedCount = if (json.has("notifiedCount")) json.get("notifiedCount").asInt else 0
                val acceptedCount = if (json.has("acceptedCount")) json.get("acceptedCount").asInt else 0
                val remainingUnits = if (json.has("remainingUnits")) json.get("remainingUnits").asInt else 1

                val donors = mutableListOf<AcceptedDonorItem>()
                if (json.has("acceptedDonors") && json.get("acceptedDonors").isJsonArray) {
                    json.getAsJsonArray("acceptedDonors").forEach { el ->
                        if (el.isJsonObject) donors.add(gson.fromJson(el, AcceptedDonorItem::class.java))
                    }
                }

                NetworkResult.Success(EmergencyDetailResponse(
                    emergency = emergency,
                    myJourney = myJourney,
                    isRequester = isRequester,
                    notifiedCount = notifiedCount,
                    acceptedCount = acceptedCount,
                    remainingUnits = remainingUnits,
                    acceptedDonors = donors
                ))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun createEmergency(
        patientName: String,
        hospital: String,
        bloodGroup: String,
        unitsRequired: Int,
        urgency: String,
        contactNumber: String,
        hospitalAddress: String,
        lat: Double,
        lng: Double
    ): NetworkResult<EmergencyRequest> {
        val payload = JsonObject().apply {
            addProperty("patientName", patientName)
            addProperty("hospital", hospital)
            addProperty("bloodGroup", bloodGroup)
            addProperty("unitsRequired", unitsRequired)
            addProperty("urgency", urgency)
            addProperty("contactNumber", contactNumber)
            addProperty("hospitalAddress", hospitalAddress)
            val coords = JsonObject().apply {
                addProperty("latitude", lat)
                addProperty("longitude", lng)
            }
            add("hospitalCoordinates", coords)
        }

        val request = Request.Builder()
            .url("$BASE_URL/emergencies")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val emObj = if (json.has("emergency")) json.getAsJsonObject("emergency") else json
                NetworkResult.Success(gson.fromJson(emObj, EmergencyRequest::class.java))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun updateJourneyStatus(
        emergencyId: String,
        action: String,
        lat: Double? = null,
        lng: Double? = null
    ): NetworkResult<String> {
        val payload = JsonObject().apply {
            addProperty("action", action)
            if (lat != null) addProperty("latitude", lat)
            if (lng != null) addProperty("longitude", lng)
        }
        val request = Request.Builder()
            .url("$BASE_URL/emergencies/$emergencyId/journey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val msg = if (res.data.has("message")) res.data.get("message").asString else "Status updated"
                NetworkResult.Success(msg)
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun getEmergencyTracking(emergencyId: String): NetworkResult<EmergencyTrackingResponse> {
        val request = Request.Builder().url("$BASE_URL/emergency/$emergencyId/tracking").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val body = res.data
                val donors = mutableListOf<DonorTrackInfo>()
                if (body.has("donors") && body.get("donors").isJsonArray) {
                    body.getAsJsonArray("donors").forEach { el ->
                        if (el.isJsonObject) donors.add(gson.fromJson(el, DonorTrackInfo::class.java))
                    }
                }
                var hospLat = 20.2289
                var hospLng = 85.7770
                if (body.has("hospitalLocation") && body.get("hospitalLocation").isJsonObject) {
                    val hl = body.getAsJsonObject("hospitalLocation")
                    if (hl.has("latitude")) hospLat = hl.get("latitude").asDouble
                    if (hl.has("longitude")) hospLng = hl.get("longitude").asDouble
                }

                NetworkResult.Success(EmergencyTrackingResponse(
                    emergencyId = emergencyId,
                    patientName = if (body.has("patientName")) body.get("patientName").asString else "Patient",
                    hospital = if (body.has("hospital")) body.get("hospital").asString else "Hospital",
                    hospitalAddress = if (body.has("hospitalAddress")) body.get("hospitalAddress").asString else "",
                    hospitalLat = hospLat,
                    hospitalLng = hospLng,
                    unitsRequired = if (body.has("unitsRequired")) body.get("unitsRequired").asInt else 1,
                    acceptedCount = if (body.has("acceptedCount")) body.get("acceptedCount").asInt else 0,
                    unitsFulfilled = if (body.has("unitsFulfilled")) body.get("unitsFulfilled").asInt else 0,
                    donors = donors
                ))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    // ==========================================
    // COORDINATOR 2FA & VERIFICATION ENDPOINTS
    // ==========================================

    suspend fun getPendingVerifications(): NetworkResult<List<PendingVerificationItem>> {
        val request = Request.Builder().url("$BASE_URL/emergencies/coordinator/pending-verifications").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val list = mutableListOf<PendingVerificationItem>()
                val arr = if (res.data.has("pendingVerifications") && res.data.get("pendingVerifications").isJsonArray) {
                    res.data.getAsJsonArray("pendingVerifications")
                } else null
                arr?.forEach { el ->
                    if (el.isJsonObject) list.add(gson.fromJson(el, PendingVerificationItem::class.java))
                }
                NetworkResult.Success(list)
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun verifyDonation(
        emergencyId: String,
        donorId: String,
        doctorName: String,
        doctorRegNo: String,
        units: Int,
        handshakeCode: String?,
        isOverride: Boolean
    ): NetworkResult<JsonObject> {
        val payload = JsonObject().apply {
            addProperty("donorId", donorId)
            addProperty("doctorName", doctorName)
            addProperty("doctorRegistrationNo", doctorRegNo)
            addProperty("unitsDonated", units)
            if (!handshakeCode.isNullOrBlank()) addProperty("handshakeCode", handshakeCode)
            if (isOverride) addProperty("isOverride", true)
        }
        val request = Request.Builder()
            .url("$BASE_URL/emergencies/$emergencyId/verify-donation")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeRequest(request)
    }

    // ==========================================
    // COORDINATION CHAT ENDPOINTS
    // ==========================================

    suspend fun getChatHistory(emergencyId: String): NetworkResult<ChatHistoryResponse> {
        val request = Request.Builder().url("$BASE_URL/chat/$emergencyId/messages").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val json = res.data
                val em = if (json.has("emergency") && json.get("emergency").isJsonObject) json.getAsJsonObject("emergency") else null
                val messages = mutableListOf<ChatMessage>()
                if (json.has("messages") && json.get("messages").isJsonArray) {
                    json.getAsJsonArray("messages").forEach { el ->
                        if (el.isJsonObject) messages.add(gson.fromJson(el, ChatMessage::class.java))
                    }
                }
                NetworkResult.Success(ChatHistoryResponse(em, messages))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun sendChatMessage(
        emergencyId: String,
        text: String,
        messageType: String = "TEXT",
        etaMins: Int? = null,
        lat: Double? = null,
        lng: Double? = null
    ): NetworkResult<ChatMessage> {
        val payload = JsonObject().apply {
            addProperty("messageText", text)
            addProperty("messageType", messageType)
            if (etaMins != null) addProperty("etaMinutes", etaMins)
            if (lat != null) addProperty("latitude", lat)
            if (lng != null) addProperty("longitude", lng)
        }
        val request = Request.Builder()
            .url("$BASE_URL/chat/$emergencyId/messages")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val msgObj = if (res.data.has("chatMessage")) res.data.getAsJsonObject("chatMessage") else res.data
                NetworkResult.Success(gson.fromJson(msgObj, ChatMessage::class.java))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    // ==========================================
    // CERTIFICATES, NOTIFICATIONS & DONORS SEARCH
    // ==========================================

    suspend fun getDonationCertificate(certificateId: String): NetworkResult<DonationCertificate> {
        val request = Request.Builder().url("$BASE_URL/certificates/$certificateId").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val certObj = if (res.data.has("certificate")) res.data.getAsJsonObject("certificate") else res.data
                NetworkResult.Success(gson.fromJson(certObj, DonationCertificate::class.java))
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun getDonationHistory(): NetworkResult<List<DonationHistoryItem>> {
        val request = Request.Builder().url("$BASE_URL/donations/my-history").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val list = mutableListOf<DonationHistoryItem>()
                val arr = if (res.data.has("history") && res.data.get("history").isJsonArray) {
                    res.data.getAsJsonArray("history")
                } else null
                arr?.forEach { el ->
                    if (el.isJsonObject) list.add(gson.fromJson(el, DonationHistoryItem::class.java))
                }
                NetworkResult.Success(list)
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun getNotifications(): NetworkResult<List<InAppNotification>> {
        val request = Request.Builder().url("$BASE_URL/notifications").get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val list = mutableListOf<InAppNotification>()
                val arr = if (res.data.has("notifications") && res.data.get("notifications").isJsonArray) {
                    res.data.getAsJsonArray("notifications")
                } else null
                arr?.forEach { el ->
                    if (el.isJsonObject) list.add(gson.fromJson(el, InAppNotification::class.java))
                }
                NetworkResult.Success(list)
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }

    suspend fun searchDonors(bloodGroup: String?, city: String?): NetworkResult<List<UserProfile>> {
        val urlBuilder = "$BASE_URL/donors".toHttpUrlOrNull()?.newBuilder() ?: return NetworkResult.Error("Invalid URL")
        if (!bloodGroup.isNullOrBlank()) urlBuilder.addQueryParameter("bloodGroup", bloodGroup)
        if (!city.isNullOrBlank()) urlBuilder.addQueryParameter("city", city)

        val request = Request.Builder().url(urlBuilder.build()).get().build()
        return when (val res = executeRequest(request)) {
            is NetworkResult.Success -> {
                val list = mutableListOf<UserProfile>()
                val arr = if (res.data.has("donors") && res.data.get("donors").isJsonArray) {
                    res.data.getAsJsonArray("donors")
                } else null
                arr?.forEach { el ->
                    if (el.isJsonObject) list.add(gson.fromJson(el, UserProfile::class.java))
                }
                NetworkResult.Success(list)
            }
            is NetworkResult.Error -> res
            is NetworkResult.Loading -> NetworkResult.Loading
        }
    }
}
