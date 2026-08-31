package com.example.abhijeet.bloodbank.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.abhijeet.bloodbank.data.model.NetworkResult
import com.example.abhijeet.bloodbank.data.model.PendingVerificationItem
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatorVerificationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }

    var pendingList by remember { mutableStateOf<List<PendingVerificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedItemForVerification by remember { mutableStateOf<PendingVerificationItem?>(null) }

    fun loadPending() {
        isLoading = true
        coroutineScope.launch {
            when (val res = apiRepo.getPendingVerifications()) {
                is NetworkResult.Success -> {
                    pendingList = res.data
                    isLoading = false
                }
                is NetworkResult.Error -> {
                    isLoading = false
                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPending()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hospital Desk Verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadPending() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandCrimson)
            }
        } else if (pendingList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Pending Donors in Desk Queue", fontWeight = FontWeight.Bold)
                    Text("When a donor arrives at the blood bank counter, their entry will appear here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(pendingList, key = { it.responseId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedItemForVerification = item },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = BrandCrimson
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(item.donorBloodGroup, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.donorName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Patient: ${item.patientName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("🏥 ${item.hospital}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Button(
                                onClick = { selectedItemForVerification = item },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson)
                            ) {
                                Text("Certify")
                            }
                        }
                    }
                }
            }
        }

        // Verification Modal Dialog
        selectedItemForVerification?.let { item ->
            VerifyDonationDoctorDialog(
                item = item,
                onDismiss = { selectedItemForVerification = null },
                onVerified = {
                    selectedItemForVerification = null
                    loadPending()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyDonationDoctorDialog(
    item: PendingVerificationItem,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }

    var handshakeCode by remember { mutableStateOf("") }
    var doctorName by remember { mutableStateOf("Attending Medical Officer") }
    var doctorRegNo by remember { mutableStateOf("") }
    var unitsDonated by remember { mutableStateOf(1) }
    var isOverride by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Certify Donation", fontWeight = FontWeight.Bold, color = BrandCrimson)
                Text("Donor: ${item.donorName} (${item.donorBloodGroup})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (!isOverride) {
                    OutlinedTextField(
                        value = handshakeCode,
                        onValueChange = { handshakeCode = it },
                        label = { Text("4-Digit Handshake Code from Donor") },
                        placeholder = { Text("e.g. 8492") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isOverride,
                        onCheckedChange = { isOverride = it }
                    )
                    Text("Emergency Override (Bypass Code)", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = doctorName,
                    onValueChange = { doctorName = it },
                    label = { Text("Attending Doctor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = doctorRegNo,
                    onValueChange = { doctorRegNo = it },
                    label = { Text("Doctor Registration / License No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Units Donated:", modifier = Modifier.weight(1f))
                    Text("$unitsDonated Unit", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isOverride && handshakeCode.isBlank()) {
                        errorMessage = "Please enter the 4-digit handshake code or check emergency override"
                        return@Button
                    }
                    isSubmitting = true
                    coroutineScope.launch {
                        when (val res = apiRepo.verifyDonation(
                            emergencyId = item.requestId,
                            donorId = item.donorId,
                            doctorName = doctorName.trim(),
                            doctorRegNo = doctorRegNo.trim(),
                            units = unitsDonated,
                            handshakeCode = if (isOverride) null else handshakeCode.trim(),
                            isOverride = isOverride
                        )) {
                            is NetworkResult.Success -> {
                                isSubmitting = false
                                Toast.makeText(context, "Donation verified and tamper-proof certificate minted!", Toast.LENGTH_LONG).show()
                                onVerified()
                            }
                            is NetworkResult.Error -> {
                                isSubmitting = false
                                errorMessage = res.message
                            }
                            is NetworkResult.Loading -> {}
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Verify & Mint Certificate")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
