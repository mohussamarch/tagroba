package app.masroufy.wiring

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.LocalMoment
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.Texts
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.emptyProfile
import app.masroufy.ui.screens.investment.AdvisorLink
import app.masroufy.ui.screens.investment.FactAnswer
import app.masroufy.ui.screens.investment.OutcomeChip
import app.masroufy.ui.screens.investment.factsSummary
import app.masroufy.ui.screens.investment.loadAdvisor
import app.masroufy.ui.screens.investment.loadZakat
import app.masroufy.ui.screens.investment.loadZakatPay
import app.masroufy.ui.screens.investment.zakatPrices
import app.masroufy.usecase.NewAsset
import app.masroufy.usecase.PurchaseInput
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «الزكاة» (+ الوقائع ودفع السنة) و«التحليلات الذكية»: من حالات الاستخدام لشكل الشاشة على التجميع الحقيقي. المطلوب والنصاب والسطور
 * **زي ما الحساب رجّعهم**، والناقص «غير متاح». الأسماء والأرقام مخترعة.
 */
class ZakatAdvisorScreensTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun bank(currency: Currency) = Wallet("w-bank", "بنك وهمي", currency, "bank", 3_000_000, "2025-01-01")

    @Test fun hiddenWhenIslamicContentIsOff() = runBlocking<Unit> {
        val w = InvestmentWorld(InvestmentWorld.SAUDI, profile = emptyProfile().copy(islamicContentVisible = false))
        val load = loadZakat(w.deps, w.space)
        assertFalse(load.visible)
        assertNull(load.ui)
    }

    @Test fun suggestedDayThenConfirmedYearThenAMissingFactThenClosedAndPaid() = runBlocking<Unit> {
        val w = InvestmentWorld(InvestmentWorld.SAUDI, wallets = listOf(bank(Currency.SAR)))
        val first = loadZakat(w.deps, w.space)
        val ui = assertNotNull(first.ui)
        assertEquals("المرجع: هيئة الزكاة والضريبة والجمارك", ui.reference)
        assertNull(ui.scopeNote, "بلد واحدة ⇒ من غير «على فلوسك في البلد دي بس»")
        assertFalse(ui.hasYear)
        assertNull(ui.chip)
        assertNull(ui.heroMinor, "من غير سنة مؤكدة مفيش مطلوب (مش صفر)")
        assertTrue(ui.hawl.canConfirm, "اليوم مقترح من سلسلة الرصيد")
        assertFalse(ui.hawl.confirmed)
        assertNotNull(ui.hawl.dateLine)
        assertNull(first.pay)

        val suggestion = assertNotNull(first.data?.suggestion)
        w.deps.zakat.confirmDate(suggestion.hawlStart)
        val confirmed = loadZakat(w.deps, w.space)
        val c = assertNotNull(confirmed.ui)
        val a = assertNotNull(confirmed.data?.assessment)
        assertTrue(c.hasYear)
        assertTrue(c.hawl.confirmed)
        assertFalse(c.hawl.canConfirm)
        assertEquals(OutcomeChip.DUE, c.chip)
        assertEquals(a.dueMinor, c.heroMinor, "المطلوب من الحساب نفسه")
        assertEquals(75_000L, c.heroMinor, "٢٫٥٪ من ٣٠٬٠٠٠")
        assertEquals(a.nisabMinor, c.nisabMinor)
        assertEquals(208_250L, c.nisabMinor, "الأقل: فضة ٥٩٥ جم × ٣٫٥٠")
        assertEquals(a.lines.map { it.dueMinor }, c.lines.map { it.dueMinor })
        assertTrue(c.canClose)

        // ذهب من غير «لبس ولا ادخار» ⇒ واقعة ناقصة ⇒ «تنقصها واقعة» والمطلوب «غير متاح» والتثبيت مقفول
        val bar = w.deps.assets.addAsset(NewAsset("سبيكة وهمية", "gold", "جرام", currency = Currency.SAR))
        w.deps.assets.recordPurchase(PurchaseInput(bar.id, "2025-01-05", 100 * QUANTITY_SCALE, 2_500_000, 0))
        val partial = loadZakat(w.deps, w.space)
        val p = assertNotNull(partial.ui)
        assertEquals(OutcomeChip.PARTIAL, p.chip)
        assertNull(p.heroMinor)
        assertFalse(p.canClose)
        val q = partial.questions.first { it.subjectId == bar.id && it.missing }
        assertEquals("ينقص ١", factsSummary(partial.questions).chip)
        assertTrue(factsSummary(partial.questions).missing)
        val saving = q.options.first { it.answer == FactAnswer.Purpose(ZakatPurpose.SAVING) }
        assertFalse(saving.selected)

        // الواقعة اللي بعدها (العيار) بتتسأل لما الحساب يطلبها — مش قبل كده
        w.deps.zakat.setAssetFacts(bar.id, purpose = ZakatPurpose.SAVING)
        val karat = loadZakat(w.deps, w.space)
        assertEquals(OutcomeChip.PARTIAL, karat.ui?.chip)
        val askKarat = karat.questions.single { it.subjectId == bar.id && it.missing }
        assertTrue(askKarat.options.any { it.answer == FactAnswer.Karat(21) })
        assertTrue(karat.questions.first { it.subjectId == bar.id }.options.first { it.answer == FactAnswer.Purpose(ZakatPurpose.SAVING) }.selected)
        w.deps.zakat.setAssetFacts(bar.id, karat = 21)
        val answered = loadZakat(w.deps, w.space)
        assertEquals(OutcomeChip.DUE, answered.ui?.chip)
        assertFalse(factsSummary(answered.questions).missing)

        // التثبيت ⇒ السنة الجاية بتتفتح ودفع المتثبّتة بيظهر تحت
        val year = assertNotNull(answered.data?.openYear)
        w.deps.zakat.close(year.id, w.env.today(), zakatPrices(w.deps))
        val closed = loadZakat(w.deps, w.space)
        val pay = assertNotNull(closed.pay)
        assertEquals(year.id, pay.yearId)
        val status = w.deps.payZakat.status(year.id)
        assertEquals(status.dueMinor, pay.dueMinor)
        assertEquals(0L, pay.paidMinor)
        assertNull(pay.allPaidText)
        assertEquals(status.lines.map { it.kind }, pay.lines.map { it.kind })

        w.deps.payZakat.payCash(year.id, null, status.dueMinor, w.env.today())
        val paid = loadZakatPay(w.deps, w.space, year.id)
        assertEquals(status.dueMinor, paid.paidMinor)
        assertEquals(0L, paid.leftMinor)
        assertEquals("دُفعت زكاة هذه السنة كاملة.", paid.allPaidText)
        assertTrue(paid.lines.all { it.done })
        assertEquals(1, paid.payments.size)
    }

    @Test fun egyptZakatSpeaksEgyptianAndHasNoPricesWithoutTheFile() = runBlocking<Unit> {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val w = InvestmentWorld(InvestmentWorld.EGYPT, wallets = listOf(bank(Currency.EGP)), withPrices = false)
        val load = loadZakat(w.deps, w.space)
        val ui = assertNotNull(load.ui)
        assertEquals("المرجع: دار الإفتاء المصرية", ui.reference)
        assertNull(ui.pricesLine, "مفيش ملف أسعار ⇒ مفيش سطر أسعار مخترع")
        assertNull(load.data?.suggestion, "من غير سعر مفيش نصاب ⇒ مفيش يوم مقترح")
        assertEquals("أكّد يوم زكاتك الأول عشان سطور السنة تظهر.", ui.heroSub)
    }

    @Test fun advisorCardsComeFromTheAlertInboxAndTheGroupSwitch() = runBlocking<Unit> {
        val w = InvestmentWorld(InvestmentWorld.SAUDI)
        val quiet = loadAdvisor(w.deps)
        assertTrue(quiet.enabled)
        assertTrue(quiet.quiet, "مفيش تنبيهات ⇒ «المساعد ساكت الآن» من غير كروت مخترعة")

        val near = AlertCandidate(AlertKind.GOAL_NEAR, "goal:g-1", "خطة وهمية قاربت على الاكتمال", "جمعت 2,760.00 من 3,000.00")
        val big = AlertCandidate(AlertKind.BIG_ONE, "big:t-1", "عملية كبيرة", "عملية 2,300.00")
        w.deps.alerts.run(listOf(near, big), LocalMoment("2026-10-09", 9))
        val ui = loadAdvisor(w.deps)
        assertFalse(ui.quiet)
        val goal = ui.cards.first { it.threadKey == "goal:g-1" }
        assertEquals("خطة وهمية قاربت على الاكتمال", goal.title)
        assertTrue(goal.good)
        assertEquals(AdvisorLink.GOALS, goal.link)
        assertTrue(goal.why.isNotBlank(), "«لماذا؟» = سبب المحرك")
        assertEquals(AdvisorLink.OPS, ui.cards.first { it.threadKey == "big:t-1" }.link)

        w.deps.alerts.setGroupEnabled(AlertGroup.ADVISOR, false)
        val off = loadAdvisor(w.deps)
        assertFalse(off.enabled)
        assertTrue(off.cards.isEmpty())
        assertFalse(off.quiet, "مقفول مش «ساكت»")
    }
}
