package app.masroufy.core

/**
 * تصنيف «المستحقات» وفروعه — قرار المالك 2026-10-01 (OVERRIDES §56): «أي حاجة بتتصرف تظهر في المصروف الشهري تحت تصنيف
 * خاص بيه و كلهم تبع المستحقات». العملية اللي بتتربط بجمعية أو بخطة أقساط بتاخد الفرع بتاعها لوحدها.
 * المعرّفات ثابتة عشان الجهازين يلاقوا نفس التصنيف (والإنشاء «لو مش موجود» بس — اللي المستخدم غيّره ما يتكتبش فوقه).
 * ⚠️ الأيقونة والألوان مؤقتة لحد تصميم المالك (§55).
 */
object DuesCategories {
    const val ROOT = "cat-dues"
    const val ROSCAS = "cat-dues-roscas"
    const val FINANCING = "cat-dues-financing"
    const val PURCHASE_PLANS = "cat-dues-purchase-plans"

    /** بالترتيب: الأب الأول (عشان الفرع ما يتحفظش قبل أبوه). */
    fun defaults(): List<Category> = listOf(
        Category(ROOT, null, "المستحقات", "calendar-clock", "#1f7a6b", "#8cd9cc", true, 900),
        Category(ROSCAS, ROOT, "جمعيات", "users-round", "#1f7a6b", "#8cd9cc", true, 901),
        Category(FINANCING, ROOT, "تمويل", "landmark", "#1f7a6b", "#8cd9cc", true, 902),
        Category(PURCHASE_PLANS, ROOT, "تقسيط مشتريات", "shopping-bag", "#1f7a6b", "#8cd9cc", true, 903),
    )

    fun forInstallment(kind: InstallmentKind): Id = if (kind == InstallmentKind.FINANCING) FINANCING else PURCHASE_PLANS

    /**
     * مصروف «المستحقات» في الفترة — بنفس قواعد `computePeriodTotals` (نصيب المستخدم، والمستبعد من الميزانية مش هنا).
     * بيتشال من **حد الميزانية بس** لما صاحب الحساب يختار كده (`UserProfile.duesInBudget` — §56).
     */
    fun spendMinor(transactions: List<Transaction>, allocations: List<PersonAllocation>, duesIds: Set<Id>): Halalas {
        if (duesIds.isEmpty()) return 0
        var total = 0L
        for (t in transactions) {
            if (t.categoryId !in duesIds || t.excludedFromBudget || countsAsIncome(t.economicKind)) continue
            total = if (reducesExpense(t.economicKind)) subtractMoney(total, t.amountMinor) else addMoney(total, personalShareOf(t, allocations))
        }
        return total
    }

    /** «المستحقات» وكل اللي تحتها (حتى لو المستخدم ضاف فروع بنفسه). */
    fun idsIn(categories: List<Category>): Set<Id> {
        val ids = mutableSetOf(ROOT)
        var grew = true
        while (grew) {
            grew = false
            for (c in categories) if (c.parentId in ids && ids.add(c.id)) grew = true
        }
        return ids
    }
}
