package app.masroufy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.Backdrop
import app.masroufy.ui.glass.BackdropBlur
import app.masroufy.ui.glass.BlurRadius
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.cssLinear
import app.masroufy.ui.glass.cssRadialGlow
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.theme.Radius

/**
 * أسطح نظام التصميم v0.4 بالوصفات الحرفية (DESIGN-SYSTEM «وصفات الأسطح» و«وصفات الزجاج»).
 * القاعدة: **الكروت طافية بالظل مش زجاج**؛ الزجاج للي بيطفو فوق المحتوى بس. ممنوع إطار على الأسطح.
 */

private val backgroundBase = cssLinear(155f, 0f to Color(0xFFFAF9F3), 0.48f to Color(0xFFEFF3ED), 1f to Color(0xFFDCE9DF))

/** خلفية الشاشة: إضاءات ناعمة (أخضر وأزرق خفيف) فوق تدرج دافي — عشان الزجاج يبان زجاج. ممنوع حلقات أو دواير زخرفية. */
fun Modifier.screenBackground(): Modifier = background(backgroundBase).drawBehind {
    cssRadialGlow(0.70f, 0.38f, 1f, 0f, Color(0x73A6DEC1), 0.7f)
    cssRadialGlow(0.60f, 0.34f, 0f, 0.62f, Color(0x299BC5FF), 0.7f)
    cssRadialGlow(0.80f, 0.40f, 0.5f, 1f, Color(0x66A6DEC1), 0.7f)
}

/**
 * الكارت الطافي (كل الكروت والحقول والأزرار الثانوية): أبيض دافي + ظل ناعم 3 طبقات + لمعة بيضا فوق. [onClick] ⇒ قابل للضغط بحالاته.
 * [contentPadding] الافتراضي 14/16 زي كروت النموذج.
 */
@Composable
fun FloatingCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.card),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    clickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val press = rememberPress()
    val far = farShadowFactor(press, enabled)
    val clickable = if (onClick != null) Modifier.tap(press, enabled, label = clickLabel, onLongClick = onLongClick, onClick = onClick) else Modifier
    Column(
        modifier
            .pressScale(press, enabled && onClick != null)
            .layeredShadow(shape, Shadows.card, far)
            .clip(shape)
            .background(Glass.card)
            .innerSheen(shape, Shadows.card)
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

private val heroGlowTop = Color(0x33FFFFFF)
private val heroGlowBottom = Color(0x2E96F3CC)

/**
 * البطاقة البطلة (البترولية) — العنصر الرئيسي في كل شاشة: تدرج بترولي + لمعة ضوء فوق + لمعة نعناعي تحت + ظل أخضر ملوّن.
 * النص جواها أبيض، والثانوي `Ink.onHeroMuted`.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.hero)
    Column(
        modifier
            .layeredShadow(shape, Shadows.hero)
            .clip(shape)
            .background(Glass.heroBase)
            .drawBehind {
                cssRadialGlow(0.90f, 0.70f, 0.88f, 0f, heroGlowTop, 0.55f)
                cssRadialGlow(0.70f, 0.60f, 0f, 1f, heroGlowBottom, 0.60f)
            }
            .innerSheen(shape, Shadows.hero)
            .padding(contentPadding),
        content = content,
    )
}

/** أيقونة التصنيف (36×36 زاوية 12): خلفية لون التصنيف بشفافية 12% + لمعة داخلية، والرمز بلون التصنيف. */
@Composable
fun IconTile(color: Color, modifier: Modifier = Modifier, size: Dp = 36.dp, radius: Dp = Radius.iconTile, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.size(size).clip(shape).background(color.copy(alpha = 0.12f)).innerSheen(shape, Shadows.iconTile),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** عدسة على الفاتح (رمز صاحب الحساب 46 دائري · الميكروفون · رمز البلد) — زجاج بتمويه 8 من أندرويد 12. */
@Composable
fun LensOnLight(modifier: Modifier = Modifier, shape: Shape = CircleShape, backdrop: Backdrop? = null, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.layeredShadow(shape, Shadows.lensOnLight).clip(shape), contentAlignment = Alignment.Center) {
        BackdropBlur(backdrop, BlurRadius.lens, shape)
        Box(Modifier.matchParentSize().background(Glass.lensOnLight).innerSheen(shape, Shadows.lensOnLight))
        content()
    }
}

/**
 * عدسة على البترولي (46×46 زاوية 16 جوه البطاقة البطلة · 76×76 زاوية 26 لرمز «أنت»): زجاج + **لمعتين موضعيتين**
 * (علوية بيضا مايلة -8° وسفلية نعناعي) بدل إطار أبيض مستمر. اللمعتين مكانهم فيزيائي (زي CSS) مهما كان اتجاه اللغة.
 */
@Composable
fun LensOnHero(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(Radius.lensSmall), background: Brush = Glass.lensOnHero, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.layeredShadow(shape, Shadows.lensOnHero).clip(shape).background(background).innerSheen(shape, Shadows.lensOnHero).drawBehind { lensSheens() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private val sheenTop = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xD9FFFFFF), Color.Transparent))
private val sheenBottom = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xB3AAFCDB), Color.Transparent))

/** علوية: top 2 · left 14% · عرض 56% · ارتفاع 2 · مايلة -8° — سفلية: bottom 2 · right 12% · عرض 46% · ارتفاع 3. */
internal fun DrawScope.lensSheens() {
    val w = size.width
    val h = size.height
    val two = 2.dp.toPx()
    val topLeft = Offset(w * 0.14f, two)
    val topSize = Size(w * 0.56f, two)
    rotate(-8f, pivot = Offset(topLeft.x + topSize.width / 2f, topLeft.y + topSize.height / 2f)) {
        translate(topLeft.x, topLeft.y) { drawRoundRect(sheenTop, size = topSize, cornerRadius = CornerRadius(two)) }
    }
    val bottomSize = Size(w * 0.46f, 3.dp.toPx())
    translate(w - w * 0.12f - bottomSize.width, h - two - bottomSize.height) {
        drawRoundRect(sheenBottom, size = bottomSize, cornerRadius = CornerRadius(bottomSize.height / 2f))
    }
}

/** خط فاصل جوه كارت: `1px solid rgba(204,216,204,0.55)`. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(app.masroufy.ui.theme.Ink.line))
}

/** فاصل رأسي رفيع (جوه البطاقة البطلة: `rgba(255,255,255,0.14)`). */
@Composable
fun HeroDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Color(0x24FFFFFF)))
}

