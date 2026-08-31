package com.example.abhijeet.bloodbank.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.abhijeet.bloodbank.data.model.BloodGroup
import com.example.abhijeet.bloodbank.data.model.NetworkResult
import com.example.abhijeet.bloodbank.data.model.UserProfile
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }

    var selectedGroup by remember { mutableStateOf("All") }
    var cityQuery by remember { mutableStateOf("") }
    var donors by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun performSearch() {
        isLoading = true
        coroutineScope.launch {
            val bgParam = if (selectedGroup == "All") null else selectedGroup
            val cityParam = if (cityQuery.isBlank()) null else cityQuery.trim()
            when (val res = apiRepo.searchDonors(bgParam, cityParam)) {
                is NetworkResult.Success -> {
                    donors = res.data
                    isLoading = false
                }
                else -> {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(selectedGroup) {
        performSearch()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Voluntary Donors", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // City search bar
            OutlinedTextField(
                value = cityQuery,
                onValueChange = {
                    cityQuery = it
                    performSearch()
                },
                label = { Text("Search by City or Area") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (cityQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            cityQuery = ""
                            performSearch()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Blood group filter pills
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedGroup == "All",
                        onClick = { selectedGroup = "All" },
                        label = { Text("All Groups") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                items(BloodGroup.ALL) { bg ->
                    FilterChip(
                        selected = selectedGroup == bg,
                        onClick = { selectedGroup = bg },
                        label = { Text(bg, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandCrimson,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandCrimson)
                }
            } else if (donors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matching donors found", fontWeight = FontWeight.Bold)
                        Text("Try selecting a different blood group or area.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Text(
                            text = "${donors.size} available voluntary donors found",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(donors, key = { it.id }) { donor ->
                        DonorCard(
                            donor = donor,
                            onCall = {
                                if (donor.mobileNumber.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${donor.mobileNumber.trim()}"))
                                    context.startActivity(intent)
                                }
                            },
                            onSms = {
                                if (donor.mobileNumber.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${donor.mobileNumber.trim()}"))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DonorCard(
    donor: UserProfile,
    onCall: () -> Unit,
    onSms: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = donor.bloodGroup,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = donor.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📍 ${donor.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (donor.totalDonations > 0) {
                    Text(
                        text = "🏆 ${donor.totalDonations} Donation(s) Completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAvailable
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onCall,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                IconButton(
                    onClick = onSms,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(Icons.Default.Message, contentDescription = "SMS", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}
