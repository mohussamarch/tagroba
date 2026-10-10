package app.masroufy.usecase

import app.masroufy.core.ASSIST_UNKNOWN_TOPIC
import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistEntity
import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistLexicon
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.AssistUnderstandContext
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.ChipBar
import app.masroufy.core.ChipRef
import app.masroufy.core.StartPick
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.conversationHeader
import app.masroufy.core.countTopic
import app.masroufy.core.currentConversation
import app.masroufy.core.inOrder
import app.masroufy.core.pendingCard
import app.masroufy.core.understandAssist
import app.masroufy.core.zakatVisible
import app.masroufy.port.AlertDismissalStore
import app.masroufy.port.AllocationRepository
import app.masroufy.port.AssistantConversationStore
import app.masroufy.port.AssistantForgottenStore
import app.masroufy.port.AssistantMessageStore
import app.masroufy.port.AssistantTopicStore
import app.masroufy.port.AssistantUnknownStore
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.UserSettingsStore

/** مخازن المساعد (كلها على الحساب — §78). */
data class AssistantStores(
    val conversations: AssistantConversationStore,
    val messages: AssistantMessageStore,
    val topics: AssistantTopicStore,
    val forgotten: AssistantForgottenStore,
    val unknown: AssistantUnknownStore,
    val settings: UserSettingsStore,
    val alertDismissals: AlertDismissalStore? = null,
)

/**
 * كل اللي المساعد محتاجه للبلد الشغالة: المخازن · مصادر الأرقام ([AssistantSources] — كل رقم منها) · الأسامي · والكتابة بحالات الاستخدام
 * العادية بس (إضافة عملية · ربط بشخص · ذاكرة تصنيف المحل). أي كتابة null ⇒ الكارت بيقول «غير متاح» بدل ما يكتب بطريقة تانية.
 */
data class AssistantDeps(
    val stores: AssistantStores,
    val sources: AssistantSources,
    val lexicon: AssistLexiconSource,
    val ids: IdGenerator,
    val clock: Clock,
    val add: AddTransaction? = null,
    val people: ManagePeople? = null,
    val rules: ManageRules? = null,
    val allocations: AllocationRepository? = null,
)

/** اللوحة اللي الرد بيطلب فتحها: السجل · «اللي اتعلمته عنك». */
enum class AssistPanel { NONE, HISTORY, MEMORY }

/**
 * شاشة المحادثة: الرسايل (فاضية = محادثة جديدة لسه ما اتبعتش فيها حاجة) · «أمور لم تُنجزها بعد» (أول المحادثة بس) · الاقتراحات تحت
 * مستطيل الكتابة · التحية والاسم «مصروفي» و«مساعدك المالي الذكي».
 */
data class ChatView(
    val conversation: AssistConversation?,
    val messages: List<AssistMessage>,
    val start: StartPick?,
    val startNote: String?,
    val chips: ChipBar,
    val greeting: String,
    val intro: String,
    val panel: AssistPanel = AssistPanel.NONE,
) {
    val title: String get() = uiText(TextKey.ASSIST_BRAND)
    val subtitle: String get() = uiText(TextKey.ASSIST_BRAND_TITLE)
}

/**
 * المساعد «مصروفي» (OVERRIDES §78 + ردود المالك 2026-10-09): قواعد على الجوال، بيرد من أرقامك بس. ترتيب الفهم في `understandAssist`
 * (الكارت المستني ⇒ تقسيم ⇒ مصروف بمبلغ ⇒ موضوع معروف ⇒ اسم شاشة ⇒ «مش فاهم»). المحادثة بتخلص بعد ساعة من غير رسايل.
 */
class AssistantChat(private val deps: AssistantDeps) {
    private val st = deps.stores
    internal val learning = AssistantLearning(st.settings, deps.clock)
    internal val answers = AssistantAnswers(deps.sources)
    internal val startItems = AssistantStart(deps)
    internal val mainWallet: (String) -> MainSpendingWallets = { spaceId -> MainSpendingWallets(st.settings, deps.sources.wallets, spaceId, deps.clock) }

    suspend fun open(ctx: AssistContext): ChatView {
        val conv = currentConversation(st.conversations.listAll(), ctx.nowIso)
        val msgs = conv?.let { st.messages.listByConversation(it.id).inOrder() }.orEmpty()
        return view(conv, msgs, ctx, AssistPanel.NONE)
    }

