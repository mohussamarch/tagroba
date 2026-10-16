package app.masroufy.usecase

import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplOpenPlan
import app.masroufy.core.BnplProvider
import app.masroufy.port.Clock
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.IsoDate
import app.masroufy.core.Transaction
import app.masroufy.core.bnplLinkAmount
import app.masroufy.core.bnplMatch
import app.masroufy.core.bnplProviderOf
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.dueProgress
import app.masroufy.core.installmentSchedule
import app.masroufy.core.parseIsoDate
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney
import app.masroufy.core.toDayNumber
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.TransactionRepository

/** خصم من مزوّد «ادفع بعدين» مستني رد المالك: مشترى جديد ([BnplMatch.NewPurchase]) أو «أنهي مشترى؟» ([BnplMatch.AskWhich] — [plans] المفتوحة). */
data class BnplAsk(val transaction: Transaction, val provider: BnplProvider, val match: BnplMatch, val plans: List<InstallmentPlan>)

/** [linked] = الخصومات اللي اتربطت لوحدها بالقسط الجاي (عملية ⇒ خطة) · [asks] = اللي مستني المالك، الأقدم الأول. */
data class BnplSettle(val linked: Map<Id, Id>, val asks: List<BnplAsk>)

/** رد «اشتريت إيه؟ قسمتها على كام؟ كل قد إيه؟». [what] فاضي ⇒ اسم المزوّد. */
data class BnplPurchase(val what: String, val categoryId: Id?, val count: Int, val cycle: BnplCycle = BnplCycle.MONTHLY)

data class BnplChargesDeps(
    val txns: TransactionRepository,
    val plans: InstallmentPlanRepository,
    val payments: InstallmentPaymentRepository,
    val installments: ManageInstallments,
    val clock: Clock,
    /** النهارده بتوقيت البلد (§88). */
    val today: () -> IsoDate,
)

/**
 * r05 — خصومات «اشتري دلوقتي وادفع بعدين» (تابي · تمارا · مدفوع · MISpay · فاليو · سيمبل · سهولة …) بعد ما رسالة البنك تتسجل:
 * - [settle]: كل خصم من مزوّد (صادر · مش مربوط · نوعه لسه ما اتأكدش) ⇒ نفس قسط خطة مفتوحة للمزوّد (±هللة) ⇒ **بيتربط لوحده** بالقسط
 *   الجاي من غير سؤال · غير كده ⇒ سؤال ([BnplAsk]).
 * - [newPurchase]: الخصم = **أول قسط** (§82-20) ⇒ خطة تقسيط مشتريات: الإجمالي = القسط × العدد، أول ميعاد = يوم الخصم، والباقي أقساط جاية
 *   بتظهر في «المستحقات» وتنبيهاتها (`ManageInstallments.saveCounted`).
 * - [installmentOf]: «ده قسط من المشترى ده» · [skip]: «تخطّي» ⇒ قسط عادي تحت «المستحقات ← تقسيط مشتريات» من غير خطة.
 * الحساب = قواعد الأقساط (§56): القسط مصروف بعمليته، والمشترى نفسه مالوش عملية ⇒ مفيش عدّ مرتين.
 */
class BnplCharges(private val deps: BnplChargesDeps) {
    private suspend fun openPlans(today: IsoDate): List<BnplOpenPlan> = deps.plans.listAll()
        .filter { it.kind == InstallmentKind.PURCHASE_PLAN && bnplProviderOf(it.provider) != null }
        .map { plan ->
            val paid = sumMoney(deps.payments.listByPlan(plan.id).map { it.amountMinor })
            BnplOpenPlan(plan, subtractMoney(plan.totalMinor, paid), dueProgress(installmentSchedule(plan), paid, today).nextDueAt)
        }

