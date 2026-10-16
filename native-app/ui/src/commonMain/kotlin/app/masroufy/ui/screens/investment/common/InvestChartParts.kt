package app.masroufy.ui.screens.investment.common

import androidx.compose.ui.graphics.Color
import app.masroufy.core.Halalas

/**
 * تشارت «هيبقى معاك كام» — الأجزاء النقية (رسم بس، مش فلوس): الشبكة بخطوة «حلوة» (1 · 2 · 2.5 · 5 · 10 × 10^ن) بأعداد صحيحة،
 * وتواريخ المحور السفلي، والوصف المقروء. التحويل لنقط الشاشة (Float) هندسة رسم بس — الأرقام نفسها بالهللة.
 */
data class ChartSeries(val key: String, val label: String, val color: Color, val values: List<Halalas>)

/** محطة على خط ([seriesKey]) عند السنة [index] (مثلًا «أول مليون»). */
data class ChartMark(val seriesKey: String, val index: Int, val label: String)

/** علامة المستخدم (من بكرة السنين): خط كهرماني متقطع عند السنة [index]. */
data class ChartPin(val index: Int, val label: String)

/** هدف ادخار على محور الزمن — بيترسم عند [monthsFromStart] ÷ 12 (هندسة بس). */
data class ChartFlag(val monthsFromStart: Int /* drawn at monthsFromStart / 12 on the year axis — geometry only */, val label: String)

/** حدود المحور الرأسي: من [lo] لـ[hi] بخطوة [step] (بالهللة). */
data class ChartScale(val lo: Halalas, val hi: Halalas, val step: Halalas) {
    /** قيم الخطوط الأفقية من تحت لفوق. */
    val gridValues: List<Halalas> get() = generateSequence(lo) { it + step }.takeWhile { it <= hi }.toList()
}

/** أصغر خطوة «حلوة» ≥ [raw]: 1 · 2 · 2.5 · 5 · 10 × 10^ن. */
fun niceStep(raw: Long): Long {
    if (raw <= 1L) return 1L
    var p = 1L
    while (p <= raw / 10) p *= 10
    for (k in longArrayOf(10, 20, 25, 50, 100)) {
        val s = p * k / 10
        if (s >= raw) return s
    }
    return p * 10
}

/** المحور من كل القيم (الصفر دايمًا جوّه) — حوالي 4 خطوط. */
fun chartScale(series: List<ChartSeries>): ChartScale {
    val all = series.flatMap { it.values }
    var lo = minOf(0L, all.minOrNull() ?: 0L)
    var hi = maxOf(1L, all.maxOrNull() ?: 1L)
    val step = niceStep((hi - lo + 3) / 4)
    lo = lo.floorDiv(step) * step
    hi = -((-hi).floorDiv(step)) * step
    if (hi == lo) hi = lo + step
    return ChartScale(lo, hi, step)
}

/** فهارس السنين اللي بتتكتب تحت: كل سنة (≤ 5) · كل 2 أو 5 (≤ 10) · كل 5 (≤ 20) · كل 10 — والأخيرة دايمًا. */
fun xLabelIndices(n: Int): List<Int> {
    val every = when {
        n <= 5 -> 1
        n <= 10 -> if (n % 2 == 1) 5 else 2
        n <= 20 -> 5
        else -> 10
    }
    val idx = (0..n step every).toMutableList()
    if (idx.last() != n) {
        if (n - idx.last() < every / 2.0 && idx.size > 1) idx.removeAt(idx.lastIndex)
        idx.add(n)
    }
    return idx
}

/** موضع X (0..1) للهدف: الشهور ÷ 12 ÷ عدد السنين — بره المدة ⇒ null. */
fun flagFraction(f: ChartFlag, n: Int): Float? {
    if (n <= 0 || f.monthsFromStart < 0 || f.monthsFromStart > n * 12) return null
    return f.monthsFromStart / (n * 12f)
}
