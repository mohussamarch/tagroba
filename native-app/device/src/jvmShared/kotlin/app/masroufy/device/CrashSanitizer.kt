package app.masroufy.device

import app.masroufy.core.ProblemCode
import app.masroufy.core.ProblemLevel
import java.util.Collections
import java.util.IdentityHashMap

/**
 * الخطأ **من غير رسالته** قبل ما يروح لتقارير الأعطال (OVERRIDES §90 · §80): رسالة الخطأ ممكن يبقى فيها نص رسالة بنك أو مبلغ أو اسم.
 * الناتج [SanitizedCrash]: رسالته = **اسم نوع الخطأ الأصلي بس** (+ كود المشكلة من القايمة الثابتة لو فيه)، ومكانه (stack trace) زي ما هو،
 * والسبب (cause) بنفس الطريقة لحد [MAX_DEPTH] — ومن غير الأخطاء المكتومة (suppressed — ممكن فيها رسايل).
 * ⚠️ مشترك بين أندرويد والكمبيوتر (`src/jvmShared`) عشان يتختبر على الكمبيوتر بنفس الكود.
 */
object CrashSanitizer {
    const val MAX_DEPTH = 8

    fun sanitize(error: Throwable, code: String? = null): SanitizedCrash = build(error, code, Collections.newSetFromMap(IdentityHashMap()), 0)

    private fun build(error: Throwable, code: String?, seen: MutableSet<Throwable>, depth: Int): SanitizedCrash {
        seen += error
        val next = error.cause?.takeIf { depth + 1 < MAX_DEPTH && it !in seen }
        val cause = next?.let { build(it, null, seen, depth + 1) }
        val type = (error as? SanitizedCrash)?.message ?: error.javaClass.name
        val label = if (code != null && SAFE_CODE.matches(code)) "[$code] $type" else type
        return SanitizedCrash(label, cause).also { it.stackTrace = error.stackTrace }
    }

    private val SAFE_CODE = Regex("[A-Z_]{1,40}")
}

/** خطأ متنضف: الرسالة اسم النوع بس، والمكان من الأصلي. */
class SanitizedCrash(typeName: String, cause: SanitizedCrash?) : RuntimeException(typeName, cause)

/**
 * إيه اللي «سجل المشاكل» يبعته لتقارير الأعطال (غير الوقعات نفسها — معالج Crashlytics بيبعتها): ERROR (بخطأ أو من غيره) و WARN ⇒ تقرير؛
 * INFO ⇒ على الجوال بس. الوقعتين (CRASH_MAIN · CRASH_BACKGROUND) مستثنيين (من غير تكرار).
 */
object CrashReportPolicy {
    fun shouldSend(code: ProblemCode): Boolean =
        code != ProblemCode.CRASH_MAIN && code != ProblemCode.CRASH_BACKGROUND && code.level != ProblemLevel.INFO

    /** الخطأ الحقيقي (متنضف) لو فيه، وإلا خطأ صناعي رسالته **اسم الكود بس** — ولا بيانات. */
    fun toSend(code: ProblemCode, error: Throwable?): SanitizedCrash =
        CrashSanitizer.sanitize(error ?: ProblemCodeReport(code.name), code.name)
}

/** خطأ صناعي لمشكلة من غير خطأ حقيقي: الرسالة اسم الكود من القايمة الثابتة بس. */
class ProblemCodeReport(codeName: String) : RuntimeException(codeName)
