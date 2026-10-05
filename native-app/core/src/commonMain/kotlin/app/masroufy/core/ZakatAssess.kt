package app.masroufy.core

/**
 * حساب الزكاة نفسه (OVERRIDES §62) — دوال نقية. المدخل «اللي بتملكه» بوقائعه، والقاعدة من جدول البلد (`ZAKAT_RULES`).
 * **قاعدة 10:** القيمة المجهولة (سعر ناقص · واقعة ما اتسألتش) `null` مش صفر، والإجمالي اللي فيه مجهول «غير متاح».
 * **التقريب:** قيمة كل قطعة بتتقرّب مرة واحدة (نص لفوق بعيد عن الصفر)، والمطلوب 2.5% **على كل سطر لوحده** بنفس التقريب،
 * والإجمالي = مجموع السطور — عشان خانات «اتدفع» تجمع على الإجمالي بالهللة (اختيار Claude — §62 قرارات تنفيذ).
 */
enum class ZakatMetal { GOLD, SILVER }

/** حاجة بتملكها (أو عليك) يوم الحساب. [heldSince] من إمتى (للحول) — null = مش معروف ⇒ من أول البيانات. */
sealed interface ZakatHolding {
    val id: Id?
    val name: String?
    val heldSince: IsoDate?

    /** رصيد محفظة يوم الزكاة — null = المحفظة ما كانتش اتفتحت في التطبيق لسه (مش صفر). */
    data class Cash(override val id: Id?, override val name: String?, val balanceMinor: Halalas?, override val heldSince: IsoDate? = null) : ZakatHolding

    data class Metal(
        override val id: Id, override val name: String, val metal: ZakatMetal, val grams: Quantity,
        val karat: Int?, val fineness: Int?, val purpose: ZakatPurpose?, val ownUnitPriceMinor: Halalas?, override val heldSince: IsoDate? = null,
    ) : ZakatHolding

    /** [saudiCompany] الشركة (أو الصندوق) سعودية؟ — واقعة بتتسأل بس في البلد اللي قاعدتها بتفرّق (السعودية، للطويل الأجل). */
    data class Security(
        override val id: Id, override val name: String, val line: ZakatLineKind, val valueMinor: Halalas?,
        val holding: ZakatShareHolding?, override val heldSince: IsoDate? = null, val saudiCompany: Boolean? = null,
    ) : ZakatHolding

    /** عملات رقمية — مالهاش فتوى من الجهتين ⇒ ما بتتحسبش. */
    data class Digital(override val id: Id, override val name: String, val valueMinor: Halalas?) : ZakatHolding { override val heldSince: IsoDate? get() = null }

    /** أصل نوعه «تاني» — مش داخل في الحساب (التطبيق ما يعرفش هو إيه). */
    data class Other(override val id: Id, override val name: String, val valueMinor: Halalas?) : ZakatHolding { override val heldSince: IsoDate? get() = null }

    data class Receivable(
        override val id: Id, override val name: String?, val remainingMinor: Halalas, val collectability: ZakatCollectability?, override val heldSince: IsoDate? = null,
    ) : ZakatHolding

    /**
     * دين ليك **اتحصّل** جوه السنة (تسوية على الدين) — [id] = معرّف التسوية، [obligationId] الدين نفسه (لواقعته).
     * بيطلع عليه سطر لوحده لو الدين ما كانش بيدخل الحساب السنوي (مصر كله · السعودية المشكوك فيه) — §62.
     */
    data class CollectedReceivable(
        override val id: Id, val obligationId: Id, override val name: String?, val amountMinor: Halalas, val collectedOn: IsoDate,
        val collectability: ZakatCollectability?,
    ) : ZakatHolding { override val heldSince: IsoDate? get() = collectedOn }

    /** أمانة: فلوس حد تاني معاك — المرجع ما حددش في البلدين ⇒ بتظهر لوحدها ومش بتتخصم من الكاش. */
    data class Custody(override val id: Id, override val name: String?, val remainingMinor: Halalas) : ZakatHolding { override val heldSince: IsoDate? get() = null }

    /** موقفك الموجب في جمعية (دفعت أكتر ما قبضت). */
    data class RoscaCredit(override val id: Id, override val name: String, val positionMinor: Halalas, override val heldSince: IsoDate? = null) : ZakatHolding

