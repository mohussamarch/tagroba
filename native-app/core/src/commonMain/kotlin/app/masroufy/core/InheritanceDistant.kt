package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_SON
import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_BROTHER_DAUGHTER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.FULL_SISTER_DAUGHTER
import app.masroufy.core.HeirKind.FULL_SISTER_SON
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_AUNT_FULL
import app.masroufy.core.HeirKind.MATERNAL_AUNT_MATERNAL
import app.masroufy.core.HeirKind.MATERNAL_AUNT_PATERNAL
import app.masroufy.core.HeirKind.MATERNAL_BROTHER
import app.masroufy.core.HeirKind.MATERNAL_BROTHER_DAUGHTER
import app.masroufy.core.HeirKind.MATERNAL_BROTHER_SON
import app.masroufy.core.HeirKind.MATERNAL_GRANDFATHER
import app.masroufy.core.HeirKind.MATERNAL_HALF_UNCLE
import app.masroufy.core.HeirKind.MATERNAL_SISTER
import app.masroufy.core.HeirKind.MATERNAL_SISTER_DAUGHTER
import app.masroufy.core.HeirKind.MATERNAL_SISTER_SON
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_FULL
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_MATERNAL
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_PATERNAL
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_AUNT_FULL
import app.masroufy.core.HeirKind.PATERNAL_AUNT_MATERNAL
import app.masroufy.core.HeirKind.PATERNAL_AUNT_PATERNAL
import app.masroufy.core.HeirKind.PATERNAL_BROTHER
import app.masroufy.core.HeirKind.PATERNAL_BROTHER_DAUGHTER
import app.masroufy.core.HeirKind.PATERNAL_SISTER
import app.masroufy.core.HeirKind.PATERNAL_SISTER_DAUGHTER
import app.masroufy.core.HeirKind.PATERNAL_SISTER_SON
import app.masroufy.core.HeirKind.SON_DAUGHTER
import app.masroufy.core.HeirKind.SON_DAUGHTER_DAUGHTER
import app.masroufy.core.HeirKind.SON_DAUGHTER_SON
import app.masroufy.core.HeirKind.WIFE

/**
 * **ذوو الأرحام** (OVERRIDES §69.4) — القريب اللي مش بيورث بفرض ولا بتعصيب (السعودية م232 · مصر م31).
 * بيورثوا بس لو **مفيش صاحب فرض ولا عاصب** غير الزوج/الزوجة (السعودية م234 · مصر م31)، والزوج/الزوجة بياخد نصيبه كامل الأول
 * والباقي ليهم — والرد على الزوج/الزوجة بعدهم بس (السعودية م231/2 · مصر م30).
 *
 * - **السعودية** (نظام الأحوال الشخصية — النص الرسمي من laws.boe.gov.sa): بالتنزيل — كل واحد في مكان اللي بيوصله بالميت «إرثاً وحجباً»
 *   والذكر زي الأنثى (م235)، والجهات تلاتة (م233)، ومن نفس الجهة الأقرب للميت بيسقط الأبعد ومن جهات مختلفة بيورثوا مع بعض (م236).
 *   `InheritanceDistantSaudi.kt`.
 * - **مصر** (77/1943 — نص الجريدة الرسمية، الوقائع المصرية عدد 92): بالقرابة — أربع أصناف بالترتيب (م31)، ثم الأقرب درجة، ثم ولد
 *   العاصب، ثم الأقوى، والجهتين في الصنف الرابع التلتين والتلت (م32–36)، وللذكر مثل حظ الأنثيين (م38). `InheritanceDistantEgypt.kt`.
 */
internal enum class DistantSide { BUNUWWA, UBUWWA, UMUMA }

