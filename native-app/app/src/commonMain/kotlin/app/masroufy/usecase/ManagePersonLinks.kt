package app.masroufy.usecase

import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.PURCHASE_MEANINGS
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.isPurchaseShaped
import app.masroufy.core.kindForMeaning
import app.masroufy.core.obligationKindFor
import app.masroufy.core.personCategoryIds
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonLinkRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository
import kotlinx.coroutines.sync.withLock

/**
 * ربط عملية بشخص (جولة r05 — شكوى المالك 2026-10-16: «علّمت كتير على كذا عملية بيني وبين شخص إنه دين… ولما دخلت على الشخص الصفحة فاضية»).
 * **أي طريق** بيقول «العملية دي مع الشخص ده» بيعدّي من هنا: «اربطها بـ › شخص» لأي نوع (حتى الشراء — F7) · التصنيف اللي معناه فلوس مع شخص
 * (`personCategoryIds` ⇒ «مع مين؟») · «محتاجة تربطها بشخص» · اقتراحات صفحة الشخص · قواعد الحسابات المربوطة ([PersonRules] — لحظة التسجيل).
 * الربط بيكتب [PersonLink] وأثره في الدفتر في خطوة واحدة: دين جديد ([Obligation]) · سداد على ديونه المفتوحة الأقدم الأول ([Settlement]) ·
 * نصيب في الشراء · أو «مع الشخص ده بس». [unlink] = «فك الربط» و«تراجع» — بيشيل اللي الربط كتبه هو بس (ومعاه ميعاد الدين — F2) ويرجّع النوع.
 */
data class PersonLinksDeps(
    val links: PersonLinkRepository,
    val people: PersonRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val parties: TransferPartyRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
    /** رجول «التحويل لنفسك» (§64) ما بتتربطش بشخص. */
    val spaceLegs: SpaceTransferLegs? = null,
    /** محافظ البلد («أضف عملية مع الشخص ده» — الكاش مسموح). */
    val wallets: WalletRepository? = null,
    /** إضافة عملية يدوي (`AddTransaction`) — null ⇒ «أضف عملية» غير متاح. */
    val add: AddTransaction? = null,
    /** مواعيد الديون (F2: «فك الربط» بيشيل ميعاد دين الربط معاه) — null (اختبارات قديمة) ⇒ مفيش مواعيد. */
    val terms: DebtTermsRepository? = null,
    /** حقايق الزكاة (R3: «فك الربط» بيشيل حقيقة دين الربط معاه) — null (اختبارات قديمة) ⇒ مفيش. */
    val zakatFacts: app.masroufy.port.ZakatFactRepository? = null,
)

class PersonLinkError(message: String) : IllegalStateException(message)

/** نتيجة الربط: [unmatchedMinor] = جزء من «سداد» مالقاش دين مفتوح يتسدد منه (بيتسجل في السجل بس، من غير ما يغيّر الأرصدة). */
data class LinkOutcome(val link: PersonLink, val personName: String, val unmatchedMinor: Halalas)

/** حالة العملية في تفاصيلها: مربوطة بمين · محتاجة شخص (تصنيفها فلوس مع شخص ومفيش ربط) · عليها دين أو سداد من طريق تاني. */
data class TxnPersonStatus(val link: PersonLink?, val personName: String?, val needsPerson: Boolean, val ledgerLinked: Boolean)

/** سطر «محتاجة تربطها بشخص». */
data class NeedsPersonRow(val transaction: Transaction, val categoryName: String)

class ManagePersonLinks(private val deps: PersonLinksDeps) {
    /** صفحة الشخص: «السجل بينا» · الاقتراحات · البحث. */
    val page: LoadPersonPage by lazy { LoadPersonPage(deps, this) }

    /** الحسابات المربوطة بالشخص · «سجّل تحويلاته لوحده» · «أضف عملية مع الشخص ده». */
    val accounts: PersonAccounts by lazy { PersonAccounts(deps, this) }

    /** قواعد الحسابات المربوطة لحظة التسجيل (A2) — `PersonRulesEffect` في رسايل البنك. */
    val rules: PersonRules by lazy { PersonRules(deps, this) }

    /** فهرس طرف التحويل لكل عملية (صفحة الشخص ما بتقراش كل العمليات كل مرة — بيتحدّث بالجديد بس). */
    internal val partyIndex: PartyTxnIndex by lazy { PartyTxnIndex(deps.txns) }

