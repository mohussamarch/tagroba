package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CancellationException

/** نتيجة تحميل شاشة: بيحمّل (هيكل رمادي) · جاهز · فشل (جملة + «أعد المحاولة») — ممنوع خطأ صامت. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data object Failed : Load<Nothing>
}

@Stable
class LoadState<T> internal constructor() {
    var value: Load<T> by mutableStateOf(Load.Loading)
        internal set
    internal var attempt by mutableIntStateOf(0)

    fun reload() {
        attempt++
    }
}

/**
 * بيحمّل [block] مع [keys] (البلد ⇒ مفتاح `deps`)، وبيحمّل تاني لوحده بعد أي تغيير في المستحقات ([DuesChanges]) **من غير ما يرجع للهيكل**
 * (اللي ظاهر بيفضل لحد ما الجديد يوصل).
 */
@Composable
internal fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): LoadState<T> {
    val state = remember(*keys) { LoadState<T>() }
    LaunchedEffect(state, state.attempt, DuesChanges.version) {
        if (state.value !is Load.Ready) state.value = Load.Loading
        state.value = try {
            Load.Ready(block())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Load.Failed
        }
    }
    return state
}

/** الهيكل الرمادي مكان الكروت (بيحمّل) — مش دوّامة في نص الشاشة. */
@Composable
internal fun LoadingBlocks(first: Int = 120, second: Int = 280) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(first.dp))
        Skeleton(Modifier.fillMaxWidth().height(second.dp))
    }
}

/** فشل التحميل: جملة صريحة + «أعد المحاولة». */
@Composable
internal fun LoadFailed(onRetry: () -> Unit) {
    EmptyState(t(TextKey.SHELL_LOAD_FAILED), action = { TonalButton(t(TextKey.SHELL_RETRY), onRetry, height = 44.dp) })
}

/** شريحة الحالة الصغيرة (11 عريض، زاوية 12) بلون [chip]. [onHero] = على البطاقة البترولية (ألوان فاتحة من نفس العيلة). */
@Composable
internal fun StatusChip(text: String, chip: Chip, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val (ink, bg) = when (chip) {
        Chip.OVERDUE -> if (onHero) Color(0xFFFFD0D2) to Color(0x33FFA8AC) else Ink.expense to Ink.expense.copy(alpha = 0.10f)
        Chip.SOON -> if (onHero) Ink.amber to Ink.amber.copy(alpha = 0.16f) else Ink.focus to Ink.alertBg
        Chip.UPCOMING -> if (onHero) Ink.selected to Color(0x29DCEBD6) else Ink.primary to Ink.selected
        Chip.CALM -> if (onHero) Ink.onHeroMuted to Color(0x1FFFFFFF) else Ink.muted to Ink.muted.copy(alpha = 0.10f)
        Chip.MUTED -> if (onHero) Ink.text to Color(0xCCFFFFFF) else Ink.muted to Ink.muted.copy(alpha = 0.12f)
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        BasicText(text, style = Type.of(11, FontWeight.Bold).copy(color = ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** شريط التقدم (8، زاوية 4) **بالعدّ** — أقساط اتدفعت من كلها (مش نسبة فلوس محسوبة في الشاشة). */
@Composable
internal fun CountBar(done: Int, total: Int, onHero: Boolean, fill: Color = if (onHero) Ink.heroProgress else Ink.primary) {
    val part = if (total <= 0) 0f else (done.coerceIn(0, total).toFloat() / total)
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.fillMaxWidth().height(8.dp).clip(shape).background(if (onHero) Color(0x2EFFFFFF) else Color(0x14193D33))) {
        if (part > 0f) Box(Modifier.fillMaxWidth(part).height(8.dp).clip(shape).background(fill))
    }
}

/** رأس شاشة داخلية بسطر تحت العنوان (`DuesDebts` · `InstallmentDetail`): رجوع 48 + العنوان + سطر رمادي 12 + أفعال على الشمال. */
@Composable
internal fun DuesHeader(title: String, subtitle: String? = null, actions: (@Composable RowScope.() -> Unit)? = null) {
    val nav = LocalNavigator.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.mirrorInLtr()) { SurfaceIconButton(Lucide.CHEVRON_RIGHT, t(TextKey.SHELL_BACK), { nav.pop() }) }
        Column(Modifier.weight(1f)) {
            BasicText(title, Modifier.semantics { heading() }, style = Type.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = Type.caption().copy(color = Ink.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actions?.invoke(this)
    }
}

/** قايمة صفوف جوه كارت طافي (حشوة 4/16 والصفوف بفواصل بينهم). */
@Composable
internal fun CardList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    FloatingCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), content = content)
}

/** سطر «اسم ← قيمة» (تفاصيل · تحليل) — [value] جاهزة من الشاشة (مبلغ بـ`AmountText` أو كلام). */
@Composable
internal fun LabelValue(label: String, modifier: Modifier = Modifier, note: String? = null, value: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(label, style = Type.of(14))
            if (note != null) BasicText(note, style = Type.caption().copy(color = Ink.muted))
        }
        value()
    }
}

