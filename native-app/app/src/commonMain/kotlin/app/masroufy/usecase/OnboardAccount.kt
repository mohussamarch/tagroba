package app.masroufy.usecase

import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.ObligationKind
import app.masroufy.core.ProfileCheck
import app.masroufy.core.UserProfile
import app.masroufy.core.checkProfile
import app.masroufy.core.jsTrim
import app.masroufy.port.Clock
import app.masroufy.port.WalletRepository

/**
 * أسئلة البداية — نقل `onboardAccount.ts` (OVERRIDES §26–27)، لأي حساب ما خلصهاش قبل كده.
 * **كل القيم بتتأكد الأول قبل أي كتابة**؛ غلطة في أي خطوة ⇒ مفيش حاجة اتكتبت، والخطأ بيقول الخطوة.
 * الكاش = **رصيد افتتاح** لمحفظة الكاش بتاريخ النهارده (مش عملية، فمش دخل)، ونفس الرصيد الحالي بالظبط ⇒ ما بيتغيرش.
 * تسجيل الانتهاء **آخر حاجة**: لو اتقطع في النص الأسئلة بترجع، والتكرار ما بيضاعفش شخص ولا دين.
 */

/** «ليا عنده» = RECEIVABLE، «عليا ليه» = LOAN_PAYABLE (OVERRIDES §27). */
data class OpeningDebtInput(val name: String, val kind: ObligationKind, val amountMinor: Halalas)

/** `cashMinor` = null ⇒ اتخطى السؤال. */
data class OnboardingInput(val profile: UserProfile, val cashMinor: Halalas?, val debts: List<OpeningDebtInput>)

data class CashOpening(val amountMinor: Halalas, val openingAt: IsoDate)

/** `cashOpening` = null ⇒ مفيش محفظة كاش، أو رصيد بدايتها لسه صفر. */
data class OnboardingStart(val profile: UserProfile, val cashOpening: CashOpening?)

sealed interface OnboardingResult {
    data object Ok : OnboardingResult

    /** `step`: "profile" / "cash" / "debts". */
    data class Failed(val step: String, val message: String) : OnboardingResult
}

data class OnboardAccountDeps(
    val profile: ManageProfile,
    val people: ManagePeople,
    val wallets: WalletRepository,
    val clock: Clock,
)

class OnboardAccount(private val deps: OnboardAccountDeps) {
    private suspend fun findCashWallet() = deps.wallets.listAll().find { it.kind == "cash" }

    /** الموجود فعلًا عشان يتكتب في الخانات — حساب قديم ما يتكتبش فوق بياناته بقيم فاضية. */
    suspend fun start(): OnboardingStart {
        val profile = deps.profile.load()
        val cash = findCashWallet()
        return OnboardingStart(profile, cash?.takeIf { it.openingBalanceMinor != 0L }?.let { CashOpening(it.openingBalanceMinor, it.openingAt) })
    }

    suspend fun finish(input: OnboardingInput): OnboardingResult {
        // شاشة قديمة ما ترجّعش الكاش والملف لورا ولا تعمل دين اتسوّى تاني
        if (!deps.profile.needsOnboarding(deps.profile.load())) return OnboardingResult.Ok
        val check = checkProfile(input.profile)
        if (check is ProfileCheck.Invalid) return OnboardingResult.Failed("profile", check.message)
        val cashMinor = input.cashMinor
        if (cashMinor != null && (cashMinor < 0 || cashMinor > MAX_SAFE_HALALAS)) return OnboardingResult.Failed("cash", "مبلغ الكاش لازم يكون رقم مش سالب")
        for (debt in input.debts) {
            val name = jsTrim(debt.name)
            if (name.isEmpty()) return OnboardingResult.Failed("debts", "اكتب اسم كل شخص")
            if (name.length > 80) return OnboardingResult.Failed("debts", "الاسم أطول من 80 حرف")
            if (debt.amountMinor <= 0 || debt.amountMinor > MAX_SAFE_HALALAS) return OnboardingResult.Failed("debts", "مبلغ دين «$name» لازم يكون أكبر من صفر")
        }

        val cashWallet = if (cashMinor != null) findCashWallet() ?: return OnboardingResult.Failed("cash", "مفيش محفظة كاش في الحساب") else null
        if (cashWallet != null && cashMinor != cashWallet.openingBalanceMinor) {
            deps.wallets.save(cashWallet.copy(openingBalanceMinor = cashMinor!!, openingAt = deps.clock.nowIso().take(10)))
        }

        if (input.debts.isNotEmpty()) {
            val rows = deps.people.listWithBalances()
            val idByName = rows.associate { jsTrim(it.person.name) to it.person.id }.toMutableMap()
            for (debt in input.debts) {
                val name = jsTrim(debt.name)
                val personId = idByName[name] ?: deps.people.addPerson(name).id.also { idByName[name] = it }
                // إعادة المحاولة بعد انقطاع: نفس الدين القديم موجود ⇒ ما يتكررش
                val already = rows.find { it.person.id == personId }?.obligations.orEmpty().any { (obligation) ->
                    obligation.originTransactionId == null && obligation.kind == debt.kind && obligation.originalMinor == debt.amountMinor
                }
                if (!already) deps.people.addOpeningDebt(personId, debt.kind, debt.amountMinor)
            }
        }

        return when (val done = deps.profile.completeOnboarding((check as ProfileCheck.Ok).profile)) {
            is ProfileCheck.Ok -> OnboardingResult.Ok
            is ProfileCheck.Invalid -> OnboardingResult.Failed("profile", done.message)
        }
    }
}
