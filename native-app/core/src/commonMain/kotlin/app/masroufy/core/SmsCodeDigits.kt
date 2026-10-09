package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * **رقم رمز فعلًا** في رسالة البنك (فلتر الجهاز بيرمي رسالة الرمز بس لو فيها رمز — `SmsVocabulary.ignoreBeforeStorage`). اتنقل من
 * `SmsGuards.kt` في الجولة التامنة (حد الـ300 سطر).
 *
 * **الجولة التامنة** (المراجعة العدائية الرابعة — OVERRIDES §72.5): عمليات حقيقية خلصت كانت **بتترمي قبل الحفظ** وتضيع لأن فيها كلمة رمز
 * («Verified with OTP» · «باستخدام رمز التحقق» · «Never share your OTP … call 19990») **ورقم تاني** مالوش علاقة بالرمز اتقري رمز: سنة بعد
 * اسم شهر («09 Oct 2026» · «9 أكتوبر 2026») · أرقام الكارت من غير نجمة («mada Pay 5093») · رقم الخدمة («اتصل على 19990») · كود دفع فوري
 * وكود المشترك والطلب والحجز والتاجر («كود الدفع 7781234» · «Customer code: 553128» · «Order code 55821») · إيصال · فاتورة · فرع.
 * دلوقتي الأرقام دي **مش رمز**، والرقم اللي في جملة تانية غير جملة كلمة الرمز ما بيتحسبش لو الرسالة عملية خلصت ([codeBesideSensitiveWord]).
 */

private val CI = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"

/**
 * رقم 4–8 أرقام لوحده: مش جوه رقم أطول ولا تاريخ ولا ساعة ولا مبلغ بكسور، ومش بعد نجمة أو «•» (كارت أو حساب متقص). الجولة التامنة:
 * بعد «:» لازقة بيتحسب («الرمز:111111») — إلا بعد رقم («10:4500» ساعة).
 */
private val CODE_CANDIDATE = Regex("(?<![\\d.,٫٬*•xX#/\\\\-])(?<!\\d:)\\d{4,8}(?!\\d|[.,٫٬/:\\\\-]\\d)")
private val MONEY_BEFORE = Regex("(?:${SmsVocabulary.CURRENCY}|بمبلغ|المبلغ|مبلغ|${B}amount|${B}of)$S*[:：]?$S*$", CI)
private val MONEY_AFTER = Regex("^$S*(?:${SmsVocabulary.CURRENCY})", CI)

/** كلمة رمز (OTP) — اللابل ده **مش** رقم عملية حتى لو قبله «رقم/كود/code». */
private const val AR_OTP_LABEL = "(?:ال)?(?:تحقق|توثيق|تفعيل|تأكيد|تاكيد|سري|مؤقت|دخول|أمان|امان|مرور)(?![$AR])"
private const val EN_OTP_LABEL =
    "(?:verification|otp|one|security|confirmation|authentication|activation|login|access|secret|secure|sms|temporary|your|the|this|a|pin)"

private val ID_BEFORE = Regex(
    // «Verification No. 731905» رمز — «No.» لوحدها مش رقم حساب
    "(?:${B}ending(?:$S+(?:in|with))?|المنتهي[ةه]?$S*بـ?|${B}(?:ref(?:erence)?|id|trx|txn|transaction|card|account|acct|ac)(?:$S*(?:no|number|id)\\.?)?" +
        "|بطاقة|البطاقة|بطاقتك|بطاقتكم|حساب|الحساب|حسابك|حسابكم|مرجع[يى]?|(?:ال)?عملية|رقم)$S*[:：#.]?$S*$" +
        // ── الجولة التامنة: «كود المشترك 44712» · «رقم الإيصال 5502231» · «كود الدفع 7781234» · «كود التاجر 778120» (مش كلمة رمز بعد «كود/رقم») ──
        "|(?<![$AR])(?:رقم|كود)$S*(?!$AR_OTP_LABEL)(?:ال)?[$AR]+$S*[:：#.]?$S*$|(?<![$AR])(?:ل|لل|ال)?خط$S*$" +
        // «Customer code: 553128» · «Bill code 4471209» · «Order code 55821» · «service code 1234» · «Authorization code 482211»
        "|$B(?!$EN_OTP_LABEL$B)[A-Za-z]+$S+(?:code|no\\.?|number|num|id|#)$S*[:：#.]?$S*$" +
        "|$B(?:receipt|invoice|store|order|branch|terminal|agent|ticket|booking|meter|subscriber|line|bill|merchant|customer)" +
        "(?:$S*(?:no\\.?|number|#|id))?$S*[:：#.]?$S*$",
    CI,
)
private val MONTH_DAY_BEFORE = Regex("(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?$S+\\d{1,2},?$S*$", CI)

