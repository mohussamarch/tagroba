package app.masroufy.core

/**
 * نصوص جلسة 18 (OVERRIDES §62 · §61 · §66): عملة سطر السعر (أسعار الجنيه لزكاة مصر) + اسم مجموعة إعدادات التنبيهات في النسخة.
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (كتابة Claude ومستني مراجعة المالك زي باقي الجدول §40).
 */
internal val MSA_FEED_ALERT_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.FEED_BAD_CURRENCY to "عملة السعر غير مكتوبة بشكل صحيح",
    TextKey.PRICE_CURRENCY_MISMATCH to "سعر «{0}» في الملف بعملة {1} والأصل بعملة {2}، فلم يُطبَّق",
    TextKey.BACKUP_GROUP_ALERT_SETTINGS to "إعدادات التنبيهات",
)

internal val EGYPTIAN_FEED_ALERT_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.FEED_BAD_CURRENCY to "عملة السعر مش مكتوبة صح",
    TextKey.PRICE_CURRENCY_MISMATCH to "سعر «{0}» في الملف بعملة {1} والأصل بعملة {2}، فما اتحطّش عليه",
    TextKey.BACKUP_GROUP_ALERT_SETTINGS to "إعدادات التنبيهات",
)

internal val ENGLISH_FEED_ALERT_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.FEED_BAD_CURRENCY to "The price currency isn't written correctly",
    TextKey.PRICE_CURRENCY_MISMATCH to "The price for “{0}” is in {1} but the asset is in {2}, so it wasn't applied",
    TextKey.BACKUP_GROUP_ALERT_SETTINGS to "Alert settings",
)
