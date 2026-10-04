package app.masroufy.port

import app.masroufy.core.IncomeSource

/**
 * مصادر الدخل (OVERRIDES §48 · §64) — مجموعة جديدة في كوتلن بس. `listAll` مسموح: العدد صغير بطبيعته (شغلاتك وعملائك).
 * **مفيش مسح** عن قصد: المصدر اللي خلص بيتقفل بتاريخ عشان المقارنة مع الشهور القديمة تفضل صح.
 */
interface IncomeSourceRepository {
    suspend fun listAll(): List<IncomeSource>

    suspend fun saveMany(sources: List<IncomeSource>)
}
