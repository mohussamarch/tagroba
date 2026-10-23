package app.masroufy.ui.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.masroufy.core.Space
import app.masroufy.core.bothCountriesActive

/**
 * cty-9 — «البلدين شغالين» **من مكان واحد** (`bothCountriesActive` على `ShellDeps.openSpaces`)، متقدّم مرة في `AppShell`.
 * [both] = أكتر من بلد مفتوحة ⇒ علامة البلد بتظهر على المحافظ والكروت والديون ومصادر الدخل والعمليات. بلد واحدة ⇒ ولا علامة.
 */
data class CountriesInfo(val active: Space?, val open: List<Space>) {
    val both: Boolean get() = bothCountriesActive(open)

    /** البلد التانية المفتوحة (للسداد بعملتها وسطر «بدّل ليها») — null لو بلد واحدة. */
    val other: Space? get() = if (both) open.firstOrNull { it.id != active?.id && !it.archived } else null

    /** كود البلد للعلامة، أو null لو مش لازم علامة (بلد واحدة). */
    fun markFor(countryCode: String?): String? = countryCode?.takeIf { both }

    companion object {
        fun of(active: Space, open: List<Space>) = CountriesInfo(active, open.ifEmpty { listOf(active) })
    }
}

val LocalCountries = staticCompositionLocalOf { CountriesInfo(null, emptyList()) }

private val RING = 1.5.dp
private val MARK = 13.dp

/**
 * علامة البلد المعتمدة (فرع التصميم `1c4eba9f` — `CountryMark.dc.html`): علم مدوّر 13dp بحلقة بيضا 1.5dp، مرسوم vector (مش صورة).
 * مصر: أحمر · أبيض · أسود ونقطة دهبي. السعودية: أخضر بخطين أبيض وسيف.
 */
@Composable
fun CountryMark(countryCode: String, modifier: Modifier = Modifier) {
    val eg = countryCode.equals("EG", ignoreCase = true)
    Box(modifier.size(MARK + RING * 2).clip(CircleShape).background(Color.White).padding(RING)) {
        Canvas(Modifier.size(MARK).clip(CircleShape)) {
            val u = size.width / 24f
            if (eg) {
                drawRect(Color(0xFFCE1126), size = size.copy(height = 8 * u))
                drawRect(Color.White, topLeft = Offset(0f, 8 * u), size = size.copy(height = 8 * u))
                drawRect(Color(0xFF111111), topLeft = Offset(0f, 16 * u), size = size.copy(height = 8 * u))
                drawCircle(Color(0xFFC09300), radius = 2.2f * u, center = Offset(12 * u, 12 * u))
            } else {
                drawRect(Color(0xFF006C35))
                drawLine(Color.White, Offset(7 * u, 9.5f * u), Offset(17 * u, 9.5f * u), 1.3f * u, StrokeCap.Round)
                drawLine(Color.White, Offset(7.8f * u, 12 * u), Offset(16.2f * u, 12 * u), 1.3f * u, StrokeCap.Round)
                val sword = Path().apply { moveTo(7.5f * u, 15.5f * u); lineTo(16.5f * u, 15.5f * u); lineTo(15.1f * u, 14.5f * u) }
                drawPath(sword, Color.White, style = Stroke(1.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * الأيقونة ومعاها العلامة في ركنها اللي ناحية الاسم (فوق شمال — الاسم على شمال الأيقونة في العربي، زي التصميم `left: -3px`).
 * [countryCode] = null ⇒ الأيقونة لوحدها (بلد واحدة أو الحاجة مالهاش بلد).
 */
@Composable
fun WithCountryMark(countryCode: String?, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    val code = LocalCountries.current.markFor(countryCode)
    if (code == null) {
        Box(modifier) { icon() }
        return
    }
    Box(modifier) {
        icon()
        CountryMark(code, Modifier.align(AbsoluteAlignment.TopLeft).offset(x = (-3).dp - RING, y = (-3).dp - RING))
    }
}

/** نفس [WithCountryMark] ببلد الشاشة (البلد الشغالة) — للمحافظ والكروت والديون ومصادر الدخل والعمليات اللي كلها من البلد دي. */
@Composable
fun WithActiveCountryMark(modifier: Modifier = Modifier, icon: @Composable () -> Unit) =
    WithCountryMark(LocalCountries.current.active?.countryCode, modifier, icon)
