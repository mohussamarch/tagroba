package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.masroufy.core.Asset
import app.masroufy.core.AverageEntry
import app.masroufy.core.Currency
import app.masroufy.core.HoldingField
import app.masroufy.core.HoldingFormText
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingType
import app.masroufy.core.HoldingValue
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.InvestRates
import app.masroufy.core.InvestRatesState
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.holdingsView
import app.masroufy.ui.theme.MasroufyTheme
import app.masroufy.usecase.ProjectHoldingGroup
import app.masroufy.usecase.planSliderFor
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * «حاجاتي» · صفحة النوع · «ضيف حاجة» مرسومين لصور PNG (بس لو `HOLDINGS_PNG_DIR` متظبط) عشان المقارنة بصور النموذج
 * (`masroufy-design-shots`). نفس أرقام النموذج المخترعة (الدهب 52,560 · الشقة 576,000 · الأسهم 89,000 · العربية 48,000).
 */
class HoldingsPngTest {
    private fun item(id: String, name: String, type: HoldingType, purpose: HoldingPurpose, value: Long?, qty: Long = QUANTITY_SCALE, karat: Int? = null) =
        HoldingValue(Asset(id, name, "other", "وحدة", Currency.SAR, false, holdingType = type.wire), type, purpose, value, qty, karat = karat)

    private val snapshot = HoldingsSnapshot(
        Currency.SAR,
        listOf(
            item("g", "ذهب", HoldingType.GOLD, HoldingPurpose.RESERVE, 5_256_000, 120 * QUANTITY_SCALE, karat = 21),
            item("f", "شقة الياسمين", HoldingType.FLAT, HoldingPurpose.INVEST, 57_600_000),
            item("s", "الأسهم", HoldingType.STK, HoldingPurpose.INVEST, 8_900_000, 40 * QUANTITY_SCALE),
            item("c", "السيارة", HoldingType.CAR, HoldingPurpose.PERSONAL, 4_800_000),
        ),
        goldPriceMissing = false,
    )
    private val entry = AverageEntry("GOLD_USD", 1000, "2015", "2024", true, false, "متوسط رسمي مخترع", null, "cagr")
    private val rates = InvestRatesState.Ready(InvestRates(250, 1000, 700, entry, entry, entry))

    @OptIn(ExperimentalComposeUiApi::class)
    private fun png(out: File, name: String, widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(widthDp * 2, heightDp * 2, Density(2f)) {
            MasroufyTheme(reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFAF9F3), Color(0xFFEFF3ED), Color(0xFFDCE9DF))))) { content() }
            }
        }
        try {
            File(out, "$name.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersHoldingsSectionsToPng() {
        val out = System.getenv("HOLDINGS_PNG_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        out.mkdirs()
        png(out, "InvestHoldings", 350, 560) {
            HoldingsBlock(holdingsView(snapshot, null), Currency.SAR, null, {}, {}, {}, Modifier.padding(0.dp))
        }
        png(out, "InvestHoldings-filter-invest", 350, 560) {
            HoldingsBlock(holdingsView(snapshot, HoldingPurpose.INVEST), Currency.SAR, HoldingPurpose.INVEST, {}, {}, {})
        }
        png(out, "InvestHoldings-empty", 350, 300) {
            HoldingsBlock(holdingsView(HoldingsSnapshot(Currency.SAR, emptyList(), false), null), Currency.SAR, null, {}, {}, {})
        }
        val sheet: @Composable (HoldingFormText?) -> Unit = { form ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp)) {
                AssetAddContent(form, null, Currency.SAR, 4_548_000, null, false, {}, {}, {}, {})
            }
        }
        png(out, "AssetAddSheet", 390, 900) { sheet(null) }
        png(out, "AssetAddSheet-gold", 390, 900) { sheet(HoldingFormText(HoldingType.GOLD).with(HoldingField.GRAMS, "120")) }
        val slider = planSliderFor(Currency.SAR)
        val ui = categoryUi(snapshot, HoldingGroup.METAL, rates)
        val projection = ProjectHoldingGroup().project(ui.startMinor, slider.defaultMinor, HoldingGroup.METAL, rates, 10)
        val v = CategoryView(ui, Currency.SAR, 2026, 10, projection, slider, slider.defaultMinor)
        val a = CategoryActions({}, {}, {}, {}, {}, {}, {})
        png(out, "AssetCategory", 390, 1150) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CategoryHeader(v, a); CategoryChartCard(v, a); CategoryList(v, a, null); CategoryPlanCard(v, a); CategorySource(v)
            }
        }
        val other = CategoryView(categoryUi(snapshot, HoldingGroup.PERSONAL, rates), Currency.SAR, 2026, 10, null, slider, slider.defaultMinor)
        png(out, "AssetCategory-personal", 390, 420) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CategoryHeader(other, a); CategoryChartCard(other, a); CategoryList(other, a, null)
            }
        }
        assertTrue(File(out, "AssetCategory.png").isFile)
    }
}
