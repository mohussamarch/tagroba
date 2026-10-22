package app.masroufy.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * لقطة عملة واحدة (وحدات مربع التصميم 64): [offsetY] إزاحة رأسية (سالب = فوق مكانها) · [scaleX]/[scaleY] تفلطح الهبوط حوالين
 * نص القاعدة · [alpha] الشفافية 0..1.
 */
data class CoinFrame(val offsetY: Float, val alpha: Float, val scaleX: Float = 1f, val scaleY: Float = 1f)

/** ظل الأرضية تحت الرصة: [scaleX] عرضه · [alpha] شفافيته. */
data class FloorFrame(val scaleX: Float, val alpha: Float)

/**
 * توقيت علامة التحميل «عملات بتترص» — النسخة النهائية (`CoinLoader.dc.html`، قرار المالك 2026-10-22). **دالة صافية** بتتختبر من غير شاشة.
 * الدورة 2.4 ثانية، كل عملة بعد اللي قبلها بـ0.28 ثانية. العملة: تنزل بتسارع من -44 (شفافة) لمكانها وتتفلطح عند الهبوط (1.07×0.8)،
 * وترتد 5 وتستقر، وتثبت لحد 78%، وفي الآخر تنزل 8 وتخفت (92%). حركة وشفافية بس (transform + opacity).
 */
object CoinLoaderTiming {
    const val COINS = 3
    const val CYCLE_MS = 2400
    const val STAGGER_MS = 280
    const val START_Y = -44f
    const val BOUNCE = 5f
    const val END_DROP = 8f
    const val SHINE_BASE_MS = 900
    const val SHINE_STEP_MS = 120
    const val SHINE_FROM = -30f
    const val SHINE_TO = 30f
    const val LAND_AT = 0.22f
    const val FADE_START = 0.78f
    const val FADE_END = 0.92f

    /** حالة «تقليل الحركة»: العملات التلاتة ثابتة وظاهرة. */
    val STATIC = CoinFrame(0f, 1f)

    private val fall = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)
    private val rise = CubicBezierEasing(0f, 0.55f, 0.45f, 1f)
    private val away = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    private val shine = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    private val inOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun seg(p: Float, from: Float, to: Float, ease: Easing) = ease.transform(((p - from) / (to - from)).coerceIn(0f, 1f))

    /** طور العملة رقم [coin] (0 = الأسفل) من 0 لـ1 عند الزمن [timeMs]. */
    fun phase(timeMs: Float, coin: Int): Float = (timeMs - coin * STAGGER_MS).mod(CYCLE_MS.toFloat()) / CYCLE_MS

    /** لقطة العملة رقم [coin] عند الزمن [timeMs] من بداية العرض. */
    fun frame(timeMs: Float, coin: Int): CoinFrame = frameAtPhase(phase(timeMs, coin))

    fun frameAtPhase(p: Float): CoinFrame {
        // الشفافية: 0→8% ظهور (بمنحنى السقوط) · ثابتة لـ78% · تخفت لـ92%
        val alpha = when {
            p < 0.08f -> seg(p, 0f, 0.08f, fall)
            p < FADE_START -> 1f
            else -> 1f - seg(p, FADE_START, FADE_END, away)
        }
        return when {
            p < LAND_AT -> { val e = seg(p, 0f, LAND_AT, fall); CoinFrame(mix(START_Y, 0f, e), alpha, mix(1f, 1.07f, e), mix(1f, 0.8f, e)) }
            p < 0.29f -> { val e = seg(p, LAND_AT, 0.29f, rise); CoinFrame(mix(0f, -BOUNCE, e), alpha, mix(1.07f, 0.98f, e), mix(0.8f, 1.03f, e)) }
            p < 0.35f -> { val e = seg(p, 0.29f, 0.35f, fall); CoinFrame(mix(-BOUNCE, 0f, e), alpha, mix(0.98f, 1f, e), mix(1.03f, 1f, e)) }
            p < FADE_START -> CoinFrame(0f, alpha)
            p < FADE_END -> { val e = seg(p, FADE_START, FADE_END, away); CoinFrame(mix(0f, END_DROP, e), alpha, mix(1f, 0.96f, e), mix(1f, 0.96f, e)) }
            else -> CoinFrame(END_DROP, 0f, 0.96f, 0.96f)
        }
    }

    /** إزاحة لمعة العملة [coin] أفقيًا (وحدات العملة 48): بتبدأ بعد 0.9 ثانية + 0.12 لكل عملة، وتعدّي من -30 لـ+30 بين 50% و72% من الدورة. */
    fun shineX(timeMs: Float, coin: Int): Float {
        val p = (timeMs - SHINE_BASE_MS - coin * SHINE_STEP_MS).mod(CYCLE_MS.toFloat()) / CYCLE_MS
        return mix(SHINE_FROM, SHINE_TO, seg(p, 0.5f, 0.72f, shine))
    }

    /** ظل الأرضية: ضيّق وباهت في الأول والآخر، وعريض وواضح بين 40% و78%. */
    fun floor(timeMs: Float): FloorFrame {
        val p = timeMs.mod(CYCLE_MS.toFloat()) / CYCLE_MS
        val k = when {
            p < 0.10f -> 0f
            p < 0.40f -> seg(p, 0.10f, 0.40f, inOut)
            p < FADE_START -> 1f
            else -> 1f - seg(p, FADE_START, 1f, inOut)
        }
        return FloorFrame(mix(0.55f, 1f, k), mix(0.4f, 1f, k))
    }
}
