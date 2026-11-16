package com.mohammedsamir.halaqati.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.dashboard.RolePhrases
import java.time.LocalDate
import java.time.LocalTime

/**
 * RC53: the top card of every home. A verse / hadith / kind word that suits the account type (changes daily),
 * a time-based greeting, and optionally one operational line for the person's role.
 */
@Composable
fun RoleWelcomeCard(role: UserRole, modifier: Modifier = Modifier, detail: String? = null, photo: String? = null, photoName: String = "") {
    val phrase = remember(role) { RolePhrases.forRole(role.name, LocalDate.now().toEpochDay()) }
    val greeting = remember { RolePhrases.greeting(LocalTime.now().hour) }
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(20.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = androidx.compose.ui.graphics.Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = .55f)),
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(greeting, style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .92f))
                Text(phrase.text, style = MaterialTheme.typography.titleMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Text(phrase.source, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .9f))
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (photoName.isNotBlank()) StudentAvatar(photo, photoName, 56.dp)
        }
    }
}
