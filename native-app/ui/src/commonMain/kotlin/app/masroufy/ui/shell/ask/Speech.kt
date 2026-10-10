package app.masroufy.ui.shell.ask

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ميكروفون المساعد = **التعرّف على الكلام بتاع الجوال العادي** (رد المالك §79.2-8: «مقبول يروح لجوجل»). أندرويد بيقدّمه من `MainActivity`
 * (`RecognizerIntent`)؛ الاختبار والآيفون لسه ⇒ [Unavailable] ⇒ «التعرّف على الكلام مش متاح على الجهاز ده».
 * [listen] بيرجّع النص اللي اتسمع (null = ما اتسمعش حاجة أو اتلغى) — والنص بيروح للمحرك زي ما يكون اتكتب.
 */
interface SpeechInput {
    val available: Boolean

    fun listen(onResult: (String?) -> Unit)

    object Unavailable : SpeechInput {
        override val available = false

        override fun listen(onResult: (String?) -> Unit) = onResult(null)
    }
}

val LocalSpeech = staticCompositionLocalOf<SpeechInput> { SpeechInput.Unavailable }
