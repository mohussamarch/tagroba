package app.masroufy.ui.screens.more

import android.content.Intent
import app.masroufy.core.outgoingText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** أندرويد: نافذة المشاركة (`ACTION_SEND` نص عادي) — `false` لو مفيش برنامج يستقبل. */
@Composable
actual fun rememberTextSharer(): ((text: String) -> Boolean)? {
    val context = LocalContext.current
    return remember(context) {
        { text: String ->
            runCatching {
                // OVERRIDES §91-8: النص الطالع برا التطبيق ⇒ «ر.س» (خطوط البرامج التانية ممكن ما يكونش فيها U+20C1)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, outgoingText(text))
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
    }
}
