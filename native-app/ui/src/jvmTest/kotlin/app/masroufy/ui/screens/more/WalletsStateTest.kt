package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.BalanceMismatch
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Language
import app.masroufy.core.ReconcileResult
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.periodForDate
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.text.t
import app.masroufy.usecase.ReconcileOutcome
import app.masroufy.usecase.TransactionsScreenData
import app.masroufy.usecase.WalletNow
import app.masroufy.usecase.WithYouNow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** المحافظ وتفاصيلها والمطابقة: الأرصدة جاهزة من حالة الاستخدام، والمجهول «غير متاح» مش صفر. */
class WalletsStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val bank = Wallet("w-bank", "الراجحي", Currency.SAR, "bank", 483_783, "2025-01-01", accountLast4 = "4407")
    private val stc = Wallet("w-stc", "STC Pay", Currency.SAR, "digital_wallet", 0, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 100_000, "2026-09-06")

    private fun now(vararg w: WalletNow, total: Long? = 1_000) = WithYouNow(Currency.SAR, w.toList(), total, null)

    @Test
    fun groupsAreBanksThenDigitalThenCashWithMainBadge() {
        val v = walletsView(now(WalletNow(bank, 270_725), WalletNow(cash, 50_000), WalletNow(stc, 1_000)), mainId = "w-cash")
        assertEquals(listOf(WalletGroupKind.BANKS, WalletGroupKind.DIGITAL, WalletGroupKind.CASH), v.groups.map { it.kind })
        val b = v.groups[0].rows.single()
        assertEquals(sentenceDigits("4407"), b.last4)
        assertEquals(270_725L, b.balanceMinor)
        assertFalse(b.isMain)
        assertFalse(b.approx)
        val c = v.groups[2].rows.single()
        assertTrue(c.isMain)
        assertTrue(c.isCash)
        assertTrue(c.approx, "الكاش تقريبي (§32)")
        assertEquals(3, v.count)
        assertTrue(v.unknown.isEmpty())
    }

    @Test
    fun noDigitalWalletsMeansNoDigitalGroupAndNoMainWithoutHook() {
        val v = walletsView(now(WalletNow(bank, 1), WalletNow(cash, 2)), mainId = null)
        assertEquals(listOf(WalletGroupKind.BANKS, WalletGroupKind.CASH), v.groups.map { it.kind })
        assertTrue(v.groups.flatMap { it.rows }.none { it.isMain })
    }

    @Test
    fun unknownBalanceStaysUnknown() {
        val v = walletsView(now(WalletNow(bank, null), WalletNow(cash, 5), total = null), null)
        assertNull(v.totalMinor)
        assertNull(v.groups[0].rows.single().balanceMinor)
        assertFalse(v.groups[0].rows.single().approx)
        assertEquals(listOf("الراجحي"), v.unknown)
        assertEquals(t(UiKey.WLIST_UNKNOWN_LINE, "الراجحي"), walletsTotalLine(v))
    }

    @Test
    fun totalLineCountsWallets() {
        assertEquals(t(UiKey.WLIST_TOTAL_LINE, t(UiKey.WLIST_TWO)), walletsTotalLine(walletsView(now(WalletNow(bank, 1), WalletNow(cash, 2)), null)))
        assertEquals(t(UiKey.WLIST_EMPTY_LINE), walletsTotalLine(walletsView(now(total = null), null)))
        val two = walletsView(now(WalletNow(bank, null), WalletNow(stc, null), total = null), null)
        assertEquals(t(UiKey.WLIST_UNKNOWN_LINE, "الراجحي" + t(UiKey.WLIST_AND) + "STC Pay"), walletsTotalLine(two))
    }

    @Test
    fun metaAndMark() {
        val row = walletRow(WalletNow(bank, 1), null)
        assertEquals(t(UiKey.WLIST_META_LAST4, t(UiKey.WLIST_KIND_BANK), sentenceDigits("4407")), walletMeta(row))
        assertEquals(t(UiKey.WLIST_KIND_CASH), walletMeta(walletRow(WalletNow(cash, 1), null)))
        assertEquals("ر", walletMark("مصرف الراجحي"))
        assertEquals("أ", walletMark("البنك الأهلي"))
        assertEquals("S", walletMark("stc pay"))
        assertEquals(UiKey.WLIST_KIND_ABROAD, kindLabel("own_abroad"))
    }

    private fun tx(id: String, date: String, order: Int, dir: Direction, amount: Long, wallet: String?, to: String? = null, merchant: String? = null) =
        Transaction(
            id, date, "day", order, EconomicKind.PURCHASE, false, dir, amount, Currency.SAR, false, false, ReviewState.CONFIRMED, false, "", "",
            walletId = wallet, transferToWalletId = to, rawMerchantName = merchant,
        )

    private fun month(vararg txs: Transaction, names: Map<String, List<String>> = emptyMap()) = TransactionsScreenData(
        periodForDate("2026-10-09", 28), "", txs.toList(), emptyList(), emptyMap(), names, null, null, null, null, 0, 0, 0, txs.size,
    )

    @Test
    fun walletMovesFilterByWalletNewestFirstWithTone() {
        val data = month(
            tx("a", "2026-10-01", 1, Direction.OUT, 2_500, "w-bank", merchant = "بنده"),
            tx("b", "2026-10-03", 1, Direction.IN, 1_000_000, "w-bank"),
            tx("c", "2026-10-03", 2, Direction.OUT, 50_000, "w-bank", to = "w-cash"),
            tx("d", "2026-10-04", 1, Direction.OUT, 900, "w-stc", merchant = "كافيه"),
            names = mapOf("a" to listOf("بنده للتجزئة", "بنده")),
        )
        val bankMoves = walletMoves(data, "w-bank")
        assertEquals(listOf("c", "b", "a"), bankMoves.map { it.id })
        assertEquals(AmountTone.TRANSFER, bankMoves[0].tone)
        assertEquals(AmountTone.INCOME, bankMoves[1].tone)
        assertEquals(t(UiKey.WDET_MOVE_UNNAMED), bankMoves[1].title)
        assertEquals(AmountTone.EXPENSE, bankMoves[2].tone)
        assertEquals("بنده للتجزئة", bankMoves[2].title, "اسم التاجر الأساسي قبل النص الخام")

        val cashMoves = walletMoves(data, "w-cash")
        assertEquals(listOf("c"), cashMoves.map { it.id })
        assertTrue(cashMoves[0].title.startsWith(t(UiKey.WDET_MOVE_IN_PREFIX, "").trimEnd()), "طرف التحويل الداخل بيتقال إنه وارد")
        assertEquals(1, walletMoves(data, "w-bank", limit = 1).size)
        assertTrue(walletMoves(data, "w-none").isEmpty())
    }

    @Test
    fun findWalletAndDuplicateNames() {
        val n = now(WalletNow(bank, 1))
        assertEquals(bank, findWallet(n, "w-bank")!!.wallet)
        assertNull(findWallet(n, "gone"))
        assertTrue(walletNameTaken("  الراجحي ", listOf(bank)))
        assertTrue(walletNameTaken("STC   Pay", listOf(stc)))
        assertFalse(walletNameTaken("الأهلي", listOf(bank, stc)))
        assertFalse(walletNameTaken("   ", listOf(bank)))
    }

    @Test
    fun lastFourKeepsOnlyTheLastFourDigits() {
        assertEquals(Last4("4407", true), lastFour("SA44 2000 0001 2345 6789 4407"))
        assertEquals(Last4("4407", false), lastFour("٤٤٠٧"))
        assertEquals(Last4("12", false), lastFour("1-2"))
        assertEquals(Last4("", false), lastFour("abc"))
    }

    private fun outcome(mismatches: List<BalanceMismatch>, checked: Int = 40, moves: Int = 42, without: Int = 2, unassigned: Int = 0) = ReconcileOutcome(
        bank,
        ReconcileResult(483_783, "2025-01-01", 270_725, "2026-09-04", 1, 1, moves, mismatches, checked, null),
        without, unassigned, 3,
    )

    @Test
    fun reconcileMatchSaysUntilWhenAndCounts() {
        val lines = reconcileLines(outcome(emptyList()))
        assertEquals(RecLineKind.OK, lines[0].kind)
        assertEquals(t(UiKey.WDET_REC_OK_UNTIL, fullDate("2026-09-04")!!), lines[0].text)
        assertEquals(RecLineKind.NOTE, lines[1].kind)
        assertTrue(lines[1].text.contains(sentenceNumber(40)))
        assertTrue(lines[1].text.contains(t(UiKey.WDET_MOVE_TWO)), "عدد الحركات اللي مالهاش رصيد معلن")
        assertEquals(2, lines.size)
    }

    @Test
    fun reconcileGapNamesTheFirstDifferenceWithoutClaimingCertainty() {
        val gap = BalanceMismatch(5, "2026-03-10", 10_000, 9_500, 500, null, null, sameDayCount = 3)
        val lines = reconcileLines(outcome(listOf(gap), unassigned = 1))
        assertEquals(listOf(RecLineKind.GAP_STATUS, RecLineKind.GAP_DETAIL, RecLineKind.GAP_AMOUNTS, RecLineKind.NOTE, RecLineKind.NOTE), lines.map { it.kind })
        assertEquals(t(UiKey.WDET_GAP_MANY, fullDate("2026-03-10")!!, t(UiKey.WDET_MOVE_FEW, sentenceNumber(3))), lines[1].text)
        assertEquals(t(UiKey.WDET_REC_UNASSIGNED, t(UiKey.WDET_OP_ONE)), lines[4].text)

        val single = reconcileLines(outcome(listOf(gap.copy(sameDayCount = 1)), without = 0))
        assertEquals(t(UiKey.WDET_GAP_ONE, fullDate("2026-03-10")!!), single[1].text)
        assertEquals(t(UiKey.WDET_REC_COUNTS, countText(42, MOVE_WORDS), sentenceNumber(40)), single.last().text, "كلها برصيد معلن ⇒ مفيش «بلا رصيد»")
    }

    @Test
    fun reconcileWithNothingComparableGivesNoVerdict() {
        assertTrue(reconcileLines(outcome(emptyList(), checked = 0)).isEmpty())
    }

    @Test
    fun egyptianWording() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("مفيش محافظ في البلد دي لسه", walletsTotalLine(walletsView(now(total = null), null)))
        assertTrue(reconcileLines(outcome(emptyList()))[0].text.startsWith("مطابق لحد"))
        Texts.arabicVariant = ArabicVariant.MSA
        assertTrue(reconcileLines(outcome(emptyList()))[0].text.startsWith("مطابق حتى"))
    }
}
