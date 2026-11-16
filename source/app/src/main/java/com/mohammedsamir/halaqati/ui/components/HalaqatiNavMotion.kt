package com.mohammedsamir.halaqati.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.navigation.NavBackStackEntry

/**
 * RC27.16: directional screen transitions. Three kinds of destination, three feelings:
 *  - Tab (bottom bar / drawer pages): a short slide toward the side the page sits on, in the order of the bottom bar.
 *  - Detail (student profile): pushes in from the reading side, the page behind recedes a little (parallax); back reverses it.
 *  - Overlay (notifications, sync center, settings, follow-up): rises from below and goes back down.
 * Everything is direction-aware (RTL safe) and collapses to a very short fade under reduced motion.
 */
internal enum class NavKind { Tab, Detail, Overlay, Section }

private val OVERLAY_ROUTES = setOf("notifications", "sync_center", "settings", "follow_up", "quran")

/** RC82: management / drawer pages (accounts, structure, team, exams, support …) glide in from the reading side instead of a bare fade. */
private val SECTION_ROUTES = setOf(
    "users", "org", "team", "approvals", "observer_requests", "support", "donor_chats", "exams", "certificates", "plans", "reports",
    "insights", "awards", "supervision", "visits", "import_export", "rankings", "students", "halaqas",
)

internal fun navKindOf(route: String?): NavKind = when {
    route == null -> NavKind.Tab
    route.startsWith("student/") -> NavKind.Detail
    route in OVERLAY_ROUTES -> NavKind.Overlay
    route in SECTION_ROUTES -> NavKind.Section
    else -> NavKind.Tab
}

private const val SLIDE_MS = 320
private const val FADE_IN_MS = 260
private const val FADE_OUT_MS = 180

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnter(order: List<String>, reduced: Boolean): EnterTransition {
    if (reduced) return fadeIn(tween(90))
    val to = navKindOf(targetState.destination.route)
    return when (to) {
        NavKind.Overlay -> slideIntoContainer(SlideDirection.Up, tween(SLIDE_MS + 40, easing = FastOutSlowInEasing)) { it / 6 } +
            fadeIn(tween(FADE_IN_MS, easing = LinearOutSlowInEasing))
        NavKind.Detail -> slideIntoContainer(SlideDirection.Start, tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 4 } +
            fadeIn(tween(FADE_IN_MS, easing = LinearOutSlowInEasing))
        NavKind.Section -> slideIntoContainer(SlideDirection.Start, tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 12 } +
            fadeIn(tween(FADE_IN_MS, delayMillis = 40, easing = LinearOutSlowInEasing))
        // RC33: tabs are peers, so switching between them is a quiet cross-fade (no sideways slide that suggests depth).
        NavKind.Tab -> fadeIn(tween(FADE_IN_MS, delayMillis = 60, easing = LinearOutSlowInEasing))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navExit(order: List<String>, reduced: Boolean): ExitTransition {
    if (reduced) return fadeOut(tween(70))
    val from = navKindOf(initialState.destination.route)
    val to = navKindOf(targetState.destination.route)
    return when {
        // Leaving an overlay / detail by choosing another page: go back down / out the way it came.
        from == NavKind.Overlay -> slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS - 60, easing = FastOutSlowInEasing)) { it / 6 } +
            fadeOut(tween(FADE_OUT_MS))
        from == NavKind.Detail -> slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS - 60, easing = FastOutSlowInEasing)) { it / 4 } +
            fadeOut(tween(FADE_OUT_MS))
        from == NavKind.Section -> slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS - 80, easing = FastOutSlowInEasing)) { it / 12 } +
            fadeOut(tween(FADE_OUT_MS))
        // Page behind a new overlay / detail recedes.
        to == NavKind.Overlay -> scaleOut(tween(SLIDE_MS, easing = FastOutSlowInEasing), targetScale = 0.95f) + fadeOut(tween(FADE_OUT_MS))
        to == NavKind.Detail -> slideOutOfContainer(SlideDirection.Start, tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 8 } +
            fadeOut(tween(FADE_OUT_MS))
        else -> fadeOut(tween(90))
    }
}

/** Back stack pops (system back, back arrow, predictive back): the page below returns, the top page leaves. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnter(reduced: Boolean): EnterTransition {
    if (reduced) return fadeIn(tween(90))
    return when (navKindOf(initialState.destination.route)) {
        NavKind.Detail -> slideIntoContainer(SlideDirection.End, tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 8 } +
            fadeIn(tween(FADE_IN_MS))
        NavKind.Section -> slideIntoContainer(SlideDirection.End, tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 12 } +
            fadeIn(tween(FADE_IN_MS))
        else -> fadeIn(tween(FADE_IN_MS, delayMillis = 40, easing = LinearOutSlowInEasing))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExit(reduced: Boolean): ExitTransition {
    if (reduced) return fadeOut(tween(70))
    return when (navKindOf(initialState.destination.route)) {
        NavKind.Overlay -> slideOutOfContainer(SlideDirection.Down, tween(SLIDE_MS - 40, easing = FastOutSlowInEasing)) { it / 6 } +
            fadeOut(tween(FADE_OUT_MS))
        NavKind.Detail -> slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS - 40, easing = FastOutSlowInEasing)) { it / 4 } +
            scaleOut(tween(SLIDE_MS), targetScale = 0.96f) + fadeOut(tween(FADE_OUT_MS))
        NavKind.Section -> slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS - 60, easing = FastOutSlowInEasing)) { it / 12 } +
            fadeOut(tween(FADE_OUT_MS))
        else -> fadeOut(tween(FADE_OUT_MS))
    }
}
