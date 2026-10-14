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
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * §6.5-14 «عمليات تابعة»: `linkedExpenseId` (على الداخلة) و`linkedInflowMinor` (على المصروف) — اختياريين: بيتكتبوا لو موجودين بس ⇒ أي مستند
 * قديم ونسخته هي هي · النسخة الشاملة بتحفظهم وبترجّعهم · ربط العملية بنفسها مرفوض · المصروف اللي مش في الملف ما بيرفضش (الربط لين). بيانات مخترعة.
 */
class LinkedFieldsBackupTest {
    private val expense = Transaction(
        id = "txn-000101", occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 12_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c",
        linkedInflowMinor = 6_000,
    )
    private val inflow = expense.copy(
        id = "txn-000102", occurredAt = "2026-09-11", observedDirection = Direction.IN, amountMinor = 6_000, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, linkedInflowMinor = null, linkedExpenseId = "txn-000101",
    )
    private val codec = LedgerCodecs.transactions

    private fun backup(vararg rows: Transaction) = emptyBackupData().also { data -> for (t in rows) data.getValue("transactions") += codec.toStore(t) }

    @Test fun theFieldsRoundTripAndAreWrittenOnlyWhenPresent() {
        for (t in listOf(expense, inflow)) assertEquals(t, codec.decode(codec.toStore(t)), t.id)
        assertEquals("txn-000101", codec.toStore(inflow)["linkedExpenseId"], "المعرّف ما اتقصش")
        assertEquals(6_000L, codec.toStore(expense)["linkedInflowMinor"])
        val plain = expense.copy(linkedInflowMinor = null)
        for (key in listOf("linkedExpenseId", "linkedInflowMinor")) assertFalse(key in codec.toStore(plain), key)
        assertEquals(plain, codec.decode(codec.toStore(plain)))
    }

    @Test fun theBackupCheckAcceptsTheFieldsAndRefusesASelfLink() {
        checkFullBackupData(backup(expense, inflow))
        checkFullBackupData(backup(inflow))
        val refused = assertFailsWith<BackupError> {
            checkFullBackupData(emptyBackupData().also { it.getValue("transactions") += codec.toStore(inflow) + ("linkedExpenseId" to inflow.id) })
        }
        assertEquals(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, "transactions", "linkedExpenseId"), refused.message)
        assertFailsWith<BackupError> { checkFullBackupData(backup(expense.copy(linkedInflowMinor = -1))) }
    }

    @Test fun theFullBackupKeepsTheLinkBothWays() = runBlocking<Unit> {
        val file = FullBackup(MemoryFullBackup(backup(expense, inflow))).create("2026-10-15T01:00:00.000Z")
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        restore.apply(restore.plan(file.toJsonText()).file)
        val back = target.read().getValue("transactions").map { codec.decode(it) }.associateBy { it.id }
        assertEquals(expense, back[expense.id])
        assertEquals(inflow, back[inflow.id])
        // للتطبيق القديم (نفس طريقة `kotlin-s3-fields-backup.json`): حقول زيادة بس
        java.io.File("build/kotlin-linked-inflows-backup.json").writeText(file.toJsonText())
    }
}
