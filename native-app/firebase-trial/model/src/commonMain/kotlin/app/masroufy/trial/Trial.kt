package app.masroufy.trial

import kotlinx.serialization.Serializable

/**
 * اللي بيتقاس في التجربة — نفس الخطوات بالظبط للمكتبتين. الفرق الوحيد في `createReader` (ملف لكل نسخة).
 * المشروع `demo-…` = محاكي بس: فايربيز ما بيقبلش اتصال بمشروع حقيقي بالاسم ده.
 */
const val TRIAL_PROJECT = "demo-masroufy-trial"
const val TRIAL_APP_ID = "1:000000000000:android:0000000000000000"
const val TRIAL_API_KEY = "emulator-only-not-a-real-key"

/** 10.0.2.2 = الكمبيوتر نفسه من جوه محاكي أندرويد. */
const val EMULATOR_HOST = "10.0.2.2"
const val EMULATOR_PORT = 8088
const val TRIAL_COLLECTION = "users/trial-user/transactions"

/** شكل مستند العملية — حقول زي التطبيق الحقيقي، والقيم وهمية (`seed/seed.mjs`). */
@Serializable
data class TxnDoc(
    val id: String = "",
    val occurredAt: String = "",
    val sourceOrder: Long = 0,
    val economicKind: String = "",
    val observedDirection: String = "",
    val amountMinor: Long = 0,
    val currency: String = "SAR",
    val rawMerchantName: String? = null,
    val categoryId: String? = null,
    val walletId: String? = null,
    val reviewState: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

enum class TrialSource { SERVER, CACHE, DEFAULT }

interface TrialReader {
    val name: String

    /** مسح النسخة المحلية — لازم قبل أول قراية عشان القياس الأول يبقى «أول مرة تفتح التطبيق». */
    suspend fun clearCache()

    /** من غير فلتر = كل العمليات (التنزيل الأول). */
    suspend fun read(from: String?, to: String?, source: TrialSource): List<TxnDoc>

    suspend fun setOnline(online: Boolean)
}
