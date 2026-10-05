package app.masroufy.core

/** أقصى عدد لأي نوع وارث (اختيار Claude — حماية من الأرقام الضخمة؛ الزوجات 4 بالنص). */
const val INHERIT_MAX_COUNT = 100

/**
 * **حاسبة الورث** (OVERRIDES §69). ترتيب الحقوق (السعودية م198 · مصر 77/1943 م4):
 * 1. تجهيز الميت · 2. الديون · 3. الوصية (الواجبة في مصر الأول — 71/1946 م78 — ثم الاختيارية في الباقي من الثلث) · 4. الورثة.
 *
 * - **الوصية الاختيارية:** حدها الثلث من الباقي بعد التجهيز والديون (مقرّب لتحت للهللة). الزيادة بتتنفذ بس لو **كل** الورثة وافقوا
 *   (السعودية م190 · مصر 71/1946 م37). **لوارث:** السعودية ما بتنفذش إلا بموافقة باقي الورثة (م179) · مصر جايزة في الثلث من غير موافقة (م37).
 * - **الوصية الواجبة (مصر بس — 71/1946 م76–79، وطريقة دار الإفتاء في الفتوى 8330):** نصيب الابن/البنت اللي مات قبله **لو كان عايش**،
 *   في حدود الثلث، بيطلع من التركة **قبل** قسمة الباقي على الورثة، وبيتقسم على أولاده للذكر مثل حظ الأنثيين. السعودية: مفيش وصية واجبة.
 *   أولاد **ابن** مات قبله من غير ابن عايش بيورثوا بنفسهم (ابن ابن · بنت ابن) أو جزء منهم ⇒ «غير مدعوم» في النسخة دي.
 * - الكسور مضبوطة والمبالغ بأكبر باقي — `InheritanceAllocation.kt`.
 */
fun calculateInheritance(case: InheritanceCase): InheritanceResult = try {
    compute(case)
} catch (_: InheritanceOverflow) {
    InheritanceResult.Invalid(InvalidReason.TOO_LARGE)
}

