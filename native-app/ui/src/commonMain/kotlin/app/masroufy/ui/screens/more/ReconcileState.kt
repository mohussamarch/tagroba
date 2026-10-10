package app.masroufy.ui.screens.more

import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.ReconcileOutcome

/**
 * نتيجة «مطابقة الرصيد» (`ReconcileBalance.run` ⇒ `ReconcileOutcome`) كسطور — **دالة نقية** (JVM). الأرقام كلها من النتيجة نفسها
 * (المحسوب · المعلن · الفرق · العدّ) — مفيش طرح هنا. «أول فرق» بصيغة العدد بدل «{1} حركة» (spec/06: توضيح الغموض من غير ادعاء يقين).
 */
enum class RecLineKind { OK, GAP_STATUS, GAP_DETAIL, GAP_AMOUNTS, NOTE }

data class RecLine(val kind: RecLineKind, val text: String)

val MOVE_WORDS = CountWords(TextKey.WDET_MOVE_ONE, TextKey.WDET_MOVE_TWO, TextKey.WDET_MOVE_FEW, TextKey.WDET_MOVE_MANY)
val OPERATION_WORDS = CountWords(TextKey.WDET_OP_ONE, TextKey.WDET_OP_TWO, TextKey.WDET_OP_FEW, TextKey.WDET_OP_MANY)

fun reconcileLines(o: ReconcileOutcome): List<RecLine> {
    val r = o.result
    // مفيش حركة برصيد معلن ⇒ مفيش حكم (الكارت بيقول «استورد كشفًا»)
    if (r.checkedCount == 0) return emptyList()
    val currency = o.wallet.currency
    val out = mutableListOf<RecLine>()
    val first = r.mismatches.firstOrNull()
    if (first == null) {
        val until = fullDate(r.closingAt)
        out += RecLine(RecLineKind.OK, if (until != null) t(TextKey.WDET_REC_OK_UNTIL, until) else t(TextKey.WDET_REC_OK))
    } else {
        out += RecLine(RecLineKind.GAP_STATUS, t(TextKey.WDET_REC_GAP))
        val day = fullDate(first.date) ?: first.date
        out += RecLine(
            RecLineKind.GAP_DETAIL,
            if (first.sameDayCount > 1) t(TextKey.WDET_GAP_MANY, day, countText(first.sameDayCount, MOVE_WORDS)) else t(TextKey.WDET_GAP_ONE, day),
        )
        out += RecLine(
            RecLineKind.GAP_AMOUNTS,
            t(
                TextKey.WDET_GAP_AMOUNTS,
                amountLabel(first.computedMinor, currency, showCurrency = false),
                amountLabel(first.statedMinor, currency, showCurrency = false),
                amountLabel(first.differenceMinor, currency, showCurrency = false),
                currencySymbol(currency),
            ),
        )
    }
    val counts = t(TextKey.WDET_REC_COUNTS, countText(r.movementCount, MOVE_WORDS), sentenceNumber(r.checkedCount)) +
        if (o.withoutStatedBalance > 0) t(TextKey.WDET_REC_COUNTS_NO_BAL, countText(o.withoutStatedBalance, MOVE_WORDS)) else ""
    out += RecLine(RecLineKind.NOTE, counts)
    if (o.unassignedCount > 0) out += RecLine(RecLineKind.NOTE, t(TextKey.WDET_REC_UNASSIGNED, countText(o.unassignedCount, OPERATION_WORDS)))
    return out
}
