package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.IncomingSender
import app.masroufy.core.IsoDate
import app.masroufy.core.PayerAccount
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.UiKey
import app.masroufy.core.accountClash
import app.masroufy.core.answerPayerQuestion
import app.masroufy.core.checkIncomeSource
import app.masroufy.core.incomingSenders
import app.masroufy.core.isEmployer
import app.masroufy.core.payerAccountsOf
import app.masroufy.core.rejoinStart
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.TransactionRepository

/**
 * «المزيد › جهات الشغل» (قايمة المالك 2026-10-15، البند 7): الجهة = مصدر دخل «وظيفة» في نفس مجموعة `incomeSources` (مفيش تخزين جديد —
 * «مصادر الدخل» بتعرضها كمان). الاسم · من · لحد (الفاضي = لسه شغال) · **حساب مُحوِّل أو أكتر** (مفتاح الطرف: الاسم + آخر 4 بس — قاعدة 11).
 * الحساب بيتضاف من **التحويلات اللي جاتلك فعلًا** ([senders]) أو من «ده راتب الدوام» على تحويل — مش بالكتابة، عشان المفتاح يطابق
 * اللي القارئ بيطلّعه بالظبط. «طبّق على اللي فات» في `EmployerRetroApply`. **مفيش مسح** (زي مصادر الدخل): الجهة اللي خلصت بتتقفل بتاريخ.
 */
data class ManageEmployersDeps(
    val sources: IncomeSourceRepository,
    val txns: TransactionRepository,
    val ids: IdGenerator,
    val clock: Clock,
    /** عملة البلد الشغالة — الجهة الجديدة بيها. */
    val currency: Currency,
)

/** اللي المالك بيكتبه: الاسم وتاريخ البداية إجباريين، والنهاية فاضية = لسه شغال هناك. */
data class EmployerInput(val name: String, val startedAt: IsoDate, val endedAt: IsoDate? = null)

/** الجهة للعرض بحساباتها (الاسم زي ما ورد + آخر 4). */
data class EmployerView(val source: IncomeSource, val accounts: List<PayerAccount>) {
    val current: Boolean get() = source.endedAt == null
}

/** «راتب من أي جهة؟» (r05 — البند 9): الجهة الحالية · اتحددت لوحدها؟ · الجهات اللي تنفع. */
data class SalaryEmployer(val transactionId: Id, val current: IncomeSource?, val auto: Boolean, val choices: List<IncomeSource>)

class ManageEmployers(private val deps: ManageEmployersDeps) {
    private suspend fun everything() = deps.txns.listByDateRange(EARLIEST, LATEST)

    /** الجهات (وظيفة · دوام جزئي): الحالية الأول، وبعدين الأحدث بداية. */
    suspend fun list(): List<EmployerView> {
        val seen = incomingSenders(everything()).associate { it.party.key to it.party }
        return employers(deps.sources.listAll()).map { EmployerView(it, payerAccountsOf(it, seen)) }
    }

    /** الأطراف اللي حوّلت لك (الداخل بس، الأحدث الأول) — لاختيار حساب الجهة. */
    suspend fun senders(): List<IncomingSender> = incomingSenders(everything())

    /** إضافة جهة ([id] = null) أو تعديل اسمها وفترتها. الحسابات بتفضل زي ما هي — ولو الفترة الجديدة بقت بتتقابل مع جهة تانية بنفس الحساب ⇒ مرفوض. */
    suspend fun save(id: Id?, input: EmployerInput): IncomeSource {
        val all = deps.sources.listAll()
        val old = id?.let { find(all, it) }
        val start = if (old == null) rejoinStart(input.name, input.startedAt, all) else input.startedAt
        val draft = old?.copy(name = input.name, startedAt = start, endedAt = input.endedAt) ?: IncomeSource(
            deps.ids.next("inc"), input.name, "", IncomeSourceKind.JOB, deps.currency, start, input.endedAt, createdAt = deps.clock.nowIso(),
        )
        val checked = checkIncomeSource(
            draft.name, draft.startedAt, draft.endedAt, draft.expectedDayOfMonth, draft.expectedMinor, all, selfId = draft.id,
            kind = draft.kind, payFrequency = draft.payFrequency, payWeekday = draft.payWeekday,
        )
        val saved = draft.copy(name = checked.name, normalizedName = checked.normalizedName)
        val others = all.filter { it.id != saved.id } + saved
        saved.payerKeys.firstNotNullOfOrNull { accountClash(saved, it, others) }?.let { throw clash(it) }
        deps.sources.saveMany(listOf(saved))
        return saved
    }

