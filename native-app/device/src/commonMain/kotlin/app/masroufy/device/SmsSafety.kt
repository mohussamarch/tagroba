package app.masroufy.device

import app.masroufy.core.Sha256
import app.masroufy.core.SmsVocabulary
import app.masroufy.core.latinizeDigits

/**
 * فلتر رسايل البنك **قبل أي حفظ على الجهاز** — نقل `SmsSafety.java` (التطبيق الحالي)، والكلمات بقت من مفردات القارئ نفسه
 * (`core/SmsGuards.kt` — `SmsVocabulary`) عشان الفلتر والقارئ ما يختلفوش:
 * رسايل الرموز (OTP) والعروض والمرفوض والحجز والطلبات والمعلومات والرسايل الشخصية ما بتتحفظش أصلًا، وأرقام الحسابات والبطاقات
 * بتتحول لـ`••••` + آخر 4 — **المبلغ اللي جنبه عملة ما بيتحجبش** (مبلغ 5 أرقام زي «بـSR 12500» كان بيتحجب ويضيع في نسخة قديمة).
 * جلسة 32: كلمات حركة وعملات أشكال البحث اتضافت (الجنيه «جم/ج/LE/ج.م» · «استرجاع» · «عكس عملية» · «مشتريات» · «received» …) —
 * من غيرها كل رسايل مصر تقريبًا كانت بتترمي في صمت.
 * كود نقي من غير أندرويد، فبيتختبر على الكمبيوتر (`SmsSafetyTest`).
 */
object SmsSafety {
    private val numbers = Regex("SA[\\d\\s]{20,}|\\b(?:\\d[ -]*){12,34}\\b|\\d{5,}", RegexOption.IGNORE_CASE)
    private val financial = Regex(
        "(?:(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)\\s*[:：]?\\s*)?(?:${SmsVocabulary.CURRENCY}\\s*[:：]?\\s*[\\d,٬]+(?:[.٫]\\d{1,2})?|[\\d,٬]+(?:[.٫]\\d{1,2})?\\s*${SmsVocabulary.CURRENCY})",
        RegexOption.IGNORE_CASE,
    )

    /** النص الآمن للحفظ، أو `null` = الرسالة دي ما تتحفظش خالص. */
    fun sanitize(body: String?): String? {
        if (body == null || body.length > 8000) return null
        val text = latinizeDigits(body)
        if (SmsVocabulary.ignoreBeforeStorage(text) || !SmsVocabulary.hasMovement(text) || !SmsVocabulary.hasMoney(text)) return null
        val safe = StringBuilder()
        var end = 0
        for (amount in financial.findAll(text)) {
            safe.append(redactIdentifiers(text.substring(end, amount.range.first))).append(amount.value)
            end = amount.range.last + 1
        }
        return safe.append(redactIdentifiers(text.substring(end))).toString()
    }

    private fun redactIdentifiers(text: String): String = numbers.replace(text) { m -> "••••" + m.value.filter { it in '0'..'9' }.takeLast(4) }

    /** معرّف ثابت للرسالة (نفس الرسالة من نفس المرسل في نفس الوقت = نفس المعرّف، فما بتتكررش). */
    fun key(sender: String, timestamp: Long, body: String): String = Sha256.hex("${sender.lowercase()}|$timestamp|$body")
}
