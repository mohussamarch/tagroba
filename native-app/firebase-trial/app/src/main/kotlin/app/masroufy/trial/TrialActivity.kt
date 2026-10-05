package app.masroufy.trial

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * بيشغّل نفس الخطوات ويكتب كل قياس في سجل الجهاز (logcat، الوسم `MASROUFY_TRIAL`) وعلى الشاشة.
 * سطر السجل: `TRIAL|نسخة|خطوة|جولة|مللي ثانية|عدد الصفوف` — السكربت بيجمعهم من السجل.
 */
class TrialActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var out: TextView

    private fun report(flavor: String, step: String, round: Int, ms: Long, rows: Int) {
        val line = "TRIAL|$flavor|$step|$round|$ms|$rows"
        Log.i(TAG, line)
        out.append(line + "\n")
    }

    private suspend fun measure(flavor: String, step: String, round: Int, block: suspend () -> Int) {
        val start = System.nanoTime()
        val rows = block()
        report(flavor, step, round, (System.nanoTime() - start) / 1_000_000, rows)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply {
            textSize = 12f
            setPadding(24, 48, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(out) })
        scope.launch {
            try {
                run()
                Log.i(TAG, "TRIAL|done")
            } catch (e: Throwable) {
                Log.e(TAG, "TRIAL|error|${e::class.simpleName}|${e.message}", e)
                out.append("ERROR ${e.message}\n")
            }
        }
    }

    private suspend fun run() {
        val start = System.nanoTime()
        val reader = createReader(applicationContext)
        val f = reader.name
        report(f, "init", 0, (System.nanoTime() - start) / 1_000_000, 0)
        reader.clearCache()

        // الجولة 1 = أول مرة تفتح التطبيق: مفيش نسخة محلية ولا اتصال مفتوح. الجولات 2-5 = الاتصال مفتوح
        for (round in 1..5) {
            measure(f, "all-server", round) { reader.read(null, null, TrialSource.SERVER).size }
            measure(f, "year-server", round) { reader.read(YEAR_FROM, YEAR_TO, TrialSource.SERVER).size }
            measure(f, "month-server", round) { reader.read(MONTH_FROM, MONTH_TO, TrialSource.SERVER).size }
            // النسخة اللي على الجوال — ده اللي التطبيق الجديد هيقرا منه (KOTLIN_PLAN §2.1)
            measure(f, "year-cache", round) { reader.read(YEAR_FROM, YEAR_TO, TrialSource.CACHE).size }
            measure(f, "month-cache", round) { reader.read(MONTH_FROM, MONTH_TO, TrialSource.CACHE).size }
            reader.setOnline(false)
            measure(f, "month-offline", round) { reader.read(MONTH_FROM, MONTH_TO, TrialSource.DEFAULT).size }
            reader.setOnline(true)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MASROUFY_TRIAL"

        // سنة مالية وشهر مالي بيوم راتب 28 — نفس شكل الشاشة الرئيسية
        const val YEAR_FROM = "2025-09-28"
        const val YEAR_TO = "2026-09-27"
        const val MONTH_FROM = "2026-08-28"
        const val MONTH_TO = "2026-09-27"
    }
}
