package app.masroufy.wiring

import app.masroufy.core.AveragesFeed
import app.masroufy.core.countryPack
import app.masroufy.ui.screens.investment.calc.CalculatorsDeps
import app.masroufy.ui.screens.investment.calc.PersonChoice
import app.masroufy.usecase.CalculateInheritance
import app.masroufy.usecase.CalculateInheritanceDeps
import app.masroufy.usecase.CompareSavingsGrowth
import app.masroufy.usecase.EstateHoldingsDeps
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.LoadActualSaving
import app.masroufy.usecase.LoadActualSavingDeps
import app.masroufy.usecase.LoadDefaultRates
import app.masroufy.usecase.ManageInheritanceScenarios
import app.masroufy.usecase.ManageInheritanceScenariosDeps
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.ManageSavingsGoals
import app.masroufy.usecase.ManageSavingsGoalsDeps
import app.masroufy.usecase.RetirementCalculator
import app.masroufy.usecase.RetirementCalculatorDeps
import app.masroufy.usecase.SavingsCalculator
import app.masroufy.usecase.SavingsCalculatorDeps

/**
 * الحاسبات (جزء من «الاستثمار» — `ui/screens/investment/calc/`): كل حالة استخدام بنفس اعتماداتها في اختبارات `:app`، من [AreaContext].
 * **الملف ده بتاع الحاسبات بس** (`InvestmentGraph` بيشاور عليه بسطر واحد).
 */
class CalculatorsGraph(private val c: AreaContext, private val rates: LoadDefaultRates) : CalculatorsDeps {
    private val r = c.repos

    override fun today() = c.env.today()

    private val goals = ManageSavingsGoals(ManageSavingsGoalsDeps(r.savingsGoals, r.goalContributions, c.env.ids, c.env.clock))

    override val savings = SavingsCalculator(
        SavingsCalculatorDeps(
            actual = LoadActualSaving(LoadActualSavingDeps(r.transactions, r.allocations, r.categories, r.profile)),
            goals = goals,
        ),
    )

    override val growth = CompareSavingsGrowth(rates)

    override suspend fun averages(): FeedState<AveragesFeed> = c.feeds.averages()

    override val retirement = RetirementCalculator(RetirementCalculatorDeps(r.incomeSources))

    private val holdings = EstateHoldingsDeps(r.wallets, r.transactions, r.assets, r.assetLots, r.assetSales, r.assetPrices)

    override val inheritance = CalculateInheritance(CalculateInheritanceDeps(c.space.countryCode, c.space.currency, c.env.clock, holdings))

    override fun inheritanceUnder(countryCode: String): CalculateInheritance =
        if (countryCode.equals(c.space.countryCode, ignoreCase = true)) inheritance
        else CalculateInheritance(CalculateInheritanceDeps(countryCode, countryPack(countryCode).currency, c.env.clock, holdings = null))

    override val scenarios = ManageInheritanceScenarios(ManageInheritanceScenariosDeps(r.inheritanceScenarios, c.env.ids, c.env.clock))

    private val people = ManagePeople(
        ManagePeopleDeps(r.people, r.obligations, r.settlements, r.settlementWriter, r.allocations, r.transactions, r.uow, c.env.ids, c.env.clock),
    )

    override suspend fun people(): List<PersonChoice> =
        people.listWithBalances().filter { !it.person.archived }.map { PersonChoice(it.person.id, it.person.name) }
}
