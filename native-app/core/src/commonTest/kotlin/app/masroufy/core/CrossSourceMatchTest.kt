package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §75-10 (قرار المالك 2026-10-08): سطر الكشف ورسالة البنك لنفس الحركة = عملية واحدة لو الفرق يومين بالكتير — الحكم النقي
 * ([matchCrossSource]) وفحص النسخة الشاملة ([matchingBackupProblem]). كل الأرقام والمعرّفات وهمية.
 */
class CrossSourceMatchTest {
    private fun sms(id: String, date: IsoDate, amount: Long = 10_000, merged: Boolean = false, refs: Set<String> = emptySet(), hashes: List<String> = emptyList()) =
        CrossSourceExisting(id, date, amount, Direction.OUT, fromSms = true, fromStatement = merged, references = refs, hashes = hashes)

    private fun statement(id: String, date: IsoDate, amount: Long = 10_000) = CrossSourceExisting(id, date, amount, Direction.OUT, fromSms = false, fromStatement = true)

    /** سطر كشف وارد (`fromSms = false`) بحكم منع التكرار العادي. */
    private fun line(n: Int, date: IsoDate, amount: Long = 10_000, state: MatchingState = MatchingState.NEW, matched: Id? = null, ref: String? = null, hash: String? = null, fromSms: Boolean = false) =
        CrossSourceRow(n, date, amount, Direction.OUT, fromSms, ref, state, matched, hash)

    @Test fun oneCandidateWithinTwoDaysMerges() {
        val out = matchCrossSource(listOf(line(1, "2026-10-02")), listOf(sms("t-sms", "2026-10-01")), CROSS_SOURCE_WINDOW_DAYS)
        assertEquals(CrossSourceVerdict.Merge("t-sms"), out[1])
        // والعكس: رسالة وارد وعملية من الكشف
        val back = matchCrossSource(listOf(line(1, "2026-10-01", fromSms = true)), listOf(statement("t-st", "2026-10-03")), 2)
        assertEquals(CrossSourceVerdict.Merge("t-st"), back[1])
    }

    @Test fun threeDaysApartIsNotTheSameMovement() {
        assertTrue(matchCrossSource(listOf(line(1, "2026-10-04")), listOf(sms("t-sms", "2026-10-01")), 2).isEmpty())
        assertTrue(matchCrossSource(listOf(line(1, "2026-09-28")), listOf(sms("t-sms", "2026-10-01")), 2).isEmpty())
    }

    @Test fun differentAmountDirectionOrSameSourceNeverMerges() {
        assertTrue(matchCrossSource(listOf(line(1, "2026-10-01", amount = 10_001)), listOf(sms("t", "2026-10-01")), 2).isEmpty())
        val incoming = CrossSourceRow(1, "2026-10-01", 10_000, Direction.IN, false, null, MatchingState.NEW)
        assertTrue(matchCrossSource(listOf(incoming), listOf(sms("t", "2026-10-01")), 2).isEmpty())
        // رسالتين بنفس المبلغ ممكن يبقوا عمليتين فعلًا — مش شغلة الدمج
        assertTrue(matchCrossSource(listOf(line(1, "2026-10-01", fromSms = true)), listOf(sms("t", "2026-10-01")), 2).isEmpty())
        assertTrue(matchCrossSource(listOf(line(1, "2026-10-01")), listOf(statement("t", "2026-10-01")), 2).isEmpty())
    }

    @Test fun twoCandidatesStaySimilarOnTheNearest() {
        val out = matchCrossSource(listOf(line(1, "2026-10-02")), listOf(sms("t-far", "2026-10-04"), sms("t-near", "2026-10-01")), 2)
        // ومعاه كل الاحتمالات (الأقرب الأول) عشان المالك يختار «هي دي»
        assertEquals(CrossSourceVerdict.Ambiguous("t-near", listOf("t-near", "t-far")), out[1])
    }

    @Test fun eachExistingTransactionAbsorbsOneRowAtMost() {
        // سطرين في الكشف بنفس المبلغ ورسالة واحدة ⇒ الاتنين بيسألوا (ما بنختارش بالتخمين)
        val out = matchCrossSource(listOf(line(1, "2026-10-01"), line(2, "2026-10-02")), listOf(sms("t-sms", "2026-10-01")), 2)
        assertEquals(CrossSourceVerdict.Ambiguous("t-sms", listOf("t-sms")), out[1])
        assertEquals(CrossSourceVerdict.Ambiguous("t-sms", listOf("t-sms")), out[2])
        // واللي اتدمجت قبل كده ما بتبلعش تاني
        assertTrue(matchCrossSource(listOf(line(3, "2026-10-01")), listOf(sms("t-sms", "2026-10-01", merged = true)), 2).isEmpty())
    }

