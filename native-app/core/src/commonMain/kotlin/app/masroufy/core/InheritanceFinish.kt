package app.masroufy.core

import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_BROTHER
import app.masroufy.core.HeirKind.MATERNAL_SISTER
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.WIFE

private val THIRD = Frac.of(1, 3)
private val SIXTH = Frac.of(1, 6)
private val SPOUSES = setOf(HUSBAND, WIFE)

/**
 * إكمال الأنصبة بعد ما الفروض والعصبة اتحددوا:
 * 1. **العُمَرية** (السعودية م213/3 · مصر م14): الورثة الأب والأم وأحد الزوجين بس ⇒ الأم ثلث الباقي بعد الزوج/الزوجة.
 *    مع **الجد** مكان الأب ⇒ الأم ثلث التركة كلها (السعودية م212/3 نصًا · مصر م14 بتقول «والأب» بس).
 * 2. **حد السدس للجد** مع الإخوة في مصر (م22).
 * 3. **الباقي للعصبة** — لو الفروض استغرقت التركة العاصب بيسقط (السعودية م227 و229 · مصر م16)، **إلا المُشَرَّكة في مصر** (م10):
 *    الأشقاء بيشاركوا الإخوة لأم في الثلث بالتساوي. السعودية م227 بتسقطهم نصًا (الحِمارية).
 * 4. **الرد** (السعودية م231 · مصر م30): الباقي من غير عصبة بيرجع لأصحاب الفروض غير الزوجين بنسبة فروضهم. الزوج/الزوجة بياخدوا الرد
 *    بس لو مفيش غيرهم **ولا ذوي أرحام** — وذوي الأرحام مش في النسخة دي ⇒ بنسأل ([InheritanceCase.distantRelatives]).
 * 5. **العول** (السعودية م230 · مصر م15): الفروض أكتر من التركة ⇒ كل واحد بينقص بنفس النسبة. الملاحظة فيها أصل المسألة قبل وبعد.
 */
internal fun ShareState.finish(distantRelatives: Boolean?): SharesOutcome {
    umariyya()
    applyGrandfatherSixthFloor(SIXTH)
    val inheriting = inheriting()
    if (inheriting.isEmpty()) {
        val reason = if (distantRelatives == true) UnsupportedReason.DISTANT_RELATIVES else UnsupportedReason.NO_HEIRS
        return SharesOutcome.Stop(InheritanceResult.Unsupported(reason))
    }
    val shares = LinkedHashMap<HeirKind, Frac>()
    val basis = LinkedHashMap<HeirKind, ShareBasis>()
    for ((k, f) in fard) {
        shares[k] = f
        basis[k] = ShareBasis.FARD
    }
    val fardSum = fard.values.sumFrac()
    val residue = Frac.ONE - fardSum
    if (group.isNotEmpty()) {
        if (residue.isPositive) {
            val totalWeight = group.entries.sumOf { (k, w) -> w * c(k) }
            for ((k, w) in group) {
                shares[k] = (shares[k] ?: Frac.ZERO) + residue * (w * c(k)) / totalWeight
                basis[k] = if (k in fard) ShareBasis.FARD_AND_RESIDUARY else ShareBasis.RESIDUARY
            }
        } else {
            if (residue < Frac.ZERO) awl(shares, fardSum)
            val maternal = listOf(MATERNAL_BROTHER, MATERNAL_SISTER).filter { has(it) && it !in blocked }.sumOf { c(it) }
            val mushtaraka = lead == FULL_BROTHER && maternal >= 2
            if (mushtaraka && law == InheritanceLaw.EG) {
                shareMaternalThird(shares, basis)
            } else {
                for (k in group.keys) {
                    if (k !in shares) shares[k] = Frac.ZERO
                    if (k !in fard) basis[k] = ShareBasis.NOTHING_LEFT
                }
                if (mushtaraka) note(InheritanceNoteKind.MUSHTARAKA_DROPPED, cite("227", "10"))
                else note(InheritanceNoteKind.RESIDUE_NONE, residueNoneCitation(), listOfNotNull(lead))
            }
        }
    } else if (residue.isPositive) {
        radd(shares, basis, distantRelatives)?.let { return SharesOutcome.Stop(it) }
    } else if (residue < Frac.ZERO) {
        awl(shares, fardSum)
    }
    for (k in blocked.keys) {
        shares[k] = Frac.ZERO
        basis[k] = ShareBasis.BLOCKED
    }
    return SharesOutcome.Shares(shares, basis, notes.toList())
}

