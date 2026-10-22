package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.masroufy.core.LastReadWhen
import app.masroufy.core.R06Key
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** آخر مرة قرا فيها العامل بتاع الخلفية، محمّلة: [last] = null ⇒ لسه ما قراش (الشاشة بتقول كده — قاعدة 10). قبل التحميل مفيش حالة أصلًا. */
data class LastReadState(val last: LastReadWhen?)

/** نص «متى» بالأرقام 0-9 (الدالة صافية — `lastReadWhen`): «دلوقتي» · «من 5 دقيقة» · «من 3 ساعة» · «2026-10-22 14:05». */
@Composable
private fun whenText(w: LastReadWhen): String = when (w) {
    LastReadWhen.JustNow -> t(R06Key.BANK_SMS_AGO_NOW)
    is LastReadWhen.MinutesAgo -> t(R06Key.BANK_SMS_AGO_MIN, w.minutes.toString())
    is LastReadWhen.HoursAgo -> t(R06Key.BANK_SMS_AGO_HOUR, w.hours.toString())
    is LastReadWhen.At -> w.text
}

/**
 * سطر خافت بساعة صغيرة: «آخر مرة قرا لوحده: …» (العامل بتاع الخلفية) — أو «لسه ما قراش لوحده» لو مفيش قيمة. القراية اليدوية ما بتغيّرش
 * الوقت ده (هو وقت الخلفية بس). الإذن اتسحب ⇒ بيفضل بيعرض آخر وقت.
 */
@Composable
fun SmsLastReadLine(state: LastReadState) {
    val text = state.last?.let { t(R06Key.BANK_SMS_LAST_READ, whenText(it)) } ?: t(R06Key.BANK_SMS_NEVER_READ)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(Lucide.CLOCK, size = 14.dp, tint = Ink.muted)
        BasicText(text, style = Type.caption().copy(color = Ink.muted))
    }
}
