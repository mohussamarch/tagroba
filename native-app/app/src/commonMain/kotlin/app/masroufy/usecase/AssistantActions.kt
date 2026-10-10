package app.masroufy.usecase

import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistNote
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistSignals
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.AssistVariants
import app.masroufy.core.AssistWords
import app.masroufy.core.CardState
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.MainWalletSource
import app.masroufy.core.RecurringItem
import app.masroufy.core.ScreenLink
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.TxnDraft
import app.masroufy.core.Wallet
import app.masroufy.core.assistSpendDate
import app.masroufy.core.shiftDays
import app.masroufy.core.pendingCard
import app.masroufy.core.resolveSpendWallet
import app.masroufy.core.similarSameDay
import app.masroufy.core.usualCategoryOf
import app.masroufy.core.usualWalletOf

/**
 * أفعال المساعد (§78-٣ + رد المالك ٢): **ولا حاجة بتتكتب قبل «احفظ»** — الكارت الأول، والحفظ بحالات الاستخدام العادية بس. المحفظة:
 * المذكورة في الكلام ⇒ محفظة الفاتورة الدورية ⇒ الأساسية للبلد ⇒ الوحيدة ⇒ «بتصرف عادةً منين؟» (والاختيار بيبقى الأساسية).
 */
internal suspend fun TurnKit.actionReply(u: AssistUnderstanding): TurnOut = when (u.intent) {
    AssistIntent.QUICK_ADD -> quickAdd(u)
    AssistIntent.EDIT_PENDING -> msgs.pendingCard()?.let { editPending(it, u) } ?: nothingPending(u)
    AssistIntent.CONFIRM_PENDING -> msgs.pendingCard()?.let { confirmCard(it) } ?: nothingPending(u)
    AssistIntent.CANCEL_PENDING -> msgs.pendingCard()?.let { cancelCard(it) } ?: nothingPending(u)
    AssistIntent.SPLIT -> splitReply(u, null)
    AssistIntent.SET_MAIN_WALLET -> setMainWallet(u)
    AssistIntent.RECORD_DUE_BILL -> recordBill(u)
    else -> TurnOut()
}

private fun TurnKit.nothingPending(u: AssistUnderstanding) = TurnOut(listOf(text(uiText(TextKey.ASSIST_NOTHING_PENDING), u.wire)), countTopic = false)

private fun TurnKit.say(key: TextKey, topic: String?, vararg args: String) = TurnOut(listOf(text(uiText(key, *args), topic)), countTopic = false)

/** فلوس مش مصروف (دخل · استرجاع · سلفة · تحويل · سحب) أو «ما تسجلش» ⇒ رد صريح + رابط المكان الصح (صفحة الشخص لو اتذكر) — من غير كارت. */
private fun TurnKit.notExpenseReply(u: AssistUnderstanding): TurnOut? {
    val (key, screen) = when (u.note) {
        AssistNote.DONT_RECORD -> return say(TextKey.ASSIST_NOT_RECORDED, u.wire)
        AssistNote.NOT_EXPENSE_IN -> TextKey.ASSIST_NOT_EXPENSE_IN to AssistScreen.ADD_OPERATION
        AssistNote.NOT_EXPENSE_REFUND -> TextKey.ASSIST_NOT_EXPENSE_REFUND to AssistScreen.ADD_OPERATION
        AssistNote.NOT_EXPENSE_DEBT -> TextKey.ASSIST_NOT_EXPENSE_DEBT to AssistScreen.DEBTS
        AssistNote.NOT_EXPENSE_TRANSFER -> TextKey.ASSIST_NOT_EXPENSE_TRANSFER to AssistScreen.TRANSFERS
        AssistNote.NOT_EXPENSE_CASH_MOVE -> TextKey.ASSIST_NOT_EXPENSE_CASH_MOVE to AssistScreen.TRANSFERS
        else -> return null
    }
    val person = u.subject?.takeIf { it.type == AssistEntityType.PERSON }?.let { linkFor(AssistScreen.PERSON_PROFILE, it) }
    return TurnOut(listOf(text(uiText(key), u.wire, listOfNotNull(person, ScreenLink.of(screen)))), countTopic = false)
}

