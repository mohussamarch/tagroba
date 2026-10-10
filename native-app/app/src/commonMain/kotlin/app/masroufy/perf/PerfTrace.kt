package app.masroufy.perf

import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * قياس السرعة (تشخيص بس — HANDOVER §7 «السرعة»): **مقفول افتراضيًا** ([sink] = null ⇒ ولا سطر ولا حساب وقت).
 * `:androidApp` بيفتحه في نسخة المحاكي بس (سطور `MPERF` في logcat) — نسخة المالك ما بتكتبش حاجة.
 * - [span] حوالين تحميل شاشة أو حالة استخدام ⇒ `span <الاسم> ms=… th=<الخيط>`.
 * - المستودعات بتكتب كل قراية ([log]): من الذاكرة ولا من فايربيز، وكام مستند وكام بايت تقريبًا.
 */
object PerfTrace {
    @Volatile var sink: ((String) -> Unit)? = null

    /** اسم الخيط الحالي (أندرويد: `Thread.currentThread().name`) — عشان نعرف الشغل التقيل على الخيط الرئيسي ولا لأ. */
    @Volatile var thread: () -> String = { "" }

    val enabled: Boolean get() = sink != null

    fun log(line: String) {
        sink?.invoke(line)
    }

    fun mark(): TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

    inline fun <T> span(name: String, block: () -> T): T {
        val out = sink ?: return block()
        val start = TimeSource.Monotonic.markNow()
        val th = thread()
        try {
            return block()
        } finally {
            out("span $name ms=${start.elapsedNow().inWholeMilliseconds} th=$th")
        }
    }
}
