package com.mohammedsamir.halaqati.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.UiPreferences
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.CollapsingFab
import com.mohammedsamir.halaqati.ui.components.DialogOrnament
import com.mohammedsamir.halaqati.ui.components.EntityBand
import com.mohammedsamir.halaqati.ui.components.EntityRule
import com.mohammedsamir.halaqati.ui.components.NourCard
import com.mohammedsamir.halaqati.ui.components.NourSectionTitle
import com.mohammedsamir.halaqati.ui.theme.Entity
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.nourHeroFill
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.FabClearance
import com.mohammedsamir.halaqati.ui.components.GradientPair
import com.mohammedsamir.halaqati.ui.components.HalaqaCardData
import com.mohammedsamir.halaqati.ui.components.HalaqaManagementCard
import com.mohammedsamir.halaqati.ui.components.HalaqatiGradients
import com.mohammedsamir.halaqati.ui.components.OrgLogo
import com.mohammedsamir.halaqati.ui.theme.HalaqatiTheme
import com.mohammedsamir.halaqati.data.ThemeMode
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real rendering of the halaqa card (Robolectric native graphics -> PNG in app/build/preview). It only runs when the
 * environment variable HALAQATI_PREVIEW=1, so normal unit-test runs skip it. The PNGs are for a human to look at; the
 * assertions only cover geometry (the add button never overlaps the last card's buttons).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// RC104: never boot the real HalaqatiApplication (AppGraph -> AndroidKeyStore is missing on the JVM); the preview needs no app graph.
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class HalaqaCardPreviewTest {
    private val composeRule = createComposeRule()

    // The assumption must run BEFORE the compose rule starts its activity: ComponentActivity is only registered for the debug
    // variant (ui-test-manifest is debugImplementation), so in a release unit-test run the rule itself would fail the test.
    @get:Rule val chain: RuleChain = RuleChain.outerRule(TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue(System.getenv("HALAQATI_PREVIEW") == "1")
                base.evaluate()
            }
        }
    }).around(composeRule)
    private val rule get() = composeRule

    /**
     * Draws the compose root view into a bitmap. captureToImage() waits for a window redraw that never arrives while the
     * Robolectric main looper is paused (ComposeTimeoutException in forceRedraw), so the view is drawn directly instead.
     * A single-colour result means nothing was painted: fail loudly instead of saving an empty PNG.
     */
    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.onRoot().fetchSemanticsNode().root as android.view.View
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val sample = IntArray(64) { i -> bmp.getPixel((i * 37) % bmp.width, (i * 53) % bmp.height) }
        assertTrue("$name: nothing was painted (single colour)", sample.toSet().size > 1)
        val dir = File("build/preview").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HalaqatiTheme(UiPreferences.defaults().copy(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, motion = com.mohammedsamir.halaqati.data.MotionLevel.REDUCED)) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    @Composable
    private fun Card(index: Int, regular: Boolean, expanded: Boolean) {
        val pair: GradientPair = if (regular) HalaqatiGradients.Deep else HalaqatiGradients.Blue
        HalaqaManagementCard(
            data = HalaqaCardData(
                name = if (regular) "حلقة معاوية بن أبي سفيان" else "دورة تأهيلية",
                typeLabel = if (regular) "حلقة أساسية" else "دورة تأهيلية",
                statusLabel = "نشطة",
                statusActive = true,
                place = "مصلى الرباط",
                schedule = if (regular) "يوميا المغرب ما عدا السبت" else "الأحد والثلاثاء بعد العصر",
                programMeta = if (regular) "" else "تأهيل محفظين · مستوى مفتوح · حضور والتزام",
                studentCount = if (regular) 41 else 22,
                categoryCount = if (regular) 3 else 0,
                supervisorCount = if (regular) 2 else 1,
                supervisors = "Saja Younis، أ. محمد كامل",
                teachers = "د. محمد سمير أبو يونس",
                gradient = pair,
            ),
            expanded = expanded,
            onToggle = {},
            logo = { OrgLogo(null, Icons.Default.Groups, pair, tile = 44.dp, glyph = 24.dp) },
            menu = { IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = Color.White) } },
            notice = {},
            details = { Text("المسارات: حفظ عام 38 · تثبيت 3", style = MaterialTheme.typography.labelMedium) },
            actions = {
                Row(Modifier.fillMaxWidth().testTag("actions-$index"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("الفئات") }
                    FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.Edit, null); Text("تعديل") }
                }
            },
        )
    }

    private fun renderCards(dark: Boolean, expanded: Boolean) {
        rule.setContent {
            Themed(dark) {
                Column(Modifier.width(411.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(0, regular = true, expanded = expanded)
                    Card(1, regular = false, expanded = expanded)
                }
            }
        }
        save("cards_${if (dark) "dark" else "light"}_${if (expanded) "expanded" else "collapsed"}")
    }

    @Test fun cards_light_collapsed() = renderCards(dark = false, expanded = false)
    @Test fun cards_light_expanded() = renderCards(dark = false, expanded = true)
    @Test fun cards_dark_collapsed() = renderCards(dark = true, expanded = false)
    @Test fun cards_dark_expanded() = renderCards(dark = true, expanded = true)

    private fun scrolledPage(dark: Boolean) {
        rule.setContent {
            Themed(dark) {
                Box(Modifier.size(width = 411.dp, height = 640.dp)) {
                    val state = rememberLazyListState()
                    LazyColumn(
                        state = state,
                        modifier = Modifier.fillMaxSize().testTag("list"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, FabClearance),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(4) { i -> Card(i, regular = i % 2 == 0, expanded = true) }
                    }
                    CollapsingFab(state, "إضافة حلقة", Icons.Default.Add) {}
                }
            }
        }
        rule.onNodeWithTag("list").performScrollToIndex(3)
        rule.waitForIdle()
        save("page_scrolled_to_end_${if (dark) "dark" else "light"}")
        val fab = rule.onNodeWithContentDescription("إضافة حلقة", useUnmergedTree = true).getBoundsInRoot()
        val last = rule.onNodeWithTag("actions-3", useUnmergedTree = true).getBoundsInRoot()
        val overlap = fab.left < last.right && fab.right > last.left && fab.top < last.bottom && fab.bottom > last.top
        assertTrue("the add button overlaps the last card's buttons ($fab vs $last)", !overlap)
    }

    @Test fun add_button_never_covers_the_last_card_light() = scrolledPage(dark = false)
    @Test fun add_button_never_covers_the_last_card_dark() = scrolledPage(dark = true)

    // RC104: the shared identity pieces (top-bar band, empty state, notices) in light and dark.
    private fun identityPieces(dark: Boolean) {
        rule.setContent {
            Themed(dark) {
                Column(Modifier.width(411.dp)) {
                    AppTopBar("المحفّظون", onBack = {}, onRefresh = {})
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StateNotice("تمت المزامنة بنجاح", kind = "success")
                        StateNotice("تنبيه: يوجد تغييرات غير مزامنة", kind = "warning")
                        StateNotice("تعذّر الحفظ، حاول مجددًا", kind = "error")
                        StateNotice("معلومة عامة للمستخدم", kind = "info")
                        EmptyState("لا توجد حلقات بعد", actionLabel = "إضافة حلقة", onAction = {}, animated = false)
                    }
                }
            }
        }
        save("identity_pieces_${if (dark) "dark" else "light"}")
    }

    @Test fun identity_pieces_light() = identityPieces(dark = false)
    @Test fun identity_pieces_dark() = identityPieces(dark = true)

    // RC104: what a HalaqatiAlertDialog draws (the dialog window itself is not part of the root image, so its content is shown on a scrim).
    private fun dialogLook(dark: Boolean) {
        rule.setContent {
            Themed(dark) {
                Column(Modifier.width(411.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Variant 1: title + text (start aligned, rule above the title). Variant 2: delete dialog with an icon (icon stays centred under the rule).
                    for (withIcon in listOf(false, true)) {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = if (withIcon) androidx.compose.ui.Alignment.CenterHorizontally else androidx.compose.ui.Alignment.Start) {
                                DialogOrnament()
                                if (withIcon) Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.error)
                                Text("حذف الحلقة؟", style = MaterialTheme.typography.headlineSmall)
                                Text("سيتم نقل الحلقة إلى المحذوفات ويمكن استعادتها لاحقًا.", style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {}) { Text("إلغاء") }
                                    FilledTonalButton(onClick = {}) { Text("حذف") }
                                }
                            }
                        }
                    }
                }
            }
        }
        save("dialog_look_${if (dark) "dark" else "light"}")
    }

    @Test fun dialog_look_light() = dialogLook(dark = false)
    @Test fun dialog_look_dark() = dialogLook(dark = true)

    // RC104: the sign-in header band now uses the shared Hero pattern level.
    private fun authBand(dark: Boolean) {
        rule.setContent {
            Themed(dark) {
                Box(Modifier.width(411.dp).height(150.dp).nourHeroFill(MaterialTheme.colorScheme.primary)) {
                    Text("حلقاتي", Modifier.align(androidx.compose.ui.Alignment.Center), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        save("auth_band_${if (dark) "dark" else "light"}")
    }

    @Test fun auth_band_light() = authBand(dark = false)
    @Test fun auth_band_dark() = authBand(dark = true)

    // RC104 phase 3: every card that names a halaqa, a teacher or a supervisor carries the entity band in its own tone.
    private fun entityUnits(dark: Boolean) {
        rule.setContent {
            Themed(dark) {
                Column(Modifier.width(411.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NourSectionTitle("حلقة معاوية بن أبي سفيان · 41 طالب", entity = Entity.HALAQA)
                    NourCard(entity = Entity.HALAQA, marker = MaterialTheme.colorScheme.tertiary) {
                        Text("حلقة معاوية بن أبي سفيان", style = MaterialTheme.typography.titleSmall)
                        Text("د. محمد سمير أبو يونس", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("زيارة") }
                    }
                    NourCard(entity = Entity.TEACHER) { Text("محفّظ: أ. محمد كامل", style = MaterialTheme.typography.titleSmall) }
                    NourCard(entity = Entity.SUPERVISOR) { Text("مشرف: Saja Younis", style = MaterialTheme.typography.titleSmall) }
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EntityRule(Entity.HALAQA)
                            Text("صف حلقة داخل بطاقة مبطّنة", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        Column {
                            EntityBand(Entity.TEACHER)
                            Text("بطاقة محفّظ بشريط كامل العرض", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        save("entity_units_${if (dark) "dark" else "light"}")
    }

    @Test fun entity_units_light() = entityUnits(dark = false)
    @Test fun entity_units_dark() = entityUnits(dark = true)
}
