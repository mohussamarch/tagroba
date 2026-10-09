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

    /**
     * المبلغ جنب عملة ما بيتحجبش. الجولة التانية من المراجعة: كود لازق في 5 أرقام أو أكتر من غير فواصل ولا كسور («Ref SR2026030512»)
     * رقم مرجع مش مبلغ — بيتحجب زي أي رقم طويل (كان بيتساب والقارئ يقراه ملياري ريال).
     */
    private val financial = Regex(
        "(?:(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)\\s*[:：]?\\s*)?(?:${SmsVocabulary.CURRENCY}(?!\\d{5,}(?![\\d,٬.٫]))\\s*[:：]?\\s*[\\d,٬]+(?:[.٫]\\d{1,2})?|[\\d,٬]+(?:[.٫]\\d{1,2})?\\s*${SmsVocabulary.CURRENCY})",
        RegexOption.IGNORE_CASE,
    )

    /**
     * الجولة السابعة (فودافون كاش): مبلغ صحيح 5 أرقام أو أكتر **لازق في العملة بعد فعل الاستلام أو الإرسال** («Received EGP12500») ده
     * المبلغ مش رقم مرجع — كان بيتحفظ «EGP••••2500» والرسالة تستنى «المبلغ مش واضح» والمبلغ نفسه يضيع من النسخة المحفوظة.
     */
    private val gluedAfterVerb = Regex(
        "(?<=(?:received|sent|paid|transferred|charged(?:\\s{1,3}for)?)\\s{1,3})(?:EGP|L\\.?E|SAR|SR)\\d{1,9}(?:[.,]\\d{1,2})?(?![\\d])",
        RegexOption.IGNORE_CASE,
    )

    /** الجولة السابعة: الرصيد من غير فواصل («الحالي 15230.50» · «Balance: 12345.00») — كان بيتحفظ «••••5230.50» والقالب المعروف يقع. */
    private val balanceNumber = Regex("(?<=(?:الحالي|الحالى|balance|رصيدك|رصيد)\\s{0,3}[:：]?\\s{0,3})\\d{1,9}(?:[.,]\\d{1,2})?(?!\\d)", RegexOption.IGNORE_CASE)

    /** الجولة السابعة: موبايل بفواصل («010-6441-8273» · «055 123 4567») — كان بيتحفظ كامل (القاعدة كانت 12 رقم أو 5 ورا بعض). */
    private val phone = Regex("(?<![\\d])0(?:1[0125]|5\\d)[ .\\-]\\d{3,4}[ .\\-]\\d{3,4}(?![\\d])")

    /** الجولة التالتة: العملة بعد «Ref/No./#/مرجع» على طول رقم مرجع مش مبلغ («Ref SR 48213») — بيتحجب زي أي رقم طويل (والقارئ بيسيبه). */
    private val referenceBefore = Regex("(?:(?<![A-Za-z])ref(?:erence)?|(?<![A-Za-z])no\\.?|#|مرجع|المرجع)[ \\t]*[:：.]?[ \\t]*$", RegexOption.IGNORE_CASE)

    private fun afterReference(text: String, at: Int): Boolean =
        referenceBefore.containsMatchIn(text.substring(text.lastIndexOf('\n', at - 1) + 1, at))

    /**
     * التاريخ بسنة («2026-03-05» · «05-03-2026» · «14/09/2026») ما بيتحجبش: من غيره، قص الأرقام الطويلة كان بيلزق رقم الحساب أو
     * المرجع اللي قبله في التاريخ («**3355 2026-03-05 09» = 14 رقم ⇒ «••••0509») والرسالة تترفض «التاريخ مش واضح» (مراجعة جلسة 33).
     */
    private val dates = Regex("(?<!\\d)(?:\\d{4}[-/\\\\]\\d{1,2}[-/\\\\]\\d{1,2}|\\d{1,2}[-/\\\\]\\d{1,2}[-/\\\\](?:\\d{4}|\\d{2}))(?!\\d)")

    /** شكل تاريخ بجد: الشهر في النص من 1 لـ 12، والطرفين سنة (4 أرقام) أو من 1 لـ 31. */
    private fun plausibleDate(value: String): Boolean {
        val parts = value.split('-', '/', '\\')
        fun dayOrYear(p: String) = p.length == 4 || p.toInt() in 1..31
        return parts.size == 3 && parts[1].toInt() in 1..12 && dayOrYear(parts[0]) && dayOrYear(parts[2])
    }

    /** النص الآمن للحفظ، أو `null` = الرسالة دي ما تتحفظش خالص. */
    fun sanitize(body: String?): String? {
        if (body == null || body.length > 8000) return null
        val text = latinizeDigits(body)
        // الجولة الخامسة (§72: الانتظار مقبول، الضياع لا): بترمي بسبب صريح بس — رمز · حارس على رسالة أولها مش شكل بنك معروف · مفيهاش
        // مبلغ. كلمة الحركة ما بقتش شرط: القارئ بيرفض اللي مش فاهمه والرسالة تستنى المالك بدل ما تضيع
        if (SmsVocabulary.ignoreBeforeStorage(text) || !SmsVocabulary.hasAmount(text)) return null
        // اللي ما بيتحجبش: المبلغ جنب عملة (محلية أو أجنبية) والتاريخ
        val kept = (
            financial.findAll(text).filterNot { afterReference(text, it.range.first) }.map { it.range } + SmsVocabulary.foreignMoneyRanges(text) +
                dates.findAll(text).filter { plausibleDate(it.value) }.map { it.range } +
                gluedAfterVerb.findAll(text).map { it.range } + balanceNumber.findAll(text).map { it.range }
            ).sortedBy { it.first }
        val safe = StringBuilder()
        var end = 0
        for (range in kept) {
            if (range.last < end) continue
            val start = maxOf(range.first, end)
            safe.append(redactIdentifiers(text.substring(end, start))).append(text.substring(start, range.last + 1))
            end = range.last + 1
        }
        return safe.append(redactIdentifiers(text.substring(end))).toString()
    }

    private fun redactIdentifiers(text: String): String =
        numbers.replace(phone.replace(text) { m -> "••••" + m.value.filter { it in '0'..'9' }.takeLast(4) }) { m ->
            "••••" + m.value.filter { it in '0'..'9' }.takeLast(4)
        }

    /** معرّف ثابت للرسالة (نفس الرسالة من نفس المرسل في نفس الوقت = نفس المعرّف، فما بتتكررش). */
    fun key(sender: String, timestamp: Long, body: String): String = Sha256.hex("${sender.lowercase()}|$timestamp|$body")
}
