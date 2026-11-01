package com.cuedeck.util

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

fun padTwoDigits(n: Long): String = if (n in 0..9) "0$n" else "$n"

fun formatTime(totalSec: Long): String {
    val m = totalSec / 60
    val s = abs(totalSec % 60)
    return "$m:${padTwoDigits(s)}"
}

fun formatCountdown(remSec: Long): String {
    return "-${formatTime(remSec)}"
}

fun formatOneDecimal(v: Float): String {
    val scaled = (v * 10).roundToInt()
    val intPart = scaled / 10
    val decPart = abs(scaled % 10)
    return "$intPart.$decPart"
}

fun formatTwoDecimals(v: Float): String {
    val scaled = (v * 100).roundToInt()
    val intPart = scaled / 100
    val decPart = abs(scaled % 100)
    return "$intPart.${padTwoDigits(decPart.toLong())}"
}

fun formatDecibels(vol: Float): String {
    if (vol <= 0.001f) return "-inf dB"
    val db = 20 * log10(vol)
    return "${formatOneDecimal(db)} dB"
}

fun formatDurationPrecise(totalMs: Long): String {
    val totalSec = totalMs / 1000
    val min = totalSec / 60
    val s = totalSec % 60
    val hundredths = (totalMs % 1000) / 10
    return if (min > 0) {
        "$min:${padTwoDigits(s)}.${padTwoDigits(hundredths)}"
    } else {
        "$s.${padTwoDigits(hundredths)}"
    }
}

fun randomUUID(): String {
    val chars = "0123456789abcdef"
    val sb = StringBuilder()
    for (i in 0 until 32) {
        if (i == 8 || i == 12 || i == 16 || i == 20) sb.append('-')
        val r = kotlin.random.Random.nextInt(16)
        sb.append(chars[r])
    }
    return sb.toString()
}
