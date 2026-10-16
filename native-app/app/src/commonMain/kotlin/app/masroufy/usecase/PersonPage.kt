package app.masroufy.usecase

import app.masroufy.core.AutoMeaning
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Person
import app.masroufy.core.PersonLedger
import app.masroufy.core.PersonMeaning
import app.masroufy.core.PersonSuggestion
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.autoLinkTargets
import app.masroufy.core.mergePersonLedgers
import app.masroufy.core.manualPartyKey
import app.masroufy.core.meaningForAuto
import app.masroufy.core.operationKindFor
import app.masroufy.core.partyMatches
import app.masroufy.core.personLedger
import app.masroufy.core.personSuggestions
import app.masroufy.core.remainingOfObligation
import app.masroufy.core.searchOperations
import app.masroufy.core.transferPartyOf
import app.masroufy.core.typedLast4
import app.masroufy.core.uiText

/** صفحة الشخص: هو · «السجل بينا» بالديون والأرصدة (كل البلاد — A3) · حساباته المربوطة (البلد الشغالة). */
data class PersonPageData(val person: Person, val ledger: PersonLedger, val accounts: List<TransferParty>)

/** دين من بلد تانية في صفحة الشخص (A3): معرّفه بيبدأ بده ⇒ الشاشة ما بتفتحوش (صفحة الدين بتقرا البلد الشغالة بس). */
const val ABROAD_ID_PREFIX = "abroad:"

/**
 * صفحة الشخص (المالك 2026-10-16: «إيه اللي بيني وبينه؟ وإيه الحساب المربوط بيه؟»): «السجل بينا» من الروابط والديون والسدادات وتحويلات حساباته
 * (`personLedger` في `core`) · «اربط عمليات موجودة» (اقتراحات + بحث في اللي **مش مربوط** بحد).
 */
class LoadPersonPage internal constructor(private val deps: PersonLinksDeps, private val owner: ManagePersonLinks) {
    /**
     * [others] = نفس الحالة في البلاد التانية (A3): سجلها وأرصدتها بتتضاف — كل عملة لوحدها (`mergePersonLedgers`)، وسطورها ما بتتفتحش من هنا.
     * السرعة: بالشخص (الروابط والديون بمعرّفه) وبمفتاح الحساب (`PartyTxnIndex`) — من غير قراية كل العمليات في كل فتحة.
     */
    suspend fun load(personId: Id, others: List<ManagePersonLinks> = emptyList()): PersonPageData? {
        val person = deps.people.listAll().firstOrNull { it.id == personId } ?: return null
        val accounts = owner.accounts.of(personId)
        val abroad = others.mapNotNull { o -> runCatching { o.page.ledgerHere(personId).abroad() }.getOrNull() }
        return PersonPageData(person, mergePersonLedgers(listOf(ledgerOf(personId, accounts)) + abroad), accounts)
    }

    /** سجل الشخص في البلد دي بس (للبلاد التانية في صفحته). */
    internal suspend fun ledgerHere(personId: Id): PersonLedger = ledgerOf(personId, owner.accounts.of(personId))

    private suspend fun ledgerOf(personId: Id, accounts: List<TransferParty>): PersonLedger {
        val links = deps.links.listByPerson(personId)
        val obligations = deps.obligations.listByPerson(personId)
        val settlements = deps.settlements.listByObligations(obligations.map { it.id })
        val accountIds = if (accounts.isEmpty()) emptySet() else owner.partyIndex.idsFor(accounts)
        val ids = (links.map { it.transactionId } + obligations.mapNotNull { it.originTransactionId } + settlements.map { it.transactionId }.filter { it.isNotEmpty() } + accountIds).distinct()
        val txns = if (ids.isEmpty()) emptyMap() else deps.txns.findByIds(ids).associateBy { it.id }
        val accountTxns = accountIds.mapNotNull(txns::get).filter { t -> !t.id.endsWith(OWN_LEG_SUFFIX) && matchesAny(t, accounts) }
        return personLedger(links, obligations, settlements, txns, accountTxns)
    }

