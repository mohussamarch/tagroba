package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * إنشاء الجمعية بالأسئلة + التحليل بعدها — طلب المالك 2026-09-30 (OVERRIDES §50). مكتوبة بالإيد؛ الأرقام وهمية.
 */
class RoscaWizardTest {
    private fun fill(vararg answers: RoscaAnswer): RoscaDraft = answers.fold(RoscaDraft(Currency.SAR)) { d, a -> applyRoscaAnswer(d, a) }

    private val basics = arrayOf(
        RoscaAnswer.Name("  جمعية   الشغل "), RoscaAnswer.TurnsCount(10), RoscaAnswer.ShareAmount(100_000),
        RoscaAnswer.Frequency(RoscaFrequency.MONTHLY), RoscaAnswer.FirstDate("2026-01-01"), RoscaAnswer.Share(RoscaShare.ONE),
    )

    @Test
    fun `الأسئلة بالترتيب وكل واحد رقمه`() {
        var d = RoscaDraft(Currency.SAR)
        val seen = mutableListOf<Pair<RoscaQuestion?, Pair<Int, Int>>>()
        for (a in basics + arrayOf(RoscaAnswer.MyTurns(listOf(4)), RoscaAnswer.Payout(null))) {
            seen += nextRoscaQuestion(d) to roscaQuestionProgress(d)
            d = applyRoscaAnswer(d, a)
        }
        assertEquals(RoscaQuestion.entries.toList(), seen.map { it.first })
        assertEquals((1..8).map { it to 8 }, seen.map { it.second })
        assertNull(nextRoscaQuestion(d))
        val r = roscaFromDraft(d, "rc-1", "2026-09-30T00:00:00.000Z")
        assertEquals("جمعية الشغل", r.name)
        assertEquals(100_000, r.contributionMinor)
        assertEquals(1_000_000, r.payoutMinor)
    }

    @Test
    fun `التحليل — هتقبض إمتى وهتدفع إيه`() {
        val r = roscaFromDraft(fill(*basics, RoscaAnswer.MyTurns(listOf(4)), RoscaAnswer.Payout(null)), "rc-1", "")
        val f = roscaForecast(r)
        assertEquals(listOf("2026-04-01"), f.payoutDates)
        assertEquals("2026-10-01", f.lastDueAt)
        assertEquals(1_000_000, f.totalPayMinor)
        assertEquals(0, f.gainMinor)
        assertEquals(3, f.paymentsBeforePayout)
        assertEquals(6, f.paymentsAfterPayout)
        assertEquals(300_000, f.peakSavedMinor)
        assertEquals(600_000, f.peakOwedMinor)
        assertEquals(listOf(100_000L, 200_000L, 300_000L, -600_000L), f.rows.take(4).map { it.positionAfterMinor })
        assertEquals(0, f.rows.last().positionAfterMinor)
    }

    @Test
    fun `جمعية كل أسبوع`() {
        val d = fill(*basics, RoscaAnswer.Frequency(RoscaFrequency.WEEKLY), RoscaAnswer.MyTurns(listOf(1)), RoscaAnswer.Payout(null))
        val rows = roscaForecast(roscaFromDraft(d, "rc-w", "")).rows
        assertEquals(listOf("2026-01-01", "2026-01-08", "2026-01-15", "2026-01-22"), rows.take(4).map { it.dueAt })
        assertEquals(DueScheduleError::class, runCatching { checkDueSchedule(DueSchedule("2026-01-01", 5, 1, 10, unit = CycleUnit.WEEK)) }.exceptionOrNull()!!::class)
    }

