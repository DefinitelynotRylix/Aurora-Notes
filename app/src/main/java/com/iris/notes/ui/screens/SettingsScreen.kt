package com.iris.notes.ui.screens

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
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
import com.iris.notes.ui.theme.IrisDangerRed
import com.iris.notes.ui.theme.IrisRoyalViolet
import com.iris.notes.util.LocalLogger
import com.iris.notes.util.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onClearLogs: () -> Unit,
    onExportLogs: () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by LocalLogger.logs.collectAsState()
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) } // null = ALL

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

        // Security Architecture Overview Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = IrisRoyalViolet, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zero-Knowledge Architecture",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Local-Only Storage: All notes are stored inside local SQLite via Room.\n" +
                            "• AES-256-GCM authenticated encryption.\n" +
                            "• PBKDF2-SHA256 key derivation with 100,000 rounds.\n" +
                            "• Zero remote network telemetry or outside analytics.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 18.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Local Diagnostic Logs Card
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
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = IrisRoyalViolet, modifier = Modifier.size(20.dp))
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

                // Filter Chips
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

                // Terminal Display Box
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

                // Action Buttons: Copy, Clear, Export (.txt)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val dump = onExportLogs()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Iris Diagnostic Logs", dump)
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IrisDangerRed),
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
                            val clip = ClipData.newPlainText("iris_diagnostic_logs.txt", dump)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Exported iris_diagnostic_logs.txt to clipboard", Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IrisRoyalViolet),
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
