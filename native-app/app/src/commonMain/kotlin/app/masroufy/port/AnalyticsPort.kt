package app.masroufy.port

import app.masroufy.core.UsageEvent
import app.masroufy.core.UsageProperty
import app.masroufy.core.UsageValue

/**
 * بيانات الاستخدام (Google Analytics for Firebase على أندرويد بس — `:androidApp` · OVERRIDES §114 · ARCHITECTURE «بيانات الاستخدام»).
 * بياخد أنواع `core` بس ([UsageEvent] · [UsageValue]) — **مفيش دالة بتاخد نص**، فمفيش طريق لمبلغ أو اسم أو نص رسالة.
 * الاختبارات والآيفون ونسخ المحاكي: مفيش تنفيذ (`Usage.port` = null ⇒ ولا حاجة) — زي `CrashReportsPort`.
 */
interface AnalyticsPort {
    fun log(event: UsageEvent)

    fun set(property: UsageProperty, value: UsageValue)
}

/**
 * OVERRIDES §121-17: مفتاح «ابعت بيانات الاستخدام» **لوحده** (منفصل عن مفتاح تقارير الأعطال — `CrashReportsPort`). على مستوى الجهاز.
 * [available] = false ⇒ النسخة دي مالهاش بيانات استخدام (المحاكي · الاختبارات · من غير فايربيز). الافتراضي زي تقارير الأعطال (مفتوح).
 */
interface UsageSwitchPort {
    val available: Boolean

    fun isOn(): Boolean

    fun setOn(on: Boolean)
}
