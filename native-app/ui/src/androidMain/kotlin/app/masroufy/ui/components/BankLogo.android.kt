package app.masroufy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource

/** أندرويد: الصورة من drawable-nodpi (WebP — OVERRIDES §76 · §81) عن طريق الخريطة المتولّدة [BANK_LOGO_IMAGES]. */
@Composable
internal actual fun bankLogoPaint(logo: String): BankLogoPaint? {
    val image = BANK_LOGO_IMAGES[logo] ?: return null
    return BankLogoPaint(painterResource(image.res), image.onDark)
}
