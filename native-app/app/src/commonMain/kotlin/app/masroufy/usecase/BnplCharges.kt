package app.masroufy.usecase

import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplOpenPlan
import app.masroufy.core.BnplPreview
import app.masroufy.core.BnplProvider
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuesCategories
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.InstallmentError
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.IsoDate
import app.masroufy.core.Transaction
import app.masroufy.core.bnplLinkAmount
import app.masroufy.core.bnplMatch
import app.masroufy.core.bnplPreview
import app.masroufy.core.bnplProviderOf
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.dueProgress
import app.masroufy.core.installmentSchedule
import app.masroufy.core.parseIsoDate
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney
import app.masroufy.core.toDayNumber
import app.masroufy.port.TransactionPatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * r05 + r051 — خصومات «اشتري دلوقتي وادفع بعدين» (تابي · تمارا · مدفوع · مس باي · فاليو · سيمبل · سهولة …) بعد ما العملية تتسجل (رسالة
 * بنك أو «+» بإيدك — اللوحة بتطلع لوحدها من الهيكل `BnplHost`):
 * - [settle]: كل خصم من مزوّد مالوش رد ⇒ نفس قسط خطة مفتوحة للمزوّد (±هللة) ⇒ **بيتربط لوحده** بالقسط الجاي ([BnplSettle.autoLinked] —
 *   «تراجع» = [undoAutoLink]) · غير كده ⇒ سؤال ([BnplAsk]). العملية المربوطة بحاجة تانية (جمعية · زكاة · نقطة · رجل تحويل) ما بتتعرضش،
 *   وربط واحدة لو وقع ما بيوقفش الباقي.
 * - [newPurchase]: الخصم = **أول قسط** (§82-20) ⇒ خطة تقسيط مشتريات: الإجمالي = القسط × العدد، أول ميعاد = يوم الخصم، والباقي أقساط جاية
 *   بتظهر في «المستحقات» و«القادم» والتنبيهات (`ManageInstallments.saveCounted` · `remindDayBefore`).
 * - [installmentOf]: «ده قسط من المشترى ده» · [notInstallment]: «مش قسط — عملية عادية». «تخطّي» و«بعدين» ما بيكتبوش حاجة (النموذج).
 * - [settle] و[answer] و[undoAutoLink] **ورا بعض** (قفل واحد): الفحص والكتابة ما يتداخلوش ⇒ عمرها ما تاخد دفعتين لخطتين.
 * الحساب = قواعد الأقساط (§56): القسط مصروف بعمليته، والمشترى نفسه مالوش عملية ⇒ مفيش عدّ مرتين.
 */
class BnplCharges(private val deps: BnplChargesDeps) {
    private val lock = Mutex()

    private suspend fun openPlans(today: IsoDate): List<BnplOpenPlan> {
        val plans = deps.plans.listAll().filter { it.kind == InstallmentKind.PURCHASE_PLAN && bnplProviderOf(it.provider) != null }
        return plans.map { plan ->
            val paid = sumMoney(deps.payments.listByPlan(plan.id).map { it.amountMinor })
            val p = dueProgress(installmentSchedule(plan), paid, today)
            BnplOpenPlan(plan, subtractMoney(plan.totalMinor, paid), p.nextDueAt, p.paidCount, p.count)
        }
    }

    /** خصومات المزوّدين اللي لسه ما اتقررتش ومش مربوطة بأي حاجة، الأقدم الأول (القواعد عند [BNPL_RECENT_DAYS]). */
    private suspend fun pending(today: IsoDate): List<Pair<Transaction, BnplProvider>> {
        val day = toDayNumber(parseIsoDate(today))
        val recent = dayNumberToIso(day - BNPL_RECENT_DAYS)
        val near = deps.txns.listByDateRange(dayNumberToIso(day - BNPL_LOOKBACK_DAYS), dayNumberToIso(day + 1)).mapNotNull { t ->
            val provider = bnplProviderOf(t.rawMerchantName ?: t.rawDescription)
            val open = t.economicKindConfirmed.not() || t.createdAt.take(10) >= recent
            val decided = t.bnplDecided == true || t.categoryId == DuesCategories.PURCHASE_PLANS
            if (provider == null || t.observedDirection != Direction.OUT || decided || !open) null else t to provider
        }
        if (near.isEmpty()) return emptyList()
        val taken = deps.installments.linkedElsewhere(near.map { it.first.id })
        return near.filter { it.first.id !in taken }.sortedWith(compareBy({ it.first.occurredAt }, { it.first.id }))
    }

