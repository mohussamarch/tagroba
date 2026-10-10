package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EstimatePolicy
import app.masroufy.core.PendingAsk
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.toDayNumber
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.port.AskSource
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * عدّ «محتاجة تأكيد» (§75-15 — عقد C0): أسئلة كل الشرايح من مصادرها، الحاجة الواحدة بتتعد مرة بأدق سؤال عليها، وأسئلة العمليات
 * من الفترة المالية الحالية بس، ورسايل الصندوق المستنية كلها. + مصدر الداخل المستني (§75-1). كل المعرّفات والمبالغ مخترعة.
 */
class NeedsConfirmationTest {
    @AfterTest
    fun reset() {
        EstimatePolicy.current = EstimatePolicy.OWNER_2026_10
    }

    private val period = buildPeriod(2026, 9, 28)
    private val today = "2026-10-05"
    private val lookback = CountNeedsConfirmation.LOOKBACK_DAYS

    private fun ask(kind: AskKind, txn: String? = null, msg: String? = null, date: String? = "2026-10-01", space: String = "sa") =
        PendingAsk(kind, space, transactionId = txn, messageId = msg, date = date)

    @Test fun oneThingCountsOnceWithItsMostSpecificQuestion() = runBlocking<Unit> {
        val slices = AskSource { _, _ -> listOf(ask(AskKind.INCOMING_KIND, txn = "t-1"), ask(AskKind.DEBT_REPAYMENT, txn = "t-1")) }
        val inbox = AskSource { _, _ -> listOf(ask(AskKind.SMS_WAITING, msg = "m-1"), ask(AskKind.FOREIGN_LOCAL_AMOUNT, msg = "m-1")) }
        val found = CountNeedsConfirmation(listOf(slices, inbox)).load(today, period)
        assertEquals(2, found.total)
        assertEquals(listOf(AskKind.FOREIGN_LOCAL_AMOUNT, AskKind.DEBT_REPAYMENT), found.asks.map { it.kind }, "الأدق بيكسب، والترتيب بالأولوية")
        assertEquals(mapOf(AskKind.FOREIGN_LOCAL_AMOUNT to 1, AskKind.DEBT_REPAYMENT to 1), found.byKind)
        // نفس النتيجة مهما كان ترتيب المصادر
        assertEquals(found, CountNeedsConfirmation(listOf(inbox, slices)).load(today, period))
    }

    @Test fun transactionAsksAreTheCurrentPeriodsInboxAsksAreAll() = runBlocking<Unit> {
        val windows = mutableListOf<Pair<String, String>>()
        val source = AskSource { from, to ->
            windows += from to to
            listOf(
                ask(AskKind.LOAN_OR_SUPPORT, txn = "old", date = "2026-09-20"),
                ask(AskKind.LOAN_OR_SUPPORT, txn = "future", date = "2026-10-06"),
                ask(AskKind.LOAN_OR_SUPPORT, txn = "now", date = "2026-09-28"),
                ask(AskKind.DUE_LINK, txn = "undated", date = null),
                ask(AskKind.SMS_WAITING, msg = "old-msg", date = "2026-06-01"),
            )
        }
        val found = CountNeedsConfirmation(listOf(source)).load(today, period)
        assertEquals(listOf(period.start to today), windows, "النافذة: من أول الفترة لحد النهارده")
        assertEquals(listOf("now", "undated", "old-msg"), found.asks.map { it.transactionId ?: it.messageId })
        // أول أسبوع في الفترة: النافذة بتبدأ من أسبوع قبل النهارده (آخر أيام الشهر اللي فات جوه)
        val early = CountNeedsConfirmation(listOf(source)).load("2026-10-01", period)
        assertEquals("2026-09-24" to "2026-10-01", windows.last())
        assertEquals(listOf("now", "undated", "old-msg"), early.asks.map { it.transactionId ?: it.messageId }, "20 سبتمبر أقدم من أسبوع")
        val beforeWeek = CountNeedsConfirmation(listOf(source)).load("2026-09-27", buildPeriod(2026, 8, 28))
        assertEquals(listOf("old", "undated", "old-msg"), beforeWeek.asks.map { it.transactionId ?: it.messageId }, "في فترته: جوه")
        // قبل ما الفترة تبدأ (اليوم قبلها) ⇒ النافذة يوم البداية بس
        CountNeedsConfirmation(listOf(source)).load("2026-09-01", period)
        assertEquals(period.start to period.start, windows.last())
    }

    @Test fun spacesAndLooseAsksStaySeparate() = runBlocking<Unit> {
        val source = AskSource { _, _ ->
            listOf(
                ask(AskKind.INCOMING_KIND, txn = "t-1", space = "sa"), ask(AskKind.INCOMING_KIND, txn = "t-1", space = "eg"),
                ask(AskKind.SMS_WAITING), ask(AskKind.SMS_WAITING),
            )
        }
        val found = CountNeedsConfirmation(listOf(source)).load(today, period)
        assertEquals(4, found.total, "نفس المعرّف في بلدين = حاجتين · سؤال من غير معرّف بيتعد لوحده")
        assertEquals(mapOf(AskKind.INCOMING_KIND to 2, AskKind.SMS_WAITING to 2), found.byKind)
        assertEquals(0, CountNeedsConfirmation(emptyList()).load(today, period).total)
        // المساعد بيشتغل مرة لكل بلد ⇒ كل ملخص أسبوعي بيعدّ أسئلة بلده بس (مش نفس التذكير مرتين)
        assertEquals(listOf("sa", "sa", "sa"), CountNeedsConfirmation(listOf(source)).load(today, period, "sa").asks.map { it.spaceId })
        assertEquals(1, CountNeedsConfirmation(listOf(source)).load(today, period, "eg").total)
    }