    @Test fun similarRowMergesOnlyIntoItsOwnMatch() {
        val same = matchCrossSource(listOf(line(1, "2026-10-01", state = MatchingState.SIMILAR, matched = "t-sms")), listOf(sms("t-sms", "2026-10-01")), 2)
        assertEquals(CrossSourceVerdict.Merge("t-sms"), same[1])
        val other = matchCrossSource(listOf(line(1, "2026-10-01", state = MatchingState.SIMILAR, matched = "t-other")), listOf(sms("t-sms", "2026-10-01")), 2)
        assertNull(other[1])
        for (state in listOf(MatchingState.DUPLICATE, MatchingState.CONFLICT, MatchingState.INVALID)) {
            assertNull(matchCrossSource(listOf(line(1, "2026-10-01", state = state)), listOf(sms("t-sms", "2026-10-01")), 2)[1], state.wire)
        }
    }

    @Test fun alreadyMergedByReferenceOrByTheSameText() {
        val merged = sms("t-m", "2026-10-02", merged = true, refs = setOf("SMS:ABC"), hashes = listOf("H-LINE"))
        val byRef = matchCrossSource(listOf(line(1, "2026-10-01", ref = " SMS:ABC ", fromSms = true)), listOf(merged), 2)
        assertEquals(CrossSourceVerdict.AlreadyMerged("t-m"), byRef[1])
        // نفس المرجع بمبلغ تاني = تعارض حقيقي ⇒ ما بيتغطاش
        assertNull(matchCrossSource(listOf(line(1, "2026-10-01", amount = 9_000, ref = "SMS:ABC", fromSms = true)), listOf(merged), 2)[1])
        // سطر الكشف نفسه بالحرف (من غير مرجع ولا رصيد) — مرة واحدة بس لكل بصمة
        val twice = matchCrossSource(listOf(line(1, "2026-10-02", hash = "H-LINE"), line(2, "2026-10-02", hash = "H-LINE")), listOf(merged), 2)
        assertEquals(CrossSourceVerdict.AlreadyMerged("t-m"), twice[1])
        assertNull(twice[2], "التاني بيكمّل في منع التكرار العادي")
        // العملية اللي لسه ما اتدمجتش: نص سجلها ما بيحكمش (منع التكرار العادي بيعرفه)
        assertNull(matchCrossSource(listOf(line(1, "2026-10-09", hash = "H-X")), listOf(sms("t", "2026-10-01", hashes = listOf("H-X"))), 2)[1])
    }

    /** مراجعة S4: النص لوحده بيتشابه بين عمليتين مختلفتين فعلًا ⇒ بيحكم بس لسطر كشف من غير مرجع، وعلى نفس الحركة. */
    @Test fun theSameTextAloneNeverDropsAnotherMovement() {
        val merged = sms("t-m", "2026-10-02", merged = true, refs = setOf("SMS:ABC"), hashes = listOf("H-LINE"))
        // رسالة نصها زي نص سجل اتدمج (شراءين بنفس المبلغ في نفس اليوم) ⇒ مش «متسجلة خلاص» — مرجعها هو اللي بيحكم
        assertNull(matchCrossSource(listOf(line(1, "2026-10-02", ref = "SMS:OTHER", hash = "H-LINE", fromSms = true)), listOf(merged), 2)[1])
        assertNull(matchCrossSource(listOf(line(1, "2026-10-02", hash = "H-LINE", fromSms = true)), listOf(merged), 2)[1])
        // سطر كشف ليه مرجع: المرجع بيحسم مش النص
        assertNull(matchCrossSource(listOf(line(1, "2026-10-02", ref = "REF-9", hash = "H-LINE")), listOf(merged), 2)[1])
        // نفس النص بس مبلغ تاني · برّه النافذة · اتجاه تاني ⇒ مش نفس الحركة
        assertNull(matchCrossSource(listOf(line(1, "2026-10-02", amount = 7_500, hash = "H-LINE")), listOf(merged), 2)[1])
        assertNull(matchCrossSource(listOf(line(1, "2026-10-05", hash = "H-LINE")), listOf(merged), 2)[1])
        val incoming = CrossSourceRow(1, "2026-10-02", 10_000, Direction.IN, false, null, MatchingState.NEW, hash = "H-LINE")
        assertNull(matchCrossSource(listOf(incoming), listOf(merged), 2)[1])
        // عمليتين مدموجتين بنفس النص: السطر بياخد اللي **نفس حركته** (مش أول واحدة في الطابور)
        val other = sms("t-other", "2026-10-02", amount = 7_500, merged = true, hashes = listOf("H-LINE"))
        val pick = matchCrossSource(listOf(line(1, "2026-10-02", amount = 7_500, hash = "H-LINE"), line(2, "2026-10-02", hash = "H-LINE")), listOf(merged, other), 2)
        assertEquals(CrossSourceVerdict.AlreadyMerged("t-other"), pick[1])
        assertEquals(CrossSourceVerdict.AlreadyMerged("t-m"), pick[2])
    }

