package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT

/**
 * **عناوين البنك المركزي السعودي الموحّدة** (تعميم 42023876 بتاريخ 2020-11-29 — السطور 122…182 في `research/banks/saudi-sms-formats.json`)
 * كقايمة **بالحرف** للتسجيل التلقائي (OVERRIDES §72 — «المفهومة» = شكل معروف): أول سطر في الرسالة = عنوان من دول بالظبط (بعد
 * [shapeKey]) واتجاهه = اتجاه القارئ ⇒ [SmsShape.SamaTitle]. ده **مش** بديل `SmsSaudiTitles.kt` (اللي بيقرر الاتجاه ببداية السطر) —
 * ده فحص تاني مستقل: الشكل لازم يبقى معروف بالكامل عشان الرسالة تتسجل لوحدها.
 * عنوانين الحجز («بطاقة ائتمانية حجز مبلغ» · «الغاء حجز مبلغ») **مش هنا عمدًا**: مش عملية، ولو الحارس فوّتهم يستنوا (دفاع تاني).
 * الاتجاه null = ملتبس في المصدر نفسه (سداد البطاقة · السحب الطارئ · تسوية نقطة البيع) ⇒ أي اتجاه القارئ يقراه.
 */

/**
 * السطر للمقارنة بالقوايم: من غير تشكيل ولا تطويل · الألف بشكل واحد (أ إ آ ٱ ⇒ ا) و«ى» ⇒ «ي» · حروف صغيرة · المسافات واحدة ·
 * من غير علامات في الآخر (. ، , : ;). [dropColons] = النقطتين مسافة («سحب:صراف آلي» = «سحب صراف آلي») — للعناوين بس، مش لرسايل مصر
 * (الساعة «14:22» فيها نقطتين).
 */
internal fun shapeKey(text: String, dropColons: Boolean = true): String {
    val out = StringBuilder(text.length)
    var space = false
    for (c in text) {
        val mapped = when {
            c in 'ً'..'ْ' || c == 'ٰ' || c == 'ـ' -> continue
            c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ' -> 'ا'
            c == 'ى' -> 'ي'
            dropColons && (c == ':' || c == '：') -> ' '
            else -> c.lowercaseChar()
        }
        if (mapped.isWhitespace()) {
            space = out.isNotEmpty()
            continue
        }
        if (space) out.append(' ')
        space = false
        out.append(mapped)
    }
    while (out.isNotEmpty() && (out.last() in ".،,:;؛" || out.last().isWhitespace())) out.setLength(out.length - 1)
    return out.toString()
}

private class SamaTitleEntry(val keys: Set<String>, val direction: Direction?)

private fun sama(direction: Direction?, english: String, vararg arabic: String) =
    SamaTitleEntry((arabic.toList() + english).map { shapeKey(it) }.toSet(), direction)

