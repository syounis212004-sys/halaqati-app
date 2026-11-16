package com.mohammedsamir.halaqati.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * RC27.15: small shared motion helpers used across the app. All of them respect "الحركة: مخففة"
 * ([LocalReducedMotion]) and never change layout size, so they are safe to add to any card or row.
 */

/**
 * Hands out a growing delay to elements that appear in the same instant, so a screenful of cards rises one after
 * another (a soft cascade) without anyone passing an index. Calls more than 90 ms apart start a new cascade.
 */
internal object HalaqatiEnterClock {
    private var last = 0L
    private var step = 0

    @Volatile private var openedAt = 0L

    /** Called when a destination opens: only elements that appear in the next moments animate in. */
    fun screenOpened() { openedAt = SystemClock.uptimeMillis() }

    /** Nour: items that scroll into view later, or appear after data loads, show instantly (no replay while scrolling). */
    fun recentlyOpened(): Boolean = SystemClock.uptimeMillis() - openedAt <= 600L

    fun nextDelayMillis(): Long {
        val now = SystemClock.uptimeMillis()
        step = if (now - last > 90L) 0 else minOf(step + 1, 8)
        last = now
        return step * 20L
    }
}

/** Fade + short rise on first composition. Elements that appear together cascade (see [HalaqatiEnterClock]). */
@Composable
fun Modifier.halaqatiEnter(): Modifier {
    val reduced = LocalReducedMotion.current
    val animate = remember { !reduced && HalaqatiEnterClock.recentlyOpened() }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val startDelay = remember { if (animate) HalaqatiEnterClock.nextDelayMillis() else 0L }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(startDelay)
            progress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 8.dp.toPx()
    }
}

/** Counts a whole number up from 0 the first time it is shown, and between values when it changes. Other text is returned as is. */
@Composable
fun animatedCountText(value: String): String {
    // Operational numbers must be readable immediately, including after refresh and navigation.
    return value
}

/** RC27.16: clickable with a soft press-in (scale .97) so cards feel physical. Same hit area, no layout change. */
@Composable
fun Modifier.halaqatiPressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val reduced = LocalReducedMotion.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed.value && !reduced) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "press-scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** One gentle swing (like a bell) every time [trigger] changes to a positive value. */
@Composable
fun Modifier.halaqatiRing(trigger: Int): Modifier {
    val reduced = LocalReducedMotion.current
    val angle = remember { Animatable(0f) }
    LaunchedEffect(trigger, reduced) {
        if (trigger > 0 && !reduced) {
            for (target in floatArrayOf(14f, -11f, 7f, -4f, 0f)) angle.animateTo(target, tween(110, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        rotationZ = angle.value
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.1f)
    }
}

/** Slow continuous turn while [active] (the sync icon when work is waiting to sync). Stops in place otherwise. */
@Composable
fun Modifier.halaqatiSpin(active: Boolean): Modifier {
    val reduced = LocalReducedMotion.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(2400, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "spin-angle",
    )
    return this.graphicsLayer { rotationZ = if (active && !reduced) angle.value else 0f }
}
