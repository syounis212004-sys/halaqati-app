@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.components

import com.mohammedsamir.halaqati.ui.theme.motionEnter
import com.mohammedsamir.halaqati.ui.theme.motionExit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion

/**
 * Shared chrome for the RC17 redesign. Existing screen contracts remain unchanged;
 * the optional kicker lets role-specific screens reproduce the supplied prototype.
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    pending: Int = 0,
    kicker: String? = null,
) {
    val shell = LocalGoldenShellActions.current
    var utilitiesOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .padding(horizontal = HalaqatiUiTokens.ScreenHorizontal, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                (kicker ?: shell?.roleLabel)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // RC90: the title may take two lines so long page names are never cut on a 360dp screen.
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            shell?.let { chrome ->
                IconButton(onClick = chrome.openNotifications, modifier = Modifier.size(48.dp)) {
                    GoldenNavIcon("notifications", "الإشعارات", Modifier.size(24.dp))
                }
            }
            if (onRefresh != null || shell != null) Box {
                IconButton(onClick = { utilitiesOpen = true }, modifier = Modifier.size(48.dp)) {
                    BadgedBox(badge = {
                        val count = maxOf(pending, shell?.pending ?: 0)
                        if (count > 0) Badge { Text(count.coerceAtMost(99).toString()) }
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات الصفحة والمزامنة")
                    }
                }
                DropdownMenu(expanded = utilitiesOpen, onDismissRequest = { utilitiesOpen = false }) {
                    onRefresh?.let { refresh ->
                        DropdownMenuItem(text = { Text("تحديث الصفحة") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { utilitiesOpen = false; refresh() })
                    }
                    shell?.let { chrome ->
                        DropdownMenuItem(
                            text = { Text(when {
                                !chrome.online -> "دون اتصال · التغييرات محفوظة"
                                chrome.pending > 0 -> "المزامنة · ${chrome.pending} عمليات معلّقة"
                                else -> "حالة المزامنة"
                            }) },
                            leadingIcon = { Icon(if (chrome.online) Icons.Default.Sync else Icons.Default.CloudOff, null) },
                            onClick = { utilitiesOpen = false; chrome.openSyncCenter() })
                    }
                }
            }
            if (onBack != null || shell != null) IconButton(
                onClick = onBack ?: shell?.openDrawer ?: {}, modifier = Modifier.size(48.dp),
            ) {
                Icon(if (onBack != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                    contentDescription = if (onBack != null) "رجوع" else "القائمة")
            }
        }
    }
    LatticeStrip()
}

@Composable
fun GoldenFeatureHero(
    title: String,
    subtitle: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** Shows the animated art of this account type (mosque, rehal, sprout...) in place of the icon. */
    role: com.mohammedsamir.halaqati.model.UserRole? = null,
    /** Any other animation (for example the trophy) in place of the icon; [role] wins when both are given. */
    art: HalaqatiAnimation? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().halaqatiEnter(),
        shape = RoundedCornerShape(HalaqatiUiTokens.RadiusHero),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(Modifier.fillMaxWidth().heroBackground(heroBrush(MaterialTheme.colorScheme.primary))) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(
                            title,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            subtitle,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (role != null) {
                        Spacer(Modifier.width(10.dp))
                        GoldenNavIcon("quran", null, Modifier.size(34.dp), MaterialTheme.colorScheme.onPrimary.copy(alpha = .85f))
                    } else if (art != null) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    } else icon?.let {
                        Spacer(Modifier.width(10.dp))
                        Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f), modifier = Modifier.size(28.dp))
                    }
                    badge?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .10f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = .14f)),
                        ) {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String = "",
    onClick: (() -> Unit)? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .halaqatiEnter()
            .then(if (onClick != null) Modifier.halaqatiPressable { onClick() } else Modifier),
        shape = RoundedCornerShape(HalaqatiUiTokens.RadiusMetric),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.5.dp,
        border = com.mohammedsamir.halaqati.ui.theme.cardHairline(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (icon != null) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(7.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(animatedCountText(value), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SyncBanner(
    online: Boolean,
    pending: Int,
    blocked: Int = 0,
    error: String? = null,
    onSync: () -> Unit,
    onDetails: (() -> Unit)? = null,
    stalePendingMinutes: Int = 0,
) {
    // RC53: one slim line (about 26dp). Offline is shown here again, with the short instruction the user asked for.
    val hostError = error != null && (error.contains("resolve host", true) || error.contains("hostname", true))
    val stale = pending > 0 && stalePendingMinutes >= 3
    val realError = error != null && !hostError
    val showIt = !online || blocked > 0 || stale || realError
    AnimatedVisibility(
        visible = showIt,
        enter = motionEnter(fadeIn() + expandVertically()),
        exit = motionExit(fadeOut() + shrinkVertically()),
    ) {
        val bad = realError || blocked > 0
        val container = if (bad) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
        val content = if (bad) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
        val text = when {
            realError -> error ?: ""
            blocked > 0 -> "$blocked عمليات تحتاج مراجعة"
            !online && pending > 0 -> "لا يوجد اتصال، $pending تغييرات محفوظة هنا. لا تسجّل خروجًا، وستُرسل عند عودة النت"
            !online -> "لا يوجد اتصال حاليًا، لا تسجّل خروجًا، وسيُرسل كل شيء عند عودة النت"
            stale -> "$pending تغييرات لم تُرسل منذ $stalePendingMinutes دقيقة. لا تسجّل خروجًا"
            else -> ""
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp),
            color = container,
            contentColor = content,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when {
                        bad -> Icons.Default.ErrorOutline
                        online -> Icons.Default.Sync
                        else -> Icons.Default.CloudOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onDetails != null && blocked > 0) {
                    Text(
                        "التفاصيل",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDetails() }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (online && pending > 0) {
                    Text(
                        "مزامنة",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSync() }.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** RC27.15: picks the empty-state picture from what is missing (notifications, people, results, documents). */
internal fun emptyAnimationFor(text: String): HalaqatiAnimation = when {
    text.contains("إشعار") -> HalaqatiAnimation.EmptyBell
    text.contains("منافس") || text.contains("ترتيب") || text.contains("إنجاز") -> HalaqatiAnimation.Trophy
    text.contains("بحث") || text.contains("مطابق") || text.contains("نتائج") -> HalaqatiAnimation.EmptySearch
    text.contains("شهاد") || text.contains("تقرير") || text.contains("سجل") || text.contains("وثيق") -> HalaqatiAnimation.EmptyDoc
    text.contains("طالب") || text.contains("طلاب") || text.contains("حلق") || text.contains("مستخدم") ||
        text.contains("ولي") || text.contains("حساب") || text.contains("ملفات") -> HalaqatiAnimation.EmptyPeople
    else -> HalaqatiAnimation.Empty
}

@Composable
fun EmptyState(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    /** Overrides the animation that is otherwise picked from the words of [text]. */
    kind: HalaqatiAnimation? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().halaqatiEnter(),
        shape = RoundedCornerShape(HalaqatiUiTokens.RadiusLarge),
        color = MaterialTheme.colorScheme.surface,
        border = com.mohammedsamir.halaqati.ui.theme.cardHairline(),
    ) {
        Column(
            Modifier.fillMaxWidth().nourPattern(PatternLevel.Subtle, MaterialTheme.colorScheme.primary)
                .padding(horizontal = 20.dp, vertical = if (animated) 14.dp else 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (animated) {
                // RC27.12: the open seat in the halaqa (halaqati_empty.json).
                HalaqatiMotion(kind ?: emptyAnimationFor(text), Modifier.size(104.dp))
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp).size(28.dp),
                    )
                }
            }
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun ProFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (() -> Unit))? = null,
    showCheckIcon: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon ?: if (selected && showCheckIcon) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp)) }
        } else null,
        modifier = modifier.heightIn(min = 48.dp).let { if (selected) it.nourRaised(RoundedCornerShape(999.dp), depth = NourDepth.Card, fill = false) else it },
        shape = RoundedCornerShape(999.dp),
        // RC38: the choice you made must be unmistakable in both themes: a clear outline when not chosen, a thick brand
        // border plus a visibly stronger fill when chosen (the old chip used nearly the same colour for both states).
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun StatusPill(text: String, kind: String = "info", modifier: Modifier = Modifier) {
    // Nour: soft container + its own on-colour (readable pair); the text always says the status, colour only supports it.
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        "success" -> cs.primaryContainer to cs.onPrimaryContainer
        "warning" -> cs.tertiaryContainer to cs.onTertiaryContainer
        "error", "danger" -> cs.errorContainer to cs.onErrorContainer
        "neutral" -> cs.surfaceVariant to cs.onSurfaceVariant
        else -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    Surface(modifier = modifier, color = bg, contentColor = fg, shape = RoundedCornerShape(10.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LoadingSkeleton(rows: Int = 3, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse = transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-pulse",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // RC27.15: the olive loader above the placeholders (skipped for the one-row strips).
        if (rows >= 2) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HalaqatiLoader(size = 56.dp) }
        }
        repeat(rows) { index ->
            Surface(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .graphicsLayer { alpha = if (reduced) 1f else pulse.value },
                shape = RoundedCornerShape(HalaqatiUiTokens.RadiusLarge),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(if (index % 2 == 0) .58f else .72f).height(15.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)))
                    Box(Modifier.fillMaxWidth(.9f).height(11.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .8f), RoundedCornerShape(8.dp)))
                    Box(Modifier.fillMaxWidth(.68f).height(11.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), RoundedCornerShape(8.dp)))
                }
            }
        }
    }
}

