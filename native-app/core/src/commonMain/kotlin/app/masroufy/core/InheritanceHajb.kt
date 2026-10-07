package app.masroufy.core

/**
 * حاسبة الورث — حالة الحساب أثناء تحديد الأنصبة + **الحجب** (مين بيمنع مين، ومادته).
 *
 * الحجب (السعودية م221–222 · مصر م23–29): المحجوب ما بيورثش، **وبيحجب غيره** — يعني الإخوة المحجوبين بالأب بيرجّعوا الأم للسدس
 * (السعودية م213/1-ب «وارثين أو محجوبين» · مصر م23 «والمحجوب يحجب غيره»).
 */
internal class ShareState(val law: InheritanceLaw, private val counts: Map<HeirKind, Int>) {
    fun c(k: HeirKind): Int = counts[k] ?: 0
    fun has(k: HeirKind): Boolean = c(k) > 0

    /** الفرض (النصيب المحدد) لكل نوع — مجموع الأفراد. */
    val fard = LinkedHashMap<HeirKind, Frac>()

    /** العصبة اللي هتاخد الباقي: النوع ⇒ وزن الفرد (ذكر 2 · أنثى 1). أول مجموعة اتضافت هي الأقرب. */
    val group = LinkedHashMap<HeirKind, Int>()
    var lead: HeirKind? = null

    val blocked = LinkedHashMap<HeirKind, HeirKind>()
    val notes = mutableListOf<InheritanceNote>()

    /** مصر م22: الجد مع الإخوة ⇒ بيتفحص في الآخر إنه ما نزلش عن السدس. */
    var grandfatherWithSiblings = false

    val anyDescendant: Boolean get() = has(HeirKind.SON) || has(HeirKind.DAUGHTER) || has(HeirKind.SON_SON) || has(HeirKind.SON_DAUGHTER)
    val maleDescendant: Boolean get() = has(HeirKind.SON) || has(HeirKind.SON_SON)

    /** فرع وارث أنثى من غير ذكر (البنات أو بنات الابن). */
    val femaleDescendant: Boolean get() = !maleDescendant && (has(HeirKind.DAUGHTER) || has(HeirKind.SON_DAUGHTER))

    fun cite(sa: String, eg: String, egSource: LawSource = LawSource.EG_INHERITANCE): Citation =
        if (law == InheritanceLaw.SA) Citation(LawSource.SA_PERSONAL_STATUS, sa) else Citation(egSource, eg)

    fun note(kind: InheritanceNoteKind, citation: Citation?, heirs: List<HeirKind> = emptyList(), values: List<String> = emptyList()) {
        notes += InheritanceNote(kind, citation, heirs, values)
    }

    fun block(k: HeirKind, by: HeirKind) {
        if (!has(k) || k in blocked) return
        blocked[k] = by
        fard.remove(k)
        group.remove(k)
        note(InheritanceNoteKind.BLOCKED, blockCitation(law, k, by), listOf(k, by))
    }

    fun addGroup(members: List<Pair<HeirKind, Int>>) {
        val present = members.filter { has(it.first) && it.first !in blocked }
        if (present.isEmpty()) return
        if (lead == null) lead = present.first().first
        for ((k, w) in present) group[k] = w
    }

    fun inheriting(): Set<HeirKind> = HeirKind.entries.filter { has(it) && it !in blocked }.toSet()
}

/** العصبة بالنفس من بعد الإخوة — بالترتيب (الجهة ثم الدرجة ثم القوة: السعودية م224 و228 · مصر م17–18). */
internal val FAR_RESIDUARIES = listOf(
    HeirKind.FULL_NEPHEW, HeirKind.PATERNAL_NEPHEW, HeirKind.FULL_UNCLE, HeirKind.PATERNAL_UNCLE, HeirKind.FULL_COUSIN, HeirKind.PATERNAL_COUSIN,
)

private val SIBLING_KINDS = setOf(HeirKind.FULL_BROTHER, HeirKind.FULL_SISTER, HeirKind.PATERNAL_BROTHER, HeirKind.PATERNAL_SISTER)
private val MATERNAL_KINDS = setOf(HeirKind.MATERNAL_BROTHER, HeirKind.MATERNAL_SISTER)

/**
 * مادة الحجب لكل (محجوب، حاجب).
 * - السعودية: الجد بالأب م212/2 · الجدات بالأم م214/2 · ابن الابن م228/1 · بنت الابن م216 · الأخت الشقيقة م217 · الأخت لأب م218 ·
 *   الإخوة لأم م219 · الجد يحجب الإخوة م212/3 · الأخت مع البنات بتحجب زي الأخ م226 · باقي العصبة م228/1.
 * - مصر: الجدات م25 · ابن الابن والجد م18 · بنت الابن م27 · الأخت الشقيقة م28 · الأخت لأب م29 · الإخوة لأم م26 · الأخت مع البنات م20 ·
 *   العصبة من جهة أبعد م17 ومن نفس الجهة م18.
 */
internal fun blockCitation(law: InheritanceLaw, kind: HeirKind, by: HeirKind): Citation {
    val sa = when {
        kind == HeirKind.GRANDFATHER -> "212/2"
        kind == HeirKind.PATERNAL_GRANDMOTHER || kind == HeirKind.MATERNAL_GRANDMOTHER -> "214/2"
        kind == HeirKind.SON_DAUGHTER -> "216"
        kind in MATERNAL_KINDS -> "219"
        by == HeirKind.GRANDFATHER && kind in SIBLING_KINDS -> "212/3"
        kind == HeirKind.FULL_SISTER -> "217"
        kind == HeirKind.PATERNAL_SISTER -> "218"
        by == HeirKind.FULL_SISTER || by == HeirKind.PATERNAL_SISTER -> "226"
        else -> "228/1"
    }
    val eg = when {
        kind == HeirKind.PATERNAL_GRANDMOTHER || kind == HeirKind.MATERNAL_GRANDMOTHER -> "25"
        kind == HeirKind.SON_DAUGHTER -> "27"
        kind in MATERNAL_KINDS -> "26"
        kind == HeirKind.FULL_SISTER -> "28"
        kind == HeirKind.PATERNAL_SISTER -> "29"
        by == HeirKind.FULL_SISTER || by == HeirKind.PATERNAL_SISTER -> "20"
        sameDirection(kind, by) -> "18"
        else -> "17"
    }
    return if (law == InheritanceLaw.SA) Citation(LawSource.SA_PERSONAL_STATUS, sa) else Citation(LawSource.EG_INHERITANCE, eg)
}

/** جهة العصبة (مصر م17): البنوة 1 · الأبوة 2 · الأخوة 3 · العمومة 4. */
private fun direction(k: HeirKind): Int = when (k) {
    HeirKind.SON, HeirKind.SON_SON, HeirKind.DAUGHTER, HeirKind.SON_DAUGHTER -> 1
    HeirKind.FATHER, HeirKind.GRANDFATHER -> 2
    HeirKind.FULL_BROTHER, HeirKind.PATERNAL_BROTHER, HeirKind.FULL_SISTER, HeirKind.PATERNAL_SISTER,
    HeirKind.FULL_NEPHEW, HeirKind.PATERNAL_NEPHEW -> 3
    else -> 4
}

private fun sameDirection(a: HeirKind, b: HeirKind): Boolean = direction(a) == direction(b)