/** الجولة التامنة: سنة بعد اسم الشهر («09 Oct 2026» · «9 أكتوبر 2026» · «16 رمضان 1447»). */
private val MONTH_YEAR_BEFORE = Regex(
    "(?:${B}(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?" +
        "|(?<![$AR])(?:يناير|فبراير|مارس|[أاإ]بريل|مايو|يونيو|يونيه|يوليو|يوليه|[أا]غسطس|سبتمبر|[أا]كتوبر|نوفمبر|ديسمبر|محرم|صفر" +
        "|ربيع$S+(?:ال)?(?:[أا]ول|ثاني|آخر|اخر)|جماد[يى]$S+(?:ال)?(?:[أا]ول[ىي]|[أا]خر[ةه]|ثاني[ةه]?|آخر[ةه]?)|رجب|شعبان|رمضان|شوال" +
        "|ذو$S+ال\\S+|ذي$S+ال\\S+)(?![$AR]))$S*,?$S*$",
    CI,
)

/** الجولة التامنة: رقم الكارت من غير نجمة بعد اسم الشبكة أو المحفظة («Via: mada Pay 5093» · «بطاقة مدى 5093» · «Visa 4476»). */
private val CARD_WORD_BEFORE = Regex(
    "(?:${B}(?:mada|visa|master(?:card)?|meeza|card|pay|amex)|(?<![$AR])(?:مدى|مدي|فيزا|ماستر(?:كارد)?|ميزة|كارت|بكارت|بطاق\\S*|ببطاق\\S*|باي))$S*[:：#*•]*$S*$",
    CI,
)

/** الجولة التامنة: رقم خدمة العملاء («call 19990» · «اتصل على 19990» · «Report any OTP request to 7115»). */
private val HOTLINE_BEFORE = Regex(
    "(?:${B}call|(?<![$AR])و?(?:اتصل|اتصال|الاتصال)|${B}hotline|الخط$S*الساخن|${B}contact|${B}report$B[^\\n]{0,40}?${B}to|(?:على|علي)$S*الرقم|بالرقم)" +
        "(?:$S+(?:us|on|at|to|على|علي|ب|بنا))*$S*[:：]?$S*$",
    CI,
)

private fun freeCodesIn(text: String): Sequence<MatchResult> = CODE_CANDIDATE.findAll(text).filter { m ->
    val lineStart = text.lastIndexOf('\n', m.range.first - 1) + 1
    val before = text.substring(maxOf(lineStart, m.range.first - 40), m.range.first)
    val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 12))
    !MONEY_BEFORE.containsMatchIn(before) && !MONEY_AFTER.containsMatchIn(after) && !ID_BEFORE.containsMatchIn(before) &&
        !MONTH_DAY_BEFORE.containsMatchIn(before) && !(m.value.length == 4 && MONTH_YEAR_BEFORE.containsMatchIn(before)) &&
        !CARD_WORD_BEFORE.containsMatchIn(before) && !HOTLINE_BEFORE.containsMatchIn(before)
}

/**
 * الجولة السابعة: الرسالة فيها **رقم رمز فعلًا** — 4 لـ8 أرقام لوحدهم مش مبلغ (جنبه عملة أو «مبلغ») ولا سنة تاريخ ولا آخر 4 من كارت أو
 * حساب ولا رقم مرجع. الجولة التامنة: ولا سنة بعد اسم شهر ولا رقم كارت بعد اسم الشبكة ولا رقم خدمة العملاء ولا رقم عملية بلابله.
 */
internal fun hasFreeCode(text: String): Boolean = freeCodesIn(text).any()

private val SENTENCE_BREAK = Regex("[.!؟?](?=$S|$)|\\n")

/**
 * الجولة التامنة: رقم الرمز **في نفس جملة** كلمة الرمز ([sensitive] = أماكن كلمات الرمز في [text]) — «Verification: 551204» ·
 * «رمز التحقق 482913 لعملية شراء …» · «Password: 551204». رسالة عملية خلصت فيها «Verified with OTP» في سطر و«Ref 553120» في سطر تاني
 * مش رسالة رمز.
 */
internal fun codeBesideSensitiveWord(text: String, sensitive: Sequence<IntRange>): Boolean = sensitive.any { range ->
    val start = SENTENCE_BREAK.findAll(text.substring(0, range.first)).lastOrNull()?.range?.last?.plus(1) ?: 0
    val end = SENTENCE_BREAK.find(text, range.last + 1)?.range?.first ?: text.length
    hasFreeCode(text.substring(start, maxOf(start, end)))
}
