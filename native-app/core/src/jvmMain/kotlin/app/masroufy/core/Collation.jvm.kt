package app.masroufy.core

import com.ibm.icu.text.Collator
import com.ibm.icu.util.ULocale

// ICU نفسه اللي في التطبيق الحالي (نفس الإصدار 78.3) — مقارنة java.text.Collator كانت بتختلف في ترتيب آ/أ/إ
private val arabic: Collator = Collator.getInstance(ULocale("ar"))

internal actual fun compareArabic(a: String, b: String): Int = arabic.compare(a, b)
