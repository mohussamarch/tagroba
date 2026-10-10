package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * مراجعة الشريحة S3 (§77-D): المجاميع التقريبية · حدود «رجعت» · الرقم المرجعي · النوع اللي المالك أكده · النسخة الشاملة. بيانات مخترعة.
 */
class ReturnsReviewTest {
    private val at = "2026-10-07T10:00:00Z"

    private fun ok(r: SmsParseResult): SmsRow = assertIs<SmsParseResult.Ok>(r).row
    private fun saudi(body: String) = ok(parseBankSms(BankSmsMessage("TESTBANK", at, body), 1))
    private fun egypt(body: String) = ok(parseEgyptBankSms(BankSmsMessage("TESTBANK", at, body), 1))

    private fun txn(id: String, date: String, dir: Direction, amount: Long = 25_000) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "w-bank",
    )

    /** P1: الفلوس اللي رجعت وعليها سؤال مستني **مش دخل** في المجاميع التقريبية اللي الرئيسية والميزانية والملخص بيستعملوها. */
    @Test fun aPendingReturnIsNotEstimatedIncome() {
        val refund = pendingRefund(txn("r1", "2026-10-05", IN), "now")
        val check = pendingReversalCheck(txn("r2", "2026-10-05", IN), "now")
        val buy = txn("o1", "2026-10-02", OUT).copy(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true)
        val view = withEstimatedKinds(listOf(refund, check, buy), emptyMap())
        val totals = computePeriodTotals(view.transactions, emptyList())
        assertEquals(0L to 25_000L, totals.incomeMinor to totals.personalExpenseMinor, "مش دخل، والشراء المؤكد زي ما هو")
        assertEquals(EconomicKind.UNCLASSIFIED to EconomicKind.UNCLASSIFIED, view.transactions[0].economicKind to view.transactions[1].economicKind)
        assertEquals(2 to 2, view.estimatedCount to view.needsReviewCount, "بتتعد «محتاجة تأكيد»")
        assertFalse(isSalaryLike(refund))
        assertFalse(isSalaryLike(check))
        // من غير السؤال: الداخل المجهول لسه بيتقدّر زي ما هو (§75-1 بتاع الشريحة S6)
        assertTrue(isSalaryLike(txn("x", "2026-10-05", IN)))
        // المالك جاوب ⇒ نوعه هو
        assertEquals(0L, computePeriodTotals(withEstimatedKinds(listOf(refund.copy(economicKind = EconomicKind.REFUND_RECEIVED, economicKindConfirmed = true)), emptyMap()).transactions, emptyList()).incomeMinor)
    }

    /** P13 · P8: «رجعت» في أول سطر بس، وجوه نفس السطر، ومش على حوالة واردة من شخص ولا عنوان استرداد. */
    @Test fun returnedWordingFalsePositives() {
        assertEquals(SmsKind.TRANSFER_IN, saudi("حوالة واردة\nمبلغ:SAR 250.00\nمن:أحمد\nراجع تطبيق البنك\nفي:26-10-05 10:00").kind)
        assertEquals(SmsKind.TRANSFER_IN, refineSmsKind("حوالة واردة\nراجع تطبيق البنك", SmsKind.TRANSFER_IN, IN))
        assertEquals(SmsKind.TRANSFER_IN, refineSmsKind("حوالة واردة\nملاحظة: تم رد السلفة", SmsKind.TRANSFER_IN, IN))
        assertEquals(SmsKind.TRANSFER_IN, refineSmsKind("تم رد السلفة من أحمد", SmsKind.TRANSFER_IN, IN), "سداد سلفة (§75-9) مش رجوع")
        assertEquals(SmsKind.REFUND, refineSmsKind("استرداد شراء\nمبلغ:SAR 25.00\nتم رد المبلغ لحسابك", SmsKind.REFUND, IN))
        assertEquals(SmsKind.REFUND, refineSmsKind("استرداد شراء بمبلغ 25 تم رد المبلغ لحسابك", SmsKind.REFUND, IN))
        assertEquals(SmsKind.REFUND, refineSmsKind("Reversed transaction at TEST STORE", SmsKind.REFUND, IN))
        assertFalse(saysReturned("حوالة واردة\nراجع تطبيق البنك"))
        // اللي رجع فعلًا لسه RETURNED — حتى لو أول كلمة «استرداد/إرجاع» (مبلغ **الحوالة** = تحويل رجع، مش استرداد من محل)
        assertEquals(SmsKind.RETURNED, refineSmsKind("حوالة صادرة مرتجعة\nمبلغ:SAR 250.00", SmsKind.REFUND, IN))
        assertEquals(SmsKind.RETURNED, refineSmsKind("استرداد مبلغ الحوالة\nمبلغ:SAR 250.00", SmsKind.REFUND, IN))
        assertEquals(SmsKind.RETURNED, refineSmsKind("إرجاع مبلغ التحويل 250.00 لحسابك", SmsKind.TRANSFER_IN, IN))
        assertTrue(saysReturned("استرداد مبلغ الحوالة"))
        assertFalse(saysReturned("استرداد شراء\nتم رد المبلغ لحسابك"))
        assertEquals(SmsKind.RETURNED, egypt("لقد تم رد EGP250.00 على بطاقتكم الائتمانية المنتهية بـ# 6604 من TEST STORE").kind)
        assertEquals("TEST STORE", egypt("لقد تم رد EGP250.00 على بطاقتكم الائتمانية المنتهية بـ# 6604 من TEST STORE").merchantName)
    }

    /** P6: التاريخ مش مرجع · مرجع الأصلية الأول · المرجع القصير ما بيلغيش لوحده. */
    @Test fun referencesAreNotDatesAndTheOriginalOneWins() {
        assertNull(smsReferenceOf("المرجع: 05/10/2026"))
        assertNull(smsReferenceOf("Ref. 2026-10-05"))
        assertEquals("••••7781", smsReferenceOf("المرجع: 05/10/2026 رقم مرجعي 553317781"), "بعد التاريخ")
        assertEquals("••••7781", smsReferenceOf("تم رد مبلغ التحويل مرجع العملية الاصلية 553317781 رقم العملية 99887766"))
        assertEquals("••••7781", smsReferenceOf("Transfer returned. Txn Ref: 99887766 Original Ref No. 553317781"))
        assertNull(smsReferenceOf("مرجع\n553317781"), "اللابل والقيمة في نفس السطر")
        assertTrue(isStrongReference("••••7781"))
        assertTrue(isStrongReference("FT26A1"))
        assertFalse(isStrongReference("A1234"))
        assertFalse(isStrongReference(null))
        val ret = txn("r", "2026-10-05", IN)
        val buy = txn("o", "2026-10-02", OUT)
        assertEquals(ReversalMatch.Check(buy), matchReversal(ret, "1234", listOf(OriginalCandidate(buy, setOf("1234"))), strongReference = false))
        assertEquals(ReversalMatch.Cancel(buy), matchReversal(ret, "1234", listOf(OriginalCandidate(buy, setOf("1234"))), strongReference = true))
    }

    /** P3: الإلغاء بيحفظ النوع اللي المالك أكده، والتراجع بيرجّعه هو — والإلغاء لوحده (نوع مش مؤكد) بيرجع «غير محددة». */
    @Test fun theOwnersKindIsKeptAndRestored() {
        val loan = txn("o", "2026-10-02", OUT).copy(economicKind = EconomicKind.LOAN_GRANTED, economicKindConfirmed = true, reviewState = ReviewState.SUGGESTED)
        val cancelled = cancelledOriginal(loan, "r", "now")
        assertEquals(EconomicKind.INTERNAL_TRANSFER to EconomicKind.LOAN_GRANTED, cancelled.economicKind to cancelled.kindBeforeReversal)
        assertEquals(ReviewState.SUGGESTED, cancelled.reviewState, "حالة مراجعة التصنيف زي ما هي")
        assertEquals(RestoredKind(EconomicKind.LOAN_GRANTED, true, ReviewState.SUGGESTED), restoredKindOf(cancelled))
        val auto = cancelledOriginal(txn("o2", "2026-10-02", OUT).copy(categoryId = "cat-shop"), "r", "now")
        assertNull(auto.kindBeforeReversal)
        assertEquals(ReviewState.CONFIRMED, auto.reviewState)
        assertEquals(RestoredKind(EconomicKind.UNCLASSIFIED, false, ReviewState.SUGGESTED), restoredKindOf(auto))
    }

    private fun row(t: Transaction): BackupRow = linkedMapOf<String, Any?>(
        "id" to t.id, "occurredAt" to t.occurredAt, "datePrecision" to "day", "sourceOrder" to 1L, "economicKind" to t.economicKind.wire,
        "economicKindConfirmed" to t.economicKindConfirmed, "observedDirection" to t.observedDirection.wire, "amountMinor" to t.amountMinor,
        "currency" to t.currency.name, "categoryConfirmed" to false, "excludedFromBudget" to false, "reviewState" to t.reviewState.wire,
        "isCashTagged" to false, "createdAt" to "c", "updatedAt" to "c",
    ).apply {
        t.reversalOfId?.let { put("reversalOfId", it) }
        t.reversedById?.let { put("reversedById", it) }
        t.kindBeforeReversal?.let { put("kindBeforeReversal", it.wire) }
        t.suggestedKind?.let { put("suggestedKind", it.wire) }
    }

    private fun data(vararg rows: BackupRow): FullBackupData = emptyBackupData().also { d -> rows.forEach { d.getValue("transactions") += it } }

    /** P11 · الإصدار القديم: رجل من الزوج اتمسحت ⇒ النسخة بتتعمل والرجل التانية بتتصلح (مش «علاقة ناقصة»). سليم ⇒ نفس الكائن. */
    @Test fun aBrokenPairIsSettledInTheBackupNotRefused() {
        val ret = cancelledReturn(txn("r", "2026-10-05", IN), "o", "now")
        val loan = cancelledOriginal(txn("o", "2026-10-02", OUT).copy(economicKind = EconomicKind.LOAN_GRANTED, economicKindConfirmed = true), "r", "now")
        val whole = data(row(ret), row(loan))
        assertSame(whole, settleReversalLinks(whole), "سليم ⇒ نفس الملف ونفس البصمة")
        checkFullBackupData(data(row(ret)))
        val onlyReturn = settleReversalLinks(data(row(ret))).getValue("transactions").single()
        assertEquals(listOf("unclassified", false, "refund_received", null), listOf(onlyReturn["economicKind"], onlyReturn["economicKindConfirmed"], onlyReturn["suggestedKind"], onlyReturn["reversalOfId"]))
        val onlyOriginal = settleReversalLinks(data(row(loan))).getValue("transactions").single()
        assertEquals(listOf("loan_granted", true, null, null), listOf(onlyOriginal["economicKind"], onlyOriginal["economicKindConfirmed"], onlyOriginal["reversedById"], onlyOriginal["kindBeforeReversal"]))
        // الأصلية اتسجلت بعد رجوعها ووقع قبل ما الرجوع يتعلّم ⇒ بيتكمّل
        val waiting = pendingRefund(txn("r2", "2026-10-05", IN), "now")
        val half = cancelledOriginal(txn("o2", "2026-10-02", OUT), "r2", "now")
        val fixed = settleReversalLinks(data(row(waiting), row(half))).getValue("transactions")
        assertEquals("o2" to "internal_transfer", fixed[0]["reversalOfId"] to fixed[0]["economicKind"])
        val again = data(*fixed.toTypedArray())
        assertSame(again, settleReversalLinks(again), "مرتين = نفس النتيجة")
    }

    /** P-merge: الترتيب في الملف ما يفرقش، والأصلية الموجودة بمعرّف تاني ما بتتلمسش ⇒ الرجوع بيتسأل «نلغي الاتنين؟». */
    @Test fun mergeOrderDoesNotMatterAndExistingOriginalsAreNotTouched() {
        val ret = cancelledReturn(txn("r", "2026-10-05", IN), "o", "now")
        val original = cancelledOriginal(txn("o", "2026-10-02", OUT), "r", "now")
        val existingOriginal = row(txn("live-o", "2026-10-02", OUT))
        for (order in listOf(listOf(row(ret), row(original)), listOf(row(original), row(ret)))) {
            val merge = mergeFullBackupDetailed(data(*order.toTypedArray()), data(existingOriginal))
            val settled = settleMergedReversals(merge, data(existingOriginal))
            val added = settled.additions.getValue("transactions")
            assertEquals(listOf("r"), added.map { it["id"] }, "الأصلية موجودة (نفس المحتوى) ⇒ ما بتتضافش")
            assertEquals(listOf("unclassified", "internal_transfer", null), listOf(added[0]["economicKind"], added[0]["suggestedKind"], added[0]["reversalOfId"]))
            checkFullBackupData(data(existingOriginal, added[0]))
        }
        // حساب فاضي ⇒ الاتنين بيتضافوا والربط زي ما هو بالترتيبين
        for (order in listOf(listOf(row(ret), row(original)), listOf(row(original), row(ret)))) {
            val added = settleMergedReversals(mergeFullBackupDetailed(data(*order.toTypedArray()), emptyBackupData()), emptyBackupData()).additions.getValue("transactions")
            assertEquals(setOf("r" to "o", "o" to null), added.map { it["id"] to it["reversalOfId"] }.toSet())
        }
    }
}