    /**
     * [amountMinor] للشراء بس (نصيب الشخص — F7: الشراء بيعدّي من هنا زي أي عملية)؛ غيره = المبلغ كله. العملية اللي عليها دين أو سداد أو نصيب
     * من طريق تاني ⇒ مرفوضة بأي معنى (F9 — من غيرها السجل كان بيخسر سطر الدين والصافي يبوظ).
     */
    suspend fun link(transactionId: Id, personId: Id, meaning: PersonMeaning, auto: Boolean = false, amountMinor: Halalas? = null): LinkOutcome =
        LINK_LOCK.withLock { linkLocked(transactionId, personId, meaning, auto, amountMinor) }

    private suspend fun linkLocked(transactionId: Id, personId: Id, meaning: PersonMeaning, auto: Boolean, amountMinor: Halalas?): LinkOutcome {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw PersonLinkError(uiText(TextKey.TXN_NOT_FOUND))
        if (deps.spaceLegs?.isLeg(transactionId) == true) throw PersonLinkError(uiText(TextKey.SPACE_TRANSFER_LEG_LOCKED))
        // N3 · R3: تحويل بين محافظك أو نص زوج «رجعت» ⇒ أي معنى مع شخص كان هيعدّ نفس الفلوس مرتين
        if (ownMoneyMove(t, deps.txns.findByIds(listOf(t.id + OWN_LEG_SUFFIX)).isNotEmpty())) throw PersonLinkError(uiText(UiKey.PLINK_ERR_OWN_MONEY))
        val people = deps.people.listAll()
        val person = people.firstOrNull { it.id == personId } ?: throw PersonLinkError(uiText(TextKey.PERSON_NOT_FOUND))
        if (meaning.direction != null && meaning.direction != t.observedDirection) throw PersonLinkError(uiText(UiKey.PLINK_ERR_DIRECTION))
        // F11: الفحص جوه القفل — ربطتين في نفس اللحظة ما يكتبوش دينين
        deps.links.listByTransactionIds(listOf(t.id)).firstOrNull()?.let { old ->
            throw PersonLinkError(uiText(UiKey.PLINK_ERR_ALREADY, people.firstOrNull { it.id == old.personId }?.name.orEmpty()))
        }
        if (ledgerLinkedIn(deps, listOf(t.id)).isNotEmpty()) throw PersonLinkError(uiText(UiKey.PLINK_ERR_HAS_DEBT))
        val purchase = isPurchaseShaped(t)
        if (purchase && meaning !in PURCHASE_MEANINGS) throw PersonLinkError(uiText(UiKey.PLINK_ERR_DIRECTION))
        val amount = if (purchase && amountMinor != null) amountMinor else t.amountMinor
        if (amount <= 0L || amount > t.amountMinor) throw PersonLinkError(uiText(UiKey.PLINK_ERR_AMOUNT))
        val now = deps.clock.nowIso()
        val obligation = obligationKindFor(meaning)?.let { Obligation(deps.ids.next("obl"), personId, t.id, it, amount, t.currency) }
        val allocation = if (purchase && (meaning == PersonMeaning.LENT || meaning == PersonMeaning.GAVE)) allocationOf(deps, t, personId, meaning, amount) else null
        val (settlements, unmatched) = when (meaning) {
            PersonMeaning.COLLECTED -> settleOldestOf(deps, personId, t, receivable = true)
            PersonMeaning.REPAID -> settleOldestOf(deps, personId, t, receivable = false)
            else -> emptyList<Settlement>() to 0L
        }
        val newKind = kindForMeaning(meaning, purchase)
        val changesKind = newKind != null &&
            (newKind != t.economicKind || !t.economicKindConfirmed || t.reviewState != ReviewState.CONFIRMED || t.suggestedKind != null)
        val link = PersonLink(
            t.id, personId, meaning, amount, now, obligation?.id, allocation?.id, settlements.map { it.id },
            kindBefore = if (changesKind) t.economicKind else null,
            kindConfirmedBefore = if (changesKind) t.economicKindConfirmed else null,
            reviewBefore = if (changesKind) t.reviewState else null,
            suggestedBefore = if (changesKind) t.suggestedKind else null,
            auto = auto,
        )
        deps.uow.run {
            deps.links.saveMany(listOf(link))
            obligation?.let { deps.obligations.saveMany(listOf(it)) }
            allocation?.let { deps.allocations.saveMany(listOf(it)) }
            if (settlements.isNotEmpty()) deps.settlements.saveMany(settlements)
            if (changesKind) {
                deps.txns.update(
                    t.id,
                    TransactionPatch(economicKind = newKind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, clearSuggestedKind = true, updatedAt = now),
                )
            }
        }
        return LinkOutcome(link, person.name, unmatched)
    }

