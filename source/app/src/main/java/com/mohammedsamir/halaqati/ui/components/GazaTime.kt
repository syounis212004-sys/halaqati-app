package com.mohammedsamir.halaqati.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mohammedsamir.halaqati.model.UserRole
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * RC62: every date/time the person reads is shown on Gaza time (Asia/Gaza), 12-hour clock, Latin digits.
 * Storage stays UTC; only the display layer converts.
 */
object GazaTime {
    val zone: ZoneId = ZoneId.of("Asia/Gaza")

    private fun clock(z: ZonedDateTime): String {
        val h = z.hour % 12
        return String.format(Locale.US, "%02d:%02d %s", if (h == 0) 12 else h, z.minute, if (z.hour < 12) "ص" else "م")
    }

    private fun zoned(iso: String): ZonedDateTime? =
        runCatching { OffsetDateTime.parse(iso.trim()).atZoneSameInstant(zone) }.getOrNull()

    /** 2026-11-13 10:20 ص */
    fun dateTime(iso: String): String = zoned(iso)?.let { String.format(Locale.US, "%04d-%02d-%02d %s", it.year, it.monthValue, it.dayOfMonth, clock(it)) } ?: iso.take(16).replace('T', ' ')

    /** 13/11 10:20 ص */
    fun shortDateTime(iso: String): String = zoned(iso)?.let { String.format(Locale.US, "%d/%d %s", it.dayOfMonth, it.monthValue, clock(it)) } ?: iso.take(10)

    /** 2026-11-13 from a date or ISO timestamp. */
    fun dayOnly(iso: String): String = if (iso.length <= 10) iso else (zoned(iso)?.let { String.format(Locale.US, "%04d-%02d-%02d", it.year, it.monthValue, it.dayOfMonth) } ?: iso.take(10))

    fun fromMillis(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).let { String.format(Locale.US, "%04d-%02d-%02d %s", it.year, it.monthValue, it.dayOfMonth, clock(it)) }
}

/** True for the single platform owner (profiles.is_platform_owner): he sits above every association, so he is "مسؤول النظام", not an association admin. */
object PlatformOwnerState { var isOwner by mutableStateOf(false) }

/** Arabic name of an account role (never show the enum name such as TEACHER). */
fun roleLabelArabic(role: UserRole): String = when (role) {
    UserRole.ADMIN -> if (PlatformOwnerState.isOwner) "مسؤول النظام" else "مسؤول الجمعية"
    UserRole.SUPERVISOR -> "مشرف"
    UserRole.TEACHER -> "محفّظ"
    UserRole.GUARDIAN -> "ولي أمر"
    UserRole.STUDENT -> "طالب"
    UserRole.OBSERVER -> "مانح"
    else -> "مستخدم"
}
