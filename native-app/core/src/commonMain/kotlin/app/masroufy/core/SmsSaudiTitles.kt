package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S
import app.masroufy.core.SmsKind.CARD_PAYMENT
import app.masroufy.core.SmsKind.CASH_DEPOSIT
import app.masroufy.core.SmsKind.CASH_WITHDRAWAL
import app.masroufy.core.SmsKind.FEE
import app.masroufy.core.SmsKind.OTHER
import app.masroufy.core.SmsKind.OWN_TRANSFER
import app.masroufy.core.SmsKind.PURCHASE
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.SALARY
import app.masroufy.core.SmsKind.TRANSFER_IN
import app.masroufy.core.SmsKind.TRANSFER_OUT

/**
 * عنوان رسالة البنك السعودي (أول سطر) ⇒ اتجاهها ونوعها. المفردات = **عناوين البنك المركزي السعودي الموحّدة** (تعميم 42023876 —
 * ٦١ عنوان بالعربي والإنجليزي، فبتشتغل لأي بنك سعودي حتى اللي مالوش عينة) + عناوين البنوك اللي في البحث.
 * العنوان بيغلب كلمات باقي الرسالة: «استرداد شراء» داخل رغم «شراء»، و«Debit Transfer Salary» طالع رغم «Salary».
 * الترتيب مهم (الأدق الأول). العنوان المجهول ⇒ القاعدة القديمة (كلمات الرسالة كلها) زي ما هي.
 */
internal data class SmsTitle(val direction: Direction, val kind: SmsKind)

/**
 * الجولة التامنة: القواعد بتتقارن على العنوان **بعد [shapeKey]** (من غير تشكيل ولا تطويل · «ا» مكان «أ/إ/آ» · «ي» مكان «ى») — زي فحص الشكل
 * (`SmsKnownShapesSaudi.kt`). قبل كده القاعدة كانت على النص الخام: «بطاقة ائتمانية تاكيد سداد» (من غير همزة) ما كانتش بتتعرف فالاتجاه
 * جه من كلمة «سداد» (صرف) والشكل بيتعرف عنوان موحّد ⇒ اتسجل صرف · و«ائتمانيّة» بالشدة اتسجل **راتب داخل** من «حساب راتب».
 */
