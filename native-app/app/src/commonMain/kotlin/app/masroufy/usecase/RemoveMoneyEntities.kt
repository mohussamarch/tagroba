package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.recurringKey
import app.masroufy.core.uiText

/** r06-delete (§118-1): الحاجة مش موجودة (اتمسحت من جهاز تاني مثلًا). */
internal fun removalGone(): Nothing = throw DuesRemovalRefused(uiText(R06Key.REMOVE_GONE))

/** ممسوح من غير عمليات ⇒ [RemoveChoice.DELETE] بس، وإلا الاختيارات. */
internal fun askOf(name: String, count: Int, choices: List<RemoveChoice>, keepBlocked: String? = null) =
    if (count == 0) RemovalAsk(name, 0, listOf(RemoveChoice.DELETE)) else RemovalAsk(name, count, choices, keepBlocked)

/**
 * الحساب أو المحفظة: العمليات = اللي على الحساب أو محوّلة له. الأرشفة = «إخفاء» (§112-1 — مخفي من الرئيسية ومن «معك الآن»).
 * المسح بيشيل ربط مرسلي رسايل البنك بالحساب (رسايلهم ترجع «مالوش حساب» وتستنى) — وآخر 4 أرقامه جوه مستنده فبتتشال معاه. «تراجع» بيرجّع الاتنين.
 */
class RemoveWallet internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    private suspend fun rows(id: Id) = kit.all().filter { it.walletId == id || it.transferToWalletId == id }

    override suspend fun ask(id: Id): RemovalAsk {
        val w = d.wallets.findById(id) ?: removalGone()
        return askOf(w.name, rows(id).size, linkedChoices(archive = true))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val w = d.wallets.findById(id) ?: removalGone()
        if (choice == RemoveChoice.ARCHIVE) {
            d.wallets.save(w.copy(hiddenOnHome = true))
            return RemovalDone(choice, w.name) { d.wallets.save(w) }
        }
        val rows = rows(id)
        val senders = d.smsInbox?.senderWallets(d.spaceId).orEmpty().filterValues { it == id }.keys.toList()
        var kept = emptyList<app.masroufy.core.Transaction>()
        var erased = ErasedOps()
        when (choice) {
            RemoveChoice.WITH_OPERATIONS -> erased = kit.erase(rows.map { it.id }, "wallet")
            else -> kept = kit.keep(rows, removedMarker("wallet", w.name)) {
                it.copy(walletId = it.walletId.takeIf { v -> v != id }, transferToWalletId = it.transferToWalletId.takeIf { v -> v != id })
            }
        }
        for (s in senders) d.smsInbox?.setSenderWallet(d.spaceId, s, null)
        d.wallets.delete(id)
        return RemovalDone(choice, w.name, erased.backup) {
            d.wallets.save(w)
            kit.unkeep(kept)
            kit.restore(erased)
            for (s in senders) d.smsInbox?.setSenderWallet(d.spaceId, s, id)
        }
    }
}

/**
 * الأصل: العمليات = صفقات الشرا والبيع (`assetLots` · `assetSales`). «امسحه هو وعملياته» بيمسح كمان العمليات اللي الصفقات مربوطة بيها
 * (ومنها عملية الفلوس اللي «بيع» عملها — `SellAsset`). الأرشفة = أرشفة الأصل الموجودة.
 */
class RemoveAsset internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    override suspend fun ask(id: Id): RemovalAsk {
        val a = d.assets.listAll().firstOrNull { it.id == id } ?: removalGone()
        val n = d.links.assetLots.listByAsset(id).size + d.links.assetSales.listByAsset(id).size
        return askOf(a.name, n, linkedChoices(archive = true))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val a = d.assets.listAll().firstOrNull { it.id == id } ?: removalGone()
        if (choice == RemoveChoice.ARCHIVE) {
            d.assets.save(a.copy(archived = true))
            return RemovalDone(choice, a.name) { d.assets.save(a) }
        }
        val lots = d.links.assetLots.listByAsset(id)
        val sales = d.links.assetSales.listByAsset(id)
        val txnIds = (lots.mapNotNull { it.transactionId } + sales.mapNotNull { it.transactionId }).distinct()
        d.uow.run {
            if (lots.isNotEmpty()) d.links.assetLots.deleteMany(lots.map { it.id })
            if (sales.isNotEmpty()) d.links.assetSales.deleteMany(sales.map { it.id })
        }
        val kept = if (choice == RemoveChoice.WITH_OPERATIONS) emptyList() else kit.keep(d.txns.findByIds(txnIds), removedMarker("asset", a.name))
        val erased = if (choice == RemoveChoice.WITH_OPERATIONS) kit.erase(txnIds, "asset") else ErasedOps()
        d.assets.delete(id)
        return RemovalDone(choice, a.name, erased.backup) {
            d.assets.save(a)
            kit.restore(erased)
            kit.unkeep(kept)
            if (lots.isNotEmpty()) d.links.assetLots.saveMany(lots)
            if (sales.isNotEmpty()) d.links.assetSales.saveMany(sales)
        }
    }
}

