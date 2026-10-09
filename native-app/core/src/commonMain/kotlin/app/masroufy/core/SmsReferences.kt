package app.masroufy.core

/**
 * رقم البنك المرجعي في رسالة البنك (§77-D — الشريحة S3): «Ref» · «Ref#» · «Ref No.» · «Reference» · «الرقم المرجعي» · «رقم مرجعي» ·
 * «رقم المرجع» · «مرجع رقم» · «مرجع»/«المرجع» · «رقم العملية». القيمة = أول كلمة بعد اللابل فيها **رقم** و4 حروف/أرقام على الأقل
 * («Ref. Code» مش مرجع). بترجع **بعد الحجب** (`redactSms` — الأرقام الطويلة «••••» + آخر 4) لأن الوصف المتخزن محجوب أصلًا.
 *
 * **المطابقة بآخر 4 بس** ([referenceTail]): وصف الرسالة المتخزن فيه «••••7781» مكان «553317781»، وتخزين فايربيز بيقص أي 5 أرقام في
 * مرجع الكشف («****7781») — فآخر 4 حروف/أرقام هي الحاجة الوحيدة اللي بتفضل زي ما هي في كل مكان.
 *
 * مراجعة الشريحة: **التاريخ مش مرجع** («المرجع: 05/10/2026» كان ذيله السنة «2026» ويطابق أي عملية بنفس المبلغ) · لو الرسالة فيها مرجع
 * **العملية الأصلية** («مرجع العملية الأصلية …» · «Original Ref …») هو اللي بيتاخد (مش رقم الرجوع نفسه) · اللابل والقيمة في نفس السطر.
 */
private const val VALUE = "[ \\t]*[:：#.]?[ \\t]*#?[ \\t]*([A-Za-z0-9•*][A-Za-z0-9•*\\-/]*)"

private val REFERENCE = Regex(
    "(?:(?<![A-Za-z])ref(?:erence)?(?![A-Za-z])\\.?(?:[ \\t]*(?:no|num|number)(?![A-Za-z])\\.?)?" +
        "|الرقم[ \\t]+المرجعي|رقم[ \\t]+مرجعي|رقم[ \\t]+المرجع|مرجع[ \\t]+رقم|رقم[ \\t]+العملية|(?<![\\u0600-\\u06FF])(?:ال)?مرجع(?![\\u0600-\\u06FF]))" +
        VALUE,
    RegexOption.IGNORE_CASE,
)

/** مرجع العملية **الأصلية** («مرجع العملية الاصلية» · «الرقم المرجعي للعملية الأصلية» · «المرجع الأصلي» · «Original (Txn) Ref No.»). */
private val ORIGINAL_REFERENCE = Regex(
    "(?:(?<![\\u0600-\\u06FF])(?:(?:ال)?رقم[ \\t]+(?:ال)?مرجعي?|(?:ال)?مرجع)(?:[ \\t]+(?:لل|ل|ال)?(?:عملية|حوالة|تحويل|معاملة))?" +
        "[ \\t]+(?:ال)?(?:أصلية|اصلية|أصلي|اصلي)(?![\\u0600-\\u06FF])" +
        "|(?<![A-Za-z])original[ \\t]+(?:(?:transaction|txn|transfer)[ \\t]+)?ref(?:erence)?(?![A-Za-z])\\.?(?:[ \\t]*(?:no|num|number)(?![A-Za-z])\\.?)?)" +
        VALUE,
    RegexOption.IGNORE_CASE,
)

/** شكل تاريخ («05/10/2026» · «2026-10-05» · «05/10») — مش رقم مرجع. */
private val DATE_SHAPED = Regex("\\d{1,4}[/\\-]\\d{1,2}(?:[/\\-]\\d{1,4})?")

private fun alnum(text: String): String = text.filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }

private fun referenceIn(regex: Regex, text: String): String? {
    for (m in regex.findAll(text)) {
        val token = m.groupValues[1].trimEnd('-', '/', '.')
        val plain = alnum(token)
        if (plain.length >= 4 && plain.any { it in '0'..'9' } && !DATE_SHAPED.matches(token)) return redactSms(token)
    }
    return null
}