    /**
     * قبض من جمعية **جوه السنة** — [id] = حركة القبض، [amountMinor] الجزء اللي كان من فلوسك (`roscaOwnMoneyReceipts`).
     * بيطلع سطر «اتحصّل» بس لو الجمعية ما كانتش بتدخل الحساب السنوي (مصر — زي الدين ليك، 4399).
     */
    data class CollectedRosca(override val id: Id, val roscaId: Id, override val name: String, val amountMinor: Halalas, val collectedOn: IsoDate) :
        ZakatHolding { override val heldSince: IsoDate? get() = collectedOn }

    /** دين عليك (سلفة · جمعية قبضت فيها أكتر ما دفعت) — بيتعرض وما بيتخصمش. */
    data class Debt(override val id: Id, override val name: String?, val remainingMinor: Halalas) : ZakatHolding { override val heldSince: IsoDate? get() = null }
}

enum class ZakatItemStatus {
    COUNTED, EXEMPT, NOT_DEDUCTED,
    /** المرجع ما حددش ⇒ ما بيتحسبش. */
    NO_RULING,
    /** نوع أصل مش داخل في الحساب. */
    NOT_COVERED,
    /** واقعة لسه ما اتسألتش ([ZakatItem.missingFact]). */
    NEEDS_FACT,
    /** القيمة مش معروفة (سعر ناقص). */
    NO_VALUE,
}

/** سطر تفصيلي: «ليه الرقم ده؟». [topic] = القاعدة اللي اتطبقت (ومصدرها من `zakatRule`). */
data class ZakatItem(
    val holding: ZakatHolding,
    val line: ZakatLineKind?,
    val topic: ZakatTopic?,
    val status: ZakatItemStatus,
    val valueMinor: Halalas?,
    /** اللي عليه زكاة: القيمة لو بتتحسب · صفر لو معفي أو دين · null لو مجهول أو مش محسوب. */
    val zakatableMinor: Halalas?,
    val missingFact: String? = null,
)

/** أسعار الجرام **الصافي** بعملة الحساب (من ملف الأسعار اليومي) — null = مش متاح (السطر بعملة الحساب مش في الملف). */
data class ZakatPrices(val goldPureGramMinor: Halalas?, val silverPureGramMinor: Halalas?, val asOf: IsoDate? = null)

/**
 * من ملف الأسعار: الدهب عيار 24 (الجرام الصافي) والفضة **بعملة الحساب بالظبط** — الريال من `GOLD_24K_GRAM`/`SILVER_GRAM`،
 * والجنيه من `GOLD_24K_GRAM_EGP`/`SILVER_GRAM_EGP` (§62). مفيش تحويل بسعر صرف (§41): سطر بعملة تانية عمره ما بيتاخد،
 * والناقص بيفضل null ⇒ «غير متاح».
 */
fun zakatPricesFromFeed(feed: PriceFeed?, currency: Currency): ZakatPrices {
    if (feed == null) return ZakatPrices(null, null)
    val gold = feedPriceIn(feed, ZAKAT_GOLD_SYMBOL, currency)
    val silver = feedPriceIn(feed, ZAKAT_SILVER_SYMBOL, currency)
    return ZakatPrices(gold?.pricePerUnitMinor, silver?.pricePerUnitMinor, listOfNotNull(gold?.asOf, silver?.asOf).minOrNull())
}

/** رمز الجرام الصافي في ملف الأسعار (عيار 24) — نسخة الجنيه بنفس الاسم + `_EGP`. */
const val ZAKAT_GOLD_SYMBOL = "GOLD_24K_GRAM"
const val ZAKAT_SILVER_SYMBOL = "SILVER_GRAM"

private fun grams(whole: Long): Quantity = whole * QUANTITY_SCALE

/** النصاب بالفلوس: السعودية الأقل من الدهب والفضة (لازم الاتنين يبقوا معروفين) · مصر 85 جم عيار 21. */
fun nisabMinor(country: ZakatCountry, prices: ZakatPrices): Halalas? = when (country) {
    ZakatCountry.SA -> {
        val gold = prices.goldPureGramMinor?.let { valueOfPureQuantity(grams(NISAB_GOLD_GRAMS), 1, 1, it) }
        val silver = prices.silverPureGramMinor?.let { valueOfPureQuantity(grams(NISAB_SILVER_GRAMS), 1, 1, it) }
        if (gold == null || silver == null) null else minOf(gold, silver)
    }
    ZakatCountry.EG -> prices.goldPureGramMinor?.let { valueOfPureQuantity(grams(NISAB_GOLD_GRAMS), NISAB_EG_GOLD_KARAT.toLong(), 24, it) }
}

