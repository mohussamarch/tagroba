package app.masroufy.ui.screens.investment

import app.masroufy.ui.icons.Lucide

/**
 * أيقونات منطقة «الاستثمار» — المسارات من `<svg>` لوحات النموذج نفسها (`Investment` · `Advisor`)، والدايرة اتحولت لمسار بنفس الشكل.
 * السُمك 1.5 والنهايات مدورة (`LucideIcon`).
 */
internal object InvestmentIcons {
    /** العقار (كارت «شقة الياسمين»). */
    val HOME = Lucide("INVEST_HOME", "M3 11l9-7 9 7", "M5 10v10h14V10", "M10 20v-6h4v6")

    /** الذهب (سبيكة). */
    val GOLD = Lucide("INVEST_GOLD", "M4 18l3-7h10l3 7z", "M8 11l2-5h4l2 5")

    /** حاسبة الادخار (علامة الفلوس). */
    val SAVINGS = Lucide("INVEST_SAVINGS", "M12 3v18", "M17 7H9.5a3 3 0 0 0 0 6h5a3 3 0 0 1 0 6H6")

    /** حاسبة التقاعد (ساعة بدايرة 9). */
    val RETIREMENT = Lucide("INVEST_RETIREMENT", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 7v5l3 2")

    /** «السنة السابقة» (−) في «الصورة كاملة». */
    val MINUS = Lucide("INVEST_MINUS", "M5 12h14")

    /** حاسبة الورث (ميزان). */
    val INHERITANCE = Lucide("INVEST_INHERITANCE", "M12 3v18", "M5 7h14", "M5 7l-3 7a3 3 0 0 0 6 0z", "M19 7l-3 7a3 3 0 0 0 6 0z")
}
