package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Sura-name cartouche drawn in code (original geometry, no third-party artwork): a double rounded frame, an eight-petal rosette medallion at each end
 * and a row of small diamonds along the top and bottom rails. The research for ready-made files found nothing with a confirmed CC0 licence for a surah
 * banner, so none is used; everything here is plain vector drawing in the theme colours.
 */
internal fun DrawScope.drawSuraOrnament(top: Float, height: Float, pal: MushafPalette) {
    val left = size.width * 0.015f; val width = size.width * 0.97f; val h = height
    val tl = Offset(left, top); val sz = Size(width, h)
    val outerR = CornerRadius(h * 0.30f)
    drawRoundRect(pal.rangeBg, tl, sz, outerR)
    val w1 = max(2f, h * 0.05f)
    drawRoundRect(pal.rule, tl, sz, outerR, style = Stroke(w1))
    val inset = h * 0.12f
    drawRoundRect(pal.frame.copy(alpha = 0.55f), Offset(left + inset, top + inset), Size(width - 2 * inset, h - 2 * inset), CornerRadius(h * 0.20f), style = Stroke(max(1f, h * 0.022f)))

    // medallions at both ends
    val r = h * 0.34f
    for (cx in listOf(left + h * 0.62f, left + width - h * 0.62f)) medallion(Offset(cx, top + h / 2f), r, pal)

    // small diamonds along the rails between the medallions and the name area
    val d = h * 0.055f
    val railTop = top + inset + h * 0.075f; val railBottom = top + h - inset - h * 0.075f
    val startX = left + h * 1.32f; val endX = left + width - h * 1.32f
    val step = h * 0.30f
    var x = startX
    while (x <= endX) {
        val inName = x > left + width * 0.34f && x < left + width * 0.66f
        if (!inName) { diamond(Offset(x, railTop), d, pal.rule.copy(alpha = 0.8f)); diamond(Offset(x, railBottom), d, pal.rule.copy(alpha = 0.8f)) }
        x += step
    }
}

private fun DrawScope.diamond(c: Offset, r: Float, color: androidx.compose.ui.graphics.Color) {
    val p = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
    drawPath(p, color)
}

private fun DrawScope.medallion(c: Offset, r: Float, pal: MushafPalette) {
    drawCircle(pal.pageBg, r, c)
    drawCircle(pal.rule, r, c, style = Stroke(max(2f, r * 0.12f)))
    drawCircle(pal.frame.copy(alpha = 0.6f), r * 0.82f, c, style = Stroke(max(1f, r * 0.05f)))
    val petal = Path().apply {
        moveTo(c.x, c.y)
        cubicTo(c.x + r * 0.30f, c.y - r * 0.22f, c.x + r * 0.30f, c.y - r * 0.62f, c.x, c.y - r * 0.74f)
        cubicTo(c.x - r * 0.30f, c.y - r * 0.62f, c.x - r * 0.30f, c.y - r * 0.22f, c.x, c.y)
        close()
    }
    for (i in 0 until 8) rotate(degrees = i * 45f, pivot = c) {
        drawPath(petal, pal.frame.copy(alpha = if (i % 2 == 0) 0.85f else 0.5f))
    }
    drawCircle(pal.rule, r * 0.12f, c)
    // eight tiny dots on the outer ring
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f + PI.toFloat() / 8f
        drawCircle(pal.rule, r * 0.045f, Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f))
    }
}
