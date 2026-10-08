package app.masroufy.core

/**
 * آخر 4 أرقام **حسابك أو كارتك** اللي الرسالة عنها — قرار §75-11: «بنك واحد بحسابين ⇒ التفرقة بآخر ٤ أرقام». **دليل بس** للي
 * بعد القارئ (ربط آخر 4 بمحفظة لسه ما اتبناش): رقم الحساب لو مكتوب، وإلا رقم الكارت؛ وأقل من 4 أرقام ⇒ مش مكتوب.
 *
 * نفس الكلمة معناها بيتقلب مع الاتجاه: في الصادر «من:» = حسابك و«إلى:» = الطرف التاني؛ في الوارد العكس («إلى:/لحساب/لـ» = حسابك).
 * «IBAN:» في رسالة دي 360 الواردة = حسابك. مصر: «لحسابكم رقم» · «حسابك المنتهي بـ» · «account ending» · «رقم محفظتك».
 */

private val OM = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
private val OI = setOf(RegexOption.IGNORE_CASE)
private const val H = "[ \\t]"
private const val MASK = "[*•xX.]*"

/** الرقم آخر حاجة في السطر — أو بعده «;» ونوع الكارت. */
private const val END = "(?:$H*[;,][^\\n]*)?$H*$"

/** الصادر: «من:1188» · «من حساب:» · «حساب: **1188» · «Acc:» · «Account number:» · «From :» · «From Account:» · «خصمت من حساب». */
private val OUT_ACCOUNT = Regex(
    "^$H*(?:من|from|From$H*Account|Acc|Account$H*number|حساب|من$H*حساب|خصمت$H*من$H*حساب)$H*[:：]?$H*$MASK(\\d{4})$END",
    OM,
)

/** الوارد: «الى:1188» · «الى: ***6604; VISA» (إس تي سي) · «لحساب:» · «لـ1188» · «IBAN:» (دي 360) · «في حساب:» · «حساب *1188». */
private val IN_ACCOUNT = Regex("^$H*(?:الى|إلى|to|لحساب|لـ|IBAN|في$H*حساب|حساب|Account)$H*[:：]?$H*$MASK(\\d{4})$END", OM)

/** مصر — جوه الجملة: «لحسابكم رقم 1188» · «بحسابك رقم ...1188» · «حسابك المنتهي بـ ****1188» · «account ending (in) 1188». */
private val EG_ACCOUNT = Regex(
    "(?:حسابكم?$H*رقم|حسابكم?$H*المنتهي$H*بـ|account$H+ending(?:$H+in)?|credited$H+to$H+account|Account$H+Number)$H*[#*•xX.]*$H*(\\d{4})(?!\\d)",
    OI,
)

/** فودافون كاش: «على رقم محفظتك 0100…0888» (أو بعد القص «••••0888»). */
private val WALLET = Regex("رقم$H*محفظتك$H*[*•]*\\d*?(\\d{4})(?!\\d)")

/** الكارت في أي اتجاه: «بطاقة:6604» · «عبر:6604;» · «Card: *6604» · «Via: *6604» · «مدى *6604» · «card ending with#6604» · «Card XXXX6604». */
private val CARD = Regex("(?:بطاق\\S*|عبر|مدى|(?<![A-Za-z])(?:Card|Via|By)(?![a-z]))[^\\n\\d]{0,30}?[*•xX#]*(\\d{4})(?!\\d)", OI)

internal fun ownLast4Of(body: String, direction: Direction): String? =
    (if (direction == Direction.OUT) OUT_ACCOUNT else IN_ACCOUNT).find(body)?.groupValues?.get(1)
        ?: EG_ACCOUNT.find(body)?.groupValues?.get(1)
        ?: WALLET.find(body)?.groupValues?.get(1)
        ?: CARD.find(body)?.groupValues?.get(1)
