package app.masroufy.core

import app.masroufy.core.HeirKind.FULL_BROTHER_DAUGHTER
import app.masroufy.core.HeirKind.PATERNAL_BROTHER_DAUGHTER

/**
 * **ذوو الأرحام في مصر — بالقرابة** (قانون المواريث 77/1943 — **نص الجريدة الرسمية**: الوقائع المصرية عدد 92، 12 أغسطس 1943،
 * https://manshurat.org/node/12494 — ثقة A−):
 * > م31: أربع أصناف «مقدم بعضها على بعض»: (1) أولاد البنات وأولاد بنات الابن (2) الجد والجدة غير الصحيحين (3) أبناء الإخوة لأم
 * >   وأولاد الأخوات وبنات الإخوة (4) … الطايفة الأولى: «أعمام الميت لأم وعماته وأخواله وخالاته لأبوين أو لأحدهما».
 * > م32: الصنف الأول «أولاهم بالميراث أقربهم إلى الميت درجة»، ثم ولد صاحب الفرض قبل ولد ذي الرحم، وإلا يشتركوا.
 * > م33: الصنف التاني الأقرب درجة … (الجد أبو الأم لوحده في النسخة دي).
 * > م34: الصنف التالت الأقرب درجة، «فإن استووا في الدرجة وكان فيهم ولد عاصب فهو أولى من ولد ذي رحم وإلا قدم أقواهم قرابة للميت:
 * >   فمن كان أصله لأبوين فهو أولى ممن كان أصله لأب، ومن كان أصله لأب فهو أولى ممن كان أصله لأم».
 * > م35: الطايفة الأولى من الصنف الرابع: فريق الأب (أعمام الميت لأم وعماته) وفريق الأم (أخواله وخالاته) — لوحده ⇒ الأقوى قرابة
 * >   (لأبوين ثم لأب ثم لأم) — مع بعض ⇒ «الثلثان لقرابة الأب والثلث لقرابة الأم».
 * > م38: «في إرث ذوي الأرحام يكون للذكر مثل حظ الأنثيين» — على الأشخاص (العدد بس بيكفي، من غير ما نعرف أولاد مين).
 *
 * - **ولد العاصب (م34 — الجريدة «وإلا قدم»، مش «والأقدم» زي النسخة القديمة):** الترتيب شرطي — لو فيهم ولد عاصب (بنت الأخ الشقيق
 *   أو لأب) **وفيهم غيره** ⇒ ولد العاصب الأول، وبعدها القوة بينهم. لو مفيش ولد عاصب أو كلهم أولاد عاصب ⇒ القوة على طول.
 *   (قراية المنسّق للنص الرسمي — 2026-10-05.) ⚠️ أولاد الأخوات والإخوة لأم أولاد **أصحاب فروض** مش «ذي رحم» — في قراية تانية الجملة
 *   ما بتنطبقش عليهم والقوة بتحكم (ابن الأخت الشقيقة يقدّم على بنت الأخ لأب). عشان كده لما ولد العاصب يغلب واحد مش ولد عاصب
 *   النتيجة بتطلع **بتنبيه** «ممكن يختلف عن حكم المحكمة» (سياسة المالك للنص الغامض — §69.3).
 */
internal fun egyptDistant(distant: Map<HeirKind, Int>): DistantSplit {
    fun eg(article: String) = Citation(LawSource.EG_INHERITANCE, article)
    val notes = mutableListOf(InheritanceNote(InheritanceNoteKind.DISTANT_EGYPT_ORDER, eg("31")))
    val excluded = LinkedHashSet<HeirKind>()
    fun drop(kinds: List<HeirKind>, by: HeirKind, article: String) {
        for (k in kinds) {
            excluded += k
            notes += InheritanceNote(InheritanceNoteKind.BLOCKED, eg(article), listOf(k, by))
        }
    }
    fun keepMost(kinds: List<HeirKind>, value: (HeirKind) -> Int, article: String): List<HeirKind> {
        val best = kinds.maxOf(value)
        val top = kinds.filter { value(it) == best }
        drop(kinds.filter { it !in top }, top.first(), article)
        return top
    }

    val all = DISTANT_INFO.keys.filter { it in distant }
    // م31: الصنف الأقرب بس
    val inClass = keepMost(all, { -distantInfo(it).egClass }, "31")
    val shares: Map<HeirKind, Frac> = when (distantInfo(inClass.first()).egClass) {
        // م32 · م33: الأقرب درجة (أولاد البنات قبل أولاد بنات الابن)، وكلهم أولاد أصحاب فروض ⇒ يشتركوا
        1, 2 -> maleDouble(keepMost(inClass, { -distantInfo(it).egDegree }, if (distantInfo(inClass.first()).egClass == 1) "32" else "33"), distant)
        // م34: الدرجة واحدة (أولاد الإخوة) ⇒ ولد العاصب الأول (لو فيهم غيره) ⇒ الأقوى قرابة
        3 -> {
            val agnates = setOf(FULL_BROTHER_DAUGHTER, PATERNAL_BROTHER_DAUGHTER)
            val mixed = inClass.any { it in agnates } && inClass.any { it !in agnates }
            if (mixed) notes += InheritanceNote(InheritanceNoteKind.UNCLEAR_TEXT, eg("34"))
            val first = if (mixed) keepMost(inClass, { if (it in agnates) 1 else 0 }, "34") else inClass
            maleDouble(keepMost(first, { distantInfo(it).egStrength }, "34"), distant)
        }
        // م35: كل فريق الأقوى فيه، والفريقين ⇒ التلتين والتلت
        else -> {
            val fatherSide = inClass.filter { distantInfo(it).egFatherSide }
            val motherSide = inClass.filter { !distantInfo(it).egFatherSide }
            val f = if (fatherSide.isEmpty()) emptyList() else keepMost(fatherSide, { distantInfo(it).egStrength }, "35")
            val m = if (motherSide.isEmpty()) emptyList() else keepMost(motherSide, { distantInfo(it).egStrength }, "35")
            when {
                f.isEmpty() -> maleDouble(m, distant)
                m.isEmpty() -> maleDouble(f, distant)
                else -> {
                    notes += InheritanceNote(InheritanceNoteKind.DISTANT_SIDES, eg("35"))
                    maleDouble(f, distant).mapValues { it.value * Frac.of(2, 3) } + maleDouble(m, distant).mapValues { it.value * Frac.of(1, 3) }
                }
            }
        }
    }
    val out = LinkedHashMap<HeirKind, Frac>()
    val personShares = LinkedHashMap<HeirKind, List<Frac>>()
    for (k in all) {
        val share = if (k in excluded) Frac.ZERO else shares[k] ?: Frac.ZERO
        out[k] = share
        personShares[k] = List(distant.getValue(k)) { share / distant.getValue(k) }
    }
    return DistantSplit.Done(out, personShares, excluded, notes)
}
