package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuplicatePair
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.addMoney
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney

/**
 * «صلّح رصيدي» خطوة بخطوة في تفاصيل محفظة البنك (طلب 2026-10-16 — المالك ما عرفش إن أدوات 0.4 لازم تتشغل بالترتيب): **نفس الأدوات** على شاشة واحدة،
 * ومفيش ولا حساب فلوس جديد غير جمع أثر كل سطر للتقدير:
 * (١) معاينة «صلّح رسايل البنك القديمة» (`RepairOldSms.previewAll`) للسطور اللي بتلمس المحفظة دي · (٢) المكرر المحتمل («افهم رصيدك» ⇒
 * `MergeDuplicates.merge`) · (٣) «كم معك في البنك دلوقتي؟» (`UpdateBalance.record`) · (٤) قبل ⇒ بعد و«فرق مش معروف».
 * [apply] = «طبّق» واحدة بنفس ترتيب تشغيل الأدوات واحدة واحدة (التصليح بنسخته المتأكدة ⇒ الدمج بنسخة كل زوج ⇒ النقطة)، و[undo] = «تراجع» واحد
 * للكل بالعكس. ولا حاجة بتتكتب من غير الضغطة. كل المبالغ هللات صحيحة.
 */
data class FixMyBalanceDeps(
    val repair: RepairOldSms?,
    val merge: MergeDuplicates?,
    val explain: ExplainBalance,
    val balance: UpdateBalance,
    /** اسم المحفظة اللي السطر رايح لها (العرض بس). null ⇒ من غير اسم. */
    val wallets: app.masroufy.port.WalletRepository? = null,
)

/** سطر في الخطوة ١ أو ٢: [key] للاختيار · [effectMinor] أثره على رصيد المحفظة دي (بإشارته). */
data class FixLine(val key: Id, val date: String, val amountMinor: Halalas, val effectMinor: Halalas, val toWalletId: Id? = null, val toWalletName: String? = null)

/** أثر السطور اللي لسه متعلّمة (العدّ في الشاشة). */
fun linesEffect(lines: List<FixLine>, skipped: Set<Id>): Halalas = sumMoney(lines.filter { it.key !in skipped }.map { it.effectMinor })

data class FixPreview(
    val walletId: Id,
    val currency: Currency,
    val beforeMinor: Halalas,
    val moves: List<FixLine>,
    val unrecorded: List<FixLine>,
    val pairs: List<FixLine>,
)

/** اللي المالك سايبه متعلّم (السطر المتخطّي مش هنا). [statedMinor] null = ما كتبش رصيد. */
data class FixChoice(val moves: Set<Id>, val unrecorded: Set<Id>, val pairs: Set<Id>, val statedMinor: Halalas?)

/** قبل ⇒ بعد (تقدير قبل التطبيق) و«فرق مش معروف» (null لو ما كتبش رصيد). */
data class FixSummary(val beforeMinor: Halalas, val afterMinor: Halalas, val unknownMinor: Halalas?)

/** «تراجع» واحد للكل. */
class FixUndo internal constructor(val walletId: Id, internal val repair: SmsRepairUndo?, internal val tickets: List<MergeTicket>, internal val checkpointId: Id?)

data class FixResult(val undo: FixUndo, val repaired: Int, val merged: Int, val refused: Int, val beforeMinor: Halalas, val afterMinor: Halalas, val unknownMinor: Halalas?)

/** مفتاح زوج المكرر: «رسالة|كشف». */
fun pairKey(p: DuplicatePair): Id = p.sms.transactionId + "|" + p.statement.transactionId

/** التقدير: قبل + أثر المختار بس، والفرق = المكتوب − بعد. */
fun fixSummary(p: FixPreview, c: FixChoice): FixSummary {
    val picked = p.moves.filter { it.key in c.moves } + p.unrecorded.filter { it.key in c.unrecorded } + p.pairs.filter { it.key in c.pairs }
    val after = addMoney(p.beforeMinor, sumMoney(picked.map { it.effectMinor }))
    return FixSummary(p.beforeMinor, after, c.statedMinor?.let { subtractMoney(it, after) })
}

