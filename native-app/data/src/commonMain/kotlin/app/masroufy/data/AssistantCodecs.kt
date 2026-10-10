package app.masroufy.data

import app.masroufy.core.ALERT_DISMISSALS_GROUP
import app.masroufy.core.ASSISTANT_CONVERSATIONS_GROUP
import app.masroufy.core.ASSISTANT_FORGOTTEN_GROUP
import app.masroufy.core.ASSISTANT_MESSAGES_GROUP
import app.masroufy.core.ASSISTANT_TOPICS_GROUP
import app.masroufy.core.ASSISTANT_UNKNOWN_GROUP
import app.masroufy.core.AlertDismissal
import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.AssistTopic
import app.masroufy.core.CardState
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.ForgottenMark
import app.masroufy.core.MainWalletSource
import app.masroufy.core.ScreenLink
import app.masroufy.core.SplitDraft
import app.masroufy.core.SplitShare
import app.masroufy.core.TextKey
import app.masroufy.core.TxnDraft
import app.masroufy.core.USER_SETTINGS_GROUP
import app.masroufy.core.UnknownQuestion
import app.masroufy.core.UserSetting
import app.masroufy.core.uiText

/**
 * المساعد «مصروفي» على فايربيز (OVERRIDES §78 + ردود المالك 2026-10-09): **كله على مستوى الحساب** وبيتزامن وفي النسخة الشاملة.
 * كل مستند فيه `id` = معرّفه (عشان النسخة الشاملة تستعيده بنفس المعرّف). النص الحر بيتقص (أي ٥ أرقام ورا بعض ⇒ آخر ٤ — CLAUDE.md #11).
 * إعدادات المستخدم في مجموعتها (`userSettings`) — **مش في `profile/main`** (الحفظ الكامل للملف في التطبيقين كان هيمسحها).
 */
object AssistantCodecs {
    val userSettings: DocCodec<UserSetting> = codec(
        USER_SETTINGS_GROUP, { it.key },
        { s ->
            doc {
                req("id", s.key); req("updatedAt", s.updatedAt)
                when (s) {
                    is UserSetting.MainWallet -> { req("type", "mainWallet"); req("spaceId", s.spaceId); req("walletId", s.walletId); req("setFrom", s.setFrom.wire) }
                    // «يتعلّم من أسئلتي» بيتكتب بس لو واقف (من غيره = شغال)
                    is UserSetting.Assistant -> { req("type", "assistant"); opt("learningOn", if (s.learningOn) null else false) }
                }
            }
        },
        { d ->
            when (val type = d.str("type")) {
                "mainWallet" -> UserSetting.MainWallet(d.str("spaceId"), d.str("walletId"), d.wire("setFrom") { MainWalletSource.fromWire(it)!! }, d.str("updatedAt"))
                "assistant" -> UserSetting.Assistant(d.boolOrNull("learningOn") ?: true, d.str("updatedAt"))
                else -> throw DocumentError(uiText(TextKey.DOC_FIELD_VALUE, USER_SETTINGS_GROUP, "type", type))
            }
        },
    )

    val conversations: DocCodec<AssistConversation> = codec(
        ASSISTANT_CONVERSATIONS_GROUP, { it.id },
        { c ->
            doc {
                req("id", c.id); req("spaceId", c.spaceId); req("createdAt", c.createdAt); req("lastMessageAt", c.lastMessageAt)
                req("title", sanitizeAccountNumbers(c.title)); req("firstReply", sanitizeAccountNumbers(c.firstReply)); req("messageCount", c.messageCount)
            }
        },
        { d -> AssistConversation(d.str("id"), d.str("spaceId"), d.str("createdAt"), d.str("lastMessageAt"), d.str("title"), d.str("firstReply"), d.int("messageCount")) },
    )

    val messages: DocCodec<AssistMessage> = codec(ASSISTANT_MESSAGES_GROUP, { it.id }, ::encodeMessage, ::decodeMessage)

