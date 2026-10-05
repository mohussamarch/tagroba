package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_BROTHER
import app.masroufy.core.HeirKind.MATERNAL_GRANDMOTHER
import app.masroufy.core.HeirKind.MATERNAL_SISTER
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_BROTHER
import app.masroufy.core.HeirKind.PATERNAL_GRANDMOTHER
import app.masroufy.core.HeirKind.PATERNAL_SISTER
import app.masroufy.core.HeirKind.SON
import app.masroufy.core.HeirKind.SON_DAUGHTER
import app.masroufy.core.HeirKind.SON_SON
import app.masroufy.core.HeirKind.WIFE

/** نتيجة تحديد الأنصبة (من غير فلوس): نصيب كل نوع من نصيب الورثة · ورث بإيه · الملاحظات. */
internal sealed interface SharesOutcome {
    data class Shares(val shares: Map<HeirKind, Frac>, val basis: Map<HeirKind, ShareBasis>, val notes: List<InheritanceNote>) : SharesOutcome
    data class Stop(val result: InheritanceResult) : SharesOutcome
}

private val HALF = Frac.of(1, 2)
private val THIRD = Frac.of(1, 3)
private val QUARTER = Frac.of(1, 4)
private val SIXTH = Frac.of(1, 6)
private val EIGHTH = Frac.of(1, 8)
private val TWO_THIRDS = Frac.of(2, 3)

private fun halfOrTwoThirds(count: Int): Frac = if (count == 1) HALF else TWO_THIRDS

/**
 * أنصبة الورثة بالقانون [law] — الترتيب: الفروع ⇒ الزوجين ⇒ الأصول ⇒ الإخوة لأم ⇒ الإخوة والأخوات ⇒ العصبة البعيدة ⇒ الإكمال
 * (العُمَرية · الباقي · المُشَرَّكة · الرد · العول — `InheritanceFinish.kt`).
 */
internal fun computeShares(law: InheritanceLaw, counts: Map<HeirKind, Int>, distantRelatives: Boolean?): SharesOutcome {
    val s = ShareState(law, counts)
    s.descendants()
    s.spouses()
    s.father()
    s.mother()
    s.grandmothers()
    s.maternalSiblings()
    s.siblingsAndGrandfather()?.let { return SharesOutcome.Stop(it) }
    s.farResiduaries()
    return s.finish(distantRelatives)
}

/** الأبناء والبنات وأولاد الابن (السعودية م215–216 · مصر م12 و19 و27). */
private fun ShareState.descendants() {
    if (has(SON)) {
        // البنت مع الابن عصبة بالغير للذكر مثل حظ الأنثيين (السعودية م215/2 · مصر م19)
        addGroup(listOf(SON to 2, DAUGHTER to 1))
        block(SON_SON, SON)
        block(SON_DAUGHTER, SON)
        return
    }
    if (has(DAUGHTER)) fard[DAUGHTER] = halfOrTwoThirds(c(DAUGHTER))
    if (has(SON_SON)) {
        addGroup(listOf(SON_SON to 2, SON_DAUGHTER to 1))
    } else if (has(SON_DAUGHTER)) {
        when (c(DAUGHTER)) {
            0 -> fard[SON_DAUGHTER] = halfOrTwoThirds(c(SON_DAUGHTER))
            1 -> {
                fard[SON_DAUGHTER] = SIXTH
                note(InheritanceNoteKind.TAKMILA, cite("216/2", "12"), listOf(SON_DAUGHTER, DAUGHTER))
            }
            else -> block(SON_DAUGHTER, DAUGHTER)
        }
    }
}

/** الزوج م209 / الزوجة أو الزوجات م210 (مصر م11). */
private fun ShareState.spouses() {
    if (has(HUSBAND)) fard[HUSBAND] = if (anyDescendant) QUARTER else HALF
    if (has(WIFE)) fard[WIFE] = if (anyDescendant) EIGHTH else QUARTER
}

/** الأب (السعودية م211 · مصر م9 و21): سدس مع الفرع الذكر · سدس + الباقي مع الفرع الأنثى · الباقي من غير فرع. */
private fun ShareState.father() {
    if (!has(FATHER)) return
    block(GRANDFATHER, FATHER)
    fatherLike(FATHER)
}

