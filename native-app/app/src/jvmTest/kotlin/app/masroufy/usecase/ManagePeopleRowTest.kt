package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.PersonProfile
import app.masroufy.core.Settlement
import app.masroufy.core.UserSetting
import app.masroufy.core.defaultSpace
import app.masroufy.core.rowAppend
import app.masroufy.core.rowRemove
import app.masroufy.core.rowRestore
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonProfileRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUserSettingsStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** صف «أهم 5» على مستودعات الذاكرة (OVERRIDES §84) — أسماء ومبالغ مخترعة. */
class ManagePeopleRowTest {
    private val clock = FixedClock("2026-10-10T10:00:00.000Z")
    private val people = MemoryPersonRepository(
        listOf(
            Person("p-1", "زميل وهمي"), Person("p-2", "أخ وهمي"), Person("p-3", "جار وهمي"), Person("p-4", "صاحب وهمي"),
            Person("p-5", "أم وهمية"), Person("p-6", "بقال وهمي"), Person("p-7", "قديم وهمي", archived = true),
        ),
    )
    private val profiles = MemoryPersonProfileRepository(
        listOf(
            PersonProfile("p-2", PersonCircle.FAMILY, null, "x"),
            PersonProfile("p-5", PersonCircle.FAMILY, "أمي", "x"),
            PersonProfile("p-1", PersonCircle.WORK, null, "x"),
        ),
    )
    private val settings = MemoryUserSettingsStore()
    private val obligations = MemoryObligationRepository(
        listOf(
            Obligation("o-1", "p-3", null, ObligationKind.RECEIVABLE, 10_000, Currency.SAR),
            Obligation("o-2", "p-3", null, ObligationKind.LOAN_PAYABLE, 5_000, Currency.SAR),
            Obligation("o-3", "p-4", null, ObligationKind.RECEIVABLE, 2_000, Currency.SAR),
            Obligation("o-4", "p-7", null, ObligationKind.RECEIVABLE, 2_000, Currency.SAR),
        ),
    )
    private val settlements = MemorySettlementRepository(listOf(Settlement("s-1", "t-9", "o-3", 1_000)))
    private val links = MemoryEventLinkRepository(listOf(EventLink("l-1", "e-1", "t-1", EventRole.SPEND, "p-6", "x")))
    private val source = PeopleSpaceSource(defaultSpace(), obligations, settlements, MemoryLifeEventRepository(), links, MemoryTransactionRepository())
    private val row = ManagePeopleRow(ManagePeopleRowDeps(people, profiles, settings, listOf(source), clock))

    @Test fun defaultBeforeAnyManualOrder() = runBlocking<Unit> {
        // العيلة الأول (بترتيبهم)، وبعدهم الأكثر تعاملًا: p-3 (دينين) = p-4 (دين + تسوية) ثم p-6 (ربط حدث) — المؤرشف برّه
        val r = row.row()
        assertFalse(r.custom)
        assertEquals(listOf("p-2", "p-5", "p-3", "p-4", "p-6"), r.ids)
        assertEquals(listOf("p-1"), row.pickable(r.ids))
        assertTrue(settings.listAll().isEmpty(), "القراية ما بتكتبش")
    }

    @Test fun everyChangeIsSavedOnTheAccountAndUndoRestoresTheIndex() = runBlocking<Unit> {
        val start = row.row().ids
        val removed = row.save(rowRemove(start, "p-5"))
        assertTrue(removed.custom)
        assertEquals(listOf("p-2", "p-3", "p-4", "p-6"), row.row().ids)
        assertEquals(listOf("p-5", "p-1"), row.pickable(removed.ids), "اللي اتشال بيفضل في الناس")
        row.save(rowRestore(removed.ids, "p-5", 1))
        assertEquals(start, row.row().ids)
        val stored = settings.listAll().filterIsInstance<UserSetting.PeopleRow>().single()
        assertEquals(start, stored.personIds)
        assertEquals("2026-10-10T10:00:00.000Z", stored.updatedAt)
    }

    @Test fun addGoesLastMaxFiveAndArchivedOrDuplicatesAreDropped() = runBlocking<Unit> {
        row.save(listOf("p-1", "p-1", "p-7", "p-3"))
        assertEquals(listOf("p-1", "p-3"), row.row().ids)
        row.save(rowAppend(row.row().ids, "p-6"))
        assertEquals(listOf("p-1", "p-3", "p-6"), row.row().ids)
        row.save(listOf("p-1", "p-2", "p-3", "p-4", "p-5", "p-6"))
        assertEquals(5, row.row().ids.size)
        // شخص اتأرشف بعد ما اتحط في الصف ⇒ بيستخبى من غير ما يتمسح من الترتيب المحفوظ
        people.save(Person("p-2", "أخ وهمي", archived = true))
        assertEquals(listOf("p-1", "p-3", "p-4", "p-5"), row.row().ids)
    }

    @Test fun emptyManualRowStaysEmpty() = runBlocking<Unit> {
        row.save(emptyList())
        val r = row.row()
        assertTrue(r.custom)
        assertTrue(r.ids.isEmpty())
    }
}
