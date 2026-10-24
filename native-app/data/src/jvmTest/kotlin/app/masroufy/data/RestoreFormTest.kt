package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.MAX_BACKUP_STRING
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.withinBackupLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PRIVACY-3: الاسترجاع بيكتب نفس شكل الكتابة العادية — مفاتيح المحوّل بس، وكل النصوص متقصوصة. **كل البيانات مخترعة.** */
class RestoreFormTest {
    private val txn = Transaction(
        id = "t-1", occurredAt = "2026-02-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = 1_250, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "c", updatedAt = "c",
        rawDescription = "شراء بطاقة 4111 2222 3333 4444", note = "حساب 9876543210",
    )

    @Test fun unknownKeysDropAndEveryStringIsRedacted() {
        val row = LedgerCodecs.transactions.toStore(txn) + mapOf("smuggled" to "IBAN SA0000000000001234567890", "categoryId" to "cat-food")
        val out = restoreForm("transactions", row)
        assertFalse("smuggled" in out, "مفتاح مش في المحوّل ما بيتكتبش")
        assertEquals("cat-food", out["categoryId"])
        val desc = out["rawDescription"] as String
        assertFalse("4111 2222 3333" in desc, desc)
        assertTrue(desc.endsWith("4444"), desc)
        assertEquals("حساب ****3210", out["note"], "القص العادي على أي نص (مش نص البنك بس)")
        assertEquals(1_250L, out["amountMinor"])
    }

    /** PRIVACY-2: القص العادي بقى شايف الأرقام المقسّمة بمسافات وشرط — والتواريخ والتليفونات والمبالغ القصيرة زي ما هي. */
    @Test fun storeFormSeesSpacedAndDashedNumbers() {
        assertEquals("بطاقة ****3456", sanitizeAccountNumbers("بطاقة 1234 5678 9012 3456"))
        assertEquals("حساب ****7519", sanitizeAccountNumbers("حساب 8000-0000-6080-1016-7519"))
        assertEquals("حساب ****3210", sanitizeAccountNumbers("حساب 9876543210"))
        for (same in listOf("2026-10-01T10:00:00.000Z", "055 123 4567", "صفحة 3 · 2025-01-05", "1,250.00 ريال")) assertEquals(same, sanitizeAccountNumbers(same))
    }

    @Test fun otherGroupsAreUntouchedAndLimitsHold() {
        val row = mapOf("id" to "x-1", "label" to "اسم 123456")
        assertEquals(row, restoreForm("people", row))
        assertTrue(withinBackupLimits(row, 1))
        assertFalse(withinBackupLimits(mapOf("t" to "x".repeat(MAX_BACKUP_STRING + 1)), 1))
        var deep: Any = "leaf"
        repeat(10) { deep = listOf(deep) }
        assertFalse(withinBackupLimits(mapOf("d" to deep), 1), "تداخل عميق بزيادة")
    }
}
