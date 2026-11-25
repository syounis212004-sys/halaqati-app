package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion
import kotlin.math.min

/**
 * The marking dock of the mushaf: a floating "squircle" tool dock (Material 3 Expressive look, built from the Compose Foundation
 * because the pinned material3 1.4.0 has no HorizontalFloatingToolbar). Portrait: a bottom dock. Landscape: a vertical side rail
 * (start edge) so the page keeps every pixel of height. Both collapse to a compact strip by tap or drag and expand back; every tool stays
 * reachable (collapsed keeps the mark type, the active tool, undo and redo).
 */
internal object DockSpec {
    /** Every touch target is at least this big (WCAG 2.5.8 / Material). */
    val Touch = 48.dp
    val ButtonCompact = 46.dp
    val ButtonRegular = 50.dp
    val ButtonWide = 54.dp
    val ContainerRadiusPortrait = 26.dp
    val ContainerRadiusRail = 24.dp
    val ButtonRadiusFraction = 0.32f    // 15–17dp for a 46–54dp button
    val MaxWidth = 540.dp
    const val CellsInRow = 7

    /** Width one cell gets in the single-row dock: container width minus its padding and the two dividers, shared by 7 cells. */
    fun cellWidth(containerWidth: Dp): Dp = ((containerWidth.value - 12f - 18f) / CellsInRow).dp
    /** Undo/redo stay in the same row only when each cell keeps a full 48dp touch width; otherwise they move to the strip above. */
    fun singleRow(containerWidth: Dp): Boolean = cellWidth(containerWidth) >= Touch
    fun buttonFor(cell: Dp): Dp = when {
        cell >= 62.dp -> ButtonWide
        cell >= 52.dp -> ButtonRegular
        else -> ButtonCompact
    }
}

/** Keeps the dock labels readable but never lets a huge system font break the row (labels are also the accessibility names). */
@Composable
private fun CappedFontScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(d.density, min(d.fontScale, 1.2f))) { content() }
}

@Composable
internal fun MushafDock(
    side: Boolean,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    counts: Pair<Int, Int>,
    type: MarkType,
    tool: Tool,
    canUndo: Boolean,
    canRedo: Boolean,
    pal: MushafPalette,
    onType: (MarkType) -> Unit,
    onTool: (Tool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val actions = DockActions(
        counts, type, tool, canUndo, canRedo, pal,
        onType = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onType(it) },
        onTool = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onTool(it) },
        onUndo = onUndo, onRedo = onRedo,
        toggle = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onExpanded(it) },
    )
    CappedFontScale {
        if (side) SideRail(expanded, actions, modifier) else BottomDock(expanded, actions, modifier)
    }
}

private class DockActions(
    val counts: Pair<Int, Int>, val type: MarkType, val tool: Tool, val canUndo: Boolean, val canRedo: Boolean, val pal: MushafPalette,
    val onType: (MarkType) -> Unit, val onTool: (Tool) -> Unit, val onUndo: () -> Unit, val onRedo: () -> Unit, val toggle: (Boolean) -> Unit,
)

// ------------------------------------------------------------------ portrait: bottom dock

@Composable
private fun BottomDock(expanded: Boolean, a: DockActions, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(DockSpec.ContainerRadiusPortrait)
    var drag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val width = min(maxWidth.value, DockSpec.MaxWidth.value).dp
        val single = DockSpec.singleRow(width)
        val cell = DockSpec.cellWidth(width).coerceAtLeast(DockSpec.Touch)
        val btn = DockSpec.buttonFor(cell)
        Column(
            Modifier.width(width).padding(horizontal = 0.dp)
                .shadow(8.dp, shape, clip = false)
                .background(a.pal.dockBg, shape).border(1.dp, a.pal.chromeLine, shape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onVerticalDrag = { _, dy -> drag += dy },
                        onDragEnd = {
                            if (drag > 36f && expanded) a.toggle(false) else if (drag < -36f && !expanded) a.toggle(true)
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    )
                }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val enter = if (reduced) EnterTransition.None else fadeIn(tween(160)) + expandVertically(spring(0.8f, Spring.StiffnessMediumLow))
            val exit = if (reduced) ExitTransition.None else fadeOut(tween(110)) + shrinkVertically(spring(0.9f, Spring.StiffnessMedium))
            // the strip: the handle (tap / drag), and — when the row is too narrow for 48dp cells — undo, redo and the chevron
            DockStrip(expanded, single, a)
            AnimatedVisibility(visible = expanded, enter = enter, exit = exit, label = "dock-tools") {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    TypeCells(a, btn, cell)
                    DockDivider(vertical = true)
                    ToolCells(a, btn, cell)
                    if (single) {
                        DockDivider(vertical = true)
                        UndoRedoCells(a, btn, cell)
                    }
                }
            }
        }
    }
}

