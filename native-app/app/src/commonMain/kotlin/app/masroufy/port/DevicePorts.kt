package app.masroufy.port

import app.masroufy.core.SharedMerchantEntry

/** قاعدة التجار المشتركة (OVERRIDES §25) وقفل الجهاز (§21) — نقل `SharedMerchantCatalogPort.ts` و`DeviceLockPort.ts`. */

/** التأكيد مش من هنا — من لوحة فايربيز بس. */
interface SharedMerchantCatalogPort {
    /** المستندات اللي اتعدلت **بعد** الوقت ده (null = كله). */
    suspend fun listChangedSince(sinceIso: String?): List<SharedMerchantEntry>

    /** كل المؤكدين — مراجعة يومية، لأن التأكيد من اللوحة ممكن ما يغيّرش `updatedAt`. */
    suspend fun listConfirmed(): List<SharedMerchantEntry>

    suspend fun get(normalizedName: String): SharedMerchantEntry?

    /** بيكتب مستند مش مؤكد — القواعد بترفض أي `confirmed = true` أو تعديل على مؤكد. */
    suspend fun save(entry: SharedMerchantEntry)
}

/** التطبيق ما بيشوفش البصمة ولا الرمز: النظام بيسأل ويرجّع نتيجة بس. */
enum class LockResult(val wire: String) { OK("ok"), CANCELLED("cancelled"), FAILED("failed"), UNAVAILABLE("unavailable") }

/** `code`: سبب عدم الإتاحة بكود ثابت (NONE_ENROLLED = الجوال مالوش قفل شاشة ولا بصمة). */
data class DeviceLockAvailability(val available: Boolean, val code: String)

interface DeviceLockPort {
    /** المنصة نفسها بتدعم القفل. */
    val supported: Boolean

    suspend fun availability(): DeviceLockAvailability

    suspend fun authenticate(title: String, subtitle: String? = null): LockResult
}

/** «القفل متشغّل» — على الجهاز نفسه، مش بيانات حساب ومش بيتزامن. */
interface AppLockSettingsPort {
    fun read(): Boolean

    fun write(enabled: Boolean)
}
