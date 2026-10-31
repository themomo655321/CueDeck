package com.cuedeck.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel {
    INFO, WARN, ERROR
}

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

object ErrorLogger {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private const val MAX_LOGS = 120

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestampMs = 0L, // will be formatted or tagged
            level = level,
            tag = tag,
            message = message,
            stackTrace = throwable?.stackTraceToString()
        )
        _logs.update { list ->
            val updated = list + entry
            if (updated.size > MAX_LOGS) updated.takeLast(MAX_LOGS) else updated
        }
    }

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    fun getLogsFormatted(): String {
        return _logs.value.joinToString("\n") { entry ->
            val levelChar = when (entry.level) {
                LogLevel.INFO -> "I"
                LogLevel.WARN -> "W"
                LogLevel.ERROR -> "E"
            }
            val base = "[$levelChar/${entry.tag}] ${entry.message}"
            if (entry.stackTrace != null) "$base\n${entry.stackTrace}" else base
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