    /**
     * مراجعة S6: داخل مستني وصل قبل يوم الراتب بيومين كان بيختفي من العدّ يوم ما الفترة الجديدة تبدأ (وهو لسه مستني). النافذة دلوقتي
     * فيها الأسبوع اللي فات دايمًا ⇒ كل سؤال بيطلع في تذكير أسبوعي واحد على الأقل.
     */
    @Test fun lastWeekStaysCountedAfterPayday() = runBlocking<Unit> {
        val aug = buildPeriod(2026, 8, 28)
        val count = CountNeedsConfirmation(
            listOf(IncomingAskSource(MemoryTransactionRepository(listOf(txn("in-26", "2026-09-26", Direction.IN))), MemoryCategoryRepository(), "sa")),
        )
        assertEquals(1, count.load("2026-09-27", aug).total)
        assertEquals(1, count.load("2026-09-29", period).total, "تاني يوم في الفترة الجديدة: لسه في العدّ")
        assertEquals(1, count.load("2026-10-03", period).total, "بعد أسبوع بالظبط")
        assertEquals(0, count.load("2026-10-04", period).total, "أقدم من أسبوع ومن الفترة ⇒ في شاشته بس")
        // الملخص الأسبوعي الجاي (أي يوم في الأسبوع اللي بعد وصوله، قبل أو بعد يوم الراتب) بيشوفه
        for (offset in 0..lookback) {
            val day = dayNumberToIso(toDayNumber(parseIsoDate("2026-09-26")) + offset)
            assertEquals(1, count.load(day, periodForDate(day, 28)).total, "ملخص يوم $day")
        }
    }

    private fun txn(id: String, date: String, dir: Direction, kind: EconomicKind = EconomicKind.UNCLASSIFIED, categoryId: String? = null) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
        observedDirection = dir, amountMinor = 150_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = categoryId,
    )

    private val incoming = IncomingAskSource(
        MemoryTransactionRepository(
            listOf(
                txn("in-now", "2026-10-02", Direction.IN),
                txn("in-old", "2026-09-10", Direction.IN),
                txn("salary-cat", "2026-10-01", Direction.IN, categoryId = "c-salary"),
                txn("salary", "2026-10-01", Direction.IN, EconomicKind.SALARY),
                txn("out", "2026-10-03", Direction.OUT),
            ),
        ),
        MemoryCategoryRepository(listOf(Category("c-salary", null, "راتب", "i", "#000", "#fff", true, 1))),
        spaceId = "sa",
    )

    @Test fun pendingIncomingBecomesOneQuestionEach() = runBlocking<Unit> {
        val found = CountNeedsConfirmation(listOf(incoming)).load(today, period)
        assertEquals(listOf(PendingAsk(AskKind.INCOMING_KIND, "sa", transactionId = "in-now", date = "2026-10-02")), found.asks)
        // سؤال أدق على نفس العملية من شريحة تانية (مثلًا «ده راتبك؟») بيكسب
        val salaryAsk = AskSource { _, _ -> listOf(ask(AskKind.IS_SALARY, txn = "in-now", date = "2026-10-02")) }
        assertEquals(listOf(AskKind.IS_SALARY), CountNeedsConfirmation(listOf(incoming, salaryAsk)).load(today, period).asks.map { it.kind })
        EstimatePolicy.current = EstimatePolicy.LEGACY
        assertEquals(0, CountNeedsConfirmation(listOf(incoming)).load(today, period).total, "التطبيق القديم: الوارد دخل، مفيش سؤال")
    }

    /** دمج S1 × S6: «ده حسابك التاني؟» بيتعد، ورسالة بلدها مش معروف ("" — S1) بتطلع في ملخص كل بلد (اختيار الدامج). */
    @Test fun anUnknownSpaceAskCountsInEverySpaceDigestAndTheOwnAccountAskCounts() = runBlocking<Unit> {
        val today = "2026-10-05"
        val source = AskSource { _, _ ->
            listOf(
                PendingAsk(AskKind.OWN_ACCOUNT_CHECK, "sa", transactionId = "t-own", date = "2026-10-04"),
                PendingAsk(AskKind.SMS_WAITING, "", messageId = "m-unknown"),
                PendingAsk(AskKind.SMS_WAITING, "eg", messageId = "m-eg"),
            )
        }
        val count = CountNeedsConfirmation(listOf(source))
        assertEquals(setOf("t-own", "m-unknown"), count.load(today, period, "sa").asks.map { it.transactionId ?: it.messageId }.toSet())
        assertEquals(setOf("m-unknown", "m-eg"), count.load(today, period, "eg").asks.map { it.transactionId ?: it.messageId }.toSet())
        assertEquals(3, count.load(today, period).total, "الشاشة اللي بتجمع: كل حاجة مرة")
    }
}
