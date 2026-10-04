package app.masroufy.port

import app.masroufy.core.CountryPack
import app.masroufy.core.Id
import app.masroufy.core.Space

/**
 * «حساب لكل بلد» (OVERRIDES §41 · §64). المستودعات بتتعمل **مربوطة بمساحة**، فحالات الاستخدام العادية ما تعرفش عن البلاد حاجة؛
 * الواجهات دي للي بيدير البلاد نفسها بس.
 */

/** سجل المساحات (على مستوى الحساب). مساحة السعودية مش فيه — بتتبني في الكود. **مفيش مسح** — أرشفة بس. */
interface SpaceRegistry {
    suspend fun listAll(): List<Space>

    /** بيكتب المساحة **لو مش موجودة بس** — جهازين بيعملوا نفس البلد مع بعض ما يدهسوش بعض. `true` = اتكتبت. */
    suspend fun addIfMissing(space: Space): Boolean

    suspend fun save(space: Space)
}

/** المساحة الشغالة على **الجهاز ده** (مش بيانات حساب، ومش بتتزامن) — لكل حساب مفتاح لوحده. */
interface ActiveSpaceStore {
    fun read(): String?

    fun write(spaceId: String)
}

/** تصنيف التاجر المشترك **جوه مساحة** غير السعودية (`merchantCategories`) — اختيار Claude §64. */
interface MerchantCategoryRepository {
    /** معرّف التاجر ⇐ التصنيف في البلد دي. */
    suspend fun listAll(): Map<Id, Id>

    /** `categoryId` = null ⇒ يتشال (التاجر مالوش تصنيف مؤكد في البلد دي). */
    suspend fun set(merchantId: Id, categoryId: Id?)
}

/** اللي تجهيز بلد جديدة بيكتب فيه: تصنيفات وقواعد المساحة الجديدة + علامة تجهيزها. */
data class SpaceSeedTargets(val categories: CategoryRepository, val rules: RuleRepository, val seed: ReferenceSeedPort)

/** المراجع الأولية لبلد (الشجرة من ملفات التطبيق + فروق البلد) — التطبيق بيقراها من موارده؛ الاختبار بيدّيها جاهزة. */
fun interface SpaceSeedSourceLoader {
    suspend fun load(pack: CountryPack): SeedSource
}
