package app.masroufy.usecase

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.BalanceSource
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.duplicateEffectMinor
import app.masroufy.memory.MemorySmsInbox
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * «افهم رصيدك» على **تاريخ طويل** (18 شهر مخترعين — نفس عالم §83 `eighteenMonths`) بنفس ترتيب حساب المالك:
 * 1. كشف متستورد من 2025-01-01 (زي حسابه).
 * 2. نسخة 0.1/0.2: رسايل آخر شهرين اتسجلت لوحدها في الخلفية **من غير دمج** (§75-10 ما كانش متوصّل هناك) ⇒ مكرر مزروع.
 * 3. نسخة 0.3: «اقرأ اللي فات؟» على كل التاريخ **بالدمج** ⇒ القديم بيتدمج (مصدرين)، واللي اتسجل في 2 بيطلع مكرر بمرجعه، ورسايل يوليو جديدة.
 * التفكيك لازم يساوي رقم «معك الآن» **بالهللة**، والباحث عن المكرر يلاقي المزروع **بس** — وبعد «دول عملية واحدة» للكل الرصيد = الحقيقي
 * وسلسلة رصيد الكشف لسه مطابقة سطر بسطر.
 */
class BalanceWhyLongTest {
    private val july = (1..5).map { HistoryTxn("j$it", "2026-07-0$it", 3_300L + it, false, "TEST MART", 0) }

    @Test fun explanationAddsUpToTheShownBalanceAndFindsOnlyThePlantedDuplicates() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, HISTORY_BANK), window = null)
        val history = HistoryWorld(space = space)
        val all = eighteenMonths()
        history.importStatement(history.statement(all))
        val statementClosing = history.closing("2026-07-10")

        // 2) 0.1/0.2: رسايل مايو ويونيو اتسجلت لوحدها من غير دمج
        val inbox = MemorySmsInbox(available = true, now = HISTORY_NOW)
        inbox.enable(listOf("TESTBANK"))
        val recent = all.filter { it.date >= "2026-05-01" }
        for (t in recent) {
            inbox.receive(t.sms)
            inbox.preLearn(space.spaceId, space.parse, t.sms)
        }
        AutoRecordSms(AutoRecordSmsDeps(inbox, listOf(space.lane(inbox)))).run()

        // 3) 0.3: التاريخ كله بالدمج (±2 يوم) + رسايل يوليو الجديدة
        val merging = SmsLane.of(
            space.spaceId, space.importDeps(space.smsEffects(inbox)).copy(crossSourceWindowDays = CROSS_SOURCE_WINDOW_DAYS),
            ManageSmsInbox(inbox, space.parse), space.wallets,
        )
        for (t in all + july) {
            inbox.receive(t.sms)
            inbox.preLearn(space.spaceId, space.parse, t.sms)
        }
        repeat(3) { AutoRecordSms(AutoRecordSmsDeps(inbox, listOf(merging))).run() }

        val world = LedgerWorld(HISTORY_BANK, CASH, space.txnStore, space.sources, space.batchStore, space.wallets)
        // شبيه مش مكرر: عملية يدوية بنفس مبلغ ويوم سطر كشف
        val lookalike = all.first { it.date == "2026-05-10" && !it.incoming }
        world.add(world.txn("manual-1", lookalike.date, lookalike.minor), from = null)

        val asOf = "2026-07-10"
        val why = world.explain().explain(HISTORY_BANK.id, asOf)!!
        val shown = world.shown(asOf)!!
        assertEquals(shown, why.computedMinor, "the explanation is the shown balance to the halala")
        assertEquals(why.openingMinor + why.totals.sumOf { it.inMinor } - why.totals.sumOf { it.outMinor }, shown)
        assertEquals(500_000L, why.openingMinor)
        assertEquals("2025-01-01", why.openingAt)

        // المزروع = كل رسالة من مايو ويونيو اتسجلت لوحدها (رسالة بس) وقصادها سطر الكشف بتاعها
        val bySource = space.sources.all().groupBy { it.transactionId }
        val smsOnly = space.all().filter { t -> bySource[t.id].orEmpty().all { it.sourceReference?.startsWith("SMS:") == true } && bySource[t.id] != null }
        val planted = smsOnly.filter { it.occurredAt < "2026-07-01" }
        assertTrue(planted.size > recent.size / 2, "the old builds really doubled: ${planted.size} of ${recent.size}")
        val expected = planted.map { it.occurredAt to it.amountMinor }.toSet()
        val found = why.review.duplicates
        assertEquals(expected, found.map { it.sms.date to it.sms.amountMinor }.toSet(), "every planted duplicate and nothing else")
        assertEquals(planted.size, found.size)
        for (p in found) {
            val twin = recent.first { it.minor == p.sms.amountMinor }
            assertTrue(p.days <= CROSS_SOURCE_WINDOW_DAYS && p.statement.amountMinor == twin.minor)
        }
        assertTrue(found.none { it.sms.transactionId == "manual-1" || it.statement.transactionId == "manual-1" })
        assertTrue(found.none { it.sms.date >= "2026-07-01" }, "July SMS have no statement line — not duplicates")
        assertTrue((why.totals.firstOrNull { it.source == BalanceSource.BOTH }?.count ?: 0) > 400, "older history merged as one transaction with two sources")

        // أثر المكرر: الرصيد الظاهر − أثره = الحقيقي (الكشف + يوليو + اليدوي)
        val truth = statementClosing - july.sumOf { it.minor } - lookalike.minor
        assertEquals(truth, shown - duplicateEffectMinor(found))
        // الشراء المكرر بينقّص والراتب المكرر بيزوّد — هنا الرواتب اتكررت كمان (رسالة «حوالة واردة»)، فالأثر الصافي بالموجب
        assertEquals(planted.sumOf { if (it.observedDirection == Direction.IN) it.amountMinor else -it.amountMinor }, duplicateEffectMinor(found))

        // «دول عملية واحدة» لكل زوج ⇒ الرصيد = الحقيقي، ونسخة قبل كل دمج
        val merger = world.merger()
        val tickets = found.map { assertIs<MergeResult.Done>(merger.merge(it.sms.transactionId, it.statement.transactionId)).ticket }
        assertEquals(found.size, world.backup.files.size)
        assertEquals(truth, world.shown(asOf))
        // سلسلة الكشف: بعد الدمج الفرق الوحيد = العمليات اللي مش في الكشف أصلًا (اليدوي ورسايل يوليو) — مفيش أثر لمكرر
        val notInStatement = july.runningFold(lookalike.minor) { acc, t -> acc + t.minor }.toSet()
        val chain = history.mismatches(asOf)
        assertTrue(chain.all { it.date >= lookalike.date && -it.differenceMinor in notInStatement }, "the statement chain differs only by non-statement items: ${chain.take(3)}")
        val after = world.explain().explain(HISTORY_BANK.id, asOf)!!
        assertEquals(emptyList(), after.review.duplicates)
        assertEquals(truth, after.computedMinor)

        // تراجع عن واحد ⇒ يرجع زي ما كان (والزوج يرجع في القايمة)
        val last = tickets.last()
        assertIs<UndoResult.Done>(merger.undo(last))
        assertEquals(truth - (if (last.removed.observedDirection == Direction.OUT) last.removed.amountMinor else -last.removed.amountMinor), world.shown(asOf))
        assertEquals(1, world.explain().explain(HISTORY_BANK.id, asOf)!!.review.duplicates.size)
        assertIs<UndoResult.Refused>(merger.undo(last), "the same ticket cannot undo twice")
        assertEquals(Currency.SAR, why.currency)
    }
}
