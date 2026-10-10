package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.AveragesFeed
import app.masroufy.core.IsoDate
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.screens.investment.InheritanceCalculatorRoute
import app.masroufy.ui.screens.investment.InheritanceSavedRoute
import app.masroufy.ui.screens.investment.InheritanceScenarioRoute
import app.masroufy.ui.screens.investment.RetirementCalculatorRoute
import app.masroufy.ui.screens.investment.SavingsCalculatorRoute
import app.masroufy.usecase.CalculateInheritance
import app.masroufy.usecase.CompareSavingsGrowth
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.ManageInheritanceScenarios
import app.masroufy.usecase.RetirementCalculator
import app.masroufy.usecase.SavingsCalculator

/**
 * حاسبات «الاستثمار» (OVERRIDES §69 — الادخار · التقاعد · الورث): جزء من منطقة «الاستثمار» في ملفاته هو (`screens/investment/calc/`)
 * عشان شاشات الاستثمار والحاسبات يتبنوا بالتوازي (ARCHITECTURE §31.31). المسارات في `investment/CalculatorRoutes.kt` (اسم كل مسار = اسم
 * اللوحة في النموذج). القطع المرسومة جوه الشاشات (`SavingsGrowth` · `RetirementSaudi` · `RetirementEgypt` · `InheritanceHeirs` ·
 * `InheritanceBefore` · `InheritanceResult` · `InheritanceSplit`) composables جوه شاشتها، مش مسارات.
 */

/** شخص من أشخاصك (تركة شخص آخر). */
data class PersonChoice(val id: String, val name: String)

/**
 * حالات الاستخدام اللي الحاسبات محتاجاها **بس** (CLAUDE.md #4) — التنفيذ في `:wiring` (`CalculatorsGraph`).
 */
interface CalculatorsDeps {
    /** النهارده بتوقيت الجوال. */
    fun today(): IsoDate

    /** حاسبة الادخار: الاتجاهين + المقارنة باللي بتحوّشه فعلًا + «حوّلها لخطة ادخار». */
    val savings: SavingsCalculator

    /** «لو وضعتها في…»: الخمسة جنب بعض بمعدلاتها ومصادرها. */
    val growth: CompareSavingsGrowth

    /** ملف المتوسطات (`averages.json`) — `FeedState.Unavailable` ⇒ «غير متاح» (مش صفر). */
    suspend fun averages(): FeedState<AveragesFeed>

    /** حاسبة التقاعد (السعودية ومصر) والراتب من مصادر الدخل. */
    val retirement: RetirementCalculator

    /** حاسبة الورث بقانون البلد الشغالة + «هات أملاكي من مصروفي». */
    val inheritance: CalculateInheritance

    /** حسبة محفوظة من بلد تانية بتتحسب بقانون بلدها (OVERRIDES §69.4 (ب)) — نفس حالة الاستخدام ببلد الحسبة، من غير جلب أملاك. */
    fun inheritanceUnder(countryCode: String): CalculateInheritance

    /** حسبات الورث المحفوظة على الحساب (حفظ · قايمة · فتح · تغيير الاسم · مسح). */
    val scenarios: ManageInheritanceScenarios

    /** أشخاصك (تركة شخص آخر) — من غير المؤرشفين. */
    suspend fun people(): List<PersonChoice>
}

fun RouteRegistry.registerCalculators() {
    screen<SavingsCalculatorRoute> { SavingsCalculatorScreen() }
    screen<RetirementCalculatorRoute> { RetirementCalculatorScreen() }
    screen<InheritanceCalculatorRoute> { InheritanceCalculatorScreen(null) }
    screen<InheritanceScenarioRoute> { r -> InheritanceCalculatorScreen(r.scenarioId) }
    screen<InheritanceSavedRoute> { InheritanceSavedScreen() }
}