/** سؤال بصراحة من غير كارت: مش مصروف · عملة تانية · مبلغين · يوم جاي · رقم مش صالح. */
private fun TurnKit.honestQuestion(u: AssistUnderstanding): TurnOut? = notExpenseReply(u) ?: when {
    u.signals.money.isForeign(ctx.currency) -> say(TextKey.ASSIST_FOREIGN, u.wire, ctx.space.name)
    u.note == AssistNote.INVALID_AMOUNT -> say(TextKey.ASSIST_INVALID_AMOUNT, u.wire)
    u.note == AssistNote.TWO_AMOUNTS -> say(TextKey.ASSIST_TWO_AMOUNTS, u.wire)
    u.note == AssistNote.FUTURE_DATE -> say(TextKey.ASSIST_FUTURE_DATE, u.wire)
    else -> null
}

private suspend fun TurnKit.quickAdd(u: AssistUnderstanding): TurnOut {
    // §79.2-7: الدخل والسلفة والتحويل بين محافظك ليهم كروت تأكيد (`AssistantMoneyCards.kt`)؛ الباقي رد صريح زي الأول
    moneyCard(u)?.let { return it }
    honestQuestion(u)?.let { return it }
    val amount = u.signals.money.amounts.firstOrNull()?.minor ?: return say(TextKey.ASSIST_INVALID_AMOUNT, u.wire)
    val date = assistSpendDate(u.signals.dayOffset, u.signals.period, ctx.today) ?: ctx.today
    val recurring = u.signals.specific(AssistEntityType.RECURRING)?.let { e -> lex.recurring.firstOrNull { it.id == e.id } }
    return cardOut(draftFrom(u.signals, amount, date, recurring), u.wire)
}

/** المحفظة اللي الكلام بيسميها: اسمها، أو «كاش» ⇒ محفظة الكاش، أو «البنك» ⇒ أول حساب بنك. */
private fun TurnKit.namedWallet(s: AssistSignals): Wallet? {
    s.entity(AssistEntityType.WALLET)?.let { e -> lex.wallets.firstOrNull { it.id == e.id }?.let { return it } }
    return if (s.has(AssistWords.CASH)) lex.wallets.firstOrNull { it.kind == "cash" } else null
}

internal suspend fun TurnKit.draftFrom(s: AssistSignals, amount: Halalas, date: IsoDate, recurring: RecurringItem?): TxnDraft {
    val merchant = s.specific(AssistEntityType.MERCHANT)?.let { e -> lex.merchants.firstOrNull { it.id == e.id } }
    val rows = if (recurring != null) deps.sources.txns.listByDateRange(shiftDays(ctx.today, -400), ctx.today) else emptyList()
    val catEnt = s.entity(AssistEntityType.CATEGORY)
    val categoryId = merchant?.verifiedCategoryId ?: recurring?.let { usualCategoryOf(it, rows) } ?: catEnt?.id
    val wallet = pickWallet(namedWallet(s)?.id, recurring?.let { usualWalletOf(it, rows) })
    val sameDay = deps.sources.txns.listByDateRange(date, date)
    return TxnDraft(
        title = merchant?.displayName ?: recurring?.name ?: catEnt?.name ?: uiText(TextKey.ASSIST_EXPENSE),
        amountMinor = amount, currency = ctx.currency, occurredOn = date,
        walletId = wallet?.id, walletName = wallet?.name,
        categoryId = categoryId, categoryName = categoryId?.let { id -> lex.categories.firstOrNull { it.id == id }?.name },
        merchantId = merchant?.id, merchantName = merchant?.displayName,
        kind = if (s.has(AssistWords.FEE)) EconomicKind.FEE else EconomicKind.PURCHASE,
        recurringItemId = recurring?.id,
        similarTransactionId = similarSameDay(sameDay, date, amount, ctx.currency)?.id,
    )
}

