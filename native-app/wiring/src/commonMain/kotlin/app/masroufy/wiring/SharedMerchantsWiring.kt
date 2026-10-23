package app.masroufy.wiring

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Space
import app.masroufy.core.countryPack
import app.masroufy.seed.BundledSeeds
import app.masroufy.usecase.ShareMerchantCategory
import app.masroufy.usecase.SharedMerchants
import app.masroufy.usecase.SharedMerchantsDeps
import app.masroufy.usecase.SyncSharedMerchants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * قاعدة التجار المشتركة في التجميع (PLAN-0.6 «القاعدة المشتركة للمحلات» — رد المالك 2026-10-22).
 * - **البلد الافتراضية (السعودية) بس** ([DEFAULT_SPACE_ID]): القاعدة بمعرّفات شجرة تصنيفاتها، والتجار على مستوى الحساب أصلًا. بلد تانية ⇒ ولا رفع
 *   (معرّفات شجرتها مش نفس المعرّفات) — والاقتراح بيوصلها من غير كتابة عن طريق `SpaceMerchantRepository` لو التصنيف موجود في شجرتها.
 * - من غير كتالوج ([SpaceRepositories.sharedMerchants]) أو مؤشرات على الجهاز ([DeviceEnv.sharedCursors]) ⇒ null (الاختبار · الآيفون لحد ما
 *   يتجمّع) والشاشة بتقول «غير متاح».
 * - الأساس من المرجع جوه التطبيق (§25.1) فاضي هنا: نسخة كوتلن مفيهاش مرجع تجار مضمّن (`BundledSeeds` بتزرع من غير تجار).
 * - الأسامي اللي بتتمنع (§80): «الأشخاص» وأطراف التحويل **في البلد اللي المالك أكد فيها**.
 */
fun sharedMerchantsOf(space: Space, r: SpaceRepositories, env: DeviceEnv): SharedMerchants? {
    if (space.id != DEFAULT_SPACE_ID) return null
    val catalog = r.sharedMerchants ?: return null
    val cursors = env.sharedCursors ?: return null
    return SharedMerchants(
        SharedMerchantsDeps(
            catalog = catalog, merchants = r.merchants, baseline = emptyList(),
            treeCategoryIds = BundledSeeds.categoryIds(countryPack(space.countryCode)),
            cursor = cursors.changes, confirmedCursor = cursors.confirmed, clock = env.clock,
            people = r.people, transferParties = r.transferParties, lastSync = cursors.lastSync,
        ),
    )
}

/** الرفع برّه خيط الواجهة ومن غير ما حد يستناه — نطاق مستقل (`SupervisorJob`: فشل رفع ما بيلغيش التاني). */
fun shareMerchantOf(space: Space, r: SpaceRepositories, env: DeviceEnv): ShareMerchantCategory? {
    val shared = sharedMerchantsOf(space, r, env) ?: return null
    val scope = CoroutineScope(SupervisorJob() + env.work)
    return ShareMerchantCategory(shared) { block -> scope.launch { block() } }
}

fun shareMerchantOf(c: AreaContext): ShareMerchantCategory? = shareMerchantOf(c.space, c.repos, c.env)

/** المزامنة دايمًا على البلد الافتراضية (التجار على مستوى الحساب) — من أي بلد شغالة. */
fun syncSharedMerchantsOf(spaces: List<Pair<Space, SpaceRepositories>>, env: DeviceEnv): SyncSharedMerchants? =
    spaces.firstOrNull { it.first.id == DEFAULT_SPACE_ID }?.let { (space, r) -> sharedMerchantsOf(space, r, env) }?.let(::SyncSharedMerchants)
