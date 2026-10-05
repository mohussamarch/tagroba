package app.masroufy.firestore

import app.masroufy.core.GoalContribution
import app.masroufy.core.Id
import app.masroufy.core.SavingsGoal
import app.masroufy.data.SavingsGoalCodecs
import app.masroufy.port.GoalContributionRepository
import app.masroufy.port.SavingsGoalRepository

/**
 * خطط الادخار وإيداعاتها (المساعد المالي §68) — على مستوى الحساب (`users/{uid}`) جنب الأشخاص والمناسبات.
 * القاعدة العامة `users/{uid}/{document=**}` بتغطيهم ⇒ مفيش تغيير في قواعد فايربيز.
 */
class FirestoreSavingsGoalRepository(private val space: FirestoreSpace) : SavingsGoalRepository {
    private val codec = SavingsGoalCodecs.savingsGoals

    override suspend fun listAll(): List<SavingsGoal> = space.select(codec)

    override suspend fun save(goal: SavingsGoal) = space.saveAll(codec, listOf(goal))
}

class FirestoreGoalContributionRepository(private val space: FirestoreSpace) : GoalContributionRepository {
    private val codec = SavingsGoalCodecs.goalContributions

    override suspend fun listAll(): List<GoalContribution> = space.select(codec)

    override suspend fun save(contribution: GoalContribution) = space.saveAll(codec, listOf(contribution))

    override suspend fun remove(id: Id) = space.deleteAll(codec.group, listOf(id))
}
