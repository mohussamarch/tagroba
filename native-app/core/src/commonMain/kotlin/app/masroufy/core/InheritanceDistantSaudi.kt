package app.masroufy.core

/**
 * **ذوو الأرحام في السعودية — بالتنزيل** (نظام الأحوال الشخصية 1443هـ، النص الرسمي من laws.boe.gov.sa):
 * > م233: «لذوي الأرحام (ثلاث) جهات»: الأبوة (العمة · العم لأم · بنت الأخ لغير أم · ولد الأخت لغير أم …) · الأمومة (الجد غير الوارث ·
 * >   الخال · الخالة · ولد الأخ والأخت لأم …) · البنوة (ولد البنت · ولد بنت الابن …).
 * > م235: «يكون توريث ذوي الأرحام بتنزيل كل واحد منهم منزلة من أدلى به من الورثة إرثاً وحجباً، دون تفاضل بين سهم الذكر وسهم الأنثى».
 * > م236: «إذا اتحدت جهات ذوي الأرحام وكان بعضهم أقرب للميت، فيسقط الأبعد، وإذا اختلفت الجهات فيرث البعيد مع وجود القريب».
 *
 * التنفيذ (بالترتيب):
 * 1. **م236 — الأقرب في نفس الجهة:** «أقرب للميت» بتتقاس بالطريقتين ([DistantInfo.civil] و[DistantInfo.links]). لو اتفقوا ⇒ الأبعد بيسقط.
 *    لو اختلفوا (الجد أبو الأم مع الخال أو أولاد الإخوة لأم: 2 مقابل 3 أجيال، بس الاتنين بينهم وبين الميت شخص واحد) ⇒ **«لا نص»**.
 * 2. **م235 — التنزيل:** كل واحد مكان اللي بيوصله ([DistantInfo.via]): البنت · بنت الابن · الأخت · الأخ · الأخ أو الأخت لأم · الأم · الأب.
 *    المسألة بتتحسب بالورثة دول كأنهم الورثة (بنفس المحرك — فروض وعصبة **وحجب** ورد وعول)، وكل واحد بياخد نصيب اللي اتنزّل مكانه.
 *    أولاد أكتر من شخص (بنتين ماتوا …) ⇒ كل شخص وارث لوحده ([DistantBranch])؛ لو مش معروف من كام شخص ⇒ «غير مدعوم» بسؤال.
 * 3. **«دون تفاضل»:** نصيب الشخص بيتقسم على اللي في مكانه **بالتساوي** (ذكر وأنثى). أكتر من صلة في مكان نفس الشخص (خال شقيق وخال لأب،
 *    عمة وعم لأم من غير نفس الصلة …) ⇒ المادة ما بتقولش نصيبه يتقسم بينهم إزاي ⇒ **«لا نص»** (م251).
 * 4. **«حجباً»:** اللي اتنزّل مكان وارث محجوب (ابن الأخ لأم مع ابن البنت: البنت بتحجب الأخ لأم — م219) ما بيورثش، حتى لو من جهة تانية.
 *    ⚠️ قراية Claude لنص م235 «إرثاً وحجباً» — م236 بتقول «يرث البعيد مع وجود القريب» عن القرب بس. مكتوب في الأسئلة المفتوحة.
 */
