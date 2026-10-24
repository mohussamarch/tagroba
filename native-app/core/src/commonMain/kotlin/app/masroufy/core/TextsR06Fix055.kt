package app.masroufy.core

/**
 * fix055 (فحص 0.5.4 + OVERRIDES §117) — مفاتيح FX55_ في [R06Key]:
 * - «مَن هذا؟» للتحويل **الخارج**: «إلى X» بدل «من X» (ملاحظة 4).
 * - §117-2: سطر تحت «المكسب المحقق» للاستثمار كله — البيعات اللي تكلفة أصلها مش متسجلة ما اتحسبتش (واحدة · اتنين · 3-10 · 11+).
 */
internal val MSA_R06_FIX055_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.FX55_PARTY_KIND_TITLE_OUT to "هذا المبلغ إلى {0}",
    R06Key.FX55_GAIN_SKIPPED_ONE to "بيعة واحدة غير محسوبة (التكلفة غير مسجلة)",
    R06Key.FX55_GAIN_SKIPPED_TWO to "بيعتان غير محسوبتين (التكلفة غير مسجلة)",
    R06Key.FX55_GAIN_SKIPPED_FEW to "{0} بيعات غير محسوبة (التكلفة غير مسجلة)",
    R06Key.FX55_GAIN_SKIPPED_MANY to "{0} بيعة غير محسوبة (التكلفة غير مسجلة)",
)

internal val EGYPTIAN_R06_FIX055_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.FX55_PARTY_KIND_TITLE_OUT to "الفلوس دي راحت إلى {0}",
    R06Key.FX55_GAIN_SKIPPED_ONE to "بيعة واحدة مش محسوبة (التكلفة مش متسجلة)",
    R06Key.FX55_GAIN_SKIPPED_TWO to "بيعتين مش محسوبين (التكلفة مش متسجلة)",
    R06Key.FX55_GAIN_SKIPPED_FEW to "{0} بيعات مش محسوبة (التكلفة مش متسجلة)",
    R06Key.FX55_GAIN_SKIPPED_MANY to "{0} بيعة مش محسوبة (التكلفة مش متسجلة)",
)

internal val ENGLISH_R06_FIX055_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.FX55_PARTY_KIND_TITLE_OUT to "This money went to {0}",
    R06Key.FX55_GAIN_SKIPPED_ONE to "One sale not counted (cost not recorded)",
    R06Key.FX55_GAIN_SKIPPED_TWO to "Two sales not counted (cost not recorded)",
    R06Key.FX55_GAIN_SKIPPED_FEW to "{0} sales not counted (cost not recorded)",
    R06Key.FX55_GAIN_SKIPPED_MANY to "{0} sales not counted (cost not recorded)",
)

/** عنوان خطوة «نوعه إيه؟» في «مَن هذا؟»: الخارج «إلى X» والداخل «من X» (فحص 0.5.4 ملاحظة 4). */
fun partyKindTitleKey(outgoing: Boolean): TextRef = if (outgoing) R06Key.FX55_PARTY_KIND_TITLE_OUT else UiKey.TRANSFER_PARTY_KIND_TITLE

/** مفتاح سطر «N بيعات مش محسوبة» بالجمع العربي (1 · 2 · 3-10 · 11+ — زي `BANK_SMS_AGO_*`). [count] > 0. */
fun gainSkippedKey(count: Int): R06Key = when {
    count == 1 -> R06Key.FX55_GAIN_SKIPPED_ONE
    count == 2 -> R06Key.FX55_GAIN_SKIPPED_TWO
    count in 3..10 -> R06Key.FX55_GAIN_SKIPPED_FEW
    else -> R06Key.FX55_GAIN_SKIPPED_MANY
}
