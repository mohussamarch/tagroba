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

/** [colon] = القالب بيتقارن بالنقطتين («Incoming Transfer: <بنك>» — «Incoming Transfer Request» مش هو). */
private class BankTitle(val bank: String, val id: String, pattern: String, val direction: Direction?, val colon: Boolean) {
    val regex = Regex(pattern)
}

private fun t(bank: String, id: String, pattern: String, direction: Direction?, colon: Boolean = false) =
    BankTitle(bank, id, pattern, direction, colon)

/**
 * الجولة الخامسة: اللاحقة الحرة في العنوان («(?: .+)?») كانت بتقبل أي كلمة — «Incoming Transfer Request» · «حوالة داخلية واردة
 * بانتظار التأكيد» · «شراء نقاط بيع مؤجل» · «شراء عبر Apple Pay - بانتظار التأكيد» كانوا شكل معروف. دلوقتي اللاحقة محصورة في اللي
 * البحث كاتبه: مبلغ بالريال · اسم محفظة · اسم بنك (من غير كلمة حالة — [hasShapeDoubt]).
 */
private const val LOCAL_MONEY = "(?:(?:sar|sr|ريال|ر\\.? ?س\\.?) ?[\\d,.]+|[\\d,.]+ ?(?:sar|sr|ريال|ر\\.? ?س\\.?))"
private const val WALLET =
    "(?:apple|ابل|google|جوجل|samsung|سامسونج|stc|mada|مدي|huawei|هواوي|garmin|fitbit)? ?(?:pay|باي)|مدي|mada|اثير|atheer|(?:مدي|mada) (?:pay|باي)"
private const val BANK_NAME = "[a-z\\u0600-\\u06FF][a-z\\u0600-\\u06FF .&-]{1,40}"

