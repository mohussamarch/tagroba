package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT

/**
 * **قوالب البنوك والمحافظ المصرية المعروفة** (`research/banks/egypt-sms-formats.json` — رقم السطر جنب كل قالب — + عينات QNB مصر اللي
 * بعتها المالك §40.3) للتسجيل التلقائي (OVERRIDES §72: «المفهومة» = شكل معروف). رسايل مصر سطر واحد من غير عنوان، فالقالب = **الجملة
 * كلها** بخاناتها: [head] = أول الجملة بهيكلها («تم خصم <مبلغ> من بطاقة الخصم المباشر رقم» …) و[rest] = باقي الجملة لحد آخرها —
 * المبلغ والكارت والمحل والتاريخ والرصيد والمرجع وسطر «للمزيد اتصل» بس. **الجولة الخامسة:** قبل كده أول الجملة بس كان بيتفحص، فـ«… الساعة
 * 09:40 - العملية قيد المعالجة» · «… وتم عكس العملية» · «Money Request: Received …» كانوا بيتسجلوا عمليات خلصت. أي كلام زيادة ⇒ تستنى.
 * كمان: **المبلغ اللي القارئ قراه لازم = الرقم اللي في خانة المبلغ في القالب** (رسوم أو ضريبة اتقرت مبلغ ⇒ تستنى)، وكلمة حالة أو عكس
 * في أي مكان ([hasShapeDoubt]) ⇒ تستنى. واتجاه القالب لازم = اتجاه القارئ (دفاع تاني).
 * الأنماط **مكتوبة بعد [shapeKey]** (من غير شيل النقطتين): حروف صغيرة · «ا» مكان «أ/إ» · «ي» مكان «ى» · مسافة واحدة (والسطور كمان).
 */

/**
 * [head] أول الجملة · [rest] الباقي. مجموعة الالتقاط الأولى (أو التانية) = خانة المبلغ. [headAnyCurrency] = أول الجملة بأي عملة في خانة
 * المبلغ (فلتر الجهاز · كارت مصري اتخصم بالريال «تم خصم 300.00 SAR من بطاقة الخصم المباشر رقم …»).
 */
private class EgyptShape(val bank: String, val id: String, head: String, rest: String, val direction: Direction?) {
    val headAnyCurrency = Regex("^(?:${head.replace(EGC, ANY_CURRENCY)})")
    val full = Regex("(?:$head)$rest")
}

private const val N = "(?:[\\d,٬]+(?:[.٫]\\d+)?|[.٫]\\d+)"
private const val EGC = "(?:جم|جنيه|جنية|egp|l\\.?e|ج\\.م\\.?|ج|e£|£e)"
private const val ANY_CURRENCY = "(?:جم|جنيه|جنية|egp|l\\.?e|ج\\.م\\.?|ج|e£|£e|sar|sr|ريال(?: سعودي)?|ر\\.? ?س\\.?|[a-z]{3})"

/** خانة المبلغ: رقم ثم جنيه · جنيه ثم رقم · أي واحدة فيهم (مجموعتين). */
private const val A = "($N) ?$EGC"
private const val CA = "$EGC ?($N)"
private const val AE = "(?:$EGC ?($N)|($N) ?$EGC)"

/** رقم كارت/حساب متقص («4821» · «**4821» · «...4821» · «••••4821») · تليفون (فلتر الجهاز ممكن يلزقه في الكلمة اللي بعده). */
private const val D = "[\\d*•x#.]+"
private const val PH = "[\\d*•+]+ ?"
private const val M = "(?:.+?)"
private const val NM = "(?:[^\\d]+?)"
private const val DATE = "(?:\\d{1,4}[-/.\\\\]\\d{1,2}(?:[-/.\\\\]\\d{1,4})?|[a-z]{3,9}\\.? \\d{1,2},? \\d{4})"
private const val TIME = "\\d{1,2}:\\d{2}(?::\\d{2})?(?: ?(?:am|pm|ص|م))?"
private const val DT = "(?:$DATE(?:,? (?:at )?$TIME)?|$TIME,? $DATE)"
private const val REF = "[^ ]+"
private const val HOT = "(?: ?\\(?للمزيد،? (?:اتصل|برجاء الاتصال) ب ?[\\d•]+\\)?)?"