private fun compute(case: InheritanceCase): InheritanceResult {
    validateInheritance(case)?.let { return it }
    val law = InheritanceLaw.of(case.countryCode) ?: return InheritanceResult.Unsupported(UnsupportedReason.COUNTRY)
    unsupportedCircumstance(case.special)?.let { return InheritanceResult.Unsupported(it) }
    val counts = case.heirs.filterValues { it > 0 }
    val currency = countryPack(case.countryCode).currency
    fun money(v: Halalas) = formatMoney(v, currency)
    fun cite(sa: String, eg: String, egSource: LawSource = LawSource.EG_INHERITANCE) =
        if (law == InheritanceLaw.SA) Citation(LawSource.SA_PERSONAL_STATUS, sa) else Citation(egSource, eg)

    val notes = mutableListOf(InheritanceNote(InheritanceNoteKind.ORDER, cite("198", "4")))
    val gross = case.items.fold(0L) { a, i -> addChecked(a, i.valueMinor) }
    if (gross > MAX_SAFE_HALALAS) return InheritanceResult.Invalid(InvalidReason.TOO_LARGE)
    val funeral = minOf(case.funeralMinor, gross)
    val debts = minOf(case.debtsMinor, gross - funeral)
    val net = gross - funeral - debts
    val claimed = addChecked(case.funeralMinor, case.debtsMinor)
    if (claimed > gross) notes += InheritanceNote(InheritanceNoteKind.DEBTS_EXCEED, cite("198", "4"), values = listOf(money(claimed), money(gross)))

    val outcome = computeShares(law, counts, case.distantRelatives)
    if (outcome is SharesOutcome.Stop) return outcome.result
    val shares = outcome as SharesOutcome.Shares
    notes += shares.notes

    val wajiba = when {
        case.predeceasedChildren.isEmpty() -> WajibaPlan(emptyList(), emptyList())
        law == InheritanceLaw.SA -> WajibaPlan(emptyList(), listOf(InheritanceNote(InheritanceNoteKind.NO_WAJIBA, null)))
        else -> when (val plan = planWajiba(counts, case.predeceasedChildren, net, ::money)) {
            is WajibaOutcome.Stop -> return plan.result
            is WajibaOutcome.Plan -> plan.plan
        }
    }
    notes += wajiba.notes
    val wajibaTotal = wajiba.shares.sumOf { it.totalMinor }

    val bequest = case.bequest?.let { planBequest(law, it, net, wajibaTotal, ::money, ::cite) } ?: BequestPlan(0, emptyList())
    notes += bequest.notes
    val heirsTotal = net - wajibaTotal - bequest.amountMinor

    // الأشخاص بالترتيب: كل نوع بعدد أفراده
    val persons = HeirKind.entries.filter { (counts[it] ?: 0) > 0 }.flatMap { k -> (0 until counts.getValue(k)).map { k to it } }
    val perPerson = persons.map { (k, _) -> (shares.shares[k] ?: Frac.ZERO) / counts.getValue(k) }
    val amounts = largestRemainder(heirsTotal, perPerson)
    val heirShares = HeirKind.entries.filter { (counts[it] ?: 0) > 0 }.map { k ->
        val n = counts.getValue(k)
        val share = shares.shares[k] ?: Frac.ZERO
        HeirShare(
            kind = k, count = n, basis = shares.basis[k] ?: ShareBasis.NOTHING_LEFT, share = share, perPerson = share / n,
            amountsMinor = persons.indices.filter { persons[it].first == k }.map { amounts[it] },
            names = (0 until n).map { case.names[k]?.getOrNull(it)?.takeIf { s -> s.isNotBlank() } },
        )
    }

    // قسمة كل حاجة: التجهيز · الديون · أحفاد الوصية الواجبة · الوصية · كل وارث
    val claimants = mutableListOf<Pair<Claimant, Halalas>>(Claimant.Funeral to funeral, Claimant.Debts to debts)
    wajiba.shares.forEachIndexed { j, w ->
        w.sonsMinor.forEachIndexed { i, a -> claimants += Claimant.Wajiba(j, true, i) to a }
        w.daughtersMinor.forEachIndexed { i, a -> claimants += Claimant.Wajiba(j, false, i) to a }
    }
    claimants += Claimant.Bequest to bequest.amountMinor
    persons.forEachIndexed { i, (k, idx) -> claimants += Claimant.Heir(k, idx) to amounts[i] }
    val live = claimants.filter { it.second > 0 }
    val matrix = roundMatrix(case.items.map { it.valueMinor }, live.map { it.second })
    val items = case.items.mapIndexed { r, item ->
        ItemSplit(item.name, item.valueMinor, live.indices.filter { matrix[r][it] > 0 }.map { ClaimantAmount(live[it].first, matrix[r][it]) })
    }
    return InheritanceResult.Computed(
        law = law, grossMinor = gross, funeralMinor = funeral, debtsMinor = debts, wajibaMinor = wajibaTotal,
        bequestMinor = bequest.amountMinor, heirsMinor = heirsTotal, heirs = heirShares, wajiba = wajiba.shares, items = items, notes = notes,
    )
}

private fun unsupportedCircumstance(special: Set<SpecialCircumstance>): UnsupportedReason? = when {
    SpecialCircumstance.PREGNANCY in special -> UnsupportedReason.PREGNANCY
    SpecialCircumstance.MISSING_HEIR in special -> UnsupportedReason.MISSING_HEIR
    SpecialCircumstance.SUCCESSIVE_DEATHS in special -> UnsupportedReason.SUCCESSIVE_DEATHS
    SpecialCircumstance.TAKHARUJ in special -> UnsupportedReason.TAKHARUJ
    else -> null
}