/** The always-visible top part of the bottom dock. Expanded it is a slim handle; collapsed it is the whole compact bar. */
@Composable
private fun DockStrip(expanded: Boolean, single: Boolean, a: DockActions) {
    val label = if (expanded) "طي شريط الأدوات" else "توسيع شريط الأدوات"
    if (expanded && single) {
        Box(
            Modifier.fillMaxWidth().height(28.dp).clickable(role = Role.Button, onClickLabel = label) { a.toggle(false) }.semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { HandlePill(a.pal) }
        return
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!expanded) {
            MiniType(a, MarkType.Mistake)
            MiniType(a, MarkType.Prompt)
            MiniTool(a)
            Spacer(Modifier.weight(1f))
        }
        MiniAction("تراجع", Icons.AutoMirrored.Filled.Undo, a.canUndo, a.pal, onClick = a.onUndo)
        MiniAction("إعادة", Icons.AutoMirrored.Filled.Redo, a.canRedo, a.pal, onClick = a.onRedo)
        if (expanded) {
            Box(Modifier.weight(1f).heightIn(min = DockSpec.Touch).clickable(role = Role.Button, onClickLabel = label) { a.toggle(false) }, contentAlignment = Alignment.Center) { HandlePill(a.pal) }
        }
        MiniAction(label, if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, true, a.pal) { a.toggle(!expanded) }
    }
}

@Composable
private fun HandlePill(pal: MushafPalette) {
    Box(Modifier.width(40.dp).height(4.dp).background(pal.chromeInk.copy(alpha = 0.32f), RoundedCornerShape(2.dp)))
}

// ------------------------------------------------------------------ landscape: side rail

@Composable
private fun SideRail(expanded: Boolean, a: DockActions, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(DockSpec.ContainerRadiusRail)
    var drag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val rowH = 76.dp   // 46dp plate + label (+ padding) + the 4dp gap between rows
        // fewest columns whose rows fit in the height we were given (chevron + padding take 60dp); the rail scrolls only as a last resort
        val cols = (1..3).firstOrNull { c -> rowH * ((DockSpec.CellsInRow + c - 1) / c) + 64.dp <= maxHeight } ?: 3
        val twoCols = expanded && cols > 1
        val cell = 56.dp
        val w = if (!expanded) 60.dp else cell * cols + 8.dp
        val enter = if (reduced) EnterTransition.None else fadeIn(tween(160)) + expandHorizontally(spring(0.8f, Spring.StiffnessMediumLow))
        val exit = if (reduced) ExitTransition.None else fadeOut(tween(110)) + shrinkHorizontally(spring(0.9f, Spring.StiffnessMedium))
        val animW by animateDpAsState(w, if (reduced) tween(0) else spring(0.82f, Spring.StiffnessMediumLow), label = "rail-width")
        Column(
            Modifier.width(animW).fillMaxHeight().shadow(8.dp, shape, clip = false)
                .background(a.pal.dockBg, shape).border(1.dp, a.pal.chromeLine, shape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, dx -> drag += dx },
                        // the rail sits on the right edge (start in Arabic): dragging toward that edge folds it, away from it opens it
                        onDragEnd = {
                            if (drag > 36f && expanded) a.toggle(false) else if (drag < -36f && !expanded) a.toggle(true)
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    )
                }
                .verticalScroll(rememberScrollState()).padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MiniAction(if (expanded) "طي شريط الأدوات" else "توسيع شريط الأدوات", if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, true, a.pal, rotate = true) { a.toggle(!expanded) }
            if (expanded) {
                val btn = DockSpec.ButtonCompact
                val cells: List<@Composable () -> Unit> = listOf(
                    { TypeCell(a, MarkType.Mistake, btn, cell) }, { TypeCell(a, MarkType.Prompt, btn, cell) },
                    { ToolCell(a, Tool.Tap, "لمس", Icons.Default.TouchApp, a.pal.chromeInk, btn, cell) },
                    { ToolCell(a, Tool.Highlight, "تظليل", Icons.Default.BorderColor, a.pal.highlight, btn, cell) },
                    { ToolCell(a, Tool.Erase, "ممحاة", Icons.Default.AutoFixNormal, a.pal.chromeInk, btn, cell) },
                    { ActionCell("تراجع", Icons.AutoMirrored.Filled.Undo, a.canUndo, a.pal, btn, cell, a.onUndo) },
                    { ActionCell("إعادة", Icons.AutoMirrored.Filled.Redo, a.canRedo, a.pal, btn, cell, a.onRedo) },
                )
                if (twoCols) cells.chunked(cols).forEach { group -> Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) { group.forEach { it() }; repeat(cols - group.size) { Spacer(Modifier.width(cell)) } } }
                else cells.forEachIndexed { i, c -> c(); if (i == 1 || i == 4) DockDivider(vertical = false) }
            } else {
                MiniType(a, MarkType.Mistake)
                MiniType(a, MarkType.Prompt)
                MiniTool(a)
                MiniAction("تراجع", Icons.AutoMirrored.Filled.Undo, a.canUndo, a.pal, onClick = a.onUndo)
                MiniAction("إعادة", Icons.AutoMirrored.Filled.Redo, a.canRedo, a.pal, onClick = a.onRedo)
            }
        }
    }
}