internal fun ShareState.fatherLike(k: HeirKind) {
    when {
        maleDescendant -> fard[k] = SIXTH
        femaleDescendant -> {
            fard[k] = SIXTH
            addGroup(listOf(k to 2))
        }
        else -> addGroup(listOf(k to 2))
    }
}

/**
 * الأم (السعودية م213 · مصر م14): السدس مع فرع وارث أو اتنين إخوة أو أكتر (من أي جهة، وارثين أو محجوبين)، وإلا الثلث.
 * ثلث الباقي (العُمَرية) بيتعمل في الإكمال بعد ما نعرف مين وارث فعلًا.
 */
private fun ShareState.mother() {
    if (!has(MOTHER)) return
    val siblings = c(FULL_BROTHER) + c(FULL_SISTER) + c(PATERNAL_BROTHER) + c(PATERNAL_SISTER) + c(MATERNAL_BROTHER) + c(MATERNAL_SISTER)
    fard[MOTHER] = if (anyDescendant || siblings >= 2) SIXTH else THIRD
}

/**
 * الجدات (السعودية م214 · مصر م14 و25): السدس بينهم بالتساوي. الأم بتحجبهم. **الفرق:** في مصر الأب بيحجب أم الأب (م25)،
 * وفي السعودية «لا يحجب الأب أمه» (م214/1). الجدتين اللي في النسخة دي في نفس الدرجة.
 */
private fun ShareState.grandmothers() {
    if (has(MOTHER)) {
        block(PATERNAL_GRANDMOTHER, MOTHER)
        block(MATERNAL_GRANDMOTHER, MOTHER)
        return
    }
    if (law == InheritanceLaw.EG && has(FATHER)) block(PATERNAL_GRANDMOTHER, FATHER)
    val eligible = listOf(PATERNAL_GRANDMOTHER, MATERNAL_GRANDMOTHER).filter { has(it) && it !in blocked }
    val total = eligible.sumOf { c(it) }
    for (k in eligible) fard[k] = SIXTH * c(k) / total
}

/** الإخوة لأم (السعودية م219–220 · مصر م10 و26): السدس للواحد والثلث للاتنين أو أكتر، الذكر زي الأنثى. */
private fun ShareState.maternalSiblings() {
    val blocker = listOf(SON, DAUGHTER, SON_SON, SON_DAUGHTER, FATHER, GRANDFATHER).firstOrNull { has(it) }
    if (blocker != null) {
        block(MATERNAL_BROTHER, blocker)
        block(MATERNAL_SISTER, blocker)
        return
    }
    val total = c(MATERNAL_BROTHER) + c(MATERNAL_SISTER)
    if (total == 0) return
    val pool = if (total == 1) SIXTH else THIRD
    for (k in listOf(MATERNAL_BROTHER, MATERNAL_SISTER)) if (has(k)) fard[k] = pool * c(k) / total
}

/**
 * الإخوة والأخوات الأشقاء ولأب + الجد.
 * - بيحجبهم الابن وابن الابن والأب (البلدين).
 * - **الجد:** السعودية بيحجبهم زي الأب (م212/3). مصر بيقاسمهم (م22 — `InheritanceGrandfather.kt`).
 */
private fun ShareState.siblingsAndGrandfather(): InheritanceResult? {
    val blocker = listOf(SON, SON_SON, FATHER).firstOrNull { has(it) }
    if (blocker != null) {
        for (k in listOf(FULL_BROTHER, FULL_SISTER, PATERNAL_BROTHER, PATERNAL_SISTER)) block(k, blocker)
        return null
    }
    val anySibling = listOf(FULL_BROTHER, FULL_SISTER, PATERNAL_BROTHER, PATERNAL_SISTER).any { has(it) }
    if (has(GRANDFATHER)) {
        if (law == InheritanceLaw.SA || !anySibling) {
            fatherLike(GRANDFATHER)
            if (anySibling) note(InheritanceNoteKind.GRANDFATHER_BLOCKS_SIBLINGS, cite("212/3", "22"))
            for (k in listOf(FULL_BROTHER, FULL_SISTER, PATERNAL_BROTHER, PATERNAL_SISTER)) block(k, GRANDFATHER)
            return null
        }
        return egyptGrandfatherWithSiblings()
    }
    applySiblings(effectiveSiblings())
    return null
}

