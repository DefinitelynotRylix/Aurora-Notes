package com.aurora.notes.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    ERROR,
    WARN,
    SECURITY
}

data class LogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: String,
    val message: String
)

object LocalLogger {
    private const val MAX_ENTRIES = 250
    private const val TAG = "AURORA_DIAGNOSTICS"

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var isLoggingActive = true

    fun setLoggingActive(active: Boolean) {
        isLoggingActive = active
    }

    fun isLoggingActive(): Boolean = isLoggingActive

    fun error(category: String, message: String) {
        record(LogLevel.ERROR, category, message)
        Log.e(TAG, "[$category] $message")
    }

    fun warn(category: String, message: String) {
        record(LogLevel.WARN, category, message)
        Log.w(TAG, "[$category] $message")
    }

    fun security(category: String, message: String) {
        // Restricted: routine informational/security actions are not shown in diagnostic logs;
        // only errors and warnings are recorded when one occurs.
    }

    private fun record(level: LogLevel, category: String, message: String) {
        if (!isLoggingActive) return
        if (level != LogLevel.ERROR && level != LogLevel.WARN) return

        val entry = LogEntry(
            level = level,
            category = category,
            message = message
        )

        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > MAX_ENTRIES) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun exportToPlainText(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val sb = StringBuilder()
        sb.append("================================================================================\n")
        sb.append("AURORA NOTES LOCAL DIAGNOSTIC LOG DUMP\n")
        sb.append("Generated At: ").append(dateFormat.format(Date())).append("\n")
        sb.append("Total Entries: ").append(_logs.value.size).append("\n")
        sb.append("Privacy Policy: Zero-Telemetry / 100% Local Device Storage Only\n")
        sb.append("================================================================================\n\n")

        for (log in _logs.value.reversed()) {
            sb.append("[").append(dateFormat.format(Date(log.timestamp))).append("] ")
                .append("[").append(log.level.name).append("] ")
                .append("[").append(log.category).append("] ")
                .append(log.message).append("\n")
        }

        return sb.toString()
    }
}