private fun ShareState.umariyya() {
    if (!has(MOTHER) || fard[MOTHER] != THIRD) return
    val spouse = SPOUSES.firstOrNull { has(it) } ?: return
    val inheriting = inheriting()
    if (inheriting == setOf(spouse, FATHER, MOTHER)) {
        fard[MOTHER] = (Frac.ONE - fard.getValue(spouse)) / 3
        note(InheritanceNoteKind.UMARIYYA, cite("213/3", "14"), listOf(spouse))
    } else if (inheriting == setOf(spouse, GRANDFATHER, MOTHER)) {
        note(InheritanceNoteKind.GRANDFATHER_MOTHER_THIRD, cite("212/3", "14"))
    }
}

/** العاصب بالنفس م227 · العاصب بالغير أو مع الغير م229 (السعودية) · مصر م16. */
private fun ShareState.residueNoneCitation(): Citation {
    val byOther = lead == FULL_SISTER || lead == HeirKind.PATERNAL_SISTER || HeirKind.DAUGHTER in group || HeirKind.SON_DAUGHTER in group ||
        FULL_SISTER in group || HeirKind.PATERNAL_SISTER in group
    return cite(if (byOther) "229" else "227", "16")
}

/** مصر م10: «يشارك أولاد الأم الأخ الشقيق أو الإخوة الأشقاء بالانفراد أو مع أخت شقيقة أو أكثر، ويقسم الثلث بينهم جميعًا» بالتساوي. */
private fun ShareState.shareMaternalThird(shares: MutableMap<HeirKind, Frac>, basis: MutableMap<HeirKind, ShareBasis>) {
    val pool = listOf(MATERNAL_BROTHER, MATERNAL_SISTER).mapNotNull { shares[it] }.sumFrac()
    val members = listOf(MATERNAL_BROTHER, MATERNAL_SISTER, FULL_BROTHER, FULL_SISTER).filter { has(it) && it !in blocked }
    val persons = members.sumOf { c(it) }
    for (k in members) {
        shares[k] = pool * c(k) / persons
        basis[k] = ShareBasis.FARD
    }
    note(InheritanceNoteKind.MUSHTARAKA_SHARED, Citation(LawSource.EG_INHERITANCE, "10"))
}

/**
 * أصل المسألة = المضاعف المشترك لمقامات الفروض **بالمجموعة** (السدس للجدات مع بعض · الثلث للإخوة لأم مع بعض) — زي كتب الفرائض
 * (أصل 6 و12 و24)، مش بعد قسمته على الأفراد.
 */
private fun ShareState.awl(shares: MutableMap<HeirKind, Frac>, fardSum: Frac) {
    val pooled = listOf(setOf(HeirKind.PATERNAL_GRANDMOTHER, HeirKind.MATERNAL_GRANDMOTHER), setOf(MATERNAL_BROTHER, MATERNAL_SISTER))
    val grouped = fard.keys.groupBy { k -> pooled.firstOrNull { k in it } ?: setOf(k) }.map { (_, ks) -> ks.map { fard.getValue(it) }.sumFrac() }
    val base = grouped.filter { it.isPositive }.fold(1L) { acc, f -> lcmOf(acc, f.den) }
    val raised = fardSum * Frac.of(base)
    note(InheritanceNoteKind.AWL, cite("230", "15"), values = listOf(base.toString(), raised.toString()))
    for ((k, f) in fard) shares[k] = f / fardSum
}

private fun ShareState.radd(shares: MutableMap<HeirKind, Frac>, basis: MutableMap<HeirKind, ShareBasis>, distantRelatives: Boolean?): InheritanceResult? {
    val others = fard.filter { (k, f) -> k !in SPOUSES && f.isPositive }
    if (others.isNotEmpty()) {
        val spouseTotal = fard.filterKeys { it in SPOUSES }.values.sumFrac()
        val rest = Frac.ONE - spouseTotal
        val sumOthers = others.values.sumFrac()
        for ((k, f) in others) {
            shares[k] = rest * f / sumOthers
            basis[k] = ShareBasis.RADD
        }
        note(InheritanceNoteKind.RADD, cite("231/1", "30"))
        return null
    }
    // الزوج/الزوجة لوحدهم: ذوو الأرحام قبلهم في الباقي (السعودية م234/2 · مصر م30–31)
    when (distantRelatives) {
        null -> return InheritanceResult.Unsupported(UnsupportedReason.ASK_DISTANT_RELATIVES)
        true -> return InheritanceResult.Unsupported(UnsupportedReason.DISTANT_RELATIVES)
        false -> Unit
    }
    val spouse = SPOUSES.first { has(it) }
    shares[spouse] = Frac.ONE
    basis[spouse] = ShareBasis.RADD
    note(InheritanceNoteKind.RADD_TO_SPOUSE, cite("231/2", "30"), listOf(spouse))
    return null
}
