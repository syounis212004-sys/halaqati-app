package com.mohammedsamir.halaqati.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.mohammedsamir.halaqati.ui.theme.Entity
import com.mohammedsamir.halaqati.ui.theme.EntityColors
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.R
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.navigation.AppDestination
import com.mohammedsamir.halaqati.ui.navigation.RoleNavigationPolicy

private val GoldenGreen = Color(0xFF0A4A3F)
private val GoldenGreen2 = Color(com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_PRIMARY)
private val GoldenGreen3 = Color(com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_ACCENT)
private val GoldenMint = Color(0xFFE1F0EA)
private val GoldenLine = Color(0xFFD9E4DD)
private val GoldenMuted = Color(0xFF42564D)


data class GoldenShellActions(
    val roleLabel: String,
    val online: Boolean,
    val pending: Int,
    val openDrawer: () -> Unit,
    val openNotifications: () -> Unit,
    val openSyncCenter: () -> Unit,
)

val LocalGoldenShellActions = staticCompositionLocalOf<GoldenShellActions?> { null }

@DrawableRes
private fun goldenIconRes(key: String): Int = when (key) {
    "home" -> R.drawable.ic_gm_home
    "notifications" -> R.drawable.ic_gm_notifications
    "group", "halaqas" -> R.drawable.ic_gm_halaqas
    "forum", "community" -> R.drawable.ic_gm_community
    "event", "sessions" -> R.drawable.ic_gm_sessions
    "person", "students" -> R.drawable.ic_gm_students
    "timeline", "plans" -> R.drawable.ic_gm_plans
    "award", "certificates" -> R.drawable.ic_gm_certificates
    "report", "reports" -> R.drawable.ic_gm_reports
    "book", "quran" -> R.drawable.ic_gm_quran
    "leaderboard", "ranking", "rankings" -> R.drawable.ic_gm_ranking
    "competition" -> R.drawable.ic_gm_competition
    "importexport", "data" -> R.drawable.ic_gm_data
    "supervisor", "supervision" -> R.drawable.ic_gm_supervision
    "users" -> R.drawable.ic_gm_users
    "profile" -> R.drawable.ic_gm_profile
    "settings" -> R.drawable.ic_gm_settings
    "support" -> R.drawable.ic_gm_support
    "impact" -> R.drawable.ic_gm_impact
    "stats" -> R.drawable.ic_gm_stats
    "more" -> R.drawable.ic_gm_more
    else -> R.drawable.ic_gm_more
}

