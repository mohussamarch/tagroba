package app.masroufy.ui.shell.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.AskKey
import app.masroufy.core.IsoDate
import app.masroufy.core.dayMonth
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.dues.DayPicker
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** أنهي يوم بيتختار دلوقتي في الكارت. */
private enum class PeriodEnd { FIRST, LAST }

/**
 * §82-1 كارت «من إمتى لإمتى؟» جوه المحادثة («صرفت كام في رمضان؟»): زرارين «أول يوم» و«آخر يوم» وتحتهم اختيار اليوم من الشهر (نفس
 * `DayPicker` بتاع المستحقات) ⇒ «احسب» بيبعت اليومين للمحرك (`AssistantChat.pickPeriod`) والإجابة بتنزل رسالة جديدة. بعد الإجابة الكارت
 * بيعرض اليومين اللي اتختاروا بس. الفحص (الترتيب · مش بعد النهارده) في المحرك برسالته — هنا اختيار بس من غير حساب.
 */
@Composable
internal fun PeriodPickCard(messageId: String, picked: Pair<IsoDate, IsoDate>?, today: IsoDate, live: Boolean, actions: MessageActions) {
    if (picked != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(Lucide.CHECK, size = 16.dp, tint = Ink.income)
            BasicText(t(AskKey.CHAT_PERIOD_PICKED, dayMonth(picked.first), dayMonth(picked.second)), style = Type.of(13, FontWeight.Bold).copy(color = Ink.income))
        }
        return
    }
    var first by remember(messageId) { mutableStateOf<IsoDate?>(null) }
    var last by remember(messageId) { mutableStateOf<IsoDate?>(null) }
    var editing by remember(messageId) { mutableStateOf(PeriodEnd.FIRST) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(first?.let { t(AskKey.CHAT_PERIOD_FIRST) + ": " + dayMonth(it) } ?: t(AskKey.CHAT_PERIOD_FIRST), primary = editing == PeriodEnd.FIRST, enabled = live) {
                editing = PeriodEnd.FIRST
            }
            SmallAction(last?.let { t(AskKey.CHAT_PERIOD_LAST) + ": " + dayMonth(it) } ?: t(AskKey.CHAT_PERIOD_LAST), primary = editing == PeriodEnd.LAST, enabled = live) {
                editing = PeriodEnd.LAST
            }
        }
        if (live) {
            val shown = if (editing == PeriodEnd.FIRST) first else last ?: first
            DayPicker(shown, today, { day ->
                if (editing == PeriodEnd.FIRST) {
                    first = day
                    editing = PeriodEnd.LAST
                } else {
                    last = day
                }
            }, cell = 32)
        }
        val from = first
        val to = last
        SmallAction(t(AskKey.CHAT_PERIOD_GO), primary = true, enabled = live && from != null && to != null) {
            if (from != null && to != null) actions.period(messageId, from, to)
        }
    }
}
