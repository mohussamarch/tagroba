package app.masroufy.core

/**
 * نصوص الشريحة S3: العملية اللي رجعت (§77-D) وسؤال المبلغ المحلي للشراء الأجنبي (§75-12). أسئلة جوه التطبيق بس (مش إشعار جوال) —
 * `RETURNS_ASK_*` نص السؤال على العملية/الرسالة: `{0}` في «نلغي الاتنين؟» = وصف العملية الأصلية (الشاشة بتكتبه) وفي سؤال الأجنبي =
 * المبلغ الأجنبي بعملته. فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (كتابة Claude ومستني مراجعة المالك زي باقي الجدول §40).
 */
internal val MSA_RETURNS_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.RETURNS_ASK_REFUND to "هل هذا المبلغ الداخل استرداد عن شيء دفعته؟",
    TextKey.RETURNS_ASK_REVERSAL to "يبدو أن هذا المبلغ عائد من عملية سابقة ({0}). هل نلغي العمليتين؟",
    TextKey.RETURNS_ASK_FOREIGN to "عملية بعملة أجنبية ({0}): كم المبلغ بعملة محفظتك؟",
    TextKey.RETURNS_REFUND_NOT_PENDING to "هذه العملية لا تنتظر إجابة عن الاسترداد",
    TextKey.RETURNS_REVERSAL_NOT_POSSIBLE to
        "لا يمكن إلغاء العمليتين معًا: يلزم أن تكونا في المحفظة نفسها وبالعملة والمبلغ نفسيهما وباتجاهين متعاكسين، وأن تسبق الأصلية بستين يومًا على الأكثر وألا تكون ملغاة",
    TextKey.RETURNS_FOREIGN_NOT_FOUND to "هذه الرسالة لم تعد تنتظر المبلغ المحلي",
    TextKey.RETURNS_FOREIGN_AMOUNT_POSITIVE to "يجب أن يكون المبلغ المحلي أكبر من صفر",
    TextKey.RETURNS_FOREIGN_WALLET_NEEDED to "اختر المحفظة التي خُصم منها المبلغ",
)

internal val EGYPTIAN_RETURNS_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.RETURNS_ASK_REFUND to "الفلوس اللي دخلت دي استرداد عن حاجة دفعتها؟",
    TextKey.RETURNS_ASK_REVERSAL to "شكل المبلغ ده راجع من عملية قبل كده ({0}). نلغي العمليتين؟",
    TextKey.RETURNS_ASK_FOREIGN to "عملية بعملة أجنبية ({0}): المبلغ كام بعملة محفظتك؟",
    TextKey.RETURNS_REFUND_NOT_PENDING to "العملية دي مش مستنية إجابة عن الاسترداد",
    TextKey.RETURNS_REVERSAL_NOT_POSSIBLE to
        "مينفعش نلغي العمليتين مع بعض: لازم يبقوا في نفس المحفظة وبنفس العملة والمبلغ واتجاههم عكس بعض، والأصلية قبلها بـ60 يوم بالكتير ومش ملغية",
    TextKey.RETURNS_FOREIGN_NOT_FOUND to "الرسالة دي مش مستنية المبلغ المحلي خلاص",
    TextKey.RETURNS_FOREIGN_AMOUNT_POSITIVE to "المبلغ المحلي لازم يبقى أكبر من صفر",
    TextKey.RETURNS_FOREIGN_WALLET_NEEDED to "اختار المحفظة اللي المبلغ اتخصم منها",
)

internal val ENGLISH_RETURNS_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.RETURNS_ASK_REFUND to "Is this money coming in a refund for something you paid for?",
    TextKey.RETURNS_ASK_REVERSAL to "This looks like money coming back from an earlier transaction ({0}). Cancel both?",
    TextKey.RETURNS_ASK_FOREIGN to "A transaction in a foreign currency ({0}): how much was it in your wallet's currency?",
    TextKey.RETURNS_REFUND_NOT_PENDING to "This transaction isn't waiting for a refund answer",
    TextKey.RETURNS_REVERSAL_NOT_POSSIBLE to
        "These two can't cancel each other: they need the same wallet, currency and amount, opposite directions, and the original no more than 60 days earlier and not already cancelled",
    TextKey.RETURNS_FOREIGN_NOT_FOUND to "This message is no longer waiting for a local amount",
    TextKey.RETURNS_FOREIGN_AMOUNT_POSITIVE to "The local amount must be more than zero",
    TextKey.RETURNS_FOREIGN_WALLET_NEEDED to "Choose the wallet the amount was taken from",
)
