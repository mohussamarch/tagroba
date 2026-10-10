package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.Texts
import app.masroufy.core.Wallet
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * تحويلاتك بين البلدين (`SpaceTransfer`): الأزواج من `TransferBetweenSpaces.list` بالرجلين (المحفظة والتاريخ من عملية كل رجل في بلدها)، المبالغ زي ما
 * هي بعملتها، والسعر نص العرض من الدومين بس. الحالات: بيحمّل · خطأ · بلد واحدة (غير متاح) · فاضي · عادي.
 */
class SpaceTransferModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val sa = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val eg = Space("eg", "مصر", "EG", Currency.EGP, "2026-02-01T00:00:00.000Z")
    private val egBank = Wallet("w-eg", "بنك مصر الوهمي", Currency.EGP, "bank", 0, "2026-02-01")
    private val books = listOf(SpaceWallets(sa, listOf(Fx.bank), active = true), SpaceWallets(eg, listOf(egBank), active = false))
    private val pair = SpaceTransfer("st1", "sa", "t-out", 200_000, Currency.SAR, "eg", "t-in", 2_564_000, Currency.EGP, "2026-10-04T09:00:00.000Z", note = "مصروف البيت")

    @Test fun pairShowsBothLegsWithWalletDateAndAmountAsIs() {
        val legs = mapOf("t-out" to Fx.tx("t-out", date = "2026-10-03"), "t-in" to Fx.tx("t-in", date = "2026-10-04", wallet = egBank.id))
        val v = pairViews(listOf(pair), books, legs).single()
        assertEquals("من السعودية إلى مصر", v.route)
        assertEquals(listOf("حساب الراتب", "بنك مصر الوهمي"), v.legs.map { it.title })
        assertEquals(listOf("خرجت، 3 أكتوبر", "وصلت، 4 أكتوبر"), v.legs.map { it.sub })
        assertEquals(listOf(200_000L, 2_564_000L), v.legs.map { it.amountMinor })
        assertEquals(listOf(Currency.SAR, Currency.EGP), v.legs.map { it.currency }, "كل رجل بعملتها — مفيش تحويل عملة")
        assertEquals(listOf("س", "م"), v.legs.map { it.mark })
        assertEquals("12.8200 ج.م لكل 1 ر.س", v.rate, "نص السعر من الدومين (للعرض بس)")
        assertEquals("ملاحظتك: مصروف البيت", v.note)
    }

    @Test fun unreadLegFallsBackToTheCountry() {
        val v = pairViews(listOf(pair.copy(note = " ")), books).single()
        assertEquals(listOf("خرجت من السعودية", "وصلت في مصر"), v.legs.map { it.title })
        assertTrue(v.legs.all { it.sub == null })
        assertNull(v.note)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("طلعت، 3 أكتوبر", pairViews(listOf(pair), books, mapOf("t-out" to Fx.tx("t-out", date = "2026-10-03"))).single().legs.first().sub)
    }

    @Test fun rateNeedsBothAmounts() {
        assertNull(rateLine(null, Currency.SAR, 100, Currency.EGP))
        assertNull(rateLine(0, Currency.SAR, 100, Currency.EGP))
        assertEquals("12.8200 ج.م لكل 1 ر.س", rateLine(10_000, Currency.SAR, 128_200, Currency.EGP))
    }

    @Test fun statesFollowThePrototype() {
        assertEquals(SpaceTransferState.LOADING, spaceTransferState(null, null, failed = false))
        assertEquals(SpaceTransferState.FAILED, spaceTransferState(null, null, failed = true))
        assertEquals(SpaceTransferState.ONE_SPACE, spaceTransferState(books.take(1), emptyList(), failed = false), "بلد واحدة ⇒ غير متاح")
        assertEquals(SpaceTransferState.EMPTY, spaceTransferState(books, emptyList(), failed = false))
        assertEquals(SpaceTransferState.READY, spaceTransferState(books, pairViews(listOf(pair), books), failed = false))
        assertEquals(listOf("sa>eg", "eg>sa"), directions(listOf(eg, sa), activeId = "sa").map { it.key }, "البلد الشغالة الأول")
    }
}
