package app.masroufy.memory

import app.masroufy.core.GoalContribution
import app.masroufy.core.Id
import app.masroufy.core.SavingsGoal
import app.masroufy.port.GoalContributionRepository
import app.masroufy.port.SavingsGoalRepository

/** خطط الادخار وإيداعاتها في الذاكرة — للاختبار (CLAUDE.md #6). */
class MemorySavingsGoalRepository(seed: List<SavingsGoal> = emptyList()) : SavingsGoalRepository {
    private val items = LinkedHashMap<Id, SavingsGoal>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<SavingsGoal> = items.values.toList()

    override suspend fun save(goal: SavingsGoal) {
        items[goal.id] = goal
    }
}

class MemoryGoalContributionRepository(seed: List<GoalContribution> = emptyList()) : GoalContributionRepository {
    private val items = LinkedHashMap<Id, GoalContribution>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<GoalContribution> = items.values.toList()

    override suspend fun save(contribution: GoalContribution) {
        items[contribution.id] = contribution
    }

    override suspend fun remove(id: Id) {
        items.remove(id)
    }
}