    /** يربط اللي يتربط لوحده (بالترتيب — كل ربط بيغيّر الباقي والقسط الجاي) ويرجّع الأسئلة. */
    suspend fun settle(): BnplSettle = lock.withLock { settleLocked() }

    private suspend fun settleLocked(): BnplSettle {
        val today = deps.today()
        val pending = pending(today)
        if (pending.isEmpty()) return BnplSettle(emptyMap(), emptyList())
        val linked = LinkedHashMap<Id, Id>()
        val auto = mutableListOf<BnplLinked>()
        val asks = mutableListOf<BnplAsk>()
        var names: Map<Id, String>? = null
        suspend fun walletOf(t: Transaction): String? {
            val all = names ?: deps.wallets?.listAll().orEmpty().associate { it.id to it.name }.also { names = it }
            return t.walletId?.let { all[it] }
        }
        var open = openPlans(today)
        for ((t, provider) in pending) {
            when (val m = bnplMatch(provider, t.amountMinor, t.currency, open, t.id)) {
                is BnplMatch.NextInstallment -> {
                    val plan = open.first { it.plan.id == m.planId }
                    // خصم واحد ما بيتربطش (اتربط بحاجة في نفس اللحظة · عملته اتغيرت) ⇒ يتساب لوحده، والباقي يكمّل
                    try {
                        deps.installments.link(m.planId, t.id, bnplLinkAmount(t.amountMinor, plan.remainingMinor))
                    } catch (_: DueLinkError) {
                        continue
                    } catch (_: InstallmentError) {
                        continue
                    }
                    linked[t.id] = m.planId
                    open = openPlans(today)
                    val after = open.first { it.plan.id == m.planId }
                    auto += BnplLinked(t.id, m.planId, plan.plan.name, after.paidCount.coerceAtLeast(1), after.count, t.bnplBefore())
                }
                BnplMatch.NewPurchase -> asks += BnplAsk(t, provider, m, emptyList(), walletName = walletOf(t))
                is BnplMatch.AskWhich -> {
                    val mine = open.filter { it.plan.id in m.planIds }
                    asks += BnplAsk(t, provider, m, mine.map { it.plan }, mine.map { optionOf(it, t.amountMinor) }, walletOf(t))
                }
            }
        }
        return BnplSettle(linked, asks, auto)
    }

    private fun optionOf(o: BnplOpenPlan, amountMinor: Halalas) = BnplOption(
        o.plan.id, o.plan.name, o.plan.installmentMinor, o.plan.currency, o.count - o.paidCount, o.count, o.nextDueAt,
        subtractMoney(amountMinor, o.plan.installmentMinor),
    )

    /** «خطتك» في لوحة «أول خصم» وهو بيختار — من الجدول المشترك (نفس تواريخ «المستحقات»). */
    fun preview(amountMinor: Halalas, currency: Currency, date: IsoDate, count: Int, cycle: BnplCycle): BnplPreview =
        bnplPreview(amountMinor, currency, date, count, cycle)

    private suspend fun charge(transactionId: Id): Pair<Transaction, BnplProvider> {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw IllegalArgumentException("transaction $transactionId")
        val provider = bnplProviderOf(t.rawMerchantName ?: t.rawDescription) ?: throw IllegalArgumentException("not a BNPL charge")
        return t to provider
    }

