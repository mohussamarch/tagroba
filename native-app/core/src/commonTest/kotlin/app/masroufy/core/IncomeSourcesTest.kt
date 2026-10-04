package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * مصادر الدخل — المكان بس (OVERRIDES §48). مفيش ملف مرجع: مش موجودة في التطبيق الحالي.
 */
class IncomeSourcesTest {
    private fun source(id: String, name: String, from: String, to: String? = null) = IncomeSource(
        id = id,
        name = name,
        normalizedName = normalizeText(name),
        kind = IncomeSourceKind.JOB,
        currency = Currency.SAR,
        startedAt = from,
        endedAt = to,
    )

    @Test
    fun `الشغل القديم ما بيتشالش — بيتقفل بتاريخ`() {
        val all = listOf(
            source("s1", "الشركة الأولى", "2024-01-01", "2026-03-31"),
            source("s2", "الشركة التانية", "2026-04-01"),
        )
        // في مارس كان لسه في الأولى
        assertEquals(listOf("s1"), sourcesActiveOn(all, "2026-03-15").map { it.id })
        // وفي مايو بقى في التانية بس
        assertEquals(listOf("s2"), sourcesActiveOn(all, "2026-05-15").map { it.id })
        // يوم التسليم نفسه الاتنين شغالين — الحدود شاملة
        assertEquals(listOf("s1"), sourcesActiveOn(all, "2026-03-31").map { it.id })
        // وقبل ما يشتغل أصلًا: مفيش
        assertTrue(sourcesActiveOn(all, "2023-12-31").isEmpty())
    }

    @Test
    fun `أكتر من مصدر في نفس الوقت — شغل وبارت تايم`() {
        val all = listOf(source("s1", "الشركة", "2026-01-01"), source("s2", "عميل", "2026-02-01"))
        assertEquals(listOf("s1", "s2"), sourcesActiveOn(all, "2026-06-01").map { it.id })
    }

    @Test
    fun `الاسم ما يتكررش والتواريخ لازم تكون منطقية`() {
        val existing = listOf(source("s1", "الشركة", "2026-01-01"))
        assertFailsWith<IncomeSourceError> { checkIncomeSource("الشركة", "2026-02-01", null, null, null, existing) }
        assertFailsWith<IncomeSourceError> { checkIncomeSource("", "2026-02-01", null, null, null, emptyList()) }
        assertFailsWith<IncomeSourceError> { checkIncomeSource("شركة", "2026-13-01", null, null, null, emptyList()) }
        // النهاية قبل البداية
        assertFailsWith<IncomeSourceError> { checkIncomeSource("شركة", "2026-05-01", "2026-04-01", null, null, emptyList()) }
        assertFailsWith<IncomeSourceError> { checkIncomeSource("شركة", "2026-05-01", null, 32, null, emptyList()) }
        assertFailsWith<IncomeSourceError> { checkIncomeSource("شركة", "2026-05-01", null, null, 0, emptyList()) }
        // وتعديل نفس المصدر باسمه ما يعتبرش تكرار
        val checked = checkIncomeSource("الشركة", "2026-01-01", null, null, null, existing, selfId = "s1")
        assertEquals("الشركة", checked.name)
    }

    @Test
    fun sameNameIsAllowedOnlyForPeriodsThatDoNotOverlap() {
        // رد المالك §64: الرجوع لشركة قديمة = فترة جديدة بنفس الاسم
        val closed = listOf(source("s1", "الشركة", "2024-01-01").copy(endedAt = "2025-06-30"))
        assertEquals("الشركة", checkIncomeSource("الشركة", "2025-07-01", null, null, null, closed).name)
        assertFailsWith<IncomeSourceError>("بتبدأ يوم القفل") { checkIncomeSource("الشركة", "2025-06-30", null, null, null, closed) }
        assertFailsWith<IncomeSourceError>("فترة قديمة جواها") { checkIncomeSource("الشركة", "2023-01-01", "2024-02-01", null, null, closed) }
        assertEquals("الشركة", checkIncomeSource("الشركة", "2023-01-01", "2023-12-31", null, null, closed).name, "فترة قبلها خالص")
        assertTrue(periodsOverlap("2024-01-01", null, "2030-01-01", "2030-02-01"))
        assertFalse(periodsOverlap("2024-01-01", "2024-12-31", "2025-01-01", null))
    }

