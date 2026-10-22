package app.masroufy.ui.screens.dues

import app.masroufy.core.BnplKey
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.BnplProvider
import app.masroufy.core.BnplStepState
import app.masroufy.core.Category
import app.masroufy.core.UiKey
import app.masroufy.ui.components.bankLogoPaint
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * r051 — قطع لوحة «ادفع بعدين» وقسم «مشتريات بالتقسيط» (التصميم المعتمد `BnplSheet.dc.html` · `Installments.dc.html`): شارة المزوّد ·
 * الشرايح الخضرا · المفتاح · وسم «اتدفعت/الجاية» · اختيار شرايح التصنيف. عرض بس — مفيش حساب فلوس هنا.
 */

/** اسم المزوّد بلغة التطبيق (بالعربي من غير حروف لاتيني). */
internal fun bnplName(p: BnplProvider): String = t(
    when (p) {
        BnplProvider.TABBY -> BnplKey.BNPL2_PROV_TABBY
        BnplProvider.TAMARA -> BnplKey.BNPL2_PROV_TAMARA
        BnplProvider.MADFU -> BnplKey.BNPL2_PROV_MADFU
        BnplProvider.MISPAY -> BnplKey.BNPL2_PROV_MISPAY
        BnplProvider.POSTPAY -> BnplKey.BNPL2_PROV_POSTPAY
        BnplProvider.SPOTII -> BnplKey.BNPL2_PROV_SPOTII
        BnplProvider.VALU -> BnplKey.BNPL2_PROV_VALU
        BnplProvider.SYMPL -> BnplKey.BNPL2_PROV_SYMPL
        BnplProvider.SOUHOOLA -> BnplKey.BNPL2_PROV_SOUHOOLA
    },
)

/**
 * لون الشارة قريب من لون المزوّد (اختيار التصميم (ج) — نص مش لوجو): تابي وتمارا بألوان النموذج بالظبط، والباقيين ألوان Claude التقريبية
 * (سؤال مفتوح للمالك). [ink] النص · [fill] الخلفية.
 */
internal class BnplTint(val ink: Color, val fill: Color)

internal fun bnplTint(p: BnplProvider): BnplTint = when (p) {
    BnplProvider.TABBY -> BnplTint(Color(0xFF0B3B2E), Color(0xFF3BFFC1))
    BnplProvider.TAMARA -> BnplTint(Color.White, Color(0xFF1F1F3A))
    BnplProvider.MADFU -> BnplTint(Color.White, Color(0xFF5B2A86))
    BnplProvider.MISPAY -> BnplTint(Color.White, Color(0xFF3A2E8C))
    BnplProvider.POSTPAY -> BnplTint(Color.White, Color(0xFF111111))
    BnplProvider.SPOTII -> BnplTint(Color.White, Color(0xFFE5486B))
    BnplProvider.VALU -> BnplTint(Color.White, Color(0xFF0A8F80))
    BnplProvider.SYMPL -> BnplTint(Color.White, Color(0xFF2456E0))
    BnplProvider.SOUHOOLA -> BnplTint(Color.White, Color(0xFF1C64C8))
}

/** لوجو رسمي موجود في التطبيق (دليل البنوك) — ڤاليو بس لحد دلوقتي. */
private fun bnplLogoKey(p: BnplProvider): String? = if (p == BnplProvider.VALU) "eg_valu" else null

/** شارة المزوّد: مربع [size] باللوجو الرسمي لو موجود، وإلا اسمه بلون قريب من لونه (56 في اللوحة · 44 في الكارت). */
@Composable
internal fun BnplBadge(p: BnplProvider, size: Dp = 56.dp, fontSize: Int = 15) {
    val shape = RoundedCornerShape(size * 0.32f)
    val name = bnplName(p)
    val a11y = Modifier.semantics { contentDescription = name; role = Role.Image }
    val paint = bnplLogoKey(p)?.let { bankLogoPaint(it) }
    if (paint != null) {
        Box(Modifier.size(size).clip(shape).background(if (paint.onDark) Ink.text else Color.White).then(a11y), contentAlignment = Alignment.Center) {
            Image(paint.painter, contentDescription = null, modifier = Modifier.fillMaxSize().padding(size * 0.12f), contentScale = ContentScale.Fit)
        }
        return
    }
    val tint = bnplTint(p)
    Box(
        Modifier.defaultMinSize(minWidth = size).height(size).clip(shape).background(tint.fill).then(a11y).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(name, style = Type.of(fontSize, FontWeight.Bold).copy(color = tint.ink), maxLines = 1) }
}

internal val BnplGreen get() = Brush.linearGradient(listOf(Color(0xFF0A7A62), Ink.primary.copy(alpha = 0xFF / 255f)))
private val ChipOff get() = Ink.tint(0x0F)

