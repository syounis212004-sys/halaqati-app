package com.mohammedsamir.halaqati.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mohammedsamir.halaqati.ui.components.CollapsingFab
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
import org.junit.Before
import org.junit.Rule
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
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xxhdpi")
class HalaqaCardPreviewTest {
    @get:Rule val rule = createComposeRule()

    @Before fun only_when_requested() = assumeTrue(System.getenv("HALAQATI_PREVIEW") == "1")

    private fun save(name: String) {
        val dir = File("build/preview").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HalaqatiTheme(UiPreferences.defaults().copy(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
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
        val fab = rule.onNodeWithContentDescription("إضافة حلقة").getBoundsInRoot()
        val last = rule.onNodeWithTag("actions-3").getBoundsInRoot()
        val overlap = fab.left < last.right && fab.right > last.left && fab.top < last.bottom && fab.bottom > last.top
        assertTrue("the add button overlaps the last card's buttons ($fab vs $last)", !overlap)
    }

    @Test fun add_button_never_covers_the_last_card_light() = scrolledPage(dark = false)
    @Test fun add_button_never_covers_the_last_card_dark() = scrolledPage(dark = true)
}