private fun unifyLetters(pattern: String): String =
    pattern.map { c -> if (c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ') 'ا' else if (c == 'ى') 'ي' else c }.joinToString("")

private class TitleRule(pattern: String, val title: SmsTitle) {
    val regex = Regex("^(?:${unifyLetters(pattern)})", setOf(RegexOption.IGNORE_CASE))
}

private fun rule(pattern: String, direction: Direction, kind: SmsKind) = TitleRule(pattern, SmsTitle(direction, kind))

/** «شراء واسترداد» مش عنوان شراء (ملف المرجع: الاتجاه مش واضح). «Purchase … was reversed» كمان (الجولة التانية من المراجعة). */
private const val NO_REFUND_AFTER = "(?![^\\n]*(?:استرداد|استرجاع|مرتجع|إرجاع|ارجاع|عكس|Revers|Refund))"
private const val DEPOSIT = "(?:إيداع|ايداع)"

/** الكلمة خلصت («دفع» مش «دفعة»، «خصم» مش «خصومات»). */
private const val NOT_LETTER = "(?![\\u0600-\\u06FF])"
private const val TO = "(?:الى|إلى)"
private val STANDING = "(?:امر|أمر)$S*مستديم"

private val RULES = listOf(
    // ── داخل: استرداد وعكس عملية (§75-6) ──
    rule("كاش$S*باك$S*عكس", OUT, OTHER),
    rule(
        "حوالة$S*عكسية|عكس$S*(?:ال)?عملية|Reverse$S*Transaction|Purchase$S*Reversal|Refunding|(?:Notification$S*:$S*)?Refund|" +
            "Credit$S*Card$S*(?:Cashback|Refund)|استرداد|استرجاع|إرجاع|ارجاع|مرتجع|تصحيح|كاش$S*باك|بطاقة$S*ائتمانية$S*(?:استرجاع$S*نقدي|استرداد$S*مبلغ)",
        IN, REFUND,
    ),
    rule("بطاقة$S*ائتمانية$S*تأكيد$S*سداد|Credit$S*Card$S*Credited", IN, CARD_PAYMENT),
    rule("بطاقة$S*ائتمانية$S*تسديد|Credit$S*Card$S*Payment", OUT, CARD_PAYMENT),
    // «سحب نقدي طارئ» اسمه الإنجليزي عند البنك المركزي «Credit transfer …» — سحب في اللغتين
    rule("سحب$S*نقدي$S*طارئ|Credit$S*transfer$S*Emergency$S*Cash$S*Withdrawal", OUT, CASH_WITHDRAWAL),
    // ── داخل ──
    rule("$DEPOSIT$S*[:：]?$S*(?:صراف|نقدي|فرع)|Deposit$S*(?:ATM|Branch)", IN, CASH_DEPOSIT),
    rule("(?:$DEPOSIT$S*)?(?:حوالة$S*)?راتب|حوالة$S*واردة$S*راتب|تم$S*إيداع$S*الراتب|salary|Credit$S*transfer$S*Salary", IN, SALARY),
    rule("حوالة$S*واردة$S*(?:بين$S*حساباتك|من$S*حسابك)|Credit$S*transfer$S*(?:Between|From$S*your)", IN, OWN_TRANSFER),
    rule(
        "$DEPOSIT$S*حوالة|حوالة$S*(?:مالية$S*)?واردة|حوالة$S*(?:داخلية|محلية|دولية)$S*واردة|تحويل$S*وارد|Credit$S*(?:Local$S*)?transfer|" +
            "(?:Incoming|Inward)$B[^\\n]*transfer|Internal$S*incoming$S*transfer|Received$S*transfer",
        IN, TRANSFER_IN,
    ),
    rule("$DEPOSIT|Deposit|Credit$S*Transaction$S*Fees|Adding$S*money|تسوية$S*نقطة$S*البيع|PoS$S*settlement", IN, OTHER),
    // ── طالع ──
    rule("سحب$S*[:：]?$S*(?:صراف|نقدي|فرع)|(?:International$S*)?ATM$S*Withdrawal|Branch$S*Withdrawal", OUT, CASH_WITHDRAWAL),
    // الجولة التامنة: شراء ومعاه كاش من المحل (عنوان موحّد) — كان بيتسجل شراء بالمبلغ كله لوحده، وجزء منه كاش رايح محفظة الكاش (§75-4)
    rule("PoS$S*Purchase$S*(?:&|and|و)$S*Cash$S*back|شراء$S*ونقد", OUT, SmsKind.PURCHASE_WITH_CASH),
    rule("(?:عملية$S*)?(?:شراء|مشتريات)$NO_REFUND_AFTER|عملية$S*(?:انترنت|إنترنت)|دفع$NOT_LETTER|خصم$S*من$S*التفويض", OUT, PURCHASE),
    rule("خصم$S*[:：]?$S*رسوم|Debit$S*(?:Transaction$S*)?fees", OUT, FEE),
    rule(
        "$STANDING$S*حوالة$S*صادرة$S*بين$S*حساباتك|حوالة$S*(?:مالية$S*)?صادرة$S*(?:بين$S*حساباتك|$TO$S*حسابك|لحسابك)|حوالة$S*بين$S*حساباتك|" +
            "تحويل$S*بين$S*حساباتي|Debit$S*Transfer$S*(?:Between|To$S*Your)|Transfer$S*between$S*my$S*accounts|Permanent$S*transfer$S*Debit$S*transfer$S*Between",
        OUT, OWN_TRANSFER,
    ),
    rule(
        "(?:عملية$S*)?حوالة$S*(?:مالية$S*)?صادرة|حوالة$S*(?:داخلية|محلية|دولية)$S*صادرة|تحويل$S*صادر|$STANDING$S*حوالة|" +
            "Debit$S*(?:Local$S*)?Transfer|Outgoing$B|Outward$B|Transfer$S*via|Permanent$S*transfer$S*Debit",
        OUT, TRANSFER_OUT,
    ),
    rule("سداد|مدفوعات|المدفوعات|$STANDING|Bill$S*Payment|Government$S*Payments|MOI$S*Payments|Permanent$S*transfer", OUT, SmsKind.BILL),
    rule("سحب$NOT_LETTER|خصم$NOT_LETTER|إصدار$S*شيك|Debit$B|Certified$S*Cheque$S*Issued", OUT, OTHER),
    rule("[^\\n]*Purchase$NO_REFUND_AFTER", OUT, PURCHASE),
)

/** أول سطر فيه كلام = العنوان. */
internal fun smsTitleLine(body: String): String = body.split('\n').map(JsText::trim).firstOrNull { it.isNotEmpty() }.orEmpty()

private val CASH_HINT = Regex("صراف|${B}ATM$B|مكان$S*السحب|نقد", setOf(RegexOption.IGNORE_CASE))

/** اتجاه ونوع العنوان المعروف، أو null. «سحب» لوحده = سحب كاش لو الرسالة فيها صرّاف أو مكان السحب. */
internal fun saudiTitle(body: String): SmsTitle? {
    val line = shapeKey(smsTitleLine(body), dropColons = false)
    val title = RULES.firstOrNull { it.regex.containsMatchIn(line) }?.title ?: return null
    return if (title.kind == OTHER && title.direction == OUT && line.startsWith("سحب") && CASH_HINT.containsMatchIn(body)) {
        SmsTitle(OUT, CASH_WITHDRAWAL)
    } else {
        title
    }
}

// ── حوالة الراجحي القديمة من غير كلمة اتجاه («حوالة داخلية» · «حوالة محلية») ──

private val UNDIRECTED_TRANSFER = Regex("^حوالة$S*(?:داخلية|محلية)$")
private val LM = setOf(RegexOption.MULTILINE)
private val TO_LINE = Regex("^[ \\t]*$TO[ \\t]*[:：][ \\t]*([^\\n]*)$", LM)
private val FROM_LINE = Regex("^[ \\t]*من[ \\t]*[:：][ \\t]*([^\\n]*)$", LM)
private val BANK_CODE_LINE = Regex("^[ \\t]*مصرف[ \\t]*[:：]", LM)
private val VIA_BANK_LINE = Regex("^[ \\t]*عبر[ \\t]*[:：][ \\t]*[^\\d\\n]", LM)
/**
 * الجولة التامنة: **اسم** = كلمة من حرفين على الأقل بعد ما نشيل قناع الرقم (x · X · * · • · #) والأرقام وبادئة SA/IBAN — «من: XX6618» ·
 * «الى: xx4417» كانت حروف القناع بتتقري اسم والاتجاه يتخمّن ويتسجل لوحده.
 */
private val MASK_TOKEN = Regex("(?<![A-Za-z])(?:(?:SA|IBAN)[ \\t]*)?[xX*•#]*[\\d*•#][\\d*•#xX ]*(?![A-Za-z])", setOf(RegexOption.IGNORE_CASE))
private val NAME_WORD = Regex("[A-Za-z\\u0600-\\u06FF]{2,}")

private fun hasName(value: String): Boolean = NAME_WORD.containsMatchIn(MASK_TOKEN.replace(value, " "))

/**
 * الاتجاه في الشكل القديم = **أنهي سطر فيه الاسم**: «الى:<اسم>» ⇒ صادرة، «من:<اسم>» ⇒ واردة (السطر التاني فيه أرقام حسابك).
 * من غير اسم: «مصرف:» (بنك المستفيد) ⇒ صادرة، «عبر:<بنك>» (البنك اللي بعت) ⇒ واردة.
 */
internal fun undirectedTransferDirection(body: String): Direction? {
    if (!UNDIRECTED_TRANSFER.matches(smsTitleLine(body))) return null
    val toName = TO_LINE.findAll(body).any { hasName(it.groupValues[1]) }
    val fromName = FROM_LINE.findAll(body).any { hasName(it.groupValues[1]) }
    return when {
        toName && !fromName -> OUT
        fromName && !toName -> IN
        BANK_CODE_LINE.containsMatchIn(body) -> OUT
        VIA_BANK_LINE.containsMatchIn(body) -> IN
        else -> null
    }
}

/** نوع الرسالة من كلامها لما العنوان مش معروف (القاعدة القديمة للاتجاه). */
internal fun saudiKindFromWords(body: String, direction: Direction): SmsKind {
    fun has(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(body)
    return when {
        direction == OUT && has("سحب") && CASH_HINT.containsMatchIn(body) -> CASH_WITHDRAWAL
        has("راتب|salary") -> SALARY
        direction == IN && has("استرداد|مرتجع|refund") -> REFUND
        has("حوالة|تحويل|transfer") -> if (direction == IN) TRANSFER_IN else TRANSFER_OUT
        direction == OUT && has("شراء|مشتريات|purchase|دفع|مدفوعات") -> PURCHASE
        else -> OTHER
    }
}