private val SAUDI_BANK_TITLES: List<BankTitle> = listOf(
    // ── الراجحي ──
    t("alrajhi", "purchase", "شراء", OUT), // #0 #5
    t("alrajhi", "purchase-international", "شراء دولي", OUT), // #1
    t("alrajhi", "purchase-online-auth-capture", "خصم من التفويض عبر الانترنت", OUT), // #3
    t("alrajhi", "purchase-current-account-estore", "عملية شراء ?- ?حساب جاري ?- ?المتجر الالكتروني", OUT), // #8
    t("alrajhi", "refund-online", "استرداد شراء الانترنت", IN), // #9
    t("alrajhi", "refund", "استرداد شراء", IN), // #10
    t("alrajhi", "transfer-in-internal", "حوالة داخلية واردة(?: ب ?$LOCAL_MONEY)?", IN), // #14 #15 (المبلغ ممكن في نفس السطر — مبلغ بس)
    t("alrajhi", "transfer-in-local", "حوالة محلية واردة", IN), // #17
    t("alrajhi", "transfer-out-internal", "حوالة داخلية صادرة", OUT), // #19 #20
    t("alrajhi", "transfer-out-local", "حوالة محلية صادرة", OUT), // #21
    t("alrajhi", "transfer-own-accounts", "حوالة بين حساباتك", OUT), // #23
    t("alrajhi", "deposit-government-support", "ايداع دعم حكومي ?- ?حساب المواطن", IN), // #26
    t("alrajhi", "salary", "راتب", IN), // #28
    t("alrajhi", "bill-payment-telecom", "مدفوعات سوا", OUT), // #30
    // ── الأهلي السعودي ──
    t("snb", "purchase-pos", "شراء نقاط بيع(?: (?:$WALLET))?", OUT), // #40 «شراء نقاط بيع {wallet}» — اسم محفظة بس
    t("snb", "refund", "استرجاع شراء", IN), // #42
    t("snb", "cash-correction", "تصحيح سحب نقدي", IN), // #43
    // ── ساب ──
    t("sab", "transfer-out", "حوالة صادرة مقبولة", OUT), // #51
    t("sab", "transfer-in", "ايداع حوالة واردة", IN), // #52
    t("sab", "salary", "حوالة راتب", IN), // #53
    // ── الإنماء ──
    t("alinma", "purchase-pos-local", "شراء محلي من نقاط البيع", OUT), // #55
    t("alinma", "purchase-via", "شراء عبر (?:$WALLET|pos|نقاط بيع)", OUT), // #56 «عبر Apple Pay» · #57 «عبر: POS» · #60 «عبر نقاط بيع»
    t("alinma", "purchase-wallet", "شراء \\((?:$WALLET)\\)", OUT), // #58 «(مدى Pay)»
    t("alinma", "purchase-online-amount", "شراء انترنت [\\d.,]+ ?(?:sar|ريال|ر\\.?س)", OUT), // #62 (المبلغ في العنوان)
    t("alinma", "transfer-in-instant", "حوالة واردة محلية سريع", IN), // #65
    // #67 (بعد «عميلنا العزيز،») — الجملة كلها: «… من حساب جاري مبلغ SAR {amount} إلى **{acct} {date} {time}»
    t("alinma", "transfer-in-notice", "تم استلام حوالة واردة من حساب جاري مبلغ $LOCAL_MONEY الي [*•x#\\d]+(?: [\\d/:.\\-]+)*", IN),
    t("alinma", "transfer-own-investment", "حوالة صادرة لحسابك الاستثماري(?: في بنك الانماء)?", OUT), // #70
    t("alinma", "salary", "تم ايداع الراتب", IN), // #71 (بعد «هلا …») · #72
    // ── الفرنسي ──
    t("bsf", "transfer-out", "عملية حوالة مالية صادرة مقبولة", OUT), // #77
    // ── دي 360 ──
    t("d360", "purchase-online-local", "local online purchase", OUT), // #80
    t("d360", "purchase-online-international", "international online purchase", OUT), // #79 (أجنبي ⇒ بيستنى أصلًا)
    t("d360", "transfer-in", "incoming transfer ?: ?$BANK_NAME", IN, colon = true), // #82 «Incoming Transfer: <بنك>»
    t("d360", "transfer-in-internal", "incoming internal transfer ?: ?$BANK_NAME", IN, colon = true), // #83
    t("d360", "transfer-out-internal", "outgoing internal transfer ?: ?$BANK_NAME", OUT, colon = true), // #84
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

/**
 * سطر تحية قبل العنوان (الإنماء #67 «عميلنا العزيز،» · #71 «هلا <اسم>») ⇒ العنوان هو السطر اللي بعده. الجولة السادسة: «هلا» + اسم
 * (4 كلمات بالكتير، من غير « - » ولا أقواس ولا كلمة حالة) — «هلا سامر - الحوالة موقوفة» كانت بتستخبى كتحية.
 */
private val GREETING = Regex("عميلنا العزيز|هلا(?: [^ ,،()\\-–—]+){1,4}")

/** عدد قوالب البنوك (للاختبار). */
internal val SAUDI_BANK_TITLE_COUNT: Int get() = SAUDI_BANK_TITLES.size

/** «Incoming Transfer: Returned» — اسم البنك بعد النقطتين لازم ما يبقاش كلمة حالة. */
private val TITLE_STATUS = Regex("(?<![a-z])(?:returned?|refused|request)(?![a-z])")

/**
 * عملية **برّه البلد** (شراء أو سحب — مش حوالة دولية، دي بالريال عادي): «شراء دولي» · «PoS International Purchase» · «سحب صراف آلي
 * دولي». الجولة الخامسة (§75-12 — قرار المالك: الشراء الأجنبي ما بيتسجلش لوحده): حتى لو المبلغ المكتوب بالريال بس، **بتستنى**.
 */
private val INTERNATIONAL = Regex("(?<![\\u0600-\\u06FF])(?:دولي|دولية)(?![\\u0600-\\u06FF])|(?<![a-z])international(?![a-z])")
private val TRANSFER_TITLE = Regex("حوالة|تحويل|transfer|remittance")

/** العنوان المعروف (أي اتجاه): [bank]/[id] للقالب (null = عنوان موحّد) · [from] = أول سطر بعد العنوان · [key] = العنوان بعد [shapeKey]. */
private class TitleHit(val bank: String?, val id: String?, val direction: Direction?, val from: Int, val key: String)

private fun titleHit(lines: List<String>): TitleHit? {
    val first = shapeKey(lines.firstOrNull() ?: return null)
    if (UNDIRECTED_TITLE.matches(first)) return TitleHit("alrajhi", "transfer-undirected", null, 1, first)
    if (isSamaTitle(first)) return TitleHit(null, null, null, 1, first)
    val index = if (GREETING.matches(first) && !hasShapeDoubt(first) && !GREETING_NOT_A_NAME.containsMatchIn(first.removePrefix("هلا"))) 1 else 0
    val raw = lines.getOrNull(index) ?: return null
    val title = shapeKey(raw)
    val withColons = shapeKey(raw, dropColons = false)
    val known = SAUDI_BANK_TITLES.firstOrNull { it.regex.matches(if (it.colon) withColons else title) } ?: return null
    // الجولة السادسة: اسم البنك بعد النقطتين قيمة حرة محصورة («Incoming Transfer: Riyad Bank - recalled» مش اسم بنك)
    if (known.colon && (!freeValueOk(withColons.substringAfter(':')) || TITLE_STATUS.containsMatchIn(withColons))) return null
    return TitleHit(known.bank, known.id, known.direction, index + 1, title)
}

/** أول سطر (بعد التحية) عنوان سعودي معروف — موحّد أو قالب بنك، أي اتجاه. فلتر الجهاز بيستعملها (`SmsVocabulary`). */
internal fun hasSaudiKnownTitle(body: String): Boolean = titleHit(body.split('\n').map(JsText::trim).filter { it.isNotEmpty() }) != null

/**
 * الرسالة السعودية اللي القارئ قبلها على [direction]: عنوان موحّد بالحرف ⇒ [SmsShape.SamaTitle] · قالب بنك معروف ⇒ [SmsShape.KnownShape]
 * · غير كده ⇒ [SmsShape.KeywordFallback] (بتستنى). الشرط: اتجاه الشكل = اتجاه القارئ، **وكل سطر بعد العنوان خانة معروفة**
 * (`SmsSaudiLines.kt` — الجولة الخامسة)، والعملية مش شراء أو سحب برّه البلد.
 */
internal fun saudiShape(body: String, direction: Direction): SmsShape {
    val lines = body.split('\n').map(JsText::trim).filter { it.isNotEmpty() }
    val hit = titleHit(lines) ?: return SmsShape.KeywordFallback
    val agrees = when {
        hit.id == "transfer-undirected" -> undirectedTransferDirection(body) == direction
        hit.bank == null -> samaTitleAgrees(hit.key, direction) == true
        else -> hit.direction == null || hit.direction == direction
    }
    if (!agrees) return SmsShape.KeywordFallback
    val body = lines.drop(hit.from)
    // الجولة السادسة: «Exchange rate: 4.6875» · «Country: GB» · «الدولة: الإمارات» تحت عنوان محلي = شراء برّه البلد ⇒ يستنى زي «دولي»
    if ((INTERNATIONAL.containsMatchIn(hit.key) || abroadLines(body)) && !TRANSFER_TITLE.containsMatchIn(hit.key)) return SmsShape.KeywordFallback
    if (!allLinesKnown(body, hit.bank?.let { "$it/${hit.id}" })) return SmsShape.KeywordFallback
    return if (hit.bank == null) SmsShape.SamaTitle else SmsShape.KnownShape(hit.bank, hit.id!!)
}