@Composable
fun GoldenNavIcon(
    key: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(goldenIconRes(key)),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun GoldenBottomBar(
    role: UserRole,
    currentRoute: String,
    drawerOpen: Boolean,
    onNavigate: (AppDestination) -> Unit,
    onMore: () -> Unit,
    onAdd: () -> Unit = {},
    mode: com.mohammedsamir.halaqati.plans.WorkMode = com.mohammedsamir.halaqati.plans.WorkMode.REGULAR,
) {
    val items = RoleNavigationPolicy.bottom(role, mode)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coreRoutes = items.filterNot { it == AppDestination.More || it == AppDestination.Add }.map { it.route }.toSet()
    val normalizedRoute = if (currentRoute == AppDestination.SessionToday.route)
        AppDestination.Sessions.route else currentRoute
    val selectedRoute = if (drawerOpen || normalizedRoute !in coreRoutes)
        AppDestination.More.route else normalizedRoute
    // Nour: a floating capsule with permanent labels under every icon.
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            // Lighter bar: a soft shadow and a hairline border instead of a heavy floating slab.
            shadowElevation = 3.dp,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Recreate item animation state on selection changes in reduced-motion mode.
                key(if (com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion.current) selectedRoute else "navigation") {
                    items.forEach { destination ->
                        val isMore = destination == AppDestination.More
                        if (destination == AppDestination.Add) {
                            // RC40: the smart "＋" — an emerald/gold disc that opens a new session wherever you are.
                            Box(
                                Modifier.padding(horizontal = 4.dp).size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                                    .clickable(role = Role.Button) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onAdd()
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Add, contentDescription = "جلسة جديدة", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp)) }
                            return@forEach
                        }
                        val isSelected = selectedRoute == destination.route
                        val label = if (destination == AppDestination.Community) "الإعلانات" else if (role == UserRole.SUPERVISOR && destination == AppDestination.Halaqas) "الحلقات" else if (role == UserRole.SUPERVISOR && destination == AppDestination.Supervision) "المحفظون" else destination.label
                        CapsuleTab(Modifier.weight(1f), isSelected, label, { if (isMore) onMore() else onNavigate(destination) }) {
                            val filled = when (destination.iconKey) {
                                "home" -> Icons.Default.Home
                                "sessions", "event" -> Icons.Default.CalendarMonth
                                "students", "person" -> Icons.Default.People
                                "plans", "timeline" -> Icons.Default.Assignment
                                "forum" -> Icons.Default.Forum
                                "more" -> Icons.Default.Apps
                                else -> null
                            }
                            if (isSelected && filled != null) Icon(filled, label, Modifier.size(24.dp))
                            else GoldenNavIcon(destination.iconKey, label, Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nour: one tab of the capsule bar. The label is ALWAYS visible under the icon (the selected tab adds a filled pill behind the icon).
 * Label size follows the user's font scale only up to 1.3x so it never wraps or clips on a 360dp phone.
 */
@Composable
private fun CapsuleTab(modifier: Modifier, selected: Boolean, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val reduced = com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion.current
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else Color.Transparent, label = "capsuleTabBg")
    val fg = if (selected) cs.onPrimary else cs.onSurfaceVariant
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelSp = (11f * (minOf(density.fontScale, 1.3f) / density.fontScale)).sp
    Column(
        modifier.heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.height(28.dp).widthIn(min = 52.dp).clip(RoundedCornerShape(50)).background(bg)
                .then(if (reduced) Modifier else Modifier.animateContentSize()),
            contentAlignment = Alignment.Center,
        ) { CompositionLocalProvider(LocalContentColor provides fg) { icon() } }
        Text(
            label, color = if (selected) cs.primary else cs.onSurfaceVariant, maxLines = 1, softWrap = false,
            fontSize = labelSp, lineHeight = (labelSp.value + 3f).sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * RC27.21: the side menu is a calm "more hub" (the pattern Material and NN/g recommend when there are too many pages for the
 * bottom bar): a slim account row, then pages as a 3-column grid of icon tiles under five short headings, then one footer.
 * No pattern or art is drawn behind text, and every tile is a big touch target.
 */
private fun drawerGroupOf(destination: AppDestination): Int = when (destination) {
    AppDestination.Home, AppDestination.Sessions, AppDestination.SessionToday, AppDestination.Students,
    AppDestination.Plans, AppDestination.Halaqas -> 0
    AppDestination.Rankings, AppDestination.Insights, AppDestination.GuardianCompetition, AppDestination.GuardianAchievements,
    AppDestination.Certificates, AppDestination.Awards, AppDestination.Support, AppDestination.DonorAwards, AppDestination.DonorSupport, AppDestination.DonorImpact, AppDestination.DonorStats, AppDestination.Contests, AppDestination.Reports, AppDestination.Supervision, AppDestination.Visits, AppDestination.GuardianProfile -> 1
    AppDestination.Community, AppDestination.Mushaf, AppDestination.Quran, AppDestination.ImportExport -> 2
    AppDestination.Notifications, AppDestination.SyncCenter, AppDestination.Settings -> 4
    else -> 3
}

private val DRAWER_GROUP_TITLES = listOf("إدارة الحلقة", "التقارير والإنجاز", "التواصل والأدوات", "إدارة النظام", "الحساب والإعدادات")
private val DrawerOnline = Color(0xFF2E9E5B)
private val DrawerOffline = Color(0xFFB7791F)

@Composable
fun GoldenDrawerContent(
    profile: Profile,
    currentRoute: String,
    onNavigate: (AppDestination) -> Unit,
    onClose: () -> Unit,
    onLogout: () -> Unit,
) {
    val drawerMode by com.mohammedsamir.halaqati.data.WorkModeHolder.state.collectAsState()
    val drawerEntries = remember(profile.role, drawerMode.mode) { RoleNavigationPolicy.drawerItems(profile.role, drawerMode.mode) }
    val chrome = LocalGoldenShellActions.current
    val online = chrome?.online != false
    val grouped = remember(drawerEntries) {
        drawerEntries.groupBy { drawerGroupOf(it.destination) }.toSortedMap()
    }
    // RC64: search, collapsible groups and a pinned "مفضلتي" block, because the administrator's menu has 20+ pages.
    val drawerContext = LocalContext.current
    val drawerPrefs = remember { drawerContext.getSharedPreferences("halaqati_drawer", android.content.Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var favorites by remember(profile.id) { mutableStateOf(drawerPrefs.getStringSet("fav_${profile.id}", emptySet())?.toSet().orEmpty()) }
    var collapsed by remember { mutableStateOf(setOf<Int>()) }
    fun toggleFavorite(route: String) {
        favorites = if (route in favorites) favorites - route else favorites + route
        drawerPrefs.edit().putStringSet("fav_${profile.id}", favorites).apply()
    }
    // RC27.29: full-screen menu on the app background (true black in dark mode), no bands or art behind text.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ---- Account header: art tile, name, role + connection dot, close.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StudentAvatar(profile.avatarPath.takeIf { it.isNotBlank() && it != "null" }, profile.fullName, 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    profile.fullName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        roleTitle(profile.role),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (online) DrawerOnline else DrawerOffline),
                    )
                    Text(
                        if (online) "متصل" else "دون اتصال",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق القائمة", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // The body owns the scroll; the footer stays reachable at all times.
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "drawer-search") {
                CompactSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "ابحث عن صفحة",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            val q = query.trim()
            if (q.isNotEmpty()) {
                val hits = drawerEntries.filter { it.label.contains(q) }
                if (hits.isEmpty()) item(key = "drawer-none") {
                    Text("لا توجد صفحة بهذا الاسم", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                if (hits.isNotEmpty()) item(key = "drawer-hits") {
                    DrawerGroupBox(hits.map { it.destination to it.label }) { d, l ->
                        DrawerRow(d, l, currentRoute, chrome?.pending ?: 0, online, favorites, ::toggleFavorite, onNavigate)
                    }
                }
            } else {
                val favEntries = drawerEntries.filter { it.destination.route in favorites }
                if (favEntries.isNotEmpty()) {
                    item(key = "title-fav") {
                        Text("مفضلتي", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp))
                    }
                    item(key = "drawer-favs") {
                        DrawerGroupBox(favEntries.map { it.destination to it.label }) { d, l ->
                            DrawerRow(d, l, currentRoute, chrome?.pending ?: 0, online, favorites, ::toggleFavorite, onNavigate)
                        }
                    }
                }
                grouped.forEach { (group, entries) ->
                    val isCollapsed = group in collapsed && entries.none { it.destination.route == currentRoute }
                    item(key = "title-$group") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { collapsed = if (group in collapsed) collapsed - group else collapsed + group }
                                .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                DRAWER_GROUP_TITLES[group] + " (${entries.size})",
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = if (isCollapsed) "توسيع" else "طي", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!isCollapsed) item(key = "group-$group") {
                        DrawerGroupBox(entries.map { it.destination to it.label }) { d, l ->
                            DrawerRow(d, l, currentRoute, chrome?.pending ?: 0, online, favorites, ::toggleFavorite, onNavigate)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الخروج", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "صُنع بأيدٍ شرقية",
                color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Designed by : Dr. Mohammed Samir Abu Younis",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** One drawer group: a 16dp rounded container with an outlineVariant border and thin dividers (inset past the icon) between rows. */
@Composable
private fun DrawerGroupBox(rows: List<Pair<AppDestination, String>>, row: @Composable (AppDestination, String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        rows.forEachIndexed { i, (d, l) ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            row(d, l)
        }
    }
}

@Composable
private fun DrawerRow(
    destination: AppDestination,
    label: String,
    currentRoute: String,
    pending: Int,
    online: Boolean,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onNavigate: (AppDestination) -> Unit,
) {
    val active = currentRoute == destination.route ||
        (currentRoute == AppDestination.SessionToday.route && destination == AppDestination.Sessions)
    val badge = when (destination) {
        AppDestination.Notifications, AppDestination.SyncCenter -> pending
        else -> 0
    }
    val syncing = destination == AppDestination.SyncCenter && online && pending > 0
    DrawerTile(
        label = if (destination == AppDestination.SyncCenter && !online) "دون اتصال" else label,
        iconKey = destination.iconKey,
        active = active,
        badge = badge,
        spinning = syncing,
        offline = destination == AppDestination.SyncCenter && !online,
        favorite = destination.route in favorites,
        onToggleFavorite = { onToggleFavorite(destination.route) },
        onClick = { onNavigate(destination) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One full-width menu row: icon square, label, badge. The active page is a soft primaryContainer pill. */
@Composable
private fun DrawerTile(
    label: String,
    iconKey: String,
    active: Boolean,
    badge: Int,
    spinning: Boolean,
    offline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.halaqatiPressable { onClick() },
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
      Box(Modifier.fillMaxWidth()) {
        if (active) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(28.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val entityTone = when (iconKey) {
                "group" -> EntityColors.of(Entity.HALAQA)
                "students" -> EntityColors.of(Entity.STUDENT)
                "forum" -> EntityColors.of(Entity.ANNOUNCEMENT)
                "supervisor" -> EntityColors.of(Entity.SUPERVISOR)
                "support" -> EntityColors.of(Entity.DONOR)
                else -> null
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(entityTone?.container ?: if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val tint = entityTone?.accent ?: if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                when {
                    offline -> Icon(Icons.Default.CloudOff, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    iconKey == "sync" -> Icon(Icons.Default.Sync, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp).halaqatiSpin(spinning))
                    else -> GoldenNavIcon(iconKey, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge > 0) {
                Badge { Text(badge.coerceAtMost(99).toString()) }
            }
            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
      }
    }
}

fun roleTitle(role: UserRole): String = when (role) {
    UserRole.ADMIN -> if (PlatformOwnerState.isOwner) "مسؤول النظام" else "مسؤول الجمعية"
    UserRole.SUPERVISOR -> "مشرف الحلقات"
    UserRole.TEACHER -> "محفّظ الحلقة"
    UserRole.GUARDIAN -> "ولي الأمر"
    UserRole.OBSERVER -> "المانح"
    else -> "المستخدم"
}
