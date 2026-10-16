package app.masroufy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.core.latinizeDigits
import app.masroufy.core.normalizeDigits

/**
 * الأرقام اللي بتتكتب في أي خانة بتتحول لـ0-9 وقت الكتابة (قرار المالك OVERRIDES §79 — L5): العربي الهندي والفارسي.
 * خانة الأرقام (مبلغ · عدد · تليفون) كمان بتحوّل «٫» لنقطة و«٬» لفاصلة. **كلمة السر ما بتتلمسش** (تغيير حرف فيها يغيّرها).
 */
fun typedDigits(text: String, keyboard: KeyboardType, password: Boolean = false): String = when {
    password || keyboard == KeyboardType.Password || keyboard == KeyboardType.NumberPassword -> text
    keyboard == KeyboardType.Number || keyboard == KeyboardType.Decimal || keyboard == KeyboardType.Phone -> normalizeDigits(text)
    else -> latinizeDigits(text)
}

/**
 * الحقول (النموذج: `SignInEmail` · `BottomBar`): أبيض بحد رفيع `rgba(204,216,204,0.9)`، والخطأ حد أحمر 1.5 + جملة **جنب الخانة**
 * بأيقونة — ممنوع خطأ صامت (مستوى 1). [ltr] للإيميل والأرقام.
 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Type.of(13, FontWeight.Bold))
}

@Composable
fun FieldError(text: String, modifier: Modifier = Modifier) {
    // R041B-3: السطر بيتقري بصوت أول ما يظهر (TalkBack) — «احفظ» المعطّل بيرد بالسطر ده بس، ومن غيره المستخدم يفتكرها اتحفظت
    Row(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(Lucide.CIRCLE_ALERT, size = 16.dp, tint = Ink.expense)
        BasicText(text, style = Type.of(13).copy(color = Ink.expense))
    }
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    ltr: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    height: Dp = 48.dp,
    textSize: Int = 16,
    draft: Boolean = true,
    /**
     * وحدة الرقم (العملة أو «شهر» أو «جرام»…). قاعدة المالك (OVERRIDES §91-12): **علامة الريال على شمال الرقم بمسافة** — جوه الخانة
     * بترتيب من الشمال لليمين («⃁ 35.62») مهما كانت لغة الشاشة. أي وحدة تانية (الجنيه وغيره) على طرف الخانة زي الأول.
     */
    unit: String? = null,
) {
    val riyal = unit != null && unit == app.masroufy.core.currencySymbol(app.masroufy.core.Currency.SAR)
    val end: (@Composable () -> Unit)? = trailing ?: unit?.takeIf { !riyal }?.let { u ->
        { BasicText(u, Modifier.padding(end = 14.dp), style = Type.of(13).copy(color = Ink.muted)) }
    }
    // [draft] = `false` لخانة البحث: كلام البحث مش مسودة ⇒ اللوحة بتتقفل بالضغط برّه عادي حتى لو اتكتب فيها
    val shape = RoundedCornerShape(14.dp)
    // الكتابة بتعلّم على اللوحة اللي فيها الخانة ⇒ الضغط برّه اللوحة ما يضيّعش الكلام (`FormGuard` — HANDOVER §6.5 بند 11)
    val guard = LocalFormGuard.current.takeIf { draft }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) FieldLabel(label)
        Row(
            // الضغط جوه الخانة (وزرارها اللي على الطرف) ما بيقفلش الكيبورد — برّاها بيقفله (`OutsideTapRoot`)
            Modifier.fillMaxWidth().height(height).keepsKeyboard().clip(shape).background(Color.White)
                .insetRing(shape, if (error != null) 1.5.dp else 1.dp, if (error != null) Ink.expense else Ink.fieldEdge)
                .semantics { if (error != null) this.error(error); if (label != null) contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                val style = Type.of(textSize).copy(textAlign = if (ltr || riyal) TextAlign.Left else TextAlign.Start)
                val input = @Composable { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    // الخانة الإنجليزي (إيميل · أرقام) من الشمال لليمين بالكامل — حتى النص الباهت
                    if (value.isEmpty() && placeholder != null) BasicText(placeholder, style = style.copy(color = Color(0xFF8A9A95)), maxLines = 1)
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            if (it != value) guard?.onTyped()
                            onChange(typedDigits(it, keyboard, password))
                        },
                        enabled = enabled,
                        singleLine = true,
                        textStyle = style,
                        cursorBrush = SolidColor(Ink.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction, autoCorrectEnabled = !ltr),
                        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } }
                // الريال: العلامة ثم مسافة ثم الرقم، من الشمال لليمين
                val field = @Composable {
                    if (!riyal) input() else Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(unit.orEmpty() + " ", style = Type.of(textSize).copy(color = Ink.muted))
                        input()
                    }
                }
                if (ltr || riyal) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { field() } else field()
            }
            end?.invoke()
        }
        if (error != null) FieldError(error)
    }
}

/** خانة كلمة السر بزرار العين (48×48) — [shown] بيظهرها. */
@Composable
fun PasswordInput(
    value: String,
    onChange: (String) -> Unit,
    shown: Boolean,
    onToggle: () -> Unit,
    showLabel: String,
    hideLabel: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    TextInput(
        value, onChange, modifier, label = label, error = error, enabled = enabled, ltr = true, keyboard = KeyboardType.Password,
        imeAction = imeAction, password = !shown,
        trailing = {
            val press = rememberPress()
            Box(
                Modifier.size(48.dp).tap(press, label = if (shown) hideLabel else showLabel, onClick = onToggle)
                    .semantics { contentDescription = if (shown) hideLabel else showLabel },
                contentAlignment = Alignment.Center,
            ) { LucideIcon(if (shown) Lucide.EYE_OFF else Lucide.EYE, size = 20.dp, tint = Ink.muted) }
        },
    )
}
