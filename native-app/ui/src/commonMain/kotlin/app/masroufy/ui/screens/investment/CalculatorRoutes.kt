package app.masroufy.ui.screens.investment

import app.masroufy.ui.nav.Route

/*
 * مسارات الحاسبات (OVERRIDES §69 — الادخار · التقاعد · الورث). اسم كل مسار = اسم اللوحة في النموذج (`SCREENS.md`).
 * الشاشات نفسها في `investment/calc/` وبتتسجّل من `registerCalculators()` (`calc/CalcRoutes.kt`).
 *
 * ⚠️ وقت الدمج مع فرع شاشات «الاستثمار» (`screens-invest`): الفرع ده عامل نسخة مؤقتة من التلاتة الأولانيين (`SavingsCalculatorRoute` ·
 * `RetirementCalculatorRoute` · `InheritanceCalculatorRoute`) في `InvestmentRoutes.kt` **بنفس الاسم والحزمة** عشان روابط شاشة «الاستثمار»
 * تتكتب — الكومبايلر هيقول «Redeclaration»: **امسح النسخة المؤقتة من `InvestmentRoutes.kt`** وسيب دي (هي اللي متسجّلة)، وروابطهم
 * (`nav.push(SavingsCalculatorRoute)` …) هتشتغل زي ما هي من غير تعديل.
 */

object SavingsCalculatorRoute : Route {
    override val name = "SavingsCalculator"
}

object RetirementCalculatorRoute : Route {
    override val name = "RetirementCalculator"
}

/** حسبة ورث جديدة (من «الاستثمار» أو «احسب تركة» في المحفوظة). */
object InheritanceCalculatorRoute : Route {
    override val name = "InheritanceCalculator"
}

/** حسبة محفوظة بتتفتح على النتيجة (من «الحسبات المحفوظة») — نفس الشاشة واسم اللوحة. */
data class InheritanceScenarioRoute(val scenarioId: String) : Route {
    override val name = "InheritanceCalculator"
}

object InheritanceSavedRoute : Route {
    override val name = "InheritanceSaved"
}