    /** «محادثة جديدة»: رأس فاضي بوقت دلوقتي ⇒ هو الحالي، والقديمة في السجل (السجل ما بيعرضش المحادثة الفاضية). */
    suspend fun newConversation(ctx: AssistContext): ChatView {
        val conv = AssistConversation(deps.ids.next("conv"), ctx.space.id, ctx.nowIso, ctx.nowIso, "", "", 0)
        st.conversations.save(conv)
        return view(conv, emptyList(), ctx, AssistPanel.NONE)
    }

    suspend fun send(text: String, ctx: AssistContext): ChatView {
        if (text.isBlank()) return open(ctx)
        return turn(text, ctx) { lex, msgs -> understandAssist(text, understandContext(lex, msgs, ctx)) }
    }

    /** اقتراح اتداس: نفس النية والاسم بالظبط (النص اللي بيظهر = اسم الاقتراح). */
    suspend fun ask(chip: ChipRef, ctx: AssistContext): ChatView {
        val lex = deps.lexicon.load()
        val label = chipLabel(chip, lex)
        return turn(label, ctx) { l, msgs ->
            val base = understandAssist(label, understandContext(l, msgs, ctx))
            val screen = AssistScreen.entries.firstOrNull { it.navWire == chip.topic }
            when {
                screen != null -> base.copy(intent = AssistIntent.NAV, screens = listOf(screen), subject = null)
                else -> base.copy(intent = AssistIntent.fromWire(chip.topic) ?: base.intent, subject = chip.subjectId?.let { entityById(l, it) } ?: base.subject)
            }
        }
    }

    private suspend fun turn(text: String, ctx: AssistContext, understand: suspend (AssistLexicon, List<AssistMessage>) -> AssistUnderstanding): ChatView {
        val lex = deps.lexicon.load()
        val conv = currentConversation(st.conversations.listAll(), ctx.nowIso)
            ?: AssistConversation(deps.ids.next("conv"), ctx.space.id, ctx.nowIso, ctx.nowIso, "", "", 0)
        val before = st.messages.listByConversation(conv.id).inOrder()
        val me = message(conv.id, before.size, ctx, AssistSpeaker.ME, AssistMessageKind.TEXT, text)
        st.messages.save(me)
        val u = understand(lex, before)
        val turn = TurnKit(deps, this, ctx, conv.id, before + me, lex)
        val out = turn.reply(u)
        out.messages.forEach { st.messages.save(it) }
        out.updates.forEach { st.messages.save(it) }
        if (out.countTopic && learning.isOn() && u.intent.learnable) {
            val key = u.topicKey
            st.topics.save(countTopic(st.topics.listAll().firstOrNull { it.key == key }, u.wire, u.subject?.id, ctx.nowIso))
        }
        val shown = persistAndView(conv, ctx, out.panel)
        return if (out.startNew) newConversation(ctx) else shown
    }

    /** «احفظ / سجّلها» على كارت (عملية · تقسيم) — نفس «أيوه». */
    suspend fun confirm(messageId: String, ctx: AssistContext): ChatView = cardAction(messageId, ctx) { kit, msg -> kit.confirmCard(msg) }

    suspend fun cancel(messageId: String, ctx: AssistContext): ChatView = cardAction(messageId, ctx) { kit, msg -> kit.cancelCard(msg) }

    /** اختيار زرار (محفظة «بتصرف عادةً منين؟» · «تقصد مين؟» · «أضيفه؟» · تأكيد). */
    suspend fun pick(messageId: String, optionId: String, ctx: AssistContext): ChatView = cardAction(messageId, ctx) { kit, msg -> kit.pick(msg, optionId) }

    /** تعديل الكارت من الشاشة (المبلغ · المحفظة · التصنيف · اليوم): الكارت القديم «اتعدّل تحت» وكارت جديد. */
    suspend fun editCard(messageId: String, draft: app.masroufy.core.TxnDraft, ctx: AssistContext): ChatView =
        cardAction(messageId, ctx) { kit, msg -> kit.replaceCard(msg, draft) }

