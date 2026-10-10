package app.masroufy.ui.screens.more

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t

/**
 * عرض بس (من غير أي حساب فلوس): صيغة العدد بالعربي (واحد · اتنين · 3–10 · 11+) والتاريخ الكامل «12 مايو 2026».
 * الأرقام العربية بالفاصلة «،» جنبها مش «·» (KOTLIN-MAP §3).
 */
data class CountWords(val one: TextRef, val two: TextRef, val few: TextRef, val many: TextRef)

val WALLET_WORDS = CountWords(UiKey.WLIST_ONE, UiKey.WLIST_TWO, UiKey.WLIST_FEW, UiKey.WLIST_MANY)
val RECORD_WORDS = CountWords(UiKey.RST_REC_ONE, UiKey.RST_REC_TWO, UiKey.RST_REC_FEW, UiKey.RST_REC_MANY)
val DAY_WORDS = CountWords(UiKey.BAK_DAY_ONE, UiKey.BAK_DAY_TWO, UiKey.BAK_DAY_FEW, UiKey.BAK_DAY_MANY)

/** «محفظة واحدة» · «محفظتان» · «3 محافظ» · «11 محفظة». */
fun countText(n: Int, words: CountWords): String = when {
    n == 1 -> t(words.one)
    n == 2 -> t(words.two)
    n in 3..10 -> t(words.few, sentenceNumber(n))
    else -> t(words.many, sentenceNumber(n))
}

/** «12 مايو 2026» من ISO (أو من أول 10 حروف في وقت ISO كامل). مش تاريخ صالح ⇒ null. */
fun fullDate(iso: String?): String? {
    val d: IsoDate = iso?.take(10) ?: return null
    if (!isValidIsoDate(d)) return null
    return t(UiKey.MORE_DATE_FULL, dayMonth(d), sentenceNumber(parseIsoDate(d).year))
}
