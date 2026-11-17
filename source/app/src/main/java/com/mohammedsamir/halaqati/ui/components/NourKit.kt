package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.CairoFamily

/**
 * Nour design kit: one calm vocabulary shared by every screen.
 *  - surfaces: white card (20dp corners, 1dp outline), at most ONE strong emerald hero per screen;
 *  - colour = status only (success / warning / danger) and appears as a small pill, a thin marker, or a progress fill;
 *  - numbers in Cairo (tabular look), Amiri stays for large titles;
 *  - icon sizes 20 / 24 / 28, avatar sizes 40 / 48 / 56 / 72.
 */
object NourIcon {
    val Inline: Dp = 20.dp
    val Action: Dp = 24.dp
    val Feature: Dp = 28.dp
}

object NourAvatarSize {
    val Small: Dp = 40.dp
    val Medium: Dp = 48.dp
    val Large: Dp = 56.dp
    val Hero: Dp = 72.dp
}

/** success / warning / danger / neutral -> (accent, soft container, on-container) */
@Composable
fun nourTone(kind: String): Triple<Color, Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (kind) {
        "success" -> Triple(cs.primary, cs.primaryContainer, cs.onPrimaryContainer)
        "warning" -> Triple(cs.tertiary, cs.tertiaryContainer, cs.onTertiaryContainer)
        "danger", "error" -> Triple(cs.error, cs.errorContainer, cs.onErrorContainer)
        else -> Triple(cs.onSurfaceVariant, cs.surfaceVariant, cs.onSurfaceVariant)
    }
}

@Composable
fun NourCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    marker: Color? = null,
    depth: NourDepth = LocalNourDepth.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val base = modifier.fillMaxWidth()
    Surface(
        modifier = (if (onClick != null) base.clip(shape).clickable(onClick = onClick) else base).nourRaised(shape, depth = depth, fill = false),
        shape = shape,
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(Modifier.then(if (marker != null) Modifier.padding(0.dp) else Modifier)) {
            if (marker != null) Box(Modifier.fillMaxWidth().height(3.dp).background(marker))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Slim progress bar: rounded, no stop-indicator dot, fills from the reading start (right in RTL). */
@Composable
fun NourProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/**
 * A number tile: soft tinted container, big Cairo number, small label, optional 20dp icon.
 * RC98: text uses the container's own `on` colour (a guaranteed pair) and the gradient only moves toward the surface,
 * so the weakest point of the tile still reads in both themes (the old gradient lightened toward white in dark mode).
 */
@Composable
fun NourStatTile(value: String, label: String, modifier: Modifier = Modifier, tone: String = "neutral", icon: ImageVector? = null) {
    val (accent, toneContainer, toneOn) = nourTone(tone)
    val cs = MaterialTheme.colorScheme
    val container = if (tone == "neutral") cs.primaryContainer else toneContainer
    val on = if (tone == "neutral") cs.onPrimaryContainer else toneOn
    val shape = RoundedCornerShape(16.dp)
    val brush = remember(container, cs.surface) {
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(container, cs.surface, .45f), container))
    }
    Column(
        modifier.nourRaised(shape, depth = NourDepth.Card, tint = accent, fill = false).clip(shape).background(brush)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = on, modifier = Modifier.size(NourIcon.Inline))
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = CairoFamily, fontWeight = FontWeight.ExtraBold), color = on)
        Text(label, style = MaterialTheme.typography.labelMedium, color = on, maxLines = 2)
    }
}

/** An icon in a soft round container; the only icon container shape used in lists and headers. */
@Composable
fun NourIconBadge(icon: ImageVector, modifier: Modifier = Modifier, tone: String = "success", size: Dp = 40.dp) {
    val (accent, container, _) = nourTone(tone)
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(if (size >= 48.dp) NourIcon.Feature else NourIcon.Action))
    }
}

/** Photo (or the first letter on a soft tint) at one of the four avatar sizes. */
@Composable
fun NourAvatar(photo: String?, name: String, size: Dp = NourAvatarSize.Medium, modifier: Modifier = Modifier) {
    StudentAvatar(photo, name, size, modifier)
}

/**
 * RC95: the app's one small ornament — an eight-pointed star (two squares, the khatam of Islamic geometry) in gold.
 * Drawn once per size via drawWithCache; decorative only, hidden from accessibility.
 */