private suspend fun TurnKit.pickWallet(named: String?, recurringWallet: String?): Wallet? {
    val usable = lex.wallets.filter { it.kind != "own_abroad" }
    val main = chat.mainWallet(ctx.space.id).get()?.id
    val (id, _) = resolveSpendWallet(named, recurringWallet, main, usable.singleOrNull()?.id)
    return id?.let { w -> lex.wallets.firstOrNull { it.id == w } }
}

/** الكارت، أو «بتصرف عادةً منين؟» الأول لو مفيش محفظة (الكارت جواه لحد ما يختار). */
private suspend fun TurnKit.cardOut(draft: TxnDraft, topic: String, updates: List<AssistMessage> = emptyList()): TurnOut {
    // كارت دخل أو سلفة أو تحويل بيفضل بنوعه بعد التعديل بالكتابة (§79.2-7)
    if (draft.cardType != app.masroufy.core.CardType.EXPENSE) return moneyCardOut(draft, topic, updates)
    if (draft.walletId == null) {
        val options = chat.mainWallet(ctx.space.id).askOptions().map { AssistOption(it.id, it.name) }
        val ask = bot(AssistMessageKind.WALLET_PICK, uiText(TextKey.ASSIST_ASK_MAIN_WALLET), topic).copy(card = draft, options = options, state = CardState.PENDING)
        return TurnOut(listOf(ask), updates)
    }
    // عملية شبهها النهارده (§79.2-6): «فيه عملية شبهها النهارده — أسجّلها كده؟» والشاشة بتعرض «سجّلها برضه» / «دي هي، سيبها»
    val card = bot(AssistMessageKind.TXN_CARD, cardText(draft), topic).copy(card = draft, state = CardState.PENDING, subjectId = draft.merchantId ?: draft.categoryId)
    return TurnOut(listOf(card), updates)
}

/** «لا خليها ٢٠» · «كاش» · «امبارح» · «التصنيف بقالة»: الكارت القديم «اتعدّل تحت» وكارت جديد. */
private suspend fun TurnKit.editPending(pending: AssistMessage, u: AssistUnderstanding): TurnOut {
    val s = u.signals
    if (pending.kind == AssistMessageKind.SPLIT_CARD) {
        val d = pending.split ?: return TurnOut()
        val amount = s.money.amounts.firstOrNull()?.minor ?: return nothingPending(u)
        return splitCardOut(d.copy(totalMinor = amount, shares = reshare(d.shares, amount)), u.wire, listOf(pending.copy(state = CardState.DROPPED)))
    }
    val old = pending.card ?: return TurnOut()
    if (old.cardType == app.masroufy.core.CardType.MOVE) {
        // التحويل: المبلغ واليوم، والمحفظتين لو اتذكروا الاتنين («من البنك للكاش») — بيفضل تحويل
        val pair = app.masroufy.core.movePair(s)?.let { (f, t) -> lex.wallets.firstOrNull { it.id == f.id } to lex.wallets.firstOrNull { it.id == t.id } }
        val draft = old.copy(
            amountMinor = s.money.amounts.firstOrNull()?.minor ?: old.amountMinor,
            occurredOn = assistSpendDate(s.dayOffset, s.period, ctx.today) ?: old.occurredOn,
            walletId = pair?.first?.id ?: old.walletId, walletName = pair?.first?.name ?: old.walletName,
            toWalletId = pair?.second?.id ?: old.toWalletId, toWalletName = pair?.second?.name ?: old.toWalletName,
        )
        return cardOut(draft, pending.topic ?: AssistIntent.QUICK_ADD.wire, listOf(pending.copy(state = CardState.DROPPED)))
    }
    val wallet = namedWallet(s)
    val cat = u.subject?.takeIf { it.type == AssistEntityType.CATEGORY } ?: s.entity(AssistEntityType.CATEGORY)?.takeIf { s.has(AssistWords.CATEGORY_WORD) }
    val draft = old.copy(
        amountMinor = s.money.amounts.firstOrNull()?.minor ?: old.amountMinor,
        occurredOn = assistSpendDate(s.dayOffset, s.period, ctx.today) ?: old.occurredOn,
        walletId = wallet?.id ?: old.walletId, walletName = wallet?.name ?: old.walletName,
        categoryId = cat?.id ?: old.categoryId, categoryName = cat?.let { c -> lex.categories.firstOrNull { it.id == c.id }?.name } ?: old.categoryName,
        categoryChanged = old.categoryChanged || (cat != null && cat.id != old.categoryId),
    )
    return cardOut(draft, pending.topic ?: AssistIntent.QUICK_ADD.wire, listOf(pending.copy(state = CardState.DROPPED)))
}

