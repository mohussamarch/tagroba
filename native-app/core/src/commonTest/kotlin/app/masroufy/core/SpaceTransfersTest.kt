package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** التحويل لنفسك بين بلدين (§64) — المنطق النقي، مبالغ مخترعة. بيشتغل على محاكي الآيفون كمان. */
class SpaceTransfersTest {
    private val sa = defaultSpace()
    private val eg = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
    private val saWallet = Wallet("wallet-bank", "بنك", Currency.SAR, "bank", 0, "2025-01-01")
    private val egWallet = Wallet("wallet-bank", "بنك", Currency.EGP, "bank", 0, "2026-01-01")

    private fun txn(id: String, dir: Direction, amount: Long, currency: Currency) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "wallet-bank",
    )

    private val out = SpaceLeg(sa, txn("t-1", Direction.OUT, 100_000, Currency.SAR), saWallet)
    private val inn = SpaceLeg(eg, txn("t-1", Direction.IN, 1_234_560, Currency.EGP), egWallet)

    @Test fun aValidPairPassesEvenWithTheSameTransactionIdInBothCountries() {
        checkSpaceTransfer(out, inn, emptyList(), emptySet())
        assertEquals("stx-default-t-1", spaceTransferId(sa.id, "t-1"))
    }

    @Test fun eachRuleRejectsItsCase() {
        fun rejects(why: String, block: () -> Unit) = assertFailsWith<SpaceTransferError>(why) { block() }
        rejects("نفس البلد") { checkSpaceTransfer(out, inn.copy(space = sa), emptyList(), emptySet()) }
        rejects("الطالعة واردة") { checkSpaceTransfer(out.copy(transaction = out.transaction.copy(observedDirection = Direction.IN)), inn, emptyList(), emptySet()) }
        rejects("الداخلة صادرة") { checkSpaceTransfer(out, inn.copy(transaction = inn.transaction.copy(observedDirection = Direction.OUT)), emptyList(), emptySet()) }
        rejects("محفظة مش في البلد") { checkSpaceTransfer(out, inn.copy(wallet = null), emptyList(), emptySet()) }
        rejects("عملة غير المحفظة") { checkSpaceTransfer(out, inn.copy(wallet = egWallet.copy(currency = Currency.SAR)), emptyList(), emptySet()) }
        val pair = SpaceTransfer("stx-x", "default", "t-9", 1, Currency.SAR, "eg", "t-1", 1, Currency.EGP, "c")
        rejects("الداخلة في زوج تاني") { checkSpaceTransfer(out, inn, listOf(pair), emptySet()) }
        rejects("متربطة بحاجة تانية") { checkSpaceTransfer(out, inn, emptyList(), setOf("default" to "t-1")) }
        // نفس المعرّف في البلد التانية مش هو نفس العملية
        checkSpaceTransfer(out, inn, listOf(pair.copy(toSpaceId = "ae")), setOf("eg" to "t-other"))
    }

    @Test fun aLegIsNeitherExpenseNorIncomeAndUnlinkingAsksAgain() {
        val leg = asSpaceTransferLeg(out.transaction.copy(transferToWalletId = "wallet-cash"), "2026-10-04T10:00:00.000Z")
        assertEquals(EconomicKind.INTERNAL_TRANSFER, leg.economicKind)
        assertFalse(countsAsIncome(leg.economicKind) || countsAsPersonalExpense(leg.economicKind))
        assertEquals(ReviewState.CONFIRMED, leg.reviewState)
        assertNull(leg.transferToWalletId, "الفلوس راحت لبلد تانية — مش لمحفظة هنا")
        assertEquals(out.transaction.amountMinor to out.transaction.currency, leg.amountMinor to leg.currency, "المبلغ والعملة زي الكشف")
        val back = asUnlinkedLeg(leg, "2026-10-05T10:00:00.000Z")
        assertEquals(EconomicKind.UNCLASSIFIED, back.economicKind)
        assertFalse(back.economicKindConfirmed)
        assertEquals(ReviewState.NEEDS_REVIEW, back.reviewState)
    }

    @Test fun backupPairsMustMatchTheirTransactionsExactly() {
        val txns = mapOf(
            DEFAULT_SPACE_ID to mapOf("t-1" to mapOf<String, Any?>("id" to "t-1", "observedDirection" to "out", "amountMinor" to 100_000L, "currency" to "SAR")),
            "eg" to mapOf("t-1" to mapOf<String, Any?>("id" to "t-1", "observedDirection" to "in", "amountMinor" to 1_234_560L, "currency" to "EGP")),
        )
        val ok = mapOf<String, Any?>(
            "id" to "stx-default-t-1", "fromSpaceId" to DEFAULT_SPACE_ID, "fromTransactionId" to "t-1", "fromAmountMinor" to 100_000L, "fromCurrency" to "SAR",
            "toSpaceId" to "eg", "toTransactionId" to "t-1", "toAmountMinor" to 1_234_560L, "toCurrency" to "EGP", "createdAt" to "c",
        )
        checkSpaceTransferRows(listOf(ok), txns)
        fun rejects(why: String, row: Map<String, Any?>, rows: List<Map<String, Any?>> = listOf(row)) =
            assertFailsWith<BackupError>(why) { checkSpaceTransferRows(rows, txns) }
        rejects("مبلغ غير العملية", ok + ("toAmountMinor" to 1_234_561L))
        rejects("عملة غير العملية", ok + ("toCurrency" to "SAR"))
        rejects("الاتجاهين معكوسين", ok + mapOf("fromSpaceId" to "eg", "toSpaceId" to DEFAULT_SPACE_ID, "id" to "stx-eg-t-1"))
        rejects("عملية مش موجودة", ok + ("toTransactionId" to "t-9"))
        rejects("معرّف مش من الرجل الطالعة", ok + ("id" to "stx-x"))
        rejects("نفس البلد", ok + ("toSpaceId" to DEFAULT_SPACE_ID))
        rejects("مبلغ سالب", ok + ("fromAmountMinor" to -1L))
        rejects("نفس الرجل في زوجين", ok, listOf(ok, ok + ("id" to "stx-default-t-1")))
    }

    @Test fun theRateIsTextOnlyFromIntegers() {
        val pair = SpaceTransfer("stx", "default", "t-1", 100_000, Currency.SAR, "eg", "t-1", 1_234_560, Currency.EGP, "c")
        assertEquals("1 SAR = 12.3456 EGP", spaceTransferRateText(pair))
        assertEquals("1 SAR = 0.3333 EGP", spaceTransferRateText(pair.copy(fromAmountMinor = 3, toAmountMinor = 1)))
        assertEquals("1 SAR = 0.6667 EGP", spaceTransferRateText(pair.copy(fromAmountMinor = 3, toAmountMinor = 2)), "نص لفوق")
        assertEquals("1 SAR = 1.0000 EGP", spaceTransferRateText(pair.copy(fromAmountMinor = 99_999, toAmountMinor = 99_999)))
        assertEquals("1 SAR = 1.0000 EGP", spaceTransferRateText(pair.copy(fromAmountMinor = 1_000_000, toAmountMinor = 999_999)), "التقريب بيرحّل للرقم الصحيح")
        assertEquals("1 SAR = 9007199254740991.0000 EGP", spaceTransferRateText(pair.copy(fromAmountMinor = 1, toAmountMinor = MAX_SAFE_HALALAS)), "من غير فيض")
    }
}
