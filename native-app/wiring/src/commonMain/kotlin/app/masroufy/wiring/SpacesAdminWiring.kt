package app.masroufy.wiring

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Space
import app.masroufy.core.SpaceArchived
import app.masroufy.port.ActiveSpaceStore
import app.masroufy.port.Clock
import app.masroufy.port.SpaceRegistry
import app.masroufy.port.SpaceSeedSourceLoader
import app.masroufy.port.SpaceSeedTargets
import app.masroufy.seed.BundledSeeds
import app.masroufy.ui.screens.more.SpacesAdmin
import app.masroufy.usecase.ManageSpaces
import app.masroufy.usecase.ManageSpacesDeps
import kotlinx.coroutines.withTimeoutOrNull

/** اللي الجلسة بتدّيه لإدارة البلاد ([SessionLinks.spaceAdmin]) — واجهات بس: فايربيز على الجوال (`androidApp`) والذاكرة في الاختبار. */
class SpaceAdminLinks(
    /** سجل البلاد على مستوى الحساب — **بذاكرة الجلسة** (اللي بيتكتب يبان في القايمة لحظتها). */
    val registry: SpaceRegistry,
    /** البلد الشغالة على الجهاز — **نفس مخزن الجلسة** (أرشفة الشغالة بترجّع الجهاز للسعودية). */
    val active: ActiveSpaceStore,
    /** تصنيفات وقواعد البلد الجديدة وعلامة تجهيزها في مكانها (`users/{uid}/spaces/{id}` على الجوال). */
    val targets: (Space) -> SpaceSeedTargets,
    /** الجلسة بتفتح البلاد الجديدة وتقفل المؤرشفة (`AccountSession.refreshSpaces`) — من غير إعادة تشغيل. */
    val reopen: suspend () -> Unit,
)

/** أقصى انتظار لإنشاء/أرشفة/رجوع (الكتابة + فتح البلد في الجلسة). من غير نت كتابة فايربيز بتستنى للأبد ⇒ الشاشة كانت هتعلّق. */
const val SPACE_ADMIN_TIMEOUT_MS: Long = 60_000

/** المهلة خلصت (غالبًا مفيش نت) — الشاشة بتقول «اتأكد من النت وجرّب تاني»، والمحاولة التانية بتكمّل الناقص من غير تكرار. */
class SpaceAdminTimeout : IllegalStateException("space admin timed out")

/**
 * «البلدان» و«أول تشغيل» ⇒ `ManageSpaces` بشجرة التصنيفات **المضمّنة في التطبيق** ([BundledSeeds] — شجرة السعودية + فروق البلد
 * `buildCountryCategoryTree`، من غير نت ولا ملفات على الجهاز). بعد كل إنشاء أو أرشفة أو رجوع ⇒ [SpaceAdminLinks.reopen] ⇒ البلد
 * الجديدة بتتفتح في **نفس الجلسة** والتبديل لها شغال على طول (`ShellDeps.switchSpace`)، والمؤرشفة بتتقفل.
 * - إنشاء اتكتب في السجل والجلسة ما لحقتش تفتحه (النت قطع بعد الكتابة) ⇒ «أضف» تاني **بيفتحه بس** بدل «عندك حساب مصر قبل كده».
 * - التجهيز نفسه ما بيتكررش (علامة التجهيز في `ManageSpaces.create`)، والتجار ما بيتزرعوش (مشتركين على مستوى الحساب).
 * **مفيش قفل اشتراك** (§41.1).
 */
class SessionSpacesAdmin(
    private val links: SpaceAdminLinks,
    clock: Clock,
    seeds: SpaceSeedSourceLoader = BundledSeeds,
    private val timeoutMs: Long = SPACE_ADMIN_TIMEOUT_MS,
) : SpacesAdmin {
    private val manage = ManageSpaces(ManageSpacesDeps(links.registry, links.active, clock, links.targets, seeds))

    override suspend fun create(countryCode: String): Space = step {
        // مراجعة 0.3: البلد في الأرشيف ⇒ «رجّعها من الأرشيف» (`SpaceArchived` — زرار الرجوع على كارتها) بدل «عندك حساب … قبل كده»
        manage.list(includeArchived = true).firstOrNull { it.archived && it.id != DEFAULT_SPACE_ID && it.countryCode.equals(countryCode, true) }
            ?.let { throw SpaceArchived(it) }
        manage.list().firstOrNull { it.id != DEFAULT_SPACE_ID && it.countryCode.equals(countryCode, true) } ?: manage.create(countryCode)
    }

    override suspend fun archive(spaceId: String): Space = step { manage.archive(spaceId) }

    override suspend fun unarchive(spaceId: String): Space = step { manage.unarchive(spaceId) }

    override suspend fun archived(): List<Space> = manage.list(includeArchived = true).filter { it.archived }

    private suspend fun step(block: suspend () -> Space): Space =
        withTimeoutOrNull(timeoutMs) { block().also { links.reopen() } } ?: throw SpaceAdminTimeout()
}