/**
 * بيانات كل نوع:
 * - [side] جهته في السعودية (م233: البنوة · الأبوة · الأمومة) · [via] الوارث اللي بيتنزّل مكانه (م235) · [place] صلته بالوارث ده —
 *   الأنواع اللي ليها نفس [via] ونفس [place] (ذكر وأنثى بنفس الصلة) بيتقسم نصيبه بينهم بالتساوي، ولو الصلة مختلفة ⇒ «لا نص».
 * - القرب من الميت (م236 «أقرب للميت» من غير ما تقول بيتعد إزاي) بالطريقتين المعروفتين: [civil] الأجيال لحد الأصل المشترك ونازل ·
 *   [links] عدد الأشخاص بينه وبين الميت (الأخ جنب أخوه). لو الطريقتين اختلفوا في مين الأقرب ⇒ «لا نص».
 * - مصر: [egClass] الصنف (م31) · [egDegree] الدرجة من الميت (م32) · [egStrength] قوة القرابة 3 لأبوين · 2 لأب · 1 لأم (م34–35) ·
 *   [egFatherSide] من قرابة الأب في الصنف الرابع (م35).
 */
internal data class DistantInfo(
    val side: DistantSide,
    val via: HeirKind,
    val place: Int,
    val civil: Int,
    val links: Int,
    val egClass: Int,
    val egDegree: Int,
    val egStrength: Int = 0,
    val egFatherSide: Boolean = false,
)

private val BUN = DistantSide.BUNUWWA
private val UBU = DistantSide.UBUWWA
private val UMU = DistantSide.UMUMA

internal val DISTANT_INFO: Map<HeirKind, DistantInfo> = linkedMapOf(
    // الصنف الأول (مصر م31) · جهة البنوة (السعودية م233/3): ولد البنت · ولد بنت الابن
    DAUGHTER_SON to DistantInfo(BUN, DAUGHTER, 0, civil = 2, links = 1, egClass = 1, egDegree = 2),
    DAUGHTER_DAUGHTER to DistantInfo(BUN, DAUGHTER, 0, civil = 2, links = 1, egClass = 1, egDegree = 2),
    SON_DAUGHTER_SON to DistantInfo(BUN, SON_DAUGHTER, 0, civil = 3, links = 2, egClass = 1, egDegree = 3),
    SON_DAUGHTER_DAUGHTER to DistantInfo(BUN, SON_DAUGHTER, 0, civil = 3, links = 2, egClass = 1, egDegree = 3),
    // الصنف التالت (مصر): أولاد الأخوات · بنات الإخوة · أولاد الإخوة لأم. السعودية: «ولد الأخت لغير أم» و«بنت الأخ لغير أم» جهة الأبوة (م233/1)،
    // و«ولد الأخ والأخت لأم» جهة الأمومة (م233/2)
    FULL_SISTER_SON to DistantInfo(UBU, FULL_SISTER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 3),
    FULL_SISTER_DAUGHTER to DistantInfo(UBU, FULL_SISTER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 3),
    PATERNAL_SISTER_SON to DistantInfo(UBU, PATERNAL_SISTER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 2),
    PATERNAL_SISTER_DAUGHTER to DistantInfo(UBU, PATERNAL_SISTER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 2),
    FULL_BROTHER_DAUGHTER to DistantInfo(UBU, FULL_BROTHER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 3),
    PATERNAL_BROTHER_DAUGHTER to DistantInfo(UBU, PATERNAL_BROTHER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 2),
    MATERNAL_BROTHER_SON to DistantInfo(UMU, MATERNAL_BROTHER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 1),
    MATERNAL_BROTHER_DAUGHTER to DistantInfo(UMU, MATERNAL_BROTHER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 1),
    MATERNAL_SISTER_SON to DistantInfo(UMU, MATERNAL_SISTER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 1),
    MATERNAL_SISTER_DAUGHTER to DistantInfo(UMU, MATERNAL_SISTER, 0, civil = 3, links = 1, egClass = 3, egDegree = 3, egStrength = 1),
    // الصنف التاني (مصر): الجد غير الصحيح · السعودية: «الجد غير الوارث» من جهة الأمومة (م233/2) ⇒ في مكان الأم
    MATERNAL_GRANDFATHER to DistantInfo(UMU, MOTHER, 1, civil = 2, links = 1, egClass = 2, egDegree = 2),
    // الصنف الرابع، الطايفة الأولى (مصر م31 و35): الأخوال والخالات (قرابة الأم) · العمات والأعمام لأم (قرابة الأب).
    // السعودية: الخال والخالة جهة الأمومة (م233/2) في مكان الأم · العم لأم والعمة جهة الأبوة (م233/1) في مكان الأب
    MATERNAL_UNCLE_FULL to DistantInfo(UMU, MOTHER, 2, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 3),
    MATERNAL_AUNT_FULL to DistantInfo(UMU, MOTHER, 2, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 3),
    MATERNAL_UNCLE_PATERNAL to DistantInfo(UMU, MOTHER, 3, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 2),
    MATERNAL_AUNT_PATERNAL to DistantInfo(UMU, MOTHER, 3, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 2),
    MATERNAL_UNCLE_MATERNAL to DistantInfo(UMU, MOTHER, 4, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 1),
    MATERNAL_AUNT_MATERNAL to DistantInfo(UMU, MOTHER, 4, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 1),
    PATERNAL_AUNT_FULL to DistantInfo(UBU, FATHER, 5, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 3, egFatherSide = true),
    PATERNAL_AUNT_PATERNAL to DistantInfo(UBU, FATHER, 6, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 2, egFatherSide = true),
    PATERNAL_AUNT_MATERNAL to DistantInfo(UBU, FATHER, 7, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 1, egFatherSide = true),
    MATERNAL_HALF_UNCLE to DistantInfo(UBU, FATHER, 7, civil = 3, links = 1, egClass = 4, egDegree = 3, egStrength = 1, egFatherSide = true),
)

