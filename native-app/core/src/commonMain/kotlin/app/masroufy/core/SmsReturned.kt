package app.masroufy.core

/**
 * §77-D (قرار المالك 2026-10-09 — «تم رد المبلغ» / «التحويل رجع»): العملية اللي **رجعت** نوعها [SmsKind.RETURNED] — بتدوّر على
 * العملية الأصلية برقمها المرجعي وتلغيها، ولو ما لقيتهاش ⇒ «استرداد» مقترح ويسأل (`ReturnedSmsEffect`).
 *
 * **تفسير الشريحة S3 (المالك يقدر يغيّره):** RETURNED = تحويل رجع («التحويل رجع» · «حوالة مرتجعة/معادة/عكسية» · «IPN transfer … returned»
 * بيت التمويل) · «تم رد …» (التجاري الدولي «لقد تم رد EGP…») · «has been refunded» (التجاري الدولي) · عكس العملية («عكس عملية» إس تي سي ·
 * «Purchase Reversal» · «حوالة عكسية» الإنماء والعنوان الموحّد). **الاسترداد من محل بيفضل REFUND** (§75-6): «استرداد شراء» · «استرجاع» ·
 * «إرجاع» و«مرتجع» لوحدهم · «Refund» · كاش باك. الصادر ما بيتغيرش، ولا السحب والإيداع والراتب وبين حساباتك وسداد الكارت.
 * القراية نفسها (المبلغ والاتجاه والتاريخ والشكل) **ما بتتغيرش** — ملفات المرجع زي ما هي.
 */
private val I = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"

private val RETURNED_WORDING: List<Regex> = listOf(
    // «التحويل رجع» · «الحوالة مرتجعة» · «حوالة صادرة مرتجعة» · «حوالة معادة» · «حوالة عكسية»
    Regex(
        "(?<![$AR])(?:ال)?(?:تحويل|حوالة)(?:\\s+(?:ال)?(?:صادرة|واردة|محلية|دولية|داخلية|سريعة|لحظية|صادر|وارد|محلي|دولي|داخلي|لحظي))?" +
            "\\s+(?:رجع|رجعت|ارتد|ارتدت|مرتجعة|مرتجع|معادة|راجعة|راجع|عكسية)(?![$AR])",
    ),
    // «رجع التحويل» · «تم رد/إرجاع مبلغ التحويل» · «استرداد مبلغ الحوالة»
    Regex("(?<![$AR])(?:رجع|رجعت|ارتد|رد|إرجاع|ارجاع|استرداد|استرجاع)\\s+(?:مبلغ\\s+)?(?:ال)?(?:تحويل|حوالة)(?![$AR])"),
    // «تم رد المبلغ» · «لقد تم رد EGP75.50» · «اترد المبلغ» (مصري)
    Regex("(?<![$AR])تم\\s+رد(?![$AR])"),
    Regex("(?<![$AR])اترد(?:ت)?(?![$AR])"),
    // «عكس عملية» · «عكس العملية»
    Regex("(?<![$AR])عكس\\s+(?:ال)?عملية(?![$AR])"),
    Regex("(?<![A-Za-z])has\\s+been\\s+refunded(?![A-Za-z])", I),
    // «IPN transfer dated … returned» · «Transfer … was returned/reversed»
    Regex("(?<![A-Za-z])(?:IPN|transfer)(?![A-Za-z])[^\\n]{0,120}?(?<![A-Za-z])(?:returned|reversed)(?![A-Za-z])", I),
    Regex("(?<![A-Za-z])(?:purchase|transaction|transfer|payment)\\s+reversal(?![A-Za-z])", I),
    // العنوان الموحّد «حوالة عكسية» بالإنجليزي
    Regex("(?<![A-Za-z])reverse(?:d)?\\s+(?:transaction|transfer)(?![A-Za-z])", I),
    Regex("(?<![A-Za-z])returned\\s+(?:transfer|payment)(?![A-Za-z])", I),
)

/** أنواع ما بتبقاش «رجعت» حتى لو الكلام فيه كده (فلوس ليها معنى تاني). */
private val NEVER_RETURNED = setOf(
    SmsKind.SALARY, SmsKind.CASH_DEPOSIT, SmsKind.CASH_WITHDRAWAL, SmsKind.PURCHASE_WITH_CASH, SmsKind.OWN_TRANSFER, SmsKind.CARD_PAYMENT,
)

/** الرسالة بتقول إن عملية رجعت (من غير شرط الاتجاه) — للاختبارات وللتقارير. */
fun saysReturned(body: String): Boolean = RETURNED_WORDING.any { it.containsMatchIn(body) }

/** نوع الرسالة بعد §77-D: وارد وكلامه «رجعت» ⇒ [SmsKind.RETURNED]، وإلا النوع زي ما هو. */
fun refineSmsKind(body: String, kind: SmsKind, direction: Direction): SmsKind = when {
    direction != Direction.IN || kind in NEVER_RETURNED -> kind
    saysReturned(body) -> SmsKind.RETURNED
    else -> kind
}