    /** «اشتريت إيه … قسمتها على [BnplPurchase.count]»: الخطة بأول قسط = الخصم ده (مدفوع)، والباقي جاي كل شهر أو كل أسبوعين. */
    suspend fun newPurchase(transactionId: Id, answer: BnplPurchase): InstallmentPlan {
        val (t, provider) = charge(transactionId)
        val input = InstallmentInput(
            name = answer.what.trim().ifEmpty { provider.brand },
            provider = provider.brand,
            kind = InstallmentKind.PURCHASE_PLAN,
            currency = t.currency,
            principalMinor = 0,
            totalMinor = 0,
            installmentMinor = t.amountMinor,
            firstDueAt = t.occurredAt.take(10),
            cycleWeeks = answer.cycle.weeks,
            categoryId = answer.categoryId,
            remindDayBefore = answer.remind,
        )
        return deps.installments.saveCounted(input, answer.count, firstPaymentTxnId = t.id)
    }

    /** «ده قسط من [planId]» — المبلغ زي ما اتخصم (لو مختلف عن القسط، الباقي بيتحسب من اللي اتدفع فعلًا). */
    suspend fun installmentOf(transactionId: Id, planId: Id): InstallmentPayment {
        val (t, _) = charge(transactionId)
        val plan = openPlans(deps.today()).firstOrNull { it.plan.id == planId } ?: throw IllegalArgumentException("plan $planId")
        return deps.installments.link(planId, t.id, bnplLinkAmount(t.amountMinor, plan.remainingMinor))
    }

    /** «مش قسط — عملية عادية»: العملية زي ما هي، وما بتتسألش تاني. */
    suspend fun notInstallment(transactionId: Id) {
        charge(transactionId)
        deps.txns.update(transactionId, TransactionPatch(bnplDecided = true, updatedAt = deps.clock.nowIso()))
    }

    /**
     * «تراجع» بعد الربط اللي لوحده: الدفعة بتتفك والعملية **بترجع زي ما كانت** ([BnplLinked.before] — النوع والتصنيف وتأكيدهم والمراجعة)،
     * والخطة دي ما بتتربطش بالخصم ده لوحدها تاني ⇒ التشغيلة الجاية بتسأل «ده قسط من أنهي مشترى؟». أي غلط بيطلع للشاشة (ما بنقولش «اتلغى» وهو ما اتلغاش).
     */
    suspend fun undoAutoLink(linked: BnplLinked): Unit = lock.withLock {
        val id = linked.transactionId
        deps.installments.unlink(id)
        linked.before?.let { b ->
            deps.txns.update(
                id,
                TransactionPatch(
                    economicKind = b.kind, economicKindConfirmed = b.kindConfirmed, categoryId = b.categoryId, clearCategoryId = b.categoryId == null,
                    categoryConfirmed = b.categoryConfirmed, reviewState = b.reviewState, suggestedKind = b.suggestedKind,
                    clearSuggestedKind = b.suggestedKind == null, updatedAt = deps.clock.nowIso(),
                ),
            )
        }
        val plan = deps.plans.listAll().firstOrNull { it.id == linked.planId } ?: return@withLock
        if (id !in plan.dismissedTxnIds) deps.plans.save(plan.copy(dismissedTxnIds = plan.dismissedTxnIds + id))
    }

    /** الرد كله في نداء واحد (ورا أي ربط شغال — نفس القفل) — والباقي على الخطة محسوب هنا. */
    suspend fun answer(a: BnplAnswer): BnplDone = lock.withLock {
        suspend fun done(planId: Id): BnplDone {
            val plan = deps.plans.listAll().first { it.id == planId }
            val paid = sumMoney(deps.payments.listByPlan(planId).map { it.amountMinor })
            return BnplDone(plan.name, subtractMoney(plan.totalMinor, paid), plan.currency)
        }
        when (a) {
            is BnplAnswer.New -> done(newPurchase(a.transactionId, a.purchase).id)
            is BnplAnswer.Of -> done(installmentOf(a.transactionId, a.planId).planId)
            is BnplAnswer.NotInstallment -> BnplDone(null, null, null).also { notInstallment(a.transactionId) }
        }
    }
}
