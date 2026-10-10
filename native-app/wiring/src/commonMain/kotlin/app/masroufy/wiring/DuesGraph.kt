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

    /** يوم الراتب من الملف (`ManageProfile.load` — 28 لو مش متسجل) ⇒ الفترة اللي فيها [today]. */
    override suspend fun period(today: IsoDate): Period = periodForDate(today, c.shell.profile.load().payday)
}
