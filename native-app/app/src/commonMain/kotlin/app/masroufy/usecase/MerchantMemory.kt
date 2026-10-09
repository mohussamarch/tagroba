package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.Merchant
import app.masroufy.core.Transaction
import app.masroufy.core.isTransferLike
import app.masroufy.core.jsTrim
import app.masroufy.core.rememberMerchant
import app.masroufy.port.IdGenerator
import app.masroufy.port.MerchantRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * «نفتكره؟» اتلغى — قرار المالك §75-16 (بيغيّر §36): التصنيف اللي المالك **يختاره بإيده** لمحل بيتحفظ للمحل **لوحده ومن غير سؤال**
 * (`verifiedCategoryId` — حقل التطبيق القديم نفسه)، والعملية الجاية من نفس المحل بتاخده مؤكد («التاجر المؤكد» في `categorize`).
 * - **آخر اختيار يكسب**، وبيأثر على **الجاي بس** — العمليات القديمة ما بتتصنفش من جديد (قراءة Claude الحرفية لـ«يفتكر لوحده دايمًا»).
 * - الاسم الفاضي أو الأرقام/النجوم بس ⇒ ما بيتحفظش (`rememberMerchant`)، ولا «بلا اسم» بتاع العملية اليدوية ([MANUAL_NO_NAME]).
 * - بلد غير السعودية: [merchants] هو `SpaceMerchantRepository` ⇒ التصنيف بيتحفظ في البلد دي بس (§64) والتاجر المشترك ما بيتلمسش.
 * - **القايمة المشتركة (§25) زي ما كانت:** الصادر بس، وفشلها ما بيوقفش الحفظ. زيادة (اختيار Claude): التحويل ما بيترفعش — اسم
 *   الشخص اللي حولتله ما يطلعش برّه حسابك — ولا اللي نوعه المؤكد مش شراء.
 */
data class MerchantPick(
    val rawMerchantName: String?,
    val categoryId: Id,
    /** يترفع للقايمة المشتركة؟ (الصادر بس — [of] بيحسبها). */
    val shareable: Boolean = false,
) {
    companion object {
        /** اختيار المالك لتصنيف [t]. */
        fun of(t: Transaction, categoryId: Id): MerchantPick = MerchantPick(
            t.rawMerchantName, categoryId,
            shareable = t.observedDirection == Direction.OUT && !isTransferLike(t) &&
                (!t.economicKindConfirmed || t.economicKind == EconomicKind.PURCHASE),
        )
    }
}

/**
 * الاسم اللي `AddTransaction` بيخزنه للعملية اليدوية اللي من غير اسم — **بيانات متخزنة، مش نص واجهة** (ما بيتترجمش). مش اسم محل ⇒
 * ما بيتفتكرش (كان هيطلع «محل» اسمه كده في قايمة التجار).
 */
internal const val MANUAL_NO_NAME = "بلا اسم"

class MerchantMemory(
    private val merchants: MerchantRepository,
    private val ids: IdGenerator,
    /** الرفع للقايمة المشتركة — نفس شكل `ReviewSmsInboxDeps.contribute`. null = ما فيش رفع. */
    private val contribute: (suspend (MerchantContribution, Id) -> Unit)? = null,
) {
    /** اختيار واحد. بيرجّع true لو تصنيف المحل اتحفظ أو اتغيّر. [share] = false لو الرفع بيتعمل في مكان تاني (`EditTransaction`). */
    suspend fun remember(pick: MerchantPick, share: Boolean = true): Boolean = rememberAll(listOf(pick), share) > 0

    /** كذا اختيار مرة واحدة (بالترتيب — **الأخير يكسب** لنفس المحل). بيرجّع عدد المحلات اللي اتغيّرت. */
    suspend fun rememberAll(picks: List<MerchantPick>, share: Boolean = true): Int {
        val usable = picks.filter { !it.rawMerchantName.isNullOrBlank() && jsTrim(it.rawMerchantName) != MANUAL_NO_NAME }
        if (usable.isEmpty()) return 0
        val all = merchants.listAll().toMutableList()
        val changed = LinkedHashMap<Id, Merchant>()
        val shared = LinkedHashMap<Id, MerchantPick>()
        for (pick in usable) {
            val merchant = rememberMerchant(all, pick.rawMerchantName!!, pick.categoryId, ids.next("merchant")) ?: continue
            val at = all.indexOfFirst { it.id == merchant.id }
            if (at >= 0 && all[at] == merchant) continue
            if (at >= 0) all[at] = merchant else all += merchant
            changed[merchant.id] = merchant
            if (pick.shareable) shared[merchant.id] = pick else shared.remove(merchant.id)
        }
        if (changed.isEmpty()) return 0
        merchants.saveMany(changed.values.toList())
        val send = contribute
        if (share && send != null) {
            for (pick in shared.values) {
                try {
                    send(MerchantContribution(EconomicKind.PURCHASE, Direction.OUT, pick.rawMerchantName!!), pick.categoryId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // الرفع اختياري — المحل اتحفظ خلاص
                }
            }
        }
        return changed.size
    }
}
