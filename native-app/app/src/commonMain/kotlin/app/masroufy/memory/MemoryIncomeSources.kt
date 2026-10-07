package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.port.IncomeSourceRepository

/** مصادر الدخل في الذاكرة — للاختبار (CLAUDE.md #6). قفل القديم وفتح الجديد بيتكتبوا في وحدة عمل واحدة ⇒ `Snapshotable`. */
class MemoryIncomeSourceRepository(seed: List<IncomeSource> = emptyList()) : IncomeSourceRepository, Snapshotable {
    private var items = LinkedHashMap<Id, IncomeSource>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<IncomeSource> = items.values.toList()

    override suspend fun saveMany(sources: List<IncomeSource>) {
        for (s in sources) items[s.id] = s
    }

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, IncomeSource>)
    }
}
