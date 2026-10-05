package app.masroufy.core

/**
 * **الوصية الواجبة — مصر بس** (قانون الوصية 71/1946 م76–79). المحكمة الدستورية أيدتها (2021-05-08):
 * https://www.sccourt.gov.eg/SCC/faces/Rules_Html/11878_30_216_1_2.html
 *
 * م76 (نص النسخة غير الرسمية، مختصر): لو الميت ما وصّاش لفرع ولده اللي مات في حياته (أو معاه) بمثل ما كان هيستحقه الولد ده ميراثًا
 * لو كان عايش ⇒ تجب للفرع وصية بقدر النصيب ده **في حدود الثلث**، بشرط إن الفرع **مش وارث**، وإن الميت ما يكونش أداه من غير مقابل قدر
 * اللي يجب له (ولو أداه أقل ⇒ تجب له وصية بالباقي). وبتكون للطبقة الأولى من أولاد البنات، ولأولاد الأبناء وإن نزلوا، وبتتقسم
 * للذكر مثل حظ الأنثيين. م78: الواجبة مقدمة على غيرها من الوصايا.
 *
 * **الحساب (دار الإفتاء — الفتوى 8330، https://www.dar-alifta.org/ar/fatwa/details/20658):** نفترض الولد عايش ونحسب نصيبه وسط الورثة
 * الحقيقيين ⇒ الوصية = الأقل من نصيبه والثلث ⇒ تطلع من التركة **الأول** ⇒ الباقي يتقسم على الورثة الحقيقيين.
 * أكتر من ولد مات قبله ⇒ نفترضهم كلهم عايشين مع بعض، ولو مجموعهم أكتر من الثلث ⇒ الثلث بينهم بنسبة أنصبتهم.
 *
 * - **المبلغ:** الكسر × الباقي بعد التجهيز والديون **مقرّب لتحت** للهللة (عشان ما يعدّيش الثلث) — الهللة الفاضلة للورثة. وبعدها
 *   بيتخصم اللي اتدّى في الحياة ([PredeceasedChild.givenInLifeMinor]).
 * - **مش في النسخة دي:** ابن مات قبله ومفيش ابن عايش ⇒ أولاده (ابن ابن · بنت ابن) بيورثوا بنفسهم أو بعضهم ⇒ «غير مدعوم».
 *   وأولاد البنت **الجيل الأول بس** (النص). وصية اختيارية للحفيد نفسه (م77) مش في النسخة دي.
 */
internal data class WajibaPlan(val shares: List<WajibaShare>, val notes: List<InheritanceNote>)

internal sealed interface WajibaOutcome {
    data class Plan(val plan: WajibaPlan) : WajibaOutcome
    data class Stop(val result: InheritanceResult) : WajibaOutcome
}

private val THIRD = Frac.of(1, 3)

internal fun planWajiba(
    counts: Map<HeirKind, Int>,
    children: List<PredeceasedChild>,
    net: Halalas,
    money: (Halalas) -> String,
): WajibaOutcome {
    val livingSons = counts[HeirKind.SON] ?: 0
    if (children.any { it.isSon } && livingSons == 0) {
        return WajibaOutcome.Stop(InheritanceResult.Unsupported(UnsupportedReason.PREDECEASED_SON_GRANDCHILDREN_INHERIT))
    }
    val extraSons = children.count { it.isSon }
    val extraDaughters = children.size - extraSons
    val hypothetical = counts.toMutableMap()
    hypothetical[HeirKind.SON] = livingSons + extraSons
    hypothetical[HeirKind.DAUGHTER] = (counts[HeirKind.DAUGHTER] ?: 0) + extraDaughters
    val alive = when (val o = computeShares(InheritanceLaw.EG, hypothetical.filterValues { it > 0 }, null)) {
        is SharesOutcome.Stop -> return WajibaOutcome.Stop(o.result)
        is SharesOutcome.Shares -> o
    }
    fun perPerson(k: HeirKind): Frac = (alive.shares[k] ?: Frac.ZERO) / hypothetical.getValue(k)
    val entitled = children.map { perPerson(if (it.isSon) HeirKind.SON else HeirKind.DAUGHTER) }
    val total = entitled.sumFrac()
    val notes = mutableListOf<InheritanceNote>()
    val cite76 = Citation(LawSource.EG_BEQUEST, "76")
    val allowed = minFrac(total, THIRD)
    if (total > THIRD) notes += InheritanceNote(InheritanceNoteKind.WAJIBA_CAPPED, cite76)
    val shares = children.mapIndexed { j, child ->
        val share = if (total.isZero) Frac.ZERO else entitled[j] * allowed / total
        val exact = mulDivRem(net, share.num, share.den).first
        val amount = maxOf(0L, exact - child.givenInLifeMinor)
        val parent = if (child.isSon) HeirKind.SON else HeirKind.DAUGHTER
        notes += InheritanceNote(InheritanceNoteKind.WAJIBA, cite76, listOf(parent), listOf(money(amount)))
        if (child.givenInLifeMinor > 0) {
            notes += InheritanceNote(InheritanceNoteKind.WAJIBA_GIFT, cite76, values = listOf(money(minOf(child.givenInLifeMinor, exact))))
        }
        // للذكر مثل حظ الأنثيين بين أولاده (م76)
        val weights = List(child.sons) { 2 } + List(child.daughters) { 1 }
        val sum = weights.sum()
        val split = largestRemainder(amount, weights.map { Frac.of(it.toLong(), sum.toLong()) })
        WajibaShare(child, share, amount, split.take(child.sons), split.drop(child.sons))
    }
    return WajibaOutcome.Plan(WajibaPlan(shares, notes))
}
