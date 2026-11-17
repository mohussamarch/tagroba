package app.masroufy.problems

import app.masroufy.core.UsageEvent
import app.masroufy.core.UsageProperty
import app.masroufy.core.UsageValue
import app.masroufy.port.AnalyticsPort
import kotlin.concurrent.Volatile

/**
 * نقطة بيانات الاستخدام من أي طبقة (زي [Problems]): **مقفولة افتراضيًا** ([port] = null ⇒ ولا حاجة — الاختبارات · الآيفون · المحاكي).
 * التجميع على أندرويد (`MasroufyApplication`) بيوصّلها بـ`FirebaseUsage`. عمرها ما بتوقّع اللي بينادي.
 * الأحداث من `UsageEvents` بس (فاحص السجلات بيتأكد).
 */
object Usage {
    @Volatile var port: AnalyticsPort? = null

    /** OVERRIDES §121-17: مفتاح «بيانات الاستخدام» (منفصل عن الأعطال). مقفول ⇒ ولا حدث ولا خاصية بتتبعت. الافتراضي مفتوح (زي تقارير الأعطال). */
    @Volatile var allowed: () -> Boolean = { true }

    /** فيه تنفيذ والمفتاح مفتوح. */
    val active: Boolean get() = port != null && runCatching { allowed() }.getOrDefault(false)

    fun log(event: UsageEvent) {
        val p = port ?: return
        if (!active) return
        try {
            p.log(event)
        } catch (_: Throwable) {
            // بيانات الاستخدام عمرها ما توقّع البرنامج
        }
    }

    fun set(property: UsageProperty, value: UsageValue) {
        val p = port ?: return
        if (!active) return
        try {
            p.set(property, value)
        } catch (_: Throwable) {
            // نفس اللي فوق
        }
    }
}
