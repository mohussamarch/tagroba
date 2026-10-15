package app.masroufy.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.masroufy.ui.R

/** خط العلامة بس (`res/font/riyal_sign.otf`، 1.3 ك.ب): حرف واحد U+20C1 من ملف ساما الرسمي — المصدر في `RiyalText.kt`. */
@Composable
actual fun rememberRiyalFont(): FontFamily? = remember { FontFamily(Font(R.font.riyal_sign)) }
