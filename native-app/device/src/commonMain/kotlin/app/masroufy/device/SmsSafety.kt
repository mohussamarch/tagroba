package app.masroufy.device

import app.masroufy.core.Sha256
import app.masroufy.core.latinizeDigits

/**
 * فلتر رسايل البنك **قبل أي حفظ على الجهاز** — نقل `SmsSafety.java` (التطبيق الحالي) سطر بسطر:
 * رسايل الرموز (OTP) والعروض والمرفوض والرسايل الشخصية ما بتتحفظش أصلًا، وأرقام الحسابات والبطاقات بتتحول لـ`••••` + آخر 4
 * — **المبلغ اللي جنبه عملة ما بيتحجبش** (مبلغ 5 أرقام زي «بـSR 12500» كان بيتحجب ويضيع في نسخة قديمة).
 * كود نقي من غير أندرويد، فبيتختبر على الكمبيوتر (`SmsSafetyTest` — نفس حالات اختبار التطبيق الحالي).
 */
object SmsSafety {
    private val ignore = Regex(
        "\\bOTP\\b|verification\\s*code|one.time\\s*(password|code)|رمز\\s*(التحقق|التوثيق|التفعيل|الدخول)|كلمة\\s*(المرور|السر)|عرض|سيتم|offer|will be|scheduled|مرفوض|لم تتم|declined|failed|مشاركة\\s*الرمز|الرمز\\s*[:：]?\\s*\\d{4,8}",
        RegexOption.IGNORE_CASE,
    )

    // رسايل الراجحي بتكتب العملة «SR» — والعملات التانية كمان عشان الرسالة تظهر بسبب رفضها بدل ما تختفي
    private const val CURRENCY = "(?:(?<![A-Za-z])(?:SAR|SR|USD|EUR|GBP|AED|EGP)(?![A-Za-z])|ريال|ر\\.?س\\.?|دولار|يورو|جنيه)"
    // «transaction»: رسالة كارت QNB مصر («had a Successful transaction of EGP …») — من غيرها كانت بتترمي في صمت (اتكشف 2026-10-01)
    private val movement = Regex(
        "شراء|سحب|خصم|سداد|مدفوعات|دفع|حوالة|تحويل|إيداع|ايداع|راتب|استرداد|مرتجع|purchase|withdrawal|transfer|deposit|refund|salary|payment|transaction",
        RegexOption.IGNORE_CASE,
    )
    private val money = Regex(CURRENCY, RegexOption.IGNORE_CASE)
    private val numbers = Regex("SA[\\d\\s]{20,}|\\b(?:\\d[ -]*){12,34}\\b|\\d{5,}", RegexOption.IGNORE_CASE)
    private val financial = Regex(
        "(?:(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)\\s*[:：]?\\s*)?(?:$CURRENCY\\s*[:：]?\\s*[\\d,٬]+(?:[.٫]\\d{1,2})?|[\\d,٬]+(?:[.٫]\\d{1,2})?\\s*$CURRENCY)",
        RegexOption.IGNORE_CASE,
    )

    /** النص الآمن للحفظ، أو `null` = الرسالة دي ما تتحفظش خالص. */
    fun sanitize(body: String?): String? {
        if (body == null || body.length > 8000) return null
        val text = latinizeDigits(body)
        if (ignore.containsMatchIn(text) || !movement.containsMatchIn(text) || !money.containsMatchIn(text)) return null
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