    private fun PersonLedger.abroad(): PersonLedger = PersonLedger(
        entries.map { it.copy(transactionId = null, obligationId = null) },
        open.map { it.copy(obligation = it.obligation.copy(id = ABROAD_ID_PREFIX + it.obligation.id)) },
        settled.map { it.copy(obligation = it.obligation.copy(id = ABROAD_ID_PREFIX + it.obligation.id)) },
        totals,
    )

    /** «اربط عمليات موجودة»: عمليات شكلها معاه ومش مربوطة بحد — نفس الحساب · نفس الاسم · نفس مبلغ دين مفتوح. أول 20. */
    suspend fun suggestions(personId: Id): List<PersonSuggestion> {
        val person = deps.people.listAll().firstOrNull { it.id == personId } ?: return emptyList()
        val obligations = deps.obligations.listByPerson(personId)
        val settlements = deps.settlements.listByObligations(obligations.map { it.id })
        val open = obligations.map { remainingOfObligation(it, settlements) }.filter { it > 0L }.toSet()
        val everything = owner.everything()
        val bases = ownLegBases(everything)
        val all = everything.filter { !ownMoneyMove(it, it.id in bases) } // N3: حركات بين فلوسك ما بتتقترحش
        val found = personSuggestions(person.name, owner.accounts.of(personId), open, all, limit = 200)
        val linked = owner.linkedAmong(found.map { it.transaction.id })
        return found.filter { it.transaction.id !in linked }.take(20)
    }

    /** البحث في أي عملية مش مربوطة بشخص (بالاسم أو المبلغ). */
    suspend fun search(query: String): List<Transaction> {
        val everything = owner.everything()
        val bases = ownLegBases(everything)
        val hits = searchOperations(query, everything.filter { !ownMoneyMove(it, it.id in bases) }, limit = 80)
        val linked = owner.linkedAmong(hits.map { it.id })
        return hits.filter { it.id !in linked }.take(30)
    }

    private fun matchesAny(t: Transaction, accounts: List<TransferParty>): Boolean =
        transferPartyOf(t)?.let { ref -> accounts.any { partyMatches(it, ref) } } == true
}

/**
 * «الحسابات المربوطة» في صفحة الشخص: الحسابات اللي اتقرر إنها بتاعته (`TransferParty` بحكم «شخص») · شيل الربط (بتراجع) · «أضف حساب» بآخر 4 أرقام
 * (والبنك اختياري — قاعدة 11) · «سجّل تحويلاته لوحده» بمعنى المالك يختاره · «أضف عملية مع الشخص ده» (الكاش مسموح).
 */
class PersonAccounts internal constructor(private val deps: PersonLinksDeps, private val owner: ManagePersonLinks) {
    suspend fun of(personId: Id): List<TransferParty> =
        deps.parties.listAll().filter { it.verdict == TransferVerdict.PERSON && it.personId == personId }.sortedWith(compareBy({ it.label }, { it.key }))

    suspend fun addManual(personId: Id, last4: String, bank: String?): TransferParty {
        val digits = typedLast4(last4) ?: throw PersonLinkError(uiText(UiKey.PLINK_ACC_ERR_LAST4))
        val people = deps.people.listAll()
        if (people.none { it.id == personId }) throw PersonLinkError(uiText(TextKey.PERSON_NOT_FOUND))
        val key = manualPartyKey(digits)
        val existing = deps.parties.listAll().firstOrNull { it.key == key }
        if (existing != null && existing.verdict == TransferVerdict.PERSON && existing.personId != personId) {
            throw PersonLinkError(uiText(UiKey.PLINK_ACC_ERR_TAKEN, people.firstOrNull { it.id == existing.personId }?.name.orEmpty()))
        }
        // F13: الحساب ده متقرر قبل كده إنه حاجة تانية (حسابك التاني · متجاهَل · جهة …) ⇒ ما بنكتبش فوق قرار المالك
        if (existing != null && existing.verdict != TransferVerdict.PERSON) throw PersonLinkError(uiText(UiKey.PLINK_ACC_ERR_DECIDED))
        val label = bank?.trim()?.take(40)?.takeIf { it.isNotEmpty() }?.let { "$it ••••$digits" } ?: "••••$digits"
        // نفس الشخص ⇒ قواعده (التسجيل لوحده · المستحقات · الممنوع) بتفضل زي ما هي
        val party = existing?.copy(label = label, last4 = digits) ?: TransferParty(key, label, digits, TransferVerdict.PERSON, personId, deps.clock.nowIso())
        deps.parties.save(party)
        return party
    }