/** إعداد الإخوة من غير الجد: فروض · عصبة (مع وزن كل فرد) · مين اتحجب بمين · ملاحظات. */
internal data class SiblingSetup(
    val fard: Map<HeirKind, Frac>,
    val group: List<Pair<HeirKind, Int>>,
    val blocks: List<Pair<HeirKind, HeirKind>>,
    val notes: List<InheritanceNote>,
)

/**
 * السعودية م217–218 و225–226 · مصر م13 و19–20 و28–29:
 * الشقيق بيحجب اللي لأب · الأخت مع البنات عصبة (مع الغير) وبتحجب زي الأخ · الأخت لأب سدس تكملة مع شقيقة واحدة، وبتتحجب بشقيقتين
 * إلا لو معاها أخ لأب.
 */
internal fun ShareState.effectiveSiblings(): SiblingSetup {
    val f = LinkedHashMap<HeirKind, Frac>()
    val g = mutableListOf<Pair<HeirKind, Int>>()
    val b = mutableListOf<Pair<HeirKind, HeirKind>>()
    val n = mutableListOf<InheritanceNote>()
    fun withDaughters(k: HeirKind) = InheritanceNote(InheritanceNoteKind.WITH_DAUGHTERS, cite(if (k == FULL_SISTER) "217/3" else "218/4", "20"), listOf(k))
    when {
        has(FULL_BROTHER) -> {
            g += FULL_BROTHER to 2
            if (has(FULL_SISTER)) g += FULL_SISTER to 1
            b += PATERNAL_BROTHER to FULL_BROTHER
            b += PATERNAL_SISTER to FULL_BROTHER
        }
        has(FULL_SISTER) && femaleDescendant -> {
            g += FULL_SISTER to 1
            n += withDaughters(FULL_SISTER)
            b += PATERNAL_BROTHER to FULL_SISTER
            b += PATERNAL_SISTER to FULL_SISTER
        }
        has(FULL_SISTER) -> {
            f[FULL_SISTER] = halfOrTwoThirds(c(FULL_SISTER))
            if (has(PATERNAL_BROTHER)) {
                g += PATERNAL_BROTHER to 2
                if (has(PATERNAL_SISTER)) g += PATERNAL_SISTER to 1
            } else if (has(PATERNAL_SISTER)) {
                if (c(FULL_SISTER) == 1) {
                    f[PATERNAL_SISTER] = SIXTH
                    n += InheritanceNote(InheritanceNoteKind.TAKMILA, cite("218/2", "13"), listOf(PATERNAL_SISTER, FULL_SISTER))
                } else {
                    b += PATERNAL_SISTER to FULL_SISTER
                }
            }
        }
        has(PATERNAL_BROTHER) -> {
            g += PATERNAL_BROTHER to 2
            if (has(PATERNAL_SISTER)) g += PATERNAL_SISTER to 1
        }
        has(PATERNAL_SISTER) && femaleDescendant -> {
            g += PATERNAL_SISTER to 1
            n += withDaughters(PATERNAL_SISTER)
        }
        has(PATERNAL_SISTER) -> f[PATERNAL_SISTER] = halfOrTwoThirds(c(PATERNAL_SISTER))
    }
    return SiblingSetup(f, g, b, n)
}

internal fun ShareState.applySiblings(setup: SiblingSetup) {
    for ((k, by) in setup.blocks) block(k, by)
    fard.putAll(setup.fard)
    notes += setup.notes
    addGroup(setup.group)
}

/** العصبة البعيدة (أولاد الإخوة · الأعمام · أولاد الأعمام): الأقرب بس بياخد، والباقي محجوب بيه. */
private fun ShareState.farResiduaries() {
    val present = FAR_RESIDUARIES.filter { has(it) }
    if (present.isEmpty()) return
    val top = lead
    if (top == null) {
        addGroup(listOf(present.first() to 2))
        for (k in present.drop(1)) block(k, present.first())
    } else {
        for (k in present) block(k, top)
    }
}
