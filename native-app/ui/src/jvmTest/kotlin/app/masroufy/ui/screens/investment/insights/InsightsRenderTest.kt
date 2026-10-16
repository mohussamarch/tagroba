package app.masroufy.ui.screens.investment.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.VERIFIED_TIPS
import app.masroufy.core.IncomeRope
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.RopeJob
import app.masroufy.core.RopeKnot
import app.masroufy.core.RopeStrand
import app.masroufy.core.TipGroup
import app.masroufy.core.Texts
import app.masroufy.core.reinvest
import app.masroufy.ui.screens.more.FaqCard
import app.masroufy.ui.screens.more.faqItems
import app.masroufy.ui.text.t
import app.masroufy.usecase.ReturnsRowView
import app.masroufy.usecase.ReturnsView
import app.masroufy.usecase.TipGroupView
import app.masroufy.usecase.TipLine
import app.masroufy.usecase.TipView
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * أقسام INSIGHTS مرسومة بـCompose نفسه لصور PNG (بس لو `INSIGHTS_PNG_DIR` متظبط) — للمقارنة بصور النموذج (`masroufy-design-shots`).
 * عرض 350 زي معاينة النموذج، كثافة 2. بيانات مخترعة بس. محتاج skiko الأصلية لويندوز (اختبار بس — ARCHITECTURE §31.40).
 */
class InsightsRenderTest {
    private val page = Color(0xFFEFF3ED)

    private fun rope() = IncomeRope(
        "2025-10-16", "2026-10-16",
        listOf(
            RopeStrand(
                IncomeSourceKind.JOB, "2025-10-16", "2026-10-16", true, "شركة النخلة للحلول", "2023-02-01", null,
                listOf(RopeJob("مجموعة الواحة الوهمية", "2023-02-01", "2026-04-27"), RopeJob("شركة النخلة للحلول", "2026-04-28", null)),
            ),
            RopeStrand(IncomeSourceKind.PART_TIME, "2026-08-10", "2026-10-16", true, "مكتب تصميم مخترع", "2026-08-10", null),
            RopeStrand(IncomeSourceKind.CLIENT, "2026-01-01", "2026-06-30", false, "متجر عطور مخترع", "2026-01-01", "2026-06-30"),
        ),
        listOf("2026-04-28"), listOf(RopeKnot("2026-03-14", "جوال قديم")),
    )

    private fun returns(withMath: Boolean) = ReturnsView(
        Currency.SAR, listOf(ReturnsRowView("توزيعات الأسهم", "مرتين: مارس وسبتمبر", 267_000)), 267_000,
        if (withMath) reinvest(267_000, 10, 795, 174) else null,
    )

    private fun tipsGroups() = TipGroup.entries.map { g ->
        TipGroupView(t(g.title), VERIFIED_TIPS.filter { it.group == g && it.fitsCountry("SA") }.map {
            val w = it.words(Texts.arabicVariant, Texts.language)
            TipLine(it.id, w.title, w.text, t(InvestInsightKey.TIP_SOURCE, it.source.name))
        })
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(out: File, name: String, height: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(700, height * 2, Density(2f)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(page).padding(16.dp)) { content() }
            }
        }
        try {
            File(out, "$name.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rendersInsightSectionsToPng() {
        val out = System.getenv("INSIGHTS_PNG_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        out.mkdirs()
        for (variant in ArabicVariant.entries) {
            Texts.arabicVariant = variant
            val v = variant.name.lowercase()
            val spread = VERIFIED_TIPS.single { it.id == "no-guarantee-diversify" }
            val w = spread.words(Texts.arabicVariant, Texts.language)
            val tip = TipView(spread.id, t(InvestInsightKey.TIP_WHY_SPREAD), w.title, w.text, t(InvestInsightKey.TIP_SOURCE, spread.source.name))
            render(out, "tip-$v", 330) { TipCard(tip, {}, {}) }
            render(out, "rope-$v", 470) { IncomeRopeCard(rope()) {} }
            render(out, "rope-none-$v", 160) { IncomeRopeCard(null) {} }
            render(out, "returns-$v", 380) { ReturnsCard(returns(true)) }
            render(out, "returns-na-$v", 380) { ReturnsCard(returns(false)) }
            render(out, "returns-none-$v", 140) { ReturnsCard(null) }
            render(out, "faq-$v", 640) { Column { FaqCard(faqItems("SA")) } }
            render(out, "tips-$v", 3600) {
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { tipsPage(tipsGroups()) }
            }
        }
        Texts.arabicVariant = ArabicVariant.MSA
        assertTrue(File(out, "tip-msa.png").isFile)
    }
}
