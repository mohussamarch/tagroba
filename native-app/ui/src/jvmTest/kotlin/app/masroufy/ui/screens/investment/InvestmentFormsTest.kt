package app.masroufy.ui.screens.investment

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Asset
import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.Texts
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.hijriOf
import app.masroufy.usecase.FeedState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * خانات شاشات «الاستثمار» (لوحة الشراء والبيع · «الصورة كاملة» · دفع الزكاة · يوم الحول) وجمل العدّ: **قراية بس** لمدخل حالة الاستخدام —
 * من غير حساب فلوس، والكسر الزيادة بيترفض بدل ما يتقرّب بصمت. الأسماء والأرقام مخترعة.
 */
class InvestmentFormsTest {
    private val g = QUANTITY_SCALE

    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun tradeSheetReadsTheFieldsIntoTheUseCaseInput() {
        val buy = tradeRequest(TradeMode.BUY, "a-1", Currency.SAR, TradeForm(qty = "١٢٠", amount = "4,800.50", fee = "32", day = "2026-10-09", linkId = "t-9"))
        val input = assertIs<TradeRequest.Buy>(buy).input
        assertEquals(120 * g, input.quantity)
        assertEquals(480_050L, input.principalMinor)
        assertEquals(3_200L, input.feeMinor)
        assertEquals("t-9", input.transactionId)
        assertEquals("2026-10-09", input.purchasedAt)

        val sell = assertIs<TradeRequest.Sell>(tradeRequest(TradeMode.SELL, "a-1", Currency.SAR, TradeForm(qty = "0.5", amount = "200", day = "2026-10-08")))
        assertEquals(g / 2, sell.input.quantity)
        assertNull(sell.input.feeMinor, "الرسوم فاضية ⇒ مش صفر مخترع (حالة الاستخدام بتعتبرها صفر)")

        assertEquals(TradeRequest.Invalid("اكتب الكمية."), tradeRequest(TradeMode.BUY, "a-1", Currency.SAR, TradeForm(amount = "10")))
        assertEquals(TradeRequest.Invalid("اكتب المبلغ المدفوع."), tradeRequest(TradeMode.BUY, "a-1", Currency.SAR, TradeForm(qty = "1")))
        assertIs<TradeRequest.Invalid>(tradeRequest(TradeMode.BUY, "a-1", Currency.SAR, TradeForm(qty = "1", amount = "10.555")), "أكتر من رقمين بعد العلامة ⇒ مرفوض مش متقرّب")
        assertEquals(TradeRequest.Invalid("اكتب سعرًا أكبر من صفر."), tradeRequest(TradeMode.PRICE, "a-1", Currency.SAR, TradeForm(price = "0")))
        assertEquals(TradeRequest.Price(43_800), tradeRequest(TradeMode.PRICE, "a-1", Currency.SAR, TradeForm(price = "438")))

        val add = assertIs<TradeRequest.Add>(tradeRequest(TradeMode.ADD, null, Currency.EGP, TradeForm(name = "دهب وهمي", kind = "gold", unit = " ")))
        assertEquals(Currency.EGP, add.input.currency)
        assertNull(add.input.unitLabel, "وحدة فاضية ⇒ الافتراضية لنوعها من حالة الاستخدام")

        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals(TradeRequest.Invalid("اكتب دفعت كام."), tradeRequest(TradeMode.BUY, "a-1", Currency.EGP, TradeForm(qty = "1")))
    }

    @Test fun projectionDraftReadsRentRateAndVacantMonths() {
        val flat = Asset("a-flat", "شقة وهمية", "other", "وحدة", Currency.SAR, false, realEstate = true)
        val ok = assertIs<DraftParse.Ok>(parseDraft(
            ProjectionDraft(RealEstateValuation.AREA, area = "120", sqm = "4,800", rent = "4000", increase = "1.5", vacant = "١", rate = "1.74"), flat, Currency.SAR,
        ))
        assertEquals(RealEstateValuation.AREA, ok.input.valuation)
        assertEquals(120 * g, ok.input.areaSqm)
        assertEquals(480_000L, ok.input.pricePerSqmMinor)
        assertEquals(400_000L, ok.input.rent.monthlyMinor)
        assertEquals(150, ok.input.rent.yearlyIncreaseBp)
        assertEquals(1, ok.input.rent.vacantMonthsPerYear)
        assertEquals(174, ok.typedRateBp)
        assertEquals(174, ok.input.expectedRateBp)

        val empty = assertIs<DraftParse.Ok>(parseDraft(ProjectionDraft(RealEstateValuation.WHOLE, whole = "576000"), flat, Currency.SAR))
        assertNull(empty.typedRateBp)
        assertNull(empty.input.rent.monthlyMinor, "الخانة الفاضية فاضية — حالة الاستخدام بتعتبرها صفر")
        assertEquals(57_600_000L, empty.wholeMinor)

        assertEquals(DraftParse.Invalid("الأشهر الفارغة في السنة من 0 إلى 12"), parseDraft(ProjectionDraft(vacant = "13"), flat, Currency.SAR))
        assertEquals(DraftParse.Invalid("في إحدى الخانات قيمة ليست رقمًا."), parseDraft(ProjectionDraft(rent = "abc"), flat, Currency.SAR))
        assertEquals(DraftParse.Invalid("في إحدى الخانات قيمة ليست رقمًا."), parseDraft(ProjectionDraft(rate = "99999999999"), flat, Currency.SAR))
    }

