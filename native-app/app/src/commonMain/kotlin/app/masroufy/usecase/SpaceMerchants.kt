package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.Merchant
import app.masroufy.port.CategoryRepository
import app.masroufy.port.MerchantCategoryRepository
import app.masroufy.port.MerchantRepository

/**
 * التجار في مساحة **غير السعودية** (اختيار Claude §64): التاجر نفسه مشترك على مستوى الحساب (§41)، وتصنيفه المؤكد بيختلف بين البلدين.
 * - القراية: التاجر المشترك + تصنيفه **في البلد دي بس** (`merchantCategories`). مفيش تصنيف هنا ⇒ مالوش تصنيف مؤكد.
 * - **الاقتراح (رد المالك §64-٢):** تاجر مالوش تصنيف في البلد دي وتصنيفه المؤكد في السعودية **موجود وشغال في شجرة البلد دي**
 *   ([spaceCategories]) ⇒ بييجي [Merchant.suggestedCategoryId] — **مقترح والمستخدم يأكد**، مش مؤكد لوحده. التصنيف اللي مش موجود
 *   في شجرة البلد (أو مقفول فيها) ⇒ مفيش اقتراح (ما بنقترحش تصنيف مش هتلاقيه).
 * - الحفظ: الاسم والأسماء البديلة بيروحوا للتاجر المشترك **بتصنيف السعودية زي ما هو** (تاجر جديد ⇒ من غير تصنيف)، والتصنيف بيروح للبلد.
 *   تاجر ما اتغيرش فيه غير التصنيف ⇒ **ولا كتابة** على المستند المشترك. **الاقتراح عمره ما بيتكتب** — لا على المشترك ولا في البلد.
 * مساحة السعودية بتستعمل مستودع التجار العادي زي ما هو.
 */
class SpaceMerchantRepository(
    private val shared: MerchantRepository,
    private val local: MerchantCategoryRepository,
    /** شجرة تصنيفات البلد دي — الاقتراح من تصنيف السعودية بس لو معرّفه موجود فيها. */
    private val spaceCategories: CategoryRepository,
) : MerchantRepository {
    private suspend fun activeCategoryIds(): Set<Id> = spaceCategories.listAll().filter { it.active }.map { it.id }.toSet()

    private fun inSpace(m: Merchant, categories: Map<Id, Id>, known: Set<Id>): Merchant {
        val here = categories[m.id]
        return m.copy(verifiedCategoryId = here, suggestedCategoryId = if (here == null) m.verifiedCategoryId?.takeIf { it in known } else null)
    }

    override suspend fun listAll(): List<Merchant> {
        val categories = local.listAll()
        val known = activeCategoryIds()
        return shared.listAll().map { inSpace(it, categories, known) }
    }

    override suspend fun findByNormalizedName(normalizedName: String): Merchant? =
        shared.findByNormalizedName(normalizedName)?.let { inSpace(it, local.listAll(), activeCategoryIds()) }

    override suspend fun saveMany(merchants: List<Merchant>) {
        if (merchants.isEmpty()) return
        val existing = shared.listAll().associateBy { it.id }
        val identity = merchants.mapNotNull { m ->
            val before = existing[m.id]
            // تصنيف السعودية زي ما هو، والاقتراح بيتشال — عمره ما بيوصل للمستند المشترك
            val kept = m.copy(verifiedCategoryId = before?.verifiedCategoryId, suggestedCategoryId = null)
            kept.takeIf { it != before }
        }
        shared.saveMany(identity)
        val categories = local.listAll()
        for (m in merchants) if (categories[m.id] != m.verifiedCategoryId) local.set(m.id, m.verifiedCategoryId)
    }
}
