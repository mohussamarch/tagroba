package app.masroufy.android

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.ui.shell.ask.SpeechInput

/**
 * ميكروفون المساعد = **نافذة التعرّف على الكلام بتاعة الجوال العادية** (رد المالك §79.2-8: «مقبول يروح لجوجل»): `RecognizerIntent` من غير
 * إذن ميكروفون للتطبيق (تطبيق التعرّف هو اللي بيسمع). اللغة: عربي سعودي أو مصري حسب البلد الشغالة، أو إنجليزي. النص بيروح للمحرك زي ما يكون اتكتب.
 * لازم يتعمل في `onCreate` (قبل ما الشاشة تبدأ) عشان `registerForActivityResult`.
 */
class AndroidSpeech(private val activity: ComponentActivity) : SpeechInput {
    private var pending: ((String?) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { r.resultCode == Activity.RESULT_OK }
        pending?.invoke(heard)
        pending = null
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    private fun languageTag(): String = when {
        Texts.language == Language.EN -> "en-US"
        Texts.arabicVariant == ArabicVariant.EGYPTIAN -> "ar-EG"
        else -> "ar-SA"
    }

    override val available: Boolean
        get() = runCatching { intent().resolveActivity(activity.packageManager) != null }.getOrDefault(false)

    override fun listen(onResult: (String?) -> Unit) {
        pending = onResult
        runCatching { launcher.launch(intent()) }.onFailure {
            pending = null
            onResult(null)
        }
    }
}