    /** شيل الحساب من الشخص — بيرجّع القرار القديم عشان «تراجع» ([restore]). التحويلات نفسها وروابطها ما بتتلمسش. */
    suspend fun remove(personId: Id, key: String): TransferParty? {
        val party = deps.parties.listAll().firstOrNull { it.key == key && it.personId == personId } ?: return null
        deps.parties.remove(key)
        return party
    }

    suspend fun restore(party: TransferParty) = deps.parties.save(party)

    /** «سجّل تحويلاته لوحده» بمعنى ([meaning]) أو قفله (null). شغال على التحويلات اللي يومها من النهارده ولقدام بس. */
    suspend fun setAuto(key: String, meaning: AutoMeaning?): TransferParty {
        val party = deps.parties.listAll().firstOrNull { it.key == key && it.verdict == TransferVerdict.PERSON }
            ?: throw PersonLinkError(uiText(UiKey.PLINK_ACC_ERR_GONE))
        val updated = party.copy(autoMeaning = meaning, autoSince = meaning?.let { deps.clock.nowIso().take(10) })
        deps.parties.save(updated)
        return updated
    }

    /**
     * بيربط التحويلات الجديدة من الحسابات اللي «سجّل تحويلاته لوحده» شغال عليها ([onlyIds] = عمليات معيّنة — تفاصيل العملية). بيرجّع العدد.
     * اللي ما ينفعش يتربط (رجل تحويل لنفسك · سداد من غير دين يتقفل…) بيتساب من غير ما يوقف الباقي.
     */
    suspend fun applyAuto(onlyIds: List<Id>? = null): Int {
        val autos = deps.parties.listAll().filter { it.autoMeaning != null && it.verdict == TransferVerdict.PERSON && it.personId != null }
        if (autos.isEmpty()) return 0
        val since = autos.minOf { it.autoSince.orEmpty() }.take(10).ifEmpty { "0000-01-01" }
        val txns = (if (onlyIds != null) deps.txns.findByIds(onlyIds) else deps.txns.listByDateRange(since, "9999-12-31")).filter { !it.id.endsWith(OWN_LEG_SUFFIX) }
        val targets = autoLinkTargets(autos, txns, emptySet())
        if (targets.isEmpty()) return 0
        val linked = owner.linkedAmong(targets.map { it.first.id })
        var count = 0
        for ((t, party) in targets) {
            if (t.id in linked) continue
            val meaning = meaningForAuto(party.autoMeaning ?: continue, t.observedDirection)
            if (runCatching { owner.link(t.id, party.personId ?: continue, meaning, auto = true) }.isSuccess) count++
        }
        return count
    }

    suspend fun wallets(): List<Wallet> = deps.wallets?.listAll().orEmpty()

    /** «أضف عملية مع الشخص ده»: عملية جديدة بالنوع اللي المعنى بيحدده (والاتجاه منه) + الربط. فشل الربط ⇒ العملية بتتشال. */
    suspend fun addOperation(personId: Id, meaning: PersonMeaning, amountMinor: Halalas, date: IsoDate, walletId: Id, note: String?): LinkOutcome {
        val add = deps.add ?: throw PersonLinkError(uiText(TextKey.NOT_AVAILABLE))
        val kind = operationKindFor(meaning) ?: throw PersonLinkError(uiText(UiKey.PLINK_ERR_WITH_NEEDS_KIND))
        val person = deps.people.listAll().firstOrNull { it.id == personId } ?: throw PersonLinkError(uiText(TextKey.PERSON_NOT_FOUND))
        val t = add.add(
            NewTransactionInput(
                amountMinor = amountMinor, occurredAt = date, walletId = walletId, economicKind = kind, merchantName = person.name,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        return try {
            owner.link(t.id, personId, meaning)
        } catch (e: Exception) {
            deps.txns.deleteMany(listOf(t.id))
            throw e
        }
    }

    /** «تراجع» عن «أضف عملية»: فك الربط ومسح العملية اللي اتعملت. */
    suspend fun undoAdd(transactionId: Id) {
        owner.unlink(transactionId)
        deps.txns.deleteMany(listOf(transactionId))
    }
}