/** قيمة قطعة دهب أو فضة: الوزن × النقاوة × سعر الجرام الصافي؛ ولو السعر الصافي ناقص ⇒ سعر الأصل نفسه (للوحدة بعيارها). */
fun metalValueMinor(m: ZakatHolding.Metal, prices: ZakatPrices): Halalas? {
    val purity: Pair<Long, Long>? = when (m.metal) {
        ZakatMetal.GOLD -> m.karat?.let { it.toLong() to 24L }
        ZakatMetal.SILVER -> m.fineness?.let { it.toLong() to 1000L }
    }
    val pure = if (m.metal == ZakatMetal.GOLD) prices.goldPureGramMinor else prices.silverPureGramMinor
    if (purity != null && pure != null) return valueOfPureQuantity(m.grams, purity.first, purity.second, pure)
    return m.ownUnitPriceMinor?.let { valueOfQuantity(m.grams, it) }
}

private fun item(h: ZakatHolding, line: ZakatLineKind?, topic: ZakatTopic?, country: ZakatCountry, value: Halalas?, missing: String? = null): ZakatItem {
    if (missing != null) return ZakatItem(h, line, topic, ZakatItemStatus.NEEDS_FACT, value, null, missing)
    val effect = topic?.let { zakatRule(country, it).effect }
    return when (effect) {
        ZakatEffect.COUNT -> if (value == null) ZakatItem(h, line, topic, ZakatItemStatus.NO_VALUE, null, null)
            else ZakatItem(h, line, topic, ZakatItemStatus.COUNTED, value, maxOf(0L, value))
        ZakatEffect.EXEMPT -> ZakatItem(h, null, topic, ZakatItemStatus.EXEMPT, value, 0)
        ZakatEffect.NOT_DEDUCTED -> ZakatItem(h, null, topic, ZakatItemStatus.NOT_DEDUCTED, value, 0)
        ZakatEffect.NO_RULING -> ZakatItem(h, null, topic, ZakatItemStatus.NO_RULING, value, null)
        ZakatEffect.METHOD, null -> ZakatItem(h, null, topic, ZakatItemStatus.NOT_COVERED, value, null)
    }
}

/** القاعدتين بيفرقوا في البلد دي؟ لو لأ (مصر في الديون ليك وفي جنسية الشركة) ⇒ الواقعة ما بتتسألش (ما بنسألش سؤال ملوش لازمة). */
private fun distinguishes(country: ZakatCountry, a: ZakatTopic, b: ZakatTopic): Boolean = zakatRule(country, a).effect != zakatRule(country, b).effect

/** قاعدة الدين ليك السنوية اللي بتنطبق (null = الواقعة لسه ما اتسألتش في بلد بتفرّق). */
private fun receivableTopic(country: ZakatCountry, collectability: ZakatCollectability?): ZakatTopic? = when {
    !distinguishes(country, ZakatTopic.RECEIVABLE_STRONG, ZakatTopic.RECEIVABLE_DOUBTFUL) -> ZakatTopic.RECEIVABLE_STRONG
    collectability == ZakatCollectability.STRONG -> ZakatTopic.RECEIVABLE_STRONG
    collectability == ZakatCollectability.DOUBTFUL -> ZakatTopic.RECEIVABLE_DOUBTFUL
    else -> null
}

/**
 * القاعدة على كل واقعة لوحدها. الدين اللي اتحصّل وكان بيتحسب كل سنة (السعودية — هيرجع) **ما بيطلعش سطر** (اتزكّى في سنينه).
 */
