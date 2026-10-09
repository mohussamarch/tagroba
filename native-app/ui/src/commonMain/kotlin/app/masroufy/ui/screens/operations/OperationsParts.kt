package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlin.coroutines.cancellation.CancellationException

/** قطع صغيرة مشتركة بين شاشات «العمليات» (بنفس قيم النموذج). */

/** نداء حالة استخدام: الفشل ⇒ `null` (الشاشة بتعرض حالة الخطأ)، والإلغاء بيعدّي زي ما هو. */
internal suspend inline fun <T> attempt(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** نفس [attempt] لكن بيرجّع رسالة الخطأ اللي تتعرض ([userMessage]). `null` = نجح. */
internal suspend inline fun failureOf(block: () -> Unit): String? = try {
    block()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    userMessage(e)
}

/**
 * رسالة الخطأ للمستخدم: رفض حالة الاستخدام (`IllegalArgumentException`/`IllegalStateException` — رسالتها من جدول النصوص بلغة البلد) بيتعرض
 * زي ما هو؛ أي فشل تاني (النت · التخزين — رسالته تقنية وممكن تبقى إنجليزي) ⇒ «تعذّر الحفظ — لم يتغير شيء» (حالة «خطأ» في النموذج).
 */
fun userMessage(e: Throwable): String {
    val text = e.message?.takeIf { it.isNotBlank() }
    return if ((e is IllegalArgumentException || e is IllegalStateException) && text != null) text else t(TextKey.OPERATIONS_SAVE_FAILED)
}

/** شريط الخطأ الكهرماني (النموذج: «تعذّر تحميل العمليات» + «أعد المحاولة»). */
@Composable
internal fun ErrorBanner(title: String, body: String, retryLabel: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.control)).background(Ink.alertBg)
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
            BasicText(body, style = Type.of(12).copy(color = Ink.focus))
        }
        val press = rememberPress()
        Box(
            Modifier.height(48.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .tap(press, onClick = onRetry).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(retryLabel, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary)) }
    }
}

/** شريط «مستنياك» (رسائل البنك · المراجعة · التحويلات): نقطة ملوّنة + الجملة + سهم، على خلفية خفيفة بلونه. */
@Composable
internal fun WaitingBanner(text: String, dot: Color, background: Color, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(press).clip(RoundedCornerShape(Radius.control)).background(background)
            .tap(press, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        BasicText(text, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
        LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.text, modifier = Modifier.mirrorInLtr())
    }
}

/** زرار الشهر فوق («أكتوبر» برمز التقويم) — كارت طافي 48 بزاوية 18. */
@Composable
internal fun MonthButton(label: String, onClick: () -> Unit) {
    FloatingCard(
        Modifier.height(48.dp),
        shape = RoundedCornerShape(Radius.control),
        contentPadding = PaddingValues(horizontal = 14.dp),
        onClick = onClick,
        clickLabel = label,
    ) {
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(Lucide.CALENDAR, size = 18.dp)
            BasicText(label, style = Type.of(13))
        }
    }
}

/** شارة صغيرة («مقترح» كهرماني · «مؤكد» أخضر · «مستورد» أزرق). */
@Composable
internal fun StatusChip(text: String, ink: Color, background: Color) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 8.dp, vertical = 2.dp)) {
        BasicText(text, style = Type.of(11, FontWeight.Bold).copy(color = ink), maxLines = 1)
    }
}

/** زرار صغير جنب الخانة («غيّر» هادي · «تأكيد» أخضر) — 36 بزاوية 12. */
@Composable
internal fun SmallAction(label: String, strong: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.height(36.dp).pressScale(press, enabled).clip(RoundedCornerShape(Radius.iconTile))
            .background(if (strong) Ink.primary else Color(0x1408634F)).tap(press, enabled, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Type.of(12, FontWeight.Bold).copy(color = if (strong) Ink.onPrimary else Ink.primary)) }
}

/** صندوق ملاحظة خفيف (`rgba(25,61,51,0.04)`) — «٣ تحويلات بلا طرف…» · ملاحظة القفل. */
@Composable
internal fun NoteBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.control)).background(Color(0x0A193D33)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        BasicText(text, style = Type.of(12).copy(color = Ink.muted))
    }
}

/**
 * كارت ربط تحت «اربطها بـ» (شخص · مشروع أو حدث): رمز 36 · العنوان والسبب · زرار 48 هادي («اربط» · «غيّر») — المقفول بسبب مكتوب تحته
 * ([sub] = السبب، زي النموذج `aria-describedby`). [extra] = سطور الربط الحالي تحت.
 */
@Composable
internal fun LinkCard(
    icon: Lucide,
    title: String,
    sub: String,
    action: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    extra: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(Radius.iconTile)).background(Color(0x1A08634F)), contentAlignment = Alignment.Center) {
                LucideIcon(icon, size = 20.dp, tint = Ink.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = Type.of(14, FontWeight.Bold))
                BasicText(sub, style = Type.of(12).copy(color = Ink.muted))
            }
            app.masroufy.ui.components.TonalButton(action, onOpen, enabled = enabled, height = 48.dp)
        }
        extra()
    }
}

/** ألوان الشارات من النموذج. */
internal object ChipInk {
    val amber = Ink.focus
    val amberBg = Ink.alertBg
    val green = Ink.primary
    val greenBg = Ink.selected
    val blue = Ink.transfer
    val blueBg = Color(0x1A2469BA)
    val grey = Ink.muted
    val greyBg = Color(0x0F193D33)
}