internal suspend fun TurnKit.replaceCard(pending: AssistMessage, draft: TxnDraft): TurnOut {
    val old = pending.card ?: return TurnOut()
    val changed = old.categoryChanged || draft.categoryId != old.categoryId
    return cardOut(draft.copy(categoryChanged = changed), pending.topic ?: AssistIntent.QUICK_ADD.wire, listOf(pending.copy(state = CardState.DROPPED)))
}

/** «احفظ»: الإضافة العادية (`AddTransaction`)، وتصنيف اتغيّر على الكارت بيتحفظ للمحل (§75-16). */
internal suspend fun TurnKit.confirmCard(msg: AssistMessage): TurnOut {
    if (!msg.pending) return TurnOut()
    if (msg.kind == AssistMessageKind.SPLIT_CARD) return confirmSplit(msg)
    val d = msg.card ?: return TurnOut()
    if (d.cardType != app.masroufy.core.CardType.EXPENSE) return confirmMoneyCard(msg, d)
    val add = deps.add ?: return say(TextKey.ASSIST_NA, msg.topic)
    val walletId = d.walletId ?: return say(TextKey.ASSIST_ASK_MAIN_WALLET, msg.topic)
    val cash = lex.wallets.firstOrNull { it.id == walletId }?.kind == "cash"
    val tx = add.add(
        NewTransactionInput(
            amountMinor = d.amountMinor, currency = d.currency, occurredAt = d.occurredOn, walletId = walletId, economicKind = d.kind,
            merchantName = d.merchantName ?: d.title, categoryId = d.categoryId, isCashTagged = cash,
        ),
    )
    if (d.categoryChanged && d.merchantId != null && d.categoryId != null) deps.rules?.setMerchantCategory(d.merchantId!!, d.categoryId)
    val (t, key) = vary(AssistVariants.RECORDED, ctx.space.name)
    val done = text(t, msg.topic, listOf(ScreenLink.of(AssistScreen.OPERATION_DETAIL, "transactionId" to tx.id)), opener = key)
    return TurnOut(listOf(done), listOf(msg.copy(state = CardState.DONE, card = d.copy(transactionId = tx.id))), countTopic = false)
}

internal fun TurnKit.cancelCard(msg: AssistMessage): TurnOut =
    TurnOut(listOf(text(uiText(TextKey.ASSIST_CANCELLED), msg.topic)), listOf(msg.copy(state = CardState.CANCELLED)), countTopic = false)

private fun TurnKit.setMainWallet(u: AssistUnderstanding): TurnOut {
    val w = u.subject?.let { e -> lex.wallets.firstOrNull { it.id == e.id } } ?: return say(TextKey.ASSIST_NA, u.wire)
    val msg = bot(AssistMessageKind.CHOICE, uiText(TextKey.ASSIST_MAIN_CONFIRM, quoted(w.name)), u.wire)
        .copy(options = yesNo(), choice = AssistChoiceKind.CONFIRM_MAIN_WALLET, state = CardState.PENDING, payload = mapOf("walletId" to w.id))
    return TurnOut(listOf(msg), countTopic = false)
}

/** «سجّل فاتورة الكهرباء»: مبلغها المتوقع ومحفظتها المعتادة (محفظة آخر دفعة، ولو عمرها ما اتدفعت ⇒ الأساسية — المالك أكّد، L2) — المبلغ يتعدّل قبل التأكيد. */
private suspend fun TurnKit.recordBill(u: AssistUnderstanding): TurnOut {
    val item = u.subject?.takeIf { it.type == AssistEntityType.RECURRING }?.let { e -> lex.recurring.firstOrNull { it.id == e.id } }
        ?: return TurnOut(listOf(text(uiText(TextKey.ASSIST_RECORD_BILL_NONE), u.wire, listOf(ScreenLink.of(AssistScreen.SUBSCRIPTIONS)))))
    return cardOut(draftFrom(u.signals, item.expectedMinor, ctx.today, item), u.wire)
}

