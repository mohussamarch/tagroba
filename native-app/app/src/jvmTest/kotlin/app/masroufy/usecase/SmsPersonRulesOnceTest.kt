package app.masroufy.usecase

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * دمج 0.5.1: «أكّد الأشكال» بقى صفحة صفحة (r051 — حفظ لكل صفحة ومحفظة جوه قفل الصفحة) و`PersonRulesEffect` (r05 — A2) آخر أثر في
 * `smsRecordEffects`. الاختبار ده بيحط أثر عدّاد **مكان** قواعد الأشخاص (نفس المعامل `personRules`) ويتأكد إن كل عملية اتسجلت من
 * الصندوق — على أكتر من صفحة — بتوصله **مرة واحدة بالظبط**، وإن التشغيلة التانية ما بتعيدش حاجة. **كل الرسايل والمحلات والمبالغ مخترعة.**
 */
class SmsPersonRulesOnceTest {
    /** بيسجّل معرّف كل عملية بتوصل للأثر الأخير (زي `PersonRulesEffect.afterCommit`: اللي اتسجل + الزيادة). */
    private class SeenEffect : RecordEffect {
        val seen = mutableListOf<String>()

        override suspend fun afterCommit(ctx: RecordContext) {
            seen += ctx.lines.map { it.transaction.id } + ctx.extra.map { it.first.id }
        }
    }

    @Test fun lastEffectSeesEveryRecordedOperationOnceAcrossPages() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space))
        val last = SeenEffect()
        val effects = smsRecordEffects(space.spaceId, space.wallets, world.inbox, space.categories, personRules = last)
        assertSame(last, effects.last(), "the person rules effect stays the last record effect")
        val lane = SmsLane.of(space.spaceId, space.importDeps(effects), ManageSmsInbox(world.inbox, space.parse), space.wallets)
        world.enable()
        world.receive(*(0 until COUNT).map { i -> sms("p$i", body(i), "${day("2026-10-01", i % 7)}T09:00:00Z") }.toTypedArray())

        val first = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(lane))).run()
        val recorded = space.all().map { it.id }
        assertEquals(COUNT, recorded.size, "every message of a known shape is recorded over ${COUNT / PAGE + 1} pages")
        assertEquals(COUNT, first.recorded)
        assertEquals(recorded.sorted(), last.seen.sorted(), "the last effect sees every recorded operation")
        assertEquals(last.seen.size, last.seen.toSet().size, "no operation reaches the last effect twice")

        AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(lane))).run()
        assertEquals(COUNT, last.seen.size, "a second run records nothing and repeats nothing")
    }

    private fun body(i: Int): String = "شراء\nبـSR ${money(1_000L + i * 13L)}\nلدى:TEST CAFE\n${smsDay(day("2026-10-01", i % 7))}"

    private companion object {
        const val COUNT = 450
        const val PAGE = 200
    }
}
