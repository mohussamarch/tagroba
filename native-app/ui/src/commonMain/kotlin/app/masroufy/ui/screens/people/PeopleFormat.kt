package app.masroufy.ui.screens.people

import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayMonth
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t

/**
 * كتابة الكلام في شاشات المنطقة (عرض بس — من غير أي حساب فلوس): العدّ بالمفرد والمثنى والجمع، «بعد/قبل كام يوم»، اسم العملية، التاريخ بالسنة.
 * الأرقام جوه الجمل عربي شرقي (`sentenceNumber`) والفاصل جنبها «،» مش «·» (KOTLIN-MAP §٣).
 */
internal enum class Noun(val one: TextKey, val two: TextKey, val few: TextKey, val many: TextKey) {
    OPS(TextKey.PPL_OPS_ONE, TextKey.PPL_OPS_TWO, TextKey.PPL_OPS_FEW, TextKey.PPL_OPS_MANY),
    GIFTS(TextKey.PPL_GIFTS_ONE, TextKey.PPL_GIFTS_TWO, TextKey.PPL_GIFTS_FEW, TextKey.PPL_GIFTS_MANY),
    ITEMS(TextKey.PPL_ITEMS_ONE, TextKey.PPL_ITEMS_TWO, TextKey.PPL_ITEMS_FEW, TextKey.PPL_ITEMS_MANY),
    PEOPLE(TextKey.PPL_PEOPLE_ONE, TextKey.PPL_PEOPLE_TWO, TextKey.PPL_PEOPLE_FEW, TextKey.PPL_PEOPLE_MANY),
    DAYS(TextKey.PPL_DAYS_ONE, TextKey.PPL_DAYS_TWO, TextKey.PPL_DAYS_FEW, TextKey.PPL_DAYS_MANY),

    // المفعول به بعد فعل الأمر («اربط عمليتين» · «سجّل نقطتين» · «احفظ بندين») — الفصحى بتفرق بين «عمليتان» و«عمليتين»
    OPS_OBJ(TextKey.PPL_OPS_ONE, TextKey.PPL_OPS_TWO_OBJ, TextKey.PPL_OPS_FEW, TextKey.PPL_OPS_MANY),
    GIFTS_OBJ(TextKey.PPL_GIFTS_ONE, TextKey.PPL_GIFTS_TWO_OBJ, TextKey.PPL_GIFTS_FEW, TextKey.PPL_GIFTS_MANY),
    ITEMS_OBJ(TextKey.PPL_ITEMS_ONE, TextKey.PPL_ITEMS_TWO_OBJ, TextKey.PPL_ITEMS_FEW, TextKey.PPL_ITEMS_MANY),
}

/** «عملية واحدة» · «عمليتان» · «٣ عمليات» · «١١ عملية». */
internal fun countOf(n: Int, noun: Noun): String = when (n) {
    1 -> t(noun.one)
    2 -> t(noun.two)
    in 3..10 -> t(noun.few, sentenceNumber(n))
    else -> t(noun.many, sentenceNumber(n))
}

/** «اليوم» · «بعد ٣ أيام» · «قبل يومين» — [days] موجب = جاي، سالب = فات. */
internal fun relativeDays(days: Int): String = when {
    days == 0 -> t(TextKey.UPCOMING_TODAY)
    days > 0 -> t(TextKey.PPL_IN_DAYS, countOf(days, Noun.DAYS))
    else -> t(TextKey.PPL_AGO_DAYS, countOf(-days, Noun.DAYS))
}

/** «١٨ أكتوبر ٢٠٢٦». */
internal fun dayMonthYear(date: IsoDate): String = t(TextKey.PPL_DATE_WITH_YEAR, dayMonth(date), sentenceNumber(parseIsoDate(date).year))

/** أجزاء سطر بالفاصل العربي «، » (من غير الفاضي). */
internal fun joinLine(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(t(TextKey.PPL_SEP))

/** أول حرف من الاسم (دايرة الشخص). */
internal fun initialOf(name: String): String = name.trim().take(1)

/** اسم العملية زي ما جه من المصدر (التاجر · الوصف · الملاحظة). */
internal fun txnTitle(txn: Transaction): String =
    listOf(txn.rawMerchantName, txn.rawDescription, txn.note).firstOrNull { !it.isNullOrBlank() }?.trim() ?: t(TextKey.PPL_TXN_NO_NAME)
