package app.masroufy.core

/**
 * نصوص «حساب لكل بلد» (OVERRIDES §41 · §64) باللغتين — بيتدمجوا في `ARABIC_TEXTS` و`ENGLISH_TEXTS`.
 * اسم البلد بيظهر **جوه التطبيق بس** — نصوص شاشة القفل ما اتغيرتش (مفيش اسم بلد عليها). الإنجليزي كتابة Claude ومستني مراجعة المالك (§40).
 */
internal val ARABIC_SPACE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.COUNTRY_SA to "السعودية",
    TextKey.COUNTRY_EG to "مصر",
    TextKey.SPACE_COUNTRY_UNKNOWN to "البلد دي لسه مش مدعومة",
    TextKey.SPACE_COUNTRY_TAKEN to "عندك حساب «{0}» قبل كده — كل بلد ليها حساب واحد",
    TextKey.SPACE_NOT_FOUND to "الحساب ده مش موجود",
    TextKey.SPACE_DEFAULT_FIXED to "حساب السعودية الأساسي ما بيتأرشفش",
    TextKey.BACKUP_GROUP_MERCHANT_CATEGORIES to "تصنيفات التجار في البلد",
)

internal val ENGLISH_SPACE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.COUNTRY_SA to "Saudi Arabia",
    TextKey.COUNTRY_EG to "Egypt",
    TextKey.SPACE_COUNTRY_UNKNOWN to "This country is not supported yet",
    TextKey.SPACE_COUNTRY_TAKEN to "You already have a “{0}” account — one account per country",
    TextKey.SPACE_NOT_FOUND to "This account was not found",
    TextKey.SPACE_DEFAULT_FIXED to "The main Saudi account cannot be archived",
    TextKey.BACKUP_GROUP_MERCHANT_CATEGORIES to "Merchant categories in this country",
)
