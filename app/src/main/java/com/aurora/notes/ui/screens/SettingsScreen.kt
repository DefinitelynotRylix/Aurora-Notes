package com.aurora.notes.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.AppUpdateManager
import com.aurora.notes.util.HapticHelper
import com.aurora.notes.util.PrivateStorageStats
import com.aurora.notes.util.StorageCleanupReport
import com.aurora.notes.util.UpdateScanResult
import kotlinx.coroutines.launch
import java.io.File

private data class ThemeOptionItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    themeMode: String = "system",
    onSelectThemeMode: (String) -> Unit = {},
    isHapticsEnabled: Boolean = true,
    onSetHapticsEnabled: (Boolean) -> Unit = {},
    isDbEncryptionEnabled: Boolean = false,
    onSetDatabaseEncryption: (Boolean) -> Unit = {},
    storageStats: PrivateStorageStats = PrivateStorageStats(),
    lastCleanupSummary: String? = null,
    onCleanUpOrphanedFiles: ((StorageCleanupReport) -> Unit) -> Unit = {},
    userTags: List<String> = emptyList(),
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onWipeAllData: () -> Unit = {},
    isBiometricEnabled: Boolean = false,
    onSetBiometricLock: (Boolean) -> Unit = {},
    onOpenWelcomeTour: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onTriggerBiometricAuth: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    // App Version & Self-Update states
    val currentVersionName = remember { AppUpdateManager.currentVersionName }
    val currentVersionCode = remember { AppUpdateManager.currentVersionCode }
    var isScanningUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var scanResult by remember { mutableStateOf<UpdateScanResult?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var awaitingInstallPermission by remember { mutableStateOf(false) }

    val themeOptions = remember {
        listOf(
            ThemeOptionItem("system", "System", Icons.Default.BrightnessAuto),
            ThemeOptionItem("light", "Light", Icons.Default.LightMode),
            ThemeOptionItem("dark", "Dark", Icons.Default.DarkMode)
        )
    }

    fun triggerPullAndSelfUpdate(update: UpdateScanResult.UpdateAvailable) {
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        downloadProgress = 0
        coroutineScope.launch {
            val result = AppUpdateManager.downloadUpdateApk(
                context = context,
                downloadUrl = update.apkDownloadUrl,
                onProgress = { pct -> downloadProgress = pct }
            )
            isDownloadingUpdate = false
            result.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                    awaitingInstallPermission = true
                    Toast.makeText(
                        context,
                        "Please allow 'Install unknown apps' for Aurora Notes, then tap Install & Update Now.",
                        Toast.LENGTH_LONG
                    ).show()
                    AppUpdateManager.openUnknownSourcesSettings(context)
                } else {
                    awaitingInstallPermission = false
                    Toast.makeText(
                        context,
                        "Downloaded v${update.latestVersion}! Installing update & restarting...",
                        Toast.LENGTH_LONG
                    ).show()
                    AppUpdateManager.installApkAndRestart(context, apkFile)
                }
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    "Update download failed: ${err.localizedMessage ?: "Network error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun scanAndAutoPullUpdate() {
        if (isScanningUpdate || isDownloadingUpdate) return
        isScanningUpdate = true
        scanResult = null
        coroutineScope.launch {
            val res = AppUpdateManager.scanForUpdate(context)
            isScanningUpdate = false
            scanResult = res
            when (res) {
                is UpdateScanResult.UpToDate -> {
                    Toast.makeText(
                        context,
                        "Aurora Notes is at the latest version (v${res.currentVersion})!",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is UpdateScanResult.UpdateAvailable -> {
                    Toast.makeText(
                        context,
                        "New update v${res.latestVersion} found! Pulling release & self-updating...",
                        Toast.LENGTH_SHORT
                    ).show()
                    triggerPullAndSelfUpdate(res)
                }
                is UpdateScanResult.Error -> {
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AuroraDangerRed
                )
            },
            title = {
                Text(
                    text = "Biometric Verification Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Wiping all application data will permanently delete all active notes, trashed notes, and custom category pins from your device.\n\nPlease verify with your fingerprint, face unlock, or device PIN to proceed.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirmDialog = false
                        onTriggerBiometricAuth(
                            {
                                onWipeAllData()
                                Toast.makeText(
                                    context,
                                    "Biometric verified. All notes and categories have been wiped.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            { err ->
                                Toast.makeText(
                                    context,
                                    "Wipe aborted — Biometric verification failed: $err",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verify & Wipe All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Compact Header
        item(key = "settings_header") {
            Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Theme, lock, updates, and guides.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                )
            }
        }

        // 1. Appearance (Compact 3-option row)
        item(key = "settings_appearance") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        themeOptions.forEach { option ->
                            val isSelected = themeMode.equals(option.id, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = if (isSelected) AuroraRoyalViolet.copy(alpha = 0.14f) else MaterialTheme.colorScheme.background,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectThemeMode(option.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        tint = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = option.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Preferences & Security (Grouped Haptics + Biometric Lock)
        item(key = "settings_preferences") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    // Haptic Feedback row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AuroraRoyalViolet,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Haptic Feedback",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Subtle vibration on navigation taps",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isHapticsEnabled) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AuroraRoyalViolet.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable {
                                        HapticHelper.triggerHaptic(view = view, context = context, enabled = true, durationMs = 40L)
                                    }
                                ) {
                                    Text(
                                        text = "Test",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AuroraRoyalViolet,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            Switch(
                                checked = isHapticsEnabled,
                                onCheckedChange = { enabled ->
                                    onSetHapticsEnabled(enabled)
                                    if (enabled) {
                                        HapticHelper.triggerHaptic(view = view, context = context, enabled = true, durationMs = 35L)
                                        Toast.makeText(context, "Haptic feedback enabled", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Haptic feedback disabled", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // Biometric / PIN Lock row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = AuroraRoyalViolet,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Biometric / PIN Lock",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Require device unlock on launch",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                        }

                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { targetState ->
                                if (targetState) {
                                    onTriggerBiometricAuth(
                                        {
                                            onSetBiometricLock(true)
                                            Toast.makeText(context, "Biometric Lock Activated!", Toast.LENGTH_SHORT).show()
                                        },
                                        { err ->
                                            onSetBiometricLock(false)
                                            Toast.makeText(context, "Authentication Failed: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    onTriggerBiometricAuth(
                                        {
                                            onSetBiometricLock(false)
                                            Toast.makeText(context, "Biometric verified. App lock disabled.", Toast.LENGTH_SHORT).show()
                                        },
                                        { err ->
                                            Toast.makeText(context, "Authentication required to disable lock: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. App Updates (Compact)
        item(key = "settings_self_update") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = AuroraRoyalViolet,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "App Updates",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "v$currentVersionName • Build $currentVersionCode",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                        }

                        Button(
                            onClick = { scanAndAutoPullUpdate() },
                            enabled = !isScanningUpdate && !isDownloadingUpdate,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                        ) {
                            if (isScanningUpdate) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            } else {
                                Text("Check", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading Aurora Notes.apk...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuroraRoyalViolet
                            )
                            Text(
                                text = "$downloadProgress%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuroraRoyalViolet
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            color = AuroraRoyalViolet,
                            trackColor = AuroraRoyalViolet.copy(alpha = 0.18f),
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                        )
                    }

                    when (val res = scanResult) {
                        is UpdateScanResult.UpToDate -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Up to date (v${res.currentVersion})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }

                        is UpdateScanResult.UpdateAvailable -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AuroraRoyalViolet.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Update available: v${res.latestVersion}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AuroraRoyalViolet
                                    )
                                    if (downloadedApkFile != null && awaitingInstallPermission) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                downloadedApkFile?.let { AppUpdateManager.installApkAndRestart(context, it) }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Install & Update Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        is UpdateScanResult.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AuroraDangerRed.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, AuroraDangerRed.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = AuroraDangerRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = res.message, fontSize = 11.sp, color = AuroraDangerRed)
                                }
                            }
                        }

                        null -> {}
                    }
                }
            }
        }

        // 4. Guides & Community (Combined compact card)
        item(key = "settings_guides_community") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "Guides & Community",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenWelcomeTour,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.45f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tour", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AuroraRoyalViolet, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onOpenWhatsNew,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.45f)),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text("What's New", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AuroraRoyalViolet, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/GithubAuroraNotes"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Could not open browser for community link", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Telegram", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 5. Danger Zone (Compact single row)
        item(key = "settings_danger_zone") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AuroraDangerRed.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, AuroraDangerRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AuroraDangerRed,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Wipe All Data",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuroraDangerRed
                            )
                            Text(
                                text = "Requires biometric or PIN verification",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Button(
                        onClick = { showWipeConfirmDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed)
                    ) {
                        Text(
                            text = "Wipe",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
