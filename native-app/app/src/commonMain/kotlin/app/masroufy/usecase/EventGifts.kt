package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventError
import app.masroufy.core.EventLink
import app.masroufy.core.EVENT_SHARE_WHOLE
import app.masroufy.core.EventRole
import app.masroufy.core.GiftCategories
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LifeEvent
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.checkEventLink
import app.masroufy.core.eventLinkId
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository
import app.masroufy.port.ZakatPaymentRepository

/**
 * ربط العمليات بالحدث وتسجيل النقوط (OVERRIDES §44.1 و§64).
 * - **نقوط الكاش: عملية لكل اسم** (اختيار المالك): كل نقطة عملية في المحفظة اللي اتختارت (عادةً الكاش) من نفس طريق
 *   الإضافة اليدوية (`AddTransaction`) — جاتلك ⇒ نوعها «نقوط» `event_gift` · إنت اديتها ⇒ «هدية أو مساعدة» `support_gift`.
 * - **عملية موجودة** (تحويل بنكي من شخص مثلًا) تتربط مصروف أو نقطة؛ لو نقطة نوعها بيتأكد زي فوق.
 * - العملية الواحدة **بحدث واحد بس**، والنقطة ما تبقاش عملية متربطة بالمستحقات أو الزكاة (نوعها كان هيتكتب فوقه).
 * - **أي نقطة** (متسجلة هنا أو عملية اتربطت كنقطة) بتاخد تصنيف «هدايا › نقوط» لوحدها (رد المالك §64) ⇒ ما بتظهرش «محتاجة مراجعة».
 * - **المصروف بنسبة** (رد المالك §64): ربط العملية مصروف بياخد نسبة 1..100 (100 = العملية كلها) بتتخزن على الربط.
 */
data class EventGiftsDeps(
    val events: LifeEventRepository,
    val links: EventLinkRepository,
    val txns: TransactionRepository,
    val wallets: WalletRepository,
    val people: PersonRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
    /** تصنيف «هدايا › نقوط» بيتعمل لو مش موجود (`GiftCategories`). */
    val categories: CategoryRepository,
    /** المستحقات والزكاة — اختياري عشان الاختبار؛ التشغيل الحقيقي بيدّيهم دايمًا (زي `DueLinks`). */
    val roscaEntries: RoscaEntryRepository? = null,
    val installmentPayments: InstallmentPaymentRepository? = null,
    val plans: InstallmentPlanRepository? = null,
    val zakatPayments: ZakatPaymentRepository? = null,
)

/** نقطة لاسم واحد. */
data class GiftEntry(val personId: Id, val amountMinor: Halalas)

data class RecordedGift(val transaction: Transaction, val link: EventLink)

class EventGifts(private val deps: EventGiftsDeps) {
    private val add = AddTransaction(AddTransactionDeps(deps.txns, deps.wallets, deps.ids, deps.clock))

    private suspend fun event(id: Id): LifeEvent = deps.events.listAll().firstOrNull { it.id == id } ?: throw EventError(uiText(TextKey.EVENT_NOT_FOUND))

    private fun kindFor(role: EventRole): EconomicKind = if (role == EventRole.GIFT_IN) EconomicKind.EVENT_GIFT else EconomicKind.SUPPORT_GIFT

    private suspend fun inDues(transactionId: Id): Boolean {
        val ids = listOf(transactionId)
        return deps.roscaEntries?.listByTransactionIds(ids).orEmpty().isNotEmpty() ||
            deps.installmentPayments?.listByTransactionIds(ids).orEmpty().isNotEmpty() ||
            deps.plans?.listAll().orEmpty().any { it.receivedTransactionId == transactionId } ||
            deps.zakatPayments?.listByTransactionIds(ids).orEmpty().isNotEmpty()
    }

