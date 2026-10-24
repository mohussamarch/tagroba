package app.masroufy.ui.screens.dues

import app.masroufy.core.BnplKey
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplProvider
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuesTotals
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.bnplPreview
import app.masroufy.core.bnplSchedule
import app.masroufy.core.bnplSteps
import app.masroufy.core.dueProgress
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.shell.Toaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.MasroufyTheme
import app.masroufy.usecase.BnplAnswer
import app.masroufy.usecase.BnplAsk
import app.masroufy.usecase.BnplPlanView
import app.masroufy.usecase.DueLinkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * جولة إصلاح «ادفع بعدين» (r051-f) على الشاشة نفسها (`ImageComposeScene` بكثافة 1 ⇒ الإحداثيات بالـdp): «خطتك» بتتبع الخصم الجاي (F1) ·
 * كارت المشترى بيفتح تفاصيل الخطة (F3) · اللوحة بتستنى «تراجع» (F5) · «تخطّي» ما بيبعتش رد (F7) · السطر تحت المبلغ (F8) · المفتاح لقارئ
 * الشاشة (F10) · غلط الربط بيظهر (F2). كل الأسامي والأرقام مخترعة.
 */
@OptIn(ExperimentalComposeUiApi::class)
class BnplFixRoundUiTest {
    private val today = "2026-10-07"
    private val cats = listOf("التسوق", "إلكترونيات وأجهزة").mapIndexed { i, n -> Category("c$i", null, n, "box", "#111111", "#222222", true, i) }

