package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT

/**
 * **قوالب البنوك السعودية المعروفة** (`research/banks/saudi-sms-formats.json` — رقم السطر جنب كل قالب) للتسجيل التلقائي (OVERRIDES §72:
 * «المفهومة» = شكل معروف). الرسالة السعودية سطور بعنوان: القالب = **أول سطر كله** (بعد [shapeKey]) مش بدايته — «SNB: Purchase of …
 * approved» أو «سداد قسط السيارة» ما بيبقوش شكل معروف حتى لو `SmsSaudiTitles.kt` عرف اتجاههم من أول كلمة. وكمان اتجاه القالب لازم
 * يبقى نفس اتجاه القارئ (دفاع تاني). الأنماط **مكتوبة بعد [shapeKey]**: حروف صغيرة · «ا» مكان «أ/إ» · «ي» مكان «ى» · من غير «:».
 *
 * **مش هنا عمدًا** (بتستنى تأكيد المالك لو اتقرت): سطور «كلمات بس» في البحث (الراجحي #11 «مرتجع/كاش باك…» · الأهلي #47 «حوالة صادرة/
 * سداد» — البحث نفسه بيقول «الرسالة الكاملة مش منشورة») · القوالب المخترعة (#39 · #48) · «Internal transfer» (#103، مفيهوش اتجاه).
 */

private class BankTitle(val bank: String, val id: String, pattern: String, val direction: Direction?) {
    val regex = Regex(pattern)
}

private fun t(bank: String, id: String, pattern: String, direction: Direction?) = BankTitle(bank, id, pattern, direction)

private val SAUDI_BANK_TITLES: List<BankTitle> = listOf(
    // ── الراجحي ──
    t("alrajhi", "purchase", "شراء", OUT), // #0 #5
    t("alrajhi", "purchase-international", "شراء دولي", OUT), // #1
    t("alrajhi", "purchase-online-auth-capture", "خصم من التفويض عبر الانترنت", OUT), // #3
    t("alrajhi", "purchase-current-account-estore", "عملية شراء ?- ?حساب جاري ?- ?المتجر الالكتروني", OUT), // #8
    t("alrajhi", "refund-online", "استرداد شراء الانترنت", IN), // #9
    t("alrajhi", "refund", "استرداد شراء", IN), // #10
    t("alrajhi", "transfer-in-internal", "حوالة داخلية واردة(?: ب.*)?", IN), // #14 #15 (المبلغ ممكن في نفس السطر)
    t("alrajhi", "transfer-in-local", "حوالة محلية واردة", IN), // #17
    t("alrajhi", "transfer-out-internal", "حوالة داخلية صادرة", OUT), // #19 #20
    t("alrajhi", "transfer-out-local", "حوالة محلية صادرة", OUT), // #21
    t("alrajhi", "transfer-own-accounts", "حوالة بين حساباتك", OUT), // #23
    t("alrajhi", "deposit-government-support", "ايداع دعم حكومي ?- ?حساب المواطن", IN), // #26
    t("alrajhi", "salary", "راتب", IN), // #28
    t("alrajhi", "bill-payment-telecom", "مدفوعات سوا", OUT), // #30
    // ── الأهلي السعودي ──
    t("snb", "purchase-pos", "شراء نقاط بيع(?: .+)?", OUT), // #40
    t("snb", "refund", "استرجاع شراء", IN), // #42
    t("snb", "cash-correction", "تصحيح سحب نقدي", IN), // #43
    // ── ساب ──
    t("sab", "transfer-out", "حوالة صادرة مقبولة", OUT), // #51
    t("sab", "transfer-in", "ايداع حوالة واردة", IN), // #52
    t("sab", "salary", "حوالة راتب", IN), // #53
    // ── الإنماء ──
    t("alinma", "purchase-pos-local", "شراء محلي من نقاط البيع", OUT), // #55
    t("alinma", "purchase-via", "شراء عبر .+", OUT), // #56 «عبر Apple Pay» · #57 «عبر: POS» · #60 «عبر نقاط بيع»
    t("alinma", "purchase-wallet", "شراء \\([^)]+\\)", OUT), // #58 «(مدى Pay)»
    t("alinma", "purchase-online-amount", "شراء انترنت [\\d.,]+ ?(?:sar|ريال|ر\\.?س)", OUT), // #62 (المبلغ في العنوان)
    t("alinma", "transfer-in-instant", "حوالة واردة محلية سريع", IN), // #65
    t("alinma", "transfer-in-notice", "تم استلام حوالة واردة(?: .*)?", IN), // #67 (بعد «عميلنا العزيز،»)
    t("alinma", "transfer-own-investment", "حوالة صادرة لحسابك الاستثماري.*", OUT), // #70
    t("alinma", "salary", "تم ايداع الراتب", IN), // #71 (بعد «هلا …») · #72
    // ── الفرنسي ──
    t("bsf", "transfer-out", "عملية حوالة مالية صادرة مقبولة", OUT), // #77
    // ── دي 360 ──
    t("d360", "purchase-online-local", "local online purchase", OUT), // #80
    t("d360", "purchase-online-international", "international online purchase", OUT), // #79 (أجنبي ⇒ بيستنى أصلًا)
    t("d360", "transfer-in", "incoming transfer(?: .+)?", IN), // #82 «Incoming Transfer: <بنك>»
    t("d360", "transfer-in-internal", "incoming internal transfer(?: .+)?", IN), // #83
    t("d360", "transfer-out-internal", "outgoing internal transfer(?: .+)?", OUT), // #84
    t("d360", "transfer-out-local", "outgoing local transfer", OUT), // #85
    // ── بنك إس تي سي ──
    t("stc", "purchase-mada-pay", "mada pay \\(atheer\\) purchase", OUT), // #87
    t("stc", "purchase-visa", "visa purchase", OUT), // #88
    t("stc", "purchase-card", "\\*+\\d{4} purchase", OUT), // #89
    t("stc", "purchase-online-amount", "online purchase transaction amount [\\d.,]+ ?sar", OUT), // #91
    t("stc", "purchase-mada-pay-ar", "شراء mada pay \\(atheer\\)", OUT), // #92
    t("stc", "purchase-online-ar", "عملية انترنت", OUT), // #93
    t("stc", "refund", "notification refund", IN), // #95
    t("stc", "reversal-ar", "عكس عملية", IN), // #96
    t("stc", "reversal", "purchase reversal", IN), // #97
    t("stc", "transfer-own-en", "transfer between my accounts", OUT), // #98
    t("stc", "transfer-own-ar", "تحويل بين حساباتي", OUT), // #99
    t("stc", "transfer-in-sarie", "inward local transfer \\(sarie\\)", IN), // #100
    t("stc", "transfer-in-local", "credit local transfer", IN), // #101
    t("stc", "transfer-in-internal", "internal incoming transfer", IN), // #102
    t("stc", "transfer-out", "outward transfer", OUT), // #104
    t("stc", "transfer-out-local", "debit local transfer", OUT), // #105
    t("stc", "transfer-out-sarie", "outward sarie transfer", OUT), // #106
    t("stc", "transfer-out-wu", "transfer via wu", OUT), // #107
    t("stc", "wallet-topup", "adding money to account", IN), // #108
    t("stc", "bill-payment", "bill payment \\(sadad\\)", OUT), // #109
    t("stc", "government-payment", "government payments", OUT), // #110
    t("stc", "government-payment-ar", "المدفوعات الحكومية", OUT), // #111
    t("stc", "fee", "debit fees", OUT), // #112
    // ── البلاد + بنك مش معروف ──
    t("albilad", "purchase-pos", "مشتريات نقاط البيع", OUT), // #117
    t("sa-unknown", "cashback-to-card", "استرداد نقدي الي البطاقة", IN), // #121
)

