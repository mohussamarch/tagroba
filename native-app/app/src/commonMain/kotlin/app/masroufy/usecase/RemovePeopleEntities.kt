package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.employerByPayer
import app.masroufy.core.uiText

/** كل لوحات المسح في مكان واحد (`ShellDeps.removals`) — نفس [RemovalDeps] للكل. */
class Removals(d: RemovalDeps) {
    val wallet: EntityRemover = RemoveWallet(d)
    val asset: EntityRemover = RemoveAsset(d)
    val plan: EntityRemover = RemoveInstallmentPlan(d)
    val rosca: EntityRemover = RemoveRosca(d)
    val subscription: EntityRemover = RemoveSubscription(d)
    val person: EntityRemover = RemovePerson(d)
    val merchant: EntityRemover = RemoveMerchant(d)
    val incomeSource: EntityRemover = RemoveIncomeSource(d)
    val category: EntityRemover = RemoveCategory(d)
}

/**
 * الشخص: العمليات = المربوطة بيه + اللي عملت ديونه أو سدّدتها + نصيبه في عمليات + الديون القديمة من غير عملية.
 * - **ديون مفتوحة** ⇒ «امسحه وسيب العمليات» مقفول وبيقول ليه (الدين المفتوح محتاج صاحبه).
 * - «سيب العمليات»: ربط العمليات بيه بيتشال وعليها العلامة. لو ليه نصيب في عمليات أو ديون مقفولة ⇒ المستند بيفضل **مؤرشف** (من غير ما يظهر)
 *   عشان النصيب يفضل محسوب صح والمجاميع ما تتغيرش والنسخة الشاملة ما تلاقيش رابط مقطوع؛ من غيرهم ⇒ بيتمسح.
 * - «هو وعملياته»: العمليات بتتمسح (`OpsKit.erase`) وكل ديونه وسداداتها ومواعيدها.
 */
class RemovePerson internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    private class Graph(
        val obligations: List<app.masroufy.core.Obligation>,
        val settlements: List<app.masroufy.core.Settlement>,
        val links: List<app.masroufy.core.PersonLink>,
        val allocations: List<app.masroufy.core.PersonAllocation>,
        val txnIds: List<Id>,
        val open: Boolean,
    ) {
        val count: Int get() = txnIds.size + obligations.count { it.originTransactionId == null }
    }

    private suspend fun graph(id: Id): Graph {
        val obligations = d.obligations.listByPerson(id)
        val settlements = if (obligations.isEmpty()) emptyList() else d.settlements.listByObligations(obligations.map { it.id })
        val links = d.links.personLinks?.listByPerson(id).orEmpty()
        val base = (links.map { it.transactionId } + obligations.mapNotNull { it.originTransactionId } + settlements.map { it.transactionId }).distinct()
        val allocations = if (base.isEmpty()) emptyList() else d.allocations.listByTransactionIds(base).filter { it.personId == id }
        val open = obligations.any { o -> settlements.filter { it.obligationId == o.id }.sumOf { it.amountMinor } < o.originalMinor }
        return Graph(obligations, settlements, links, allocations, (base + allocations.map { it.transactionId }).distinct(), open)
    }

    override suspend fun ask(id: Id): RemovalAsk {
        val p = d.people.listAll().firstOrNull { it.id == id } ?: removalGone()
        val g = graph(id)
        return askOf(p.name, g.count, linkedChoices(archive = true), keepBlocked = if (g.open) uiText(R06Key.DEL_KEEP_BLOCKED_DEBTS) else null)
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val p = d.people.listAll().firstOrNull { it.id == id } ?: removalGone()
        if (choice == RemoveChoice.ARCHIVE) {
            d.people.save(p.copy(archived = true))
            return RemovalDone(choice, p.name) { d.people.save(p) }
        }
        val g = graph(id)
        if (choice == RemoveChoice.KEEP_OPERATIONS && g.open) throw DuesRemovalRefused(uiText(R06Key.DEL_KEEP_BLOCKED_DEBTS))
        if (choice == RemoveChoice.WITH_OPERATIONS) {
            val erased = kit.erase(g.txnIds, "person")
            val terms = d.debtTerms?.listAll().orEmpty().filter { t -> g.obligations.any { it.id == t.obligationId } }
            val restSettlements = if (g.obligations.isEmpty()) emptyList() else d.settlements.listByObligations(g.obligations.map { it.id })
            val obligations = d.obligations.listByPerson(id)
            d.uow.run {
                if (restSettlements.isNotEmpty()) d.settlements.deleteMany(restSettlements.map { it.id })
                for (t in terms) d.debtTerms?.remove(t.obligationId)
                if (obligations.isNotEmpty()) d.obligations.deleteMany(obligations.map { it.id })
                d.people.delete(id)
            }
            return RemovalDone(choice, p.name, erased.backup) {
                d.people.save(p)
                if (obligations.isNotEmpty()) d.obligations.saveMany(obligations)
                if (restSettlements.isNotEmpty()) d.settlements.saveMany(restSettlements)
                for (t in terms) d.debtTerms?.save(t)
                kit.restore(erased)
            }
        }
        // «امسح» (مالوش عمليات) أو «سيب العمليات»
        val kept = kit.keep(d.txns.findByIds(g.txnIds), removedMarker("person", p.name))
        if (g.links.isNotEmpty()) d.links.personLinks?.deleteMany(g.links.map { it.transactionId })
        val tombstone = g.allocations.isNotEmpty() || g.obligations.isNotEmpty()
        if (tombstone) d.people.save(p.copy(archived = true)) else d.people.delete(id)
        return RemovalDone(choice, p.name) {
            d.people.save(p)
            if (g.links.isNotEmpty()) d.links.personLinks?.saveMany(g.links)
            kit.unkeep(kept)
        }
    }
}

