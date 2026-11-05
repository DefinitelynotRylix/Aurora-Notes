package com.aurora.notes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.AppUpdateManager
import com.aurora.notes.util.HapticHelper
import com.aurora.notes.util.LocalLogger
import com.aurora.notes.util.LogLevel
import com.aurora.notes.util.PrivateStorageStats
import com.aurora.notes.util.StorageCleanupReport
import com.aurora.notes.util.UpdateScanResult
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ThemeOptionItem(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector
)

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
    onClearLogs: () -> Unit = {},
    onExportLogs: () -> String = { "" },
    onTriggerBiometricAuth: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val logs by LocalLogger.logs.collectAsState()
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

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

    val filteredLogs = remember(logs, selectedLevelFilter) {
        if (selectedLevelFilter == null) logs else logs.filter { it.level == selectedLevelFilter }
    }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    val themeOptions = remember {
        listOf(
            ThemeOptionItem("system", "System", "Match phone theme", Icons.Default.BrightnessAuto),
            ThemeOptionItem("light", "Light", "Warm mauve canvas", Icons.Default.LightMode),
            ThemeOptionItem("dark", "Dark", "Obsidian amethyst", Icons.Default.DarkMode)
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
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "settings_header") {
            Column {
                Text(
                    text = "Settings & Preferences",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Manage app appearance & theme, self-updates, biometric security, custom category pins, and local data.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // 0. App Version & Self-Update Card (GitHub Release Scanner + Auto Self-Update & Forced Restart)
        item(key = "settings_self_update") {
            Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = AuroraRoyalViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "App Version & Self-Update",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Installed: v$currentVersionName • Build $currentVersionCode • GPL-3.0",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AuroraRoyalViolet
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Minimal Internet Access Warning Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AuroraRoyalViolet.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Minimal Internet Access Notice: All your notes, tags, and checklists remain 100% offline on your device. Internet permission is strictly limited to manual update scans when you tap 'Scan for New Update' below to pull the latest Aurora Notes.apk release.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                // Live Download Progress Bar
                if (isDownloadingUpdate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pulling latest Aurora Notes.apk release...",
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
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            color = AuroraRoyalViolet,
                            trackColor = AuroraRoyalViolet.copy(alpha = 0.18f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                }

                // Scan Result Feedback Banner
                when (val res = scanResult) {
                    is UpdateScanResult.UpToDate -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "You are on the latest version (v${res.currentVersion})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                    Text(
                                        text = "Latest GitHub Release: v${res.latestVersion} — No update needed.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }

                    is UpdateScanResult.UpdateAvailable -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AuroraRoyalViolet.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Update Available: v${res.latestVersion} (Current: v${res.currentVersion})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AuroraRoyalViolet
                                )
                                if (res.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = res.releaseNotes.take(180),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }

                                if (downloadedApkFile != null && awaitingInstallPermission) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val file = downloadedApkFile
                                            if (file != null) {
                                                AppUpdateManager.installApkAndRestart(context, file)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Install & Update Now",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is UpdateScanResult.Error -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AuroraDangerRed.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, AuroraDangerRed.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = AuroraDangerRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.message,
                                    fontSize = 11.sp,
                                    color = AuroraDangerRed
                                )
                            }
                        }
                    }

                    null -> {}
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { scanAndAutoPullUpdate() },
                    enabled = !isScanningUpdate && !isDownloadingUpdate,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isScanningUpdate) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning Releases...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scan for Updates",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        }

        // 1. App Appearance & Theme Card
        item(key = "settings_appearance") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "App Appearance & Theme",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AuroraRoyalViolet.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = themeMode.uppercase(Locale.US),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuroraRoyalViolet,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themeOptions.forEach { option ->
                        val isSelected = themeMode.equals(option.id, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) AuroraRoyalViolet.copy(alpha = 0.14f) else MaterialTheme.colorScheme.background,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectThemeMode(option.id) }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        tint = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = AuroraRoyalViolet,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = option.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.description,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // 1b. In-App Haptic Feedback Card
        item(key = "settings_haptics") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Haptic Feedback",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isHapticsEnabled) "Active on navbar & controls" else "Disabled",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isHapticsEnabled) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Switch(
                        checked = isHapticsEnabled,
                        onCheckedChange = { enabled ->
                            onSetHapticsEnabled(enabled)
                            if (enabled) {
                                HapticHelper.triggerHaptic(view = view, context = context, enabled = true, durationMs = 35L)
                                Toast.makeText(context, "In-app haptic feedback enabled", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "In-app haptic feedback disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Provides tactile vibration feedback when pressing navigation bar buttons, even if system touch vibration is muted.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                )

                if (isHapticsEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            HapticHelper.triggerHaptic(view = view, context = context, enabled = true, durationMs = 40L)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "Test Haptic Pulse",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuroraRoyalViolet
                        )
                    }
                }
            }
        }
        }

        // 2. Biometric / PIN Lock Card
        item(key = "settings_biometric") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = AuroraRoyalViolet, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Biometric & App Lock",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
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
                                // Require biometric authentication to disable app lock
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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Require Android native fingerprint, face unlock, or device PIN before opening Aurora Notes.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                )
            }
        }
        }

        // 2b. Private External Storage & Optional Room Database Encryption Card
        item(key = "settings_storage") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Private Storage & DB Encryption",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${storageStats.syncedFileCount} synced file(s) • ${storageStats.totalBytes} B on device",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuroraRoyalViolet
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "AES-256",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Device Private External Storage Path",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuroraRoyalViolet
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = storageStats.directoryPath.ifBlank {
                                "/storage/emulated/0/Android/data/com.aurora.notes/files/aurora_private_notes"
                            },
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AuroraRoyalViolet,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Room Database & File Encryption",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Mandatory hardware-backed AndroidKeyStore AES-256-GCM encryption is permanently enabled for all Room SQLite records and mirrored device storage files.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AuroraRoyalViolet,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Orphaned & Temp File Cleanup",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Orphaned note files, incomplete .tmp writes, and stale shared/update cache files are swept automatically on launch, resume, and every note change.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                        )
                    }
                }

                if (!lastCleanupSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AuroraRoyalViolet.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lastCleanupSummary,
                            fontSize = 11.sp,
                            color = AuroraRoyalViolet,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
        }

        // 3. Welcome Tour & What's New Guide Card
        item(key = "settings_welcome") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AuroraRoyalViolet, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Welcome Guide & What's New",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View the Aurora Notes welcome guide or check what's new in the latest release.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenWelcomeTour,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                    ) {
                        Text("Open Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onOpenWhatsNew,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.5f))
                    ) {
                        Text("What's New", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuroraRoyalViolet)
                    }
                }
            }
        }
        }

        // 4. Aurora Notes Community Card
        item(key = "settings_community") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Aurora Notes Community",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AuroraRoyalViolet.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "TELEGRAM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuroraRoyalViolet,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Join the official Aurora Notes community server on Telegram for user discussions, questions, feature proposals, and direct announcements.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/GithubAuroraNotes"))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open browser for community link", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Join Community Server", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        }

        // 5. Local Diagnostic Logs Card
        item(key = "settings_logs") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = AuroraRoyalViolet, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local Diagnostic Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "${logs.size}/250 entries",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Restricted to errors and warnings only. Logged locally in device memory when an issue occurs.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedLevelFilter == null,
                        onClick = { selectedLevelFilter = null },
                        label = { Text("ALL (${logs.size})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedLevelFilter == LogLevel.WARN,
                        onClick = { selectedLevelFilter = LogLevel.WARN },
                        label = { Text("WARN (${logs.count { it.level == LogLevel.WARN }})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedLevelFilter == LogLevel.ERROR,
                        onClick = { selectedLevelFilter = LogLevel.ERROR },
                        label = { Text("ERROR (${logs.count { it.level == LogLevel.ERROR }})", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF0F0B14), shape = RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = "No warnings or errors recorded.",
                            color = Color(0xFF6B6577),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            filteredLogs.forEach { log ->
                                val levelColor = when (log.level) {
                                    LogLevel.ERROR -> Color(0xFFFB7185)
                                    LogLevel.WARN -> Color(0xFFFBBF24)
                                    LogLevel.SECURITY -> Color(0xFFA78BFA)
                                }
                                Text(
                                    text = "[${dateFormat.format(Date(log.timestamp))}] [${log.level.name}] [${log.category}] ${log.message}",
                                    color = levelColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val dump = onExportLogs()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Aurora Diagnostic Logs", dump)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied logs to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onClearLogs,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuroraDangerRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val dump = onExportLogs()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("aurora_diagnostic_logs.txt", dump)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Exported logs to clipboard", Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export .txt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }

        // 6. Danger Zone: Wipe All Application Data (Biometric Required)
        item(key = "settings_danger_zone") {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = AuroraDangerRed.copy(alpha = 0.08f)
            ),
            border = BorderStroke(1.dp, AuroraDangerRed.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AuroraDangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wipe All Application Data",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AuroraDangerRed
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Permanently delete all notes, trashed items, and custom category pins from local storage. Requires biometric or device PIN verification.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showWipeConfirmDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wipe All Notes",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        }
    }
}
