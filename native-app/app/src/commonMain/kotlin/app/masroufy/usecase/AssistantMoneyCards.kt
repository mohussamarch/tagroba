package app.masroufy.usecase

import app.masroufy.core.AskKey
import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistNote
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.AssistVariants
import app.masroufy.core.CardState
import app.masroufy.core.CardType
import app.masroufy.core.CashMove
import app.masroufy.core.EconomicKind
import app.masroufy.core.LoanSide
import app.masroufy.core.ObligationKind
import app.masroufy.core.ScreenLink
import app.masroufy.core.TextKey
import app.masroufy.core.TxnDraft
import app.masroufy.core.Wallet
import app.masroufy.core.assistSpendDate
import app.masroufy.core.cashMoveOf
import app.masroufy.core.incomeKindOf
import app.masroufy.core.loanSide
import app.masroufy.core.movePair
import app.masroufy.core.ruleFor
import app.masroufy.core.uiText

/**
 * الدخل والسلفة والتحويل بين محافظك **بالكتابة بكارت تأكيد زي المصروف** (رد المالك §79.2-7 — مش المقترح): «قبضت ٥٠٠٠» دخل «+» · «سلفت أحمد
 * ٢٠٠» سلفة «−» بتظهر في «لك» · «استلفت من خالد ٣٠٠» سلفة «+» في «عليك» · «سحبت ٥٠٠» / «حولت ٥٠٠ من الكاش للبنك» تحويل من غير إشارة.
 * **ولا حاجة بتتكتب قبل «احفظ»**، والحفظ بحالات الاستخدام العادية بس (`AddTransaction` · `ManagePeople.linkToPerson`). الاسترجاع و«دفعت عن …»
 * و«سددت لـ…» وتحويل لحد تاني لسه رد صريح بالمكان الصح (ما اتسألش عنهم).
 */
internal suspend fun TurnKit.moneyCard(u: AssistUnderstanding): TurnOut? {
    val s = u.signals
    if (s.money.isForeign(ctx.currency) || u.note == AssistNote.TWO_AMOUNTS || u.note == AssistNote.FUTURE_DATE) return null
    val amount = s.money.amounts.firstOrNull()?.minor?.takeIf { it > 0 } ?: return null
    val date = assistSpendDate(s.dayOffset, s.period, ctx.today) ?: ctx.today
    val base = TxnDraft(
        title = "", amountMinor = amount, currency = ctx.currency, occurredOn = date, walletId = null, walletName = null, categoryId = null, categoryName = null,
    )
    return when (u.note) {
        AssistNote.NOT_EXPENSE_IN -> {
            val kind = incomeKindOf(s)
            moneyCardOut(base.copy(title = ruleFor(kind).label, kind = kind).withWallet(spendWalletOrNull(s)), u.wire)
        }
        AssistNote.NOT_EXPENSE_DEBT -> {
            val side = loanSide(s) ?: return null
            val person = (u.subject?.takeIf { it.type == AssistEntityType.PERSON } ?: s.specific(AssistEntityType.PERSON))
                ?.let { e -> lex.people.firstOrNull { it.id == e.id } }
                ?: return TurnOut(listOf(text(uiText(AskKey.CHAT_NEEDS_PERSON), u.wire, listOf(ScreenLink.of(AssistScreen.PEOPLE)))), countTopic = false)
            val (kind, title) = if (side == LoanSide.LENT) EconomicKind.LOAN_GRANTED to AskKey.CHAT_LENT_TITLE else EconomicKind.LOAN_RECEIVED to AskKey.CHAT_BORROWED_TITLE
            val draft = base.copy(title = uiText(title, person.name), kind = kind, personId = person.id, personName = person.name)
            moneyCardOut(draft.withWallet(spendWalletOrNull(s)), u.wire)
        }
        AssistNote.NOT_EXPENSE_CASH_MOVE, AssistNote.NOT_EXPENSE_TRANSFER -> {
            val pair = movePair(s)?.let { (f, t) -> wallet(f.id) to wallet(t.id) }
                ?: if (u.note == AssistNote.NOT_EXPENSE_CASH_MOVE) cashMovePair(cashMoveOf(s), s) else return null
            val (from, to) = pair
            if (from == null || to == null || from.id == to.id) {
                return TurnOut(listOf(text(uiText(AskKey.CHAT_MOVE_NEEDS_WALLETS), u.wire, listOf(ScreenLink.of(AssistScreen.TRANSFERS)))), countTopic = false)
            }
            val draft = base.copy(
                title = uiText(AskKey.CHAT_MOVE_TITLE), kind = EconomicKind.INTERNAL_TRANSFER, walletId = from.id, walletName = from.name,
                toWalletId = to.id, toWalletName = to.name,
            )
            moneyCardOut(draft, u.wire)
        }
        else -> null
    }
}

private fun TurnKit.wallet(id: String): Wallet? = lex.wallets.firstOrNull { it.id == id }

private fun TxnDraft.withWallet(w: Wallet?) = copy(walletId = w?.id, walletName = w?.name)

/** المحفظة المذكورة في الكلام ⇒ الأساسية للبلد ⇒ الوحيدة ⇒ null (يتسأل من غير ما الاختيار يبقى «الأساسية» — دي للصرف بس). */
private suspend fun TurnKit.spendWalletOrNull(s: app.masroufy.core.AssistSignals): Wallet? {
    s.entity(AssistEntityType.WALLET)?.let { e -> wallet(e.id)?.let { return it } }
    val usable = lex.wallets.filter { it.kind != "own_abroad" }
    val main = chat.mainWallet(ctx.space.id).get()
    return main ?: usable.singleOrNull()
}

