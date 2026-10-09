package app.masroufy.core

/**
 * تصنيف «رسوم بنكية» (§77-B — قرار المالك 2026-10-09): رسوم الحوالة والمحفظة اللي في رسالة البنك بتتسجل عملية لوحدها تحته.
 * **لو مش موجود بس** (زي «المستحقات» — `DuesCategories.kt`) بيتعمل بالمعرّف الثابت [ID] (عشان الجهازين يلاقوا نفس التصنيف)؛ الموجود
 * بيتختار بـ[existingId]. اللي المالك غيّره ما يتكتبش فوقه.
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

    /**
     * معرّف التصنيف الموجود للرسوم، أو null لو لازم يتعمل. **الشغّال الأول** (مراجعة S2): المعرّف الثابت لو شغّال ⇒ تصنيف المالك
     * الشغّال بنفس الاسم ⇒ وإلا المخفي (الثابت الأول) — ما بنعملش تصنيف تاني فوق اللي المالك خبّاه.
     */
    fun existingId(categories: List<Category>): Id? {
        val fixed = categories.firstOrNull { it.id == ID }
        val named = categories.filter { it.id != ID && sameCategoryName(it.name, NAME) }
        return (fixed?.takeIf { it.active } ?: named.firstOrNull { it.active } ?: fixed ?: named.firstOrNull())?.id
    }
}
