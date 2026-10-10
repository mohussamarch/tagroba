package app.masroufy.usecase

import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistNote
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.CardState
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.ObligationKind
import app.masroufy.core.ScreenLink
import app.masroufy.core.SplitDraft
import app.masroufy.core.SplitShare
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.shiftDays
import app.masroufy.core.parseSplit
import app.masroufy.core.similarSameDay
import app.masroufy.core.splitShares

/** [splitReply] بـ«سجّل جديدة» (مش «قسّم الموجودة»). */
internal const val NEW_SPLIT = "__new__"

/**
 * «قسّم فاتورة العشا ٣٦٠ مع أحمد وسارة» (التصميم `AssistSplit`): أنصبة بأعداد صحيحة والباقي على نصيبك. اسم مش في الأشخاص ⇒ «أضيفه؟»؛
 * عملية بنفس المبلغ النهارده ⇒ «أقسّمها هي؟»؛ «قسّم آخر عملية» ⇒ آخر مصروف (٣٠ يوم). [existingChoice] = اختيار سابق.
 */
internal suspend fun TurnKit.splitReply(u: AssistUnderstanding, existingChoice: String?): TurnOut {
    val s = u.signals
    if (u.note == AssistNote.TWO_AMOUNTS) return TurnOut(listOf(text(uiText(TextKey.ASSIST_TWO_AMOUNTS), u.wire)), countTopic = false)
    if (s.money.isForeign(ctx.currency)) return TurnOut(listOf(text(uiText(TextKey.ASSIST_FOREIGN, ctx.space.name), u.wire)), countTopic = false)
    val p = parseSplit(s)
    p.newNames.firstOrNull()?.takeIf { deps.people != null }?.let { name ->
        val ask = bot(AssistMessageKind.CHOICE, uiText(TextKey.ASSIST_ADD_PERSON, quoted(name)), u.wire).copy(
            options = listOf(AssistOption(OPT_YES, uiText(TextKey.ASSIST_OPT_ADD)), AssistOption(OPT_NO, uiText(TextKey.ASSIST_OPT_NO))),
            choice = AssistChoiceKind.ADD_PERSON, state = CardState.PENDING, payload = mapOf("text" to s.raw, "name" to name),
        )
        return TurnOut(listOf(ask), countTopic = false)
    }
    val recent = deps.sources.txns.listByDateRange(shiftDays(ctx.today, -30), ctx.today).filter { it.observedDirection == Direction.OUT }
    val amount = s.money.amounts.firstOrNull()?.minor
    var existing = existingChoice?.takeIf { it != NEW_SPLIT }?.let { id -> recent.firstOrNull { it.id == id } }
    if (amount == null && p.lastTransaction) existing = recent.maxWithOrNull(compareBy({ it.occurredAt }, { it.sourceOrder }))
    val total = amount ?: existing?.amountMinor ?: return needs(u)
    val heads = if (p.known.isNotEmpty()) p.known.size + 1 else p.heads ?: return needs(u)
    if (heads < 2) return needs(u)
    if (existing == null && existingChoice == null && amount != null) {
        similarSameDay(recent, ctx.today, amount, ctx.currency)?.let { same ->
            val ask = bot(AssistMessageKind.CHOICE, uiText(TextKey.ASSIST_SPLIT_EXISTING, same.rawDescription.orEmpty()), u.wire).copy(
                options = listOf(AssistOption(OPT_EXISTING, uiText(TextKey.ASSIST_OPT_SPLIT_EXISTING)), AssistOption(OPT_NEW, uiText(TextKey.ASSIST_OPT_RECORD_NEW))),
                choice = AssistChoiceKind.SPLIT_EXISTING, state = CardState.PENDING, payload = mapOf("text" to s.raw, "transactionId" to same.id),
            )
            return TurnOut(listOf(ask), countTopic = false)
        }
    }
    val amounts = splitShares(total, heads)
    val others = if (p.known.isNotEmpty()) p.known.map { it.id to (lex.people.firstOrNull { x -> x.id == it.id }?.name ?: it.name) }
    else (2..heads).map { null to uiText(TextKey.ASSIST_SPLIT_OTHER, it.toString()) }
    val shares = listOf(SplitShare(null, uiText(TextKey.ASSIST_SPLIT_ME), amounts[0], isMe = true)) +
        others.mapIndexed { i, (id, name) -> SplitShare(id, name, amounts[i + 1]) }
    val cat = s.entity(AssistEntityType.CATEGORY)
    val wallet = existing?.walletId?.let { w -> lex.wallets.firstOrNull { it.id == w } } ?: draftFrom(s, total, ctx.today, null).walletId?.let { w -> lex.wallets.firstOrNull { it.id == w } }
    val draft = SplitDraft(
        title = cat?.name ?: existing?.rawDescription ?: uiText(TextKey.ASSIST_SPLIT_TITLE), totalMinor = total, currency = ctx.currency,
        occurredOn = existing?.occurredAt?.take(10) ?: ctx.today, walletId = wallet?.id, walletName = wallet?.name,
        categoryId = cat?.id, categoryName = cat?.name, shares = shares, existingTransactionId = existing?.id,
    )
    return splitCardOut(draft, u.wire)
}