    @Test
    fun `نص سهم وسهمين`() {
        // نص سهم لقسط فردي بالهللة مرفوض — ما بنقرّبش
        assertFailsWith<RoscaError> { fill(*basics, RoscaAnswer.ShareAmount(100_001), RoscaAnswer.Share(RoscaShare.HALF)) }
        val half = fill(*basics, RoscaAnswer.Share(RoscaShare.HALF), RoscaAnswer.MyTurns(listOf(5)))
        assertEquals(500_000, suggestedRoscaPayout(half))
        assertEquals(50_000, roscaFromDraft(applyRoscaAnswer(half, RoscaAnswer.Payout(null)), "rc-h", "").contributionMinor)

        val two = fill(*basics, RoscaAnswer.Share(RoscaShare.TWO))
        assertFailsWith<RoscaError> { applyRoscaAnswer(two, RoscaAnswer.MyTurns(listOf(2))) }
        val f = roscaForecast(roscaFromDraft(fill(*basics, RoscaAnswer.Share(RoscaShare.TWO), RoscaAnswer.MyTurns(listOf(7, 2)), RoscaAnswer.Payout(null)), "rc-2", ""))
        assertEquals(listOf("2026-02-01", "2026-07-01"), f.payoutDates)
        assertEquals(1 to 3, f.paymentsBeforePayout to f.paymentsAfterPayout)
    }

    @Test
    fun `الدور لسه ما اتعرفش — القبض غير متاح مش صفر`() {
        val d = fill(*basics, RoscaAnswer.MyTurns(emptyList()))
        assertNull(nextRoscaQuestion(d))
        assertEquals(7 to 7, roscaQuestionProgress(d))
        val r = roscaFromDraft(d, "rc-u", "")
        val f = roscaForecast(r)
        assertNull(f.totalReceiveMinor)
        assertNull(f.gainMinor)
        assertNull(f.paymentsBeforePayout)
        assertNull(roscaStatus(r, emptyList(), "2026-02-01").gainMinor)
        assertEquals(uiText(TextKey.ROSCA_TURN_UNKNOWN), checkRoscaEntry(r, emptyList(), RoscaEntryKind.PAYOUT, 100))
    }

    @Test
    fun `الرجوع وتغيير إجابة بيمسح اللي بقى متعارض بس`() {
        val d = fill(*basics, RoscaAnswer.MyTurns(listOf(4)), RoscaAnswer.Payout(null))
        val fewer = applyRoscaAnswer(d, RoscaAnswer.TurnsCount(3))
        assertEquals(RoscaQuestion.MY_TURN, nextRoscaQuestion(fewer))
        assertEquals("جمعية الشغل", fewer.name)
        // نفس عدد الأدوار أو أكتر ⇒ دورك بيفضل، وقيمة الدور بس هي اللي بتتسأل تاني
        assertEquals(RoscaQuestion.PAYOUT, nextRoscaQuestion(applyRoscaAnswer(d, RoscaAnswer.TurnsCount(12))))
    }

    @Test
    fun `الاختيارات والنص المقترح`() {
        val d = fill(*basics)
        val turn = roscaPrompt(d, RoscaQuestion.MY_TURN)
        assertEquals((1..10).map { it.toString() } + uiText(TextKey.ROSCA_TURN_UNKNOWN_CHOICE), turn.choices)
        val withTurn = applyRoscaAnswer(d, RoscaAnswer.MyTurns(listOf(4)))
        assertEquals(formatMoney(1_000_000), roscaPrompt(withTurn, RoscaQuestion.PAYOUT).suggested)
        assertEquals(RoscaFrequency.entries.size, roscaPrompt(d, RoscaQuestion.FREQUENCY).choices.size)
    }

    @Test
    fun `الإجابات الغلط بتترفض في سؤالها`() {
        assertFailsWith<RoscaError> { fill(RoscaAnswer.Name("   ")) }
        assertFailsWith<RoscaError> { fill(RoscaAnswer.TurnsCount(1)) }
        assertFailsWith<RoscaError> { fill(RoscaAnswer.ShareAmount(0)) }
        assertFailsWith<RoscaError> { fill(RoscaAnswer.FirstDate("2026-02-30")) }
        assertFailsWith<RoscaError> { fill(*basics, RoscaAnswer.MyTurns(listOf(11))) }
        assertFailsWith<RoscaError> { roscaFromDraft(fill(*basics), "x", "") }
    }
}
