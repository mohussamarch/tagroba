package app.masroufy.firestore

import app.masroufy.core.IncomeSource
import app.masroufy.data.IncomeCodecs
import app.masroufy.port.IncomeSourceRepository

/**
 * مصادر الدخل (OVERRIDES §48 · §64) — مجموعة كوتلن بس، والقواعد بتسمح بيها من غير نشر (`IncomeCodecs`).
 * الكتابة merge بتمسح الحقل الاختياري اللي اتفضّى (زي يوم المرتب اللي اتشال) — نفس `saveAll` لباقي المجموعات.
 */
class FirestoreIncomeSourceRepository(private val space: FirestoreSpace) : IncomeSourceRepository {
    private val codec = IncomeCodecs.incomeSources

    override suspend fun listAll(): List<IncomeSource> = space.select(codec)

    override suspend fun saveMany(sources: List<IncomeSource>) = space.saveAll(codec, sources)
}
