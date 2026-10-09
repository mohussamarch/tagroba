package app.masroufy.wiring

import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectSummary
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.budgetStatus
import app.masroufy.core.eventShareMinor
import app.masroufy.core.projectNetMinor
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney
import app.masroufy.memory.MemorySyncCursor
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.SpaceTransferRepository
import app.masroufy.ui.screens.people.PeopleDeps
import app.masroufy.ui.screens.people.PeopleMoney
import app.masroufy.usecase.EventGifts
import app.masroufy.usecase.EventGiftsDeps
import app.masroufy.usecase.LoadPeopleOverview
import app.masroufy.usecase.LoadPeopleOverviewDeps
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadTransactionsScreenDeps
import app.masroufy.usecase.ManageEventPrep
import app.masroufy.usecase.ManageEventPrepDeps
import app.masroufy.usecase.ManageEvents
import app.masroufy.usecase.ManageEventsDeps
import app.masroufy.usecase.ManageOccasions
import app.masroufy.usecase.ManageOccasionsDeps
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.ManagePersonCircles
import app.masroufy.usecase.ManagePersonCirclesDeps
import app.masroufy.usecase.ManageProjects
import app.masroufy.usecase.PeopleSpaceSource
import app.masroufy.usecase.ProjectsDeps

/**
 * «الأشخاص» (الأشخاص · الأحداث · المشاريع) — **الملف ده بتاع المنطقة بس.** كل حالة استخدام بنفس اعتماداتها في اختبارات `:app`.
 * - رجول «التحويل لنفسك» (§64) بتترفض في الربط (شخص · حدث · مشروع) — [SpaceLegsOf] بيقرا الأزواج بس.
 * - ⚠️ مؤشر قواعد المشاريع (`ProjectsDeps.cursor`) في الذاكرة طول عمر البلد المفتوحة: ضياعه = مراجعة من أقدم قاعدة **من غير تكرار** (ManageProjects)،
 *   فصحيح بس أبطأ. حفظه على الجهاز (`AndroidSyncCursor(…, "projects")`) محتاج خانة في `DeviceEnv` — قرار للدمج (missingLogic).
 */
class PeopleGraph(c: AreaContext) : PeopleDeps {
    private val r = c.repos
    private val env = c.env
    private val legs = SpaceLegsOf(c.space.id, r.spaceTransfers)

    override val overview: LoadPeopleOverview by lazy {
        // البلد الشغالة الأول، وبعدها باقي البلاد المفتوحة (لـ`acrossSpaces`) — كل بلد بديونها وأحداثها، من غير جمع بين عملتين
        val others = runCatching { c.session.spaces() }.getOrDefault(emptyList()).filter { it.first.id != c.space.id }
        val sources = (listOf(c.space to r) + others).map { (s, repos) ->
            PeopleSpaceSource(s, repos.obligations, repos.settlements, repos.lifeEvents, repos.eventLinks, repos.transactions)
        }
        LoadPeopleOverview(LoadPeopleOverviewDeps(r.people, r.personProfiles, r.personRelations, r.occasions, sources))
    }

    override val people = ManagePeople(
        ManagePeopleDeps(r.people, r.obligations, r.settlements, r.settlementWriter, r.allocations, r.transactions, r.uow, env.ids, env.clock, legs),
    )

    override val circles = ManagePersonCircles(ManagePersonCirclesDeps(r.people, r.personProfiles, r.personRelations, env.clock))

    override val occasions = ManageOccasions(ManageOccasionsDeps(r.occasions, r.people, r.lifeEvents, r.eventLinks, r.transactions, env.ids, env.clock))

    override val events = ManageEvents(ManageEventsDeps(r.lifeEvents, r.eventLinks, r.transactions, r.people, env.ids, env.clock))

    override val gifts = EventGifts(
        EventGiftsDeps(
            r.lifeEvents, r.eventLinks, r.transactions, r.wallets, r.people, r.uow, env.ids, env.clock, r.categories,
            r.roscaEntries, r.installmentPayments, r.installmentPlans, r.zakatPayments, legs,
        ),
    )

    override val prep = ManageEventPrep(ManageEventPrepDeps(r.lifeEvents, r.eventLinks, r.eventPrep, r.transactions, r.uow, env.ids, env.clock))

    override val projects = ManageProjects(
        ProjectsDeps(r.projects, r.projectLinks, r.projectRules, r.transactions, r.allocations, r.categories, env.ids, env.clock, MemorySyncCursor(), legs),
    )

    override val dues = loadDues(r)

    override val transactions = LoadTransactionsScreen(
        LoadTransactionsScreenDeps(r.transactions, r.categories, r.allocations, r.merchants, r.tags, r.transactionTags),
    )

    override val money: PeopleMoney = CoreMoney
}

/** الحسابات من `core` بس (الشاشة ما بتحسبش — CLAUDE.md #4). */
internal object CoreMoney : PeopleMoney {
    override fun projectHeadline(kind: ProjectKind, summary: ProjectSummary): Halalas =
        if (kind == ProjectKind.WORK) projectNetMinor(summary) else subtractMoney(summary.spentMinor, summary.receivedMinor)

    override fun eventShare(amountMinor: Halalas, percent: Int): Halalas = eventShareMinor(amountMinor, percent)

    override fun total(amounts: List<Halalas>): Halalas = sumMoney(amounts)

    override fun usedTenthPercent(plannedMinor: Halalas, spentMinor: Halalas): Long = budgetStatus(plannedMinor, spentMinor, null).usedTenthPercent
}

/**
 * رجول «التحويل لنفسك» في البلد دي من سجل الأزواج — للرفض بس (`isLeg` · `legsAmong`). الفك ([detach]) مش شغل المنطقة دي
 * (بيحتاج الكاتب الذرّي — `TransferBetweenSpaces`)، فبيرفض بصراحة لو حد ناداه.
 */
internal class SpaceLegsOf(private val spaceId: String, private val pairs: SpaceTransferRepository) : SpaceTransferLegs {
    override suspend fun pairsOf(transactionIds: List<Id>): List<SpaceTransfer> =
        pairs.listAll().filter { p -> transactionIds.any { p.involves(spaceId, it) } }

    override suspend fun legsAmong(transactionIds: List<Id>): Set<Id> {
        val all = pairs.listAll()
        return transactionIds.filter { id -> all.any { it.involves(spaceId, id) } }.toSet()
    }

    override suspend fun detach(pairs: List<SpaceTransfer>) {
        throw UnsupportedOperationException("PeopleGraph ما بيفكّش أزواج التحويل — TransferBetweenSpaces")
    }
}
