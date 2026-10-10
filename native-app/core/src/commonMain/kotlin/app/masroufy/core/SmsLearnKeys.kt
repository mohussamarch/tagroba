package app.masroufy.core

/**
 * **§77-A «وضع التعلّم»** (قرار المالك 2026-10-09): رسالة البنك بتتسجل لوحدها **بس بعد ما المالك يأكد أول رسالة من نفس الشكل** من نفس
 * المرسل. «نفس الشكل» = البصمة دي: **هيكل الرسالة كلها** من غير قيمها —
 * - السعودية: العنوان (المبلغ اللي فيه مخفي) + كل سطر بعده بنوع خانته ولابله ونوع قيمته (`SmsKnownShapesSaudi.saudiLayoutSkeleton`).
 * - مصر: القالب + الجملة والمحل والاسم مكانهم علامة (`SmsKnownShapesEgypt.egyptLayoutSkeleton`).
 * المحل والاسم والمبلغ والتاريخ والكارت والمرجع **ما بيغيّروش** البصمة؛ كلمة ثابتة اتغيّرت (العنوان · اللابل · جملة التحذير) **بتغيّرها**
 * ⇒ الرسالة تستنى تأكيد مرة. البصمة بتتحسب **بس** للشكل الواضح ([SmsShape.clear]) — الكلمات العامة عمرها ما بتتعلّم (اختيار Claude).
 * البصمة **بصمة** ([hashContent]) — ولا كلمة من نص الرسالة بتتخزن.
 */

/** نسخة طريقة البصمة: لو اتغيّرت، الأشكال اللي اتعلّمت بتستنى تأكيد مرة تاني (أأمن من بصمة قديمة تعدّي شكل جديد). */
private const val LAYOUT_VERSION = "sms-layout/v1"

/** بصمة شكل رسالة سعودية واضحة [shape]، أو null. */
internal fun saudiLearnKey(body: String, shape: SmsShape): String? {
    if (!shape.clear) return null
    val skeleton = saudiLayoutSkeleton(body) ?: return null
    return hashContent("$LAYOUT_VERSION\nSA\n${shape.wire}\n$skeleton")
}

/** بصمة شكل رسالة مصرية واضحة [shape]، أو null. */
internal fun egyptLearnKey(body: String, shape: SmsShape): String? {
    if (!shape.clear) return null
    val skeleton = egyptLayoutSkeleton(body) ?: return null
    return hashContent("$LAYOUT_VERSION\nEG\n${shape.wire}\n$skeleton")
}

private val MONTH_DATE = Regex("(?<![a-z])(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.? \\d{1,2},? \\d{4}")
private val CLOCK_VALUE = Regex("(?<![\\d:])\\d{1,2}:\\d{2}(?::\\d{2})?(?: ?(?:am|pm|ص|م)(?![a-z\\u0600-\\u06FF]))?")

/** رقم بأي شكل: مبلغ بفواصل · تاريخ بأرقام · كارت أو حساب أو مرجع متقص («**4821» · «••••0427» · «xx6618») · موبايل («+2010…»). */
private val NUMBER_VALUE = Regex("[+•*#x.]*\\d(?:[\\d•*#.,٬٫/\\\\\\-]*\\d)?")

/** المسافة جنب الرقم مش شكل («EGP174.40» و«EGP 174.40» نفس القالب — القوالب نفسها بتقبل الاتنين). */
private val SPACED_NUMBER = Regex(" ?# ?")

/** القيم جوه نص ثابت (عنوان · سطر تحذير · جملة مصر): التاريخ بالشهر ⇒ `<date>` · الساعة (بـ AM/PM) ⇒ `<time>` · أي رقم ⇒ `#`. */
internal fun maskLayoutValues(text: String): String =
    text.replace(MONTH_DATE, "<date>").replace(CLOCK_VALUE, "<time>").replace(NUMBER_VALUE, "#").replace(SPACED_NUMBER, "#")
