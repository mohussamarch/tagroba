package app.masroufy.firestore

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * الذاكرة اللي الشاشات بتقرا منها ([LocalMirror] — HANDOVER §7 «السرعة»): من غير فايربيز.
 * - **ما فيش قراية قديمة:** أي كتابة من التطبيق أو تغيير من المستمع (جهاز تاني · التطبيق القديم) بيبان في القراية اللي بعده على طول.
 * - **تنزيل واحد مش N:** كذا قراية قبل ما المستمع يسلّم بيستنوا **نفس** التسليم (ولا واحدة بتروح لفايربيز بنفسها).
 * - نسخة الجهاز بتتقري بس لو الجهاز خلّص تنزيل كامل قبل كده (القاعدة 10 · §54).
 */
class LocalMirrorTest {
    private val g = "transactions"

    private fun synced(vararg docs: Pair<String, Map<String, Any?>>) = LocalMirror(listOf(g, "profile")).apply {
        applyChanges(g, docs.toList())
        markDelivered(g)
        markSynced(g)
    }

    private fun LocalMirror.field(id: String, name: String): Any? = docsOf(g)?.get(id)?.doc?.get(name)

    @Test
    fun writeIsVisibleToTheVeryNextRead() {
        val m = synced("t1" to mapOf("amountMinor" to 100L, "note" to "x"))
        m.applySet(g, "t2", mapOf("amountMinor" to 250))
        assertEquals(250L, m.field("t2", "amountMinor"), "set ⇒ موجود والرقم Long زي المستمع")
        m.applyUpdate(g, "t1", mapOf("amountMinor" to 300L))
        assertEquals(300L, m.field("t1", "amountMinor"))
        assertEquals("x", m.field("t1", "note"), "update ⇒ الحقول التانية زي ما هي")
        m.applyUpdate(g, "missing", mapOf("amountMinor" to 1L))
        assertNull(m.docsOf(g)!!["missing"], "update على مستند مش موجود ⇒ ولا حاجة (زي فايربيز)")
        m.applyDelete(g, listOf("t2"))
        assertNull(m.docsOf(g)!!["t2"])
    }

    @Test
    fun replaceDropsOldFields() {
        val m = LocalMirror(listOf("profile")).apply {
            applyChanges("profile", listOf("main" to mapOf("displayName" to "قديم", "payday" to 25L)))
            markDelivered("profile")
            markSynced("profile")
        }
        m.applyReplace("profile", "main", mapOf("displayName" to "جديد"))
        val doc = m.docsOf("profile")!!.getValue("main").doc
        assertEquals("جديد", doc["displayName"])
        assertFalse("payday" in doc, "set من غير merge ⇒ المستند كله اتبدّل")
    }

    @Test
    fun listenerChangeFromAnotherDeviceIsVisibleAndReDecoded() {
        val m = synced("t1" to mapOf("amountMinor" to 100L))
        var decodes = 0
        val decode: (Map<String, Any?>) -> Long = { decodes++; it["amountMinor"] as Long }
        assertEquals(100L, m.docsOf(g)!!.getValue("t1").entity(decode))
        assertEquals(100L, m.docsOf(g)!!.getValue("t1").entity(decode))
        assertEquals(1, decodes, "نفس النسخة من المستند ⇒ تحويل مرة واحدة")
        m.applyChanges(g, listOf("t1" to mapOf("amountMinor" to 700L), "t9" to mapOf("amountMinor" to 5L)))
        assertEquals(700L, m.docsOf(g)!!.getValue("t1").entity(decode), "التعديل من برا بيبان — مش النسخة المتحولة القديمة")
        assertEquals(2, decodes)
        assertNotNull(m.docsOf(g)!!["t9"])
        m.applyChanges(g, listOf("t1" to null))
        assertNull(m.docsOf(g)!!["t1"], "اتمسح من جهاز تاني ⇒ اختفى")
    }

    @Test
    fun concurrentReadsShareOneListenerDelivery() = runBlocking {
        val m = LocalMirror(listOf(g))
        val readers = (1..20).map { async { m.awaitDocs(g, 5_000) } }
        yield()
        delay(50)
        assertTrue(readers.none { it.isCompleted }, "قبل ما المستمع يسلّم ⇒ كله مستني (ولا واحدة راحت لفايربيز)")
        launch {
            m.applyChanges(g, (1..4000).map { "t$it" to mapOf<String, Any?>("amountMinor" to it.toLong()) })
            m.markDelivered(g)
            m.markSynced(g)
        }
        val results = readers.awaitAll()
        assertTrue(results.all { it != null }, "ولا قراية رجعت فاضية ⇒ ولا واحدة هتروح لفايربيز بنفسها")
        val first = results.first()!!
        assertEquals(4000, first.size)
        results.forEach { assertSame(first, it, "كلهم نفس التسليم — تنزيل واحد مش 20") }
    }

    @Test
    fun deviceCopyIsReadOnlyAfterACompleteSyncBefore() = runBlocking {
        val m = LocalMirror(listOf(g))
        m.applyChanges(g, listOf("t1" to mapOf("amountMinor" to 1L)))
        m.markDelivered(g) // نسخة الجهاز وصلت، السيرفر لسه
        assertNull(m.docsOf(g), "جهاز جديد ⇒ نسخة الجهاز ممكن تبقى ناقصة ⇒ ما تتقريش (القاعدة 10)")
        assertNull(m.awaitDocs(g, 30), "وبعد المهلة ⇒ null (القراية بتروح لفايربيز زي الأول)")
        m.deviceCopyComplete = true
        assertEquals(1, m.docsOf(g)?.size, "الجهاز خلّص تنزيل كامل قبل كده ⇒ نسخة الجهاز بتتقري لحد ما السيرفر يرد")
    }

    @Test
    fun failedListenerFallsBackWithoutWaiting() = runBlocking {
        val m = synced("t1" to mapOf("amountMinor" to 1L))
        m.markStale(g)
        val started = System.nanoTime()
        assertNull(m.awaitDocs(g, 10_000))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000, "مستمع وقع ⇒ ما نستناش")
        assertNull(m.awaitDocs("notMirrored", 10_000), "مجموعة مالهاش مستمع ⇒ فايربيز على طول")
    }

    @Test
    fun clearForgetsDataAndTrust() {
        val m = synced("t1" to mapOf("amountMinor" to 1L)).apply { deviceCopyComplete = true }
        m.clear()
        assertNull(m.docsOf(g))
        assertFalse(m.deviceCopyComplete)
        assertEquals(0, m.documentCount(), "الخروج ⇒ بيانات الحساب ما تفضلش في الذاكرة")
    }
}
