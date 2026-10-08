package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * عملة أجنبية بكود ISO 4217 جنب مبلغ — **أي عملة**، مش قايمة العشر عملات بس (مراجعة جلسة 33: «مبلغ:TRY 450.00» كانت بتترفض
 * «المبلغ مش واضح» أو فلتر الجهاز بيرميها في صمت، وقرار §75-12 بيقول العملية الأجنبية تتسجل وتسأل عن المبلغ المحلي).
 *
 * عشان اسم محل زي «TOP 10» أو «ALL 4 KIDS» ما يتقريش عملة: الكود **حروف كبيرة بس**، ولازق في رقم، والرقم يا إما فيه كسور
 * («450.00») يا إما جاي بعد كلمة مبلغ مباشرة («مبلغ:JPY 4500» · «Amount: 4500 JPY»).
 * الريال والجنيه مش هنا: دول عملة البلد، وليهم قواعدهم في كل قارئ.
 */
private const val ISO_CODES =
    "AED|AFN|ALL|AMD|ANG|AOA|ARS|AUD|AWG|AZN|BAM|BBD|BDT|BGN|BHD|BIF|BMD|BND|BOB|BRL|BSD|BTN|BWP|BYN|BZD|CAD|CDF|CHF|CLP|CNY|" +
        "COP|CRC|CUP|CVE|CZK|DJF|DKK|DOP|DZD|ERN|ETB|EUR|FJD|FKP|GBP|GEL|GHS|GIP|GMD|GNF|GTQ|GYD|HKD|HNL|HTG|HUF|IDR|ILS|INR|IQD|" +
        "IRR|ISK|JMD|JOD|JPY|KES|KGS|KHR|KMF|KPW|KRW|KWD|KYD|KZT|LAK|LBP|LKR|LRD|LSL|LYD|MAD|MDL|MGA|MKD|MMK|MNT|MOP|MRU|MUR|MVR|" +
        "MWK|MXN|MYR|MZN|NAD|NGN|NIO|NOK|NPR|NZD|OMR|PAB|PEN|PGK|PHP|PKR|PLN|PYG|QAR|RON|RSD|RUB|RWF|SBD|SCR|SDG|SEK|SGD|SHP|SLE|" +
        "SLL|SOS|SRD|SSP|STN|SVC|SYP|SZL|THB|TJS|TMT|TND|TOP|TRY|TTD|TWD|TZS|UAH|UGX|USD|UYU|UZS|VES|VND|VUV|WST|XAF|XCD|XCG|XOF|" +
        "XPF|YER|ZAR|ZMW|ZWG|ZWL"

private const val MONEY_NUMBER = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d{1,2})?"

// من غير IGNORE_CASE: «try 3» أو «all 4» في الكلام مش عملة
private val ISO_AMOUNT = Regex(
    "(?<![A-Za-z])($ISO_CODES)$S*[:：]?$S*($MONEY_NUMBER)(?!\\d)|(?<![\\d.,٬٫])($MONEY_NUMBER)$S*($ISO_CODES)(?![A-Za-z])",
)

/** كلمة مبلغ قبل الكود أو الرقم على طول (في نفس السطر). */
private val AMOUNT_LABEL_BEFORE = Regex(
    "(?:بمبلغ|المبلغ|مبلغ|بقيمة|قيمة|بـ|${B}amount|${B}for|${B}of|${B}charged)$S*[:：]?$S*$",
    setOf(RegexOption.IGNORE_CASE),
)

/** مبلغ بعملة أجنبية: مكانه في النص + الكود + الرقم زي ما هو مكتوب. */
internal data class IsoMoney(val range: IntRange, val code: String, val number: String)

/** كل المبالغ بكود عملة أجنبية (مش ريال ولا جنيه) اللي شكلها فلوس فعلًا. */
internal fun isoMoneyIn(text: String): List<IsoMoney> = ISO_AMOUNT.findAll(text).mapNotNull { m ->
    val code = m.groups[1]?.value ?: m.groups[4]!!.value
    val number = m.groups[2]?.value ?: m.groups[3]!!.value
    val lineStart = text.lastIndexOf('\n', m.range.first - 1) + 1
    val looksLikeMoney = number.any { it == '.' || it == '٫' } || AMOUNT_LABEL_BEFORE.containsMatchIn(text.substring(lineStart, m.range.first))
    if (looksLikeMoney) IsoMoney(m.range, code, number) else null
}.toList()
