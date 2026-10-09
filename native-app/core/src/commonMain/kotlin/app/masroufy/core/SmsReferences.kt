package app.masroufy.core

/**
 * رقم البنك المرجعي في رسالة البنك (§77-D — الشريحة S3): «Ref» · «Ref#» · «Ref No.» · «Reference» · «الرقم المرجعي» · «رقم مرجعي» ·
 * «رقم المرجع» · «مرجع رقم» · «مرجع»/«المرجع» · «رقم العملية». القيمة = أول كلمة بعد اللابل فيها **رقم** و4 حروف/أرقام على الأقل
 * («Ref. Code» مش مرجع). بترجع **بعد الحجب** (`redactSms` — الأرقام الطويلة «••••» + آخر 4) لأن الوصف المتخزن محجوب أصلًا.
 *
 * **المطابقة بآخر 4 بس** ([referenceTail]): وصف الرسالة المتخزن فيه «••••7781» مكان «553317781»، وتخزين فايربيز بيقص أي 5 أرقام في
 * مرجع الكشف («****7781») — فآخر 4 حروف/أرقام هي الحاجة الوحيدة اللي بتفضل زي ما هي في كل مكان.
 */
private val REFERENCE = Regex(
    "(?:(?<![A-Za-z])ref(?:erence)?(?![A-Za-z])\\.?(?:\\s*(?:no|num|number)(?![A-Za-z])\\.?)?" +
        "|الرقم\\s+المرجعي|رقم\\s+مرجعي|رقم\\s+المرجع|مرجع\\s+رقم|رقم\\s+العملية|(?<![\\u0600-\\u06FF])(?:ال)?مرجع(?![\\u0600-\\u06FF]))" +
        "[ \\t]*[:：#.]?[ \\t]*#?[ \\t]*([A-Za-z0-9•*][A-Za-z0-9•*\\-/]*)",
    RegexOption.IGNORE_CASE,
)

private fun alnum(text: String): String = text.filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }

/** الرقم المرجعي زي ما اتقري (محجوب)، أو null. */
fun smsReferenceOf(body: String): String? {
    for (m in REFERENCE.findAll(latinizeDigits(body))) {
        val token = m.groupValues[1].trimEnd('-', '/', '.')
        val plain = alnum(token)
        if (plain.length >= 4 && plain.any { it in '0'..'9' }) return redactSms(token)
    }
    return null
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