    /**
     * «فك الربط» و«تراجع»: بيشيل الرابط واللي هو كتبه بس (الدين وميعاده · النصيب · السدادات) ويرجّع نوع العملية زي ما كان. الدين اللي اتسدد
     * منه حاجة من مكان تاني ⇒ مرفوض بسببه. [remember] (المالك هو اللي فك — A2/F3) ⇒ قواعد الحساب ما ترجعش تربطها لوحدها. false = مش مربوطة.
     */
    suspend fun unlink(transactionId: Id, remember: Boolean = true): Boolean = LINK_LOCK.withLock {
        val link = deps.links.listByTransactionIds(listOf(transactionId)).firstOrNull() ?: return@withLock false
        link.obligationId?.let { id -> if (deps.settlements.listByObligations(listOf(id)).isNotEmpty()) throw PersonLinkError(uiText(UiKey.PLINK_ERR_HAS_PAYMENTS)) }
        val now = deps.clock.nowIso()
        deps.uow.run {
            deps.links.deleteMany(listOf(transactionId))
            link.obligationId?.let { id ->
                deps.terms?.let { terms -> if (terms.listAll().any { it.obligationId == id }) terms.remove(id) }
                deps.zakatFacts?.let { z -> if (z.listAll().any { it.subjectId == id }) z.remove(id) }
                deps.obligations.deleteMany(listOf(id))
            }
            link.allocationId?.let { deps.allocations.deleteMany(listOf(it)) }
            if (link.settlementIds.isNotEmpty()) deps.settlements.deleteMany(link.settlementIds)
            revertLinkKind(deps.txns, link, now)
            if (remember) rememberSkip(deps, transactionId)
        }
        true
    }

    suspend fun statusOf(transactionId: Id): TxnPersonStatus {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: return TxnPersonStatus(null, null, needsPerson = false, ledgerLinked = false)
        val link = deps.links.listByTransactionIds(listOf(transactionId)).firstOrNull()
        val name = link?.let { l -> deps.people.listAll().firstOrNull { it.id == l.personId }?.name }
        val ledger = link == null && ledgerLinkedIn(deps, listOf(transactionId)).isNotEmpty()
        val personCategory = t.categoryId != null && t.categoryId in personCategoryIds(deps.categories.listAll())
        return TxnPersonStatus(link, name, needsPerson = personCategory && link == null && !ledger, ledgerLinked = ledger)
    }

    /** «محتاجة تربطها بشخص»: العمليات اللي تصنيفها فلوس مع شخص ومش مربوطة بحد بأي طريق. الأحدث الأول. */
    suspend fun needsPerson(): List<NeedsPersonRow> {
        val categories = deps.categories.listAll()
        val ids = personCategoryIds(categories)
        if (ids.isEmpty()) return emptyList()
        val labelled = everything().filter { it.categoryId in ids && !it.id.endsWith(OWN_LEG_SUFFIX) }
        if (labelled.isEmpty()) return emptyList()
        val linked = linkedAmong(labelled.map { it.id })
        val names = categories.associate { it.id to it.name }
        return labelled.filter { it.id !in linked }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenBy { it.id })
            .map { NeedsPersonRow(it, names[it.categoryId].orEmpty()) }
    }

    internal suspend fun everything(): List<Transaction> = deps.txns.listByDateRange(EARLIEST, LATEST)

    /** العمليات المربوطة بشخص بأي طريق (رابط · دين أصله العملية · سداد · نصيب). */
    internal suspend fun linkedAmong(ids: List<Id>): Set<Id> =
        if (ids.isEmpty()) emptySet() else deps.links.listByTransactionIds(ids).map { it.transactionId }.toSet() + ledgerLinkedIn(deps, ids)

    internal companion object {
        const val EARLIEST = "0000-01-01"
        const val LATEST = "9999-12-31"
    }
}
