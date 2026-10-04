package app.masroufy.port

import app.masroufy.core.CountryPack
import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.Transaction

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

/** أزواج التحويل لنفسك بين بلدين (على مستوى الحساب — §64). */
interface SpaceTransferRepository {
    suspend fun listAll(): List<SpaceTransfer>
}

/** عملية في بلد بعينها — الرجل في الزوج. */
data class SpaceLegWrite(val spaceId: String, val transaction: Transaction)

/**
 * كتابة الزوج **ذرّيًا**: الزوج والرجلين مع بعض أو ولا حاجة (فايربيز: دفعة كتابة واحدة على البلدين والحساب).
 * العمليات بتتحفظ كاملة (merge) في بلد كل رجل.
 */
interface SpaceTransferWriter {
    suspend fun link(pair: SpaceTransfer, legs: List<SpaceLegWrite>)

    /** الزوج بيتمسح والرجلين اللي لسه موجودين بيرجعوا «لسه ما اتحددش» — **العمليات نفسها ما بتتمسحش**. */
    suspend fun unlink(pair: SpaceTransfer, legs: List<SpaceLegWrite>)
}

/**
 * رجول التحويل لنفسك **في بلد واحدة** — للي بيربط عملية بحاجة تانية (المستحقات · الزكاة · النقوط · تغيير النوع) يرفض الرجل،
 * وللتراجع عن دفعة استيراد يفك الزوج قبل ما يمسح العملية.
 */
interface SpaceTransferLegs {
    suspend fun pairsOf(transactionIds: List<Id>): List<SpaceTransfer>

    /** اللي منهم رجل في زوج (في البلد دي). */
    suspend fun legsAmong(transactionIds: List<Id>): Set<Id>

    suspend fun isLeg(transactionId: Id): Boolean = transactionId in legsAmong(listOf(transactionId))

    /** فك الأزواج دي (الرجل في البلد التانية بترجع «لسه ما اتحددش»). */
    suspend fun detach(pairs: List<SpaceTransfer>)
}

/** المراجع الأولية لبلد (الشجرة من ملفات التطبيق + فروق البلد) — التطبيق بيقراها من موارده؛ الاختبار بيدّيها جاهزة. */
fun interface SpaceSeedSourceLoader {
    suspend fun load(pack: CountryPack): SeedSource
}