internal fun saudiDistant(distant: Map<HeirKind, Int>, branches: List<DistantBranch>): DistantSplit {
    fun sa(article: String) = Citation(LawSource.SA_PERSONAL_STATUS, article)
    val notes = mutableListOf<InheritanceNote>()
    val excluded = LinkedHashSet<HeirKind>()

    // 1. م236: الأبعد من نفس الجهة بيسقط — بس لو طريقتين العدّ متفقين
    val kept = mutableListOf<HeirKind>()
    for ((_, kinds) in distant.keys.groupBy { distantInfo(it).side }) {
        val minCivil = kinds.minOf { distantInfo(it).civil }
        val minLinks = kinds.minOf { distantInfo(it).links }
        val nearCivil = kinds.filter { distantInfo(it).civil == minCivil }
        val nearLinks = kinds.filter { distantInfo(it).links == minLinks }
        if (nearCivil.toSet() != nearLinks.toSet()) return DistantSplit.Stop(InheritanceResult.NoText(NoTextReason.DISTANT_NEARNESS, sa("236")))
        kept += nearCivil
        for (k in kinds.filter { it !in nearCivil }) {
            excluded += k
            notes += InheritanceNote(InheritanceNoteKind.BLOCKED, sa("236"), listOf(k, nearCivil.first()))
        }
    }

    // 2–3. مين في مكان مين — ومكان الشخص الواحد لازم صلة واحدة
    val byVia = LinkedHashMap<HeirKind, MutableList<HeirKind>>()
    for (k in DISTANT_INFO.keys.filter { it in kept }) byVia.getOrPut(distantInfo(k).via) { mutableListOf() } += k
    for ((_, kinds) in byVia) {
        if (kinds.map { distantInfo(it).place }.distinct().size > 1) {
            return DistantSplit.Stop(InheritanceResult.NoText(NoTextReason.DISTANT_SAME_PLACE, sa("235")))
        }
    }
    val branchesByVia = LinkedHashMap<HeirKind, List<DistantBranch>>()
    for (via in byVia.keys) {
        val (sonKind, daughterKind) = DISTANT_BRANCH_CHILDREN[via] ?: continue
        val given = branches.filter { it.parent == via }
        val sons = sonKind?.let { distant[it] } ?: 0
        val daughters = distant[daughterKind] ?: 0
        branchesByVia[via] = when {
            given.isNotEmpty() -> given
            sons + daughters == 1 -> listOf(DistantBranch(via, sons, daughters))
            else -> return DistantSplit.Stop(InheritanceResult.Unsupported(UnsupportedReason.ASK_DISTANT_BRANCHES, InheritanceLaw.SA, listOf(via.label)))
        }
    }

    // المسألة بالورثة اللي اتنزّلوا مكانهم (فروض · عصبة · حجب · رد · عول — نفس المحرك)
    val hypothetical = byVia.keys.associateWith { branchesByVia[it]?.size ?: 1 }
    val tanzil = when (val o = computeShares(InheritanceLaw.SA, hypothetical, false)) {
        is SharesOutcome.Stop -> return DistantSplit.Stop(o.result)
        is SharesOutcome.Shares -> o
    }

    val persons = LinkedHashMap<HeirKind, MutableList<Frac>>()
    fun add(k: HeirKind, share: Frac, n: Int) = repeat(n) { persons.getOrPut(k) { mutableListOf() } += share }
    for ((via, kinds) in byVia) {
        val viaShare = tanzil.shares[via] ?: Frac.ZERO
        if (!viaShare.isPositive) {
            // 4. «حجباً»: الوارث اللي اتنزّلوا مكانه محجوب (أو ما فضلش له حاجة) ⇒ ما بيورثوش
            val by = tanzil.notes.firstOrNull { it.kind == InheritanceNoteKind.BLOCKED && it.heirs.firstOrNull() == via }?.heirs?.getOrNull(1)
            val byRelative = by?.let { byVia[it]?.firstOrNull() }
            for (k in kinds) {
                excluded += k
                if (byRelative != null) notes += InheritanceNote(InheritanceNoteKind.BLOCKED, sa("235"), listOf(k, byRelative))
            }
            continue
        }
        for (k in kinds) notes += InheritanceNote(InheritanceNoteKind.DISTANT_TANZIL, sa("235"), listOf(k, via))
        val own = branchesByVia[via]
        if (own != null) {
            val (sonKind, daughterKind) = DISTANT_BRANCH_CHILDREN.getValue(via)
            val perBranch = viaShare / own.size
            for (b in own) {
                val each = perBranch / (b.sons + b.daughters)
                if (sonKind != null) add(sonKind, each, b.sons)
                add(daughterKind, each, b.daughters)
            }
        } else {
            val each = viaShare / kinds.sumOf { distant.getValue(it) }
            for (k in kinds) add(k, each, distant.getValue(k))
        }
    }
    val shares = LinkedHashMap<HeirKind, Frac>()
    val personShares = LinkedHashMap<HeirKind, List<Frac>>()
    for (k in distant.keys) {
        val list = persons[k] ?: List(distant.getValue(k)) { Frac.ZERO }
        personShares[k] = list
        shares[k] = list.sumFrac()
        if (!shares.getValue(k).isPositive) excluded += k
    }
    return DistantSplit.Done(shares, personShares, excluded, notes)
}
