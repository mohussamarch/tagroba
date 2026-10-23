package app.masroufy.core

/**
 * r06-more (OVERRIDES §116) — «المزيد» الجديدة: 3 مجموعات (فلوسك · بياناتك · الإعدادات) · علم البلد جنب كارت الملف و«القايمة السريعة» ·
 * صفحتين صغيرين («الاستيراد» و«الإعدادات») · سطر «مصادر الدخل» في «ملفك». الكلام من `More.dc.html` و`Account.dc.html` (فرع التصميم c37ce76a).
 * ملف لوحده زي `TextsR06Split.kt` (مفاتيح MR_ في [R06Key]).
 */
internal val MSA_R06_MORE_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.MR_GROUP_MONEY to "أموالك",
    R06Key.MR_WALLETS_HINT to "البنوك والنقد",
    R06Key.MR_CATS to "التصنيفات والقواعد",
    R06Key.MR_CATS_HINT to "وما يتذكره التطبيق عن التجار",
    R06Key.MR_IMPORT to "الاستيراد",
    R06Key.MR_IMPORT_HINT to "رسائل البنك · كشف الحساب",
    R06Key.MR_BACKUP to "النسخة والتصدير",
    R06Key.MR_SETTINGS to "الإعدادات",
    R06Key.MR_SETTINGS_HINT to "الإشعارات · القفل · المظهر · اللغة · سجل المشكلات",
    R06Key.MR_SET_LANG to "اللغة والمحتوى الإسلامي",
    R06Key.MR_SWAP_TO to "بدّل إلى {0}",
    R06Key.MR_ADD_COUNTRY to "+ أضف بلدًا",
    R06Key.MR_ARCHIVE_COUNTRY to "أرشف {0}",
    R06Key.MR_QUICK_TITLE to "القائمة السريعة",
    R06Key.MR_INCOME_VALUE to "عملك والإيجار",
)

internal val EGYPTIAN_R06_MORE_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.MR_GROUP_MONEY to "فلوسك",
    R06Key.MR_WALLETS_HINT to "البنوك والكاش",
    R06Key.MR_CATS to "التصنيفات والقواعد",
    R06Key.MR_CATS_HINT to "واللي البرنامج فاكره عن التجار",
    R06Key.MR_IMPORT to "الاستيراد",
    R06Key.MR_IMPORT_HINT to "رسايل البنك · كشف الحساب",
    R06Key.MR_BACKUP to "النسخة والتصدير",
    R06Key.MR_SETTINGS to "الإعدادات",
    R06Key.MR_SETTINGS_HINT to "الإشعارات · القفل · المظهر · اللغة · سجل المشكلات",
    R06Key.MR_SET_LANG to "اللغة والمحتوى الإسلامي",
    R06Key.MR_SWAP_TO to "بدّل لـ{0}",
    R06Key.MR_ADD_COUNTRY to "+ ضيف بلد",
    R06Key.MR_ARCHIVE_COUNTRY to "أرشف {0}",
    R06Key.MR_QUICK_TITLE to "القايمة السريعة",
    R06Key.MR_INCOME_VALUE to "شغلك والإيجار",
)

internal val ENGLISH_R06_MORE_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.MR_GROUP_MONEY to "Your money",
    R06Key.MR_WALLETS_HINT to "Banks and cash",
    R06Key.MR_CATS to "Categories and rules",
    R06Key.MR_CATS_HINT to "And what the app remembers about merchants",
    R06Key.MR_IMPORT to "Import",
    R06Key.MR_IMPORT_HINT to "Bank messages · Statement",
    R06Key.MR_BACKUP to "Backup and export",
    R06Key.MR_SETTINGS to "Settings",
    R06Key.MR_SETTINGS_HINT to "Notifications · Lock · Appearance · Language · Problem log",
    R06Key.MR_SET_LANG to "Language and Islamic content",
    R06Key.MR_SWAP_TO to "Switch to {0}",
    R06Key.MR_ADD_COUNTRY to "+ Add a country",
    R06Key.MR_ARCHIVE_COUNTRY to "Archive {0}",
    R06Key.MR_QUICK_TITLE to "Quick menu",
    R06Key.MR_INCOME_VALUE to "Your work and rent",
)