    @Test
    fun rejoiningOnTheEndDayStartsTheNextDay() {
        // رد المالك §64-٨: الرجوع لنفس الشركة يوم الخروج ⇒ الفترة الجديدة بتبدأ تاني يوم لوحدها
        val closed = listOf(source("s1", "الشركة", "2024-01-01").copy(endedAt = "2025-06-30"))
        assertEquals("2025-07-01", rejoinStart("  الشركة ", "2025-06-30", closed))
        assertEquals("الشركة", checkIncomeSource("الشركة", rejoinStart("الشركة", "2025-06-30", closed), null, null, null, closed).name)
        assertEquals("2025-06-30", rejoinStart("شركة تانية", "2025-06-30", closed), "اسم تاني ⇒ زي ما هو")
        assertEquals("2025-06-29", rejoinStart("الشركة", "2025-06-29", closed), "قبل الخروج ⇒ زي ما هو (والتداخل بيترفض في الفحص)")
        assertEquals("2026-01-01", rejoinStart("الشركة", "2026-01-01", closed))
        assertEquals("2024-12-31", rejoinStart("الشركة", "2024-12-31", listOf(source("s2", "الشركة", "2024-01-01").copy(endedAt = "2024-12-30"))))
        assertEquals("2025-01-01", rejoinStart("الشركة", "2024-12-31", listOf(source("s2", "الشركة", "2024-01-01").copy(endedAt = "2024-12-31"))), "آخر السنة ⇒ أول السنة الجاية")
    }

    @Test
    fun weeklyPayTakesAWeekdayAndAJobMayBeWeeklyToo() {
        val ok = checkIncomeSource("محل", "2026-01-01", null, null, null, emptyList(), kind = IncomeSourceKind.PART_TIME, payFrequency = PayFrequency.WEEKLY, payWeekday = 4)
        assertEquals("محل", ok.name)
        assertFailsWith<IncomeSourceError> { checkIncomeSource("محل", "2026-01-01", null, null, null, emptyList(), kind = IncomeSourceKind.PART_TIME, payFrequency = PayFrequency.WEEKLY, payWeekday = 0) }
        assertFailsWith<IncomeSourceError> { checkIncomeSource("محل", "2026-01-01", null, 5, null, emptyList(), kind = IncomeSourceKind.PART_TIME, payFrequency = PayFrequency.WEEKLY, payWeekday = 4) }
        assertFailsWith<IncomeSourceError> { checkIncomeSource("محل", "2026-01-01", null, null, null, emptyList(), kind = IncomeSourceKind.PART_TIME, payWeekday = 4) }
        // رد المالك §64-٤: الوظيفة ينفع تكون بقبض أسبوعي — بيوم في الأسبوع ومن غير يوم في الشهر
        assertEquals("شركة", checkIncomeSource("شركة", "2026-01-01", null, null, null, emptyList(), kind = IncomeSourceKind.JOB, payFrequency = PayFrequency.WEEKLY, payWeekday = 4).name)
        assertFailsWith<IncomeSourceError> { checkIncomeSource("شركة", "2026-01-01", null, 28, null, emptyList(), kind = IncomeSourceKind.JOB, payFrequency = PayFrequency.WEEKLY, payWeekday = 4) }
        assertEquals(IncomeSourceKind.PENSION, IncomeSourceKind.fromWire("pension"))
        assertEquals(PayFrequency.MONTHLY, PayFrequency.fromWire(null), "القديم من غير الحقل = شهري")
        assertEquals(PayFrequency.MONTHLY, source("s", "x", "2026-01-01").payFrequency)
    }

    @Test
    fun `تركيز الدخل — نسبة أكبر مصدر`() {
        // 920 من 1000 = ٩٢٪
        assertEquals(920, topSourceShareTenthPercent(mapOf("s1" to 920_000, "s2" to 80_000)))
        assertEquals(1000, topSourceShareTenthPercent(mapOf("s1" to 500_000)))
        // مفيش دخل ⇒ مفيش نسبة، مش صفر
        assertEquals(null, topSourceShareTenthPercent(emptyMap()))
        assertEquals(null, topSourceShareTenthPercent(mapOf("s1" to 0)))
    }

    @Test
    fun `النوع المش معروف بيبقى أخرى مش استثناء`() {
        assertEquals(IncomeSourceKind.OTHER, IncomeSourceKind.fromWire(null))
        assertEquals(IncomeSourceKind.OTHER, IncomeSourceKind.fromWire("حاجة جديدة"))
        assertEquals(IncomeSourceKind.PART_TIME, IncomeSourceKind.fromWire("part_time"))
    }
}
