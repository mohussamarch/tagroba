package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplProvider
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.bnplPreview
import app.masroufy.core.bnplSchedule
import app.masroufy.core.bnplSteps
import app.masroufy.core.dueProgress
import app.masroufy.core.BnplCycle
import app.masroufy.ui.theme.MasroufyTheme
import app.masroufy.usecase.BnplAsk
import app.masroufy.usecase.BnplOption
import app.masroufy.usecase.BnplPlanView
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * r051 — لوحة «ادفع بعدين» بحالتيها وقسم «مشتريات بالتقسيط» مرسومين PNG (بس لو `BNPL_PNG_DIR` متظبط) للمقارنة بصور التصميم
 * (`masroufy-design-shots/review-installments/3-أول-خصم.png` · `4-أنهي-مشترى.png` · `5-الأقساط.png`). نفس أرقام النموذج المخترعة
 * (القسط 250 · الخصم التاني 300 · سماعة لاسلكية 2 من 4 · ساعة رياضية 1 من 4 بـ400). النهارده 7 أكتوبر 2026 زي النموذج.
 */
class BnplPngTest {
    private val today = "2026-10-07"
    private fun charge(minor: Long) = Transaction(
        id = "t-new", occurredAt = today, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = "Tabby",
    )
    private val cats = listOf("التسوق", "إلكترونيات وأجهزة", "ملابس", "المنزل", "سفر").mapIndexed { i, n -> Category("c$i", null, n, "box", "#111111", "#222222", true, i) }

    private fun plan(id: String, name: String, provider: String, inst: Long, first: String, cat: String?) = InstallmentPlan(
        id, name, provider, InstallmentKind.PURCHASE_PLAN, Currency.SAR, inst * 4, inst * 4, inst, 1, first, categoryId = cat, remindDayBefore = true,
    )

    private fun view(p: InstallmentPlan, provider: BnplProvider, paid: Long, catName: String) = BnplPlanView(
        p, provider, catName, dueProgress(bnplSchedule(p.installmentMinor, p.currency, p.firstDueAt, 4, BnplCycle.MONTHLY), paid, today),
        bnplSteps(bnplSchedule(p.installmentMinor, p.currency, p.firstDueAt, 4, BnplCycle.MONTHLY), paid),
    )

    @OptIn(ExperimentalComposeUiApi::class)
    private fun png(out: File, name: String, heightDp: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(390 * 2, heightDp * 2, Density(2f)) {
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

    @Composable
    private fun SheetFrame(s: BnplSheetState) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color.White).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BnplSheetContent(s, today, bnplCategoryChips(cats), { n, c, _ -> bnplPreview(s.ask.transaction.amountMinor, Currency.SAR, today, n, c) }, 2000.dp, {}, {}, {})
            }
        }
    }

    @Test
    fun rendersSheetStatesAndPurchasesSection() {
        val chips = bnplCategoryChips(cats)
        assertEquals(listOf("التسوق", "إلكترونيات وأجهزة", "ملابس", "المنزل", "سفر"), chips.map { it.name }, "design chip order")
        val out = System.getenv("BNPL_PNG_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        out.mkdirs()
        val setup = BnplSheetState(BnplAsk(charge(25_000), BnplProvider.TABBY, BnplMatch.NewPurchase, emptyList(), walletName = "بنك وهمي"), chips)
        png(out, "BnplSheet-first-charge", 1040) { SheetFrame(setup) }
        val p1 = plan("p1", "سماعة لاسلكية", "Tabby", 25_000, "2026-09-07", "c1")
        val p2 = plan("p2", "طقم أواني", "Tabby", 18_000, "2026-08-20", "c3")
        val options = listOf(
            BnplOption("p1", p1.name, 25_000, Currency.SAR, 2, 4, "2026-11-07", 5_000),
            BnplOption("p2", p2.name, 18_000, Currency.SAR, 1, 3, "2026-10-20", 12_000),
        )
        val which = BnplSheetState(BnplAsk(charge(30_000), BnplProvider.TABBY, BnplMatch.AskWhich(listOf("p1", "p2")), listOf(p1, p2), options, "بنك وهمي"), chips)
        which.pick = "p1"
        png(out, "BnplSheet-which-purchase", 844) { SheetFrame(which) }
        val tamara = plan("p3", "ساعة رياضية", "Tamara", 40_000, "2026-10-01", "c0")
        png(out, "Installments-bnpl-section", 900) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BnplSectionTitle()
                BnplPlanCard(view(p1, BnplProvider.TABBY, 50_000, "إلكترونيات وأجهزة"), today, startOpen = true)
                BnplPlanCard(view(tamara, BnplProvider.TAMARA, 40_000, "التسوق"), today)
            }
        }
        assertTrue(File(out, "BnplSheet-first-charge.png").isFile)
    }
}
