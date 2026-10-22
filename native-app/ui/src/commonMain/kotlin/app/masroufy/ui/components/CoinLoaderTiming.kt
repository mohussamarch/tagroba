package app.masroufy.ui.components

import androidx.compose.animation.core.CubicBezierEasing

/** لقطة عملة واحدة: [offsetY] = إزاحة رأسية بوحدة التصميم 64 (سالب = فوق مكانها) · [alpha] = الشفافية 0..1. */
data class CoinFrame(val offsetY: Float, val alpha: Float)

/**
 * توقيت علامة التحميل «عملات بتترص» (`Loaders.dc.html` خيار 1 — قرار المالك 2026-10-22) — **دالة صافية** بتتختبر من غير شاشة:
 * 3 عملات، الدورة 1.8 ثانية، الفرق بين عملة وعملة 0.25 ثانية. كل عملة: أول 22% بتنزل 30 وحدة لمكانها بنابض زيادة
 * `cubic-bezier(.3,.9,.4,1.15)` وتظهر، وبعدين تثبت لحد 78%، وبعدين تختفي (بنفس المنحنى) لآخر الدورة.
 */
object CoinLoaderTiming {
    const val COINS = 3
    const val CYCLE_MS = 1800
    const val STAGGER_MS = 250
    const val DROP = 30f
    const val APPEAR_END = 0.22f
    const val FADE_START = 0.78f

    /** حالة «تقليل الحركة»: العملات التلاتة ثابتة وظاهرة. */
    val STATIC = CoinFrame(0f, 1f)

    private val overshoot = CubicBezierEasing(0.3f, 0.9f, 0.4f, 1.15f)

    /** لقطة العملة رقم [coin] (0 = الأسفل) عند الزمن [timeMs] من بداية العرض. */
    fun frame(timeMs: Float, coin: Int): CoinFrame {
        val phase = (timeMs - coin * STAGGER_MS).mod(CYCLE_MS.toFloat()) / CYCLE_MS
        return when {
            phase < APPEAR_END -> {
                val e = overshoot.transform(phase / APPEAR_END)
                CoinFrame(-DROP * (1f - e), e.coerceIn(0f, 1f))
            }
            phase < FADE_START -> CoinFrame(0f, 1f)
            else -> CoinFrame(0f, (1f - overshoot.transform((phase - FADE_START) / (1f - FADE_START))).coerceIn(0f, 1f))
        }
    }
}
