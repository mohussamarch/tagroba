package app.masroufy.ui.screens.more

import app.masroufy.ui.icons.Lucide

/** أيقونات منطقة «المزيد» بس — المسارات من `<svg>` لوحات النموذج نفسها (`More` · `Backup`)، والمستطيل اتحول لمسار بنفس الشكل. */
internal object MoreIcons {
    /** «مصادر الدخل» — محفظة وسهم نازل جواها. */
    val INCOME = Lucide("MORE_INCOME", "M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z", "M12 10v5M9.5 12.5L12 15l2.5-2.5")

    /** «المحافظ» (`More`). */
    val WALLET_CARD = Lucide("MORE_WALLET", "M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z", "M16 12.5h2", "M3 9h18")

    /** الاستيراد (رسائل البنك · كشف الحساب · دفعات الاستيراد). */
    val IMPORT = Lucide("MORE_IMPORT", "M12 4v11", "M8 11l4 4 4-4", "M4 19h16")

    /** «النسخة الاحتياطية» — سحابة وسهم طالع. */
    val BACKUP = Lucide("MORE_BACKUP", "M7 18a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 9.5a4 4 0 0 1-.5 8.5z", "M12 11v5M10 13l2-2 2 2")

    /** «التصدير». */
    val EXPORT = Lucide("MORE_EXPORT", "M12 15V4", "M8 8l4-4 4 4", "M4 19h16")

    /** كارت «النسخة الشاملة» (`Backup`). */
    val DATABASE_BACKUP = Lucide(
        "MORE_DATABASE_BACKUP",
        "M3 5a9 3 0 1 0 18 0a9 3 0 1 0 -18 0M3 12a9 3 0 0 0 5 2.69M21 9.3V5M3 5v14a9 3 0 0 0 6.47 2.88M12 12v4h4M13 20a5 5 0 0 0 9-3 4.5 4.5 0 0 0-4.5-4.5c-1.33 0-2.54.54-3.41 1.41L12 16",
    )

    /** كارت «تصدير العمليات» (جدول). */
    val TABLE = Lucide("MORE_TABLE", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2ZM3 9L21 9M3 15L21 15M9 9L9 21M15 9L15 21")

    /** «عميل» في مصادر الدخل. */
    val CLIENT = Lucide("MORE_CLIENT", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0")

    /** ملف نسخة (لوحة اختيار الملف). */
    val FILE_JSON = Lucide("MORE_FILE_JSON", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 12a1 1 0 0 0-1 1v1a1 1 0 0 1-1 1 1 1 0 0 1 1 1v1a1 1 0 0 0 1 1", "M14 18a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1 1 1 0 0 1-1-1v-1a1 1 0 0 0-1-1")
}
