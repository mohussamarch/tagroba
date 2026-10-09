package app.masroufy.core

/**
 * §77-D (قرار المالك 2026-10-09 — «تم رد المبلغ» / «التحويل رجع»): العملية اللي **رجعت** نوعها [SmsKind.RETURNED] — بتدوّر على
 * العملية الأصلية برقمها المرجعي وتلغيها، ولو ما لقيتهاش ⇒ «استرداد» مقترح ويسأل (`ReturnedSmsEffect`).
 *
 * **اللي بيبقى RETURNED (حدود كلام المالك):** تحويل رجع («التحويل رجع» · «حوالة مرتجعة/معادة» · «حوالة صادرة مرتجعة» · «رجع التحويل» ·
 * «تم رد مبلغ التحويل» · «IPN transfer … returned» بيت التمويل · «Transfer returned/reversed») · والبنك بيقول إن الفلوس **اترجعت**:
 * «لقد تم رد …» و«has been refunded» (التجاري الدولي — اللي كانوا سؤال (هـ) في §75.1 واتحسم بـ§77-D) · «اترد».
 * **الكلام لازم في أول سطر** (العنوان أو جملة الرسالة الواحدة) — «راجع تطبيق البنك» أو «ملاحظة: تم رد السلفة» في سطر تحت مش رجوع.
 *
 * **بيفضل REFUND (§75-6 — «يقترح استرداد ويستنى تأكيده»، والمالك رفض «لوحده + ربط بالشراء»):** أي عنوان استرداد أو عكس من محل —
 * «استرداد شراء» · «استرجاع» · «مرتجع» · «Refund» · كاش باك · **«عكس عملية» · «Purchase Reversal» · «حوالة عكسية» / «Reverse Transaction»**
 * (الإنماء: «… من البائع»). دول كانوا RETURNED في أول نسخة من الشريحة (بيتسجلوا لوحدهم ويلغوا الشراء من غير سؤال) — ده تفسير مش من
 * كلام المالك، فرجعوا استرداد بيستنى؛ ولو المالك عايزهم يلغوا الشراء زي «تم رد» ده سؤال مفتوح ليه.
 * و«تم رد/اترد» على **حوالة واردة** (`TRANSFER_IN`) مش رجوع: «تم رد السلفة» = شخص بيسدد (§75-9). الصادر ما بيتغيرش، ولا السحب والإيداع
 * والراتب وبين حساباتك وسداد الكارت. القراية نفسها (المبلغ والاتجاه والتاريخ والشكل) **ما بتتغيرش** — ملفات المرجع زي ما هي.
 */
private val I = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"

/** مسافة جوه نفس السطر بس (`\s` كانت بتعدّي السطر: «حوالة واردة\nراجع …» اتقرت «حوالة راجعة»). */
private const val GAP = "[ \\t]+"

/** تحويل رجع. */
private val TRANSFER_RETURNED: List<Regex> = listOf(
    // «التحويل رجع» · «الحوالة مرتجعة» · «حوالة صادرة مرتجعة» · «حوالة معادة»
    Regex(
        "(?<![$AR])(?:ال)?(?:تحويل|حوالة)(?:$GAP(?:ال)?(?:صادرة|واردة|محلية|دولية|داخلية|سريعة|لحظية|صادر|وارد|محلي|دولي|داخلي|لحظي))?" +
            "$GAP(?:رجع|رجعت|ارتد|ارتدت|مرتجعة|مرتجع|معادة)(?![$AR])",
    ),
    // «رجع التحويل» · «تم رد/إرجاع مبلغ التحويل» · «استرداد مبلغ الحوالة»
    Regex("(?<![$AR])(?:رجع|رجعت|ارتد|رد|إرجاع|ارجاع|استرداد|استرجاع)$GAP(?:مبلغ$GAP)?(?:ال)?(?:تحويل|حوالة)(?![$AR])"),
    // «IPN transfer dated … returned» · «Transfer … was returned/reversed»
    Regex("(?<![A-Za-z])(?:IPN|transfer)(?![A-Za-z])[^\\n]{0,120}?(?<![A-Za-z])(?:returned|reversed)(?![A-Za-z])", I),
    Regex("(?<![A-Za-z])transfer${GAP}reversal(?![A-Za-z])", I),
    Regex("(?<![A-Za-z])(?:returned|reversed)$GAP(?:transfer|payment)(?![A-Za-z])", I),
)

/** البنك بيقول إن الفلوس اترجعت: «لقد تم رد EGP…» · «تم رد المبلغ» · «اترد» · «has been refunded». */
private val BANK_RETURNED: List<Regex> = listOf(
    Regex("(?<![$AR])تم${GAP}رد(?![$AR])"),
    Regex("(?<![$AR])اترد(?:ت)?(?![$AR])"),
    Regex("(?<![A-Za-z])has${GAP}been${GAP}refunded(?![A-Za-z])", I),
)

/** أول السطر عنوان استرداد/عكس من محل (§75-6) ⇒ استرداد مهما كان الكلام بعده («استرداد شراء … تم رد المبلغ لحسابك»). */
private val MERCHANT_REFUND_HEAD = Regex(
    "^(?:استرداد|استرجاع|إرجاع|ارجاع|مرتجع|تصحيح|كاش[ \\t]*باك|عكس|(?:Notification[ \\t]*:[ \\t]*)?Refund|Cash[ \\t]*back|Purchase${GAP}Reversal|Reverse(?![A-Za-z]))",
    I,
)

/** أنواع ما بتبقاش «رجعت» حتى لو الكلام فيه كده (فلوس ليها معنى تاني). */
private val NEVER_RETURNED = setOf(
    SmsKind.SALARY, SmsKind.CASH_DEPOSIT, SmsKind.CASH_WITHDRAWAL, SmsKind.PURCHASE_WITH_CASH, SmsKind.OWN_TRANSFER, SmsKind.CARD_PAYMENT,
)

/**
 * أول سطر الرسالة بيقول إن عملية رجعت (من غير شرط الاتجاه ولا النوع) — للاختبارات وللتقارير. **التحويل اللي رجع الأول**: «استرداد/إرجاع
 * مبلغ الحوالة» تحويل رجع حتى لو أوله كلمة استرداد؛ وبعده عنوان استرداد من محل بيمنع «تم رد» اللي بعده.
 */
fun saysReturned(body: String): Boolean {
    val head = smsTitleLine(body)
    return TRANSFER_RETURNED.any { it.containsMatchIn(head) } ||
        (!MERCHANT_REFUND_HEAD.containsMatchIn(head) && BANK_RETURNED.any { it.containsMatchIn(head) })
}

/** نوع الرسالة بعد §77-D: وارد وأول سطره «رجعت» ⇒ [SmsKind.RETURNED]، وإلا النوع زي ما هو. */
internal fun refineSmsKind(body: String, kind: SmsKind, direction: Direction): SmsKind {
    if (direction != Direction.IN || kind in NEVER_RETURNED) return kind
    val head = smsTitleLine(body)
    return when {
        TRANSFER_RETURNED.any { it.containsMatchIn(head) } -> SmsKind.RETURNED
        MERCHANT_REFUND_HEAD.containsMatchIn(head) -> kind
        // «تم رد السلفة» على حوالة واردة من شخص = سداد (§75-9) — مش عملية رجعت
        kind != SmsKind.TRANSFER_IN && BANK_RETURNED.any { it.containsMatchIn(head) } -> SmsKind.RETURNED
        else -> kind
    }
}
