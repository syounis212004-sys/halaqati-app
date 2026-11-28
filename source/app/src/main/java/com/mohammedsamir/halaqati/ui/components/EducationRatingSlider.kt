package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.sessions.EducationScoreRules
import kotlin.math.roundToInt

/** A discrete native slider; the neutral thumb position is not a recorded score. */
@Composable
fun EducationRatingSlider(label: String, score: Int?, enabled: Boolean, onCommit: (Int) -> Unit, modifier: Modifier = Modifier) {
    var draft by remember(score) { mutableFloatStateOf((score ?: 3).toFloat()) }
    var touched by remember(score) { mutableStateOf(false) }
    val visibleScore = if (touched) draft.roundToInt() else score
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(visibleScore?.let { "$it/5 · ${EducationScoreRules.label(it)}" } ?: "غير مقيّم",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = draft,
            onValueChange = { draft = it; touched = true },
            onValueChangeFinished = { if (touched) onCommit(draft.roundToInt().coerceIn(1,5)) },
            enabled = enabled,
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
