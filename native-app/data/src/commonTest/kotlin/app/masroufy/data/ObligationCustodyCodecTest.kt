package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §82-19 «أمانة ليك عنده» على فايربيز والنسخة الشاملة: نفس نوع «لك عنده» (`receivable` — التطبيق القديم بيقبل الأنواع التلاتة بس) + حقل
 * `custodyHeld` **لو true بس** ⇒ أي دين قديم بيتكتب هو هو بالحرف.
 */
class ObligationCustodyCodecTest {
    private val plain = Obligation("obl-1", "p-1", null, ObligationKind.RECEIVABLE, 20_000, Currency.SAR)

    @Test fun custodyHeldIsWrittenOnlyWhenTrueAndReadsBack() {
        val held = plain.copy(custodyHeld = true)
        val doc = LedgerCodecs.obligations.encode(held)
        assertEquals("receivable", doc["kind"], "النوع اللي التطبيق القديم يعرفه")
        assertEquals(true, doc["custodyHeld"])
        assertEquals(held, LedgerCodecs.obligations.decode(doc))
        val old = LedgerCodecs.obligations.encode(plain)
        assertFalse("custodyHeld" in old, "دين عادي ⇒ نفس المستند القديم")
        assertEquals(plain, LedgerCodecs.obligations.decode(old))
        assertTrue(LedgerCodecs.obligations.decode(old + ("custodyHeld" to false)).custodyHeld.not())
    }
}
