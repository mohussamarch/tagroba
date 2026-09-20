package app.masroufy.port

import app.masroufy.core.Id

/**
 * وحدة العمل الذرية — spec/03: «تغيير العملية وتخصيص الشخص وتسويته وتحديث حالة الاستيراد
 * عملية ذرية واحدة». spec/06: «انقطاع وسط حفظ دفعة ⇒ صفر أو الدفعة كاملة، مش نصها».
 */
interface UnitOfWork {
    suspend fun <T> run(work: suspend () -> T): T
}

/** بيولّد معرّفات. بيتمرر كاعتماد عشان الاختبار يبقى نتيجته واحدة كل مرة. */
interface IdGenerator {
    fun next(prefix: String): Id
}

/** ساعة بتتمرر كاعتماد — مفيش وقت بيتقرا من الجهاز جوه المنطق. */
interface Clock {
    fun nowIso(): String
}
