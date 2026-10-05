package app.masroufy.port

import app.masroufy.core.TransferParty

/**
 * قرارات «زون التحويلات» (OVERRIDES §60) — مستند لكل طرف اتقرر فيه، والمعرّف مفتاح الطرف (الاسم المطبّع + آخر 4).
 * `listAll` مسموح: العدد صغير بطبيعته (الأطراف اللي اتسأل عنها صاحب الحساب، مش العمليات).
 */
interface TransferPartyRepository {
    suspend fun listAll(): List<TransferParty>

    suspend fun save(party: TransferParty)

    suspend fun remove(key: String)
}