/** آخر سطر في رسايل فودافون كاش: إعلان بيتغير (البحث) — من غير أرقام غير في رابط (والكلام بيتفحص بـ[hasShapeDoubt]). */
private const val VF_TAIL = "(?: [^\\d]{2,120}?(?: https?://\\S+)?)?"
private const val VF_WHEN = "(?: تاريخ العملية:? ?(?:$TIME )?$DATE(?: $TIME)?)?"
private const val VF_REF = "(?: رقم العملية:? ?$REF)?"

private fun e(bank: String, id: String, head: String, rest: String, direction: Direction?) = EgyptShape(bank, id, head, rest, direction)

private val EGYPT_SHAPES: List<EgyptShape> = listOf(
    // ── الأهلي المصري ──
    e("nbe", "purchase-credit-card", "تم خصم $A من بطاقة الائتمان رقم", " ?$D عند ?$M يوم ?$DT(?: الساعة ?$TIME)?(?: المتاح ?$N ?$EGC)?$HOT", OUT), // #0
    e("nbe", "purchase-debit-card", "تم خصم $A من بطاقة الخصم المباشر رقم", " ?$D عند ?$M يوم ?$DT(?: الساعة ?$TIME)?(?: المتاح ?$N ?$EGC)?$HOT", OUT), // #1 #2
    e(
        "nbe", "transfer-in-instapay", "تم اضافة تحويل لحظي ل(?:حسابكم|بطاقتكم)",
        "(?: مسبقة الدفع)?(?: رقم ?$D)? بمبلغ $A(?: من $NM)? رقم مرجعي ?$REF(?: يوم ?$DT(?: الساعة ?$TIME)?)?$HOT", IN,
    ), // #3 #5 #8
    e(
        "nbe", "transfer-out-instapay", "تم تنفيذ تحويل لحظي من حسابكم",
        "(?: رقم ?$D)? بمبلغ $A(?: الي $NM)? رقم مرجعي ?$REF(?: يوم ?$DT(?: الساعة ?$TIME)?)?$HOT", OUT,
    ), // #4 #9
    e("nbe", "transfer-in-instapay-network", "تم تحويل مبلغ $A لحسابكم المنتهي", " ب ?$D(?: من $NM)?(?: عبر شبكة المدفوعات اللحظية)?(?: مرجع رقم ?$REF)?", IN), // #6
    e("nbe", "transfer-in-instapay-en", "instant transfer of $A received from", " $NM\\.?(?: reference: ?$REF)?", IN), // #7
    // ── التجاري الدولي ──
    e(
        "cib", "purchase-credit-card", "your credit card ending with ?#? ?\\S*? ?was charged for",
        " $AE at $M on $DT(?: at $TIME)?\\.?(?: card available limit is $EGC ?$N)?", OUT,
    ), // #10
    e(
        "cib", "refund-en", "the transaction on your credit card",
        " ?#? ?$D from $M with $AE on $DT(?: at $TIME)? has been refunded", IN,
    ), // #11
    e("cib", "refund-ar", "لقد تم رد $CA علي بطاقتكم الائتمانية", " المنتهية ب ?#? ?$D من $M", IN), // #12
    e("cib", "purchase-debit-card", "تم خصم مبلغ $CA من بطاقة الخصم المباشر المنتهية", " ب ?$D عند $M في $DT(?: $TIME)?(?: ?، ?الرصيد المتاح $EGC ?$N)?", OUT), // #14
    e("cib", "credit-card-payment", "تم سداد مبلغ $A في بطاقتكم الائتمانية", " المنتهية ب ?$D بتاريخ ?$DT", null), // #15 (سداد البطاقة — الاتجاه حسب المحفظة)
    e(
        "cib", "transfer-out-instapay", "يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ $A من حسابك",
        " المنتهي ب ?$D(?: برقم مرجعي ?$REF)?(?: بتاريخ ?$DT(?: $TIME)?)?(?: للمزيد،? برجاء الاتصال ب ?[\\d•]+)?", OUT,
    ), // #16
    e(
        "cib", "transfer-in-instapay", "يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ $A الي حسابك",
        " المنتهي ب ?$D(?: من $NM)?(?: برقم مرجعي ?$REF)?(?: بتاريخ ?$DT(?: $TIME)?)?(?: للمزيد،? برجاء الاتصال ب ?[\\d•]+)?", IN,
    ), // #17
    e("cib", "salary", "عميلنا العزيز لقد تم تحويل مبلغ $CA علي حسابكم لدينا من جهة العمل", "", IN), // #18
    e("cib", "transfer-in-ipn-en", "you have received an ipn transfer of", " $CA to account ending ?$D(?: from $NM)?\\.?(?: ref no: ?$REF)?", IN), // #26
    e("cib", "transfer-in-instapay-2", "تم استلام تحويل لحظي بمبلغ $A من .+ الي حسابكم المنتهي", " ب ?$D\\.?(?: رقم المرجع ?$REF)?", IN), // #27
    // ── بنوك تانية ──
    e("banquemisr", "transfer-in-instapay", "ايداع تحويل لحظي ipn بمبلغ", " $A بحسابك رقم ?$D(?: من $NM)?(?: مرجع ?$REF)?", IN), // #28
    e(
        "hsbc", "transfer-in", "your account ending in \\S+? ?has been credited with",
        " $CA on $DT(?: from $NM)?\\.?(?: ref: ?$REF)?(?: \\(ipn inward transfer\\))?", IN,
    ), // #29
    e("kfh", "transfer-out-ipn", "ipn transfer with $CA deducted on", " $DT from your ac ending with ?$D(?: with ref# ?$REF)?\\.?(?: for info call ?[\\d•]+)?", OUT), // #30
    e("kfh", "ipn-returned", "ipn transfer dated .+ with $CA returned", "(?: with ref# ?$REF)?\\.?(?: for info call ?[\\d•]+)?", IN), // #31
    e("arabbank", "purchase-card", "a trx using card \\S+? ?from .+ for ", "(?:$AE) on $DT(?: at $TIME)?(?: gmt ?\\+? ?\\d+)?\\.?(?: available balance is $EGC ?$N)?", OUT), // #33 #34
    e("arabbank", "credit-card-credit", "تم قيد مبلغ $A لبطاقتك الائتمانية", " رقم ?#? ?$D", IN), // #35
    e(
        "breadfast", "card-top-up", "you received $CA on .+ to your card ending in",
        " ?$D\\.?(?: ?for details, please contact breadfast customer support via the app)?", IN,
    ), // #36
    // ── فودافون كاش ──
    e(
        "vodafone-cash", "receive-ar", "تم استلام مبلغ $A من رقم ${PH}المسجل باسم",
        " $NM(?: علي رقم محفظتك ?$PH)?\\.?(?: رصيدك الحالي:? ?$N ?$EGC)?$VF_WHEN$VF_REF$VF_TAIL", IN,
    ), // #37
    // #38 — التاريخ والساعة الأول («2026-09-14 14:22» · «Sep 14, 2026 2:22:05 PM»)، مش أي كلام («Money Request:»)
    e(
        "vodafone-cash", "receive-en", "(?:$DATE(?:,? $TIME)?|$TIME): received $CA from ${PH}to mobile account number",
        // فلتر الجهاز بيقص رقم المرجع الطويل **ومعاه المسافة** («Ref: ••••0427Available Balance»)
        " ?$PH\\.?(?: ref: ?$REF)?(?: ?available balance: ?$N)?", IN,
    ),
    e(
        "vodafone-cash", "send", "تم تحويل $A لرقم ${PH}مصاريف الخدمة",
        "(?: ?$N ?$EGC)? رصيد حسابك في فودافون كاش الحالي ?$N\\.?$VF_WHEN$VF_REF$VF_TAIL", OUT,
    ), // #39
    e(
        "vodafone-cash", "cash-out", "تم سحب $A بنجاح\\.? رصيد حسابك في فودافون كاش",
        " الحالي ?$N ?$EGC\\.?$VF_WHEN$VF_REF\\.?$VF_TAIL", OUT,
    ), // #41
    e(
        "vodafone-cash", "recharge-en", "you have successfully recharged $A to the balance of",
        " ?$PH;? ?your current vodafone cash balance is ?$N ?$EGC;? ?trx date: ?$DT trx id ?$REF", OUT,
    ), // #45
    e(
        "vodafone-cash", "recharge-ar", "تم شحن رصيد موبايلك ب ?$N بنجاح وخصم",
        " ($N)(?: ?$EGC)? من محفظتك شاملة الضريبة(?: ?$EGC\\.?)? رصيد حسابك في فودافون كاش الحالي ?$N", OUT,
    ), // #46 (خانة المبلغ = المخصوم)
    e("vodafone-cash", "receive-en-2", "you have received $A from ${PH}\\.? ?transaction id", ": ?$REF(?: $DT)?(?: new balance: ?$N ?$EGC)?", IN), // #49
    // ── محافظ تانية (e& money · أورانج كاش · وي باي) ──
    e(
        "eg-wallet", "receive", "تم استلام (?:مبلغ )?$A من رقم ${PH}رصيدك الحالي",
        " ?$N ?$EGC(?: (?:رقم المرجع|كود العملية|رقم العملية) ?$REF)?", IN,
    ), // #50 #52 #54
    e("eg-wallet", "send", "تم تحويل $A لرقم ${PH}رصيدك الحالي", " ?$N ?$EGC(?: (?:ref:|رقم العملية) ?$REF)?", OUT), // #51 #53 #55
    // ── أشكال عامة في البحث ──
    e("eg-generic", "transfer-in-ipn-en", "dear customer, ipn transfer of $A credited to account", " ?$D(?: from $NM)?\\.?(?: reference: ?$REF)?", IN), // #56
    e("eg-generic", "transfer-in-instapay-ar", "تم اضافة مبلغ $A الي حسابك المنتهي", " ب ?$D(?: من $NM)? تحويل لحظي(?: مرجع: ?$REF)?", IN), // #57
    // ── QNB مصر (عينات المالك الحقيقية §40.3 — مش في المستودع) ──
    e("qnb", "transfer-out-ipn", "ipn transfer sent with amount of", " $CA (?:from|on) ?$D on $DT(?: at $TIME)?\\.?(?: ref# ?$REF)?\\.?(?: for more details call ?\\d+)?", OUT),
    e("qnb", "transfer-in-ipn", "ipn transfer received with amount of", " $CA (?:from|on) ?$D on $DT(?: at $TIME)?\\.?(?: ref# ?$REF)?\\.?(?: for more details call ?\\d+)?", IN),
    e(
        "qnb", "debit-card", "your debit card \\S+? ?had a successful transaction of",
        " $CA ?@ ?[^,]+,(?: ?your available bal\\.? ?$EGC ?$N)?(?: for lost/stolen card call ?\\d+)?", OUT,
    ),
)

