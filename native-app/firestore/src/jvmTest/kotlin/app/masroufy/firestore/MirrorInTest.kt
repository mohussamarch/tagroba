package app.masroufy.firestore

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * r041c — `findIn` من الذاكرة بقى لفة واحدة ([mirrorIn]) بدل لفة وفرز لكل 30 قيمة. لازم يطلع **نفس** النتيجة ونفس الترتيب اللي كانت
 * الدفعات بتطلعه (كل دفعة مترتبة بالمعرّف لوحدها — `MirrorParityTest` على الجهاز بيقارنها بفايربيز). بيانات مخترعة.
 */
class MirrorInTest {
    private fun docs(n: Int): Map<String, LocalMirror.Entry> = (1..n).associate { i ->
        val id = "t" + (n - i).toString().padStart(4, '0')
        id to LocalMirror.Entry(mapOf("accountIdentity" to "acc${i % 7}", "id" to id, "amountMinor" to i.toLong()))
    }

    private fun chunked(all: Map<String, LocalMirror.Entry>, field: String, values: List<String>) =
        values.chunked(IN_LIMIT).flatMap { DocQuery(listOf(Cond.In(field, it))).runOn(all) }

    @Test fun sameResultAndOrderAsTheThirtyValueChunks() {
        val all = docs(400)
        val ids = all.keys.shuffled(kotlin.random.Random(7)).take(130) + listOf("missing", "t0003", "t0003")
        assertEquals(chunked(all, "id", ids).map { it.doc["id"] }, mirrorIn(all, "id", ids).map { it.doc["id"] })
        val accounts = listOf("acc3", "acc1", "nope")
        assertEquals(chunked(all, "accountIdentity", accounts).map { it.doc["id"] }, mirrorIn(all, "accountIdentity", accounts).map { it.doc["id"] })
    }

    @Test fun noValuesNoRows() {
        assertEquals(emptyList(), mirrorIn(docs(5), "id", emptyList()))
    }
}
