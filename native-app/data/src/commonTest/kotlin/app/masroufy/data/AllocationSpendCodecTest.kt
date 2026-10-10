package app.masroufy.data

import app.masroufy.core.AllocationKind
import app.masroufy.core.Currency
import app.masroufy.core.PersonAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** §88: «دعم» الزيادة على سداد دين (`countedAsSpend`) على فايربيز والنسخة الشاملة — بيتكتب **لو true بس** ⇒ أي تخصيص قديم هو هو بالحرف. */
class AllocationSpendCodecTest {
    private val plain = PersonAllocation("alloc-1", "t-1", "p-1", AllocationKind.GIFT, 20_000, Currency.SAR)

    @Test fun countedAsSpendIsWrittenOnlyWhenTrueAndReadsBack() {
        val spent = plain.copy(countedAsSpend = true)
        val doc = LedgerCodecs.allocations.encode(spent)
        assertEquals(true, doc["countedAsSpend"])
        assertEquals(spent, LedgerCodecs.allocations.decode(doc))
        val old = LedgerCodecs.allocations.encode(plain)
        assertFalse("countedAsSpend" in old, "تخصيص عادي ⇒ نفس المستند القديم")
        assertEquals(plain, LedgerCodecs.allocations.decode(old))
    }
}
