package app.masroufy.usecase

import app.masroufy.core.Merchant
import app.masroufy.port.MerchantCategoryRepository
import app.masroufy.port.MerchantRepository

/**
 * التجار في مساحة **غير السعودية** (اختيار Claude §64): التاجر نفسه مشترك على مستوى الحساب (§41)، وتصنيفه المؤكد بيختلف بين البلدين.
 * - القراية: التاجر المشترك + تصنيفه **في البلد دي بس** (`merchantCategories`). مفيش تصنيف هنا ⇒ مالوش تصنيف مؤكد (تصنيف السعودية
 *   ما بيعدّيش — معرّفه ممكن ما يبقاش موجود في شجرة البلد التانية).
 * - الحفظ: الاسم والأسماء البديلة بيروحوا للتاجر المشترك **بتصنيف السعودية زي ما هو** (تاجر جديد ⇒ من غير تصنيف)، والتصنيف بيروح للبلد.
 *   تاجر ما اتغيرش فيه غير التصنيف ⇒ **ولا كتابة** على المستند المشترك.
 * مساحة السعودية بتستعمل مستودع التجار العادي زي ما هو.
 */
class SpaceMerchantRepository(private val shared: MerchantRepository, private val local: MerchantCategoryRepository) : MerchantRepository {
    override suspend fun listAll(): List<Merchant> {
        val categories = local.listAll()
        return shared.listAll().map { it.copy(verifiedCategoryId = categories[it.id]) }
    }

    override suspend fun findByNormalizedName(normalizedName: String): Merchant? =
        shared.findByNormalizedName(normalizedName)?.let { m -> m.copy(verifiedCategoryId = local.listAll()[m.id]) }

    override suspend fun saveMany(merchants: List<Merchant>) {
        if (merchants.isEmpty()) return
        val existing = shared.listAll().associateBy { it.id }
        val identity = merchants.mapNotNull { m ->
            val before = existing[m.id]
            val kept = m.copy(verifiedCategoryId = before?.verifiedCategoryId)
            kept.takeIf { it != before }
        }
        shared.saveMany(identity)
        val categories = local.listAll()
        for (m in merchants) if (categories[m.id] != m.verifiedCategoryId) local.set(m.id, m.verifiedCategoryId)
    }
}