/** الرقم المرجعي زي ما اتقري (محجوب)، أو null — مرجع العملية الأصلية الأول لو مكتوب. */
fun smsReferenceOf(body: String): String? {
    val text = latinizeDigits(body)
    return referenceIn(ORIGINAL_REFERENCE, text) ?: referenceIn(REFERENCE, text)
}

/** أقل طول لرقم مرجعي يلغي عملية **لوحده** (§77-D أمان): أقصر من كده ممكن يتكرر صدفة ⇒ يسأل. */
const val STRONG_REFERENCE_LENGTH: Int = 6

/**
 * المرجع (زي ما اتخزن — محجوب) طويل كفاية يلغي عملية لوحده: [STRONG_REFERENCE_LENGTH] حروف/أرقام على الأقل، أو فيه أرقام اتحجبت
 * («••••7781» = 5 أرقام ورا بعض أو أكتر — الحجب ما بيسيبش غير آخر 4).
 */
fun isStrongReference(reference: String?): Boolean {
    val text = reference ?: return false
    return '•' in text || alnum(text).length >= STRONG_REFERENCE_LENGTH
}

/** آخر 4 حروف/أرقام من المرجع (حروف كبيرة) — أقل من 4 ⇒ null (أضعف من إنه يربط عمليتين). */
fun referenceTail(reference: String?): String? {
    val plain = alnum(reference ?: return null).uppercase()
    return if (plain.length < 4) null else plain.takeLast(4)
}

/** مرجع الرسالة في الاستيراد (`SMS:` + البصمة) — **نفس** اللي القارئين بيحطوه (`smsRow`). لإجابة سؤال المبلغ المحلي (§75-12). */
fun smsSourceReference(message: BankSmsMessage): String = "SMS:" + hashContent(message.sender + "|" + message.receivedAt + "|" + normalizeSmsBody(message.body))

/** نص الرسالة المحجوب — نفس الوصف اللي القارئين بيكتبوه. */
fun smsSafeText(message: BankSmsMessage): String = redactSms(normalizeSmsBody(message.body))

/** رقم البنك المرجعي في الرسالة — نفس اللي القارئين بيحطوه في `SmsRow.bankReference` (لإجابة سؤال المبلغ المحلي §75-12 → §77-D). */
fun smsMessageReference(message: BankSmsMessage): String? = smsReferenceOf(normalizeSmsBody(message.body))

/** نوع الرسالة بعد §77-D — نفس اللي القارئين بيعملوه (`refineSmsKind`)؛ الرسالة الأجنبية في مصر بتعدّي من غيره في القارئ. */
fun smsMessageKind(message: BankSmsMessage, kind: SmsKind, direction: Direction): SmsKind = refineSmsKind(normalizeSmsBody(message.body), kind, direction)

/** كسور العملة حسب ISO 4217 (الدولار 2 · الدينار الكويتي 3 · الين 0). */
fun currencyDecimals(code: String): Int = foreignDecimals(code)

/**
 * المبلغ المكتوب جنب عملة اتعرفت بعدين ⇒ وحدتها الصغرى، أو null لو مش مقروء بالظبط (قاعدة 10 — ما بنخمّنش): أرقام وفواصل آلاف «,»
 * ونقطة عشرية واحدة كسورها مش أكتر من كسور العملة.
 */
fun writtenForeignMinor(written: String?, currency: String): Long? {
    val text = latinizeDigits(written ?: return null).trim().replace(",", "")
    val match = Regex("(\\d{1,15})(?:\\.(\\d+))?").matchEntire(text) ?: return null
    val decimals = currencyDecimals(currency)
    val fraction = match.groupValues[2]
    if (fraction.length > decimals) return null
    var minor = match.groupValues[1].toLong()
    repeat(decimals) { minor *= 10 }
    val frac = if (fraction.isEmpty()) 0L else fraction.padEnd(decimals, '0').toLong()
    return (minor + frac).takeIf { it in 1..MAX_SAFE_HALALAS }
}