/** المدخلات: العدد في حدوده · 4 زوجات على الأكثر · زوج واحد وأب واحد … · مش زوج وزوجة مع بعض · حاجة واحدة على الأقل · مفيش سالب. */
internal fun validateInheritance(case: InheritanceCase): InheritanceResult.Invalid? {
    fun invalid(r: InvalidReason, vararg args: String) = InheritanceResult.Invalid(r, args.toList())
    for ((k, n) in case.heirs) {
        if (n < 0 || n > INHERIT_MAX_COUNT) return invalid(InvalidReason.COUNT_RANGE, k.label, INHERIT_MAX_COUNT.toString())
    }
    val n = { k: HeirKind -> case.heirs[k] ?: 0 }
    if (n(HeirKind.WIFE) > 4) return invalid(InvalidReason.WIVES_MAX)
    if (n(HeirKind.HUSBAND) > 0 && n(HeirKind.WIFE) > 0) return invalid(InvalidReason.HUSBAND_AND_WIFE)
    val single = listOf(
        HeirKind.HUSBAND, HeirKind.FATHER, HeirKind.MOTHER, HeirKind.GRANDFATHER, HeirKind.PATERNAL_GRANDMOTHER, HeirKind.MATERNAL_GRANDMOTHER,
    )
    single.firstOrNull { n(it) > 1 }?.let { return invalid(InvalidReason.ONLY_ONE, it.label) }
    if (case.items.isEmpty()) return invalid(InvalidReason.NO_ITEMS)
    if (case.items.any { it.name.isBlank() }) return invalid(InvalidReason.ITEM_NAME)
    val amounts = case.items.map { it.valueMinor } + listOf(case.funeralMinor, case.debtsMinor) + listOfNotNull(case.bequest?.amountMinor) +
        case.predeceasedChildren.map { it.givenInLifeMinor }
    if (amounts.any { it < 0 }) return invalid(InvalidReason.NEGATIVE_AMOUNT)
    if (amounts.any { it > MAX_SAFE_HALALAS }) return invalid(InvalidReason.TOO_LARGE)
    for (p in case.predeceasedChildren) {
        val kind = if (p.isSon) HeirKind.SON else HeirKind.DAUGHTER
        if (p.sons < 0 || p.daughters < 0 || p.sons > INHERIT_MAX_COUNT || p.daughters > INHERIT_MAX_COUNT) {
            return invalid(InvalidReason.COUNT_RANGE, kind.label, INHERIT_MAX_COUNT.toString())
        }
        if (p.sons + p.daughters == 0) return invalid(InvalidReason.PREDECEASED_NO_CHILDREN)
    }
    return null
}

internal data class BequestPlan(val amountMinor: Halalas, val notes: List<InheritanceNote>)

/** الوصية الاختيارية بعد الواجبة: حدها (الثلث − الواجبة)، والزيادة بموافقة الورثة كلهم. */
private fun planBequest(
    law: InheritanceLaw,
    b: Bequest,
    net: Halalas,
    wajibaTotal: Halalas,
    money: (Halalas) -> String,
    cite: (String, String, LawSource) -> Citation,
): BequestPlan {
    val notes = mutableListOf<InheritanceNote>()
    var amount = b.amountMinor
    if (b.toHeir) {
        when {
            law == InheritanceLaw.EG -> notes += InheritanceNote(InheritanceNoteKind.BEQUEST_HEIR_VALID, cite("179", "37", LawSource.EG_BEQUEST))
            b.heirsConsent -> notes += InheritanceNote(InheritanceNoteKind.BEQUEST_HEIR_CONSENTED, cite("179", "37", LawSource.EG_BEQUEST))
            else -> {
                notes += InheritanceNote(InheritanceNoteKind.BEQUEST_HEIR_VOID, cite("179", "37", LawSource.EG_BEQUEST))
                amount = 0
            }
        }
    }
    val available = maxOf(0L, net / 3 - wajibaTotal)
    if (amount > available) {
        if (b.heirsConsent) {
            amount = minOf(amount, net - wajibaTotal)
            notes += InheritanceNote(InheritanceNoteKind.BEQUEST_EXCESS_CONSENTED, cite("190", "37", LawSource.EG_BEQUEST))
        } else {
            notes += InheritanceNote(InheritanceNoteKind.BEQUEST_CAPPED, cite("190", "37", LawSource.EG_BEQUEST), values = listOf(money(amount), money(available)))
            amount = available
        }
    }
    return BequestPlan(amount, notes)
}
