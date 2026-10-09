package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الطرف التاني في التحويل (OVERRIDES §60) — **كل الأسماء والأرقام مخترعة**، بنفس أشكال كشفين المالك
 * (الراجحي CSV بالعربي المقلوب، وQNB PDF). على الكشف الحقيقي: الراجحي 418 من 418، وQNB 17 من 28 (الباقي اسمه ضاع من ملف البنك).
 */
class TransferPartiesTest {
    private var seq = 0

    private fun t(op: String?, desc: String, dir: Direction = Direction.OUT, date: String = "2026-01-10") = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = desc, sourceOperationType = op,
    )

    // «علي» بحروف العرض ومقلوبة زي كشف الراجحي القديم
    private val aliVisual = "ﻲﻠﻋ"
    private val internalOp = "عملية تحويل داخلية"

    @Test fun visualArabicIsTurnedBackAndOnlyThat() {
        assertEquals("علي", nfkc(logicalArabic(aliVisual)))
        // العربي العادي جوه نفس الوصف ما بيتقلبش
        assertEquals("[رقم] علي", nfkc(logicalArabic("[رقم] $aliVisual")))
        assertEquals("محمد", logicalArabic("محمد"), "نص من غير حروف عرض بيرجع زي ما هو")
    }

    @Test fun alrajhiAccountTransferKeepsLastFourOnlyAndSurvivesStorage() {
        val full = t(internalOp, "${aliVisual}W-/FRACCT/12345678901234567FR:ملاحظة ,10:00:00:الوقت", Direction.IN)
        val party = transferPartyOf(full)!!
        assertEquals(TransferPartyRef("علي#4567", "علي", "4567"), party)
        // اللي في فايربيز: الرقم متقص — نفس المفتاح
        val stored = full.copy(rawDescription = full.rawDescription!!.replace("12345678901234567", "****4567"))
        assertEquals(party, transferPartyOf(stored))
        assertFalse(party.key.contains("1234567"), "مفيش رقم كامل في المفتاح")
    }

    @Test fun latinNameAfterTheAccountLosesTheDirectionMark() {
        val from = transferPartyOf(t(internalOp, "W-/FRACCT/98765432109876543FRSAMI:ملاحظة", Direction.IN))!!
        val to = transferPartyOf(t(internalOp, "W-/TOACCT/98765432109876543TOSAMI:ملاحظة"))!!
        assertEquals("SAMI", from.label)
        assertEquals(from.key, to.key, "نفس الطرف رايح وجاي")
    }

    @Test fun walletTopUpAndWalletIncomingAreTheSameParty() {
        val out = transferPartyOf(t("تحويل الى محفظة دراهم", "المحفظة/W1a2b3c:ملاحظة"))!!
        val inc = transferPartyOf(t("حوالة واردة داخل الراجحي", ":ملاحظة | B1B/FRACCT/SA1234567890123456789012Drahim/B1B", Direction.IN))!!
        assertEquals("محفظة دراهم", out.label)
        assertEquals(out.key, inc.key)
    }

    @Test fun incomingAndOutgoingTransfersByName() {
        assertEquals("سامي علي", transferPartyOf(t("حوالة فورية صادرة", "سامي علي/[رقم]:ملاحظة ,10:00:00:الوقت"))!!.label)
        assertEquals("SAMI ALI", transferPartyOf(t("حواالت فورية واردة", ":ملاحظة | 12345678SAARNBARNB1B12345678901234/SAMI ALI | x", Direction.IN))!!.label)
        assertEquals("شركة مثال", transferPartyOf(t("حواالت سريع الواردة", "PAYROLL-PA1234:ملاحظة | سامي-INMAINM1234567RJ-شركة مثال | x", Direction.IN))!!.label)
        assertEquals("SAMI ALI", transferPartyOf(t("حوالة واردة دولية - فيزا", "PAYPAL*SAMI ALI /SG:ملاحظة", Direction.IN))!!.label)
    }

    @Test fun qnbNameIsCutFromItsCode() {
        val inc = transferPartyOf(t("IPN TRANSFER", "IPN TRANSFER-VC••••1234-IPNTEST_PERSONa1b2c3d4e5f6ACC -2026-01-", Direction.IN))!!
        assertEquals("TEST PERSON", inc.label)
        // الصادر قبله اسم صاحب الحساب — الطرف هو اللي بعد IPN
        val out = transferPartyOf(t("IPN TRANSFER", "IPN TRANSFER-VC••••1234-OWNER NAME- IPNTEST_PERSON9f8e••••7d6cACC-2026-01-03"))!!
        assertEquals(inc.key, out.key)
        assertEquals("SAMPLE NAME", transferPartyOf(t("IPN TRANSFER", "IPN TRANSFER-VC••••1234-IPNSAMPLE_NAMEc1d2e3f4a5b6CAR -2026"))!!.label)
        // الاسم العربي ضاع من ملف البنك ⇒ مفيش طرف (ما بنخمّنش)
        assertNull(transferPartyOf(t("IPN TRANSFER", "IPN TRANSFER-VC••••1234-OWNER- IPN???? _????_??1a2b3c4dACC-2026")))
    }

    @Test fun feesAndPurchasesAreNotTransfers() {
        assertFalse(isTransferLike(t("رسوم حوالة فورية صادرة", "سامي علي/[رقم]:ملاحظة")))
        assertNull(transferPartyOf(t("شراء انترنت (محلي)", "متجر/123")))
        assertTrue(isTransferLike(t(null, "IPN TRANSFER-VC••••1234-IPNTEST_PERSONa1b2c3ACC")), "من غير نوع عملية ⇒ من الوصف")
    }

    @Test fun fiveTransfersInOneMonthRaiseTheQuestionOnce() {
        val desc = "${aliVisual}W-/TOACCT/12345678901234567TO:ملاحظة"
        val five = (1..5).map { t(internalOp, desc, if (it % 2 == 0) Direction.IN else Direction.OUT, "2026-02-0$it") }
        val spread = (1..4).map { t(internalOp, "W-/TOACCT/11112222333344445TOSAMI:x", date = "2026-0$it-01") } +
            (1..4).map { t(internalOp, "W-/TOACCT/11112222333344445TOSAMI:x", date = "2026-05-0$it") }
        val found = suspiciousTransferParties(five + spread + t(internalOp, desc, date = "2026-03-01"), emptySet())
        assertEquals(1, found.size, "4 في الشهر مش كفاية حتى لو المجموع أكتر")
        assertEquals(SuspiciousParty(TransferPartyRef("علي#4567", "علي", "4567"), "2026-02", 5, 2, 3), found.single())
        assertTrue(suspiciousTransferParties(five, setOf("علي#4567")).isEmpty(), "الطرف اللي اتقرر فيه ما يتسألش تاني")
    }

    private fun decided(verdict: TransferVerdict) = TransferParty("علي#4567", "علي", "4567", verdict, if (verdict == TransferVerdict.PERSON) "p-1" else null)

    /** قرار المالك §75-5: الصادر لشخص بيتسأل «سلفة ولا دعم؟» كل مرة — مش «دعم» مؤكد لوحده زي §60. */
    @Test fun personVerdictLeavesOutgoingUndecidedAndWaiting() {
        val now = "2026-10-08T00:00:00.000Z"
        val suggested = t(internalOp, "x").copy(economicKind = EconomicKind.PURCHASE, reviewState = ReviewState.SUGGESTED)
        val out = applyTransferVerdict(suggested, decided(TransferVerdict.PERSON), now)
        assertEquals(EconomicKind.UNCLASSIFIED, out.economicKind, "النوع المقترح بيتشال — التحويل لشخص مش شراء")
        assertFalse(out.economicKindConfirmed)
        assertEquals(ReviewState.NEEDS_REVIEW to now, out.reviewState to out.updatedAt)
        // مستنية أصلًا ⇒ نفس العملية (مفيش كتابة على الفاضي)
        val waiting = t(internalOp, "x")
        assertTrue(applyTransferVerdict(waiting, decided(TransferVerdict.PERSON), now) === waiting)
        // اللي المالك أكده — ومنه «دعم» اتحط لوحده قبل §75-5 — ما بيتلمسش
        val oldSupport = t(internalOp, "x").copy(economicKind = EconomicKind.SUPPORT_GIFT, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED)
        assertTrue(applyTransferVerdict(oldSupport, decided(TransferVerdict.PERSON), now) === oldSupport)
        // الوارد زي ما هو: بيتسأل ونوعه ما بيتغيرش
        val incoming = t(internalOp, "x", Direction.IN).copy(reviewState = ReviewState.SUGGESTED)
        assertEquals(EconomicKind.UNCLASSIFIED to ReviewState.NEEDS_REVIEW, applyTransferVerdict(incoming, decided(TransferVerdict.PERSON), now).let { it.economicKind to it.reviewState })
    }

    @Test fun ownAccountAndDismissedDidNotChange() {
        val now = "2026-10-08T00:00:00.000Z"
        val loan = t(internalOp, "x").copy(economicKind = EconomicKind.LOAN_GRANTED, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED)
        val own = applyTransferVerdict(loan, decided(TransferVerdict.OWN_ACCOUNT), now)
        assertEquals(Triple(EconomicKind.INTERNAL_TRANSFER, true, ReviewState.CONFIRMED), Triple(own.economicKind, own.economicKindConfirmed, own.reviewState))
        val open = t(internalOp, "x")
        assertTrue(applyTransferVerdict(open, decided(TransferVerdict.DISMISSED), now) === open)
        assertTrue(applyTransferVerdict(open, null, now) === open)
    }
}
