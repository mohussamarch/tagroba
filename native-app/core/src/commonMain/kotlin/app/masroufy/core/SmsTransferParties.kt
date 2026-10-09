package app.masroufy.core

import app.masroufy.core.JsText.B

/**
 * الطرف التاني في **رسايل البنك** (§72): الوصف المتخزن = نص الرسالة بعد قص الأرقام (`redactSms` — آخر 4 بس). الطرف = **الاسم
 * + آخر 4 أرقام** لو الاتنين في الرسالة، وإلا اللي موجود (§39/§60). الأشكال من أشكال البحث (`research/banks/`) والرسايل المخترعة
 * في المشروع — **مفيش رسالة حقيقية فيها الطرف لسه** (سؤال §72-ب مفتوح للمالك).
 *
 * السعودية (سطور بعناوين) — الصادر: «إلى:/الى:/To:» · «المستفيد:» · «Receiver:» · «إلى <اسم>» من غير نقطتين (الفرنسي والأهلي) ·
 * «لـ7719;<اسم>» (الراجحي 2026) · «لـ <اسم>» + «لحساب *7719» (الإنماء)؛ والأرقام من «إلى حساب:» · «لحساب» · «آيبان/IBAN» · «الى:7719».
 * الوارد: «من:/From:» · «من <اسم>» و«From <اسم>» (الإنماء وإس تي سي — أول سطر) · «من7719;<اسم>» (الراجحي 2026)؛ والأرقام من
 * «من حساب» · «آيبان:» العربي (ساب والفرنسي — الإنجليزي «IBAN:» في دي 360 الوارد = حسابك إنت فما بيتقريش) · «من:7719».
 * مصر (سطر واحد): «من رقم <موبايل> المسجل بإسم <اسم>» و«لرقم <موبايل>» (المحافظ) · «من <اسم> رقم مرجعي» و«إلى <اسم> رقم مرجعي»
 * (الأهلي) · «from <NAME>.» · «from <digits>» (فودافون). QNB «IPN transfer sent/received» **مفيهاش الطرف أصلًا** ⇒ null.
 */

private val LM = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
private val EI = setOf(RegexOption.IGNORE_CASE)
private const val H = "[ \\t]"
private const val TO = "(?:إلى|الى)"

// ── شكل الرسالة ──────────────────────────────────────────────────────────

private val QNB_SMS_TRANSFER = Regex("^IPN transfer (?:sent|received) with amount of")

/** رسايل مصر سطر واحد: بتبدأ بعبارة الرسالة مش بوصف كشف (الكشف: «IPN TRANSFER-…» بحروف كبيرة · «W-/FRACCT/…» · عربي مقلوب). */
private val ONE_LINE_SMS = Regex(
    "^(?:تم$H|لقد$H+تم|يرجى$H+العلم|عميلنا|إيداع$H+تحويل$H+لحظي|Dear$H+customer|You$H+have|You$H+received|Your$H|Instant$H+transfer|" +
        "IPN Transfer (?:with|dated)|A Trx using|The transaction|[A-Z][a-z]{2}$H+\\d{1,2},$H+\\d{4})",
)

/** وصف الكشف سطر واحد دايمًا (CSV/PDF)؛ الرسالة سطور، أو سطر واحد بشكل رسايل مصر. */
internal fun looksLikeSms(text: String): Boolean = QNB_SMS_TRANSFER.containsMatchIn(text) || '\n' in text || ONE_LINE_SMS.containsMatchIn(text)

/** تحويل في رسالة محفظة أو تحويل لحظي — كلمة «تحويل» ممكن تيجي بعد أول 60 حرف («… من <اسم> تحويل لحظي» · «(IPN Inward Transfer)»). */
private val SMS_TRANSFER_HINT = Regex(
    "من${H}*رقم$H*[0-9*•]{4}|لرقم$H*[0-9*•]{4}|Received$H*EGP|received$H+[\\d,.]+$H*EGP$H+from|IPN$H+Inward|تحويل$H*لحظي",
    EI,
)

internal fun isSmsTransferText(text: String): Boolean = SMS_TRANSFER_HINT.containsMatchIn(text)

// ── الأنماط ──────────────────────────────────────────────────────────────

private val ACCOUNT_NAME_IN = Regex("^$H*من$H*[:：]?$H*([0-9*•]{3,})$H*;$H*([^\\n]+?)$H*$", LM)
private val ACCOUNT_NAME_OUT = Regex("^$H*لـ$H*[:：]?$H*([0-9*•]{3,})$H*;$H*([^\\n]+?)$H*$", LM)

private val NAME_OUT = Regex(
    "^$H*(?:(?:$TO|to)$H*[:：]|المستفيد$H*[:：]|Receiver$H*[:：]|Beneficiary$H*[:：]|(?:$TO|to)$H+(?!حساب)|لـ$H+(?=[^\\n]+\\n$H*لحساب))$H*([^\\n]+?)$H*$",
    LM,
)
private val NAME_IN = Regex("^$H*(?:(?:من|from)$H*[:：]|(?:من|from)$H+(?!حساب|بنك|البائع|رصيد|بطاقة|account))$H*([^\\n]+?)$H*$", LM)

