package com.aurora.notes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.AppUpdateManager
import com.aurora.notes.util.LocalLogger
import com.aurora.notes.util.LogLevel
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
    userTags: List<String> = emptyList(),
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onWipeAllData: () -> Unit = {},
    isBiometricEnabled: Boolean = false,
    onSetBiometricLock: (Boolean) -> Unit = {},
    onOpenWelcomeTour: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onExportLogs: () -> String = { "" },
    onTriggerBiometricAuth: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val logs by LocalLogger.logs.collectAsState()
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    var newCategoryInput by remember { mutableStateOf("") }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    // App Version & Self-Update states
    val currentVersionName = remember { AppUpdateManager.currentVersionName }
    val currentVersionCode = remember { AppUpdateManager.currentVersionCode }
    var repoSlugInput by remember { mutableStateOf(AppUpdateManager.getConfiguredRepoSlug(context)) }
    var showRepoEditor by remember { mutableStateOf(repoSlugInput.isBlank()) }
    var isScanningUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var scanResult by remember { mutableStateOf<UpdateScanResult?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var awaitingInstallPermission by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, selectedLevelFilter) {
        if (selectedLevelFilter == null) logs else logs.filter { it.level == selectedLevelFilter }
    }

    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

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
        val cleanSlug = repoSlugInput
            .trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        if (cleanSlug.isNotBlank() && cleanSlug.contains("/")) {
            AppUpdateManager.setConfiguredRepoSlug(context, cleanSlug)
            repoSlugInput = cleanSlug
            showRepoEditor = false
        }
        isScanningUpdate = true
        scanResult = null
        coroutineScope.launch {
            val res = AppUpdateManager.scanForUpdate(context, cleanSlug)
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
                    if (cleanSlug.isBlank()) {
                        showRepoEditor = true
                    }
                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (pendingDeleteCategory != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            title = { Text("Delete Category Pin #${pendingDeleteCategory}?") },
            text = {
                Text(
                    "Deleting #${pendingDeleteCategory} removes this custom category pin and returns any notes under it back to All without deleting the notes.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cat = pendingDeleteCategory
                        pendingDeleteCategory = null
                        if (cat != null) {
                            onRemoveTag(cat)
                            Toast.makeText(context, "Removed category pin #$cat", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed)
                ) {
                    Text("Delete Pin", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteCategory = null }) {
                    Text("Cancel")
                }
            }
        )
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Page Header & Description
        Text(
            text = "Settings & Preferences",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Manage app appearance & theme, self-updates, biometric security, custom category pins, and local data.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        // 0. App Version & Self-Update Card (GitHub Release Scanner + Auto Self-Update & Forced Restart)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                text = "Installed: v$currentVersionName • Build $currentVersionCode",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuroraRoyalViolet
                            )
                        }
                    }

                    TextButton(
                        onClick = { showRepoEditor = !showRepoEditor },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (repoSlugInput.isNotBlank()) repoSlugInput else "Set Repo",
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
                            text = "Minimal Internet Access Notice: All your notes, tags, and checklists remain 100% offline on your device. Internet permission is strictly limited to manual update scans when you tap 'Scan for Updates' below to pull the latest Aurora Notes.apk from GitHub Releases.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                if (showRepoEditor) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = repoSlugInput,
                        onValueChange = { repoSlugInput = it },
                        label = { Text("GitHub Repository (owner/Aurora-Notes)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. username/Aurora-Notes", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
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

        Spacer(modifier = Modifier.height(14.dp))

        // 1. App Appearance & Theme Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
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

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Biometric / PIN Lock Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
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
                                onSetBiometricLock(false)
                                Toast.makeText(context, "Biometric Lock Disabled", Toast.LENGTH_SHORT).show()
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

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Custom Category Pins Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = AuroraRoyalViolet,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Custom Category Pins",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Manage custom category tags pinned to your filter bar. Tap X or long-press a category pin to delete it (notes return to All).",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (userTags.isEmpty()) {
                    Text(
                        text = "No custom category pins created yet.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AuroraRoyalViolet.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.35f)),
                                modifier = Modifier.combinedClickable(
                                    onClick = { pendingDeleteCategory = tag },
                                    onLongClick = { pendingDeleteCategory = tag }
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AuroraRoyalViolet
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete $tag",
                                        tint = AuroraRoyalViolet,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { pendingDeleteCategory = tag }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        placeholder = { Text("New category name...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            val clean = newCategoryInput.trim().removePrefix("#").trim()
                            if (clean.isNotBlank() && !clean.equals("All", ignoreCase = true)) {
                                onAddTag(clean)
                                newCategoryInput = ""
                                Toast.makeText(context, "Added category pin #$clean", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Welcome Tour & App Guide Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AuroraRoyalViolet, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Welcome Screen & Guide",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "View the Aurora Notes overview and feature walkthrough.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onOpenWelcomeTour,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                ) {
                    Text("Open Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Local Diagnostic Logs Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
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
                        selected = selectedLevelFilter == LogLevel.SECURITY,
                        onClick = { selectedLevelFilter = LogLevel.SECURITY },
                        label = { Text("SECURITY (${logs.count { it.level == LogLevel.SECURITY }})", fontSize = 11.sp) }
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
                            text = "No diagnostic events captured in current buffer.",
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

        Spacer(modifier = Modifier.height(14.dp))

        // 6. Danger Zone: Wipe All Application Data (Biometric Required)
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

        Spacer(modifier = Modifier.height(96.dp))
    }
}
