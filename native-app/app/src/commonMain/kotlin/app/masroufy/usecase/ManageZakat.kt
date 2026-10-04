package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ObligationKind
import app.masroufy.core.TextKey
import app.masroufy.core.ZAKAT_RULES
import app.masroufy.core.ZakatAssessment
import app.masroufy.core.ZakatCollectability
import app.masroufy.core.ZakatCountry
import app.masroufy.core.ZakatError
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatOutcome
import app.masroufy.core.ZakatPrices
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.ZakatRule
import app.masroufy.core.ZakatShareHolding
import app.masroufy.core.ZakatSubject
import app.masroufy.core.ZakatYear
import app.masroufy.core.ZakatYearLine
import app.masroufy.core.assessZakat
import app.masroufy.core.checkHawl
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.nextZakatDate
import app.masroufy.core.nisabMinor
import app.masroufy.core.suggestHawlStart
import app.masroufy.core.uiText
import app.masroufy.core.zakatItems
import app.masroufy.core.zakatVisible
import app.masroufy.core.zakatWealthSeries
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.Clock
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.RoscaRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository
import app.masroufy.port.ZakatFactRepository
import app.masroufy.port.ZakatYearRepository

/**
 * الزكاة — الوقائع والميعاد والحساب وتثبيته (OVERRIDES §62). الدفع في `PayZakat`.
 * [countryCode] بلد المساحة (§41) ⇒ القواعد إجباري (مفيش اختيار رأي). [currency] عملتها.
 */
data class ManageZakatDeps(
    val countryCode: String,
    val currency: Currency,
    val profile: ProfileRepository,
    val wallets: WalletRepository,
    val txns: TransactionRepository,
    val assets: AssetRepository,
    val lots: AssetLotRepository,
    val sales: AssetSaleRepository,
    val prices: AssetPriceRepository,
    val people: PersonRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val roscas: RoscaRepository,
    val roscaEntries: RoscaEntryRepository,
    val facts: ZakatFactRepository,
    val years: ZakatYearRepository,
    val uow: UnitOfWork,
    val clock: Clock,
    /** سجل البلاد (§64) — عشان جملة «الحساب ده على فلوسك في البلد دي بس». التشغيل الحقيقي بيدّيه. */
    val spaces: app.masroufy.port.SpaceRegistry? = null,
)

/** اقتراح الميعاد: بداية الحول (أول يوم وصلت النصاب) وأول ميعاد زكاة (نفس اليوم الهجري بعد سنة). */
data class ZakatDateSuggestion(val hawlStart: IsoDate, val dueAt: IsoDate)

class ManageZakat(private val deps: ManageZakatDeps) {
    private val reader = ZakatHoldingsReader(deps)

    private val country: ZakatCountry
        get() = ZakatCountry.of(deps.countryCode) ?: throw ZakatError(uiText(TextKey.ZAKAT_COUNTRY_UNSUPPORTED))

    /** «المحتوى الإسلامي: ظاهر» + بلد ليها قواعد. */
    suspend fun visible(): Boolean = zakatVisible(deps.profile.load()) && ZakatCountry.of(deps.countryCode) != null

    /**
     * الزكاة **لكل بلد لوحدها** (اختيار المالك §64): لو عنده بلد تانية (حتى مؤرشفة — فلوسها لسه موجودة) الصفحة بتقول جملة ثابتة
     * إن الحساب على فلوس البلد دي بس. `null` = بلد واحدة ⇒ مفيش جملة.
     */
    suspend fun scopeNote(): String? = if (deps.spaces?.listAll().orEmpty().isNotEmpty()) uiText(TextKey.ZAKAT_THIS_COUNTRY_ONLY) else null

    /** القواعد اللي بتتطبق على الحساب ده بمصادرها — لشاشة «ليه الرقم ده؟». */
    fun rules(): List<ZakatRule> = ZAKAT_RULES.filter { it.country == country }

    /**
     * وقائع أصل: الغرض والعيار للدهب والفضة · مضاربة ولا طويل، و«الشركة سعودية؟» ([saudiCompany]) للسهم والصندوق.
     * `null` = ما يتغيرش.
     */
    suspend fun setAssetFacts(
        assetId: Id, purpose: ZakatPurpose? = null, holding: ZakatShareHolding? = null, karat: Int? = null, fineness: Int? = null,
        saudiCompany: Boolean? = null,
    ): ZakatFact {
        val asset = deps.assets.listAll().firstOrNull { it.id == assetId } ?: throw ZakatError(uiText(TextKey.ZAKAT_ASSET_NOT_FOUND))
        val metal = asset.kind == "gold" || asset.kind == "silver"
        val share = asset.kind == "stock" || asset.kind == "fund"
        if ((purpose != null && !metal) || ((holding != null || saudiCompany != null) && !share)) throw ZakatError(uiText(TextKey.ZAKAT_FACT_WRONG_KIND))
        if ((karat != null && asset.kind != "gold") || (fineness != null && asset.kind != "silver")) throw ZakatError(uiText(TextKey.ZAKAT_FACT_WRONG_KIND))
        if (karat != null && karat !in 1..24) throw ZakatError(uiText(TextKey.ZAKAT_KARAT_RANGE))
        if (fineness != null && fineness !in 1..1000) throw ZakatError(uiText(TextKey.ZAKAT_FINENESS_RANGE))
        val old = deps.facts.listAll().firstOrNull { it.subjectId == assetId }
        val fact = ZakatFact(
            assetId, ZakatSubject.ASSET, purpose ?: old?.purpose, holding ?: old?.holding, null,
            karat ?: old?.karat, fineness ?: old?.fineness, deps.clock.nowIso(), saudiCompany ?: old?.saudiCompany,
        )
        deps.facts.save(fact)
        return fact
    }

