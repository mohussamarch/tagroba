package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Space
import app.masroufy.core.computePersonBalance
import app.masroufy.core.inSpace
import app.masroufy.core.profileCompletionCandidate
import app.masroufy.core.smsConfirmCandidate
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository

/**
 * اللي بيبص على **كل البلاد** مع بعض (OVERRIDES §64) — من غير ما يجمع أي رقم بين بلدين.
 */

/** بلد واحدة في جمع التنبيهات: مصادرها ومدخلاتها (ميزانيتها ومطابقتها بعملتها). */
data class SpaceAlertSource(val space: Space, val deps: GatherAlertsDeps, val input: AlertGatherInput)

/**
 * التنبيهات من كل البلاد (§64 «التنبيهات من كل البلاد وعليها اسم البلد»): كل بلد بمصادرها، وموضوعها بيتعلّم ببلدها
 * (السعودية زي ما هي — `inSpace`)، واسم البلد **جوه التطبيق بس** لو عنده أكتر من بلد. **مصادر الحساب** (مناسبات الشخص · كارت الملف)
 * **مرة واحدة** — مش من كل بلد (كانت هتتكرر). نص شاشة القفل من النوع بس زي الأول.
 */
class GatherAllSpaceAlerts(
    private val sources: List<SpaceAlertSource>,
    private val occasions: ManageOccasions? = null,
    /** رسايل البنك المستنية (§72) — مرة واحدة على مستوى الحساب (الصندوق واحد على الجهاز لكل البلاد). */
    private val sms: AutoRecordSms? = null,
) {
    suspend fun gather(today: IsoDate, profileCompletionPercent: Int? = null): List<AlertCandidate> {
        val labelled = sources.size > 1
        val out = mutableListOf<AlertCandidate>()
        for (s in sources) {
            val perSpace = GatherAlerts(s.deps.copy(occasions = null, sms = null)).gather(s.input.copy(profileCompletionPercent = null))
            out += perSpace.map { it.inSpace(s.space, labelled) }
        }
        occasions?.let { out += it.alertCandidates(today) }
        sms?.let { s -> smsConfirmCandidate(s.waiting().messageIds)?.let { out += it } }
        profileCompletionCandidate(profileCompletionPercent)?.let { out += it }
        return out
    }
}

/** ديون شخص في بلد واحدة بعملة واحدة — «لك عنده» و«له عندك» منفصلين (spec/02) ومن غير تحويل عملة. */
data class PersonSpaceDebt(
    val spaceId: String,
    val spaceLabel: String,
    val currency: Currency,
    val receivableMinor: Halalas,
    val payableLoanMinor: Halalas,
    val payableCustodyMinor: Halalas,
)

/** مستودعات الديون في بلد. */
data class PersonSpaceBook(val space: Space, val obligations: ObligationRepository, val settlements: SettlementRepository)

/**
 * نفس الشخص في البلدين (رد المالك §64: «كل بلد لوحدها بعملتها»): الديون **جنب بعض** — سطر لكل (بلد · عملة)، و**مفيش إجمالي** عن قصد
 * (مفيش دالة بتجمع بين بلدين ولا عملتين). البلد اللي مالهاش دين مع الشخص ما بتطلعش سطر.
 */
class PersonAcrossSpaces(private val books: List<PersonSpaceBook>) {
    suspend fun debts(personId: Id): List<PersonSpaceDebt> = books.flatMap { b ->
        val obligations = b.obligations.listByPerson(personId)
        val settlements = b.settlements.listByObligations(obligations.map { it.id })
        obligations.groupBy { it.currency }.map { (currency, inCurrency) ->
            val ids = inCurrency.map { it.id }.toSet()
            val balance = computePersonBalance(personId, inCurrency, settlements.filter { it.obligationId in ids })
            PersonSpaceDebt(b.space.id, b.space.name, currency, balance.receivableMinor, balance.payableLoanMinor, balance.payableCustodyMinor)
        }.filter { it.receivableMinor != 0L || it.payableLoanMinor != 0L || it.payableCustodyMinor != 0L }
    }
}
