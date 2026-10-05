package app.masroufy.port

import app.masroufy.core.Id
import app.masroufy.core.InheritanceScenario

/**
 * حسابات الورث المحفوظة (رد المالك §69.3) — **على مستوى الحساب** (تبان على كل الأجهزة). `listAll` مسموح: العدد صغير بطبيعته.
 * **المسح مسموح** (رد المالك): سيناريو عمله المستخدم بإيده، مش بيانات فلوس.
 */
interface InheritanceScenarioRepository {
    suspend fun listAll(): List<InheritanceScenario>

    suspend fun save(scenario: InheritanceScenario)

    suspend fun remove(id: Id)
}
