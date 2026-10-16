package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DebtLine
import app.masroufy.core.FutureGoal
import app.masroufy.core.FutureInputs
import app.masroufy.core.FxRate
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InvestRatesState
import app.masroufy.core.IsoDate
import app.masroufy.core.PayFrequency
import app.masroufy.core.PriceFeed
import app.masroufy.core.egpPerSar4
import app.masroufy.core.fullMonthsBetween
import app.masroufy.core.investableSplit
import app.masroufy.core.sumMoney
import app.masroufy.port.IncomeSourceRepository

/** التحويش الشهري الفعلي (متوسط آخر 3 شهور مالية كاملة — `LoadActualSaving`) — null = مش معروف. */
fun interface FutureSavingSource {
    suspend fun averageMinor(today: IsoDate): Halalas?
}

/**
 * الدخل الشهري لقاعدة الـ10%. **رد المالك Q7 (2026-10-16):** متوسط الدخل الفعلي لآخر 3 شهور كاملة، ولو مش معروف ⇒ المرتب اللي المستخدم
 * كتبه بالظبط (فرع `r05-salary` — مش نطاق ولا تخمين). ولا ده ولا ده ⇒ null ⇒ مبلغ البلد الثابت والجملة بتقول «دخلك مش معروف».
 */
fun interface FutureIncomeSource {
    suspend fun monthlyIncomeMinor(today: IsoDate): Halalas?
}

/** الديون اللي عليك: الأقساط (المتبقي + القسط الجاي) و«عليك» للناس وديون البداية (المتبقي والقسط 0). */
fun interface FutureDebtsSource {
    suspend fun debts(today: IsoDate): List<DebtLine>
}

/** أهداف خطط الادخار الشغالة. */
fun interface FutureGoalsSource {
    suspend fun goals(today: IsoDate): List<FutureGoal>
}

/**
 * مصادر بلد واحد. [holdings] و[rates] للبلاد **التانية** بس («في البلدين»): البلد الشغال بياخدهم من الشاشة (بتحمّلهم مرة واحدة — §1.0).
 */
class FutureSpaceSource(
    val spaceId: String,
    val countryCode: String,
    val currency: Currency,
    val withYouNow: LoadWithYouNow,
    val saving: FutureSavingSource,
    val income: FutureIncomeSource,
    val debts: FutureDebtsSource,
    val incomeSources: IncomeSourceRepository,
    val goals: FutureGoalsSource,
    val holdings: (suspend (IsoDate) -> HoldingsSnapshot?)? = null,
    val rates: (suspend (IsoDate) -> InvestRatesState?)? = null,
)

data class LoadFutureInputsDeps(
    val mine: FutureSpaceSource,
    val others: List<FutureSpaceSource>,
    val birth: ManageBirthDate,
    /** ملف الأسعار (سعر الدولار بالريال وبالجنيه) — null أو الملف مش واصل ⇒ مفيش سعر صرف. */
    val prices: (suspend () -> PriceFeed?)? = null,
)

/** رمز سعر الدولار في ملف الأسعار: بالريال (موجود) وبالجنيه (مش موجود لسه — سؤال Q5). */
const val FX_USD_SAR = "USD_SAR"
const val FX_USD_EGP = "USD_EGP"

/**
 * مدخلات «هتبقى معاك كام» من بيانات المستخدم نفسه (مواصفة الاستثمار §2.1): الكاش من «معك الآن» (المحفظة اللي رصيدها مش معروف برّه ومعدودة) ·
 * الحاجات من لقطة «حاجاتي» (الشخصي برّه) · الديون · التحويش الفعلي · الدخل · الإيجار والعائد المتوقع · العمر من تاريخ الميلاد · الأهداف.
 */
class LoadFutureInputs(private val deps: LoadFutureInputsDeps) {
    suspend fun load(today: IsoDate, holdings: HoldingsSnapshot, rates: InvestRatesState): FutureInputs =
        inputsOf(deps.mine, today, holdings, rates, ageOn(today))

    /** البلاد التانية المفتوحة (فاضية ⇒ كارت «في البلدين» مستخبي). بلد حاجاته أو متوسطاته مش معروفة ⇒ صفه «غير متاح». */
    suspend fun otherSpaces(today: IsoDate): List<FutureInputs> {
        val age = ageOn(today)
        return deps.others.map { s ->
            val h = s.holdings?.invoke(today)
            val r = s.rates?.invoke(today)
            if (h == null || r == null) inputsOf(s, today, HoldingsSnapshot(s.currency, emptyList(), false), InvestRatesState.Unavailable(listOf("holdings")), age)
            else inputsOf(s, today, h, r, age)
        }
    }

    /** سعر الصرف من سعر الدولار بالريال وبالجنيه في ملف الأسعار — أي واحد ناقص ⇒ null («غير متاح»). */
    suspend fun fx(): FxRate? {
        val feed = deps.prices?.invoke() ?: return null
        val sar = feed.prices.firstOrNull { it.symbol == FX_USD_SAR } ?: return null
        val egp = feed.prices.firstOrNull { it.symbol == FX_USD_EGP } ?: return null
        return FxRate(egpPerSar4(sar.pricePerUnitMinor, egp.pricePerUnitMinor), egp.source, minOf(sar.asOf, egp.asOf))
    }

    private suspend fun ageOn(today: IsoDate): Int? = deps.birth.load()?.takeIf { it < today }?.let { fullMonthsBetween(it, today) / 12 }

    private suspend fun inputsOf(s: FutureSpaceSource, today: IsoDate, holdings: HoldingsSnapshot, rates: InvestRatesState, age: Int?): FutureInputs {
        val now = s.withYouNow.load(today)
        val known = now.wallets.mapNotNull { it.balanceMinor }
        val unknownWallets = now.wallets.count { it.balanceMinor == null }
        val split = investableSplit(holdings.items, holdings.includePersonal)
        // الإيجار والعائد المتوقع (مصادر شغالة بعملة البلد وفيها مبلغ متوقع شهري) — اللي من غير مبلغ مش داخل (مش صفر مؤكد)
        val passive = s.incomeSources.listAll().filter {
            it.currency == s.currency && it.kind in PASSIVE_KINDS && it.startedAt <= today && (it.endedAt == null || it.endedAt!! > today) &&
                it.payFrequency == PayFrequency.MONTHLY
        }.mapNotNull { it.expectedMinor }
        return FutureInputs(
            spaceId = s.spaceId, countryCode = s.countryCode, currency = s.currency, today = today,
            cashMinor = sumMoney(known), unknownCount = unknownWallets + split.unknownCount, split = split, debts = s.debts.debts(today),
            saveMinor = s.saving.averageMinor(today), incomeMinor = s.income.monthlyIncomeMinor(today), passiveKnownMinor = sumMoney(passive),
            rates = rates, ageNow = age, goals = s.goals.goals(today),
        )
    }

    private companion object {
        val PASSIVE_KINDS = setOf(IncomeSourceKind.RENT, IncomeSourceKind.INVESTMENT)
    }
}
