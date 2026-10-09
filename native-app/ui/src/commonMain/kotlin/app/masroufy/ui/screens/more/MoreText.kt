package app.masroufy.ui.screens.more

import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t

/**
 * عرض بس (من غير أي حساب فلوس): صيغة العدد بالعربي (واحد · اتنين · ٣–١٠ · ١١+) والتاريخ الكامل «١٢ مايو ٢٠٢٦».
 * الأرقام العربية بالفاصلة «،» جنبها مش «·» (KOTLIN-MAP §٣).
 */
data class CountWords(val one: TextKey, val two: TextKey, val few: TextKey, val many: TextKey)

val WALLET_WORDS = CountWords(TextKey.WLIST_ONE, TextKey.WLIST_TWO, TextKey.WLIST_FEW, TextKey.WLIST_MANY)
val RECORD_WORDS = CountWords(TextKey.RST_REC_ONE, TextKey.RST_REC_TWO, TextKey.RST_REC_FEW, TextKey.RST_REC_MANY)
val DAY_WORDS = CountWords(TextKey.BAK_DAY_ONE, TextKey.BAK_DAY_TWO, TextKey.BAK_DAY_FEW, TextKey.BAK_DAY_MANY)

/** «محفظة واحدة» · «محفظتان» · «٣ محافظ» · «١١ محفظة». */
fun countText(n: Int, words: CountWords): String = when {
    n == 1 -> t(words.one)
    n == 2 -> t(words.two)
    n in 3..10 -> t(words.few, sentenceNumber(n))
    else -> t(words.many, sentenceNumber(n))
}

/** «١٢ مايو ٢٠٢٦» من ISO (أو من أول ١٠ حروف في وقت ISO كامل). مش تاريخ صالح ⇒ null. */
fun fullDate(iso: String?): String? {
    val d: IsoDate = iso?.take(10) ?: return null
    if (!isValidIsoDate(d)) return null
    return t(TextKey.MORE_DATE_FULL, dayMonth(d), sentenceNumber(parseIsoDate(d).year))
}
