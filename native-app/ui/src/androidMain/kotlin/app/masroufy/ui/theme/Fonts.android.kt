package app.masroufy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import app.masroufy.ui.R

/**
 * Noto Sans Arabic من خدمة خطوط جوجل على الجهاز (Google Play Services) — بيتنزل مرة ويتحفظ عند النظام، **من غير ملف خط في المستودع**.
 * لو الخدمة مش موجودة أو التنزيل فشل ⇒ Compose بيكمّل بخط الجهاز العربي (نفس دور `Tahoma` في النموذج). الشهادات في `res/values/font_certs.xml`.
 */
@Composable
actual fun rememberMasroufyFont(): FontFamily = remember {
    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )
    val noto = GoogleFont("Noto Sans Arabic")
    FontFamily(
        Font(googleFont = noto, fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = noto, fontProvider = provider, weight = FontWeight.Medium),
        Font(googleFont = noto, fontProvider = provider, weight = FontWeight.Bold),
    )
}
