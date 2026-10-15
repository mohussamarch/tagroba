package app.masroufy.wiring

import app.masroufy.core.Id
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.SpaceTransferError
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.usecase.EmployerRetroApply
import app.masroufy.usecase.EmployerRetroDeps
import app.masroufy.usecase.IncomeAnswers
import app.masroufy.usecase.IncomeAnswersDeps
import app.masroufy.usecase.ManageEmployers
import app.masroufy.usecase.ManageEmployersDeps

/**
 * «جهات الشغل» وردود الداخل الجديدة (قايمة المالك 2026-10-15، البندين 6 و7) لبلد — نفس الاعتمادات في «العمليات» و«المزيد». النسخة قبل
 * «طبّق على اللي فات» من الجهاز ([DeviceEnv.repairBackup]); من غيرها التطبيق على اللي فات بيترفض برسالة. [legs] = رجول «التحويل لنفسك»
 * (null ⇒ قراية بس من أزواج البلد — عشان «المزيد» كمان ما يلمسش رجل).
 */
internal class EmployerUseCases(c: AreaContext, legs: SpaceTransferLegs? = null) {
    private val r = c.repos
    private val spaceLegs = legs ?: ReadOnlyLegs(c)

    val employers = ManageEmployers(ManageEmployersDeps(r.incomeSources, r.transactions, c.env.ids, c.env.clock, c.space.currency))

    val retro = EmployerRetroApply(EmployerRetroDeps(r.incomeSources, r.transactions, r.uow, c.env.clock, c.env.repairBackup, spaceLegs))

    val answers = IncomeAnswers(IncomeAnswersDeps(r.transactions, r.categories, employers, retro, r.uow, c.env.clock, spaceLegs))
}

/** رجول «التحويل لنفسك» في البلد دي — قراية بس (الفك شغل «العمليات»، ومن هنا بيرفض). */
private class ReadOnlyLegs(private val c: AreaContext) : SpaceTransferLegs {
    override suspend fun pairsOf(transactionIds: List<Id>): List<SpaceTransfer> =
        c.repos.spaceTransfers.listAll().filter { p -> transactionIds.any { p.involves(c.space.id, it) } }

    override suspend fun legsAmong(transactionIds: List<Id>): Set<Id> {
        val pairs = c.repos.spaceTransfers.listAll()
        return transactionIds.filter { id -> pairs.any { it.involves(c.space.id, id) } }.toSet()
    }

    override suspend fun detach(pairs: List<SpaceTransfer>) = throw SpaceTransferError(uiText(TextKey.NOT_AVAILABLE))
}