@Composable
fun StateNotice(text: String, kind: String = "info", modifier: Modifier = Modifier) {
    // Nour: a soft container with a small icon (status is never colour alone) instead of a solid saturated bar.
    val tone = when (kind) { "success" -> "success"; "warning" -> "warning"; "error", "danger" -> "danger"; else -> "neutral" }
    val (accent, container, onContainer) = nourTone(tone)
    val icon = when (tone) {
        "success" -> androidx.compose.material.icons.Icons.Default.CheckCircle
        "warning" -> androidx.compose.material.icons.Icons.Default.Warning
        "danger" -> androidx.compose.material.icons.Icons.Default.ErrorOutline
        else -> androidx.compose.material.icons.Icons.Default.Info
    }
    Surface(modifier = modifier.fillMaxWidth().halaqatiEnter(), color = if (tone == "neutral") MaterialTheme.colorScheme.surfaceVariant else container, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.nourPattern(PatternLevel.Subtle, accent).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = if (tone == "danger") onContainer else accent, modifier = Modifier.size(NourIcon.Inline))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (tone == "neutral") MaterialTheme.colorScheme.onSurface else onContainer)
        }
    }
}

/**
 * RC24.3 compact single-line search field: 48dp tall, short placeholder (never wraps),
 * optional trailing action (e.g. a filter button) inside the same row.
 */
@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "مسح البحث", modifier = Modifier.size(18.dp))
                }
            }
            trailing?.invoke()
        }
    }
}