@Composable
fun NourStar(modifier: Modifier = Modifier, size: Dp = 14.dp, color: Color = MaterialTheme.colorScheme.tertiary, hole: Color = MaterialTheme.colorScheme.surface) {
    androidx.compose.foundation.layout.Spacer(
        modifier.size(size).drawWithCache {
            val r = this.size.minDimension / 2f
            val c = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
            fun square(rot: Double) = androidx.compose.ui.graphics.Path().apply {
                for (k in 0 until 4) {
                    val a = rot + k * Math.PI / 2
                    val x = c.x + r * kotlin.math.cos(a).toFloat()
                    val y = c.y + r * kotlin.math.sin(a).toFloat()
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            val a = square(0.0)
            val b = square(Math.PI / 4)
            onDrawBehind {
                drawPath(a, color)
                drawPath(b, color)
                drawCircle(hole, radius = r * .28f, center = c)
            }
        },
    )
}

/** RC95: a section title with the gold star before it and a hairline after it. One style for every role. */
@Composable
fun NourSectionTitle(text: String, modifier: Modifier = Modifier, entity: com.mohammedsamir.halaqati.ui.theme.Entity? = null, trailing: (@Composable () -> Unit)? = null) {
    // RC104: a title that names a halaqa / teacher / supervisor is a whole ornamented bar in the entity colour.
    if (entity != null) {
        EntityHeader(entity, text, modifier.padding(top = 6.dp), trailing = trailing)
        return
    }
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { NourStar(size = 16.dp) }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        trailing?.invoke()
    }
}

/**
 * RC98: the three depth tiers of the identity, so not every surface shouts the same.
 *  - [Hero]: the one lead block of a screen (number strip, today card, role hero): strongest lift.
 *  - [Card]: a secondary card (forms, details, summaries): medium lift and a faint gradient.
 *  - [Row]: a repeated list entry: nearly flat (hairline and a 1dp lift) so a long list stays calm.
 */
enum class NourDepth { Hero, Card, Row }

/** The tier a default card draws with. List wrappers ([items], [itemsIndexed]) switch it to [NourDepth.Row] for their rows. */
val LocalNourDepth = androidx.compose.runtime.compositionLocalOf { NourDepth.Card }

/**
 * Colours of every emerald hero. The user may pick any primary in Settings and the dark theme turns primary into a LIGHT
 * green, so a hero can no longer assume "white text on primary": the base is darkened (hue kept) until white reads on it.
 * ContrastTest checks every point of the gradient for the default and the five preset primaries in both themes.
 */
object NourHeroColors {
    /** Minimum contrast of white on the darkest-to-lightest point of the gradient (WCAG AA for small text is 4.5). */
    const val MIN_WHITE_CONTRAST = 4.5f
    private const val BASE_TARGET = 6.6f
    /** Opacity of the white girih lattice drawn over the hero; ContrastTest checks the lightest gradient stop WITH this lattice on top. */
    const val LatticeAlpha = .12f
    /** Gold used for the number that needs attention; large text, so 3:1 is required (ContrastTest checks it). */
    val Attention = Color(0xFFFFE08A)

    fun base(primary: Color): Color {
        var c = primary
        var steps = 0
        while (com.mohammedsamir.halaqati.ui.theme.contrastRatio(c, Color.White) < BASE_TARGET && steps < 24) {
            c = androidx.compose.ui.graphics.lerp(c, Color.Black, .08f)
            steps++
        }
        return c
    }

    fun stops(primary: Color): List<Color> {
        val b = base(primary)
        return listOf(androidx.compose.ui.graphics.lerp(b, Color.Black, .28f), b, androidx.compose.ui.graphics.lerp(b, Color.White, .04f))
    }
}

/** The single hero fill shared by every role: deepened theme primary + one cached girih lattice. Text on it is always white. */
@Composable
fun Modifier.nourHeroFill(primary: Color): Modifier {
    val brush = remember(primary) { androidx.compose.ui.graphics.Brush.linearGradient(NourHeroColors.stops(primary)) }
    return this.background(brush).nourPattern(PatternLevel.Hero)
}

/**
 * The lead element of a list screen: one hero holding its 2-4 key numbers; [items].third marks the one needing attention.
 * At large font scales the three columns cannot hold a word, so the numbers stack as rows instead of breaking mid-word.
 */
@Composable
fun NourStatStrip(items: List<Triple<String, String, Boolean>>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().nourRaised(shape, depth = NourDepth.Hero, fill = false).clip(shape).nourHeroFill(primary)) {
        if (!stacked) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                items.forEachIndexed { i, (value, label, warn) ->
                    if (i > 0) Box(Modifier.width(1.dp).height(32.dp).align(Alignment.CenterVertically).background(Color.White.copy(alpha = .3f)))
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = CairoFamily, fontWeight = FontWeight.ExtraBold), color = if (warn) NourHeroColors.Attention else Color.White, maxLines = 1)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { (value, label, warn) ->
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = CairoFamily, fontWeight = FontWeight.ExtraBold), color = if (warn) NourHeroColors.Attention else Color.White, modifier = Modifier.width(72.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * The one "depth" recipe: a brand-tinted shadow (not grey) sized by [depth], and (when [fill]) a soft vertical surface gradient.
 * [fill] = false keeps a caller-chosen colour and only lifts it. Brushes are remembered per (surface, tint) so a recomposition
 * does not allocate a new gradient and the modifier chain stays equal.
 */
@Composable
fun Modifier.nourRaised(
    shape: androidx.compose.ui.graphics.Shape,
    depth: NourDepth = LocalNourDepth.current,
    tint: Color = MaterialTheme.colorScheme.primary,
    fill: Boolean = true,
): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    val elevation = when (depth) { NourDepth.Hero -> 8.dp; NourDepth.Card -> 3.dp; NourDepth.Row -> 1.dp }
    val glow = remember(tint) { tint.copy(alpha = .30f) }
    val brush = remember(surface, tint, depth) {
        if (depth == NourDepth.Row) null
        else androidx.compose.ui.graphics.Brush.verticalGradient(listOf(surface, androidx.compose.ui.graphics.lerp(surface, tint, if (depth == NourDepth.Hero) .08f else .05f)))
    }
    val lifted = this.shadow(elevation, shape, clip = false, ambientColor = glow, spotColor = glow)
    return if (!fill) lifted else if (brush != null) lifted.background(brush, shape) else lifted.background(surface, shape)
}
