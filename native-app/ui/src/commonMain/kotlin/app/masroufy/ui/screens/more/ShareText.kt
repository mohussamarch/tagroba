package app.masroufy.ui.screens.more

import androidx.compose.runtime.Composable

/**
 * «ابعته» في «سجل المشاكل» (OVERRIDES §90): **نافذة المشاركة بتاعة الجوال نفسها** (أندرويد: `ACTION_SEND` — المستخدم بيختار واتساب أو
 * الإيميل أو غيره، ومفيش حاجة بتتبعت لوحدها). null ⇒ الجهاز مالوش (اختبار JVM · الآيفون لسه) ⇒ الشاشة بتقول «غير متاح».
 */
@Composable
expect fun rememberTextSharer(): ((text: String) -> Boolean)?
