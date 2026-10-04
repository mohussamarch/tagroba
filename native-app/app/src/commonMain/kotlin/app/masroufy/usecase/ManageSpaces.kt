package app.masroufy.usecase

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Space
import app.masroufy.core.SpaceError
import app.masroufy.core.TextKey
import app.masroufy.core.activeSpaceOf
import app.masroufy.core.allSpaces
import app.masroufy.core.countryPack
import app.masroufy.core.newSpaceFor
import app.masroufy.core.uiText
import app.masroufy.port.ActiveSpaceStore
import app.masroufy.port.Clock
import app.masroufy.port.SpaceRegistry
import app.masroufy.port.SpaceSeedSourceLoader
import app.masroufy.port.SpaceSeedTargets

/**
 * إدارة البلاد (OVERRIDES §41 · §64): القايمة · بلد جديدة · التبديل · الأرشفة. **من غير أي قفل اشتراك** (§41.1 — القفل في آخر المشروع).
 * - **بلد واحدة = حساب واحد** (رد المالك §64-٢)، والسعودية موجودة من الأول ببيانات التطبيق الحالي في مكانها.
 * - البلد الجديدة بتتجهز بتصنيفات وقواعد **حزمتها** (مصر = شجرة السعودية + فروق بسيطة — §64-٣). **التجار ما بيتزرعوش** في البلد —
 *   التاجر مشترك على مستوى الحساب، وتصنيفه في البلد بيتعلم من الاستعمال (`SpaceMerchantRepository`).
 * - الترتيب: التصنيفات الأول وبعدين السجل **آخر حاجة** — لو اتقطع في النص، المحاولة التانية بتكمّل الناقص (علامة التجهيز)
 *   والبلد ما تبانش في القايمة بنص شجرة.
 * - **مفيش مسح** لبلد — أرشفة بس، والأرشيف بيرجع.
 */
data class ManageSpacesDeps(
    val registry: SpaceRegistry,
    val active: ActiveSpaceStore,
    val clock: Clock,
    /** مستودعات المساحة الجديدة اللي التجهيز بيكتب فيها (`users/{uid}/spaces/{id}` في التشغيل). */
    val targets: (Space) -> SpaceSeedTargets,
    val seeds: SpaceSeedSourceLoader,
)

class ManageSpaces(private val deps: ManageSpacesDeps) {
    /** السعودية الأول وبعدها البلاد بترتيب إنشائها. المؤرشفة بتظهر بس لو اتطلبت. */
    suspend fun list(includeArchived: Boolean = false): List<Space> = allSpaces(deps.registry.listAll()).filter { includeArchived || !it.archived }

    suspend fun active(): Space = activeSpaceOf(deps.active.read(), deps.registry.listAll())

    /** التبديل بيغيّر المساحة الشغالة على الجهاز ده بس — مفيش ولا كتابة على البيانات. */
    suspend fun switchTo(spaceId: String): Space {
        val target = find(spaceId)
        if (target.archived) throw SpaceError(uiText(TextKey.SPACE_NOT_FOUND))
        deps.active.write(target.id)
        return target
    }

    suspend fun create(countryCode: String): Space {
        val space = newSpaceFor(countryCode, deps.registry.listAll(), deps.clock.nowIso())
        val source = deps.seeds.load(countryPack(space.countryCode))
        val targets = deps.targets(space)
        SeedUserReferences(targets.categories, targets.rules, targets.seed).seed(source.copy(merchants = emptyList()))
        deps.registry.addIfMissing(space)
        return deps.registry.listAll().first { it.id == space.id }
    }

    /** الأرشفة بتخبي البلد من غير ما تمسح حاجة. لو كانت الشغالة، الجهاز بيرجع للسعودية. */
    suspend fun archive(spaceId: String): Space = setArchived(spaceId, true).also { if (deps.active.read() == spaceId) deps.active.write(DEFAULT_SPACE_ID) }

    suspend fun unarchive(spaceId: String): Space = setArchived(spaceId, false)

    private suspend fun setArchived(spaceId: String, archived: Boolean): Space {
        if (spaceId == DEFAULT_SPACE_ID) throw SpaceError(uiText(TextKey.SPACE_DEFAULT_FIXED))
        val updated = find(spaceId).copy(archived = archived)
        deps.registry.save(updated)
        return updated
    }

    private suspend fun find(spaceId: String): Space =
        allSpaces(deps.registry.listAll()).firstOrNull { it.id == spaceId } ?: throw SpaceError(uiText(TextKey.SPACE_NOT_FOUND))
}