private val SAMA_TITLES: List<SamaTitleEntry> = listOf(
    sama(OUT, "Bill Payment", "سداد فاتورة"),
    sama(OUT, "Bill Payment one time", "سداد فاتورة لمرة واحدة"),
    sama(OUT, "Certified Cheque Issued", "إصدار شيك مصدّق"),
    sama(IN, "Credit Card Cashback", "بطاقة ائتمانية استرجاع نقدي"),
    sama(null, "Credit Card Credited", "بطاقة ائتمانية تأكيد سداد"),
    sama(null, "Credit Card Payment", "بطاقة ائتمانية تسديد"),
    sama(IN, "Credit Card Refund", "بطاقة ائتمانية استرداد مبلغ"),
    sama(IN, "Credit Transaction Fees", "إيداع رسوم"),
    sama(IN, "Credit transfer from card", "حوالة واردة من بطاقة"),
    sama(IN, "Credit transfer Between Your Accounts", "حوالة واردة بين حساباتك"),
    sama(IN, "Credit transfer Citizen Account", "حوالة واردة حساب مواطن"),
    sama(null, "Credit transfer Emergency Cash Withdrawal", "سحب نقدي طارئ"),
    sama(IN, "Credit transfer From your Current Account", "حوالة واردة من حسابك الجاري"),
    sama(IN, "Credit transfer From Your Investment Account", "حوالة واردة من حسابك الاستثماري"),
    sama(IN, "Credit transfer Hafiz", "حوالة واردة حافز"),
    sama(IN, "Credit transfer Internal", "حوالة واردة داخلية"),
    sama(IN, "Credit transfer International", "حوالة واردة دولية"),
    sama(IN, "Credit transfer Loan", "حوالة واردة تمويل"),
    sama(IN, "Credit transfer Local", "حوالة واردة محلية"),
    sama(IN, "Credit transfer Salary", "حوالة واردة راتب"),
    sama(IN, "Credit transfer Sponsor", "حوالة واردة كفيل"),
    sama(IN, "Credit transfer Student Reward", "حوالة واردة مكافأة طلاب"),
    sama(OUT, "Debit Transaction Fees", "خصم رسوم"),
    sama(OUT, "Debit Transfer to card", "حوالة صادرة الى بطاقة"),
    sama(OUT, "Debit Transfer Between Your Account", "حوالة صادرة بين حساباتك"),
    sama(OUT, "Debit Transfer Internal", "حوالة صادرة داخلية"),
    sama(OUT, "Debit Transfer International", "حوالة صادرة دولية"),
    sama(OUT, "Debit Transfer Loan Instalment", "خصم قسط تمويل"),
    sama(OUT, "Debit Transfer Local", "حوالة صادرة محلية"),
    sama(OUT, "Debit Transfer Salary", "حوالة صادرة راتب"),
    sama(OUT, "Debit Transfer Sponsored", "حوالة صادرة مكفول"),
    sama(OUT, "Debit Transfer To Your Current Account", "حوالة صادرة الى حسابك الجاري"),
    sama(OUT, "Debit Transfer To Your Investment account", "حوالة صادرة الى حسابك الاستثمار", "حوالة صادرة الى حسابك الاستثماري"),
    sama(OUT, "Debit Certified Cheque", "خصم شيك مصدق"),
    sama(OUT, "Debit Paper Cheque", "خصم شيك ورقي"),
    sama(IN, "Deposit ATM", "إيداع صراف آلي"),
    sama(IN, "Deposit Branch", "إيداع فرع"),
    sama(IN, "Deposit Certified Cheque", "إيداع شيك مصدق"),
    sama(IN, "Deposit Paper Cheque", "إيداع شيك ورقي"),
    sama(OUT, "Foreign Currency Purchase", "شراء عملة أجنبية"),
    sama(OUT, "International ATM Withdrawal", "سحب صراف آلي دولي"),
    sama(OUT, "MOI Payments", "مدفوعات وزارة الداخلية"),
    sama(OUT, "Online Purchase", "شراء إنترنت"),
    sama(OUT, "Permanent transfer Bill Payment", "امر مستديم سداد فواتير"),
    sama(OUT, "Permanent transfer Debit transfer Bank internal", "امر مستديم حوالة صادرة داخلية"),
    sama(OUT, "Permanent transfer Debit transfer Between Your Accounts", "امر مستديم حوالة صادرة بين حساباتك"),
    sama(OUT, "Permanent transfer Debit transfer International", "امر مستديم حوالة صادرة دولية"),
    sama(OUT, "Permanent transfer Debit transfer Local", "امر مستديم حوالة صادرة محلية"),
    sama(OUT, "Permanent transfer Debit transfer Salary", "امر مستديم حوالة صادرة راتب"),
    sama(OUT, "Permanent transfer MOI Payments", "امر مستديم مدفوعات وزارة الداخلية"),
    sama(OUT, "PoS International Purchase", "شراء عبر نقاط البيع دولية"),
    sama(OUT, "PoS Purchase", "شراء عبر نقاط البيع"),
    sama(OUT, "PoS Purchase & Cashback", "شراء ونقد عبر نقاط البيع"),
    sama(null, "PoS settlement", "تسوية نقطة البيع"),
    sama(IN, "Received transfer", "حوالة واردة"),
    sama(IN, "Refunding MOI Payments", "استرجاع مدفوعات وزارة الداخلية"),
    sama(IN, "Reverse Transaction", "حوالة عكسية"),
    sama(OUT, "ATM Withdrawal", "سحب صراف آلي"),
    sama(OUT, "Branch Withdrawal", "سحب فرع"),
)

private val SAMA_BY_KEY: Map<String, SamaTitleEntry> = SAMA_TITLES.flatMap { e -> e.keys.map { it to e } }.toMap()

/** عدد العناوين اللي في القايمة (للاختبار: ٦١ في التعميم − عنوانين الحجز). */
internal val SAMA_TITLE_COUNT: Int get() = SAMA_TITLES.size

/**
 * [titleKey] (بعد [shapeKey]) عنوان موحّد؟ null = لأ · true = أيوه واتجاهه زي اتجاه القارئ ([direction]) أو ملتبس ·
 * false = عنوان موحّد بس اتجاهه **عكس** القارئ (دفاع تاني — المفروض ما يحصلش، ولو حصل الرسالة تستنى).
 */
internal fun isSamaTitle(titleKey: String): Boolean = titleKey in SAMA_BY_KEY

internal fun samaTitleAgrees(titleKey: String, direction: Direction): Boolean? {
    val entry = SAMA_BY_KEY[titleKey] ?: return null
    return entry.direction == null || entry.direction == direction
}