/** حوالة الراجحي القديمة من غير كلمة اتجاه (#13 #16 #18 #22) — الاتجاه من **مكان الاسم** (`undirectedTransferDirection`). */
private val UNDIRECTED_TITLE = Regex("حوالة (?:داخلية|محلية)")

/** سطر تحية قبل العنوان (الإنماء #67 «عميلنا العزيز،» · #71 «هلا <اسم>») ⇒ العنوان هو السطر اللي بعده. */
private val GREETING = Regex("عميلنا العزيز|هلا .+")

/** عدد قوالب البنوك (للاختبار). */
internal val SAUDI_BANK_TITLE_COUNT: Int get() = SAUDI_BANK_TITLES.size

/**
 * الرسالة السعودية اللي القارئ قبلها على [direction]: عنوان موحّد بالحرف ⇒ [SmsShape.SamaTitle] · قالب بنك معروف ⇒ [SmsShape.KnownShape]
 * · غير كده ⇒ [SmsShape.KeywordFallback] (بتستنى). الشرط في الحالتين: اتجاه الشكل = اتجاه القارئ.
 */
internal fun saudiShape(body: String, direction: Direction): SmsShape {
    val lines = body.split('\n').map(JsText::trim).filter { it.isNotEmpty() }
    val first = shapeKey(lines.firstOrNull() ?: return SmsShape.KeywordFallback)
    if (UNDIRECTED_TITLE.matches(first)) {
        return if (undirectedTransferDirection(body) == direction) SmsShape.KnownShape("alrajhi", "transfer-undirected") else SmsShape.KeywordFallback
    }
    samaTitleAgrees(first, direction)?.let { agrees -> return if (agrees) SmsShape.SamaTitle else SmsShape.KeywordFallback }
    val title = if (GREETING.matches(first)) shapeKey(lines.getOrNull(1) ?: return SmsShape.KeywordFallback) else first
    val known = SAUDI_BANK_TITLES.firstOrNull { it.regex.matches(title) } ?: return SmsShape.KeywordFallback
    return if (known.direction == null || known.direction == direction) SmsShape.KnownShape(known.bank, known.id) else SmsShape.KeywordFallback
}
