package app.masroufy.core

/**
 * تصنيف «رسوم بنكية» (§77-B — قرار المالك 2026-10-09): رسوم الحوالة والمحفظة اللي في رسالة البنك بتتسجل عملية لوحدها تحته.
 * **لو مش موجود بس** (زي «المستحقات» — `DuesCategories.kt`): المعرّف الثابت [ID] (عشان الجهازين يلاقوا نفس التصنيف) ⇒ وإلا تصنيف
 * المالك اللي اسمه «رسوم بنكية» (الظاهر الأول) ⇒ وإلا بيتعمل جديد. اللي المالك غيّره ما يتكتبش فوقه.
 * الاسم **بيانات** (بيتخزن في حساب المستخدم، والتطبيق القديم وقواعد الاقتراح بيطابقوا بيه — `SuggestEconomicKind`) مش نص واجهة.
 * ⚠️ الأيقونة والألوان مؤقتة لحد تصميم المالك (§55) — اختيار Claude.
 */
object BankFeeCategory {
    const val ID = "cat-bank-fees"
    const val NAME = "رسوم بنكية"

    /**
     * الحقول اللي التطبيق القديم بيطلبها في التصنيف كلها موجودة (`checkFullBackup.ts`: name · iconKey · lightColor · darkColor · active ·
     * order) و`parentId` = null (أساسي).
     */
    fun default(): Category = Category(ID, null, NAME, "landmark", "#6b5b2e", "#d9c88c", true, 910)

    /** معرّف التصنيف الموجود للرسوم، أو null لو لازم يتعمل. */
    fun existingId(categories: List<Category>): Id? {
        categories.firstOrNull { it.id == ID }?.let { return it.id }
        val named = categories.filter { sameCategoryName(it.name, NAME) }
        return (named.firstOrNull { it.active } ?: named.firstOrNull())?.id
    }
}
