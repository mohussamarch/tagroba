package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT

/**
 * **قوالب البنوك والمحافظ المصرية المعروفة** (`research/banks/egypt-sms-formats.json` — رقم السطر جنب كل قالب — + عينات QNB مصر اللي
 * بعتها المالك §40.3) للتسجيل التلقائي (OVERRIDES §72: «المفهومة» = شكل معروف). رسايل مصر سطر واحد من غير عنوان، فالقالب = **أول
 * الجملة** بهيكلها كله («تم خصم <مبلغ> من بطاقة الخصم المباشر رقم» …) — مش كلمة زي «تم خصم» أو «received» في أي مكان (دي القاعدة
 * العامة في `EgyptSmsShapes.kt`، وبتفضل تقرا الرسالة بس ما بتسجلهاش لوحدها). واتجاه القالب لازم = اتجاه القارئ (دفاع تاني).
 * الأنماط **مكتوبة بعد [shapeKey]** (من غير شيل النقطتين): حروف صغيرة · «ا» مكان «أ/إ» · «ي» مكان «ى» · مسافة واحدة.
 */

private class EgyptShape(val bank: String, val id: String, pattern: String, val direction: Direction?) {
    val regex = Regex("^(?:$pattern)")
}

/** خانة المبلغ: كلمة أو اتنين («245.75 جم» · «EGP 245.75» · «EGP245.75») — مش جملة (عشان «من حسابك» ما تعديش على «إلى حسابك»). */
private const val AMT = "\\S+(?: \\S+)?"

/**
 * رقم (موبايل · حساب) والكلمة اللي بعده — فلتر الجهاز (`SmsSafety`) بيقص الرقم الطويل **ومعاه المسافة اللي بعده** («from ••••4567to Mobile»)،
 * فالمسافة اختيارية. (ده سلوك قديم في الفلتر، و`redactSms` مقفول في ملف المرجع — ما اتغيرش.)
 */
private const val NUM_THEN = "\\S+? ?"

private fun e(bank: String, id: String, pattern: String, direction: Direction?) = EgyptShape(bank, id, pattern, direction)

