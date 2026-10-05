package app.masroufy.core

import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.PATERNAL_BROTHER
import app.masroufy.core.HeirKind.PATERNAL_SISTER

/**
 * **الجد مع الإخوة في مصر** — قانون 77/1943 م22 (نص النسخة غير الرسمية):
 * > إذا اجتمع الجد مع الإخوة والأخوات لأبوين أو لأب كانت له حالتان:
 * > الأولى: أن يقاسمهم كأخ إن كانوا ذكورًا فقط أو ذكورًا وإناثًا أو إناثًا عُصّبن مع الفرع الوارث من الإناث.
 * > الثانية: أن يأخذ الباقي بعد أصحاب الفروض بطريق التعصيب إذا كان مع أخوات لم يُعصّبن بالذكور ولا مع الفرع الوارث من الإناث.
 * > على أنه إذا كانت المقاسمة أو الإرث بالتعصيب على الوجه المتقدم تحرم الجد من الإرث أو تنقصه عن السدس اعتُبر صاحب فرض بالسدس،
 * > ولا يُعتبر في المقاسمة من كان محجوبًا من الإخوة أو الأخوات لأب.
 *
 * التنفيذ:
 * - الحالة الأولى: الجد بيدخل العصبة بوزن أخ (2) والأخت بوزن 1.
 * - الحالة التانية: الأخوات بفرضهم (النص أو الثلثين) والجد الباقي.
 * - الحد الأدنى السدس بيتفحص في الإكمال ([ShareState.grandfatherWithSiblings]) **على التركة كلها قبل العول**.
 * - **اختيار Claude (مش متأكد — مكتوب في HANDOVER):** مع البنات الجد ما بياخدش سدس الأب الأول (م21) — م22 هي الخاصة بالجد مع الإخوة،
 *   فبيقاسم في الباقي بعد فرض البنات وبحد السدس.
 * - **لا نص:** أشقاء ولأب **الاتنين وارثين** مع الجد (شقيقة واحدة بفرضها + أخت لأب · أو شقيقات + أخ لأب): المادة ما بتقولش يتقسم
 *   إزاي (مفيش «معادّة» في النص) ⇒ «اسأل المحكمة».
 * - **الأكدرية:** النص ما فيهوش ضم نصيب الأخت للجد، فـ(زوج · أم · جد · أخت شقيقة) = نص · ثلث · سدس للجد (م22) · نص للأخت ⇒ عول من 6 لـ9.
 */
internal fun ShareState.egyptGrandfatherWithSiblings(): InheritanceResult? {
    val setup = effectiveSiblings()
    val blockedNow = setup.blocks.map { it.first }.toSet()
    val full = listOf(FULL_BROTHER, FULL_SISTER).any { has(it) && it !in blockedNow }
    val paternal = listOf(PATERNAL_BROTHER, PATERNAL_SISTER).any { has(it) && it !in blockedNow }
    if (full && paternal) return InheritanceResult.NoText(NoTextReason.GRANDFATHER_MIXED_SIBLINGS, Citation(LawSource.EG_INHERITANCE, "22"))
    grandfatherWithSiblings = true
    if (setup.group.isNotEmpty()) {
        applySiblings(setup.copy(group = listOf(GRANDFATHER to 2) + setup.group))
        note(InheritanceNoteKind.GRANDFATHER_SHARES, Citation(LawSource.EG_INHERITANCE, "22"))
    } else {
        applySiblings(setup)
        addGroup(listOf(GRANDFATHER to 2))
        note(InheritanceNoteKind.GRANDFATHER_RESIDUE, Citation(LawSource.EG_INHERITANCE, "22"))
    }
    return null
}

/** م22 (آخرها): لو نصيب الجد بالمقاسمة أو بالباقي أقل من السدس (أو صفر) ⇒ ياخد السدس فرضًا، والإخوة الباقي لو فيه. */
internal fun ShareState.applyGrandfatherSixthFloor(sixth: Frac) {
    if (!grandfatherWithSiblings || GRANDFATHER !in group) return
    val residue = Frac.ONE - fard.values.sumFrac()
    val totalWeight = group.entries.sumOf { (k, w) -> w * c(k) }
    val mine = if (residue.isPositive) residue * group.getValue(GRANDFATHER) / totalWeight else Frac.ZERO
    if (mine >= sixth) return
    group.remove(GRANDFATHER)
    fard[GRANDFATHER] = sixth
    if (lead == GRANDFATHER) lead = group.keys.firstOrNull()
    note(InheritanceNoteKind.GRANDFATHER_SIXTH, Citation(LawSource.EG_INHERITANCE, "22"))
}
