package app.masroufy.data

import app.masroufy.core.Asset
import app.masroufy.core.Currency
import app.masroufy.core.ZAKAT_BACKUP_GROUPS
import app.masroufy.core.ZakatCollectability
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatPayment
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.ZakatShareHolding
import app.masroufy.core.ZakatSubject
import app.masroufy.core.ZakatYear
import app.masroufy.core.ZakatYearLine
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** الزكاة (§62) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class ZakatBackupTest {
    private val fact = ZakatFact("a-1", ZakatSubject.ASSET, ZakatPurpose.SAVING, null, null, 21, null, "2026-02-18T00:00:00.000Z")
    private val open = ZakatYear("2026-02-18", "2025-03-01", "2026-02-18", Currency.SAR, "2025-03-02T00:00:00.000Z")
    private val closed = open.copy(closedAt = "2026-02-18T10:00:00.000Z", nisabMinor = 208_250, lines = listOf(ZakatYearLine(ZakatLineKind.CASH, 4_000_000, 100_000)))
    private val cashPayment = ZakatPayment("zp-1", "2026-02-18", null, 100_000, listOf(ZakatLineKind.CASH), "2026-02-19", "2026-02-19T00:00:00.000Z")

    private fun <T> roundTrip(codec: DocCodec<T>, value: T): Doc {
        val d = codec.toStore(value)
        assertEquals(value, codec.decode(d), codec.group)
        return d
    }

    @Test
    fun `المستندات بتتكتب وتتقرا والفاضي ما بيتكتبش`() {
        val d = roundTrip(ZakatCodecs.zakatFacts, fact)
        assertEquals(setOf("id", "subject", "assetId", "purpose", "karat", "updatedAt"), d.keys)
        val receivable = ZakatFact("o-1", ZakatSubject.OBLIGATION, collectability = ZakatCollectability.DOUBTFUL, updatedAt = "x")
        assertEquals("o-1", roundTrip(ZakatCodecs.zakatFacts, receivable)["obligationId"])
        roundTrip(ZakatCodecs.zakatFacts, fact.copy(subjectId = "a-2", purpose = null, karat = null, holding = ZakatShareHolding.TRADING))
        assertEquals(setOf("id", "hawlStart", "dueAt", "currency", "confirmedAt"), roundTrip(ZakatCodecs.zakatYears, open).keys)
        assertEquals(listOf(mapOf("kind" to "cash", "zakatableMinor" to 4_000_000L, "dueMinor" to 100_000L)), roundTrip(ZakatCodecs.zakatYears, closed)["lines"])
        assertFalse("transactionId" in roundTrip(ZakatCodecs.zakatPayments, cashPayment), "الدفع الكاش من غير عملية")
        assertEquals(listOf("cash"), roundTrip(ZakatCodecs.zakatPayments, cashPayment.copy(transactionId = "t-1"))["lines"])
        // قيمة مش معروفة ⇒ خطأ واضح باسم الحقل
        val bad = ZakatCodecs.zakatPayments.toStore(cashPayment) + ("lines" to listOf("cars"))
        assertTrue("cars" in assertFailsWith<DocumentError> { ZakatCodecs.zakatPayments.decode(bad) }.message!!)
    }

    private fun account(withZakat: Boolean) = emptyBackupData().also {
        it.getValue("assets") += AssetProjectCodecs.assets.toStore(Asset("a-1", "سبيكة وهمية", "silver", "جرام", Currency.SAR, false))
        if (withZakat) {
            it.getValue("zakatFacts") += ZakatCodecs.zakatFacts.toStore(fact)
            it.getValue("zakatYears") += ZakatCodecs.zakatYears.toStore(closed)
            it.getValue("zakatPayments") += ZakatCodecs.zakatPayments.toStore(cashPayment)
        }
    }

    @Test
    fun `الزكاة بتسافر في النسخة الشاملة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(withZakat = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-02-20T00:00:00.000Z")
        val text = file.toJsonText()
        for (g in ZAKAT_BACKUP_GROUPS) assertTrue("\"$g\"" in text, g)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val outcome = restore.apply(restore.plan(text).file)
        assertEquals(listOf(1, 1, 1), ZAKAT_BACKUP_GROUPS.map { outcome.added[it] })
        for (g in ZAKAT_BACKUP_GROUPS) assertEquals(source.getValue(g), target.read().getValue(g), g)
        assertEquals(file.checksum, FullBackup(target).create("2026-02-20T00:00:00.000Z").checksum)
    }

    @Test
    fun `من غير زكاة المجموعات ما بتتكتبش والعلاقة المكسورة بتترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withZakat = false))).create("2026-02-20T00:00:00.000Z").toJsonText()
        for (g in ZAKAT_BACKUP_GROUPS) assertFalse("\"$g\"" in text, g)
        val orphan = account(withZakat = true).also { it.getValue("zakatYears").clear() }
        val e = assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(orphan)).create("2026-02-20T00:00:00.000Z") }
        assertTrue("zakatPayments" in (e.message ?: ""), e.message)
        val wrongPurpose = account(withZakat = true).also { it.getValue("zakatFacts")[0] = it.getValue("zakatFacts")[0] + ("purpose" to "gift") }
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(wrongPurpose)).create("2026-02-20T00:00:00.000Z") }
    }

    @Test
    fun `واقعة الشركة السعودية بتتكتب وتسافر وسطر الدين اللي اتحصّل بيتقرا`() = runBlocking<Unit> {
        val share = ZakatFact("a-1", ZakatSubject.ASSET, holding = ZakatShareHolding.LONG_TERM, updatedAt = "x", saudiCompany = false)
        assertEquals(false, roundTrip(ZakatCodecs.zakatFacts, share)["saudiCompany"])
        assertFalse("saudiCompany" in roundTrip(ZakatCodecs.zakatFacts, fact), "الفاضي ما بيتكتبش")
        val collected = closed.copy(lines = closed.lines + ZakatYearLine(ZakatLineKind.COLLECTED_RECEIVABLES, 200_000, 5_000))
        assertEquals("collected_receivables", (roundTrip(ZakatCodecs.zakatYears, collected)["lines"] as List<*>).map { (it as Map<*, *>)["kind"] }.last())
        val source = account(withZakat = true).also {
            it.getValue("zakatFacts")[0] = ZakatCodecs.zakatFacts.toStore(share)
            it.getValue("zakatYears")[0] = ZakatCodecs.zakatYears.toStore(collected)
        }
        val text = FullBackup(MemoryFullBackup(source)).create("2026-02-20T00:00:00.000Z").toJsonText()
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        restore.apply(restore.plan(text).file)
        assertEquals(share, ZakatCodecs.zakatFacts.decode(target.read().getValue("zakatFacts").single()))
        assertEquals(collected, ZakatCodecs.zakatYears.decode(target.read().getValue("zakatYears").single()))
        val notBool = account(withZakat = true).also { it.getValue("zakatFacts")[0] = it.getValue("zakatFacts")[0] + ("saudiCompany" to "yes") }
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(notBool)).create("2026-02-20T00:00:00.000Z") }
    }
}