/** التاجر المحفوظ: العمليات **بتفضل بأساميها** — المسح بيشيل المستند بس (ومعرّفه من العمليات). مفيش «امسح عملياته». */
class RemoveMerchant internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    override suspend fun ask(id: Id): RemovalAsk {
        val m = d.merchants.listAll().firstOrNull { it.id == id } ?: removalGone()
        return askOf(m.displayName, kit.all().count { it.merchantId == id }, linkedChoices(archive = false, withOps = false))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val m = d.merchants.listAll().firstOrNull { it.id == id } ?: removalGone()
        val kept = kit.keep(kit.all().filter { it.merchantId == id }, removedMarker("merchant", m.displayName)) {
            it.copy(merchantId = null, rawMerchantName = it.rawMerchantName ?: m.displayName)
        }
        d.merchants.delete(id)
        return RemovalDone(RemoveChoice.KEEP_OPERATIONS.takeIf { kept.isNotEmpty() } ?: RemoveChoice.DELETE, m.displayName) {
            d.merchants.saveMany(listOf(m))
            kit.unkeep(kept)
        }
    }
}

/** مصدر الدخل أو جهة العمل: العمليات = الإيداعات اللي اتنسبت له (`employerByPayer`). مفيش أرشفة غير «اقفل» بتاريخ (مش إخفاء). */
class RemoveIncomeSource internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    private suspend fun rows(s: app.masroufy.core.IncomeSource) = kit.all().filter { employerByPayer(it, listOf(s)) != null }

    override suspend fun ask(id: Id): RemovalAsk {
        val s = d.incomeSources.listAll().firstOrNull { it.id == id } ?: removalGone()
        return askOf(s.name, rows(s).size, linkedChoices(archive = false))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val s = d.incomeSources.listAll().firstOrNull { it.id == id } ?: removalGone()
        val rows = rows(s)
        val kept = if (choice == RemoveChoice.WITH_OPERATIONS) emptyList() else kit.keep(rows, removedMarker("income", s.name))
        val erased = if (choice == RemoveChoice.WITH_OPERATIONS) kit.erase(rows.map { it.id }, "income") else ErasedOps()
        d.incomeSources.delete(id)
        return RemovalDone(choice, s.name, erased.backup) {
            d.incomeSources.saveMany(listOf(s))
            kit.restore(erased)
            kit.unkeep(kept)
        }
    }
}

/**
 * التصنيف: بدل «امسح عملياته» فيه «انقل العمليات لتصنيف تاني» (قاعدة المالك). الأرشفة = «إخفاء» الموجود (`active = false`).
 * «سيب العمليات» ⇒ من غير تصنيف وعليها العلامة. القواعد اللي بتحط التصنيف ده بتتنقل معاه أو بتتشال. تصنيف تحته فرعيات ⇒ مرفوض.
 */
class RemoveCategory internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    override suspend fun ask(id: Id): RemovalAsk {
        val all = d.categories.listAll()
        val c = all.firstOrNull { it.id == id } ?: removalGone()
        if (all.any { it.parentId == id }) return RemovalAsk(c.name, 0, emptyList(), refused = uiText(R06Key.DEL_REFUSED_CHILDREN))
        val targets = all.filter { it.active && it.id != id }.map { RemovalTarget(it.id, it.name) }
        val count = kit.all().count { it.categoryId == id }
        val choices = listOf(RemoveChoice.ARCHIVE, RemoveChoice.MOVE_OPERATIONS, RemoveChoice.KEEP_OPERATIONS)
        return askOf(c.name, count, choices).copy(targets = targets)
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val all = d.categories.listAll()
        val c = all.firstOrNull { it.id == id } ?: removalGone()
        if (all.any { it.parentId == id }) throw DuesRemovalRefused(uiText(R06Key.DEL_REFUSED_CHILDREN))
        if (choice == RemoveChoice.ARCHIVE) {
            d.categories.save(c.copy(active = false))
            return RemovalDone(choice, c.name) { d.categories.save(c) }
        }
        val target = if (choice == RemoveChoice.MOVE_OPERATIONS) all.firstOrNull { it.id == moveTo && it.id != id } ?: removalGone() else null
        val rows = kit.all().filter { it.categoryId == id }
        val rules = d.rules?.listAll().orEmpty().filter { it.categoryId == id }
        val now = d.clock.nowIso()
        val kept = if (target != null) {
            if (rows.isNotEmpty()) d.txns.saveMany(rows.map { it.copy(categoryId = target.id, updatedAt = now) })
            if (rules.isNotEmpty()) d.rules?.saveMany(rules.map { it.copy(categoryId = target.id) })
            rows
        } else {
            if (rules.isNotEmpty()) d.rules?.deleteMany(rules.map { it.id })
            kit.keep(rows, removedMarker("category", c.name)) { it.copy(categoryId = null, categoryConfirmed = false) }
        }
        d.categories.delete(id)
        return RemovalDone(choice, c.name) {
            d.categories.save(c)
            kit.unkeep(kept)
            if (rules.isNotEmpty()) d.rules?.saveMany(rules)
        }
    }
}
