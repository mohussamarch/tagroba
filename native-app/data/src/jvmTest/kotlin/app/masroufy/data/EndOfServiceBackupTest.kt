package app.masroufy.data

import app.masroufy.core.BackupError
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.emptyBackupData
import app.masroufy.core.uiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** النوع الجديد «مكافأة نهاية خدمة» (رد المالك §64-٧) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class EndOfServiceBackupTest {
    private val benefit = Transaction(
        id = "t-eos", occurredAt = "2026-10-03", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.END_OF_SERVICE,
        economicKindConfirmed = true, observedDirection = Direction.IN, amountMinor = 9_000_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c",
    )

    @Test
    fun endOfServiceIsStoredAndAcceptedInABackup() {
        val doc = LedgerCodecs.transactions.toStore(benefit)
        assertEquals("end_of_service", doc["economicKind"])
        assertEquals(benefit, LedgerCodecs.transactions.decode(doc))
        val data = emptyBackupData().also { it.getValue("transactions") += doc }
        checkFullBackupData(data)
        val bad = emptyBackupData().also { it.getValue("transactions") += doc + ("economicKind" to "end_of_services") }
        assertEquals(uiText(TextKey.BACKUP_KIND_INVALID), assertFailsWith<BackupError> { checkFullBackupData(bad) }.message)
    }
}
