package app.masroufy.wiring

import app.masroufy.core.Id
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.SpaceTransferError
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import app.masroufy.port.SpaceLegWrite
import app.masroufy.port.SpaceTransferWriter
import app.masroufy.ui.screens.operations.OperationsDeps
import app.masroufy.ui.screens.operations.SpaceWallets
import app.masroufy.usecase.EditTransaction
import app.masroufy.usecase.EditTransactionDeps
import app.masroufy.usecase.EventGifts
import app.masroufy.usecase.EventGiftsDeps
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadTransactionsScreenDeps
import app.masroufy.usecase.LoadWithYouNow
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ManageCategoriesDeps
import app.masroufy.usecase.ManageEvents
import app.masroufy.usecase.ManageEventsDeps
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.ManagePersonCircles
import app.masroufy.usecase.ManagePersonCirclesDeps
import app.masroufy.usecase.ManageProjects
import app.masroufy.usecase.ManageRules
import app.masroufy.usecase.ManageRulesDeps
import app.masroufy.usecase.ManageTransfers
import app.masroufy.usecase.ManageTransfersDeps
import app.masroufy.usecase.ProjectsDeps
import app.masroufy.usecase.SetEconomicKind
import app.masroufy.usecase.SetEconomicKindDeps
import app.masroufy.usecase.SpaceBook
import app.masroufy.usecase.TransactionLinkRepos
import app.masroufy.usecase.TransferBetweenSpaces
import app.masroufy.usecase.TransferBetweenSpacesDeps

/**
 * «العمليات» — كل حالة استخدام بتتبني من [AreaContext] بنفس اعتماداتها في اختبارات `:app`. رجول «التحويل لنفسك» ([spaceTransfers]`.legsIn`)
 * بتتدّي للي بيربط عملية بحاجة تانية (التعديل · النوع · الأشخاص · المشاريع · الأحداث) زي التشغيل الحقيقي. **مفيش حساب هنا.**
 */
class OperationsGraph(private val c: AreaContext) : OperationsDeps {
    private val r = c.repos
    private val clock = c.env.clock
    private val ids = c.env.ids

    override val spaceTransfers = TransferBetweenSpaces(
        TransferBetweenSpacesDeps(r.spaceTransfers, c.session.spaceTransferWriter() ?: RefusingWriter, ::book, ids, clock),
    )
    private val legs = spaceTransfers.legsIn(c.space.id)

    override val transactions = LoadTransactionsScreen(LoadTransactionsScreenDeps(r.transactions, r.categories, r.allocations, r.merchants, r.tags, r.transactionTags))
    override val edit = EditTransaction(
        EditTransactionDeps(
            r.transactions, r.categories, r.tags, r.transactionTags, r.uow, ids, clock,
            allocations = r.allocations, settlements = r.settlements, spaceLegs = legs,
        ),
    )
    override val kinds = SetEconomicKind(SetEconomicKindDeps(r.transactions, r.categories, r.uow, clock, legs))
    override val categories = ManageCategories(ManageCategoriesDeps(r.categories, ids))
    override val merchants = ManageRules(ManageRulesDeps(r.rules, r.merchants, r.categories, ids))
    override val people = ManagePeople(
        ManagePeopleDeps(r.people, r.obligations, r.settlements, r.settlementWriter, r.allocations, r.transactions, r.uow, ids, clock, legs),
    )
    override val circles = ManagePersonCircles(ManagePersonCirclesDeps(r.people, r.personProfiles, r.personRelations, clock))

    // `membership` و`setMember` بس (مزامنة قواعد المشاريع شغل شاشة المشاريع) ⇒ مؤشر في الذاكرة كفاية هنا
    override val projects = ManageProjects(
        ProjectsDeps(r.projects, r.projectLinks, r.projectRules, r.transactions, r.allocations, r.categories, ids, clock, app.masroufy.memory.MemorySyncCursor(), legs),
    )
    override val events = ManageEvents(ManageEventsDeps(r.lifeEvents, r.eventLinks, r.transactions, r.people, ids, clock))
    override val eventLinks = EventGifts(
        EventGiftsDeps(
            r.lifeEvents, r.eventLinks, r.transactions, r.wallets, r.people, r.uow, ids, clock, r.categories,
            roscaEntries = r.roscaEntries, installmentPayments = r.installmentPayments, plans = r.installmentPlans, zakatPayments = r.zakatPayments,
            spaceLegs = legs,
        ),
    )
    override val transfers = ManageTransfers(ManageTransfersDeps(r.transactions, r.transferParties, r.people, r.uow, clock))

    override suspend fun payday(): Int = c.shell.profile.load().payday

    override suspend fun wallets(): List<Wallet> = LoadWithYouNow(r.wallets, r.transactions, c.space.currency).load(c.env.today()).wallets.map { it.wallet }

    override suspend fun spaceBooks(): List<SpaceWallets> = openSpaces().map { (s, repos) ->
        SpaceWallets(s, LoadWithYouNow(repos.wallets, repos.transactions, s.currency).load(c.env.today()).wallets.map { it.wallet }, s.id == c.space.id)
    }

    override suspend fun legOf(spaceId: String, transactionId: Id): Transaction? {
        val repos = openSpaces().firstOrNull { it.first.id == spaceId }?.second ?: return null
        val read = EditTransaction(EditTransactionDeps(repos.transactions, repos.categories, repos.tags, repos.transactionTags, repos.uow, ids, clock))
        return read.load(transactionId).transaction
    }

    /** البلاد المفتوحة في الجلسة — والبلد الشغالة دايمًا منهم (الاختبار ممكن ما يدّيهاش). */
    private fun openSpaces() = c.session.spaces().let { all -> if (all.any { it.first.id == c.space.id }) all else listOf(c.space to r) + all }

    private suspend fun book(spaceId: String): SpaceBook? = openSpaces().firstOrNull { it.first.id == spaceId }?.let { (s, repos) ->
        SpaceBook(
            s, repos.transactions, repos.wallets,
            TransactionLinkRepos(
                repos.roscaEntries, repos.installmentPayments, repos.installmentPlans, repos.zakatPayments, repos.eventLinks, repos.projectLinks,
                repos.allocations, repos.obligations, repos.settlements,
            ),
        )
    }
}

/** الجلسة من غير كاتب البلدين ⇒ التسجيل والربط والفك بيرفضوا بـ«غير متاح» (ولا كتابة) بدل ما يتظاهروا إنهم نجحوا. */
private object RefusingWriter : SpaceTransferWriter {
    override suspend fun link(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = throw SpaceTransferError(uiText(TextKey.NOT_AVAILABLE))

    override suspend fun unlink(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = throw SpaceTransferError(uiText(TextKey.NOT_AVAILABLE))
}