private const val DIGITS = "([0-9*•xX][0-9*•xX -]*)"
private val ACCOUNT_OUT = Regex("^$H*(?:$TO$H*حساب|لحساب|آيبان|الأيبان|IBAN|$TO|to)$H*[:：]?$H*$DIGITS", LM)
private val ACCOUNT_IN = Regex("^$H*(?:من$H*حساب|آيبان|الأيبان|من|from)$H*[:：]?$H*$DIGITS", LM)

// مصر — سطر واحد
private val EG_WALLET_FROM = Regex("من$H*رقم$H*([0-9*•]{4,})(?:$H*المسجل$H*ب[إا]سم$H*(.+?)(?=$H*(?:على$H*رقم|[.\\n]|$)))?")
private val EG_WALLET_TO = Regex("لرقم$H*([0-9*•]{4,})")
private val EN_FROM_DIGITS = Regex("${B}from$H+([0-9*•]{4,})", EI)
/**
 * «from NADIA M. EXAMPLE. Ref: …» — الاسم كلمات، والحرف لوحده بعده نقطة اختصار (initial) مش آخر الجملة (الجولة التانية من المراجعة:
 * كان بيتقص «NADIA M» فناس مختلفين بنفس الاسم الأول والحرف بيبقى ليهم نفس الطرف).
 */
private const val EN_WORD = "(?:[A-Za-z]\\.(?=$H)|[A-Za-z][A-Za-z'*-]*)"
private val EN_FROM_NAME = Regex("${B}from$H+(?!your$B)($EN_WORD(?:$H+$EN_WORD)*)$H*\\.(?:$H|$)", EI)
private val AR_FROM_NAME = Regex(
    "(?<![\\u0600-\\u06FF])من$H+(?!رقم|حساب|بطاقة|جهة)([^\\n]+?)$H+(?:رقم$H*مرجعي|برقم$H*مرجعي|عبر$H*شبكة|$TO$H*حساب|مرجع|تحويل$H*لحظي)",
)
private val AR_TO_NAME = Regex("(?<![\\u0600-\\u06FF])$TO$H+(?!حساب)([^\\n]+?)$H+(?:رقم$H*مرجعي|برقم$H*مرجعي|مرجع)")

private val LETTER = Regex("[A-Za-z\\u0600-\\u06FF]")
private val DATE_LIKE = Regex("^\\d{1,4}[-/.\\\\]\\d{1,2}")

/** اسم حقيقي: فيه حرف، ومش تاريخ. */
private fun nameOf(value: String?): String? = value?.let(JsText::trim)?.takeIf { LETTER.containsMatchIn(it) && !DATE_LIKE.containsMatchIn(it) }

private fun lastFourOf(run: String?): String? = run?.filter { it in '0'..'9' }?.takeLast(4)?.takeIf { it.length == 4 }

private fun firstName(regex: Regex, text: String): String? = regex.findAll(text).firstNotNullOfOrNull { nameOf(it.groupValues[1]) }

private fun firstDigits(regex: Regex, text: String): String? = regex.findAll(text).firstNotNullOfOrNull { lastFourOf(it.groupValues[1]) }

private fun sideParty(text: String, direction: Direction): TransferPartyRef? {
    val combined = if (direction == Direction.OUT) ACCOUNT_NAME_OUT else ACCOUNT_NAME_IN
    combined.find(text)?.let { m -> nameOf(m.groupValues[2])?.let { return partyRef(it, lastFourOf(m.groupValues[1])) } }
    val name = firstName(if (direction == Direction.OUT) NAME_OUT else NAME_IN, text)
    val digits = firstDigits(if (direction == Direction.OUT) ACCOUNT_OUT else ACCOUNT_IN, text)
    return if (name != null || digits != null) partyRef(name.orEmpty(), digits) else null
}

private fun egyptParty(text: String, direction: Direction): TransferPartyRef? {
    if (direction == Direction.IN) {
        EG_WALLET_FROM.find(text)?.let { m -> return partyRef(nameOf(m.groupValues[2]).orEmpty(), lastFourOf(m.groupValues[1])) }
        firstName(AR_FROM_NAME, text)?.let { return partyRef(it, null) }
        EN_FROM_DIGITS.find(text)?.let { m -> return partyRef("", lastFourOf(m.groupValues[1])) }
        firstName(EN_FROM_NAME, text)?.let { return partyRef(it, null) }
    } else {
        EG_WALLET_TO.find(text)?.let { m -> return partyRef("", lastFourOf(m.groupValues[1])) }
        firstName(AR_TO_NAME, text)?.let { return partyRef(it, null) }
    }
    return null
}

/** الطرف من رسالة البنك — اسم و/أو آخر 4، أو null لو الرسالة مفيهاش الطرف (ما بنخمّنش). */
internal fun smsPartyOf(text: String, direction: Direction): TransferPartyRef? {
    if (QNB_SMS_TRANSFER.containsMatchIn(text)) return null
    return sideParty(text, direction) ?: egyptParty(text, direction)
}
