package app.masroufy.ui.screens.imports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.masroufy.core.LastReadWhen
import app.masroufy.core.UiKey
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CancellationException

/** نص «متى» بالأرقام 0-9 (الدالة صافية — [lastReadWhen]): «دلوقتي» · «من 5 دقيقة» · «من 3 ساعة» · «2026-10-22 14:05». */
@Composable
private fun whenText(w: LastReadWhen): String = when (w) {
    LastReadWhen.JustNow -> t(UiKey.BANK_SMS_AGO_NOW)
    is LastReadWhen.MinutesAgo -> t(UiKey.BANK_SMS_AGO_MIN, w.minutes.toString())
    is LastReadWhen.HoursAgo -> t(UiKey.BANK_SMS_AGO_HOUR, w.hours.toString())
    is LastReadWhen.At -> w.text
}

/**
 * سطر خافت: «آخر مرة قرا لوحده: …» (العامل بتاع الخلفية) — أو «لسه ما قراش لوحده» لو مفيش قيمة (قاعدة 10: مفيش وقت بيتخترع).
 * بيتحمّل لوحده ([reload] = إعادة قراية)، وما بيظهرش قبل ما القيمة تتحمّل (عشان «لسه» ما تلمعش غلط).
 */
@Composable
fun SmsLastReadLine(sms: SmsDeps?, reload: Int) {
    var state by remember(sms) { mutableStateOf<Pair<LastReadWhen?, Boolean>?>(null) }
    LaunchedEffect(sms, reload) {
        state = try {
            sms?.lastBackgroundRead() to true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val loaded = state ?: return
    val text = loaded.first?.let { t(UiKey.BANK_SMS_LAST_READ, whenText(it)) } ?: t(UiKey.BANK_SMS_NEVER_READ)
    BasicText(text, style = Type.caption().copy(color = Ink.muted))
}