    val topics: DocCodec<AssistTopic> = codec(
        ASSISTANT_TOPICS_GROUP, { it.key },
        { t -> doc { req("id", t.key); req("topic", t.topic); opt("subjectId", t.subjectId); req("askCount", t.askCount); req("lastAskedAt", t.lastAskedAt) } },
        { d -> AssistTopic(d.str("topic"), d.strOrNull("subjectId"), d.int("askCount"), d.str("lastAskedAt")) },
    )

    val forgotten: DocCodec<ForgottenMark> = codec(
        ASSISTANT_FORGOTTEN_GROUP, { it.id },
        { f -> doc { req("id", f.id); req("factKey", f.factKey); req("createdAt", f.createdAt) } },
        { d -> ForgottenMark(d.str("factKey"), d.str("createdAt")) },
    )

    val unknown: DocCodec<UnknownQuestion> = codec(
        ASSISTANT_UNKNOWN_GROUP, { it.id },
        { q ->
            doc {
                req("id", q.id); req("text", sanitizeAccountNumbers(q.text)); req("normalized", q.normalized); req("askCount", q.askCount)
                req("firstAskedAt", q.firstAskedAt); req("lastAskedAt", q.lastAskedAt); req("screen", q.screen); req("spaceId", q.spaceId)
            }
        },
        { d -> UnknownQuestion(d.str("text"), d.str("normalized"), d.int("askCount"), d.str("firstAskedAt"), d.str("lastAskedAt"), d.str("screen"), d.str("spaceId")) },
    )

    val alertDismissals: DocCodec<AlertDismissal> = codec(
        ALERT_DISMISSALS_GROUP, { it.id },
        { a -> doc { req("id", a.id); req("threadKey", a.threadKey); req("dismissedAt", a.dismissedAt); opt("eventKey", a.eventKey) } },
        { d -> AlertDismissal(d.str("threadKey"), d.str("dismissedAt"), d.strOrNull("eventKey")) },
    )

    val all: List<DocCodec<*>> = listOf(userSettings, conversations, messages, topics, forgotten, unknown, alertDismissals)
}

private fun encodeMessage(m: AssistMessage) = doc {
    req("id", m.id); req("conversationId", m.conversationId); req("createdAt", m.createdAt); req("from", m.from.wire); req("kind", m.kind.wire)
    req("text", sanitizeAccountNumbers(m.text)); opt("topic", m.topic); opt("subjectId", m.subjectId); opt("openerKey", m.openerKey)
    opt("state", m.state?.wire); opt("card", m.card?.let(::encodeTxn)); opt("split", m.split?.let(::encodeSplit))
    req("options", m.options.map { mapOf("id" to it.id, "label" to sanitizeAccountNumbers(it.label)) }); opt("picked", m.picked)
    req("links", m.links.map { mapOf("screen" to it.screen.navWire, "args" to it.args, "label" to it.label) })
    opt("choice", m.choice?.wire); req("payload", m.payload.mapValues { sanitizeAccountNumbers(it.value) }); req("approximate", m.approximate)
}

private fun decodeMessage(d: DocReader) = AssistMessage(
    id = d.str("id"), conversationId = d.str("conversationId"), createdAt = d.str("createdAt"),
    from = d.wire("from") { AssistSpeaker.fromWire(it)!! }, kind = d.wire("kind") { AssistMessageKind.fromWire(it)!! }, text = d.str("text"),
    topic = d.strOrNull("topic"), subjectId = d.strOrNull("subjectId"), openerKey = d.strOrNull("openerKey"),
    state = d.strOrNull("state")?.let { w -> CardState.fromWire(w) ?: throw DocumentError(uiText(TextKey.DOC_FIELD_VALUE, ASSISTANT_MESSAGES_GROUP, "state", w)) },
    card = if (d.has("card")) decodeTxn(d.map("card")) else null, split = if (d.has("split")) decodeSplit(d.map("split")) else null,
    options = if (d.has("options")) d.maps("options").map { AssistOption(it.str("id"), it.str("label")) } else emptyList(), picked = d.strOrNull("picked"),
    // شاشة مش معروفة (نسخة أحدث من التطبيق) ⇒ الرابط بيتخطّى، الرسالة نفسها بتفضل
    links = if (d.has("links")) d.maps("links").mapNotNull { l -> AssistScreen.entries.firstOrNull { it.navWire == l.str("screen") }?.let { ScreenLink(it, l.stringMap("args"), l.str("label")) } } else emptyList(),
    choice = d.strOrNull("choice")?.let { AssistChoiceKind.fromWire(it) }, payload = if (d.has("payload")) d.stringMap("payload") else emptyMap(),
    approximate = d.boolOrNull("approximate") ?: false,
)

