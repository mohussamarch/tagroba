package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.AssistEntity
import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistIntentKind
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistUnderstandContext
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.dayBefore
import app.masroufy.core.formatMoney
import app.masroufy.core.periodForDate
import app.masroufy.core.uiText
import app.masroufy.core.understandAssist
import app.masroufy.core.walletBalancesOn
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «لا رقم بلا مصدر» (القاعدة 10): كل مبلغ في رد المساعد **هو هو** رقم حالة الاستخدام اللي تحته — بنقارن [AssistReply.amounts] بنتيجة
 * حالة الاستخدام نفسها على نفس بيانات الذاكرة. المصدر الناقص ⇒ «غير متاح» من غير أي رقم. بيانات مخترعة (`AssistantWorld`).
 */
class AssistantAnswersTest {
    @BeforeTest fun msa() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val w = AssistantWorld()
    private val lex = runBlocking { w.deps.lexicon.load() }
    private val req = LoadHomeScreenRequest(w.period, w.today, 28, includeHistory = false)

    private suspend fun ask(text: String, sources: AssistantSources = w.sources): Pair<AssistUnderstanding, AssistReply> {
        val u = understandAssist(text, AssistUnderstandContext(lex))
        return u to AssistantAnswers(sources).answer(u, w.ctx())
    }

    @Test fun spendTotalIsTheHomeExpense() = runBlocking<Unit> {
        val (u, r) = ask("كم صرفت هذا الشهر؟")
        assertEquals(AssistIntent.SPEND_TOTAL, u.intent)
        assertEquals(w.home.load(req).expenseMinor, r.amounts.first())
        assertTrue(r.links.any { it.screen == AssistScreen.OPERATIONS })
    }

    @Test fun categorySpendAndItsCapComeFromTheirUseCases() = runBlocking<Unit> {
        val (u, r) = ask("صرفت كام على القهوة الشهر ده؟")
        assertEquals(AssistIntent.SPEND_CATEGORY, u.intent)
        val spend = w.categorySpend.load(w.period.start, w.today, setOf(w.coffeeId)).amountMinor
        assertEquals(8_500L, spend, "٢٥ + ٢٥ + ٢٥ + ١٠ في الشهر الحالي")
        val line = w.budget.load(LoadBudgetScreenRequest(w.period, w.today, 28)).lines.first { it.categoryId == w.coffeeId }.status!!
        assertEquals(listOf(spend!!, line.limitMinor), r.amounts)
        assertTrue(r.text.contains("85"), r.text)
        assertEquals(w.coffeeId, r.links.first { it.screen == AssistScreen.CATEGORY_BUDGET }.args["categoryId"])
    }

    @Test fun merchantSpendAndLastVisit() = runBlocking<Unit> {
        val (_, r) = ask("كم صرفت عند المرسى هذا الشهر")
        assertEquals(listOf(w.categorySpend.byMerchant(w.period.start, w.today, w.marsa).amountMinor!!), r.amounts)
        val (u2, last) = ask("آخر مرة في المرسى؟")
        assertEquals(AssistIntent.LAST_AT_MERCHANT, u2.intent)
        val found = FindLastAtMerchant(w.txns, w.wallets).find(w.marsa, w.today)!!
        assertEquals(listOf(found.transaction.amountMinor), last.amounts)
        assertEquals("t-c4", last.links.single().args["transactionId"])
    }

    @Test fun remainingUsesTheBudgetLineAndHomeAllowance() = runBlocking<Unit> {
        val (u, r) = ask("فاضلي كام؟")
        assertEquals(AssistIntent.REMAINING, u.intent)
        val st = w.budget.load(LoadBudgetScreenRequest(w.period, w.today, 28)).totalStatus!!
        val allowance = w.home.load(req).allowance.amountMinor!!
        assertEquals(listOf(st.remainingMinor, st.limitMinor, allowance), r.amounts)
    }

    @Test fun budgetStatusAndCategoryCap() = runBlocking<Unit> {
        val bd = w.budget.load(LoadBudgetScreenRequest(w.period, w.today, 28))
        val (_, status) = ask("أنا ماشي على الخطة؟")
        assertEquals(listOf(bd.totalStatus!!.spentMinor, bd.totalStatus!!.limitMinor), status.amounts)
        assertTrue(status.text.contains("قهوة"), "القهوة عند حد التنبيه: ${status.text}")
        val (_, cap) = ask("ميزانية القهوة فاضل فيها كام؟")
        val line = bd.lines.first { it.categoryId == w.coffeeId }.status!!
        assertEquals(listOf(line.spentMinor, line.limitMinor, line.remainingMinor), cap.amounts)
    }

    @Test fun cashAndNamedWalletBalances() = runBlocking<Unit> {
        val (_, r) = ask("معايا كام كاش؟")
        val c = w.cashSummary.load(w.period, w.today)!!
        assertEquals(listOf(c.balanceMinor, c.spentInPeriodMinor), r.amounts)
        val (u2, bank) = ask("رصيد الراجحي كام")
        assertEquals(AssistIntent.ON_HAND, u2.intent)
        val expected = walletBalancesOn(listOf(w.bank), w.txns.listByDateRange(w.bank.openingAt, w.today), w.today)[w.bank.id]
        assertEquals(listOf(expected!!), bank.amounts)
    }

