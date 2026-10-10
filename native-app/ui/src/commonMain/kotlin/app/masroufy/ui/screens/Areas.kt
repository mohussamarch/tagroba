package app.masroufy.ui.screens

import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.screens.budgets.registerBudgets
import app.masroufy.ui.screens.dues.registerDues
import app.masroufy.ui.screens.home.registerHome
import app.masroufy.ui.screens.imports.registerImports
import app.masroufy.ui.screens.investment.registerInvestment
import app.masroufy.ui.screens.more.registerMore
import app.masroufy.ui.screens.onboarding.registerOnboarding
import app.masroufy.ui.screens.operations.registerOperations
import app.masroufy.ui.screens.people.registerPeople

/**
 * المناطق الثمانية (`SCREENS.md` §2) — **القايمة دي ثابتة** عشان المناطق تتبني بالتوازي: كل منطقة بتضيف شاشاتها في
 * `screens/<المنطقة>/<Area>Routes.kt` بس، والملف ده ما بيتلمسش. منطقة جديدة = قرار مكتوب.
 */
fun buildRegistry(): RouteRegistry = RouteRegistry().apply {
    registerHome()
    registerOperations()
    registerImports()
    registerPeople()
    registerDues()
    registerInvestment()
    registerMore()
    registerOnboarding()
    // منطقة تاسعة (قرار مكتوب — ARCHITECTURE §31.32): الميزانيات والخطط والتصنيفات والقواعد
    registerBudgets()
}
