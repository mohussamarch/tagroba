package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.Rosca
import app.masroufy.core.SourceRecord
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * الشريحة S4 على Firestore Emulator: سجل الدمج (§75-10) بخريطته المتداخلة `mergeUndo`، و«مش ده» (§75-8) على الجمعية والخطة — بيتكتب
 * لو فيه حاجة بس، ولو اتفضى بيتمسح من المستند (الحفظ بـmerge). بيانات وهمية.
 */
@RunWith(AndroidJUnit4::class)
class MatchingOnFirestoreTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun mergeRecordKeepsWhatToRestore() = run {
        val sources = FirestoreSourceRecordRepository(space())
        val plain = SourceRecord("sr-1", "b-1", "بنك وهمي", "SMS:TEST", "H1", 1, "رسالة وهمية", "t-1", MatchingState.NEW, "جديد")
        val merge = SourceRecord("sr-2", "b-2", "بنك وهمي", null, "H2", 2, "سطر كشف وهمي", "t-1", MatchingState.DUPLICATE, "دمج وهمي", MergeRestore("2026-10-01", 1, 490_000))
        sources.saveMany(listOf(plain, merge, merge.copy(id = "sr-3", mergeUndo = MergeRestore("2026-10-01", 1, null))))
        val stored = sources.listByTransactionIds(listOf("t-1")).associateBy { it.id }
        assertEquals(plain, stored.getValue("sr-1"))
        assertEquals(merge, stored.getValue("sr-2"))
        assertNull(stored.getValue("sr-3").mergeUndo?.statedBalanceMinor)
    }

    @Test fun dismissedSuggestionsAreWrittenAndClearedOnFirestore() = run {
        val s = space()
        val roscas = FirestoreRoscaRepository(s)
        val rosca = Rosca("rc-1", "جمعية وهمية", Currency.SAR, 50_000, 1, "2026-09-01", 5, listOf(2), 250_000, createdAt = "x", dismissedTxnIds = listOf("t-1", "t-2"))
        roscas.save(rosca)
        assertEquals(rosca, roscas.listAll().single())
        roscas.save(rosca.copy(dismissedTxnIds = emptyList()))
        assertEquals(emptyList(), roscas.listAll().single().dismissedTxnIds)

        val plans = FirestoreInstallmentPlanRepository(s)
        val plan = InstallmentPlan("ip-1", "تمويل وهمي", "بنك وهمي", InstallmentKind.FINANCING, Currency.SAR, 1_200_000, 1_440_000, 120_000, 1, "2026-10-10", null, "x", dismissedTxnIds = listOf("t-1"))
        plans.save(plan)
        assertEquals(plan, plans.listAll().single())
        plans.save(plan.copy(dismissedTxnIds = emptyList()))
        assertEquals(emptyList(), plans.listAll().single().dismissedTxnIds)
    }
}