/** عدد القوالب (للاختبار). */
internal val EGYPT_SHAPE_COUNT: Int get() = EGYPT_SHAPES.size

/** أول الجملة على قالب مصري معروف (أي اتجاه) — فلتر الجهاز (`SmsVocabulary`) وقارئ مصر (كارت مصري اتخصم بالريال) بيستعملوها. */
internal fun hasEgyptianKnownHead(body: String): Boolean {
    val key = shapeKey(body, dropColons = false)
    return EGYPT_SHAPES.any { it.headAnyCurrency.containsMatchIn(key) }
}

/** أول الجملة على قالب الأهلي المصري (تاريخه «يوم MM-DD» شهر-يوم — `SmsDates.kt`). */
internal fun isNbeSentence(body: String): Boolean {
    val key = shapeKey(body, dropColons = false)
    return EGYPT_SHAPES.any { it.bank == "nbe" && it.headAnyCurrency.containsMatchIn(key) }
}

private fun egp(raw: String): Halalas? {
    val number = raw.replace('٬', ',').replace('٫', '.')
    return tryParseMoney(if (number.startsWith(".")) "0$number" else number, Currency.EGP)
}

/**
 * رسالة مصر اللي القارئ قبلها على [direction] بمبلغ [amount]: **الجملة كلها** على قالب معروف · اتجاهه نفس القارئ · الرقم اللي في خانة
 * المبلغ = [amount] (null = ما بيتفحصش) · مفيش كلمة حالة أو عكس ⇒ [SmsShape.KnownShape]، غير كده تستنى.
 */
internal fun egyptShape(body: String, direction: Direction, amount: Halalas? = null): SmsShape {
    val key = shapeKey(body, dropColons = false)
    if (hasShapeDoubt(key)) return SmsShape.KeywordFallback
    for (shape in EGYPT_SHAPES) {
        val m = shape.full.matchEntire(key) ?: continue
        if (shape.direction != null && shape.direction != direction) return SmsShape.KeywordFallback
        val slot = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.let(::egp)
        if (amount != null && slot != amount) return SmsShape.KeywordFallback
        return SmsShape.KnownShape(shape.bank, shape.id)
    }
    return SmsShape.KeywordFallback
}
