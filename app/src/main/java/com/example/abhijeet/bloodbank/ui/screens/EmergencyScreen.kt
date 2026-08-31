package com.example.abhijeet.bloodbank.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.abhijeet.bloodbank.data.model.BloodGroup
import com.example.abhijeet.bloodbank.data.model.EmergencyRequest
import com.example.abhijeet.bloodbank.data.model.NetworkResult
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.data.repository.DataManager
import com.example.abhijeet.bloodbank.ui.theme.BrandCrimson
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    onNavigateToEmergencyDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }

    var emergencies by remember { mutableStateOf<List<EmergencyRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    fun loadEmergencies() {
        isLoading = true
        coroutineScope.launch {
            when (val res = apiRepo.getEmergencies()) {
                is NetworkResult.Success -> {
                    emergencies = res.data
                    isLoading = false
                }
                else -> {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadEmergencies()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency SOS Broadcasts", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadEmergencies() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = BrandCrimson,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.AddAlert, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Broadcast SOS")
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandCrimson)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Urgent volunteer requirements in your area",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (emergencies.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🩺", fontSize = 36.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No Active Emergencies", fontWeight = FontWeight.Bold)
                                Text("Tap the red button below to request emergency blood.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    itemsIndexed(emergencies, key = { index, req -> if (req.id.isNotBlank()) "${req.id}_$index" else "em_$index" }) { _, req ->
                        EmergencyItemCard(
                            request = req,
                            onClick = { onNavigateToEmergencyDetail(req.id) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (showCreateDialog) {
            CreateEmergencyDialog(
                onDismiss = { showCreateDialog = false },
                onCreated = {
                    showCreateDialog = false
                    loadEmergencies()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEmergencyDialog(
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }

    var patientName by remember { mutableStateOf("") }
    var hospital by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var unitsRequired by remember { mutableStateOf(1) }
    var urgency by remember { mutableStateOf("CRITICAL") }
    var contactNumber by remember { mutableStateOf("") }
    var hospitalAddress by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedBloodDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Broadcast Emergency SOS", fontWeight = FontWeight.Bold, color = BrandCrimson) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hospital,
                    onValueChange = { hospital = it },
                    label = { Text("Hospital Name (e.g. AIIMS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Blood Group Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedBloodDropdown,
                    onExpandedChange = { expandedBloodDropdown = !expandedBloodDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = bloodGroup,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Blood Group Needed") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBloodDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedBloodDropdown,
                        onDismissRequest = { expandedBloodDropdown = false }
                    ) {
                        BloodGroup.ALL.forEach { bg ->
                            DropdownMenuItem(
                                text = { Text(bg, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    bloodGroup = bg
                                    expandedBloodDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Units Required:", modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (unitsRequired > 1) unitsRequired-- }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text("$unitsRequired", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { if (unitsRequired < 10) unitsRequired++ }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }

                OutlinedTextField(
                    value = contactNumber,
                    onValueChange = { contactNumber = it },
                    label = { Text("Attendant Contact Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hospitalAddress,
                    onValueChange = { hospitalAddress = it },
                    label = { Text("Hospital Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (patientName.isBlank() || hospital.isBlank() || contactNumber.isBlank()) {
                        errorMessage = "Please enter patient name, hospital, and phone"
                        return@Button
                    }
                    isSubmitting = true
                    coroutineScope.launch {
                        when (val res = apiRepo.createEmergency(
                            patientName = patientName.trim(),
                            hospital = hospital.trim(),
                            bloodGroup = bloodGroup,
                            unitsRequired = unitsRequired,
                            urgency = urgency,
                            contactNumber = contactNumber.trim(),
                            hospitalAddress = hospitalAddress.trim(),
                            lat = 20.2289,
                            lng = 85.7770
                        )) {
                            is NetworkResult.Success -> {
                                isSubmitting = false
                                onCreated()
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
                    Text("Broadcast Now")
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
