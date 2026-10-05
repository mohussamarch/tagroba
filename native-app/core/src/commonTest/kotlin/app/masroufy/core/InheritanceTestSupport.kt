package app.masroufy.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * أدوات اختبارات حاسبة الورث (OVERRIDES §69). كل الأسماء والأرقام مخترعة.
 * الأنصبة بتتكتب كسور نص ("1/6") — نصيب **النوع كله** من نصيب الورثة.
 */
internal fun inheritanceCase(
    country: String,
    vararg heirs: Pair<HeirKind, Int>,
    estate: Halalas = 7_200_000,
    items: List<EstateItem>? = null,
    funeral: Halalas = 0,
    debts: Halalas = 0,
    bequest: Bequest? = null,
    predeceased: List<PredeceasedChild> = emptyList(),
    distantRelatives: Boolean? = null,
    special: Set<SpecialCircumstance> = emptySet(),
) = InheritanceCase(
    countryCode = country,
    heirs = heirs.toMap(),
    items = items ?: listOf(EstateItem("شقة", estate)),
    funeralMinor = funeral,
    debtsMinor = debts,
    bequest = bequest,
    predeceasedChildren = predeceased,
    distantRelatives = distantRelatives,
    special = special,
)

internal fun computed(case: InheritanceCase): InheritanceResult.Computed {
    val r = calculateInheritance(case)
    if (r !is InheritanceResult.Computed) fail("متوقع حساب، طلع: $r")
    assertExact(r)
    return r
}

/** الأنصبة بالظبط (كل نوع مكتوب لازم يتذكر — المحجوب "0"). */
internal fun assertShares(r: InheritanceResult.Computed, vararg expected: Pair<HeirKind, String>) {
    val actual = r.heirs.associate { it.kind to it.share.toString() }
    assertEquals(expected.toMap(), actual, "الأنصبة")
}

internal fun InheritanceResult.Computed.noteKinds(): List<InheritanceNoteKind> = notes.map { it.kind }

internal fun InheritanceResult.Computed.hasNote(kind: InheritanceNoteKind, article: String? = null): Boolean =
    notes.any { it.kind == kind && (article == null || it.citation?.article == article) }

internal fun InheritanceResult.Computed.amounts(kind: HeirKind): List<Halalas> = heir(kind)?.amountsMinor ?: emptyList()

/**
 * الثوابت اللي لازم تتحقق في أي نتيجة: مجموع الأنصبة 1 (لو فيه ورثة) · مجموع مبالغ الورثة = نصيبهم · التركة = كل الأجزاء ·
 * كل حاجة مجموع قسمتها = قيمتها · مجموع كل مستحق على الحاجات = مبلغه · مفيش سالب · كل مبلغ في حدود هللة من الكسر بالظبط.
 */
internal fun assertExact(r: InheritanceResult.Computed) {
    val shareSum = r.heirs.map { it.share }.sumFrac()
    if (r.heirs.any { it.share.isPositive }) assertEquals(Frac.ONE, shareSum, "مجموع الأنصبة")
    for (h in r.heirs) {
        assertEquals(h.share, h.perPerson * h.count, "نصيب الفرد × العدد")
        assertEquals(h.count, h.amountsMinor.size)
        for (a in h.amountsMinor) {
            assertTrue(a >= 0, "مبلغ سالب")
            val (q, _) = mulDivRem(r.heirsMinor, h.perPerson.num, h.perPerson.den)
            assertTrue(a == q || a == q + 1, "${h.kind}: $a بعيد عن ${h.perPerson} × ${r.heirsMinor}")
        }
    }
    assertEquals(r.heirsMinor, r.heirs.sumOf { it.totalMinor }, "مجموع مبالغ الورثة")
    val wajiba = r.wajiba.sumOf { it.totalMinor }
    assertEquals(r.wajibaMinor, wajiba)
    for (w in r.wajiba) assertEquals(w.totalMinor, w.sonsMinor.sum() + w.daughtersMinor.sum(), "قسمة الواجبة على الأحفاد")
    val unpaid = r.grossMinor - (r.funeralMinor + r.debtsMinor + r.wajibaMinor + r.bequestMinor + r.heirsMinor)
    assertEquals(0L, unpaid, "التركة = التجهيز + الديون + الواجبة + الوصية + الورثة")
    assertTrue(r.heirsMinor >= 0 && r.bequestMinor >= 0 && r.wajibaMinor >= 0)
    for (item in r.items) {
        assertEquals(item.valueMinor, item.parts.sumOf { it.amountMinor }, "قسمة «${item.name}»")
        assertTrue(item.parts.all { it.amountMinor > 0 })
    }
    fun column(c: Claimant) = r.items.sumOf { it.of(c) }
    assertEquals(r.funeralMinor, column(Claimant.Funeral))
    assertEquals(r.debtsMinor, column(Claimant.Debts))
    assertEquals(r.bequestMinor, column(Claimant.Bequest))
    for (h in r.heirs) h.amountsMinor.forEachIndexed { i, a -> assertEquals(a, column(Claimant.Heir(h.kind, i)), "${h.kind} #$i على الحاجات") }
    r.wajiba.forEachIndexed { j, w ->
        w.sonsMinor.forEachIndexed { i, a -> assertEquals(a, column(Claimant.Wajiba(j, true, i))) }
        w.daughtersMinor.forEachIndexed { i, a -> assertEquals(a, column(Claimant.Wajiba(j, false, i))) }
    }
}
