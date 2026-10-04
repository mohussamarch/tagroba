package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.INCOME_SOURCES_GROUP
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** مصادر الدخل (§48 · §64) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class IncomeBackupTest {
    private val closed = IncomeSource(
        "inc-1", "شركة النجمة الوهمية", "شركة النجمة الوهمية", IncomeSourceKind.JOB, Currency.SAR, "2024-01-01", "2026-09-30", 27, 900_000,
        "2024-01-01T00:00:00.000Z", payerKeys = listOf("شركةالنجمةالوهمية"), declinedPayerKeys = listOf("سامي#4567"),
    )
    private val open = IncomeSource("inc-2", "عميل وهمي", "عميل وهمي", IncomeSourceKind.CLIENT, Currency.EGP, "2026-10-01", createdAt = "c")

    private fun roundTrip(value: IncomeSource): Doc {
        val d = IncomeCodecs.incomeSources.toStore(value)
        assertEquals(value, IncomeCodecs.incomeSources.decode(d))
        return d
    }

    @Test
    fun `المصدر بيتكتب ويتقرا والفاضي ما بيتكتبش`() {
        assertEquals(listOf("شركةالنجمةالوهمية"), roundTrip(closed)["payerKeys"])
        assertEquals("job", roundTrip(closed)["kind"])
        assertEquals(setOf("id", "name", "normalizedName", "kind", "currency", "startedAt", "createdAt"), roundTrip(open).keys)
        // اللي اتفضّى بيتمسح صريح وقت الكتابة merge
        assertTrue("endedAt" in IncomeCodecs.incomeSources.omittedFields(open))
        val bad = IncomeCodecs.incomeSources.toStore(open) + ("kind" to "lottery")
        assertTrue("lottery" in assertFailsWith<DocumentError> { IncomeCodecs.incomeSources.decode(bad) }.message!!)
    }

    private fun account(withSources: Boolean) = emptyBackupData().also {
        if (withSources) {
            it.getValue(INCOME_SOURCES_GROUP) += IncomeCodecs.incomeSources.toStore(closed)
            it.getValue(INCOME_SOURCES_GROUP) += IncomeCodecs.incomeSources.toStore(open)
        }
    }

    @Test
    fun `المصادر بتسافر في النسخة الشاملة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(withSources = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-04T00:00:00.000Z")
        val text = file.toJsonText()
        assertTrue("\"$INCOME_SOURCES_GROUP\"" in text)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        assertEquals(2, restore.apply(restore.plan(text).file).added[INCOME_SOURCES_GROUP])
        assertEquals(source.getValue(INCOME_SOURCES_GROUP), target.read().getValue(INCOME_SOURCES_GROUP))
        assertEquals(file.checksum, FullBackup(target).create("2026-10-04T00:00:00.000Z").checksum)
        assertEquals(0, restore.apply(restore.plan(text).file).added[INCOME_SOURCES_GROUP] ?: 0, "نفس النسخة تاني ⇒ مفيش تكرار")
    }

    @Test
    fun `من غير مصادر المجموعة ما بتتكتبش والقيم الغلط بتترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withSources = false))).create("2026-10-04T00:00:00.000Z").toJsonText()
        assertFalse("\"$INCOME_SOURCES_GROUP\"" in text)
        fun broken(change: (MutableMap<String, Any?>) -> Unit) = account(withSources = true).also { data ->
            val rows = data.getValue(INCOME_SOURCES_GROUP)
            rows[0] = LinkedHashMap(rows[0]).apply(change)
        }
        for (b in listOf<(MutableMap<String, Any?>) -> Unit>(
            { it["kind"] = "lottery" },
            { it["startedAt"] = "2026-13-01" },
            { it["expectedDayOfMonth"] = 32L },
            { it["payerKeys"] = listOf(1L) },
            { it.remove("startedAt") },
            { it["expectedMinor"] = -5L },
        )) {
            assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(broken(b))).create("2026-10-04T00:00:00.000Z") }
        }
    }
}
