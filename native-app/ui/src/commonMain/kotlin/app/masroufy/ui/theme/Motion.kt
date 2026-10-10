package app.masroufy.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * الحركة (DESIGN-SYSTEM v0.4 «3 نوابض بأسماء»): **استعمل الاسم، مش رقم جديد**. ممنوع حركة خطية وممنوع نابض رابع من غير قرار.
 * «تقليل الحركة» في الجهاز ([LocalReduceMotion]) ⇒ كل حركة تبقى شفافية بس ≤ 150ms ([motion]).
 */
enum class Springs {
    /** الضغط، التبويبات، التبديل — `220ms cubic-bezier(.2,.7,.3,1)`. */
    SNAPPY,

    /** الصفحات، اللوحات، ظهور البطاقة البطلة، الأرقام — `450ms`. */
    GENTLE,

    /** الزجاج السائل بس: القائمة المنبثقة، ارتفاع العملية، العدسة المختارة — `420ms cubic-bezier(.18,.9,.28,1.08)`. */
    BOUNCY,
    ;

    fun <T> spec(): AnimationSpec<T> = when (this) {
        SNAPPY -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        GENTLE -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        BOUNCY -> spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    }
}

/** «تقليل الحركة» من إعدادات الجهاز — التطبيق بيقراه ويديه هنا (`MasroufyTheme`). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** أزمنة v0.2 المعتمدة (لما مكان مش نابض: الضغط المطوّل وظهور الشفافية). */
object MotionMs {
    const val press = 160
    const val page = 220
    const val sheet = 450
    const val holdThreshold = 440L
    const val holdCancelPx = 9f
    const val reduceFade = 150
    const val sheen = 1200
    const val confettiMax = 2000
}

/** النابض بالاسم، أو ظهور بالشفافية ≤ 150ms لو «تقليل الحركة» شغال. */
@Composable
@ReadOnlyComposable
fun <T> motion(spring: Springs): AnimationSpec<T> =
    if (LocalReduceMotion.current) tween(MotionMs.reduceFade, easing = FastOutSlowInEasing) else spring.spec()