    private suspend fun cardAction(messageId: String, ctx: AssistContext, act: suspend (TurnKit, AssistMessage) -> TurnOut): ChatView {
        val msg = st.messages.listAll().firstOrNull { it.id == messageId } ?: return open(ctx)
        val conv = st.conversations.listAll().firstOrNull { it.id == msg.conversationId } ?: return open(ctx)
        val msgs = st.messages.listByConversation(conv.id).inOrder()
        val out = act(TurnKit(deps, this, ctx, conv.id, msgs, deps.lexicon.load()), msg)
        out.updates.forEach { st.messages.save(it) }
        out.messages.forEach { st.messages.save(it) }
        return persistAndView(conv, ctx, out.panel)
    }

    private suspend fun persistAndView(conv: AssistConversation, ctx: AssistContext, panel: AssistPanel): ChatView {
        val all = st.messages.listByConversation(conv.id).inOrder()
        val header = conversationHeader(conv.id, conv.spaceId, all, ctx.nowIso)
        st.conversations.save(header)
        return view(header, all, ctx, panel)
    }

    private suspend fun view(conv: AssistConversation?, msgs: List<AssistMessage>, ctx: AssistContext, panel: AssistPanel): ChatView {
        val lex = deps.lexicon.load()
        val start = if (msgs.isEmpty()) startItems.pick(ctx, lex) else null
        val note = start?.let { if (it.items.isNotEmpty()) null else uiText(if (it.anyHidden) TextKey.ASSIST_NOTHING_WAITING else TextKey.ASSIST_ALL_DONE) }
        val lastTopic = msgs.lastOrNull { it.from == AssistSpeaker.BOT && it.topic != null && it.topic != ASSIST_UNKNOWN_TOPIC }
        val chips = chipBar(ctx, lex, lastTopic, st.topics.listAll(), learning.isOn(), zakatShown())
        val profile = deps.sources.profileOrNull()
        return ChatView(conv, msgs, start, note, chips, greetingLine(ctx.hour, profile?.displayName), introLine(ctx, msgs))
    }

    internal suspend fun zakatShown(): Boolean = deps.sources.zakat?.visible() ?: zakatVisible(deps.sources.profileOrNull())

    internal suspend fun understandContext(lex: AssistLexicon, msgs: List<AssistMessage>, ctx: AssistContext): AssistUnderstandContext {
        val zakat = zakatShown()
        return AssistUnderstandContext(lex, msgs.pendingCard() != null, ctx.subjectPersonId) { s ->
            zakat || (s != AssistScreen.ZAKAT && s != AssistScreen.ZAKAT_PAY)
        }
    }

    internal fun message(conversationId: String, index: Int, ctx: AssistContext, from: AssistSpeaker, kind: AssistMessageKind, text: String) =
        AssistMessage(messageId(conversationId, index), conversationId, ctx.nowIso, from, kind, text)

    internal fun messageId(conversationId: String, index: Int) = "$conversationId-${index.toString().padStart(4, '0')}-${deps.ids.next("m")}"
}

/** اسم في أسامي المستخدم بمعرّفه (للاقتراحات المحفوظة بالمعرّف). */
internal fun entityById(lex: AssistLexicon, id: String): AssistEntity? {
    fun e(type: AssistEntityType, name: String?) = name?.let { AssistEntity(type, id, it, -1, -1) }
    return e(AssistEntityType.CATEGORY, lex.categories.firstOrNull { it.id == id }?.name)
        ?: e(AssistEntityType.MERCHANT, lex.merchants.firstOrNull { it.id == id }?.displayName)
        ?: e(AssistEntityType.PERSON, lex.people.firstOrNull { it.id == id }?.name)
        ?: e(AssistEntityType.WALLET, lex.wallets.firstOrNull { it.id == id }?.name)
        ?: e(AssistEntityType.RECURRING, lex.recurring.firstOrNull { it.id == id }?.name)
        ?: e(AssistEntityType.GOAL, lex.goals.firstOrNull { it.id == id }?.names?.firstOrNull())
        ?: e(AssistEntityType.EVENT, lex.events.firstOrNull { it.id == id }?.names?.firstOrNull())
        ?: e(AssistEntityType.PROJECT, lex.projects.firstOrNull { it.id == id }?.names?.firstOrNull())
        ?: e(AssistEntityType.ASSET, lex.assets.firstOrNull { it.id == id }?.names?.firstOrNull())
        ?: e(AssistEntityType.ROSCA, lex.roscas.firstOrNull { it.id == id }?.names?.firstOrNull())
        ?: e(AssistEntityType.PLAN, lex.plans.firstOrNull { it.id == id }?.names?.firstOrNull())
}
