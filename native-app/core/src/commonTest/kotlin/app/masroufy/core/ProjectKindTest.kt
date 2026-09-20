package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * المشروع شخصي ولا شغل (OVERRIDES §47) — والرقم المهم بيتقلب بينهم.
 * مفيش ملف مرجع: التطبيق الحالي مفيهوش النوع ده.
 */
class ProjectKindTest {
    private fun txn(id: String, kind: EconomicKind, amount: Halalas) = Transaction(
        id = id,
        occurredAt = "2026-09-30",
        datePrecision = "day",
        sourceOrder = 0,
        economicKind = kind,
        economicKindConfirmed = true,
        observedDirection = if (ruleFor(kind).liquidity == Liquidity.OUT) Direction.OUT else Direction.IN,
        amountMinor = amount,
        currency = Currency.SAR,
        categoryConfirmed = false,
        excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED,
        isCashTagged = false,
        createdAt = "x",
        updatedAt = "x",
    )

    @Test
    fun `المشروع القديم اللي مالوش نوع بيبقى شخصي`() {
        assertEquals(ProjectKind.PERSONAL, ProjectKind.fromWire(null))
        assertEquals(ProjectKind.PERSONAL, ProjectKind.fromWire("مش معروف"))
        assertEquals(ProjectKind.WORK, ProjectKind.fromWire("work"))
        // المشروع اللي اتعمل من غير ما يتحدد نوعه بيفضل شخصي
        assertEquals(ProjectKind.PERSONAL, Project("p1", "فرح", "فرح", false, "2026-09-01").kind)
    }

    @Test
    fun `مشروع الشغل بيتقاس بصافيه`() {
        // عقد بارت تايم: جالك 800 وصرفت عليه 300 ⇒ كسبت 500
        val summary = summarizeProject(
            listOf(txn("t1", EconomicKind.FREELANCE, 80_000), txn("t2", EconomicKind.PURCHASE, 30_000)),
            emptyList(),
            emptyMap(),
        )
        assertEquals(80_000, summary.receivedMinor)
        assertEquals(30_000, summary.spentMinor)
        assertEquals(50_000, projectNetMinor(summary))
    }

    @Test
    fun `المشروع الخسران بيطلع سالب مش صفر`() {
        val summary = summarizeProject(
            listOf(txn("t1", EconomicKind.FREELANCE, 20_000), txn("t2", EconomicKind.PURCHASE, 95_000)),
            emptyList(),
            emptyMap(),
        )
        assertEquals(-75_000, projectNetMinor(summary))
    }

    @Test
    fun `الفرح كلّفك كام بعد النقوط`() {
        // مشروع شخصي: نفس الحساب، بس معناه «كلّفك» مش «كسبت»
        val summary = summarizeProject(
            listOf(txn("t1", EconomicKind.PURCHASE, 500_000), txn("t2", EconomicKind.EVENT_GIFT, 180_000)),
            emptyList(),
            emptyMap(),
        )
        assertEquals(-320_000, projectNetMinor(summary))
    }
}
