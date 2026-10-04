package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.Currency
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.balanceMismatchCandidate
import app.masroufy.core.budgetAlertCandidates
import app.masroufy.core.dueAlertCandidates
import app.masroufy.core.profileCompletionCandidate
import app.masroufy.core.transferQuestionCandidates
import app.masroufy.core.zakatAlertCandidate
import app.masroufy.core.zakatVisible
import app.masroufy.core.zakatYearStatus
import app.masroufy.port.ProfileRepository
import app.masroufy.port.ZakatPaymentRepository
import app.masroufy.port.ZakatYearRepository

/**
 * بيجمع المرشحين للتنبيه النهارده من البيانات الموجودة (OVERRIDES §61) — من غير ما يقرر حاجة؛ القرار في `RunAlertEngine`.
 * المصادر: المستحقات (`LoadDues`) · الميزانية (نفس منطق `LoadNotifications`) · أسئلة «زون التحويلات» · ميعاد الزكاة ·
 * اختلاف المطابقة · كارت «ملفك X%» (§63) · مناسبات الشخص (§64).
 * **مش بيتولد:** الإيميل · الحسابات المربوطة · الدخول من جهاز جديد — محتاجين سيرفر («الجوال الأول»).
 */
data class GatherAlertsDeps(
    val dues: LoadDues,
    /** null = «زون التحويلات» مش متوصل. */
    val transfers: ManageTransfers? = null,
    val zakatYears: ZakatYearRepository? = null,
    val zakatPayments: ZakatPaymentRepository? = null,
    val profile: ProfileRepository? = null,
    /** مناسبات الشخص (§64) — null = مش متوصلة. */
    val occasions: ManageOccasions? = null,
)

data class AlertGatherInput(
    val today: IsoDate,
    val period: Period,
    val currency: Currency,
    /** ناتج شاشة الميزانية (null = مش متحسب) — سقفه كمان «حجم الشهر» للحكم على المبلغ. */
    val budget: BudgetScreenData? = null,
    /** ناتج المطابقة لكل محفظة اتطابقت. */
    val reconcile: List<ReconcileOutcome> = emptyList(),
    /** نسبة اكتمال الملف (§63). ⚠️ حسبتها لسه ما اتبنتش ⇒ اللي بينادي بيدّيها، وnull = مفيش كارت. */
    val profileCompletionPercent: Int? = null,
)

class GatherAlerts(private val deps: GatherAlertsDeps) {
    suspend fun gather(input: AlertGatherInput): List<AlertCandidate> {
        val out = mutableListOf<AlertCandidate>()
        // «حجم الشهر» = سقف الميزانية الإجمالي بعملة المساحة؛ عملة تانية أو مفيش سقف ⇒ مفيش حكم على الحجم
        val monthLimit = input.budget?.totalStatus?.limitMinor
        val dues = deps.dues.load(input.today, input.period, input.currency, null)
        out += dueAlertCandidates(dues.agenda, input.today) { c -> if (c == input.currency) monthLimit else null }

        input.budget?.let { out += budgetAlertCandidates(budgetNotificationEvents(it)) }

        deps.transfers?.let { out += transferQuestionCandidates(it.zone().questions) }

        val years = deps.zakatYears
        if (years != null && zakatVisible(deps.profile?.load())) {
            for (year in years.listAll()) {
                val remaining = if (year.closed) zakatYearStatus(year, deps.zakatPayments?.listByYear(year.id).orEmpty()).remainingMinor else null
                zakatAlertCandidate(year, remaining, input.today)?.let { out += it }
            }
        }

        for (r in input.reconcile) balanceMismatchCandidate(r.wallet.id, r.wallet.name, r.result.mismatches)?.let { out += it }
        deps.occasions?.let { out += it.alertCandidates(input.today) }
        profileCompletionCandidate(input.profileCompletionPercent)?.let { out += it }
        return out
    }
}
