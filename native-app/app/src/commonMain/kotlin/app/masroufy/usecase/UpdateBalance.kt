package app.masroufy.usecase

import app.masroufy.core.BalanceCheckpoint
import app.masroufy.core.Category
import app.masroufy.core.CheckpointEffect
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Liquidity
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.ReviewState
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.balanceAtCheckpoint
import app.masroufy.core.checkpointEffects
import app.masroufy.core.dayIn
import app.masroufy.core.dayMonth
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.ruleFor
import app.masroufy.core.subtractMoney
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/** الرقم المحسوب قبل التسجيل والفرق عن المكتوب (لسه ما اتكتبش حاجة). [differenceMinor] null لو لسه ما اتكتبش رقم. */
data class BalancePreview(val wallet: Wallet, val day: IsoDate, val now: Boolean, val computedMinor: Halalas, val differenceMinor: Halalas?)

/** جزء من «فرق مش معروف» المالك عارف راح فين (أو جه منين): المبلغ موجب، والنوع بيحدد الاتجاه (الصرف للنقص · الوارد للزيادة). */
data class SplitPart(val amountMinor: Halalas, val economicKind: EconomicKind, val categoryId: Id?)

/** تحديثات رصيد محفظة: أثر كل نقطة **الأحدث الأول** + الأجزاء اللي اتفصلت من كل نقطة. */
data class WalletCheckpoints(val wallet: Wallet, val effects: List<CheckpointEffect>, val parts: Map<Id, List<Transaction>>)

class BalanceError(message: String) : IllegalArgumentException(message)

/**
 * «حدّث الرصيد» / «حدّث الكاش» (قايمة المالك 2026-10-15 البنود 4 و5 — القاعدة كلها في `core/BalanceCheckpoint.kt`): المالك بيكتب اللي معاه فعلًا
 * لأي محفظة (دلوقتي، أو آخر يوم فات — مش المستقبل، بيوم البلد [timeZone]) ⇒ **نقطة واحدة** في المحفظة والفرق «فرق مش معروف».
 * بعدها: يوزّع الفرق على تصنيفات حقيقية ([split] ⇒ عمليات عادية) أو يسيبه ([keep]) أو يتراجع ([undo] — الرصيد بيرجع المحسوب).
 * ولا عملية بتتمسح من غير ما المالك يطلب (الجزء اللي رجّعه للفرق بس — [removePart]). الفرق مش مصروف ولا دخل. المبالغ هللات صحيحة.
 */
