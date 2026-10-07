package app.masroufy.port

import app.masroufy.core.LocalMoment
import app.masroufy.core.SystemNotice

/**
 * تسليم الإشعار لنظام الجوال (OVERRIDES §61 · §72). بياخد [SystemNotice] بس — النص العام اللي **ما بيتبنيش غير من نوع التنبيه**
 * (مُنشئه `internal` في `core`)، فمستحيل مبلغ أو اسم محل يوصل لشريط الإشعارات أو شاشة القفل من هنا.
 * أندرويد: `AndroidDeviceNotifier` (`:device`). الآيفون: **لسه ما اتعملش** (ARCHITECTURE §31.29). الاختبار: `MemoryDeviceNotifier`.
 */
enum class NoticeOutcome {
    /** ظهر دلوقتي. */
    SHOWN,

    /** هيظهر في الوقت اللي المحرك اختاره. */
    SCHEDULED,

    /** المستخدم ما سمحش بالإشعارات (أندرويد 13+ أو قفلها من الإعدادات) ⇒ ولا حاجة اتعملت، من غير خطأ. */
    BLOCKED,

    /** الجهاز ده مالوش تنفيذ. */
    UNAVAILABLE,
}

interface DeviceNotifier {
    val available: Boolean

    /** الإشعارات مسموحة دلوقتي؟ (التحقق بس — عمره ما بيفتح نافذة الإذن؛ دي شغل الشاشة.) */
    suspend fun permitted(): Boolean

    /**
     * [tag] = معرّف ثابت للإشعار (نفس الـtag بيحل محل القديم). [at] = الساعة المحلية اللي المحرك اختارها؛ null أو فاتت ⇒ دلوقتي.
     */
    suspend fun post(tag: String, notice: SystemNotice, at: LocalMoment?): NoticeOutcome
}
