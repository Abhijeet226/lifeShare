package com.example.abhijeet.bloodbank.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.abhijeet.bloodbank.data.model.*
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.data.repository.DataManager
import com.example.abhijeet.bloodbank.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyChatScreen(
    emergencyId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTracking: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }
    val dataManager = remember { DataManager.getInstance(context) }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var fleetTracking by remember { mutableStateOf<EmergencyTrackingResponse?>(null) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val offlineQueue = remember { mutableStateListOf<ChatMessage>() }

    // Polling loop for messages and fleet tracking
    LaunchedEffect(emergencyId) {
        while (true) {
            // 1. Fetch Chat Messages
            when (val chatRes = apiRepo.getChatHistory(emergencyId)) {
                is NetworkResult.Success -> {
                    if (chatRes.data.messages.size != messages.size) {
                        messages = chatRes.data.messages
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                    // Flush offline queue if needed
                    if (offlineQueue.isNotEmpty()) {
                        val pending = offlineQueue.toList()
                        offlineQueue.clear()
                        pending.forEach { msg ->
                            apiRepo.sendChatMessage(emergencyId, msg.messageText, msg.messageType)
                        }
                    }
                }
                else -> {}
            }

            // 2. Fetch Multi-Donor Fleet Tracking
            when (val trackRes = apiRepo.getEmergencyTracking(emergencyId)) {
                is NetworkResult.Success -> {
                    fleetTracking = trackRes.data
                }
                else -> {}
            }

            delay(3500) // 3.5s polling
        }
    }

    fun sendMessage(text: String, type: String = "TEXT") {
        if (text.isBlank()) return
        val optimisticMsg = ChatMessage(
            id = "temp_${System.currentTimeMillis()}",
            emergencyId = emergencyId,
            senderName = dataManager.getCurrentUser()?.name ?: "Me",
            messageText = text,
            messageType = type,
            isMine = true,
            isPending = false
        )
        messages = messages + optimisticMsg
        inputText = ""

        coroutineScope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
            when (val res = apiRepo.sendChatMessage(emergencyId, text, type)) {
                is NetworkResult.Success -> {
                    // Update successfully
                }
                is NetworkResult.Error -> {
                    offlineQueue.add(optimisticMsg)
                    Toast.makeText(context, "Network offline: Message staged for auto-resend", Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Emergency Coordination Room", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Live volunteer timeline & status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToTracking(emergencyId) }) {
                        Icon(Icons.Default.Map, contentDescription = "Map Tracking", tint = BrandCrimson)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Pinned Multi-Donor Live Fleet Ticker Banner
            val activeEnRoute = fleetTracking?.donors?.filter { it.journeyStatus == "TRAVELLING" || it.journeyStatus == "ACCEPTED" } ?: emptyList()
            AnimatedVisibility(
                visible = activeEnRoute.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onNavigateToTracking(emergencyId) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚗", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (activeEnRoute.size == 1) {
                                val d = activeEnRoute.first()
                                Text(
                                    text = "En Route: ${d.name} (${d.bloodGroup})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "~${d.etaMinutes}m away • ${String.format("%.1f km", d.distanceKm)} (Tap to view map)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            } else {
                                Text(
                                    text = "Fleet Transit (${activeEnRoute.size} Donors En Route)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val summary = activeEnRoute.sortedBy { it.etaMinutes }.joinToString(" • ") { "${it.name.split(" ")[0]} ~${it.etaMinutes}m" }
                                Text(
                                    text = "$summary (Tap for Live Map)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Chat Messages Feed
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(messages, key = { index, msg -> if (msg.id.isNotBlank()) "${msg.id}_$index" else "msg_$index" }) { _, msg ->
                    ChatBubble(message = msg)
                }
            }

            // Milestone Quick Action Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MilestoneAction.ALL) { action ->
                    SuggestionChip(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            sendMessage(action.text, "MILESTONE")
                        },
                        label = { Text("${action.iconEmoji} ${action.label}", fontSize = 12.sp) },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Chat Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Coordinate with team...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            sendMessage(inputText.trim())
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = BrandCrimson),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isMine = message.isMine || message.senderName == "Me"
    val isMilestone = message.messageType == "MILESTONE" || message.messageType == "STATUS_CHANGE"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMilestone) Alignment.CenterHorizontally else if (isMine) Alignment.End else Alignment.Start
    ) {
        if (isMilestone) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "${message.senderName}: ${message.messageText}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            if (!isMine) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                ),
                color = if (isMine) BrandCrimson else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 1.dp
            ) {
                Text(
                    text = message.messageText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