    /** خصومات المزوّدين اللي لسه ما اتقررتش في آخر [BNPL_LOOKBACK_DAYS] يوم، الأقدم الأول. */
    private suspend fun pending(today: IsoDate): List<Pair<Transaction, BnplProvider>> {
        val from = dayNumberToIso(toDayNumber(parseIsoDate(today)) - BNPL_LOOKBACK_DAYS)
        val near = deps.txns.listByDateRange(from, dayNumberToIso(toDayNumber(parseIsoDate(today)) + 1)).mapNotNull { t ->
            val provider = bnplProviderOf(t.rawMerchantName ?: t.rawDescription)
            if (provider == null || t.observedDirection != Direction.OUT || t.economicKindConfirmed) null else t to provider
        }
        val taken = deps.payments.listByTransactionIds(near.map { it.first.id }).map { it.transactionId }.toSet()
        return near.filter { it.first.id !in taken }.sortedWith(compareBy({ it.first.occurredAt }, { it.first.id }))
    }

    /** يربط اللي يتربط لوحده (بالترتيب — كل ربط بيغيّر الباقي والقسط الجاي) ويرجّع الأسئلة. */
    suspend fun settle(): BnplSettle {
        val today = deps.today()
        val linked = LinkedHashMap<Id, Id>()
        val asks = mutableListOf<BnplAsk>()
        for ((t, provider) in pending(today)) {
            val open = openPlans(today)
            when (val m = bnplMatch(provider, t.amountMinor, t.currency, open)) {
                is BnplMatch.NextInstallment -> {
                    val plan = open.first { it.plan.id == m.planId }
                    deps.installments.link(m.planId, t.id, bnplLinkAmount(t.amountMinor, plan.remainingMinor))
                    linked[t.id] = m.planId
                }
                BnplMatch.NewPurchase -> asks += BnplAsk(t, provider, m, emptyList())
                is BnplMatch.AskWhich -> asks += BnplAsk(t, provider, m, open.filter { it.plan.id in m.planIds }.map { it.plan })
            }
        }
        return BnplSettle(linked, asks)
    }

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
        )
        return deps.installments.saveCounted(input, answer.count, firstPaymentTxnId = t.id)
    }

    /** «ده قسط من [planId]». */
    suspend fun installmentOf(transactionId: Id, planId: Id): InstallmentPayment {
        val (t, _) = charge(transactionId)
        val plan = openPlans(deps.today()).firstOrNull { it.plan.id == planId } ?: throw IllegalArgumentException("plan $planId")
        return deps.installments.link(planId, t.id, bnplLinkAmount(t.amountMinor, plan.remainingMinor))
    }

    /** «تخطّي»: قسط عادي تحت «المستحقات ← تقسيط مشتريات» (مصروف) من غير خطة — وما بيتسألش تاني. */
    suspend fun skip(transactionId: Id) {
        charge(transactionId)
        deps.installments.markPlainInstallment(transactionId)
    }

    /** الرد كله في نداء واحد (`SmsDeps.bnplAnswer`) — والباقي على الخطة محسوب هنا. */
    suspend fun answer(a: BnplAnswer): BnplDone {
        suspend fun done(planId: Id): BnplDone {
            val plan = deps.plans.listAll().first { it.id == planId }
            val paid = sumMoney(deps.payments.listByPlan(planId).map { it.amountMinor })
            return BnplDone(plan.name, subtractMoney(plan.totalMinor, paid), plan.currency)
        }
        return when (a) {
            is BnplAnswer.New -> done(newPurchase(a.transactionId, a.purchase).id)
            is BnplAnswer.Of -> done(installmentOf(a.transactionId, a.planId).planId)
            is BnplAnswer.Skip -> BnplDone(null, null, null).also { skip(a.transactionId) }
        }
    }
}

/** الخصومات الأقدم من كده ما بتتسألش (تاريخ قديم اتسجل قبل الميزة). */
const val BNPL_LOOKBACK_DAYS = 180

/** رد المالك على كارت «ادفع بعدين» (الشاشة بتبعته زي ما هو). */
sealed interface BnplAnswer {
    val transactionId: Id

    data class New(override val transactionId: Id, val purchase: BnplPurchase) : BnplAnswer

    data class Of(override val transactionId: Id, val planId: Id) : BnplAnswer

    data class Skip(override val transactionId: Id) : BnplAnswer
}

/** بعد الرد: اسم الخطة والباقي عليها (محسوب هنا — الشاشة بتعرضه بس). «تخطّي» ⇒ الاتنين null. */
data class BnplDone(val planName: String?, val remainingMinor: Halalas?, val currency: Currency?)