/** قيمة كلام عريضة في [LabelValue] (رمادي لو «غير متاح»). */
@Composable
internal fun ValueText(text: String, muted: Boolean = false, color: Color? = null) {
    BasicText(text, style = Type.of(15, FontWeight.Bold).copy(color = color ?: if (muted) Ink.muted else Ink.text), maxLines = 1)
}

/** خانة مبلغ: أرقام من الشمال لليمين + رمز العملة. النص بيتقري بالهللة بـ`tryParseMoney` (قراية مش حساب). */
@Composable
internal fun MoneyField(value: String, onChange: (String) -> Unit, label: String?, currency: Currency, error: String? = null, big: Boolean = false) {
    TextInput(
        value, onChange, label = label, placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal, imeAction = ImeAction.Done, error = error,
        height = if (big) 60.dp else 52.dp, textSize = if (big) 24 else 16,
        trailing = { BasicText(currencySymbol(currency), Modifier.padding(end = 16.dp), style = Type.of(15).copy(color = Ink.muted)) },
    )
}

/** سؤال «هل فيها فوائد؟»: نعم · لا — الضغط على المختار يلغيه (null = لم تحدّد). */
@Composable
internal fun InterestQuestion(value: Boolean?, onPick: (Boolean?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(t(TextKey.DUES_INTEREST_Q), style = Type.bodyBold())
            BasicText(t(if (value != null) TextKey.DUES_INTEREST_SAVED else TextKey.DUES_INTEREST_UNSET), style = Type.caption().copy(color = Ink.muted))
        }
        for ((v, key) in listOf(true to TextKey.DUES_YES, false to TextKey.DUES_NO)) {
            SelectChip(t(key), value == v, { onPick(if (value == v) null else v) }, height = 44.dp)
        }
    }
}

/** لوحة تأكيد صغيرة («فك الربط؟» · «إيقاف المتابعة؟»): عنوان + جملة + زرارين. */
@Composable
internal fun ConfirmSheet(visible: Boolean, title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    Sheet(visible, onDismiss, title, closeLabel = t(TextKey.SHELL_CLOSE)) {
        BasicText(title, style = Type.section())
        BasicText(body, style = Type.of(14).copy(color = Ink.soft))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (danger) DangerButton(confirm, onConfirm, Modifier.weight(2f)) else PrimaryButton(confirm, onConfirm, Modifier.weight(2f))
            TonalButton(t(TextKey.DUES_CANCEL), onDismiss, Modifier.weight(1f), muted = true)
        }
    }
}

/** زرار فعل خطر (إيقاف · فك الربط): أبيض بحد أحمر رفيع والنص أحمر — زي النموذج. */
@Composable
internal fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(48.dp).alpha(if (enabled) 1f else 0.45f).pressScale(press, enabled).clip(shape).background(Color.White)
            .insetRing(shape, 1.dp, Ink.expense.copy(alpha = 0.25f)).tap(press, enabled, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Type.of(14, FontWeight.Bold).copy(color = Ink.expense, textAlign = TextAlign.Center)) }
}
