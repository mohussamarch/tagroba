package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.ReviewState
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.Transaction
import app.masroufy.core.emptyBackupData
import app.masroufy.port.TransactionPatch
import app.masroufy.usecase.FullBackup
import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * النسخة الشاملة على فايربيز (محاكي، بيانات وهمية) بحالة الاستخدام نفسها (`FullBackup`):
 * نسخة من حساب ⇒ استعادة في حساب فاضي ⇒ **نفس البصمة** · إعادة الاستعادة ما بتضيفش حاجة · الموجود ما يتكتبش فوقه ·
 * صفحات أكبر من 200 · معرّف إيصال فيه نقطة · نصوص البنك بتتقص · عدّاد التسويات · ملف الحساب · من غير نت بتفشل مش بتنقص.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreFullBackupTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun txn(i: Int) = Transaction(
        id = "t-${i.toString().padStart(4, '0')}", occurredAt = "2026-09-${(1 + i % 28).toString().padStart(2, '0')}", datePrecision = "day", sourceOrder = i,
        economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 1_000L + i,
        currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.SUGGESTED, isCashTagged = false,
        createdAt = "2026-09-01T00:00:00.000Z", updatedAt = "2026-09-01T00:00:00.000Z",
    )

    @Test fun backupAndRestoreThroughFirestore() = runBlocking<Unit> {
        withTimeout(180_000) {
            val a = FirestoreContainer(space())
            a.transactions.saveMany((0 until 450).map(::txn)) // 3 صفحات (200 · 200 · 50)
            a.notificationReceipts.saveMany(listOf(NotificationReceipt("budget|2026.09|total|80", 80, "2026-08-28", "x")))
            // «المستحقات» (§55): جمعية + قسط مربوط بعملية
            a.roscas.save(Rosca("rc-1", "جمعية وهمية", Currency.SAR, 100_000, 1, "2026-01-01", 10, listOf(4), 1_000_000, createdAt = "x"))
            a.roscaEntries.saveMany(listOf(RoscaEntry("e-1", "rc-1", "t-0001", RoscaEntryKind.CONTRIBUTION, 1_001)))
            val file = FullBackup(a.fullBackup).create("2026-10-01T00:00:00.000Z")
            assertEquals(450L, file.counts["transactions"], "كل الصفحات اتقرت")
            assertEquals(1L, file.counts["notificationReceipts"])
            assertEquals(1L, file.counts["roscaEntries"])

            // حساب فاضي ⇒ استعادة ⇒ نفس البصمة بالظبط
            val b = FirestoreContainer(space())
            val restore = FullBackup(b.fullBackup)
            val plan = restore.plan(file.toJsonText())
            assertEquals(453, plan.totalToAdd)
            assertEquals(453, restore.apply(plan.file).totalAdded)
            assertEquals(listOf("e-1"), b.roscaEntries.listByRosca("rc-1").map { it.id })
            assertEquals(file.checksum, FullBackup(b.fullBackup).create("2026-10-01T00:00:00.000Z").checksum, "اللي اترجع لازم يبقى هو هو")
            assertEquals(0, restore.apply(plan.file).totalAdded, "إعادة الاستعادة ما بتضيفش حاجة")

            // حساب فيه نفس العملية متعدّلة ⇒ الاستعادة ما تكتبش فوقها
            val c = FirestoreContainer(space())
            c.transactions.saveMany(listOf(txn(7)))
            c.transactions.update("t-0007", TransactionPatch(note = "بتاعتي", updatedAt = "2026-10-01T00:00:00.000Z"))
            assertEquals(452, FullBackup(c.fullBackup).apply(plan.file).totalAdded)
            assertEquals("بتاعتي", c.transactions.findByIds(listOf("t-0007")).single().note, "الموجود يفضل زي ما هو")
        }
    }

    @Test fun portDetails() = runBlocking<Unit> {
        withTimeout(60_000) {
            val s = space()
            val port = FirestoreFullBackup(s)
            val data = emptyBackupData().also {
                it.getValue("sourceRecords") += mapOf("id" to "sr-1", "rawLine" to "حوالة من حساب 1234567890123456 بنجاح", "lineNumber" to 1L)
                it.getValue("settlements") += mapOf("id" to "st-1", "transactionId" to "t-1", "obligationId" to "o-1", "amountMinor" to 500L)
            }
            assertEquals(1, port.addMissing(data)["settlements"])
            val raw = s.collection("sourceRecords").document("sr-1").get(Source.SERVER).rawData()!!
            assertFalse((raw["rawLine"] as String).contains("1234567890123456"), "رقم الحساب الكامل ما يتخزنش: ${raw["rawLine"]}")
            assertEquals(1L, s.db.document("${s.root}/concurrency/settlements").get(Source.SERVER).rawData()?.get("revision"), "تسوية جديدة ⇒ العدّاد +1")
            assertEquals(0, port.addMissing(data)["settlements"])
            assertEquals(1L, s.db.document("${s.root}/concurrency/settlements").get(Source.SERVER).rawData()?.get("revision"), "مفيش جديد ⇒ العدّاد ما يتحركش")

            assertTrue(port.addProfileIfMissing(mapOf("displayName" to "تجربة", "payday" to 28L)))
            assertFalse(port.addProfileIfMissing(mapOf("displayName" to "تاني")), "ملف الحساب ما يتكتبش فوقه")
            assertEquals("تجربة", port.readProfile()?.get("displayName"))

            // من غير نت: القراية بتفشل — مش بترجع نسخة الجهاز الناقصة
            s.db.disableNetwork()
            assertFailsWith<Throwable> { port.read() }
            s.db.enableNetwork()
        }
    }
}