val HeirKind.isDistant: Boolean get() = this in DISTANT_INFO

internal fun distantInfo(k: HeirKind): DistantInfo = DISTANT_INFO.getValue(k)

/** الشخص اللي ممكن يبقى ليه أولاد من ذوي الأرحام ⇒ (نوع ولده الذكر أو null · نوع بنته). ابن الأخ الشقيق/لأب عاصب ⇒ null. */
val DISTANT_BRANCH_CHILDREN: Map<HeirKind, Pair<HeirKind?, HeirKind>> = linkedMapOf(
    DAUGHTER to (DAUGHTER_SON to DAUGHTER_DAUGHTER),
    SON_DAUGHTER to (SON_DAUGHTER_SON to SON_DAUGHTER_DAUGHTER),
    FULL_SISTER to (FULL_SISTER_SON to FULL_SISTER_DAUGHTER),
    PATERNAL_SISTER to (PATERNAL_SISTER_SON to PATERNAL_SISTER_DAUGHTER),
    FULL_BROTHER to (null to FULL_BROTHER_DAUGHTER),
    PATERNAL_BROTHER to (null to PATERNAL_BROTHER_DAUGHTER),
    MATERNAL_BROTHER to (MATERNAL_BROTHER_SON to MATERNAL_BROTHER_DAUGHTER),
    MATERNAL_SISTER to (MATERNAL_SISTER_SON to MATERNAL_SISTER_DAUGHTER),
)