private fun encodeTxn(t: TxnDraft): Map<String, Any> = linkedMapOf<String, Any>(
    "title" to sanitizeAccountNumbers(t.title), "amountMinor" to t.amountMinor, "currency" to t.currency.name, "occurredOn" to t.occurredOn,
    "kind" to t.kind.wire, "categoryChanged" to t.categoryChanged,
).apply {
    t.walletId?.let { put("walletId", it) }; t.walletName?.let { put("walletName", it) }; t.categoryId?.let { put("categoryId", it) }
    t.categoryName?.let { put("categoryName", it) }; t.merchantId?.let { put("merchantId", it) }; t.merchantName?.let { put("merchantName", it) }
    t.recurringItemId?.let { put("recurringItemId", it) }; t.similarTransactionId?.let { put("similarTransactionId", it) }; t.transactionId?.let { put("transactionId", it) }
}

private fun decodeTxn(d: DocReader) = TxnDraft(
    d.str("title"), d.long("amountMinor"), currencyOf(d), d.str("occurredOn"), d.strOrNull("walletId"), d.strOrNull("walletName"),
    d.strOrNull("categoryId"), d.strOrNull("categoryName"), d.strOrNull("merchantId"), d.strOrNull("merchantName"),
    d.wire("kind") { w -> EconomicKind.entries.first { it.wire == w } }, d.strOrNull("recurringItemId"), d.strOrNull("similarTransactionId"),
    d.boolOrNull("categoryChanged") ?: false, d.strOrNull("transactionId"),
)

private fun encodeSplit(s: SplitDraft): Map<String, Any> = linkedMapOf<String, Any>(
    "title" to sanitizeAccountNumbers(s.title), "totalMinor" to s.totalMinor, "currency" to s.currency.name, "occurredOn" to s.occurredOn,
    "shares" to s.shares.map { sh -> linkedMapOf<String, Any>("name" to sh.name, "amountMinor" to sh.amountMinor, "isMe" to sh.isMe).apply { sh.personId?.let { put("personId", it) } } },
).apply {
    s.walletId?.let { put("walletId", it) }; s.walletName?.let { put("walletName", it) }; s.categoryId?.let { put("categoryId", it) }
    s.categoryName?.let { put("categoryName", it) }; s.existingTransactionId?.let { put("existingTransactionId", it) }; s.transactionId?.let { put("transactionId", it) }
}

private fun decodeSplit(d: DocReader) = SplitDraft(
    d.str("title"), d.long("totalMinor"), currencyOf(d), d.str("occurredOn"), d.strOrNull("walletId"), d.strOrNull("walletName"),
    d.strOrNull("categoryId"), d.strOrNull("categoryName"),
    d.maps("shares").map { SplitShare(it.strOrNull("personId"), it.str("name"), it.long("amountMinor"), it.boolOrNull("isMe") ?: false) },
    d.strOrNull("existingTransactionId"), d.strOrNull("transactionId"),
)

private fun currencyOf(d: DocReader): Currency = d.wire("currency") { w -> Currency.valueOf(w) }

/** خريطة نصوص (معرّفات رابط الشاشة · بيانات الاختيار). */
private fun DocReader.stringMap(name: String): Map<String, String> {
    if (!has(name)) return emptyMap()
    val m = map(name)
    return m.keys().associateWith { m.str(it) }
}