private fun TurnKit.needs(u: AssistUnderstanding) = TurnOut(listOf(text(uiText(TextKey.ASSIST_SPLIT_NEEDS), u.wire)), countTopic = false)

internal suspend fun TurnKit.splitCardOut(d: SplitDraft, topic: String, updates: List<AssistMessage> = emptyList()): TurnOut {
    if (d.walletId == null && d.existingTransactionId == null) {
        val options = chat.mainWallet(ctx.space.id).askOptions().map { AssistOption(it.id, it.name) }
        return TurnOut(listOf(bot(AssistMessageKind.WALLET_PICK, uiText(TextKey.ASSIST_ASK_MAIN_WALLET), topic).copy(split = d, options = options, state = CardState.PENDING)), updates)
    }
    return TurnOut(listOf(bot(AssistMessageKind.SPLIT_CARD, uiText(TextKey.ASSIST_SPLIT_CARD), topic).copy(split = d, state = CardState.PENDING)), updates)
}

/** المبلغ اتغيّر على كارت التقسيم ⇒ نفس الناس بأنصبة جديدة. */
internal fun reshare(shares: List<SplitShare>, total: Halalas): List<SplitShare> {
    val amounts = splitShares(total, shares.size)
    return shares.mapIndexed { i, sh -> sh.copy(amountMinor = amounts[i]) }
}

/** «احفظ» على التقسيم: العملية (الموجودة أو جديدة بالإجمالي) + نصيب كل شخص «ليك عنده» بالربط العادي. */
internal suspend fun TurnKit.confirmSplit(msg: AssistMessage): TurnOut {
    val d = msg.split ?: return TurnOut()
    val people = deps.people ?: return TurnOut(listOf(text(uiText(TextKey.ASSIST_NA), msg.topic)), countTopic = false)
    val txId = d.existingTransactionId ?: run {
        val add = deps.add ?: return TurnOut(listOf(text(uiText(TextKey.ASSIST_NA), msg.topic)), countTopic = false)
        val walletId = d.walletId ?: return TurnOut()
        add.add(
            NewTransactionInput(
                amountMinor = d.totalMinor, currency = d.currency, occurredAt = d.occurredOn, walletId = walletId,
                economicKind = EconomicKind.PURCHASE, merchantName = d.title, categoryId = d.categoryId,
                isCashTagged = lex.wallets.firstOrNull { it.id == walletId }?.kind == "cash",
            ),
        ).id
    }
    d.shares.filter { !it.isMe && it.personId != null }.forEach { people.linkToPerson(txId, it.personId!!, ObligationKind.RECEIVABLE, it.amountMinor) }
    val each = d.shares.firstOrNull { !it.isMe }?.amountMinor ?: 0
    val done = text(
        uiText(TextKey.ASSIST_SPLIT_DONE, ReplyBuilder(d.currency).money(each, d.currency)), msg.topic,
        listOf(ScreenLink.of(AssistScreen.OPERATION_DETAIL, "transactionId" to txId), ScreenLink.of(AssistScreen.OWED_TO_YOU)),
    )
    return TurnOut(listOf(done), listOf(msg.copy(state = CardState.DONE, split = d.copy(transactionId = txId))), countTopic = false)
}
