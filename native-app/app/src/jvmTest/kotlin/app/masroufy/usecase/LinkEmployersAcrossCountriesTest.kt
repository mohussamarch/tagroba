package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.memory.MemoryIncomeSourceRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §111-2: جهة في البلدين = جهة واحدة بمبلغين (ريال وجنيه) — من غير جمع بين العملتين. أسماء ومبالغ مخترعة. */
class LinkEmployersAcrossCountriesTest {
    private fun job(id: String, name: String, currency: Currency, expected: Long?) =
        IncomeSource(id, name, name.lowercase(), IncomeSourceKind.JOB, currency, "2025-01-01", expectedMinor = expected)

    private val sa = MemoryIncomeSourceRepository(listOf(job("sa-1", "Fake Co", Currency.SAR, 1_500_000), job("sa-2", "Other Fake", Currency.SAR, 400_000)))
    private val eg = MemoryIncomeSourceRepository(listOf(job("eg-1", "Fake Co", Currency.EGP, 2_000_000), job("eg-2", "Fake Co Egypt", Currency.EGP, null)))
    private val use = LinkEmployersAcrossCountries(listOf(EmployerSpaceSource(DEFAULT_SPACE_ID, sa), EmployerSpaceSource("eg", eg)))

    @Test fun theSameEmployerInBothCountriesIsOneEmployerWithTwoAmounts() = runBlocking<Unit> {
        val all = use.list("2026-10-23")
        val fake = all.first { it.key == "fake co" }
        assertTrue(fake.inBothCountries)
        assertEquals(1_500_000L, fake.payIn(Currency.SAR))
        assertEquals(2_000_000L, fake.payIn(Currency.EGP), "كل عملة لوحدها — مفيش جمع")
        assertEquals(3, all.size)
    }

    @Test fun twoDifferentNamesCanBeLinkedAndUnlinked() = runBlocking<Unit> {
        assertTrue(use.link(DEFAULT_SPACE_ID, "sa-2", "eg", "eg-2"))
        val linked = use.list("2026-10-23").first { it.key == "other fake" }
        assertTrue(linked.inBothCountries)
        assertNull(linked.payIn(Currency.EGP), "المبلغ ما اتكتبش ⇒ غير متاح، مش صفر")
        assertFalse(use.link(DEFAULT_SPACE_ID, "sa-1", DEFAULT_SPACE_ID, "sa-2"), "نفس البلد ما بيترابطش")
        assertTrue(use.unlink("eg", "eg-2"))
        assertFalse(use.list("2026-10-23").first { it.key == "other fake" }.inBothCountries)
    }
}