/** شريحة اختيار بشكل اللوحة: المختارة خضرا بنص أبيض عريض، والباقي رمادي فاتح — ارتفاع 44 وحواف 14. */
@Composable
internal fun BnplChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(44.dp).pressScale(press).clip(shape).then(if (selected) Modifier.background(BnplGreen) else Modifier.background(ChipOff))
            .tap(press, onClick = onClick).semantics { role = Role.RadioButton; this.selected = selected }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.of(14, if (selected) FontWeight.Bold else FontWeight.Normal).copy(color = if (selected) Color.White else Ink.text), maxLines = 1)
    }
}

/** شرايح بعرض متساوي في صف («قسمتها على كام؟» · «كل قد إيه؟»). */
@Composable
internal fun BnplChipRow(content: @Composable RowScope.() -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

/** المفتاح 44×26 (أخضر لما يشتغل) بمساحة لمس 56×48. */
@Composable
internal fun BnplSwitch(on: Boolean, label: String, onToggle: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(56.dp, 48.dp).tap(press, onClick = onToggle)
            .semantics { role = Role.Switch; contentDescription = label; stateDescription = bnplSwitchState(on) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).then(if (on) Modifier.background(BnplGreen) else Modifier.background(Ink.tint(0x29)))) {
            // التصميم: شغال ⇒ الدايرة على الشمال في العربي (`right: 20px`)
            Box(Modifier.padding(2.dp).size(22.dp).align(if (on) Alignment.CenterEnd else Alignment.CenterStart).clip(RoundedCornerShape(11.dp)).background(Color.White))
        }
    }
}

/** حالة المفتاح لقارئ الشاشة بلغة الجوال («شغّال» · «مقفول») — مش رقم. */
internal fun bnplSwitchState(on: Boolean): String = t(if (on) UiKey.PPL_SWITCH_ON else UiKey.PPL_SWITCH_OFF)

/** الزرار التاني اللازق تحت («تخطّي» · «بعدين»): رمادي فاتح 7% بنص غامق عريض — ارتفاع 52 وحواف 18. */
@Composable
internal fun BnplQuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    Box(
        modifier.height(52.dp).pressScale(press).clip(RoundedCornerShape(18.dp)).background(Ink.tint(0x12))
            .tap(press, onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Type.of(15, FontWeight.Bold).copy(color = Ink.text), maxLines = 1) }
}

/** وسم «اتدفعت» (أخضر) و«الجاية» (أصفر) — الباقي من غير وسم. */
@Composable
internal fun BnplTag(state: BnplStepState, before: Boolean = false) {
    val (text, ink, fill) = if (before && state == BnplStepState.PAID) Triple(t(app.masroufy.core.R06Key.BS_TAG_BEFORE), Ink.muted, Ink.tint(0x12)) else when (state) {
        // r06-1b4: دفعة اتدفعت قبل ما الخطة تتسجل (من غير عملية) ⇒ «تم الدفع» رمادي
        BnplStepState.PAID -> Triple(t(BnplKey.BNPL2_TAG_PAID), Ink.income, Color(0xFFDCEBD6))
        BnplStepState.NEXT -> Triple(t(BnplKey.BNPL2_TAG_NEXT), Color(0xFF6B4600), Color(0xFFFBF0DD))
        BnplStepState.LATER -> return
    }
    BasicText(text, Modifier.clip(RoundedCornerShape(10.dp)).background(fill).padding(horizontal = 8.dp, vertical = 1.dp), style = Type.of(11, FontWeight.Bold).copy(color = ink))
}

/** نقطة الدفعة في «خطتك»: المدفوعة مليانة، والباقي دايرة بحد أخضر. */
@Composable
internal fun BnplDot(paid: Boolean) {
    val m = Modifier.size(12.dp).clip(CircleShape)
    Box(if (paid) m.background(Ink.primary) else m.background(Ink.card).border(2.dp, Ink.primary.copy(alpha = 0.45f), CircleShape))
}

/**
 * شرايح التصنيف في «أول خصم» (التصميم: التسوق · إلكترونيات وأجهزة · ملابس · المنزل · سفر · «تصنيف تاني…»): من تصنيفات المالك الشغالة
 * بالاسم، الأساسي قبل الفرعي — اللي مش موجود ما بيظهرش.
 */
internal fun bnplCategoryChips(all: List<Category>): List<Category> {
    val active = all.filter { it.active }.sortedWith(compareBy({ it.parentId != null }, { it.order }))
    val wants = listOf(listOf("تسوق"), listOf("إلكترونيات", "أجهزة"), listOf("ملابس", "لبس"), listOf("منزل", "بيت"), listOf("سفر"))
    return wants.mapNotNull { keys -> active.firstOrNull { c -> keys.any { c.name.contains(it) } } }.distinctBy { it.id }
}
