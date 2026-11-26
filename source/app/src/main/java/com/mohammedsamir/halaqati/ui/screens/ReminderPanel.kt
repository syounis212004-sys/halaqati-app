@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mohammedsamir.halaqati.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mohammedsamir.halaqati.push.NotificationHelper
import com.mohammedsamir.halaqati.reminders.Reminder
import com.mohammedsamir.halaqati.reminders.ReminderPrefs
import com.mohammedsamir.halaqati.reminders.ReminderScheduler
import com.mohammedsamir.halaqati.reminders.ReminderTexts
import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.StateNotice
import java.time.DayOfWeek
import java.time.ZonedDateTime

/** Arabic week order, Saturday first. */
internal val reminderWeek = listOf(
    DayOfWeek.SATURDAY to "السبت", DayOfWeek.SUNDAY to "الأحد", DayOfWeek.MONDAY to "الاثنين", DayOfWeek.TUESDAY to "الثلاثاء",
    DayOfWeek.WEDNESDAY to "الأربعاء", DayOfWeek.THURSDAY to "الخميس", DayOfWeek.FRIDAY to "الجمعة",
)

internal fun dayName(d: DayOfWeek) = reminderWeek.first { it.first == d }.second

/** 18:05 -> "٦:٠٥ م". */
internal fun timeLabel(hour: Int, minute: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "${digits(h12)}:${digits("%02d".format(minute))} ${if (hour < 12) "ص" else "م"}"
}

/**
 * The reminder switch, days and time. Everything is kept on this device ([ReminderPrefs]) and scheduled on it ([ReminderScheduler]); nothing is read
 * from the server. Turning it on asks for the notification permission (Android 13+) with an explanation first.
 */
@Composable
fun ReminderPanel(role: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var r by remember { mutableStateOf(ReminderPrefs.load(ctx)) }
    var showTime by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    fun permissionMissing() = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    var missing by remember { mutableStateOf(permissionMissing()) }
    fun commit(n: Reminder) { r = n; ReminderPrefs.save(ctx, n); ReminderScheduler.apply(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        missing = permissionMissing(); refused = !ok
        if (ok) commit(r.copy(enabled = true))
    }
    val next = ReminderScheduler.nextAt(ZonedDateTime.now(), r)

    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تذكير من التطبيق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        ReminderTexts.forRole(role)?.second ?: "لا يوجد تذكير لهذا الحساب",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = r.enabled,
                    onCheckedChange = { on -> if (on && permissionMissing()) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else commit(r.copy(enabled = on)) },
                )
            }
            if (r.enabled && missing) {
                StateNotice(
                    if (refused) "الإشعارات مغلقة لهذا التطبيق. فعّلها من إعدادات الهاتف ليصلك التذكير." else "اسمح بالإشعارات ليصلك التذكير في وقته.",
                    "warning",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!refused) Button(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("السماح بالإشعارات") }
                    OutlinedButton(
                        onClick = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("إعدادات الهاتف") }
                }
            }
            if (r.enabled) {
                Text("أيام التذكير", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reminderWeek.forEach { (d, name) ->
                        ProFilterChip(
                            selected = d in r.days,
                            onClick = { commit(r.copy(days = if (d in r.days) r.days - d else r.days + d)) },
                            label = name, showCheckIcon = false,
                        )
                    }
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("الساعة ${timeLabel(r.hour, r.minute)}") }
                Text(
                    when {
                        r.days.isEmpty() -> "اختر يومًا واحدًا على الأقل."
                        next != null -> "التذكير القادم: ${dayName(next.dayOfWeek)} ${timeLabel(next.hour, next.minute)}. الوقت تقريبي وقد يتأخر بضع دقائق."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { ReminderTexts.forRole(role)?.let { NotificationHelper.showReminder(ctx, it.first, it.second) } },
                    enabled = !missing,
                ) { Text("إرسال تذكير تجريبي الآن") }
            } else {
                Text("التذكير مُطفأ. لن يصلك أي إشعار منه.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = r.hour, initialMinute = r.minute, is24Hour = false)
        HalaqatiAlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("وقت التذكير") },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { commit(r.copy(hour = state.hour, minute = state.minute)); showTime = false }) { Text("تأكيد") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } },
        )
    }
}
