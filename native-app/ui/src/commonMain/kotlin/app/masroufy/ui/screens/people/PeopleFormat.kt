package app.masroufy.ui.screens.people

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
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
internal enum class Noun(val one: TextRef, val two: TextRef, val few: TextRef, val many: TextRef) {
    OPS(UiKey.PPL_OPS_ONE, UiKey.PPL_OPS_TWO, UiKey.PPL_OPS_FEW, UiKey.PPL_OPS_MANY),
    GIFTS(UiKey.PPL_GIFTS_ONE, UiKey.PPL_GIFTS_TWO, UiKey.PPL_GIFTS_FEW, UiKey.PPL_GIFTS_MANY),
    ITEMS(UiKey.PPL_ITEMS_ONE, UiKey.PPL_ITEMS_TWO, UiKey.PPL_ITEMS_FEW, UiKey.PPL_ITEMS_MANY),
    PEOPLE(UiKey.PPL_PEOPLE_ONE, UiKey.PPL_PEOPLE_TWO, UiKey.PPL_PEOPLE_FEW, UiKey.PPL_PEOPLE_MANY),
    DAYS(UiKey.PPL_DAYS_ONE, UiKey.PPL_DAYS_TWO, UiKey.PPL_DAYS_FEW, UiKey.PPL_DAYS_MANY),

    // المفعول به بعد فعل الأمر («اربط عمليتين» · «سجّل نقطتين» · «احفظ بندين») — الفصحى بتفرق بين «عمليتان» و«عمليتين»
    OPS_OBJ(UiKey.PPL_OPS_ONE, UiKey.PPL_OPS_TWO_OBJ, UiKey.PPL_OPS_FEW, UiKey.PPL_OPS_MANY),
    GIFTS_OBJ(UiKey.PPL_GIFTS_ONE, UiKey.PPL_GIFTS_TWO_OBJ, UiKey.PPL_GIFTS_FEW, UiKey.PPL_GIFTS_MANY),
    ITEMS_OBJ(UiKey.PPL_ITEMS_ONE, UiKey.PPL_ITEMS_TWO_OBJ, UiKey.PPL_ITEMS_FEW, UiKey.PPL_ITEMS_MANY),
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
    days == 0 -> t(UiKey.UPCOMING_TODAY)
    days > 0 -> t(UiKey.PPL_IN_DAYS, countOf(days, Noun.DAYS))
    else -> t(UiKey.PPL_AGO_DAYS, countOf(-days, Noun.DAYS))
}

/** «١٨ أكتوبر ٢٠٢٦». */
internal fun dayMonthYear(date: IsoDate): String = t(UiKey.PPL_DATE_WITH_YEAR, dayMonth(date), sentenceNumber(parseIsoDate(date).year))

/** أجزاء سطر بالفاصل العربي «، » (من غير الفاضي). */
internal fun joinLine(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(t(UiKey.PPL_SEP))

/** أول حرف من الاسم (دايرة الشخص). */
internal fun initialOf(name: String): String = name.trim().take(1)

/** اسم العملية زي ما جه من المصدر (التاجر · الوصف · الملاحظة). */
internal fun txnTitle(txn: Transaction): String =
    listOf(txn.rawMerchantName, txn.rawDescription, txn.note).firstOrNull { !it.isNullOrBlank() }?.trim() ?: t(UiKey.PPL_TXN_NO_NAME)
