package app.masroufy.wiring

import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.periodForDate
import app.masroufy.ui.screens.dues.DuesDeps
import app.masroufy.usecase.ManageInstallments
import app.masroufy.usecase.ManageInstallmentsDeps
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.ManageRecurring
import app.masroufy.usecase.ManageRecurringDeps
import app.masroufy.usecase.ManageRoscas
import app.masroufy.usecase.ManageRoscasDeps
import app.masroufy.usecase.RoscaSetup

/**
 * «المستحقات» — `loadDues(c.repos)` · `ManagePeople` · `ManageRoscas` + `RoscaSetup` · `ManageInstallments` · `ManageRecurring`،
 * بنفس اعتماداتها في اختبارات `:app`. **الملف ده بتاع المنطقة بس.**
 * رجول «التحويل لنفسك» (`spaceLegs`) مش متوصّلة هنا: شاشات المستحقات ما بتربطش عمليات لسه (التسوية من غير عملية) — لما الربط يتبني
 * (اقتراح ربط قسط/جمعية §75-٨) لازم تتوصّل من `TransferBetweenSpaces.legsIn`.
 */
class DuesGraph(private val c: AreaContext) : DuesDeps {
    private val r = c.repos
    private val env = c.env

    override val loadDues = loadDues(r)

    override val people = ManagePeople(
        ManagePeopleDeps(r.people, r.obligations, r.settlements, r.settlementWriter, r.allocations, r.transactions, r.uow, env.ids, env.clock),
    )

    override val roscas = ManageRoscas(
        ManageRoscasDeps(
            r.roscas, r.roscaEntries, r.installmentPayments, r.transactions, r.uow, env.ids, env.clock, r.categories, r.installmentPlans,
            zakatPayments = r.zakatPayments, eventLinks = r.eventLinks,
        ),
    )

    override val roscaSetup = RoscaSetup()

    override val installments = ManageInstallments(
        ManageInstallmentsDeps(
            r.installmentPlans, r.installmentPayments, r.roscaEntries, r.debtTerms, r.obligations, r.transactions, r.uow, env.ids, env.clock,
            r.categories, zakatPayments = r.zakatPayments, eventLinks = r.eventLinks,
        ),
    )

    override val recurring = ManageRecurring(ManageRecurringDeps(r.recurring, r.transactions, r.categories, env.ids))

    /** r051 — لوحة «ادفع بعدين» (الهيكل `BnplHost`) بنفس `installments` اللي فوق، واسم المحفظة والتصنيف. */
    override val bnpl = app.masroufy.usecase.BnplCharges(
        app.masroufy.usecase.BnplChargesDeps(
            r.transactions, r.installmentPlans, r.installmentPayments, installments, env.clock, env.today, wallets = r.wallets, categories = r.categories,
        ),
    )

    /** PLAN-0.6 §8 — «مجموع أكتر من قسط» بنفس اعتمادات اللوحة. */
    override val bnplSplit = app.masroufy.usecase.BnplSplitCharge(
        app.masroufy.usecase.BnplChargesDeps(r.transactions, r.installmentPlans, r.installmentPayments, installments, env.clock, env.today),
    )

    /** r051 — قسم «مشتريات بالتقسيط» في «الأقساط». */
    override val bnplPlans = app.masroufy.usecase.LoadBnplPlans(r.installmentPlans, r.installmentPayments, r.categories)

    override val deleteDebt = app.masroufy.usecase.DeleteDebt(
        app.masroufy.usecase.DeleteDebtDeps(r.obligations, r.settlements, r.allocations, r.uow, terms = r.debtTerms, links = r.personLinks, txns = r.transactions, clock = env.clock, zakatFacts = r.zakatFacts),
    )

    /** r06-debts (OVERRIDES §112-2) — «ضيف دين»: نفس `people` و`installments` اللي فوق + ربط العمليات بالأشخاص بنفس اعتمادات صفحة الشخص (`personLinksOf`). */
    override val addDebt = app.masroufy.usecase.AddDebt(people, personLinksOf(r, env.ids, env.clock, SpaceLegsOf(c.space.id, r.spaceTransfers)), installments, r.obligations)

    /** يوم الراتب من الملف (`ManageProfile.load` — 28 لو مش متسجل) ⇒ الفترة اللي فيها [today]. */
    override suspend fun period(today: IsoDate): Period = periodForDate(today, c.shell.profile.load().payday)
}