fun zakatItems(country: ZakatCountry, holdings: List<ZakatHolding>, prices: ZakatPrices): List<ZakatItem> = holdings.mapNotNull { h ->
    when (h) {
        is ZakatHolding.Cash -> item(h, ZakatLineKind.CASH, ZakatTopic.CASH, country, h.balanceMinor)
        is ZakatHolding.Metal -> {
            val line = if (h.metal == ZakatMetal.GOLD) ZakatLineKind.GOLD else ZakatLineKind.SILVER
            val value = metalValueMinor(h, prices)
            when (h.purpose) {
                null -> item(h, line, null, country, value, "purpose")
                ZakatPurpose.WEAR -> item(h, line, ZakatTopic.WORN_JEWELRY, country, value)
                ZakatPurpose.SAVING -> {
                    val noPurity = (if (h.metal == ZakatMetal.GOLD) h.karat else h.fineness) == null
                    if (value == null && noPurity) item(h, line, ZakatTopic.SAVED_METAL, country, null, if (h.metal == ZakatMetal.GOLD) "karat" else "fineness")
                    else item(h, line, ZakatTopic.SAVED_METAL, country, value)
                }
            }
        }
        is ZakatHolding.Security -> when (h.holding) {
            null -> item(h, h.line, null, country, h.valueMinor, "holding")
            ZakatShareHolding.TRADING -> item(h, h.line, ZakatTopic.TRADING_SHARES, country, h.valueMinor)
            ZakatShareHolding.LONG_TERM -> when {
                !distinguishes(country, ZakatTopic.LONG_TERM_SHARES, ZakatTopic.FOREIGN_LONG_TERM_SHARES) || h.saudiCompany == true ->
                    item(h, h.line, ZakatTopic.LONG_TERM_SHARES, country, h.valueMinor)
                h.saudiCompany == false -> item(h, h.line, ZakatTopic.FOREIGN_LONG_TERM_SHARES, country, h.valueMinor)
                else -> item(h, h.line, null, country, h.valueMinor, "saudiCompany")
            }
        }
        is ZakatHolding.Digital -> item(h, null, ZakatTopic.CRYPTO, country, h.valueMinor)
        is ZakatHolding.Other -> ZakatItem(h, null, null, ZakatItemStatus.NOT_COVERED, h.valueMinor, null)
        is ZakatHolding.Receivable -> {
            // مصر (4399) ما بتفرّقش بين هيرجع ومشكوك ⇒ الواقعة ما بتتسألش
            val topic = receivableTopic(country, h.collectability)
            if (topic == null) item(h, ZakatLineKind.RECEIVABLES, null, country, h.remainingMinor, "collectability")
            else item(h, ZakatLineKind.RECEIVABLES, topic, country, h.remainingMinor)
        }
        is ZakatHolding.CollectedReceivable -> {
            val yearly = receivableTopic(country, h.collectability)
            when {
                yearly == null -> item(h, ZakatLineKind.COLLECTED_RECEIVABLES, null, country, h.amountMinor, "collectability")
                // كان بيتحسب كل سنة ⇒ اتزكّى في سنينه، والتحصيل ما بيطلّعش حاجة جديدة
                zakatRule(country, yearly).effect == ZakatEffect.COUNT -> null
                else -> item(h, ZakatLineKind.COLLECTED_RECEIVABLES, ZakatTopic.RECEIVABLE_COLLECTED, country, h.amountMinor)
            }
        }
        is ZakatHolding.Custody -> item(h, null, ZakatTopic.CUSTODY, country, h.remainingMinor)
        is ZakatHolding.RoscaCredit -> item(h, ZakatLineKind.ROSCA, ZakatTopic.ROSCA_CREDIT, country, h.positionMinor)
        // السعودية: الجمعية بتتحسب كل سنة ⇒ القبض ما بيطلّعش حاجة جديدة · مصر: سطر «اتحصّل» مرة واحدة على فلوسك
        is ZakatHolding.CollectedRosca ->
            if (zakatRule(country, ZakatTopic.ROSCA_CREDIT).effect == ZakatEffect.COUNT) null
            else item(h, ZakatLineKind.COLLECTED_RECEIVABLES, ZakatTopic.RECEIVABLE_COLLECTED, country, h.amountMinor)
        is ZakatHolding.Debt -> item(h, null, ZakatTopic.DEBTS_OWED, country, h.remainingMinor)
    }
}

/** حالة الحول: كمّل (وهل البيانات غطّت السنة كلها) · أو بدأ من جديد (من يوم ما رجع فوق النصاب — null = لسه تحته). */
sealed interface HawlState {
    data class Complete(val verified: Boolean) : HawlState
    data class Restarted(val since: IsoDate?) : HawlState
}