    @Test fun windowZeroIsSameDayAndNegativeIsRejected() {
        assertEquals(CrossSourceVerdict.Merge("t"), matchCrossSource(listOf(line(1, "2026-10-01")), listOf(sms("t", "2026-10-01")), 0)[1])
        assertTrue(matchCrossSource(listOf(line(1, "2026-10-02")), listOf(sms("t", "2026-10-01")), 0).isEmpty())
        assertFailsWith<IllegalArgumentException> { matchCrossSource(emptyList(), emptyList(), -1) }
    }

    @Test fun backupChecksTheMergeRecordAndDismissedLists() {
        val record = mapOf<String, Any?>("transactionId" to "t-1", "matchingState" to "duplicate")
        assertNull(matchingBackupProblem("sourceRecords", record))
        assertNull(matchingBackupProblem("sourceRecords", record + ("mergeUndo" to mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 3.0, "statedBalanceMinor" to null))))
        assertNull(matchingBackupProblem("sourceRecords", record + ("mergeUndo" to mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 3L, "statedBalanceMinor" to -500L))))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", record + ("mergeUndo" to "2026-10-01")))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", record + ("mergeUndo" to mapOf("occurredAt" to "2026-13-01", "sourceOrder" to 3L))))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", record + ("mergeUndo" to mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1.5))))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", record + ("mergeUndo" to mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1L, "statedBalanceMinor" to 0.5))))
        // اللي سطر الكشف كتبه (مراجعة S4) — اختياري، ولو موجود لازم يبقى صح
        val wrote = mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1L, "mergedOccurredAt" to "2026-10-02", "mergedStatedBalanceMinor" to 490_000L)
        assertNull(matchingBackupProblem("sourceRecords", record + ("mergeUndo" to wrote)))
        assertNull(matchingBackupProblem("sourceRecords", record + ("mergeUndo" to wrote + ("mergedStatedBalanceMinor" to null))))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", record + ("mergeUndo" to wrote + ("mergedOccurredAt" to "2026-02-30"))))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", record + ("mergeUndo" to wrote + ("mergedOccurredAt" to 20261002L))))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", record + ("mergeUndo" to wrote + ("mergedStatedBalanceMinor" to 0.5))))
        // سجل الدمج لازم يبقى «مكرر» مربوط بعملية
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", mapOf("transactionId" to null, "matchingState" to "duplicate", "mergeUndo" to mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1L))))
        assertEquals("mergeUndo", matchingBackupProblem("sourceRecords", mapOf("transactionId" to "t", "matchingState" to "new", "mergeUndo" to mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1L))))
        for (group in listOf("roscas", "installmentPlans")) {
            assertNull(matchingBackupProblem(group, mapOf("dismissedTxnIds" to listOf("t-1", "t-2"))))
            assertNull(matchingBackupProblem(group, emptyMap()))
            assertEquals("dismissedTxnIds", matchingBackupProblem(group, mapOf("dismissedTxnIds" to "t-1")))
            assertEquals("dismissedTxnIds", matchingBackupProblem(group, mapOf("dismissedTxnIds" to listOf("t-1", 2L))))
            assertEquals("dismissedTxnIds", matchingBackupProblem(group, mapOf("dismissedTxnIds" to listOf(""))))
        }
    }
}
