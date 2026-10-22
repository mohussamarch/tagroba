package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import app.masroufy.core.Currency
import app.masroufy.core.UiKey
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.MasroufyTheme
import app.masroufy.usecase.SpendRankRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * مراجعة r051 (F4–F6) على تصميم `TopSpending` (0.5.1): شرايح الفترة مجموعة اختيار اسمها «الفترة» (`aria-label` في النموذج) وبتنزل
 * سطر تاني لو العرض ما كفاش (`flex-wrap`)، واسم التصنيف الطويل بيلف على سطرين بدل ما يتقص من غير «…». المشهد بكثافة 1 ⇒ البكسل = dp.
 * بيانات مخترعة بس.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TopSpendLayoutTest {
    private fun <T> scene(widthDp: Int, content: @Composable () -> Unit, read: (ImageComposeScene) -> T): T {
        val scene = ImageComposeScene(widthDp, 2000, Density(1f)) { MasroufyTheme(reduceMotion = true) { content() } }
        try {
            scene.render()
            return read(scene)
        } finally {
            scene.close()
        }
    }

    private fun heightOf(widthDp: Int, content: @Composable () -> Unit): Int {
        var height = -1
        scene(widthDp, { Box(Modifier.onGloballyPositioned { height = it.size.height }) { content() } }) { }
        return height
    }

    private fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::nodes)

    @Test
    fun thePeriodChipsAreOneGroupNamedPeriod() {
        val groups = scene(400, { TopSpendPeriodChips(FilterPeriod.THIS) {} }) { s ->
            s.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }
                .filter { SemanticsProperties.SelectableGroup in it.config }
                .map { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        }
        assertEquals(listOf(listOf(t(UiKey.TOP_SPEND_PERIOD))), groups, "مجموعة واحدة اسمها «الفترة»")
    }

    @Test
    fun thePeriodChipsWrapToASecondLineWhenTheWidthIsShort() {
        assertEquals(40, heightOf(1000) { TopSpendPeriodChips(FilterPeriod.THIS) {} }, "عرض كفاية ⇒ سطر واحد بارتفاع الشريحة")
        val narrow = heightOf(150) { TopSpendPeriodChips(FilterPeriod.LAST) {} }
        assertTrue(narrow >= 40 + 8 + 40, "عرض ضيق ⇒ الشرايح بتنزل سطر تاني بدل ما الأخيرة تتقص ($narrow)")
    }

    private fun rankHeight(name: String): Int = heightOf(300) {
        TopSpendRanks(listOf(SpendRankRow("c-0", name, null, "#336699", 12_000, 3, 500, 50, 100)), Currency.SAR) {}
    }

    @Test
    fun aLongCategoryNameWrapsInsteadOfBeingClipped() {
        val short = rankHeight("أكل")
        val long = rankHeight("اشتراكات رقمية وخدمات سحابية ومنصات ترفيه وهمية")
        assertTrue(long > short, "الاسم الطويل بيلف ⇒ السطر أطول ($long مقابل $short)")
    }
}