    /** ربط حساب مُحوِّل بالجهة (الحساب التاني لنفس الجهة مسموح). نفس الحساب لجهة تانية في فترة بتتقابل ⇒ مرفوض باسمها. */
    suspend fun attach(employerId: Id, party: TransferPartyRef): IncomeSource {
        val all = deps.sources.listAll()
        val employer = find(all, employerId)
        if (party.key in employer.payerKeys) return employer
        accountClash(employer, party.key, all)?.let { throw clash(it) }
        val updated = answerPayerQuestion(employer, party.key, yes = true)
        deps.sources.saveMany(listOf(updated))
        return updated
    }

    /** شيل حساب من الجهة — العمليات اللي اتسجلت «راتب» قبل كده ما بتتلمسش (التراجع عنها من «طبّق على اللي فات» نفسه). */
    suspend fun detach(employerId: Id, key: String): IncomeSource {
        val employer = find(deps.sources.listAll(), employerId)
        if (key !in employer.payerKeys) return employer
        val updated = employer.copy(payerKeys = employer.payerKeys - key)
        deps.sources.saveMany(listOf(updated))
        return updated
    }

    /**
     * r05 (البند 9) «راتب من أي جهة؟» على راتب: الجهة الحالية ([SalaryEmployer.current] — من الحساب أو اختيارك أو لوحدها لأنها الوحيدة الشغالة يومه
     * [SalaryEmployer.auto]) والجهات اللي تنفع (الشغالة يومه الأول). null = مش راتب داخل.
     */
    suspend fun employerFor(transactionId: Id): SalaryEmployer? {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: return null
        if (t.observedDirection != app.masroufy.core.Direction.IN || t.economicKind != app.masroufy.core.EconomicKind.SALARY) return null
        val all = deps.sources.listAll()
        val current = app.masroufy.core.employerOf(t, all)
        val explicit = all.any { app.masroufy.core.salaryTxnKey(t.id) in it.payerKeys }
        val auto = current != null && !explicit && app.masroufy.core.transferPartyOf(t) == null
        val active = app.masroufy.core.sourcesActiveOn(all, t.occurredAt).filter(::isEmployer).map { it.id }.toSet()
        return SalaryEmployer(t.id, current, auto, employers(all).sortedBy { if (it.id in active) 0 else 1 })
    }

    /** «الراتب ده من الجهة دي» على عملية بعينها (من غير ما نلمس حسابات الجهة) — بيرجّع الجهات قبل التغيير للـ«تراجع» ([restore]). */
    suspend fun attachTransaction(employerId: Id, transactionId: Id): List<IncomeSource> {
        val all = deps.sources.listAll()
        val target = find(all, employerId)
        val key = app.masroufy.core.salaryTxnKey(transactionId)
        val before = all.filter { isEmployer(it) && (key in it.payerKeys || key in it.declinedPayerKeys) && it.id != target.id } + target
        val updated = before.map { s ->
            if (s.id == target.id) s.copy(payerKeys = (s.payerKeys - key) + key, declinedPayerKeys = s.declinedPayerKeys - key)
            else s.copy(payerKeys = s.payerKeys - key)
        }
        deps.sources.saveMany(updated)
        return before
    }

    /** «مش دي» على الجهة اللي اتحددت لوحدها ⇒ الراتب ده يتسأل «راتب من أي جهة؟». بيرجّع الجهة قبل التغيير للـ«تراجع». */
    suspend fun declineAuto(employerId: Id, transactionId: Id): List<IncomeSource> {
        val target = find(deps.sources.listAll(), employerId)
        val key = app.masroufy.core.salaryTxnKey(transactionId)
        deps.sources.saveMany(listOf(target.copy(declinedPayerKeys = (target.declinedPayerKeys - key) + key, payerKeys = target.payerKeys - key)))
        return listOf(target)
    }

    /** «تراجع» عن [attachTransaction] أو [declineAuto]: الجهات بترجع زي ما كانت بالظبط. */
    suspend fun restore(before: List<IncomeSource>) {
        if (before.isNotEmpty()) deps.sources.saveMany(before)
    }

    private fun find(all: List<IncomeSource>, id: Id): IncomeSource =
        all.firstOrNull { it.id == id && isEmployer(it) } ?: throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_NOT_FOUND))

    private fun clash(other: IncomeSource) = IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_CLASH, other.name))

    internal companion object {
        const val EARLIEST = "0000-01-01"
        const val LATEST = "9999-12-31"

        fun employers(all: List<IncomeSource>): List<IncomeSource> =
            all.filter(::isEmployer).sortedWith(compareBy<IncomeSource> { it.endedAt != null }.thenByDescending { it.startedAt }.thenBy { it.id })
    }
}