    private fun charge(id: String, minor: Long, date: String, merchant: String) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = merchant,
    )

    private fun ask(id: String, minor: Long, date: String, provider: BnplProvider) =
        BnplAsk(charge(id, minor, date, provider.brand), provider, BnplMatch.NewPurchase, emptyList())

    /** إطار اللوحة: تحت خالص بحواف اللوحة الحقيقية، واتجاه عربي ثابت (الزرار الهادي على الشمال). */
    @Composable
    private fun SheetFrame(content: @Composable ColumnScope.() -> Unit) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp), content = content)
            }
        }
    }

    private fun scene(height: Int, content: @Composable () -> Unit) =
        ImageComposeScene(390, height, Density(1f)) { MasroufyTheme(reduceMotion = true) { content() } }

    private fun ImageComposeScene.tap(x: Float, y: Float) {
        sendPointerEvent(PointerEventType.Press, Offset(x, y))
        sendPointerEvent(PointerEventType.Release, Offset(x, y))
        render()
    }

    @Test fun planPreviewFollowsTheNextChargeWhileTheSheetStaysOpen() {
        val chips = bnplCategoryChips(cats)
        val first = BnplSheetState(ask("t-a", 25_000, "2026-10-07", BnplProvider.TABBY), chips)
        val second = BnplSheetState(ask("t-b", 40_000, "2026-10-09", BnplProvider.TAMARA), chips)
        val holder = mutableStateOf(first)
        val seen = mutableListOf<Long>()
        val scene = scene(1100) {
            SheetFrame {
                val s = holder.value
                val t0 = s.ask.transaction
                BnplSheetContent(s, today, chips, { n, c, _ -> seen += t0.amountMinor; bnplPreview(t0.amountMinor, Currency.SAR, t0.occurredAt, n, c) }, 2000.dp, {}, {}, {})
            }
        }
        try {
            scene.render()
            assertEquals(listOf(25_000L), seen.distinct())
            // نفس الافتراضات (4 · كل شهر) للخصم الجاي ⇒ «خطتك» لازم تتحسب تاني بأرقامه هو
            holder.value = second
            Snapshot.sendApplyNotifications()
            scene.render()
            assertTrue(40_000L in seen, "preview recomputed for the next charge: $seen")
        } finally {
            scene.close()
        }
    }

    @Test fun skipOnlyClosesTheSheetAndSendsNoAnswer() {
        val s = BnplSheetState(ask("t-a", 25_000, "2026-10-07", BnplProvider.TABBY), bnplCategoryChips(cats))
        val answers = mutableListOf<BnplAnswer>()
        var later = 0
        val scene = scene(1100) {
            SheetFrame { BnplSheetContent(s, today, bnplCategoryChips(cats), { n, c, _ -> bnplPreview(25_000, Currency.SAR, today, n, c) }, 2000.dp, {}, { answers += it }, { later++ }) }
        }
        try {
            scene.render()
            // «تخطّي»: آخر صف (52 ارتفاع · 24 تحت) — التلت اللي على الشمال في العربي
            scene.tap(20f + (350f - 8f) / 3f / 2f, 1100f - 24f - 26f)
            assertEquals(1, later, "«تخطّي» closes the sheet")
            assertTrue(answers.isEmpty(), "nothing is written on the operation: $answers")
        } finally {
            scene.close()
        }
    }

    @Test fun aPurchasePlanCardOpensThePlan() {
        val p = InstallmentPlan("p1", "سماعة وهمية", "Tabby", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 100_000, 100_000, 25_000, 1, "2026-09-07", remindDayBefore = true)
        val sched = bnplSchedule(p.installmentMinor, p.currency, p.firstDueAt, 4, BnplCycle.MONTHLY)
        val v = BnplPlanView(p, BnplProvider.TABBY, "إلكترونيات وأجهزة", dueProgress(sched, 50_000, today), bnplSteps(sched, 50_000))
        var opened = 0
        val scene = scene(500) { Column(Modifier.fillMaxWidth().padding(20.dp)) { BnplPlanCard(v, today, onClick = { opened++ }, onLongClick = {}) } }
        try {
            scene.render()
            scene.tap(195f, 50f)
            assertEquals(1, opened, "tapping the card opens the plan (edit · delete)")
        } finally {
            scene.close()
        }
    }

    @Test fun sheetWaitsForTheUndoToastAndLinkErrorsAreShown() {
        val toaster = Toaster()
        toaster.show("الدفعة 3 من 4 اتعلّمت اتدفعت", "تراجع") {}
        assertTrue(toaster.holdsAction)
        assertFalse(bnplSheetShown(hasAsk = true, holding = false, actionToast = toaster.holdsAction), "the undo toast is never under the veil")
        assertFalse(bnplSheetShown(hasAsk = true, holding = true, actionToast = false), "waits for a fresh read after an answer")
        assertTrue(bnplSheetShown(hasAsk = true, holding = false, actionToast = false))
        toaster.show("سُجّلت")
        assertTrue(toaster.holdsAction, "fix055c: a plain toast waits behind a fresh undo bar")
        toaster.endAction(toaster.current!!.id)
        assertFalse(toaster.holdsAction)
        assertEquals("سُجّلت", toaster.current?.text)
        assertEquals("العملية مربوطة بحاجة تانية", bnplErrorText(DueLinkError("العملية مربوطة بحاجة تانية")))
        assertNull(bnplErrorText(IllegalStateException("internal")))
    }

    @Test fun heroLineCountsTheCardsAboveAndThePurchasesApart() {
        val one = plansLine(1, 0)
        assertEquals(t(BnplKey.BNPL2_HERO_AND, one, sentenceNumber(2)), plansLine(1, 2))
        assertEquals(t(BnplKey.BNPL2_HERO_ONLY, sentenceNumber(2)), plansLine(0, 2))
        val ui = installmentsUi(emptyList(), DuesTotals(0, 0, 0, 0, 100_000, 0), today, Currency.SAR, bnplCount = 2)
        assertEquals(plansLine(0, 2), ui.plansCount)
    }

    @Test fun switchStateIsReadAsWordsNotDigits() {
        assertEquals(t(UiKey.PPL_SWITCH_ON), bnplSwitchState(true))
        assertEquals(t(UiKey.PPL_SWITCH_OFF), bnplSwitchState(false))
        assertFalse(bnplSwitchState(true).any { it.isDigit() } || bnplSwitchState(false).any { it.isDigit() })
    }
}