/** اختيار زرار على رسالة (محفظة · «تقصد مين؟» · «أضيفه؟» · قسّم الموجودة · تأكيدات). */
internal suspend fun TurnKit.pick(msg: AssistMessage, optionId: String): TurnOut {
    if (!msg.pending) return TurnOut()
    val picked = msg.copy(picked = optionId, state = CardState.DONE)
    if (msg.kind == AssistMessageKind.WALLET_PICK) {
        val w = lex.wallets.firstOrNull { it.id == optionId } ?: return TurnOut()
        // كارت دخل أو سلفة أو تحويل: المحفظة للعملية دي بس — الأساسية للصرف (§78 ٢)
        val setsMain = msg.payload[NOT_MAIN] != "1"
        if (setsMain) chat.mainWallet(ctx.space.id).set(w.id, MainWalletSource.CHAT)
        val note = text(uiText(TextKey.ASSIST_MAIN_SET, quoted(w.name)), msg.topic).takeIf { setsMain }
        val out = msg.split?.let { splitCardOut(it.copy(walletId = w.id, walletName = w.name), msg.topic.orEmpty(), listOf(picked)) }
            ?: msg.card?.let { cardOut(it.copy(walletId = w.id, walletName = w.name), msg.topic.orEmpty(), listOf(picked)) } ?: TurnOut(updates = listOf(picked))
        return out.copy(messages = listOfNotNull(note) + out.messages)
    }
    val payload = msg.payload
    val out = when (msg.choice) {
        AssistChoiceKind.PICK_SUBJECT -> {
            val u = app.masroufy.core.understandAssist(payload["text"].orEmpty(), chat.understandContext(lex, msgs, ctx))
            val forced = AssistIntent.fromWire(payload["topic"].orEmpty())
            reply(u.copy(intent = forced ?: u.intent, subject = entityById(lex, optionId), note = null))
        }
        AssistChoiceKind.CONFIRM_MAIN_WALLET -> if (optionId != OPT_YES) say(TextKey.ASSIST_CANCELLED, msg.topic) else {
            val w = lex.wallets.firstOrNull { it.id == payload["walletId"] } ?: return TurnOut()
            chat.mainWallet(ctx.space.id).set(w.id, MainWalletSource.CHAT)
            say(TextKey.ASSIST_MAIN_SET, msg.topic, quoted(w.name))
        }
        AssistChoiceKind.CONFIRM_LEARNING -> if (optionId != OPT_YES) say(TextKey.ASSIST_CANCELLED, msg.topic) else {
            val on = payload["on"] == "true"
            chat.learning.set(on)
            say(if (on) TextKey.ASSIST_LEARNING_ON_DONE else TextKey.ASSIST_LEARNING_OFF_DONE, msg.topic)
        }
        AssistChoiceKind.ADD_PERSON -> if (optionId != OPT_YES) say(TextKey.ASSIST_SPLIT_NEEDS_PEOPLE, msg.topic) else {
            deps.people?.addPerson(payload["name"].orEmpty()) ?: return say(TextKey.ASSIST_NA, msg.topic)
            val fresh = deps.lexicon.load()
            val u = app.masroufy.core.understandAssist(payload["text"].orEmpty(), chat.understandContext(fresh, msgs, ctx))
            TurnKit(deps, chat, ctx, conversationId, msgs + picked, fresh).splitReply(u, null)
        }
        AssistChoiceKind.SPLIT_EXISTING -> {
            val u = app.masroufy.core.understandAssist(payload["text"].orEmpty(), chat.understandContext(lex, msgs, ctx))
            splitReply(u, if (optionId == OPT_EXISTING) payload["transactionId"] else NEW_SPLIT)
        }
        null -> TurnOut()
    }
    return out.copy(updates = listOf(picked) + out.updates)
}

internal const val OPT_EXISTING = "existing"
internal const val OPT_NEW = "new"
