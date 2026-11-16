package com.mohammedsamir.halaqati.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.sin

/**
 * RC86: three soft, overlapping waves along the lower part of a gradient header (like the sea-and-dune bands of a calm
 * illustration). Painted behind content at low alpha so text on top keeps its contrast. Combine with [girih] for the lattice.
 */
fun Modifier.waves(color: Color = Color.White, alpha: Float = .12f): Modifier =
    this.clipToBounds().drawBehind {
        val w = size.width
        val h = size.height
        fun wave(base: Float, amp: Float, phase: Float, a: Float) {
            val p = Path()
            p.moveTo(0f, h)
            p.lineTo(0f, base)
            val steps = 56
            for (i in 0..steps) {
                val x = w * i / steps
                p.lineTo(x, base + amp * sin(i.toFloat() / steps * 2f * PI.toFloat() * 1.5f + phase))
            }
            p.lineTo(w, h)
            p.close()
            drawPath(p, color.copy(alpha = a))
        }
        wave(h * .60f, h * .07f, 0f, alpha)
        wave(h * .76f, h * .06f, 1.9f, alpha * .85f)
        wave(h * .90f, h * .05f, 3.8f, alpha * .7f)
    }
