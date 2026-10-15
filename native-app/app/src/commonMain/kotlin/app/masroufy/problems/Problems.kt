package app.masroufy.problems

import app.masroufy.core.ProblemCode
import app.masroufy.core.ProblemDetail
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * نقطة الإبلاغ عن مشكلة من أي طبقة (المزامنة · رسايل البنك · الاستيراد · النسخة — OVERRIDES §90) — زي [app.masroufy.perf.PerfTrace]:
 * **مقفولة افتراضيًا** ([sink] = null ⇒ ولا حاجة). التجميع (`AppContainer`) بيوصّلها بـ`RecordProblem` (السجل على الجوال) وبتقارير الأعطال.
 * - [report] بياخد **كود من القايمة الثابتة** + الخطأ نفسه (نوعه ومكانه بس بيتاخدوا منه — رسالته عمرها ما بتتسجل) + أرقام بمفاتيح ثابتة.
 * - عمرها ما بتوقّع اللي بينادي (أي خطأ جوه التسجيل بيتبلع).
 */
object Problems {
    @Volatile var sink: ((code: ProblemCode, error: Throwable?, details: Map<ProblemDetail, Long>) -> Unit)? = null

    fun report(code: ProblemCode, error: Throwable? = null, details: Map<ProblemDetail, Long> = emptyMap()) {
        // إلغاء (الشاشة اتقفلت · المهلة خلصت) مش مشكلة — `runCatching` بيمسكه هو كمان
        if (error is CancellationException) return
        val out = sink ?: return
        try {
            out(code, error, details)
        } catch (_: Throwable) {
            // التسجيل عمره ما يوقّع البرنامج
        }
    }
}
