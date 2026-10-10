package app.masroufy.usecase

import app.masroufy.core.CountryPack
import app.masroufy.core.Halalas
import app.masroufy.core.Wallet
import app.masroufy.port.CategoryRepository
import app.masroufy.port.ReferenceSeedPort
import app.masroufy.port.RuleRepository
import app.masroufy.port.SpaceSeedSourceLoader
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.ObligationKind
import app.masroufy.core.ProfileCheck
import app.masroufy.core.TextKey
import app.masroufy.core.UserProfile
import app.masroufy.core.checkProfile
import app.masroufy.core.jsTrim
import app.masroufy.core.uiText
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

/**
 * `cashOpening` = null ⇒ مفيش محفظة كاش، أو رصيد بدايتها لسه صفر.
 * [freshAccount] = حساب جديد خالص ([OnboardAccount.shouldStart]) ⇒ بعد الانتهاء الشاشة بتفتح «المحافظ» عشان يضيف محفظته.
 */
data class OnboardingStart(val profile: UserProfile, val cashOpening: CashOpening?, val freshAccount: Boolean = false)

sealed interface OnboardingResult {
    data object Ok : OnboardingResult

    /** `step`: "profile" / "cash" / "debts". */
    data class Failed(val step: String, val message: String) : OnboardingResult
}

/**
 * تجهيز حساب جديد خالص (المحاكي 2026-10-10: حساب جديد كان بيفتح على رئيسية فاضية من غير محافظ ولا تصنيفات):
 * تصنيفات وقواعد **حزمة البلد** ([source]) + محفظة الكاش باسم الحزمة وعملتها. بتتدّي **لبلد الحساب الأساسية بس** (الحساب الجديد بيبدأ فيها).
 */
data class NewAccountSeeding(
    val categories: CategoryRepository,
    val rules: RuleRepository,
    val progress: ReferenceSeedPort,
    val source: SpaceSeedSourceLoader,
    val pack: CountryPack,
)

data class OnboardAccountDeps(
    val profile: ManageProfile,
    val people: ManagePeople,
    val wallets: WalletRepository,
    val clock: Clock,
    /** null = مفيش تجهيز ولا فتح تلقائي (بلد تانية غير الأساسية · اختبارات قديمة). */
    val seeding: NewAccountSeeding? = null,
)

class OnboardAccount(private val deps: OnboardAccountDeps) {
    private suspend fun findCashWallet() = deps.wallets.listAll().find { it.kind == "cash" }

    /**
     * الهيكل يفتح أسئلة البداية لوحده؟ **حساب جديد خالص بس**: البلد الأساسية + الأسئلة ما خلصتش + **مفيش ولا محفظة**.
     * حساب قديم فيه بيانات (زي حساب المالك المشترك مع التطبيق القديم في السعودية) عنده محافظ ⇒ `false` حتى لو `onboardedAt` فاضي.
     */
    suspend fun shouldStart(): Boolean =
        deps.seeding != null && deps.profile.needsOnboarding(deps.profile.load()) && deps.wallets.listAll().isEmpty()

    /** الموجود فعلًا عشان يتكتب في الخانات — حساب قديم ما يتكتبش فوق بياناته بقيم فاضية. */
    suspend fun start(): OnboardingStart {
        val profile = deps.profile.load()
        val cash = findCashWallet()
        return OnboardingStart(profile, cash?.takeIf { it.openingBalanceMinor != 0L }?.let { CashOpening(it.openingBalanceMinor, it.openingAt) }, shouldStart())
    }

    /** التصنيفات والقواعد (التجهيز بيكمّل الناقص بس، وحساب فيه تصنيفات ما بيتلمسش) ثم محفظة الكاش لو مفيش محافظ خالص. */
    private suspend fun seedNewAccount(s: NewAccountSeeding) {
        SeedUserReferences(s.categories, s.rules, s.progress).seed(s.source.load(s.pack).copy(merchants = emptyList()))
        if (deps.wallets.listAll().isEmpty()) {
            deps.wallets.save(Wallet(CASH_WALLET_ID, s.pack.cashWalletName, s.pack.currency, "cash", 0, deps.clock.nowIso().take(10)))
        }
    }

    suspend fun finish(input: OnboardingInput): OnboardingResult {
        // شاشة قديمة ما ترجّعش الكاش والملف لورا ولا تعمل دين اتسوّى تاني
        if (!deps.profile.needsOnboarding(deps.profile.load())) return OnboardingResult.Ok
        val check = checkProfile(input.profile)
        if (check is ProfileCheck.Invalid) return OnboardingResult.Failed("profile", check.message)
        val cashMinor = input.cashMinor
        if (cashMinor != null && (cashMinor < 0 || cashMinor > MAX_SAFE_HALALAS)) return OnboardingResult.Failed("cash", uiText(TextKey.ONBOARD_CASH_INVALID))
        for (debt in input.debts) {
            val name = jsTrim(debt.name)
            if (name.isEmpty()) return OnboardingResult.Failed("debts", uiText(TextKey.ONBOARD_DEBT_NAME_REQUIRED))
            if (name.length > 80) return OnboardingResult.Failed("debts", uiText(TextKey.PROFILE_NAME_TOO_LONG, "80"))
            if (debt.amountMinor <= 0 || debt.amountMinor > MAX_SAFE_HALALAS) return OnboardingResult.Failed("debts", uiText(TextKey.ONBOARD_DEBT_AMOUNT, name))
        }

        // بعد التأكد وقبل أي كتابة تانية: حساب جديد خالص بياخد تصنيفاته ومحفظة الكاش (وده كمان بيخلّي سؤال الكاش يلاقي محفظته)
        deps.seeding?.let { if (shouldStart()) seedNewAccount(it) }

        val cashWallet = if (cashMinor != null) findCashWallet() ?: return OnboardingResult.Failed("cash", uiText(TextKey.ONBOARD_NO_CASH_WALLET)) else null
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
