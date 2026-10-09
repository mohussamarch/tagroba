package app.masroufy.core

/**
 * نصوص الشريحة S2: الرسوم والسحب للكاش والاسترداد (§77-B · §75-4 · §75-6).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (OVERRIDES §66 — الإنجليزي كتابة Claude ومستني مراجعة المالك).
 * `SMS_WAIT_NO_CASH_WALLET` = سبب انتظار السحب من الصرّاف لما البلد مالهاش محفظة كاش **واحدة** · `SMS_FEE_SOURCE_REASON` = سبب سجل
 * المصدر بتاع عملية «رسوم بنكية» اللي اتعملت من نفس الرسالة (بيتخزن جملة جاهزة زي باقي الأسباب — §40.1).
 */
internal val MSA_SMS_FEE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_NO_CASH_WALLET to
        "سحب نقدي: يُنقل تلقائيًا إلى محفظة النقد، لكن لا توجد في هذا البلد محفظة نقد واحدة (لا توجد، أو توجد أكثر من واحدة)؛ لذلك لم يُسجَّل تلقائيًا. راجعه وأكّده",
    TextKey.SMS_FEE_SOURCE_REASON to "رسوم بنكية مكتوبة في الرسالة نفسها، سُجّلت عملية مستقلة بجانب العملية الأصلية",
)

internal val EGYPTIAN_SMS_FEE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_NO_CASH_WALLET to
        "سحب كاش: بيتنقل لوحده لمحفظة الكاش، بس مفيش في البلد دي محفظة كاش واحدة (مفيش خالص، أو فيه أكتر من واحدة)؛ عشان كده ما اتسجلش لوحده. راجعه وأكّده",
    TextKey.SMS_FEE_SOURCE_REASON to "رسوم بنكية مكتوبة في نفس الرسالة، اتسجلت عملية لوحدها جنب العملية الأصلية",
)

internal val ENGLISH_SMS_FEE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_NO_CASH_WALLET to
        "A cash withdrawal: it moves to your cash wallet automatically, but this country doesn't have exactly one cash wallet (none, or more than one), so it wasn't recorded automatically. Check it and confirm",
    TextKey.SMS_FEE_SOURCE_REASON to "Bank fees written in the same message, recorded as a separate transaction next to the original one",
)