// ------------------------------------------------------------------ groups

@Composable
private fun TypeCells(a: DockActions, btn: Dp, cell: Dp) {
    TypeCell(a, MarkType.Mistake, btn, cell)
    TypeCell(a, MarkType.Prompt, btn, cell)
}

@Composable
private fun ToolCells(a: DockActions, btn: Dp, cell: Dp) {
    ToolCell(a, Tool.Tap, "لمس", Icons.Default.TouchApp, a.pal.chromeInk, btn, cell)
    ToolCell(a, Tool.Highlight, "تظليل", Icons.Default.BorderColor, a.pal.highlight, btn, cell)
    ToolCell(a, Tool.Erase, "ممحاة", Icons.Default.AutoFixNormal, a.pal.chromeInk, btn, cell)
}

@Composable
private fun UndoRedoCells(a: DockActions, btn: Dp, cell: Dp) {
    ActionCell("تراجع", Icons.AutoMirrored.Filled.Undo, a.canUndo, a.pal, btn, cell, a.onUndo)
    ActionCell("إعادة", Icons.AutoMirrored.Filled.Redo, a.canRedo, a.pal, btn, cell, a.onRedo)
}

@Composable
private fun DockDivider(vertical: Boolean) {
    if (vertical) Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp).width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
    else Box(Modifier.padding(vertical = 2.dp).width(32.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun TypeCell(a: DockActions, t: MarkType, btn: Dp, cell: Dp) {
    val mistake = t == MarkType.Mistake
    val color = if (mistake) a.pal.chromeMistake else a.pal.chromePrompt
    val n = if (mistake) a.counts.first else a.counts.second
    val name = if (mistake) "خطأ" else "تلقين"
    SquircleCell(
        label = name, selected = a.type == t, enabled = true, accent = color, pal = a.pal, btn = btn, cellW = cell,
        description = "$name، العدد $n", selectable = true, badge = n, onClick = { a.onType(t) },
    ) { MarkBadge(mistake, color, btn * 0.6f) }
}

@Composable
private fun ToolCell(a: DockActions, t: Tool, label: String, icon: ImageVector, accent: Color, btn: Dp, cell: Dp) {
    SquircleCell(
        label = label, selected = a.tool == t, enabled = true, accent = accent, pal = a.pal, btn = btn, cellW = cell,
        description = label, selectable = true, badge = null, onClick = { a.onTool(t) },
    ) { tint -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun ActionCell(label: String, icon: ImageVector, enabled: Boolean, pal: MushafPalette, btn: Dp, cell: Dp, onClick: () -> Unit) {
    SquircleCell(
        label = label, selected = false, enabled = enabled, accent = pal.chromeInk, pal = pal, btn = btn, cellW = cell,
        description = label, selectable = false, badge = null, onClick = onClick,
    ) { tint -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) }
}

/**
 * One squircle tool button: a rounded-square plate (radius ≈ 32% of the side), the icon inside, the Arabic label under it.
 * The selected plate takes the tool's semantic colour with a ring; pressing shrinks it with a spring. The cell is at least 48dp wide
 * and tall, whatever the plate size, so the touch target never gets smaller than the guideline.
 */
@Composable
private fun SquircleCell(
    label: String, selected: Boolean, enabled: Boolean, accent: Color, pal: MushafPalette, btn: Dp, cellW: Dp,
    description: String, selectable: Boolean, badge: Int?, onClick: () -> Unit, icon: @Composable (Color) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val colorSpec = if (reduced) tween<Color>(0) else tween(180)
    val plate by animateColorAsState(if (selected) accent.copy(alpha = if (pal.dark) 0.30f else 0.20f) else pal.chromeLine.copy(alpha = 0.32f), colorSpec, label = "plate")
    val ring by animateColorAsState(if (selected) accent else Color.Transparent, colorSpec, label = "ring")
    val tint by animateColorAsState(if (selected) accent else pal.chromeInk, colorSpec, label = "tint")
    val ringW by animateDpAsState(if (selected) 2.dp else 0.dp, if (reduced) tween(0) else tween(160), label = "ring-width")
    val scale by animateFloatAsState(
        if (pressed) 0.90f else if (selected) 1.05f else 1f,
        if (reduced) tween(0) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "plate-scale",
    )
    val radius = btn * DockSpec.ButtonRadiusFraction
    Column(
        Modifier.width(cellW).heightIn(min = DockSpec.Touch).padding(vertical = 2.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = if (selectable) Role.RadioButton else Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description; if (selectable) this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.size(btn).graphicsLayer { scaleX = scale; scaleY = scale }
                .drawBehind {
                    val r = CornerRadius(radius.toPx())
                    drawRoundRect(plate, cornerRadius = r)
                    if (ringW > 0.dp) {
                        val s = ringW.toPx()
                        drawRoundRect(ring, topLeft = androidx.compose.ui.geometry.Offset(s / 2, s / 2), size = androidx.compose.ui.geometry.Size(size.width - s, size.height - s), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(s))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            icon(tint)
            if (badge != null) CountBadge(badge, accent, Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = 1.dp))
        }
        Text(label, color = tint, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun CountBadge(n: Int, color: Color, modifier: Modifier) {
    Box(modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).background(color, RoundedCornerShape(9.dp)).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(digits(n), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
    }
}

// ------------------------------------------------------------------ collapsed pieces (icon only, 48dp touch)

@Composable
private fun MiniType(a: DockActions, t: MarkType) {
    val mistake = t == MarkType.Mistake
    val color = if (mistake) a.pal.chromeMistake else a.pal.chromePrompt
    val n = if (mistake) a.counts.first else a.counts.second
    val name = if (mistake) "خطأ" else "تلقين"
    SquircleCell(
        label = name, selected = a.type == t, enabled = true, accent = color, pal = a.pal, btn = 40.dp, cellW = DockSpec.Touch,
        description = "$name، العدد $n", selectable = true, badge = n, onClick = { a.onType(t) },
    ) { MarkBadge(mistake, color, 24.dp) }
}

@Composable
private fun MiniTool(a: DockActions) {
    val (label, icon, accent) = when (a.tool) {
        Tool.Tap -> Triple("لمس", Icons.Default.TouchApp, a.pal.chromeInk)
        Tool.Highlight -> Triple("تظليل", Icons.Default.BorderColor, a.pal.highlight)
        Tool.Erase -> Triple("ممحاة", Icons.Default.AutoFixNormal, a.pal.chromeInk)
    }
    SquircleCell(
        label = label, selected = true, enabled = true, accent = accent, pal = a.pal, btn = 40.dp, cellW = DockSpec.Touch,
        description = "الأداة الحالية: $label. اضغط لتوسيع الأدوات", selectable = false, badge = null, onClick = { a.toggle(true) },
    ) { tint -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun MiniAction(label: String, icon: ImageVector, enabled: Boolean, pal: MushafPalette, rotate: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(DockSpec.Touch).alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).background(pal.chromeLine.copy(alpha = 0.32f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = pal.chromeInk, modifier = Modifier.size(22.dp).graphicsLayer { if (rotate) rotationZ = 0f })
        }
    }
}