    /**
     * ربط عملية موجودة بالحدث بدور. النقطة لازم معاها شخص، ونوعها وتصنيفها بيتأكدوا (نقوط / هدية أو مساعدة · «هدايا › نقوط»).
     * [sharePercent] نصيب الحدث من المصروف (1..100، الافتراضي العملية كلها) — النقطة دايمًا 100.
     */
    suspend fun link(eventId: Id, transactionId: Id, role: EventRole, personId: Id? = null, sharePercent: Int = EVENT_SHARE_WHOLE): EventLink {
        val event = event(eventId)
        val txn = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw EventError(uiText(TextKey.EVENT_TXN_NOT_FOUND))
        if (personId != null && deps.people.listAll().none { it.id == personId }) throw EventError(uiText(TextKey.EVENT_PERSON_NOT_FOUND))
        checkEventLink(event, role, personId, txn, deps.links.listByTransactionIds(listOf(transactionId)), sharePercent)
        if (role.isGift && inDues(transactionId)) throw EventError(uiText(TextKey.EVENT_TXN_IN_DUES))
        val now = deps.clock.nowIso()
        val link = EventLink(eventLinkId(txn.id), event.id, txn.id, role, personId, now, sharePercent)
        deps.uow.run {
            deps.links.saveMany(listOf(link))
            if (role.isGift) {
                deps.categories.ensureAll(GiftCategories.defaults())
                deps.txns.update(
                    txn.id,
                    TransactionPatch(
                        economicKind = kindFor(role), economicKindConfirmed = true, categoryId = GiftCategories.EVENT_GIFTS,
                        categoryConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = now,
                    ),
                )
            }
        }
        return link
    }

    /**
     * فك الربط. المصروف بيفضل زي ما هو؛ النقطة نوعها وتصنيفها بيرجعوا «لسه ما اتحددش» ويتسألوا تاني (زي فك القسط — ما بنخمّنش القديم).
     * عملية النقطة نفسها **ما بتتمسحش** (الفلوس دخلت أو خرجت فعلًا).
     */
    suspend fun unlink(eventId: Id, transactionId: Id) {
        val link = deps.links.listByTransactionIds(listOf(transactionId)).firstOrNull { it.eventId == eventId }
            ?: throw EventError(uiText(TextKey.EVENT_LINK_NOT_FOUND))
        deps.uow.run {
            deps.links.deleteMany(listOf(link.id))
            if (link.role.isGift) {
                deps.txns.update(
                    transactionId,
                    TransactionPatch(
                        economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, clearCategoryId = true, categoryConfirmed = false,
                        reviewState = ReviewState.NEEDS_REVIEW, updatedAt = deps.clock.nowIso(),
                    ),
                )
            }
        }
    }

    /**
     * تسجيل نقوط بالأسامي — **عملية لكل اسم** في [walletId] بتاريخ [occurredAt]. [direction] IN = جاتلك (في حدثك بس) ·
     * OUT = إنت اديتها. الكل بيتكتب مع بعض أو ما يتكتبش. اسم العملية = اسم الشخص، والملاحظة = اسم الحدث،
     * والتصنيف «هدايا › نقوط» دايمًا (بيتعمل لو مش موجود) ⇒ العملية مؤكدة ومش «محتاجة مراجعة».
     */
    suspend fun recordGifts(
        eventId: Id,
        direction: Direction,
        walletId: Id,
        occurredAt: IsoDate,
        entries: List<GiftEntry>,
    ): List<RecordedGift> {
        val event = event(eventId)
        val role = if (direction == Direction.IN) EventRole.GIFT_IN else EventRole.GIFT_OUT
        if (role == EventRole.GIFT_IN && !event.mine) throw EventError(uiText(TextKey.EVENT_GIFT_IN_NOT_MINE))
        if (entries.isEmpty() || entries.any { it.amountMinor <= 0 }) throw EventError(uiText(TextKey.EVENT_GIFTS_EMPTY))
        if (entries.map { it.personId }.toSet().size != entries.size) throw EventError(uiText(TextKey.EVENT_GIFT_PERSON_TWICE))
        val names = deps.people.listAll().associate { it.id to it.name }
        if (entries.any { it.personId !in names }) throw EventError(uiText(TextKey.EVENT_PERSON_NOT_FOUND))
        return deps.uow.run {
            deps.categories.ensureAll(GiftCategories.defaults())
            entries.map { e ->
                val txn = add.add(
                    NewTransactionInput(
                        amountMinor = e.amountMinor, occurredAt = occurredAt, walletId = walletId, economicKind = kindFor(role),
                        merchantName = names.getValue(e.personId), categoryId = GiftCategories.EVENT_GIFTS, note = event.name,
                    ),
                )
                checkEventLink(event, role, e.personId, txn, emptyList())
                val link = EventLink(eventLinkId(txn.id), event.id, txn.id, role, e.personId, deps.clock.nowIso())
                deps.links.saveMany(listOf(link))
                RecordedGift(txn, link)
            }
        }
    }
}
