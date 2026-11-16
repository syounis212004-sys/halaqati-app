package com.mohammedsamir.halaqati.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * RC27.10: one switch for optional animations. When "الحركة: مخففة" is chosen in Settings every optional expand / fade /
 * scale animation in the app is skipped (navigation keeps only a very short fade).
 */
val LocalReducedMotion = compositionLocalOf { false }

/** animateContentSize that does nothing under reduced motion. */
fun Modifier.calmContentSize(): Modifier = composed { if (LocalReducedMotion.current) this else this.animateContentSize() }

@Composable
fun motionEnter(transition: EnterTransition): EnterTransition = if (LocalReducedMotion.current) EnterTransition.None else transition

@Composable
fun motionExit(transition: ExitTransition): ExitTransition = if (LocalReducedMotion.current) ExitTransition.None else transition

/** Duration for tween() calls: zero under reduced motion. */
@Composable
fun motionMillis(millis: Int): Int = if (LocalReducedMotion.current) 0 else millis