/** خطة التقسيط (و«ادفع بعدين»): العمليات = الدفعات المربوطة + عملية استلام المبلغ لو موجودة. مفيش أرشفة للخطط لسه. */
class RemoveInstallmentPlan internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    private suspend fun txnIds(id: Id, received: Id?) = (d.links.installmentPayments.listByPlan(id).map { it.transactionId } + listOfNotNull(received)).distinct()

    override suspend fun ask(id: Id): RemovalAsk {
        val p = d.links.installmentPlans.listAll().firstOrNull { it.id == id } ?: removalGone()
        return askOf(p.name, txnIds(id, p.receivedTransactionId).size, linkedChoices(archive = false))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val p = d.links.installmentPlans.listAll().firstOrNull { it.id == id } ?: removalGone()
        val payments = d.links.installmentPayments.listByPlan(id)
        val ids = txnIds(id, p.receivedTransactionId)
        if (payments.isNotEmpty()) d.links.installmentPayments.deleteMany(payments.map { it.id })
        val kept = if (choice == RemoveChoice.WITH_OPERATIONS) emptyList() else kit.keep(d.txns.findByIds(ids), removedMarker("plan", p.name))
        val erased = if (choice == RemoveChoice.WITH_OPERATIONS) kit.erase(ids, "plan") else ErasedOps()
        d.links.installmentPlans.delete(id)
        return RemovalDone(choice, p.name, erased.backup) {
            d.links.installmentPlans.save(p)
            kit.restore(erased)
            kit.unkeep(kept)
            if (payments.isNotEmpty()) d.links.installmentPayments.saveMany(payments)
        }
    }
}

/** الجمعية: العمليات = مدخلاتها (الأقساط والقبض). مفيش أرشفة للجمعيات لسه. */
class RemoveRosca internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    override suspend fun ask(id: Id): RemovalAsk {
        val r = d.roscas.listAll().firstOrNull { it.id == id } ?: removalGone()
        return askOf(r.name, d.links.roscaEntries.listByRosca(id).map { it.transactionId }.distinct().size, linkedChoices(archive = false))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val r = d.roscas.listAll().firstOrNull { it.id == id } ?: removalGone()
        val entries = d.links.roscaEntries.listByRosca(id)
        val ids = entries.map { it.transactionId }.distinct()
        if (entries.isNotEmpty()) d.links.roscaEntries.deleteMany(entries.map { it.id })
        val kept = if (choice == RemoveChoice.WITH_OPERATIONS) emptyList() else kit.keep(d.txns.findByIds(ids), removedMarker("rosca", r.name))
        val erased = if (choice == RemoveChoice.WITH_OPERATIONS) kit.erase(ids, "rosca") else ErasedOps()
        d.roscas.delete(id)
        return RemovalDone(choice, r.name, erased.backup) {
            d.roscas.save(r)
            kit.restore(erased)
            kit.unkeep(kept)
            if (entries.isNotEmpty()) d.links.roscaEntries.saveMany(entries)
        }
    }
}

/**
 * الاشتراك أو الفاتورة: العمليات = الخصومات من نفس المحل بنفس العملة (`recurringKey` — نفس ربط «المستحقات»). الأرشفة = «وقّف» الموجودة.
 */
class RemoveSubscription internal constructor(private val d: RemovalDeps) : EntityRemover {
    private val kit = OpsKit(d)

    private suspend fun rows(item: app.masroufy.core.RecurringItem) =
        kit.all().filter { it.observedDirection == Direction.OUT && it.currency == item.currency && recurringKey(it) == item.merchantKey }

    override suspend fun ask(id: Id): RemovalAsk {
        val item = d.recurring.listAll().firstOrNull { it.id == id } ?: removalGone()
        return askOf(item.name, rows(item).size, linkedChoices(archive = true))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val item = d.recurring.listAll().firstOrNull { it.id == id } ?: removalGone()
        if (choice == RemoveChoice.ARCHIVE) {
            d.recurring.save(item.copy(active = false))
            return RemovalDone(choice, item.name) { d.recurring.save(item) }
        }
        val rows = rows(item)
        val kept = if (choice == RemoveChoice.WITH_OPERATIONS) emptyList() else kit.keep(rows, removedMarker("subscription", item.name))
        val erased = if (choice == RemoveChoice.WITH_OPERATIONS) kit.erase(rows.map { it.id }, "subscription") else ErasedOps()
        d.recurring.delete(id)
        return RemovalDone(choice, item.name, erased.backup) {
            d.recurring.save(item)
            kit.restore(erased)
            kit.unkeep(kept)
        }
    }
}
