package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.InheritanceScenario
import app.masroufy.port.InheritanceScenarioRepository

/** حسابات الورث المحفوظة في الذاكرة — للاختبار (CLAUDE.md #6). */
class MemoryInheritanceScenarioRepository(seed: List<InheritanceScenario> = emptyList()) : InheritanceScenarioRepository {
    private val items = LinkedHashMap<Id, InheritanceScenario>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<InheritanceScenario> = items.values.toList()

    override suspend fun save(scenario: InheritanceScenario) {
        items[scenario.id] = scenario
    }

    override suspend fun remove(id: Id) {
        items.remove(id)
    }
}