    /** «الفلوس دي هترجع؟» على دين ليك. */
    suspend fun setReceivableFact(personId: Id, obligationId: Id, collectability: ZakatCollectability): ZakatFact {
        val o = deps.obligations.listByPerson(personId).firstOrNull { it.id == obligationId }
        if (o == null || o.kind != ObligationKind.RECEIVABLE) throw ZakatError(uiText(TextKey.ZAKAT_RECEIVABLE_NOT_FOUND))
        val fact = ZakatFact(obligationId, ZakatSubject.OBLIGATION, collectability = collectability, updatedAt = deps.clock.nowIso())
        deps.facts.save(fact)
        return fact
    }

    /** التطبيق بيقترح (من سلسلة الرصيد) والمستخدم بيأكد — null لو النصاب مش معروف أو الفلوس تحته دلوقتي. */
    suspend fun suggestDate(today: IsoDate, prices: ZakatPrices): ZakatDateSuggestion? {
        val nisab = nisabMinor(country, prices) ?: return null
        val gathered = reader.read(today)
        val series = zakatWealthSeries(gathered.wallets, gathered.transactions, zakatItems(country, gathered.holdings, prices))
        val start = suggestHawlStart(country, series, nisab, today) ?: return null
        return ZakatDateSuggestion(start, nextZakatDate(start))
    }

    /** المستخدم أكد بداية الحول ⇒ سنة مفتوحة ميعادها نفس اليوم الهجري بعد سنة. سنة مفتوحة قديمة بتتبدل (ما اتدفعش عليها — الدفع بعد التثبيت بس). */
    suspend fun confirmDate(hawlStart: IsoDate): ZakatYear {
        check(country in ZakatCountry.entries)
        if (!isValidIsoDate(hawlStart)) throw ZakatError(uiText(TextKey.ZAKAT_DATE_INVALID))
        val dueAt = nextZakatDate(hawlStart)
        val all = deps.years.listAll()
        if (all.any { it.id == dueAt && it.closed }) throw ZakatError(uiText(TextKey.ZAKAT_YEAR_ALREADY_CLOSED))
        val year = ZakatYear(dueAt, hawlStart, dueAt, deps.currency, deps.clock.nowIso())
        deps.uow.run {
            for (open in all.filter { !it.closed }) deps.years.remove(open.id)
            deps.years.save(year)
        }
        return year
    }

    /** السنة المفتوحة (لو فيه). */
    suspend fun openYear(): ZakatYear? = deps.years.listAll().filter { !it.closed }.maxByOrNull { it.dueAt }

    private suspend fun find(yearId: Id): ZakatYear =
        deps.years.listAll().firstOrNull { it.id == yearId } ?: throw ZakatError(uiText(TextKey.ZAKAT_YEAR_NOT_FOUND))

    /** الحساب الحي للسنة: يوم الميعاد، أو النهارده لو الميعاد لسه ما جاش (معاينة/تعجيل). */
    suspend fun assess(yearId: Id, today: IsoDate, prices: ZakatPrices): ZakatAssessment {
        val year = find(yearId)
        val asOf = minOf(today, year.dueAt)
        val gathered = reader.read(asOf, collectedAfter = year.hawlStart)
        val items = zakatItems(country, gathered.holdings, prices)
        val nisab = nisabMinor(country, prices)
        val hawl = if (nisab == null) app.masroufy.core.HawlState.Complete(false)
        else checkHawl(country, zakatWealthSeries(gathered.wallets, gathered.transactions, items), nisab, year.hawlStart, asOf)
        return assessZakat(country, year.currency, asOf, items, nisab, hawl)
    }

    /**
     * تثبيت حساب السنة: السطور والنصاب بيتحفظوا زي ما هما (سعر بكرة ما يغيّرش زكاة امبارح)، والسنة الجاية بتتفتح لوحدها.
     * مسموح بس لو الحساب كامل (مطلوب أو تحت النصاب) — الناقص أو «الحول بدأ من جديد» ⇒ مرفوض.
     */
    suspend fun close(yearId: Id, today: IsoDate, prices: ZakatPrices): ZakatYear {
        val year = find(yearId)
        if (year.closed) throw ZakatError(uiText(TextKey.ZAKAT_YEAR_ALREADY_CLOSED))
        val a = assess(yearId, today, prices)
        if (a.outcome != ZakatOutcome.DUE && a.outcome != ZakatOutcome.BELOW_NISAB) throw ZakatError(uiText(TextKey.ZAKAT_CANNOT_CLOSE))
        val closed = year.copy(
            closedAt = deps.clock.nowIso(), nisabMinor = a.nisabMinor,
            lines = a.lines.map { ZakatYearLine(it.kind, it.zakatableMinor!!, it.dueMinor!!) },
        )
        val nextDue = nextZakatDate(year.dueAt)
        deps.uow.run {
            deps.years.save(closed)
            if (deps.years.listAll().none { it.id == nextDue }) deps.years.save(ZakatYear(nextDue, year.dueAt, nextDue, year.currency, deps.clock.nowIso()))
        }
        return closed
    }
}
