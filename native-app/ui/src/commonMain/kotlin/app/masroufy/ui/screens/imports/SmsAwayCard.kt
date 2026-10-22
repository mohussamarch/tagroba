package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.ui.components.CoinLoader
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «فيه رسايل فاتته» (PLAN-0.6 §13 ب): [Missed] = كارت أصفر · [Reading] = العملات جوه الزرار · [Done] = كارت أخضر بعد القراية.
 * [n] = عدد محسوب من مسح الصندوق نفسه (`SmsDeps.missedWhileAway`) — عمره ما بيتخمّن.
 */
sealed interface AwayState {
    data object Hidden : AwayState

    data class Missed(val n: Int, val more: Boolean = false) : AwayState

    data class Reading(val n: Int, val more: Boolean = false) : AwayState

    /** [recorded] = اللي اتسجل فعلًا في القراية دي (0 ⇒ اتقرت ومستنية تأكيد). */
    data class Done(val recorded: Int) : AwayState
}

/** الحالة بتمنع القراية اليدوية التلقائية في الشاشة لحد ما المالك يدوس «اقراهم دلوقتي» (غير كده الكارت كان هيختفي قبل ما يتشاف). */
val AwayState.holdsAutoRecord: Boolean get() = this is AwayState.Missed || this is AwayState.Reading

@Composable
fun SmsAwayCard(state: AwayState, onRead: () -> Unit, onKeep: () -> Unit) {
    when (state) {
        AwayState.Hidden -> Unit
        is AwayState.Done -> TintedPanel(PanelTone.MINT) {
            PanelTitle(if (state.recorded > 0) t(R06Key.AWAY_DONE, msgsCount(state.recorded)) else t(R06Key.AWAY_DONE_WAIT), PanelTone.MINT)
        }
        is AwayState.Missed -> awayPanel(state.n, state.more, reading = false, onRead, onKeep)
        is AwayState.Reading -> awayPanel(state.n, state.more, reading = true, onRead, onKeep)
    }
}

@Composable
private fun awayPanel(n: Int, more: Boolean, reading: Boolean, onRead: () -> Unit, onKeep: () -> Unit) {
    TintedPanel(PanelTone.AMBER) {
        PanelTitle(t(R06Key.AWAY_TITLE), PanelTone.AMBER)
        BasicText(t(R06Key.AWAY_BODY, if (more) t(R06Key.AWAY_MORE, msgsCount(n)) else msgsCount(n)), style = Type.of(13).copy(color = Ink.focus))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val press = rememberPress()
            Box(
                Modifier.height(44.dp).pressScale(press, !reading).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.85f))
                    .tap(press, !reading, onClick = onRead).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (reading) CoinLoader(size = 18.dp, row = true, text = t(R06Key.AWAY_READING), description = null)
                else BasicText(t(R06Key.AWAY_READ), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
            }
            QuietButton(t(R06Key.AWAY_KEEP), onKeep, onTint = true, height = 44.dp)
        }
    }
}
