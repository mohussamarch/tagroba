package app.masroufy.ui.screens.more

import app.masroufy.core.Currency
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.usecase.ArchivedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-archive (§118-ب-1): مصدر الدخل المؤرشف ما بيظهرش في «الدخل» ولا جوه نوعه (لا شغال ولا قديم) — بيظهر في «المؤرشف» بس. بيانات مخترعة. */
class IncomeArchivedTest {
    private val live = IncomeSource("inc-1", "شركة تجربة", "شركة تجربة", IncomeSourceKind.JOB, Currency.SAR, "2026-01-01")
    private val gone = IncomeSource(
        "inc-2", "شركة قديمة", "شركة قديمة", IncomeSourceKind.JOB, Currency.SAR, "2025-01-01",
        endedAt = "2026-10-24", archivedAt = "2026-10-24T10:00:00Z", endedByArchive = true,
    )

    @Test
    fun archivedSource_hiddenFromHubAndKindPage_shownInArchivedRow() {
        val page = kindPage(IncomeSourceKind.JOB, listOf(live, gone))
        assertEquals(listOf("inc-1"), page.now.map { it.id })
        assertTrue(page.past.none { it.id == "inc-2" })
        assertEquals(listOf(ArchivedItem("inc-2", "شركة قديمة")), archivedSources(listOf(live, gone)))
        assertEquals(emptyList(), archivedSources(listOf(live, gone), IncomeSourceKind.PART_TIME))
        // الأرشيف لوحده ⇒ كارت النوع «ضيف» مش «قبل كده»
        val tile = incomeHub(listOf(gone)).tiles.first { it.kind == IncomeSourceKind.JOB }
        assertEquals(TileState.EMPTY, tile.state)
    }
}
