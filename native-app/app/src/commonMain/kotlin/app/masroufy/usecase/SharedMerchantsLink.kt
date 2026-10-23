package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ProblemCode
import app.masroufy.core.Transaction
import app.masroufy.problems.Problems
import kotlin.coroutines.cancellation.CancellationException

/**
 * توصيل قاعدة التجار المشتركة (PLAN-0.6 «القاعدة المشتركة للمحلات» · OVERRIDES §25 · §25.1 · §80) — رد المالك 2026-10-22:
 * - [ShareMerchantCategory]: لما المالك يأكد تصنيف **شراء** (تفاصيل العملية · لوحة التصنيف · تأكيد رسالة البنك) اسم المحل وتصنيفه بيترفعوا
 *   **مش مؤكدين** — برّه خيط الواجهة ومن غير ما حد يستناه، والفشل بيتسجل في «سجل المشاكل» بكود. الحماية كلها جوه [SharedMerchants.contribute]
 *   (`shareableName` · أسامي الأشخاص وأطراف التحويل · مفيش مبالغ ولا تواريخ ولا رقم مستخدم).
 * - [SyncSharedMerchants]: التغييرات من آخر مؤشر (فتح البرنامج · كل دورة خلفية · زرار «حدّث الآن»)، وفشلها بيتسجل.
 */
class ShareMerchantCategory(
    private val shared: SharedMerchants,
    /** بيشغّل الرفع **من غير ما يستنى** (على الجوال: نطاق في الخلفية). الاختبار بيدّي تشغيل مباشر. */
    private val launch: (suspend () -> Unit) -> Unit,
) {
    /** نفس شكل `ReviewSmsInboxDeps.contribute` و`MerchantMemory.contribute` — بيرجع على طول. */
    val hook: suspend (MerchantContribution, Id) -> Unit = { c, categoryId -> send(c, categoryId) }

    /** نفس شكل `EditTransactionDeps.onCategoryConfirmed`: الشراء الصادر بس (`MerchantPick.shareable`) — التحويل ما بيوصلش أصلًا. */
    val onCategoryConfirmed: suspend (Transaction, Id) -> Unit = { t, categoryId ->
        val pick = MerchantPick.of(t, categoryId)
        val name = pick.rawMerchantName
        if (pick.shareable && name != null) send(MerchantContribution(t.economicKind, t.observedDirection, name), categoryId)
    }

    fun send(c: MerchantContribution, categoryId: Id) = launch {
        try {
            shared.contribute(c.economicKind, c.observedDirection, c.rawMerchantName, categoryId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Problems.report(ProblemCode.SHARED_MERCHANTS_CONTRIBUTE_FAILED, e)
        }
    }
}

/** مؤشرات المزامنة على الجهاز (لكل حساب): التغييرات · آخر مراجعة لكل المؤكدين · آخر مزامنة نجحت (للعرض). */
data class SharedMerchantCursors(
    val changes: app.masroufy.port.SyncCursorPort,
    val confirmed: app.masroufy.port.SyncCursorPort,
    val lastSync: app.masroufy.port.SyncCursorPort,
)

class SyncSharedMerchants(private val shared: SharedMerchants) {
    /** null = فشلت (واتسجلت في «سجل المشاكل»). */
    suspend fun run(): SharedSyncResult? = try {
        shared.sync()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Problems.report(ProblemCode.SHARED_MERCHANTS_SYNC_FAILED, e)
        null
    }

    suspend fun lastSyncAt(): String? = shared.lastSyncAt()
}
