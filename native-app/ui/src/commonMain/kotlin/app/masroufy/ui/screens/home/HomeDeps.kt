package app.masroufy.ui.screens.home

import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadCashSummary
import app.masroufy.usecase.LoadHomeScreen
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ManageProfile
import app.masroufy.usecase.ReviewHistory
import app.masroufy.usecase.RunAlertEngine
import app.masroufy.usecase.SetEconomicKind

/**
 * منطقة «الرئيسية» (`SCREENS.md` §٢.٢ + القفل وسؤال الملف و«اختر شكلك» والمراجعة — توزيع جلسة البناء): حالات الاستخدام اللي شاشاتها محتاجاها **بس**.
 * التنفيذ: `:wiring` → `HomeGraph`. **ممنوع** مستودع هنا (CLAUDE.md #4). «معك الآن» والجرس والبلد في `ShellDeps` (`LocalSpace.current.shell`).
 */
interface HomeDeps {
    /** «القادم» والتقويم الذكي — `LoadCalendar.items(من، لحد، النهارده)` · `month` · `summary` (ميعاد الراتب الجاي). */
    val calendar: LoadCalendar

    /** «صرفت هذا الشهر» في البطاقة البطلة (`HomeScreenData.expenseMinor`) — من غير التاريخ (`includeHistory = false`). */
    val homeScreen: LoadHomeScreen

    /** لوحة الكاش (`CashDetails`): الرصيد · اللي دخل وخرج من رصيد البداية · المصروف كاش في الشهر · عملياته. null = مفيش محفظة كاش. */
    val cash: LoadCashSummary

    /** يوم الراتب (بداية الشهر المالي) وأسئلة «كمّل ملفك» (`saveWithQuestions` · `answerCarToWork`). نفس نسخة الهيكل. */
    val profile: ManageProfile

    /** صفحة الإشعارات (`inbox` · `opened`) وكارت المساعد في الرئيسية (آخر سطر من مجموعة المساعد). نفس محرك الهيكل. */
    val alerts: RunAlertEngine

    /** المراجعة: اقتراح النوع (`summarize`) · تأكيد واحدة (`setOne`) · تأكيد مجموعة قاطعة (`confirmBulk`). */
    val kinds: SetEconomicKind

    /** المراجعة: عمليات الشهر (`preview(...).rows`) و«طبّق قواعدك على السابق» (`preview` ⇒ `applyCategories`). */
    val history: ReviewHistory

    /** أسماء التصنيفات في معاينة «طبّق قواعدك» (`list`). */
    val categories: ManageCategories
}