/** «سحبت ٥٠٠» = من البنك المذكور (أو الأساسية لو بنك، أو أول بنك) للكاش · «أودعت» العكس. */
private suspend fun TurnKit.cashMovePair(move: CashMove, s: app.masroufy.core.AssistSignals): Pair<Wallet?, Wallet?> {
    val cash = lex.wallets.firstOrNull { it.kind == "cash" }
    val banks = lex.wallets.filter { it.kind == "bank" || it.kind == "digital_wallet" }
    val named = s.entities.filter { it.type == AssistEntityType.WALLET }.mapNotNull { e -> banks.firstOrNull { it.id == e.id } }.firstOrNull { true }
    val main = chat.mainWallet(ctx.space.id).get()?.takeIf { m -> banks.any { it.id == m.id } }
    val bank = named ?: main ?: banks.firstOrNull()
    return if (move == CashMove.WITHDRAW) bank to cash else cash to bank
}

/** الكارت بنصه حسب النوع، أو «بتصرف عادةً منين؟» من غير ما الاختيار يبقى الأساسية (الكارت جواه لحد ما يختار). */
internal suspend fun TurnKit.moneyCardOut(draft: TxnDraft, topic: String, updates: List<AssistMessage> = emptyList()): TurnOut {
    if (draft.walletId == null) {
        val options = chat.mainWallet(ctx.space.id).askOptions().map { AssistOption(it.id, it.name) }
        val ask = bot(AssistMessageKind.WALLET_PICK, uiText(AskKey.CHAT_WHICH_WALLET), topic)
            .copy(card = draft, options = options, state = CardState.PENDING, payload = mapOf(NOT_MAIN to "1"))
        return TurnOut(listOf(ask), updates, countTopic = false)
    }
    val card = bot(AssistMessageKind.TXN_CARD, cardText(draft), topic).copy(card = draft, state = CardState.PENDING, subjectId = draft.personId)
    return TurnOut(listOf(card), updates, countTopic = false)
}

/** نص الكارت بنوعه: «أسجّلها كده؟» · «أسجّله دخل كده؟» · «أسجّلها سلفة لـأحمد؟ هتظهر في «ليك».» … وعملية شبهها النهارده قبله (§79.2-6). */
internal fun cardText(d: TxnDraft): String = when (d.cardType) {
    CardType.EXPENSE -> (if (d.similarTransactionId != null) uiText(AskKey.CHAT_SIMILAR_TODAY) + " — " else "") + uiText(TextKey.ASSIST_TXN_CARD)
    CardType.INCOME -> uiText(AskKey.CHAT_CARD_INCOME)
    CardType.LENT -> uiText(AskKey.CHAT_CARD_LENT, d.personName.orEmpty())
    CardType.BORROWED -> uiText(AskKey.CHAT_CARD_BORROWED, d.personName.orEmpty())
    CardType.MOVE -> uiText(AskKey.CHAT_CARD_MOVE)
}

/** اختيار المحفظة على كارت دخل أو سلفة أو تحويل ما بيغيّرش الأساسية (دي للصرف بس). */
internal const val NOT_MAIN = "notMain"

/** «احفظ» على كارت دخل · سلفة · تحويل — الإضافة العادية، والسلفة كمان بتتربط بالشخص (`linkToPerson`) فتظهر في «لك» أو «عليك». */
internal suspend fun TurnKit.confirmMoneyCard(msg: AssistMessage, d: TxnDraft): TurnOut {
    val add = deps.add ?: return TurnOut(listOf(text(uiText(TextKey.ASSIST_NA), msg.topic)), countTopic = false)
    val walletId = d.walletId ?: return TurnOut(listOf(text(uiText(TextKey.ASSIST_ASK_MAIN_WALLET), msg.topic)), countTopic = false)
    val type = d.cardType
    if ((type == CardType.LENT || type == CardType.BORROWED) && (deps.people == null || d.personId == null)) {
        return TurnOut(listOf(text(uiText(TextKey.ASSIST_NA), msg.topic)), countTopic = false)
    }
    val tx = add.add(
        NewTransactionInput(
            amountMinor = d.amountMinor, currency = d.currency, occurredAt = d.occurredOn, walletId = walletId,
            transferToWalletId = d.toWalletId.takeIf { type == CardType.MOVE }, economicKind = d.kind, merchantName = d.personName ?: d.title,
            isCashTagged = lex.wallets.firstOrNull { it.id == walletId }?.kind == "cash",
        ),
    )
    val links = mutableListOf(ScreenLink.of(AssistScreen.OPERATION_DETAIL, "transactionId" to tx.id))
    if (type == CardType.LENT || type == CardType.BORROWED) {
        val kind = if (type == CardType.LENT) ObligationKind.RECEIVABLE else ObligationKind.LOAN_PAYABLE
        deps.people!!.linkToPerson(tx.id, d.personId!!, kind, d.amountMinor)
        links += ScreenLink.of(AssistScreen.PERSON_PROFILE, "personId" to d.personId!!)
    }
    val (t, key) = vary(AssistVariants.RECORDED, ctx.space.name)
    return TurnOut(listOf(text(t, msg.topic, links, opener = key)), listOf(msg.copy(state = CardState.DONE, card = d.copy(transactionId = tx.id))), countTopic = false)
}
