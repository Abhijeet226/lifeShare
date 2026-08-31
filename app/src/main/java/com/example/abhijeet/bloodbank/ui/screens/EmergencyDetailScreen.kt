package com.example.abhijeet.bloodbank.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.abhijeet.bloodbank.data.model.*
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.data.repository.DataManager
import com.example.abhijeet.bloodbank.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyDetailScreen(
    emergencyId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToTracking: (String) -> Unit,
    onNavigateToCertificate: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }
    val dataManager = remember { DataManager.getInstance(context) }

    var detailResponse by remember { mutableStateOf<EmergencyDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isActionLoading by remember { mutableStateOf(false) }

    fun loadDetail() {
        coroutineScope.launch {
            when (val res = apiRepo.getEmergencyDetail(emergencyId)) {
                is NetworkResult.Success -> {
                    detailResponse = res.data
                    isLoading = false
                }
                is NetworkResult.Error -> {
                    isLoading = false
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun submitJourney(action: String) {
        isActionLoading = true
        coroutineScope.launch {
            val userLoc = dataManager.getLastKnownLocation()
            when (val res = apiRepo.updateJourneyStatus(emergencyId, action, userLoc?.first, userLoc?.second)) {
                is NetworkResult.Success -> {
                    isActionLoading = false
                    Toast.makeText(context, res.data, Toast.LENGTH_SHORT).show()
                    loadDetail()
                }
                is NetworkResult.Error -> {
                    isActionLoading = false
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun shareMilestone(patientName: String, hospital: String, bloodGroup: String) {
        val shareBody = """
            🩸 LifeShare Voluntary Blood Donor Milestone!
            I just successfully completed a voluntary blood donation for $patientName ($bloodGroup) at $hospital.
            
            Every drop counts. Join the life-saving movement on LifeShare:
            https://lifeshare-74c2.onrender.com
        """.trimIndent()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareBody)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Donation Milestone"))
    }

    LaunchedEffect(emergencyId) {
        loadDetail()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Coordination", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToChat(emergencyId) }) {
                        Icon(Icons.Default.Chat, contentDescription = "Chat", tint = BrandCrimson)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandCrimson)
            }
        } else {
            val emergency = detailResponse?.emergency
            val journey = detailResponse?.myJourney
            val isRequester = detailResponse?.isRequester == true
            val journeyState = JourneyState.fromString(journey?.status)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = emergency?.patientName ?: "Patient",
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🏥 ${emergency?.hospital ?: "Hospital"}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    if (!emergency?.hospitalAddress.isNullOrBlank()) {
                                        Text(
                                            text = emergency!!.hospitalAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = BrandCrimson
                                ) {
                                    Text(
                                        text = emergency?.bloodGroup ?: "O+",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Units: ${emergency?.unitsRequired ?: 1} Required • ${emergency?.acceptedCount ?: 0} Accepted",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = emergency?.statusDisplay ?: "Searching",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BrandCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2FA Handshake Counter Box (Visible when ARRIVED)
                if (journeyState == JourneyState.ARRIVED && !journey?.handshakeCode.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "HOSPITAL COUNTER CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = journey!!.handshakeCode!!,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = BrandCrimson,
                                        letterSpacing = 8.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Present this 4-digit code to the hospital blood bank desk coordinator to certify your donation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // 5-Stage Visual Journey Tracker (Donor View)
                if (!isRequester) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Your Donation Journey",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                JourneyStepRow(title = "1. Request Received", subtitle = "Matched with emergency", isCompleted = journeyState.stepNumber >= 1, isActive = journeyState == JourneyState.NOTIFIED)
                                JourneyStepRow(title = "2. Emergency Accepted", subtitle = "Committed to donate", isCompleted = journeyState.stepNumber >= 2, isActive = journeyState == JourneyState.ACCEPTED)
                                JourneyStepRow(title = "3. In Transit to Hospital", subtitle = "Live GPS tracking active", isCompleted = journeyState.stepNumber >= 3, isActive = journeyState == JourneyState.TRAVELLING)
                                JourneyStepRow(title = "4. Arrived at Hospital", subtitle = "500m Geofence Verified", isCompleted = journeyState.stepNumber >= 4, isActive = journeyState == JourneyState.ARRIVED)
                                JourneyStepRow(title = "5. Donation Certified", subtitle = "Doctor Verified & Certificate Minted", isCompleted = journeyState.stepNumber >= 5, isActive = journeyState == JourneyState.DONATED || journeyState == JourneyState.COMPLETED)
                            }
                        }
                    }

                    // Action Buttons depending on State
                    item {
                        when (journeyState) {
                            JourneyState.NOTIFIED, JourneyState.VIEWED -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { submitJourney("ACCEPTED") },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson),
                                        enabled = !isActionLoading
                                    ) {
                                        Text("Accept SOS", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { submitJourney("DECLINED") },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Decline")
                                    }
                                }
                            }
                            JourneyState.ACCEPTED -> {
                                Button(
                                    onClick = { submitJourney("TRAVELLING") },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson),
                                    enabled = !isActionLoading
                                ) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Journey to Hospital", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            JourneyState.TRAVELLING -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { submitJourney("ARRIVED") },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusAvailable),
                                        enabled = !isActionLoading
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("I've Arrived at Hospital Counter", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    OutlinedButton(
                                        onClick = { onNavigateToTracking(emergencyId) },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open In-App GPS Map")
                                    }
                                }
                            }
                            JourneyState.COMPLETED, JourneyState.DONATED -> {
                                Button(
                                    onClick = {
                                        emergency?.let { em ->
                                            shareMilestone(em.patientName, em.hospital, em.bloodGroup)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share Donation Milestone", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {}
                        }
                    }
                }

                // Tracking / Chat Nav Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToChat(emergencyId) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Live Chat", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Button(
                            onClick = { onNavigateToTracking(emergencyId) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Track Fleet", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun JourneyStepRow(
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = when {
                isCompleted -> StatusAvailable
                isActive -> BrandCrimson
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isCompleted) "✓" else "•",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) BrandCrimson else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
