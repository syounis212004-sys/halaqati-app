@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.StateNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private fun JSONObject.chatStr(key: String): String = optString(key).takeIf { it != "null" }.orEmpty()

private fun chatKindLabel(kind: String) = when (kind) {
    "direct" -> "محادثة عامة"
    "reward" -> "طلب مكافأة"
    "pledge" -> "كفالة / دعم"
    else -> ""
}

/** RC60: administrator's list of conversations with donors (unread counters) and "new chat" with any donor. */
@Composable
fun DonorChatsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var threads by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var donors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var avatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    var picking by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.chatOverview() } }
                .onSuccess { o ->
                    val t = o.optJSONArray("threads"); val d = o.optJSONArray("donors")
                    threads = (0 until (t?.length() ?: 0)).mapNotNull { t?.optJSONObject(it) }
                    donors = (0 until (d?.length() ?: 0)).mapNotNull { d?.optJSONObject(it) }
                    error = null
                    // RC83: donors' photos (read once per cycle, otherwise from the copy on the device)
                    avatars = withContext(Dispatchers.IO) {
                        runCatching {
                            val due = com.mohammedsamir.halaqati.data.RefreshGate.due("donor-avatars")
                            val rows = AppGraph.repo.table("profiles", "select=id,avatar_path&role=eq.observer&avatar_path=not.is.null&limit=300", forceRefresh = due, preferCache = true)
                            if (due) com.mohammedsamir.halaqati.data.RefreshGate.mark("donor-avatars")
                            (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                                .associate { it.optString("id") to it.optString("avatar_path") }.filterValues { it.startsWith("data:image/") }
                        }.getOrDefault(emptyMap())
                    }
                }
                .onFailure {
                    error = if (it.message.orEmpty().contains("rc54_")) "لم يُطبَّق تحديث قاعدة البيانات الخاص بالمحادثات (RC54) بعد." else (it.message ?: "تعذر تحميل المحادثات")
                }
            loading = false
        }
    }
    LaunchedEffect(pending) { load() }
    // refresh the list after a chat sheet closes (unread counters change)
    LaunchedEffect(open) { if (open == null) load() }

    val cs = MaterialTheme.colorScheme
    val donorTone = com.mohammedsamir.halaqati.ui.theme.EntityColors.of(com.mohammedsamir.halaqati.ui.theme.Entity.DONOR)
    var onlyUnread by remember { mutableStateOf(false) }
    val totalUnread = threads.sumOf { it.optInt("unread") }
    val shown = if (onlyUnread) threads.filter { it.optInt("unread") > 0 } else threads
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(donorTone.container), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Forum, null, tint = donorTone.onContainer)
            }
            Column(Modifier.weight(1f)) {
                Text("المحادثات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(if (totalUnread > 0) "$totalUnread رسالة غير مقروءة" else "لا رسائل جديدة", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("محادثة جديدة")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.mohammedsamir.halaqati.ui.components.ProFilterChip(!onlyUnread, { onlyUnread = false }, "الكل (${threads.size})")
            com.mohammedsamir.halaqati.ui.components.ProFilterChip(onlyUnread, { onlyUnread = true }, "غير مقروءة ($totalUnread)")
        }
        error?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        if (loading) LoadingSkeleton(rows = 4, modifier = Modifier.padding(16.dp))
        if (!loading && error == null && shown.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(donorTone.container), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Forum, null, tint = donorTone.onContainer, modifier = Modifier.size(36.dp))
                }
                Text(if (onlyUnread) "لا توجد رسائل غير مقروءة" else "لا توجد محادثات بعد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(if (onlyUnread) "كل المحادثات مقروءة." else "اضغط «محادثة جديدة» لمراسلة أحد المانحين.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
        ) {
            items(shown, key = { it.chatStr("id") }) { t ->
                val unread = t.optInt("unread")
                val name = t.chatStr("donor_name").ifBlank { "مانح" }
                val kind = t.chatStr("kind")
                val kindIcon = when (kind) { "reward" -> Icons.Default.EmojiEvents; "pledge" -> Icons.Default.Favorite; else -> Icons.Default.Forum }
                val preview = (if (t.chatStr("last_role") == "admin") "أنت: " else "") + t.chatStr("last_body")
                val time = listOf("last_at", "last_message_at", "updated_at").map { t.chatStr(it) }.firstOrNull { it.isNotBlank() }?.take(10).orEmpty()
                Column(Modifier.fillMaxWidth().background(if (unread > 0) cs.primaryContainer.copy(alpha = .35f) else Color.Transparent)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = t.chatStr("id") to "$name · ${chatKindLabel(kind)}" }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(donorTone.container), contentAlignment = Alignment.Center) {
                            Icon(kindIcon, contentDescription = chatKindLabel(kind), tint = donorTone.accent, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (t.chatStr("last_body").isNotBlank()) Text(preview, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else Text(chatKindLabel(kind), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (time.isNotBlank()) Text(time, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            if (unread > 0) Surface(shape = CircleShape, color = cs.primary) {
                                Text("$unread", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = cs.onPrimary, fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 0.5.dp, color = cs.outlineVariant)
                }
            }
        }
    }

    if (picking) {
        HalaqatiAlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("محادثة جديدة مع مانح") },
            text = {
                if (donors.isEmpty()) Text("لا يوجد مانحون مسجَّلون بعد.")
                else LazyColumn(Modifier.heightIn(max = 360.dp), state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(donors, key = { it.chatStr("id") }) { d ->
                        val name = d.chatStr("name").ifBlank { "مانح" }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable {
                                picking = false
                                scope.launch {
                                    com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.chatOpenDirect(d.chatStr("id")) } }
                                        .onSuccess { open = it to "$name · ${chatKindLabel("direct")}" }
                                        .onFailure { error = it.message ?: "تعذر فتح المحادثة" }
                                }
                            }.padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            com.mohammedsamir.halaqati.ui.components.StudentAvatar(null, name, com.mohammedsamir.halaqati.ui.components.NourAvatarSize.Small)
                            Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("إلغاء") } },
        )
    }
    open?.let { (id, title) -> ChatSheet(id, title) { open = null } }
}
