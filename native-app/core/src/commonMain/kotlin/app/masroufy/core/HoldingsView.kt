package app.masroufy.core

/**
 * «حاجاتي» (مواصفة r05 §1.2 · §2.6) — شكل القسم من [HoldingsSnapshot]: الإجمالي · شريط الأغراض بنسب صحيحة مجموعها 100 · مربع لكل
 * مجموعة. دوال نقية بأعداد صحيحة بس (CLAUDE.md #1)، والشاشة بتعرض اللي هنا من غير أي حساب.
 */
data class PurposeShare(val purpose: HoldingPurpose, val valueMinor: Halalas, val percent: Int)

/**
 * مربع مجموعة: [valueMinor] مجموع المعروف من حاجاته (بعد الفلتر) — null لو كلها «غير متاح» (مش صفر). [names] أسامي الحاجات بالترتيب ·
 * [purposes] الأغراض اللي عليها (بدون تكرار، بترتيب ظهورها).
 */
data class GroupTile(val group: HoldingGroup, val valueMinor: Halalas?, val count: Int, val names: List<String>, val purposes: List<HoldingPurpose>)

/**
 * [totalMinor] = مجموع المعروف من غير الشخصي؛ null ⇒ «غير متاح» (فيه حاجات غير شخصية وكلها من غير قيمة). [unknownCount] غير الشخصي
 * من غير قيمة (برّه الإجمالي). [empty] = مفيش ولا حاجة.
 */
data class HoldingsViewModel(
    val totalMinor: Halalas?,
    val strip: List<PurposeShare>,
    val tiles: List<GroupTile>,
    val goldPriceMissing: Boolean,
    val empty: Boolean,
    val unknownCount: Int,
)

/** ترتيب المربعات الثابت (النموذج): معادن · عقار · أسهم · شخصي · مقتنيات · أخرى. */
val HOLDING_GROUP_ORDER: List<HoldingGroup> = HoldingGroup.entries

/** [filter] = الغرض اللي اتضغط عليه (null = الكل): المربعات بتعرض الحاجات اللي عليها الغرض ده بس، والشريط والإجمالي ثابتين. */
fun holdingsView(snapshot: HoldingsSnapshot, filter: HoldingPurpose?): HoldingsViewModel {
    val items = snapshot.items
    val byPurpose = LinkedHashMap<HoldingPurpose, Halalas>()
    var total = 0L
    var investableKnown = 0
    var unknown = 0
    for (item in items) {
        val v = item.valueMinor
        if (v == null) {
            if (item.purpose != HoldingPurpose.PERSONAL) unknown++
            continue
        }
        byPurpose[item.purpose] = addMoney(byPurpose[item.purpose] ?: 0L, v)
        if (item.purpose != HoldingPurpose.PERSONAL) { total = addMoney(total, v); investableKnown++ }
    }
    // الشريط بترتيب الأغراض الثابت، واللي قيمته صفر ما بيظهرش (زي النموذج)
    val present = HoldingPurpose.entries.filter { (byPurpose[it] ?: 0L) > 0L }
    val percents = purposePercents(present.map { byPurpose.getValue(it) })
    val strip = present.mapIndexed { i, p -> PurposeShare(p, byPurpose.getValue(p), percents[i]) }
    val shown = if (filter == null) items else items.filter { it.purpose == filter }
    val tiles = HOLDING_GROUP_ORDER.mapNotNull { group ->
        val list = shown.filter { it.group == group }
        if (list.isEmpty()) return@mapNotNull null
        val known = list.mapNotNull { it.valueMinor }
        GroupTile(
            group = group,
            valueMinor = if (known.isEmpty()) null else addMoney(*known.toLongArray()),
            count = list.size,
            names = list.map { it.asset.name },
            purposes = list.map { it.purpose }.distinct(),
        )
    }
    val totalOrNull = if (investableKnown == 0 && unknown > 0) null else total
    return HoldingsViewModel(totalOrNull, strip, tiles, snapshot.goldPriceMissing, items.isEmpty(), unknown)
}

/**
 * نسب صحيحة مجموعها 100 بطريقة «أكبر باقي» (النموذج سطر 95-96): كل نسبة = ⌊v·100 ÷ الكل⌋، والنقط الباقية للأكبر في الباقي
 * (v·100 mod الكل) — بالتساوي الأول في الترتيب ياخد. القيم الموجبة بس؛ الكل صفر ⇒ أصفار.
 */
fun purposePercents(values: List<Halalas>): List<Int> {
    val all = values.fold(0L) { a, v -> addMoney(a, v) }
    if (all <= 0L) return values.map { 0 }
    val base = values.map { multiplyMoneyByInt(it, 100) / all }.toMutableList()
    var rest = 100 - base.sum().toInt()
    val order = values.indices.sortedWith { a, b ->
        val ra = multiplyMoneyByInt(values[a], 100) % all
        val rb = multiplyMoneyByInt(values[b], 100) % all
        if (ra != rb) rb.compareTo(ra) else a.compareTo(b)
    }
    for (i in order) {
        if (rest <= 0) break
        base[i] = base[i] + 1
        rest--
    }
    return base.map { it.toInt() }
}

/** المتوسط الرسمي اللي بيكبّر المجموعة في صفحة النوع: المعادن ⇐ الدهب · العقار ⇐ الغلاء · الأسهم ⇐ الأسهم · غيرهم مفيش. */
fun groupBucket(group: HoldingGroup): GrowthBucket = when (group) {
    HoldingGroup.METAL -> GrowthBucket.GOLD
    HoldingGroup.PROPERTY -> GrowthBucket.PROPERTY
    HoldingGroup.SHARES -> GrowthBucket.SHARES
    else -> GrowthBucket.NONE
}

/** خطّين صفحة النوع لكل سنة من 0 لـ[years]، بفلوس النهارده: [leave] «لو سبتها» · [plan] «لو زوّدت كل شهر». */
data class GroupGrowth(val leave: List<Halalas>, val plan: List<Halalas>)

/**
 * نقل `grow()` من النموذج: كل شهر a += a·m و b += b·m + [addMinor] (m = المعدل الشهري المكافئ لـ[rateBp] بمقياس 10^8)، وكل سنة
 * بيتخزن `deflate(القيمة، 12·سنة، [cpiBp])`. معدل صفر وتضخم صفر ⇒ خط مستقيم بالظبط.
 */
fun growHoldingGroup(startMinor: Halalas, addMinor: Halalas, rateBp: Int, cpiBp: Int, years: Int): GroupGrowth {
    require(years in 0..MAX_CALC_MONTHS / 12)
    require(addMinor >= 0L)
    val m = monthlyRateFromAnnual(rateBp)
    var a = startMinor
    var b = startMinor
    val leave = ArrayList<Halalas>(years + 1)
    val plan = ArrayList<Halalas>(years + 1)
    for (t in 0..years * 12) {
        if (t % 12 == 0) {
            leave += deflate(a, t, cpiBp)
            plan += deflate(b, t, cpiBp)
        }
        if (t == years * 12) break
        a = addMoney(a, mulDivWide(a, m, MONTHLY_RATE_SCALE))
        b = addMoney(b, mulDivWide(b, m, MONTHLY_RATE_SCALE), addMinor)
    }
    return GroupGrowth(leave, plan)
}
