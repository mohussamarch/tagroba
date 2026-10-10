package app.masroufy.ui.shell

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type

/** رمز نوع الميعاد ولونه (النموذج: اشتراك كهرماني · مناسبة وفرح وردي · قسط رمادي أزرق · يوم الراتب أخضر). */
enum class UpcomingIcon(val icon: Lucide, val color: Color) {
    SUBSCRIPTION(Lucide.RECEIPT, Color(0xFF956000)),
    OCCASION(Lucide.GIFT, Color(0xFFA55060)),
    EVENT(Lucide.GEM, Color(0xFFA55060)),
    INSTALLMENT(Lucide.CAR, Color(0xFF4D747C)),
    DEBT(Lucide.HAND_COINS, Color(0xFF2469BA)),
    PAYDAY(Lucide.BANKNOTE, Color(0xFF13764D)),
    OTHER(Lucide.CALENDAR, Color(0xFF637570)),
}

/** حالة الميعاد: محجوز (أخضر) · غير محجوز (أحمر) · بلا مبلغ · دخل متوقع · فات موعده. */
enum class UpcomingStatus(val text: TextRef, val color: Color) {
    HELD(UiKey.UPCOMING_HELD, Color(0xFF13764D)),
    OPEN(UiKey.UPCOMING_OPEN, Color(0xFFBE3D48)),
    NO_AMOUNT(UiKey.UPCOMING_NO_AMOUNT, Color(0xFF637570)),
    INCOMING(UiKey.UPCOMING_INCOMING, Color(0xFF13764D)),
    LATE(UiKey.UPCOMING_LATE, Color(0xFFBE3D48)),
}

/** كارت «القادم» جاهز للعرض — الحالة والمبلغ من حالة الاستخدام (`LoadCalendar` + `reservationState`)، مش محسوبين هنا. */
data class UpcomingCard(
    val key: String,
    val title: String,
    val date: IsoDate,
    val daysLeft: Int,
    val icon: UpcomingIcon,
    val status: UpcomingStatus,
    val amountMinor: Halalas?,
    val currency: Currency,
    val onClick: (() -> Unit)? = null,
)

/** «بعد كام يوم»: النهارده · بكرة · بعد يومين · بعد ٣–١٠ أيام · بعد ١١+ يومًا. */
fun daysLeftText(days: Int): String = when {
    days <= 0 -> t(UiKey.UPCOMING_TODAY)
    days == 1 -> t(UiKey.UPCOMING_TOMORROW)
    days == 2 -> t(UiKey.UPCOMING_TWO_DAYS)
    days <= 10 -> t(UiKey.UPCOMING_FEW_DAYS, sentenceNumber(days))
    else -> t(UiKey.UPCOMING_MANY_DAYS, sentenceNumber(days))
}

/**
 * شريط «القادم» في الرئيسية (`UpcomingStrip` — OVERRIDES §76 «كروت هادية»): كروت بيضا 196 بتتمرّر بالعرض (في العربي بتبدأ من اليمين)،
 * «بعد كام يوم» يمين (كهرماني لو ≤٣) والتاريخ شمال كلام عادي، الرمز والعنوان في النص، خط رفيع، والحالة والمبلغ.
 * **من غير** ختم ولا شرايط ولا خط متقطع. الشريط بيطلع لحد حافة الشاشة (برّه حواف الـ20).
 */
@Composable
fun UpcomingStrip(cards: List<UpcomingCard>, modifier: Modifier = Modifier) {
    if (cards.isEmpty()) {
        BasicText(t(UiKey.UPCOMING_EMPTY), modifier.padding(vertical = 8.dp), style = Type.of(13).copy(color = Ink.muted))
        return
    }
    LazyRow(
        modifier.fullBleed().semantics { contentDescription = t(UiKey.UPCOMING_LIST) },
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = 6.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.key }) { c -> UpcomingCardView(c) }
    }
}

@Composable
private fun UpcomingCardView(c: UpcomingCard) {
    val soon = c.daysLeft <= 3
    val aria = listOfNotNull(c.title, daysLeftText(c.daysLeft), dayMonth(c.date), t(c.status.text), c.amountMinor?.let { amountLabel(it, c.currency) }).joinToString("، ")
    FloatingCard(
        Modifier.width(196.dp).semantics(mergeDescendants = true) { contentDescription = aria },
        onClick = c.onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                BasicText(daysLeftText(c.daysLeft), style = Type.captionBold().copy(color = if (soon) Ink.focus else Ink.text))
                BasicText(dayMonth(c.date), style = Type.caption().copy(color = Ink.muted))
            }
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(c.icon.color.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    LucideIcon(c.icon.icon, size = 18.dp, tint = c.icon.color)
                }
                BasicText(c.title, style = Type.of(16, FontWeight.Bold, 1.4).copy(textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14193D33)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                BasicText(t(c.status.text), style = Type.captionBold().copy(color = c.status.color))
                if (c.amountMinor != null && c.status != UpcomingStatus.NO_AMOUNT) {
                    AmountText(c.amountMinor, c.currency, size = 12, color = c.status.color)
                }
            }
        }
    }
}

/** يطلّع العنصر لحد حافة الشاشة (−20 على الجنبين) جوه عمود ليه حواف 20. */
internal fun Modifier.fullBleed(): Modifier = layout { measurable, constraints ->
    val extra = (Space.gutter * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