/** [InheritanceCase.distantBranches]: كل شخص نوعه معروف وليه ولد واحد على الأقل، ومجموع أولاد كل نوع = العدد المكتوب. */
internal fun validateDistantBranches(case: InheritanceCase): InheritanceResult.Invalid? {
    fun invalid(parent: HeirKind) = InheritanceResult.Invalid(InvalidReason.DISTANT_BRANCHES, listOf(parent.label))
    for (b in case.distantBranches) {
        val children = DISTANT_BRANCH_CHILDREN[b.parent] ?: return invalid(b.parent)
        if (b.sons < 0 || b.daughters < 0 || b.sons > INHERIT_MAX_COUNT || b.daughters > INHERIT_MAX_COUNT) {
            return InheritanceResult.Invalid(InvalidReason.COUNT_RANGE, listOf(b.parent.label, INHERIT_MAX_COUNT.toString()))
        }
        if (b.sons + b.daughters == 0 || (children.first == null && b.sons > 0)) return invalid(b.parent)
    }
    for ((parent, list) in case.distantBranches.groupBy { it.parent }) {
        val (sonKind, daughterKind) = DISTANT_BRANCH_CHILDREN.getValue(parent)
        val sons = sonKind?.let { case.heirs[it] ?: 0 } ?: 0
        if (list.sumOf { it.sons } != sons || list.sumOf { it.daughters } != (case.heirs[daughterKind] ?: 0)) return invalid(parent)
    }
    return null
}

/** تقسيم ذوي الأرحام: نصيب كل نوع من الباقي (مجموعهم 1) · نصيب كل فرد · اللي ما ورثش · الملاحظات. أو وقفة («لا نص» / «غير مدعوم»). */
internal sealed interface DistantSplit {
    data class Done(
        val shares: Map<HeirKind, Frac>,
        val personShares: Map<HeirKind, List<Frac>>,
        val excluded: Set<HeirKind>,
        val notes: List<InheritanceNote>,
    ) : DistantSplit

    data class Stop(val result: InheritanceResult) : DistantSplit
}

/** للذكر مثل حظ الأنثيين بين الأنواع دي (مصر م38) — نصيب كل نوع من 1. */
internal fun maleDouble(kinds: List<HeirKind>, counts: Map<HeirKind, Int>): Map<HeirKind, Frac> {
    val total = kinds.sumOf { counts.getValue(it) * (if (it.male) 2 else 1) }
    return kinds.associateWith { Frac.of((counts.getValue(it) * (if (it.male) 2 else 1)).toLong(), total.toLong()) }
}

/**
 * ذوو الأرحام بيورثوا (مفيش وارث غير الزوج/الزوجة): الزوج/الزوجة ياخد فرضه كامل (النص أو الربع — ذوو الأرحام مش «فرع وارث»:
 * السعودية م205 «ولا يعد من الفرع الوارث من أدلى بأنثى» · مصر م11 «الولد وولد الابن وإن نزل») والباقي ليهم بقانون البلد.
 */
internal fun ShareState.distantOutcome(distant: Map<HeirKind, Int>, branches: List<DistantBranch>): SharesOutcome {
    val spouse = listOf(HUSBAND, WIFE).firstOrNull { has(it) }
    val spouseShare = spouse?.let { fard.getValue(it) } ?: Frac.ZERO
    val pool = Frac.ONE - spouseShare
    val split = when (law) {
        InheritanceLaw.SA -> saudiDistant(distant, branches)
        InheritanceLaw.EG -> egyptDistant(distant)
    }
    if (split is DistantSplit.Stop) return SharesOutcome.Stop(split.result)
    split as DistantSplit.Done
    val shares = LinkedHashMap<HeirKind, Frac>()
    val basis = LinkedHashMap<HeirKind, ShareBasis>()
    if (spouse != null) {
        shares[spouse] = spouseShare
        basis[spouse] = ShareBasis.FARD
        note(InheritanceNoteKind.DISTANT_BEFORE_SPOUSE_RADD, cite("231/2", "30"), listOf(spouse))
    }
    for ((k, f) in split.shares) {
        shares[k] = pool * f
        basis[k] = if (k in split.excluded) ShareBasis.BLOCKED else ShareBasis.DISTANT
    }
    val persons = split.personShares.mapValues { (_, list) -> list.map { pool * it } }
    return SharesOutcome.Shares(shares, basis, notes + split.notes, persons)
}
