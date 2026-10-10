package app.masroufy.firestore

import app.masroufy.data.Doc
import app.masroufy.perf.PerfTrace
import kotlin.time.TimeSource

/**
 * عدّاد القراية (تشخيص بس — [PerfTrace] مقفول ⇒ ولا حاجة). كل قراية سطر:
 * `read src=mirror|fs group=… docs=… bytes=… ms=… th=…` — `fs` = استعلام لفايربيز (سيرفر لو فيه نت)، `fromCache` لو رجع من نسخة الجهاز.
 * البايتات تقريبية بنفس حساب حجم مستند فايربيز (أسماء الحقول + القيم + 32 للمستند) — للمقارنة بين قبل وبعد، مش للفاتورة.
 */
internal object ReadMeter {
    inline fun <T> mirror(group: String, scanned: Int, block: () -> List<T>): List<T> {
        if (!PerfTrace.enabled) return block()
        val start = TimeSource.Monotonic.markNow()
        val out = block()
        PerfTrace.log("read src=mirror group=$group scanned=$scanned docs=${out.size} ms=${start.elapsedNow().inWholeMilliseconds} th=${PerfTrace.thread()}")
        return out
    }

    fun server(what: String, docs: List<Doc?>, fromCache: Boolean, start: TimeSource.Monotonic.ValueTimeMark) {
        if (!PerfTrace.enabled) return
        val bytes = docs.sumOf { (it?.let(::approxBytes) ?: 0) + 32 }
        PerfTrace.log("read src=fs what=$what docs=${docs.size} bytes=$bytes cache=$fromCache ms=${start.elapsedNow().inWholeMilliseconds} th=${PerfTrace.thread()}")
    }

    fun approxBytes(value: Any?): Int = when (value) {
        null -> 1
        is String -> value.encodeToByteArray().size + 1
        is Boolean -> 1
        is Number -> 8
        is List<*> -> value.sumOf { approxBytes(it) }
        is Map<*, *> -> value.entries.sumOf { (k, v) -> (k as? String)?.let { it.encodeToByteArray().size + 1 }.orZero() + approxBytes(v) }
        else -> 8
    }

    private fun Int?.orZero() = this ?: 0
}
