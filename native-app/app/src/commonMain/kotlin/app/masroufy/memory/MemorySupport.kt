package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.UnitOfWork

/** أدوات مساعدة للاختبار — نقل `memorySupport.ts`. */

/**
 * مستودع يقدر يرجّع حالته لِما كانت عليه — هو اللي بيخلي `MemoryUnitOfWork`
 * ذرّية فعلًا مش اسمًا (spec/06: «انقطاع وسط حفظ دفعة ⇒ صفر أو كامل الدفعة»).
 * الكيانات في كوتلن ثابتة (data class)، فنسخة الخريطة نفسها كافية.
 */
interface Snapshotable {
    fun snapshot(): Any

    fun restore(state: Any)
}

/** وحدة عمل ذرّية فعليًا: لقطة من كل مستودع قبل البدء، واستعادة كاملة عند أي فشل. */
class MemoryUnitOfWork(private val stores: List<Snapshotable>) : UnitOfWork {
    override suspend fun <T> run(work: suspend () -> T): T {
        val snapshots = stores.map { it.snapshot() }
        try {
            return work()
        } catch (error: Throwable) {
            // الاستعادة **كلها** مهمة: مستودع واحد ما اترجعش = نص دفعة
            stores.forEachIndexed { i, store -> store.restore(snapshots[i]) }
            throw error
        }
    }
}

/** وحدة عمل من غير تراجع — للاختبارات اللي مش محتاجة محاكاة الفشل. */
class PassthroughUnitOfWork : UnitOfWork {
    override suspend fun <T> run(work: suspend () -> T): T = work()
}

class SequentialIdGenerator : IdGenerator {
    private val counters = mutableMapOf<String, Int>()

    override fun next(prefix: String): Id {
        val n = (counters[prefix] ?: 0) + 1
        counters[prefix] = n
        return "$prefix-${n.toString().padStart(6, '0')}"
    }
}

class FixedClock(private val iso: String) : Clock {
    override fun nowIso(): String = iso
}
