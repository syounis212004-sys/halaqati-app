package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.navigation.AppDestination
import com.mohammedsamir.halaqati.ui.navigation.RoleNavigationPolicy
import com.mohammedsamir.halaqati.ui.theme.cardHairline

private data class Shortcut(val route: String, val label: String, val icon: ImageVector, val grad: GradientPair)

/**
 * RC67: one calm colour. Every tile is a solid emerald disc with a white icon (no random pastel hues), the label sits
 * below, and only the pages the signed-in role can really open are shown.
 */
@Composable
fun HomeShortcuts(showPlans: Boolean, navigate: (String) -> Unit, showAwards: Boolean = false, role: UserRole? = null) {
    fun allowed(d: AppDestination) = role == null || RoleNavigationPolicy.canOpen(role, d)
    val items = buildList {
        add(Shortcut("students", "الطلاب", Icons.Rounded.People, HalaqatiGradients.Emerald))
        if (showPlans) add(Shortcut("plans", "الخطط", Icons.Rounded.Assignment, HalaqatiGradients.Gold))
        add(Shortcut("sessions", "الجلسات", Icons.Rounded.CalendarMonth, HalaqatiGradients.Blue))
        if (allowed(AppDestination.Exams)) add(Shortcut("exams", "الاختبارات", Icons.Rounded.Quiz, HalaqatiGradients.Violet))
        if (allowed(AppDestination.Certificates)) add(Shortcut("certificates", "الشهادات", Icons.Rounded.WorkspacePremium, HalaqatiGradients.Orange))
        add(Shortcut("reports", "التقارير", Icons.Rounded.Assessment, HalaqatiGradients.Teal))
        if (allowed(AppDestination.Insights)) add(Shortcut("insights", "المتابعة", Icons.Rounded.Insights, HalaqatiGradients.Rose))
        add(Shortcut("rankings", "الترتيب", Icons.Rounded.EmojiEvents, HalaqatiGradients.Gold))
        add(Shortcut("quran", "المصحف", Icons.Rounded.MenuBook, HalaqatiGradients.Deep))
        if (allowed(AppDestination.Org)) add(Shortcut("org", "الهيكل", Icons.Rounded.AccountTree, HalaqatiGradients.Deep))
        if (allowed(AppDestination.Team)) add(Shortcut("team", "الكادر", Icons.Rounded.Groups, HalaqatiGradients.Slate))
        if (showAwards) add(Shortcut("awards", "التكريم", Icons.Rounded.Star, HalaqatiGradients.Orange))
        if (showAwards) add(Shortcut("support", "الدعم", Icons.Rounded.Favorite, HalaqatiGradients.Rose))
    }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الوصول السريع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
        // Nour: 4 columns up to 1.3x font scale, 3 columns beyond it so labels never wrap or clip.
        val cols = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 3 else 4
        items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Nour: an incomplete last row is centred instead of leaving one orphan on the reading side.
                val gap = (cols - row.size) / 2f
                if (gap > 0f) Spacer(Modifier.weight(gap))
                row.forEach { item ->
                    Column(
                        Modifier.weight(1f).halaqatiEnter().halaqatiPressable { navigate(item.route) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(shape = RoundedCornerShape(18.dp), color = cs.primaryContainer, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(item.icon, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(item.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
                if (gap > 0f) Spacer(Modifier.weight(gap))
            }
        }
    }
}
