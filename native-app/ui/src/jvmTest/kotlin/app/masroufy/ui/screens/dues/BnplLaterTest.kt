package app.masroufy.ui.screens.dues

import app.masroufy.ui.theme.PrefStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-polish: «تخطّي» / «بعدين» على لوحة تابي وتمارا بيتفتكروا بعد قفل البرنامج، لكل بلد لوحدها (بيانات مخترعة). */
class BnplLaterTest {
    private class MemoryStore(val data: MutableMap<String, String> = mutableMapOf()) : PrefStore {
        override fun read(key: String): String? = data[key]
        override fun write(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
    }

    @AfterTest
    fun reset() = BnplLater.attach(null)

    @Test
    fun choiceSurvivesRestartPerSpace() {
        val store = MemoryStore()
        BnplLater.attach(store)
        BnplLater.add("space-a", "txn-1")
        BnplLater.add("space-a", "txn-1")
        BnplLater.add("space-b", "txn-2")
        // قفل البرنامج وفتحه: الحالة في الذاكرة بتضيع، والتخزين بيفضل
        BnplLater.attach(store)
        assertEquals(listOf("txn-1"), BnplLater.ids("space-a"))
        assertEquals(listOf("txn-2"), BnplLater.ids("space-b"))
        assertTrue(BnplLater.ids("space-c").isEmpty())
    }

    @Test
    fun removeIsPersistedToo() {
        val store = MemoryStore()
        BnplLater.attach(store)
        BnplLater.add("space-a", "txn-1")
        BnplLater.add("space-a", "txn-2")
        BnplLater.remove("space-a", "txn-1")
        BnplLater.attach(store)
        assertEquals(listOf("txn-2"), BnplLater.ids("space-a"))
        BnplLater.remove("space-a", "txn-2")
        assertTrue(store.data.isEmpty())
    }

    @Test
    fun keepsOnlyTheLatestIdsAndWorksWithoutStore() {
        val store = MemoryStore()
        BnplLater.attach(store)
        repeat(BnplLater.MAX + 5) { BnplLater.add("s", "t$it") }
        BnplLater.attach(store)
        val ids = BnplLater.ids("s")
        assertEquals(BnplLater.MAX, ids.size)
        assertEquals("t5", ids.first())
        BnplLater.attach(null)
        BnplLater.add("s", "x")
        assertEquals(listOf("x"), BnplLater.ids("s"))
    }
}
