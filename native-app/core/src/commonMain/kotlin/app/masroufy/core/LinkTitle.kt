package app.masroufy.core

/**
 * fix055 (فحص 0.5.4 ملاحظة 2): اسم العملية في قوايم الربط («اربطه بالتحويل اللي وصل»). قبل كده رسالة بنك سطر واحد شكلها مش معروف
 * («حوالة محلية واردة من:<اسم> بـSR 600 إلى:0000 …») كان عنوانها نص الرسالة كله.
 *
 * الترتيب: طرف التحويل (`transferPartyOf`) ⇒ اسم التاجر لو مش هو نص الرسالة نفسه ⇒ ملاحظتك ⇒ الاسم بعد «من:» (الوارد) أو «إلى:» (الصادر)
 * في الرسالة السطر الواحد ⇒ اسم نوع العملية. **عمره ما يرجّع نص الرسالة.** المبلغ والتاريخ بيتعرضوا جنبه في الصف.
 */
fun linkTitleOf(tx: Transaction): String =
    transferPartyOf(tx)?.label?.let(::cleanTitle)
        ?: cleanTitle(tx.rawMerchantName)?.takeIf { it != cleanTitle(tx.rawDescription) && it.length <= LT_MAX_NAME }
        ?: cleanTitle(tx.note)
        ?: inlinePartyOf(tx.rawDescription, tx.observedDirection)
        ?: ruleFor(tx.economicKind).label

private const val LT_MAX_NAME = 40
private const val LT_SP = "[ \\t]"

/** «من:<اسم>» أو «إلى:<اسم>» جوه سطر — الاسم لحد المبلغ («بـ» · SR · SAR · EGP) أو العنوان اللي بعده أو آخر السطر، ولازم فيه حرف. */
private val INLINE_FROM = Regex("(?<![\\u0600-\\u06FF])(?:من|from)$LT_SP*[:：]$LT_SP*([^\\n:：]+?)(?=$LT_SP+(?:بـ|ب$LT_SP|SR|SAR|EGP|إلى|الى|to$LT_SP*[:：])|$LT_SP*$)", RegexOption.IGNORE_CASE)
private val INLINE_TO = Regex("(?<![\\u0600-\\u06FF])(?:إلى|الى|to)$LT_SP*[:：]$LT_SP*([^\\n:：]+?)(?=$LT_SP+(?:بـ|ب$LT_SP|SR|SAR|EGP|من|from$LT_SP*[:：]|\\d)|$LT_SP*$)", RegexOption.IGNORE_CASE)
private val LT_LETTER = Regex("[A-Za-z\\u0600-\\u06FF]")

internal fun inlinePartyOf(text: String?, direction: Direction): String? {
    if (text.isNullOrBlank()) return null
    val regex = if (direction == Direction.OUT) INLINE_TO else INLINE_FROM
    return regex.findAll(text).firstNotNullOfOrNull { m ->
        m.groupValues[1].trim().takeIf { LT_LETTER.containsMatchIn(it) && !it.first().isDigit() && it.length <= LT_MAX_NAME }
    }
}

private fun cleanTitle(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }
