package app.masroufy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.Bank
import app.masroufy.core.bankLabel
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.theme.CategoryInk
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** صورة لوجو جاهزة للرسم: [onDark] = لوجو أبيض معمول لخلفية غامقة (بيتعرض عليها زي ما هو — §76 «من غير تعديل»). */
class BankLogoPaint(val painter: Painter, val onDark: Boolean)

/**
 * صورة اللوجو لمفتاح `Bank.logo` على المنصة: أندرويد = صور WebP في drawable-nodpi (`BankLogos.android.kt` — متولّد بـ
 * `node scripts/bank-catalog.mjs`) · الكمبيوتر (اختبارات) والآيفون لحد دلوقتي = null ⇒ الحرف في دايرة.
 */
@Composable
internal expect fun bankLogoPaint(logo: String): BankLogoPaint?

private val LOGO_DARK = Color(0xFF193D33)

/** ألوان الدايرة لما مفيش لوجو — من ألوان التصنيفات (ثابت لكل اسم عشان البنك يفضل بنفس لونه في كل شاشة). */
private val TINTS = listOf(CategoryInk.shopping, CategoryInk.savings, CategoryInk.transport, CategoryInk.restaurants, CategoryInk.gifts, Ink.transfer)

/** حرف مكان اللوجو: أول حرف من الاسم من غير «بنك/مصرف/شركة» و«ال». */
fun logoInitial(name: String): String {
    val skip = setOf("بنك", "مصرف", "البنك", "المصرف", "شركة")
    val word = name.trim().split(Regex("\\s+")).firstOrNull { it !in skip } ?: name.trim()
    return word.removePrefix("ال").take(1).uppercase()
}

/**
 * لوجو البنك (OVERRIDES §81 · §76): الصورة زي ما هي جوه مربع أبيض بحواف مدورة (أو غامق لو اللوجو أبيض)، و**من غير لوجو** (بنك مش في
 * الدليل · التجاري الدولي · الكمبيوتر) ⇒ أول حرف من [name] في دايرة ملوّنة. [wide] = مستطيل أعرض للوجوهات الطويلة (قايمة «اختار بنكك»).
 * [name] = اسم المحفظة أو المرسل — بيتقري لقارئ الشاشة. [fallback] = false ⇒ من غير لوجو ما يترسمش حاجة (الشارة الصغيرة على صف العملية).
 * [letterInk] = الحرف لوحده باللون ده من غير دايرة (جوه عدسة زجاج على البطاقة البطلة — الدايرة الفاتحة ما بتبانش على الغامق).
 */
@Composable
fun BankLogo(bank: Bank?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, wide: Boolean = false, fallback: Boolean = true, letterInk: Color? = null) {
    val label = bank?.let(::bankLabel) ?: name
    val a11y = Modifier.semantics { contentDescription = label; role = Role.Image }
    val paint = bank?.logo?.let { bankLogoPaint(it) }
    if (paint == null) {
        if (!fallback) return
        if (letterInk != null) {
            Box(modifier.size(size).then(a11y), contentAlignment = Alignment.Center) {
                BasicText(logoInitial(label), style = Type.of((size.value * 0.5f).toInt().coerceAtLeast(10), FontWeight.Bold).copy(color = letterInk))
            }
            return
        }
        val tint = TINTS[(label.hashCode() and Int.MAX_VALUE) % TINTS.size]
        Box(modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)).then(a11y), contentAlignment = Alignment.Center) {
            BasicText(logoInitial(label), style = Type.of((size.value * 0.4f).toInt().coerceAtLeast(10), FontWeight.Bold).copy(color = tint))
        }
        return
    }
    val shape = RoundedCornerShape(size * 0.3f)
    val width = if (wide) size * 1.5f else size
    Box(
        modifier.size(width, size).clip(shape).background(if (paint.onDark) LOGO_DARK else Color.White)
            .then(if (paint.onDark) Modifier else Modifier.insetRing(shape, 1.dp, Ink.fieldEdge)).then(a11y),
        contentAlignment = Alignment.Center,
    ) {
        Image(paint.painter, contentDescription = null, modifier = Modifier.fillMaxSize().padding(size * 0.12f), contentScale = ContentScale.Fit)
    }
}