private val EGYPT_SHAPES: List<EgyptShape> = listOf(
    // ── الأهلي المصري ──
    e("nbe", "purchase-credit-card", "تم خصم $AMT من بطاقة الائتمان رقم", OUT), // #0
    e("nbe", "purchase-debit-card", "تم خصم $AMT من بطاقة الخصم المباشر رقم", OUT), // #1 #2 (الصرّاف نفس النص)
    e("nbe", "transfer-in-instapay", "تم اضافة تحويل لحظي ل(?:حسابكم|بطاقتكم)", IN), // #3 #5 #8
    e("nbe", "transfer-out-instapay", "تم تنفيذ تحويل لحظي من حسابكم", OUT), // #4 #9
    e("nbe", "transfer-in-instapay-network", "تم تحويل مبلغ $AMT لحسابكم المنتهي", IN), // #6
    e("nbe", "transfer-in-instapay-en", "instant transfer of $AMT received from", IN), // #7
    // ── التجاري الدولي ──
    e("cib", "purchase-credit-card", "your credit card ending with ?#? ?\\S*? ?was charged for", OUT), // #10
    e("cib", "refund-en", "the transaction on your credit card ?#? ?\\S* from .+ has been refunded", IN), // #11
    e("cib", "refund-ar", "لقد تم رد $AMT علي بطاقتكم الائتمانية", IN), // #12
    e("cib", "purchase-debit-card", "تم خصم مبلغ $AMT من بطاقة الخصم المباشر المنتهية", OUT), // #14
    e("cib", "credit-card-payment", "تم سداد مبلغ $AMT في بطاقتكم الائتمانية", null), // #15 (سداد البطاقة — الاتجاه حسب المحفظة)
    e("cib", "transfer-out-instapay", "يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ $AMT من حسابك", OUT), // #16
    e("cib", "transfer-in-instapay", "يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ $AMT الي حسابك", IN), // #17
    e("cib", "salary", "عميلنا العزيز لقد تم تحويل مبلغ $AMT علي حسابكم لدينا من جهة العمل", IN), // #18
    e("cib", "transfer-in-ipn-en", "you have received an ipn transfer of", IN), // #26
    e("cib", "transfer-in-instapay-2", "تم استلام تحويل لحظي بمبلغ $AMT من .+ الي حسابكم المنتهي", IN), // #27
    // ── بنوك تانية ──
    e("banquemisr", "transfer-in-instapay", "ايداع تحويل لحظي ipn بمبلغ", IN), // #28
    e("hsbc", "transfer-in", "your account ending in \\S+? ?has been credited with", IN), // #29
    e("kfh", "transfer-out-ipn", "ipn transfer with $AMT deducted on", OUT), // #30
    e("kfh", "ipn-returned", "ipn transfer dated .+ with $AMT returned", IN), // #31
    e("arabbank", "purchase-card", "a trx using card \\S+? ?from .+ for ", OUT), // #33 #34
    e("arabbank", "credit-card-credit", "تم قيد مبلغ $AMT لبطاقتك الائتمانية", IN), // #35
    e("breadfast", "card-top-up", "you received $AMT on .+ to your card ending in", IN), // #36
    // ── فودافون كاش ──
    e("vodafone-cash", "receive-ar", "تم استلام مبلغ $AMT من رقم ${NUM_THEN}المسجل باسم", IN), // #37
    // #38 — التاريخ والساعة الأول بأي شكل («2026-09-14 14:22» · «Sep 14, 2026 2:22:05 PM»)
    e("vodafone-cash", "receive-en", ".{0,40}?: received egp ?\\S+ from ${NUM_THEN}to mobile account number", IN),
    e("vodafone-cash", "send", "تم تحويل $AMT لرقم ${NUM_THEN}مصاريف الخدمة", OUT), // #39
    e("vodafone-cash", "cash-out", "تم سحب $AMT بنجاح\\.? رصيد حسابك في فودافون كاش", OUT), // #41
    e("vodafone-cash", "recharge-en", "you have successfully recharged $AMT to the balance of", OUT), // #45
    e("vodafone-cash", "recharge-ar", "تم شحن رصيد موبايلك ب ?$AMT بنجاح وخصم", OUT), // #46
    e("vodafone-cash", "receive-en-2", "you have received $AMT from ${NUM_THEN}transaction id", IN), // #49
    // ── محافظ تانية (e& money · أورانج كاش · وي باي) ──
    e("eg-wallet", "receive", "تم استلام (?:مبلغ )?$AMT من رقم ${NUM_THEN}رصيدك الحالي", IN), // #50 #52 #54
    e("eg-wallet", "send", "تم تحويل $AMT لرقم ${NUM_THEN}رصيدك الحالي", OUT), // #51 #53 #55
    // ── أشكال عامة في البحث ──
    e("eg-generic", "transfer-in-ipn-en", "dear customer, ipn transfer of $AMT credited to account", IN), // #56
    e("eg-generic", "transfer-in-instapay-ar", "تم اضافة مبلغ $AMT الي حسابك المنتهي .+ تحويل لحظي", IN), // #57
    // ── QNB مصر (عينات المالك الحقيقية §40.3 — مش في المستودع) ──
    e("qnb", "transfer-out-ipn", "ipn transfer sent with amount of", OUT),
    e("qnb", "transfer-in-ipn", "ipn transfer received with amount of", IN),
    e("qnb", "debit-card", "your debit card \\S+? ?had a successful transaction of", OUT),
)

/** عدد القوالب (للاختبار). */
internal val EGYPT_SHAPE_COUNT: Int get() = EGYPT_SHAPES.size

/** رسالة مصر اللي القارئ قبلها على [direction]: أول الجملة على قالب معروف واتجاهه نفس القارئ ⇒ [SmsShape.KnownShape]، غير كده تستنى. */
internal fun egyptShape(body: String, direction: Direction): SmsShape {
    val key = shapeKey(body, dropColons = false)
    val known = EGYPT_SHAPES.firstOrNull { it.regex.containsMatchIn(key) } ?: return SmsShape.KeywordFallback
    return if (known.direction == null || known.direction == direction) SmsShape.KnownShape(known.bank, known.id) else SmsShape.KeywordFallback
}