    @Test fun incomeCompareAndBiggestMatchHome() = runBlocking<Unit> {
        val h = w.home.load(req)
        assertEquals(listOf(h.incomeMinor!!), ask("دخلي كام الشهر ده؟").second.amounts)
        val previous = periodForDate(dayBefore(w.period.start), 28)
        val before = LoadHomeHistory(LoadHomeHistoryDeps(w.txns, w.allocations, w.catRepo)).load(w.period, 28, h).first { it.period.key == previous.key }.expenseMinor!!
        val (u, cmp) = ask("صرفت أكتر من الشهر اللي فات؟")
        assertEquals(AssistIntent.SPEND_COMPARE, u.intent)
        assertEquals(listOf(h.expenseMinor!!, before, h.expenseMinor!! - before), cmp.amounts)
        val (_, top) = ask("صرفت أكتر على إيه الشهر ده؟")
        assertEquals(h.distribution.filter { it.categoryId != null && it.amountMinor > 0 }.take(3).map { it.amountMinor }, top.amounts)
    }

    @Test fun salaryFromStoredSourceNeverAveraged() = runBlocking<Unit> {
        val (_, next) = ask("إمتى الراتب؟")
        assertTrue(next.amounts.isEmpty())
        assertTrue(next.text.contains("28"), next.text)
        assertEquals(listOf(1_000_000L), ask("راتبي كام؟").second.amounts)
        assertEquals(listOf(38_000L), ask("الكهربا بتيجي كام عادة؟").second.amounts)
    }

    /** بلد من غير راتب (مصر مثلًا): بيقول كده بصراحة ويعرض دخل البلد بمواعيده. */
    @Test fun noActiveSalaryIsSaidHonestly() = runBlocking<Unit> {
        val egypt = AssistantWorld(salaried = false)
        val u = understandAssist("إمتى الراتب؟", AssistUnderstandContext(egypt.deps.lexicon.load()))
        val r = AssistantAnswers(egypt.sources).answer(u, egypt.ctx())
        assertTrue(r.text.startsWith(uiText(TextKey.ASSIST_NO_SALARY, "السعودية")), r.text)
        assertTrue(r.text.contains("إيجار الشقة"), r.text)
        assertTrue(r.amounts.isEmpty())
    }

    @Test fun personWithoutDebtsSaysSoWithoutANumber() = runBlocking<Unit> {
        val (u, r) = ask("كم لي عند خالد")
        assertEquals(AssistIntent.PERSON_BALANCE, u.intent)
        assertEquals(uiText(TextKey.ASSIST_PERSON_NONE, "خالد"), r.text)
        assertTrue(r.amounts.isEmpty())
    }

    /** مصدر ناقص ⇒ «غير متاح» من غير رقم (مش صفر). */
    @Test fun missingSourceIsUnavailableNeverZero() = runBlocking<Unit> {
        val bare = AssistantSources(w.wallets, w.txns)
        listOf("زكاتي كام؟", "أصولي بكام؟", "فاضل كام قسط؟", "مين عليه فلوس ليّا؟", "كم صرفت هذا الشهر؟", "فاضلي كام؟", "معايا كام كاش؟").forEach { q ->
            val (_, r) = ask(q, bare)
            assertTrue(r.amounts.isEmpty(), "$q ⇒ ${r.text}")
            assertTrue(r.text.startsWith(uiText(TextKey.ASSIST_NA)) || r.text.contains(uiText(TextKey.ASSIST_NA).trimEnd('.')), "$q ⇒ ${r.text}")
        }
    }

    /** كل نية بيانات بترد بنص، وكل مبلغ في الرد مكتوب في النص زي ما اتنسق — في الفصحى والمصري والإنجليزي. */
    @Test fun everyDataIntentAnswersAndEveryAmountIsInTheText() = runBlocking<Unit> {
        val subjects = mapOf(
            AssistIntent.SPEND_CATEGORY to AssistEntity(AssistEntityType.CATEGORY, w.coffeeId, "قهوة", -1, -1),
            AssistIntent.CATEGORY_BUDGET to AssistEntity(AssistEntityType.CATEGORY, w.coffeeId, "قهوة", -1, -1),
            AssistIntent.SPEND_MERCHANT to AssistEntity(AssistEntityType.MERCHANT, w.marsa.id, "مقهى المرسى", -1, -1),
            AssistIntent.LAST_AT_MERCHANT to AssistEntity(AssistEntityType.MERCHANT, w.marsa.id, "مقهى المرسى", -1, -1),
            AssistIntent.SPEND_PERSON to AssistEntity(AssistEntityType.PERSON, "p-ahmed", "أحمد", -1, -1),
            AssistIntent.PERSON_BALANCE to AssistEntity(AssistEntityType.PERSON, "p-ahmed", "أحمد", -1, -1),
        )
        for (variant in listOf(ArabicVariant.MSA, ArabicVariant.EGYPTIAN, null)) {
            if (variant == null) Texts.language = Language.EN else { Texts.language = Language.AR; Texts.arabicVariant = variant }
            AssistIntent.entries.filter { it.kind == AssistIntentKind.DATA }.forEach { intent ->
                val u = understandAssist("كم", AssistUnderstandContext(lex)).copy(intent = intent, subject = subjects[intent])
                val r = AssistantAnswers(w.sources).answer(u, w.ctx())
                assertTrue(r.text.isNotBlank(), "$intent")
                r.amounts.forEach { a -> assertTrue(r.text.contains(formatMoney(a, Currency.SAR)) || r.text.contains(formatMoney(-a, Currency.SAR)), "$intent: $a في «${r.text}»") }
            }
        }
    }
}
