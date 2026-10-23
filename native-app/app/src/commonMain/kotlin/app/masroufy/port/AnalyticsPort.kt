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
