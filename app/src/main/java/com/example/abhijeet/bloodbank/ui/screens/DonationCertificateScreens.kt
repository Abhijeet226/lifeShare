package com.example.abhijeet.bloodbank.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.abhijeet.bloodbank.data.model.DonationCertificate
import com.example.abhijeet.bloodbank.data.model.DonationHistoryItem
import com.example.abhijeet.bloodbank.data.model.NetworkResult
import com.example.abhijeet.bloodbank.data.repository.ApiRepository
import com.example.abhijeet.bloodbank.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCertificate: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }

    var historyList by remember { mutableStateOf<List<DonationHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        coroutineScope.launch {
            when (val res = apiRepo.getDonationHistory()) {
                is NetworkResult.Success -> {
                    historyList = res.data
                    isLoading = false
                }
                else -> {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donation History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandCrimson)
            }
        } else if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📜", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Donation History Found", fontWeight = FontWeight.Bold)
                    Text("Your completed voluntary blood donations and certified badges will be stored here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
                itemsIndexed(historyList, key = { index, item -> if (item.id.isNotBlank()) "${item.id}_$index" else "dh_$index" }) { _, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.certificateId.isNotBlank()) {
                                    onNavigateToCertificate(item.certificateId)
                                }
                            },
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
                                Text("🏥 ${item.hospital}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Donated: ${item.unitsDonated} Unit • Certified", style = MaterialTheme.typography.bodySmall, color = StatusAvailable)
                                if (item.certificateId.isNotBlank()) {
                                    Text("Cert: #${item.certificateId.take(8)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Icon(Icons.Default.Verified, contentDescription = null, tint = StatusAvailable)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationCertificateScreen(
    certificateId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiRepo = remember { ApiRepository.getInstance() }

    var certificate by remember { mutableStateOf<DonationCertificate?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(certificateId) {
        coroutineScope.launch {
            when (val res = apiRepo.getDonationCertificate(certificateId)) {
                is NetworkResult.Success -> {
                    certificate = res.data
                    isLoading = false

                    // Generate verification QR
                    try {
                        val writer = QRCodeWriter()
                        val bitMatrix = writer.encode(
                            "https://lifeshare-74c2.onrender.com/api/certificates/$certificateId",
                            BarcodeFormat.QR_CODE,
                            256,
                            256
                        )
                        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565)
                        for (x in 0 until 256) {
                            for (y in 0 until 256) {
                                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                            }
                        }
                        qrBitmap = bmp
                    } catch (e: Exception) {
                        qrBitmap = null
                    }
                }
                else -> {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certified Donation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareText = "🩸 Certified Voluntary Blood Donor Milestone on LifeShare!\nVerify Certificate: https://lifeshare-74c2.onrender.com/api/certificates/$certificateId"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Certificate"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = BrandCrimson)
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
            val cert = certificate
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Gold Seal Certificate Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎖️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "CERTIFICATE OF DONATION",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            letterSpacing = 2.sp,
                            color = Color(0xFFD4AF37)
                        )
                        Text(
                            text = "LifeShare Voluntary Blood Network",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text("This certifies that", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = cert?.donorName ?: "Voluntary Donor",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "has voluntarily donated ${cert?.unitsDonated ?: 1} unit(s) of ${cert?.bloodGroup ?: "O+"} Blood at",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = cert?.hospital ?: "Hospital Blood Bank",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = BrandCrimson
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Attending Medical Officer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(cert?.attendingDoctor ?: "Medical Officer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (!cert?.doctorRegistrationNo.isNullOrBlank()) {
                                    Text("Reg: ${cert?.doctorRegistrationNo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            qrBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Cert QR",
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "SHA-256 Tamper-Proof Verified • Cert ID #${cert?.certificateId?.take(12) ?: "VALID"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
