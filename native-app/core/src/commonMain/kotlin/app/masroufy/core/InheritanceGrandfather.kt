package app.masroufy.core

import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.PATERNAL_BROTHER
import app.masroufy.core.HeirKind.PATERNAL_SISTER

/**
 * **الجد مع الإخوة في مصر** — قانون 77/1943 م22 (نص الجريدة الرسمية — الوقائع المصرية عدد 92، 12 أغسطس 1943):
 * > إذا اجتمع الجد مع الإخوة والأخوات لأبوين أو لأب كانت له حالتان:
 * > الأولى — أن يقاسمهم كأخ إن كانوا ذكورا فقط أو ذكورا وإناثا **أو** إناثا عصبن مع الفرع الوارث من الإناث.
 * > الثانية — أن يأخذ الباقى بعد أصحاب الفروض بطريق التعصيب إذا كان مع أخوات لم يعصبن بالذكور أو مع الفرع الوارث من الإناث.
 * > على أنه إذا كانت المقاسمة أو الإرث بالتعصيب على الوجه المتقدم تحرم الجد من الإرث أو تنقصه عن السدس اعتبر صاحب فرض بالسدس.
 * > ولا يعتبر فى المقاسمة من كان محجوبا من الإخوة أو الأخوات لأب.
 *
 * التنفيذ:
 * - الحالة الأولى — **تلت بدايل منفصلة** (الجريدة «أو»، مش «و» زي النسخة القديمة): إخوة ذكور بس · ذكور وإناث · **أخوات بس بقوا عصبة مع
 *   البنات أو بنات الابن** (م20). في التلاتة الجد بيدخل العصبة بوزن أخ (2) والأخت بوزن 1.
 * - الحالة التانية: الأخوات بفرضهم (النص أو الثلثين) والجد الباقي.
 * - الحد الأدنى السدس بيتفحص في الإكمال ([ShareState.grandfatherWithSiblings]) **على التركة كلها قبل العول**.
 * - **اختيار Claude (مش متأكد — مكتوب في HANDOVER):** مع البنات الجد ما بياخدش سدس الأب الأول (م21) — م22 هي الخاصة بالجد مع الإخوة
 *   (والبديل التالت فيها بيذكر الفرع الوارث من الإناث بالاسم)، فبيقاسم في الباقي بعد فرض البنات وبحد السدس.
 * - **لا نص:** أشقاء ولأب **الاتنين وارثين** مع الجد (شقيقة واحدة بفرضها + أخت لأب · أو شقيقات + أخ لأب): المادة ما بتقولش يتقسم
 *   إزاي (مفيش «معادّة» في النص) ⇒ «اسأل المحكمة».
 * - **الأكدرية:** النص ما فيهوش ضم نصيب الأخت للجد، فـ(زوج · أم · جد · أخت شقيقة) = نص · ثلث · سدس للجد (م22) · نص للأخت ⇒ عول من 6 لـ9.
 * - **رد المالك (§69.3):** المسألتين اللي فوق (الجد مع البنات، والأكدرية وأخواتها) **بحسابهم ده + ملاحظة** «ممكن يختلف عن حكم المحكمة —
 *   النص مش واضح في المسألة دي» ([InheritanceNoteKind.UNCLEAR_TEXT]). و«لا نص» (أشقاء ولأب مع بعض) فاضلة زي ما هي.
 */
internal fun ShareState.egyptGrandfatherWithSiblings(): InheritanceResult? {
    val setup = effectiveSiblings()
    val blockedNow = setup.blocks.map { it.first }.toSet()
    val full = listOf(FULL_BROTHER, FULL_SISTER).any { has(it) && it !in blockedNow }
    val paternal = listOf(PATERNAL_BROTHER, PATERNAL_SISTER).any { has(it) && it !in blockedNow }
    if (full && paternal) return InheritanceResult.NoText(NoTextReason.GRANDFATHER_MIXED_SIBLINGS, Citation(LawSource.EG_INHERITANCE, "22"))
    grandfatherWithSiblings = true
    // رد المالك (§69.3): الجد مع البنات والإخوة — الحساب زي ما هو + تنبيه إن النص مش واضح
    if (femaleDescendant) note(InheritanceNoteKind.UNCLEAR_TEXT, Citation(LawSource.EG_INHERITANCE, "22"))
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
    // رد المالك (§69.3): الأكدرية وأخواتها — الأخوات بفرضهم والجد بالسدس والفروض فوق التركة (عول). النص ما فيهوش الضم المعروف في
    // كتب الفرائض ⇒ الحساب زي ما هو + تنبيه إن النص مش واضح
    val sistersByFard = listOf(FULL_SISTER, PATERNAL_SISTER).any { it in fard }
    if (sistersByFard && fard.values.sumFrac() > Frac.ONE) note(InheritanceNoteKind.UNCLEAR_TEXT, Citation(LawSource.EG_INHERITANCE, "22"))
}
