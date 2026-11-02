package com.aurora.notes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.LocalLogger
import com.aurora.notes.util.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    isBiometricEnabled: Boolean = false,
    onSetBiometricLock: (Boolean) -> Unit = {},
    onOpenWelcomeTour: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onExportLogs: () -> String = { "" },
    onTriggerBiometricAuth: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by LocalLogger.logs.collectAsState()
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, selectedLevelFilter) {
        if (selectedLevelFilter == null) logs else logs.filter { it.level == selectedLevelFilter }
    }

    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Page Header with Description
        Text(
            text = "Settings & Preferences",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Manage native biometric security, welcome guide, and local diagnostic logs.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        // 1. Biometric / PIN Lock Card
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

        // 2. Welcome Tour & App Guide Card
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

        // 3. Local Diagnostic Logs Card
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
                        .height(200.dp)
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

        Spacer(modifier = Modifier.height(90.dp))
    }
}