class UpdateBalance(
    private val wallets: WalletRepository,
    private val txns: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val timeZone: String = SAUDI_TIME_ZONE,
) {
    /** النهارده في البلد. */
    fun today(): IsoDate = clock.nowIso().let { dayIn(timeZone, it) ?: it.take(10) }

    /** التصنيفات (لاختيار تصنيف الجزء). */
    suspend fun categories(): List<Category> = categoryRepo.listAll()

    /** المحسوب في اللحظة دي ([day] null أو النهارده = دلوقتي) والفرق عن [statedMinor] — من غير كتابة. */
    suspend fun preview(walletId: Id, statedMinor: Halalas?, day: IsoDate? = null): BalancePreview {
        val wallet = walletOf(walletId)
        val probe = probeOf(wallet, statedMinor ?: 0L, day)
        val computed = balanceAtCheckpoint(wallet, ledgerOf(wallet, probe.day), probe)!!
        return BalancePreview(wallet, probe.day, probe.atInstant != null, computed, statedMinor?.let { subtractMoney(it, computed) })
    }

    /** سجّل الرصيد اللي المالك كتبه ⇒ أثر النقطة الجديدة (فرقها وقت التسجيل). */
    suspend fun record(walletId: Id, statedMinor: Halalas, day: IsoDate? = null): CheckpointEffect {
        val wallet = walletOf(walletId)
        val probe = probeOf(wallet, statedMinor, day)
        val rows = ledgerOf(wallet, probe.day)
        val computed = balanceAtCheckpoint(wallet, rows, probe)!!
        val cp = probe.copy(recordedDiffMinor = subtractMoney(statedMinor, computed))
        val saved = wallet.copy(checkpoints = wallet.checkpoints + cp)
        wallets.save(saved)
        return checkpointEffects(saved, rows).first { it.checkpoint.id == cp.id }
    }

    /** تحديثات المحفظة بأثرها دلوقتي (الأحدث الأول) والأجزاء اللي اتفصلت منها. */
    suspend fun history(walletId: Id): WalletCheckpoints {
        val wallet = walletOf(walletId)
        val rows = ledgerOf(wallet, null)
        val cpIds = wallet.checkpoints.map { it.id }.toSet()
        val parts = rows.filter { t -> t.explainsCheckpointId?.let { it in cpIds } == true && t.walletId == wallet.id }.groupBy { it.explainsCheckpointId!! }
        return WalletCheckpoints(wallet, checkpointEffects(wallet, rows).reversed(), parts)
    }

    /** «فروق مش معروفة» في كل محافظ البلد اللي محتاجة مراجعة (الأحدث الأول). */
    suspend fun openDifferences(): List<CheckpointEffect> =
        wallets.listAll().filter { it.checkpoints.isNotEmpty() }.flatMap { w -> checkpointEffects(w, ledgerOf(w, null)) }
            .filter { it.needsReview }.sortedWith(compareByDescending<CheckpointEffect> { it.checkpoint.day }.thenByDescending { it.checkpoint.createdAt })

    /** «سيبه زي ما هو»: الفرق بقيمته دلوقتي اتراجع — لو اتغيّر بعدين بيرجع للمراجعة. */
    suspend fun keep(walletId: Id, checkpointId: Id) {
        val wallet = walletOf(walletId)
        val effect = checkpointEffects(wallet, ledgerOf(wallet, null)).firstOrNull { it.checkpoint.id == checkpointId } ?: throw gone()
        wallets.save(wallet.copy(checkpoints = wallet.checkpoints.map { if (it.id == checkpointId) it.copy(keptMinor = effect.adjustmentMinor) else it }))
    }

    /**
     * تراجع عن تحديث: النقطة بتتشال والرصيد بيرجع المحسوب. الأجزاء اللي اتفصلت منها **بتفضل عمليات عادية** (المالك قال إنها حصلت) — الربط
     * بس بيتشال. بيرجّع عدد الأجزاء دي.
     */
    suspend fun undo(walletId: Id, checkpointId: Id): Int {
        val wallet = walletOf(walletId)
        if (wallet.checkpoints.none { it.id == checkpointId }) throw gone()
        val parts = ledgerOf(wallet, null).filter { it.explainsCheckpointId == checkpointId }
        if (parts.isNotEmpty()) txns.saveMany(parts.map { it.copy(explainsCheckpointId = null, updatedAt = clock.nowIso()) })
        wallets.save(wallet.copy(checkpoints = wallet.checkpoints.filter { it.id != checkpointId }))
        return parts.size
    }

    /**
     * جزء من الفرق ⇒ عملية عادية على نفس المحفظة ويوم النقطة، مربوطة بيها (بتشرح الفرق ومش بتغيّر الرصيد). النقص بيتوزّع على صرف بس، والزيادة على
     * وارد بس، ومن غير ما الأجزاء تعدّي الفرق اللي فاضل.
     */
    suspend fun split(walletId: Id, checkpointId: Id, part: SplitPart): Transaction {
        val wallet = walletOf(walletId)
        val effect = checkpointEffects(wallet, ledgerOf(wallet, null)).firstOrNull { it.checkpoint.id == checkpointId } ?: throw gone()
        val left = effect.adjustmentMinor
        if (part.amountMinor <= 0 || part.amountMinor > MAX_SAFE_HALALAS) throw BalanceError(uiText(UiKey.BAL_ERR_AMOUNT))
        if (part.economicKind == EconomicKind.UNCLASSIFIED || part.economicKind == EconomicKind.INTERNAL_TRANSFER) throw BalanceError(uiText(UiKey.BAL_ERR_SPLIT_SIDE))
        val direction = if (ruleFor(part.economicKind).liquidity == Liquidity.IN) Direction.IN else Direction.OUT
        // النقص (سالب) بيتشرح بصرف، والزيادة (موجب) بوارد
        if (left == 0L || (left < 0) != (direction == Direction.OUT)) throw BalanceError(uiText(UiKey.BAL_ERR_SPLIT_SIDE))
        if (part.amountMinor > if (left < 0) -left else left) throw BalanceError(uiText(UiKey.BAL_ERR_SPLIT_TOO_BIG))
        if (part.categoryId != null && categoryRepo.listAll().none { it.id == part.categoryId }) throw BalanceError(uiText(UiKey.BAL_ERR_SPLIT_SIDE))
        val now = clock.nowIso()
        val t = Transaction(
            id = ids.next("txn"), occurredAt = effect.checkpoint.day, datePrecision = "day", sourceOrder = 9_000_000,
            economicKind = part.economicKind, economicKindConfirmed = true, observedDirection = direction, amountMinor = part.amountMinor,
            currency = wallet.currency, categoryConfirmed = part.categoryId != null, excludedFromBudget = false,
            reviewState = if (part.categoryId != null) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW, isCashTagged = wallet.kind == CASH,
            createdAt = now, updatedAt = now, categoryId = part.categoryId, walletId = wallet.id,
            rawMerchantName = uiText(UiKey.BAL_PART_NAME), explainsCheckpointId = checkpointId,
        )
        txns.saveMany(listOf(t))
        return t
    }

    /** «رجّعه للفرق»: الجزء اللي اتفصل بيتمسح (ده بس — أي عملية تانية بترفض) والفرق بيكبر تاني. */
    suspend fun removePart(transactionId: Id) {
        val t = txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw gone()
        if (t.explainsCheckpointId == null) throw gone()
        // مراجعة r04: الجزء ممكن يبقى طرف في «عمليات تابعة» ⇒ الربط بيتفك الأول (ولا مصروف بيفضل منقوص بداخلة مش موجودة)
        unlinkBeforeDelete(txns, clock, listOf(t.id))
        txns.deleteMany(listOf(t.id))
    }

    private suspend fun walletOf(id: Id): Wallet = wallets.findById(id) ?: throw BalanceError(uiText(UiKey.BAL_ERR_GONE))

    private fun gone() = BalanceError(uiText(UiKey.BAL_ERR_CP_GONE))

    /** نقطة تجريبية بعد التأكد: دلوقتي (النهارده) بساعة التسجيل، ويوم فات = آخر اليوم. */
    private fun probeOf(wallet: Wallet, stated: Halalas, day: IsoDate?): BalanceCheckpoint {
        if (stated < 0 || stated > MAX_SAFE_HALALAS) throw BalanceError(uiText(UiKey.BAL_ERR_AMOUNT))
        val today = today()
        val at = day ?: today
        if (!isValidIsoDate(at) || at > today) throw BalanceError(uiText(UiKey.BAL_ERR_DATE))
        if (at < wallet.openingAt) throw BalanceError(uiText(UiKey.BAL_ERR_BEFORE_OPENING, dayMonth(wallet.openingAt) + " " + wallet.openingAt.take(4)))
        val now = clock.nowIso()
        return BalanceCheckpoint(ids.next("checkpoint"), at, if (at == today) now else null, stated, 0L, now)
    }

    /** كل عمليات الفترة من بداية المحفظة لحد النهارده (أو آخر نقطة لو بعده) — زي أي حساب رصيد. */
    private suspend fun ledgerOf(wallet: Wallet, day: IsoDate?): List<Transaction> {
        val until = listOfNotNull(today(), day, wallet.checkpoints.maxOfOrNull { it.day }).max()
        return if (wallet.openingAt > until) emptyList() else txns.listByDateRange(wallet.openingAt, until)
    }
}

private const val CASH = "cash"