    @Test fun zakatPaymentReadsTheLinesAndHowItWasPaid() {
        val lines = setOf(ZakatLineKind.entries.last(), ZakatLineKind.entries.first())
        assertEquals(ZakatPayRequest.Invalid("اختر سطرًا واحدًا على الأقل من سطور هذه السنة."), zakatPayRequest(emptySet(), PAY_CASH, "10", Currency.SAR))
        assertEquals(ZakatPayRequest.Invalid("اختر العملية التي دفعت بها، أو «دفعتها كاشًا»."), zakatPayRequest(lines, null, "", Currency.SAR))
        assertEquals(ZakatPayRequest.Invalid("اكتب المبلغ الذي دفعته كاشًا."), zakatPayRequest(lines, PAY_CASH, "", Currency.SAR))
        assertEquals(ZakatPayRequest.Invalid("اكتب المبلغ الذي دفعته كاشًا."), zakatPayRequest(lines, PAY_CASH, "-5", Currency.SAR))
        val cash = assertIs<ZakatPayRequest.Cash>(zakatPayRequest(lines, PAY_CASH, "1,200.50", Currency.SAR))
        assertEquals(120_050L, cash.amountMinor)
        assertEquals(listOf(ZakatLineKind.entries.first(), ZakatLineKind.entries.last()), cash.lines, "بترتيب سطور السنة مش بترتيب الاختيار")
        assertEquals(ZakatPayRequest.FromOperation(cash.lines, "t-1"), zakatPayRequest(lines, "t-1", "", Currency.SAR))
    }

    @Test fun countsAndDatesInSentences() {
        assertEquals(listOf("اليوم", "غدًا", "بعد يومين", "بعد 5 أيام", "بعد 24 يومًا", "مضى موعدها"), listOf(0, 1, 2, 5, 24, -1).map(::relativeDays))
        assertEquals(listOf("خطة واحدة", "خطتان", "3 خطط", "12 خطة"), listOf(1, 2, 3, 12).map(::goalsHint))
        assertEquals("1.74%", pctText(174))
        assertEquals("−2.5%", pctText(-250))
        assertEquals("16%", pctText(1600))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals(listOf("النهارده", "بكرة", "كمان يومين", "كمان 24 يوم"), listOf(0, 1, 2, 24).map(::relativeDays))
        assertEquals("خطتين", goalsHint(2))
        Texts.language = Language.EN
        assertEquals("1.74%", pctText(174))
        assertEquals("No plans yet", goalsHint(0))
    }

    @Test fun hawlDaySheetStartsOnTheSuggestedHijriDay() {
        val due = "2026-10-20"
        val h = hijriOf(due)
        val ui = hawlDayUi("2026-10-09", due)
        assertEquals(h.month, ui.month)
        assertEquals(h.day, ui.day)
        assertEquals("يوم ثابت كل سنة هجرية — المقترح من رصيدك ${hijriDayMonth(h.month, h.day)}", ui.intro)
        val none = hawlDayUi("2026-10-09", null)
        assertEquals(hijriOf("2026-10-09").month, none.month, "مفيش مقترح ⇒ يبدأ من النهارده")
    }

    @Test fun feedProblemOnlyWhenTheFileDidNotArrive() {
        assertNull(feedProblem(null))
        assertEquals("السبب الوهمي", feedProblem(FeedState.Unavailable("السبب الوهمي")))
        assertEquals("المعروض بأسعار آخر تحديث ناجح.", feedProblem(FeedState.Ready(Unit, "2026-10-08T05:00:00Z", fromNetworkNow = false, refreshFailed = "مفيش نت")))
        assertNull(feedProblem(FeedState.Ready(Unit, "2026-10-09T05:00:00Z", fromNetworkNow = true)))
    }
}