enum class ZakatOutcome {
    /** كله معروف وفوق النصاب. */
    DUE,
    /** فوق النصاب بس فيه سطور مجهولة ⇒ المعروف ليه مطلوبه، والإجمالي «غير متاح». */
    PARTIAL,
    BELOW_NISAB,
    /** الحول ما كملش: (السعودية) نزلت تحت النصاب جوه السنة · (مصر) كانت تحت النصاب أول السنة ⇒ الحول بدأ من جديد. */
    HAWL_RESTARTED,
    /** النصاب مجهول، أو تحت النصاب وفيه مجهول ممكن يعدّيه. */
    UNAVAILABLE,
}

data class ZakatLine(val kind: ZakatLineKind, val zakatableMinor: Halalas?, val dueMinor: Halalas?)

data class ZakatAssessment(
    val country: ZakatCountry,
    val currency: Currency,
    val asOf: IsoDate,
    val items: List<ZakatItem>,
    val lines: List<ZakatLine>,
    /** مجموع اللي معروف ومحسوب بس. */
    val knownZakatableMinor: Halalas,
    /** null لو فيه سطر مجهول. */
    val totalZakatableMinor: Halalas?,
    val nisabMinor: Halalas?,
    val dueMinor: Halalas?,
    val outcome: ZakatOutcome,
    val hawl: HawlState,
) {
    /** اللي المرجع ما حددش فيه أو مش داخل — بيظهر لوحده. */
    val notComputed: List<ZakatItem> get() = items.filter { it.status == ZakatItemStatus.NO_RULING || it.status == ZakatItemStatus.NOT_COVERED }
    /** اللي ناقصه واقعة أو سعر. */
    val blockers: List<ZakatItem> get() = items.filter { it.status == ZakatItemStatus.NEEDS_FACT || it.status == ZakatItemStatus.NO_VALUE }
}

fun zakatDueOf(zakatableMinor: Halalas): Halalas = rateOfMoney(zakatableMinor, ZAKAT_RATE_PER_THOUSAND, 1000)

fun assessZakat(
    country: ZakatCountry, currency: Currency, asOf: IsoDate, items: List<ZakatItem>, nisab: Halalas?, hawl: HawlState,
): ZakatAssessment {
    val blocked = items.any { it.status == ZakatItemStatus.NEEDS_FACT || it.status == ZakatItemStatus.NO_VALUE }
    val known = sumMoney(items.filter { it.status == ZakatItemStatus.COUNTED }.mapNotNull { it.zakatableMinor })
    val byLine = ZakatLineKind.entries.mapNotNull { kind ->
        val mine = items.filter { it.line == kind }
        if (mine.isEmpty()) return@mapNotNull null
        kind to (if (mine.all { it.zakatableMinor != null }) sumMoney(mine.map { it.zakatableMinor!! }) else null)
    }
    val outcome = when {
        nisab == null -> ZakatOutcome.UNAVAILABLE
        hawl is HawlState.Restarted -> ZakatOutcome.HAWL_RESTARTED
        known < nisab -> if (blocked) ZakatOutcome.UNAVAILABLE else ZakatOutcome.BELOW_NISAB
        blocked -> ZakatOutcome.PARTIAL
        else -> ZakatOutcome.DUE
    }
    val lines = byLine.map { (kind, zakatable) ->
        val due = when (outcome) {
            ZakatOutcome.DUE, ZakatOutcome.PARTIAL -> zakatable?.let(::zakatDueOf)
            ZakatOutcome.BELOW_NISAB, ZakatOutcome.HAWL_RESTARTED -> 0L
            ZakatOutcome.UNAVAILABLE -> null
        }
        ZakatLine(kind, zakatable, due)
    }
    val total = if (blocked) null else known
    val due = when (outcome) {
        ZakatOutcome.DUE -> sumMoney(lines.map { it.dueMinor!! })
        ZakatOutcome.BELOW_NISAB, ZakatOutcome.HAWL_RESTARTED -> 0L
        ZakatOutcome.PARTIAL, ZakatOutcome.UNAVAILABLE -> null
    }
    return ZakatAssessment(country, currency, asOf, items, lines, known, total, nisab, due, outcome, hawl)
}
