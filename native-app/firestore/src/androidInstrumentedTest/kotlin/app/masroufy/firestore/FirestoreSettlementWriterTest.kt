package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Settlement
import app.masroufy.core.SettlementError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * التسوية الذرّية على محاكي أندرويد قدام Firestore Emulator — نفس حالات `settlementConcurrency.test.ts` في التطبيق الحالي:
 * الطلب المكرر، والزيادة عن الباقي، وتسويتين في نفس اللحظة مجموعهم أكبر من الدين. بيانات وهمية.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreSettlementWriterTest {
    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    private suspend fun setUp(): Pair<FirestoreSpace, FirestoreSettlementWriter> {
        val s = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())
        FirestoreObligationRepository(s).saveMany(listOf(Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 1_000, Currency.SAR)))
        return s to FirestoreSettlementWriter(s)
    }

    @Test fun sameRequestTwiceIsOneSettlement() = run {
        val (s, writer) = setUp()
        val input = Settlement("s-1", "t-1", "o-1", 400)
        assertEquals(input, writer.settle(input, "p-1"))
        assertEquals(input, writer.settle(input, "p-1"))
        assertEquals(listOf("s-1"), FirestoreSettlementRepository(s).listByObligations(listOf("o-1")).map { it.id })
        assertEquals(1L, s.db.document("${s.root}/concurrency/settlements").get().rawData()?.get("revision"), "العدّاد بيزيد مرة واحدة بس")
        // نفس المعرّف ببيانات مختلفة = تعارض صريح، مش كتابة فوقه
        assertFailsWith<SettlementError> { writer.settle(input.copy(amountMinor = 500), "p-1") }
    }

    @Test fun moreThanTheRemainingIsRefused() = run {
        val (_, writer) = setUp()
        writer.settle(Settlement("s-1", "t-1", "o-1", 700), "p-1")
        assertFailsWith<SettlementError> { writer.settle(Settlement("s-2", "t-2", "o-1", 400), "p-1") }
        assertFailsWith<SettlementError> { writer.settle(Settlement("s-3", "t-3", "o-1", 100), "p-مش-صاحبه") }
    }

    @Test fun twoAtOnceNeverExceedTheDebt() = run {
        val (s, writer) = setUp()
        // تسويتين 600 + 600 على دين 1,000 في نفس اللحظة ⇒ واحدة بس تنجح
        val results = coroutineScope { listOf("s-a", "s-b").map { id -> async { runCatching { writer.settle(Settlement(id, "t-$id", "o-1", 600), "p-1") } } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(600, FirestoreSettlementRepository(s).listByObligations(listOf("o-1")).sumOf { it.amountMinor })
    }
}
