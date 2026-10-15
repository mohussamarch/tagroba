package app.masroufy.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * طلب فتح القايمة السريعة لعنصر واحد: مكانه ([anchor]) · اسمه لقارئ الشاشة ([title]) · البنود · شكله وهو «مرفوع» ([lifted]).
 * [liftedPadding] = الهامش جوه الصورة المرفوعة (خانة يوم في التقويم صغيرة ⇒ أقل).
 */
class QuickMenuRequest(
    val anchor: Anchor,
    val title: String,
    val items: List<MenuItem>,
    val liftedPadding: Dp = 12.dp,
    val lifted: @Composable () -> Unit,
)

/**
 * القايمة السريعة بالضغطة الطويلة — **نفس قايمة «العمليات»** (`LiquidMenu`: ستارة + العنصر مرفوع + زجاج سائل) لأي عنصر ظاهر
 * (قايمة المالك §6.5-13). الشاشة بتعمل واحدة ([rememberQuickMenu]) وترسمها مرة ([QuickMenuHost])، وكل عنصر بيتلف بـ[QuickTarget].
 * الضغطة العادية بتفضل زي ما هي؛ الطويلة (ومعاها الهزة — `combinedClickable` بيعملها لوحده زي «العمليات») بتفتح القايمة.
 * الضغط برّه = قفل (الستارة) · الرجوع = قفل · اختيار بند = قفل ثم الفعل.
 */
@Stable
class QuickMenu {
    /** المفتوح دلوقتي (null = مقفولة). */
    var open: QuickMenuRequest? by mutableStateOf(null)
        private set

    /** آخر واحد اتفتح — بيفضل مرسوم وهي بتقفل بحركتها (مخزن عادي — مش حالة، فمفيش رسم زيادة). */
    internal var last: QuickMenuRequest? = null
        private set

    val isOpen: Boolean get() = open != null

    fun show(request: QuickMenuRequest) {
        last = request
        open = request
    }

    fun dismiss() {
        open = null
    }

    /**
     * الضغطة العادية على العنصر **مش** بتشتغل والقايمة مفتوحة (الصباع اللي فتحها بالضغط الطويل ممكن يترفع على العنصر نفسه):
     * القايمة بتقفل بس، والضغطة اللي بعدها بترجع تشتغل عادي.
     */
    fun guardTap(onTap: () -> Unit): () -> Unit = {
        if (open != null) dismiss() else onTap()
    }
}

@Composable
fun rememberQuickMenu(): QuickMenu = remember { QuickMenu() }

/** القايمة نفسها — مرة واحدة في الشاشة (برّه القايمة الطويلة). المحتوى بيتنقل لطبقة اللوحات لوحده (`Overlay`). */
@Composable
fun QuickMenuHost(menu: QuickMenu) {
    val shown = menu.open ?: menu.last
    LiquidMenu(
        visible = menu.open != null,
        onDismiss = menu::dismiss,
        anchor = shown?.anchor ?: Anchor(),
        title = shown?.title.orEmpty(),
        items = shown?.items.orEmpty(),
        closeLabel = t(UiKey.SHELL_CLOSE),
        liftedPadding = shown?.liftedPadding ?: 12.dp,
    ) { shown?.lifted?.invoke() }
}

/**
 * عنصر بقايمة سريعة: بيسجّل مكانه ويدي المحتوى [content] دالة الضغط الطويل — المحتوى بيحطها في نفس الضغطة بتاعته
 * (`Modifier.tap(onLongClick = …)` / `FloatingCard(onLongClick = …)` / `ListRow(onLongClick = …)`) عشان الضغطة العادية ما تتلخبطش.
 * [items] بتتبني وقت الضغط بس (مش مع كل رسم). القايمة الفاضية ⇒ الضغط الطويل ما بيعملش حاجة.
 * [lifted] null ⇒ شكل افتراضي: الاسم و[subtitle].
 */
@Composable
fun QuickTarget(
    menu: QuickMenu,
    title: String,
    items: () -> List<MenuItem>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    liftedPadding: Dp = 12.dp,
    lifted: (@Composable () -> Unit)? = null,
    content: @Composable (onLongPress: () -> Unit) -> Unit,
) {
    val anchor = remember { Anchor() }
    Box(modifier.onGloballyPositioned { anchor.update(it) }) {
        content {
            val list = items()
            if (list.isNotEmpty()) menu.show(QuickMenuRequest(anchor, title, list, liftedPadding, lifted ?: { QuickLifted(title, subtitle) }))
        }
    }
}

/** الشكل المرفوع الافتراضي: الاسم عريض + سطر تاني باهت (نفس سطور صف العملية المرفوع). */
@Composable
fun QuickLifted(title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
