package com.example.abhijeet.bloodbank.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.abhijeet.bloodbank.data.model.EmergencyTrackingResponse
import com.example.abhijeet.bloodbank.data.model.NetworkResult
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.data.repository.LocationHelper
import com.example.abhijeet.bloodbank.ui.theme.BrandCrimson
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDonorTrackingScreen(
    emergencyId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val context = LocalContext.current
    val apiRepo = remember { ApiRepository.getInstance() }

    var trackingData by remember { mutableStateOf<EmergencyTrackingResponse?>(null) }
    var currentPollingInterval by remember { mutableStateOf(4000L) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Adaptive Geofenced Polling Loop
    LaunchedEffect(emergencyId) {
        while (true) {
            when (val res = apiRepo.getEmergencyTracking(emergencyId)) {
                is NetworkResult.Success -> {
                    trackingData = res.data
                    val track = res.data

                    // Adaptive interval based on closest donor distance
                    val closestDist = track.donors.minOfOrNull { it.distanceKm } ?: 5.0
                    currentPollingInterval = when {
                        closestDist > 2.0 -> 10000L // Battery Saver Transit Mode
                        closestDist > 1.0 -> 5000L  // Standard Mode
                        else -> 3000L              // High Precision Arrival Mode
                    }

                    // Update MapView markers dynamically
                    mapViewInstance?.let { map ->
                        map.overlays.clear()

                        val hospGeo = GeoPoint(track.hospitalLat, track.hospitalLng)
                        val hospMarker = Marker(map).apply {
                            position = hospGeo
                            title = "🏥 ${track.hospital}"
                            snippet = track.hospitalAddress
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(hospMarker)

                        track.donors.forEach { d ->
                            val donorGeo = GeoPoint(d.latitude, d.longitude)
                            val donorMarker = Marker(map).apply {
                                position = donorGeo
                                title = "🚗 ${d.name} (${d.bloodGroup})"
                                snippet = "ETA ~${d.etaMinutes}m (${String.format("%.1f km", d.distanceKm)})"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(donorMarker)

                            // Direct polyline route
                            val line = Polyline().apply {
                                addPoint(donorGeo)
                                addPoint(hospGeo)
                                outlinePaint.color = android.graphics.Color.RED
                                outlinePaint.strokeWidth = 6f
                            }
                            map.overlays.add(line)
                        }
                        map.invalidate()
                    }
                }
                else -> {}
            }
            delay(currentPollingInterval)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Donor GPS Fleet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(trackingData?.hospital ?: "Hospital Navigation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        trackingData?.let { t ->
                            val gmmIntentUri = Uri.parse("google.navigation:q=${t.hospitalLat},${t.hospitalLng}&mode=d")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${t.hospitalLat},${t.hospitalLng}")))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Navigation, contentDescription = "Google Maps", tint = BrandCrimson)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // OSMDroid Map Composable
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
                    Configuration.getInstance().userAgentValue = "LifeShare-Compose/3.0"
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        controller.setCenter(GeoPoint(20.2289, 85.7770))
                        mapViewInstance = this
                    }
                },
                update = { map ->
                    mapViewInstance = map
                }
            )

            // Bottom Tracking Status Floating Card
            trackingData?.let { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Destination: ${track.hospital}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                val activeCount = track.donors.size
                                Text(
                                    text = "$activeCount Donor(s) responding in real time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(
                                onClick = { onNavigateToChat(emergencyId) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chat")
                            }
                        }
                    }
                }
            }
        }
    }
}
