package app.masroufy.core

/**
 * عقد C0 — نصوص الشريحة S2: الرسوم عملية لوحدها · السحب للكاش · الاسترداد (§77-B · §75-4 · §75-6).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (§66 — الإنجليزي كتابة Claude ومستني مراجعة المالك). الشريحة بتملك الملف ده وبتضيف
 * مفاتيحها تحت علامتها في `TextKeys.kt`.
 * `SMS_WAIT_CASH_ADVANCE` = سبب انتظار سحب كاش **ما بيتنقلش** لمحفظة الكاش لوحده (من كارت ائتمان أو من صرّاف برّه البلد) ·
 * `SMS_FEE_SOURCE_REASON` = سبب سجل المصدر بتاع عملية «رسوم بنكية» اللي اتعملت من نفس الرسالة (بيتخزن جملة جاهزة زي باقي الأسباب — §40.1).
 * سبب انتظار السحب العادي (`SMS_WAIT_CASH_WITHDRAWAL`) في `TextsSmsAuto.kt`.
 */
internal val MSA_SMS_FEE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_CASH_ADVANCE to
        "سحب نقدي من بطاقة ائتمانية أو من صراف خارج البلد: لا يُنقل تلقائيًا إلى محفظة النقد، ولم يُسجَّل تلقائيًا. راجعه وأكّده",
    TextKey.SMS_FEE_SOURCE_REASON to "رسوم بنكية مكتوبة في الرسالة نفسها، سُجّلت عملية مستقلة بجانب العملية الأصلية",
)

internal val EGYPTIAN_SMS_FEE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_CASH_ADVANCE to
        "سحب كاش من كارت ائتمان أو من صرّاف برّه البلد: ما بيتنقلش لوحده لمحفظة الكاش، وما اتسجلش لوحده. راجعه وأكّده",
    TextKey.SMS_FEE_SOURCE_REASON to "رسوم بنكية مكتوبة في نفس الرسالة، اتسجلت عملية لوحدها جنب العملية الأصلية",
)

internal val ENGLISH_SMS_FEE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_CASH_ADVANCE to
        "Cash taken from a credit card or from an ATM abroad: it isn't moved to your cash wallet automatically, so it wasn't recorded automatically. Check it and confirm",
    TextKey.SMS_FEE_SOURCE_REASON to "Bank fees written in the same message, recorded as a separate transaction next to the original one",
)
