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
import kotlin.test.assertTrue

/**
 * حقول الشريحة S3 على العملية (§75-6 · §75-12 · §77-D): `suggestedKind` · `reversalOfId` · `reversedById` · `foreignAmountMinor` · `foreignCurrency`.
 * بتتكتب **لو موجودة بس** ⇒ أي مستند قديم ونسخته هي هي بالحرف. الربط آخره `Id` ⇒ قص أرقام الحسابات وقت الكتابة ما بيلمسوش. بيانات مخترعة.
 */
class TransactionNewFieldsTest {
    private val base = Transaction(
        id = "txn-000001", occurredAt = "2026-10-05", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.INTERNAL_TRANSFER,
        economicKindConfirmed = true, observedDirection = Direction.IN, amountMinor = 25_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c",
        rawDescription = "Purchase Reversal Ref: ••••7781",
    )
    private val original = base.copy(id = "txn-000002", occurredAt = "2026-10-02", observedDirection = Direction.OUT, reversedById = "txn-000001")
    private val ret = base.copy(reversalOfId = "txn-000002")
    private val foreign = base.copy(
        id = "txn-000003", economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, observedDirection = Direction.OUT,
        amountMinor = 8_775, foreignCurrency = "USD", foreignAmountMinor = 2_340,
    )
    private val refund = base.copy(id = "txn-000004", economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, suggestedKind = EconomicKind.REFUND_RECEIVED)

    private val codec = LedgerCodecs.transactions

    @Test fun theFieldsRoundTripAndAreWrittenOnlyWhenPresent() {
        for (t in listOf(original, ret, foreign, refund)) assertEquals(t, codec.decode(codec.toStore(t)), t.id)
        val stored = codec.toStore(ret)
        assertEquals("txn-000002", stored["reversalOfId"], "المعرّف (6 أرقام ورا بعض) ما اتقصش")
        assertEquals("txn-000001", codec.toStore(original)["reversedById"])
        assertEquals("refund_received", codec.toStore(refund)["suggestedKind"])
        assertEquals("USD" to 2_340L, codec.toStore(foreign).let { it["foreignCurrency"] to it["foreignAmountMinor"] })
        // مستند من غير الحقول ⇒ هو هو بالحرف، والحقول في قايمة «اتمسحت» (الحفظ بـmerge بيمسح الفاضي)
        val plain = base.copy(rawDescription = null)
        val doc = codec.toStore(plain)
        for (key in listOf("suggestedKind", "reversalOfId", "reversedById", "foreignAmountMinor", "foreignCurrency")) {
            assertFalse(key in doc, key)
            assertTrue(key in codec.omittedFields(plain), key)
        }
        assertEquals(plain, codec.decode(doc))
        assertFailsWith<DocumentError> { codec.decode(codec.toStore(refund) + ("suggestedKind" to "refund")) }
    }

    private fun backup(vararg rows: Transaction) = emptyBackupData().also { data -> for (t in rows) data.getValue("transactions") += codec.toStore(t) }

    private fun refused(vararg rows: Map<String, Any?>): String = assertFailsWith<BackupError> {
        checkFullBackupData(emptyBackupData().also { data -> rows.forEach { data.getValue("transactions") += it } })
    }.message!!

    @Test fun theBackupCheckAcceptsTheFieldsAndRefusesBadValues() {
        checkFullBackupData(backup(original, ret, foreign, refund))
        val unsupported = { field: String -> uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, "transactions", field) }
        assertEquals(unsupported("suggestedKind"), refused(codec.toStore(refund) + ("suggestedKind" to "refund")))
        assertEquals(unsupported("foreignCurrency"), refused(codec.toStore(foreign) + ("foreignCurrency" to "usd")))
        assertEquals(unsupported("foreignCurrency"), refused(codec.toStore(foreign) + ("foreignCurrency" to "US")))
        assertEquals(unsupported("foreignAmountMinor"), refused(codec.toStore(foreign) - "foreignCurrency"))
        assertEquals(unsupported("foreignAmountMinor"), refused(codec.toStore(foreign) + ("foreignAmountMinor" to 0L)))
        assertEquals(uiText(TextKey.BACKUP_NEGATIVE_AMOUNT, "foreignAmountMinor"), refused(codec.toStore(foreign) + ("foreignAmountMinor" to -5L)))
        assertEquals(unsupported("reversalOfId"), refused(codec.toStore(ret) + ("reversalOfId" to ret.id)))
        // ربط بعملية مش في الملف
        assertEquals(uiText(TextKey.BACKUP_RELATION_MISSING, "transactions", "reversalOfId"), refused(codec.toStore(ret)))
        assertEquals(uiText(TextKey.BACKUP_RELATION_MISSING, "transactions", "reversedById"), refused(codec.toStore(original)))
        // العملة الأجنبية لوحدها (العملة معروفة والمبلغ مش مقروء بالظبط) مقبولة
        checkFullBackupData(backup(foreign.copy(foreignAmountMinor = null)))
    }

    @Test fun theKotlinFullBackupKeepsTheFields() = runBlocking<Unit> {
        val data = backup(original, ret, foreign, refund)
        val file = FullBackup(MemoryFullBackup(data)).create("2026-10-09T12:00:00.000Z")
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val plan = restore.plan(file.toJsonText())
        restore.apply(plan.file)
        val back = target.read().getValue("transactions").map { codec.decode(it) }.associateBy { it.id }
        for (t in listOf(original, ret, foreign, refund)) assertEquals(t, back[t.id], t.id)
        // للتطبيق القديم (`scripts/golden/kotlinBackupInOldApp.ts` بنفس الطريقة): الحقول الجديدة ⇒ بيقبله · «استرداد» **مؤكد** ⇒ بيرفضه (§55)
        java.io.File("build/kotlin-s3-fields-backup.json").writeText(file.toJsonText())
        val confirmedRefund = backup(original, ret, foreign, refund.copy(economicKind = EconomicKind.REFUND_RECEIVED, economicKindConfirmed = true, suggestedKind = null))
        java.io.File("build/kotlin-s3-confirmed-refund-backup.json").writeText(FullBackup(MemoryFullBackup(confirmedRefund)).create("2026-10-09T12:00:00.000Z").toJsonText())
    }
}
