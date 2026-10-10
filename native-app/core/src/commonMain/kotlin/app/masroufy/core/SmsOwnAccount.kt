package app.masroufy.core

/**
 * آخر 4 أرقام **حسابك أو كارتك** اللي الرسالة عنها — قرار §75-11: «بنك واحد بحسابين ⇒ التفرقة بآخر ٤ أرقام». رقم الحساب لو مكتوب، وإلا
 * رقم الكارت؛ وأقل من 4 أرقام ⇒ مش مكتوب. الجولة السادسة: رسالة أرقامها حساب تاني من حسابات المالك ⇒ بتستنى (`ReviewSmsInbox`).
 *
 * نفس الكلمة معناها بيتقلب مع الاتجاه: في الصادر «من:» = حسابك و«إلى:» = الطرف التاني؛ في الوارد العكس («إلى:/لحساب/لـ» = حسابك).
 * «IBAN:» · «Account:» · «الحساب:» (من غير اتجاه) = حسابك في الاتجاهين. مصر: «لحسابكم رقم» · «حسابك المنتهي ب» · «account ending» · «رقم محفظتك».
 *
 * **الجولة التامنة** (المراجعة العدائية الرابعة — حساب تاني اتسجل في محفظة البنك المربوط): «Account: **4417» · «الحساب: **4417» ·
 * «من حساب: 4417*» · «IBAN: SA** **** 4417» · «From: SA****4417» · «المنتهي ب 4417» (من غير تطويل) ما كانتش بتتقري، فالرسالة اللي عن
 * حسابك التاني كانت بتتسجل لوحدها في المحفظة المربوطة. دلوقتي القيمة = أي رقم متقص (بادئة SA/IBAN · مسافات جواه · نجوم قبله أو بعده)
 * وآخر 4 أرقامه، و**سطر الحساب بيغلب سطر الكارت** («Card: *9001\nAccount: **4417» = 4417).
 */

private val OM = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
private val OI = setOf(RegexOption.IGNORE_CASE)
private const val H = "[ \\t]"

/** قيمة حساب متقصة: بادئة SA/IBAN (اختياري) · نجوم/•/x/# · أرقام · مسافات جواه — وبعدها بالكتير « - نوع الحساب» أو «;…». */
private const val VALUE = "((?:SA|IBAN)?$H*[*•xX#\\d][\\d*•xX# \\t]*?)$H*(?:[-–;,][^\\n]*)?$H*$"

/** لابل الحساب من غير اتجاه («حساب: **1188» · «Account:» · «Account number:» · «Acc:» · «IBAN:» · «خصمت من حساب»). */
private const val NEUTRAL =
    "حساب|الحساب|في$H*حساب|رقم$H*الحساب|Acc(?:ount)?(?:$H*(?:number|no\\.?))?|IBAN|[اآإ]يبان|ال[اآإ]يبان|خصمت$H*من$H*حساب"

/** الصادر بس: «من:1188» · «من حساب:» · «From :» · «From Account:». */
private const val OUT_ONLY = "من|from|من$H*حساب|from$H*account|debit$H*account"

/** الوارد بس: «الى:1188» · «الى: ***6604; VISA» · «لحساب:» · «لـ1188» · «To:». */
private const val IN_ONLY = "[اإ]ل[ىي]|to|لحساب|[اإ]ل[ىي]$H*حساب|لـ|to$H*account|credit$H*account"

private fun accountLine(labels: String) = Regex("^$H*(?:$labels)$H*[:：]?$H*$VALUE", OM)

private val OUT_ACCOUNT = accountLine("$NEUTRAL|$OUT_ONLY")
private val IN_ACCOUNT = accountLine("$NEUTRAL|$IN_ONLY")

/** مصر — جوه الجملة: «لحسابكم رقم 1188» · «بحسابك رقم ...1188» · «حسابك المنتهي ب ****1188» (بتطويل أو من غيره) · «account ending (in) 1188». */
private val EG_ACCOUNT = Regex(
    "(?:حسابكم?$H*رقم|حسابكم?$H*المنتهي$H*بـ?|account$H+ending(?:$H+(?:in|with))?|credited$H+to$H+account|Account$H+Number)$H*[#*•xX.]*$H*(\\d{4})(?!\\d)",
    OI,
)

/** فودافون كاش: «على رقم محفظتك 0100…0888» (أو بعد القص «••••0888»). */
private val WALLET = Regex("رقم$H*محفظتك$H*[*•]*\\d*?(\\d{4})(?!\\d)")

/** الكارت في أي اتجاه: «بطاقة:6604» · «عبر:6604;» · «Card: *6604» · «Via: *6604» · «مدى *6604» · «card ending with#6604» · «Card XXXX6604». */
private val CARD = Regex("(?:بطاق\\S*|عبر|مدى|(?<![A-Za-z])(?:Card|Via|By)(?![a-z]))[^\\n\\d]{0,30}?[*•xX#]*(\\d{4})(?!\\d)", OI)

/** آخر 4 أرقام القيمة المتقصة (4 أرقام على الأقل)، أو null. */
private fun lastFourOf(value: String): String? = value.filter { it in '0'..'9' }.takeIf { it.length >= 4 }?.takeLast(4)

internal fun ownLast4Of(body: String, direction: Direction): String? = ownAccountLast4Of(body, direction) ?: CARD.find(body)?.groupValues?.get(1)

/**
 * آخر 4 أرقام **حسابك** بس (سطر الحساب · «حسابك المنتهي ب» · رقم المحفظة) — **مش الكارت**. مراجعة S1: التوزيع على المحافظ بآخر 4 أرقام
 * (§75-11 «بنك واحد بحسابين») بيبص على ده بس — آخر 4 أرقام كارت من بنك تاني ممكن تبقى زي آخر 4 أرقام حساب في البنك ده بالصدفة.
 */
internal fun ownAccountLast4Of(body: String, direction: Direction): String? =
    (if (direction == Direction.OUT) OUT_ACCOUNT else IN_ACCOUNT).findAll(body).firstNotNullOfOrNull { lastFourOf(it.groupValues[1]) }
        ?: EG_ACCOUNT.find(body)?.groupValues?.get(1)
        ?: WALLET.find(body)?.groupValues?.get(1)