private fun signed(direction: Direction, amount: Halalas): Halalas = if (direction == Direction.OUT) -amount else amount

class FixMyBalance(private val deps: FixMyBalanceDeps) {
    suspend fun preview(walletId: Id, today: String): FixPreview {
        val before = deps.balance.preview(walletId, null)
        val repair = deps.repair?.previewAll()
        val names = deps.wallets?.listAll().orEmpty().associate { it.id to it.name }
        val moves = repair?.moves.orEmpty().filter { it.oldWalletId == walletId || it.newWalletId == walletId }.map { l ->
            val old = if (l.oldWalletId == walletId) signed(l.oldDirection, l.amountMinor) else 0L
            val new = if (l.newWalletId == walletId) signed(l.newDirection, l.amountMinor) else 0L
            FixLine(l.transactionId, l.date, l.amountMinor, subtractMoney(new, old), l.newWalletId.takeIf { it != walletId }, names[l.newWalletId])
        }
        val unrecorded = repair?.unrecorded.orEmpty().filter { it.walletId == walletId }
            .map { FixLine(it.recordId, it.date, it.amountMinor, signed(it.direction, it.amountMinor)) }
        // الزوج بعد الدمج = عملية واحدة باتجاه الكشف ⇒ أثره = شيل نسخة الرسالة
        val pairs = if (deps.merge == null) emptyList() else deps.explain.explain(walletId, today)?.review?.duplicates.orEmpty()
            .map { p -> FixLine(pairKey(p), p.sms.date, p.sms.amountMinor, -signed(p.sms.direction, p.sms.amountMinor)) }
        return FixPreview(walletId, before.wallet.currency, before.computedMinor, moves, unrecorded, pairs)
    }

    /** «طبّق»: نفس ترتيب الأدوات واحدة واحدة. مفيش حاجة مختارة ولا رصيد ⇒ null. */
    suspend fun apply(p: FixPreview, c: FixChoice): FixResult? {
        val moves = c.moves.filter { k -> p.moves.any { it.key == k } }.toSet()
        val fresh = c.unrecorded.filter { k -> p.unrecorded.any { it.key == k } }.toSet()
        val pairs = p.pairs.filter { it.key in c.pairs }
        if (moves.isEmpty() && fresh.isEmpty() && pairs.isEmpty() && c.statedMinor == null) return null
        val repaired = if (moves.isEmpty() && fresh.isEmpty()) null else deps.repair?.applyAll(moves, fresh)
        val tickets = mutableListOf<MergeTicket>()
        var refused = 0
        for (line in pairs) {
            val (sms, statement) = line.key.split('|').let { it[0] to it[1] }
            when (val r = runCatching { deps.merge?.merge(sms, statement) }.getOrNull()) {
                is MergeResult.Done -> tickets += r.ticket
                else -> refused++
            }
        }
        // «بعد» = بعد التصليح والدمج وقبل النقطة (النقطة نفسها بتخلي الرصيد = المكتوب، والفرق هو «فرق مش معروف»)
        val after = deps.balance.preview(p.walletId, null).computedMinor
        val cp = c.statedMinor?.let { deps.balance.record(p.walletId, it) }
        return FixResult(
            FixUndo(p.walletId, repaired?.undo, tickets.toList(), cp?.checkpoint?.id),
            repaired?.undo?.count ?: 0, tickets.size, refused, p.beforeMinor, after, cp?.adjustmentMinor,
        )
    }

    /** «تراجع عن الكل» بالعكس: النقطة ⇒ الدمج (الأحدث الأول) ⇒ التصليح. بيرجّع عدد اللي ما رجعش (اتغير من ساعتها). */
    suspend fun undo(u: FixUndo): Int {
        var failed = 0
        u.checkpointId?.let { id -> if (runCatching { deps.balance.undo(u.walletId, id) }.isFailure) failed++ }
        for (t in u.tickets.asReversed()) {
            if (runCatching { deps.merge?.undo(t) }.getOrNull() !is UndoResult.Done) failed++
        }
        u.repair?.let { r -> if (runCatching { deps.repair?.undo(r) }.isFailure) failed++ }
        return failed
    }
}
