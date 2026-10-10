package app.masroufy.usecase

import app.masroufy.core.ASSIST_UNKNOWN_TOPIC
import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistEntity
import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistIntentKind
import app.masroufy.core.AssistLexicon
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistNote
import app.masroufy.core.AssistOption
import app.masroufy.core.AssistPlatform
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.AssistVariants
import app.masroufy.core.CardState
import app.masroufy.core.GreetingKind
import app.masroufy.core.ScreenLink
import app.masroufy.core.TAB_CHIPS
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.Variant
import app.masroufy.core.clipText
import app.masroufy.core.detectGreeting
import app.masroufy.core.isLateNight
import app.masroufy.core.nearestScreens
import app.masroufy.core.pickVariant
import app.masroufy.core.recordUnknown
import app.masroufy.core.variantDay

/** نتيجة دور: رسايل جديدة · رسايل اتعدلت (حالة كارت) · لوحة تتفتح · يتعدّ في المواضيع ولا لأ. */
internal data class TurnOut(
    val messages: List<AssistMessage> = emptyList(),
    val updates: List<AssistMessage> = emptyList(),
    val panel: AssistPanel = AssistPanel.NONE,
    val countTopic: Boolean = true,
    /** «محادثة جديدة» مكتوبة: الرد بيتحفظ في دي، وبعدها محادثة جديدة فاضية. */
    val startNew: Boolean = false,
)

/** دور واحد في المحادثة: بيبني ردود «مصروفي» بالمعرّفات المتسلسلة بعد آخر رسالة. */
internal class TurnKit(
    val deps: AssistantDeps,
    val chat: AssistantChat,
    val ctx: AssistContext,
    val conversationId: String,
    val msgs: List<AssistMessage>,
    val lex: AssistLexicon,
) {
    private var next = msgs.size
    private val lastBot = msgs.lastOrNull { it.from == AssistSpeaker.BOT }
    private val botCount = msgs.count { it.from == AssistSpeaker.BOT }

    fun bot(kind: AssistMessageKind, text: String, topic: String? = null): AssistMessage =
        AssistMessage(chat.messageId(conversationId, next++), conversationId, ctx.nowIso, AssistSpeaker.BOT, kind, text, topic = topic)

    fun text(text: String, topic: String? = null, links: List<ScreenLink> = emptyList(), approximate: Boolean = false, opener: String? = null) =
        bot(AssistMessageKind.TEXT, text, topic).copy(links = links, approximate = approximate, openerKey = opener)

    /** صيغة من الردود المتنوعة — ما بتتكررش ورا بعض. */
    fun vary(variants: List<Variant>, vararg args: String): Pair<String, String> {
        val v = pickVariant(variants, lastBot?.openerKey, variantDay(ctx.today), botCount) ?: variants.first()
        return uiText(v.text, *args) to v.key
    }

    suspend fun reply(u: AssistUnderstanding): TurnOut = when (u.intent.kind) {
        AssistIntentKind.DATA -> dataReply(u)
        AssistIntentKind.NAVIGATION -> navReply(u)
        AssistIntentKind.ACTION -> actionReply(u)
        AssistIntentKind.CONTROL -> controlReply(u)
        AssistIntentKind.SMALLTALK -> TurnOut(listOf(smallTalk(u)), countTopic = false)
        AssistIntentKind.FALLBACK -> unknownReply(u)
    }

    /** تحية جوه سؤال حقيقي ⇒ أول الرد بس؛ نفس الموضوع تاني ⇒ «الرقم الآن:»؛ بعد «مش فاهم» ⇒ «فهمتك الآن». */
    private fun opener(u: AssistUnderstanding): String {
        val key = when {
            u.greeting == GreetingKind.SALAM -> TextKey.ASSIST_OPENER_SALAM
            u.greeting != null -> TextKey.ASSIST_OPENER_HELLO
            lastBot?.topic == ASSIST_UNKNOWN_TOPIC -> TextKey.ASSIST_OPENER_UNDERSTOOD
            lastBot?.topic == u.wire && lastBot.subjectId == u.subject?.id -> TextKey.ASSIST_OPENER_AGAIN
            else -> return ""
        }
        return uiText(key) + " "
    }

    private suspend fun dataReply(u: AssistUnderstanding): TurnOut {
        if (u.note == AssistNote.AMBIGUOUS && u.subject != null) return pickSubject(u)
        val r = chat.answers.answer(u, ctx)
        return TurnOut(listOf(text(opener(u) + r.text, u.wire, r.links, r.approximate).copy(subjectId = u.subject?.id)))
    }

    /** اسمين بنفس الاسم ⇒ «تقصد مين؟» — ما بنخمّنش. */
    private fun pickSubject(u: AssistUnderstanding): TurnOut {
        val subject = u.subject ?: return TurnOut()
        val same = u.signals.entities.filter { it.type == subject.type && it.start == subject.start }.distinctBy { it.id }
        // نفس الاسم ⇒ رقم جنبه بترتيب الأشخاص (الشاشة بتعرض صورة/صلة كل واحد — هنا الاسم بس)
        val options = same.mapIndexed { i, e -> AssistOption(e.id, if (same.size > 1) "${e.name} (${i + 1})" else e.name) }
        val msg = bot(AssistMessageKind.CHOICE, uiText(TextKey.ASSIST_PICK_SUBJECT), u.wire)
            .copy(options = options, choice = AssistChoiceKind.PICK_SUBJECT, state = CardState.PENDING, payload = mapOf("text" to u.signals.raw, "topic" to u.wire))
        return TurnOut(listOf(msg), countTopic = false)
    }

    private suspend fun navReply(u: AssistUnderstanding): TurnOut {
        if (u.note == AssistNote.AMBIGUOUS && u.subject != null) return pickSubject(u)
        val first = u.screens.firstOrNull() ?: return unknownReply(u)
        val special: Pair<TextKey, ScreenLink>? = when {
            first == AssistScreen.LOCK -> TextKey.ASSIST_NAV_LOCK to ScreenLink.of(AssistScreen.LOCK)
            first == AssistScreen.SPACE_TRANSFER && ctx.spaceCount < 2 -> TextKey.ASSIST_NAV_NEEDS_SPACE to ScreenLink.of(AssistScreen.SPACES)
            first == AssistScreen.BANK_SMS && ctx.platform == AssistPlatform.IOS -> TextKey.ASSIST_NAV_IOS_SMS to ScreenLink.of(AssistScreen.SMS_PASTE)
            (first == AssistScreen.ZAKAT || first == AssistScreen.ZAKAT_PAY) && !chat.zakatShown() -> TextKey.ASSIST_ZAKAT_HIDDEN to ScreenLink.of(AssistScreen.ISLAMIC_CONTENT)
            first == AssistScreen.ZAKAT_PAY && deps.sources.zakat?.openYear() == null -> TextKey.ASSIST_NAV_ZAKAT_NO_YEAR to ScreenLink.of(AssistScreen.ZAKAT)
            (first == AssistScreen.NUQOOT || first == AssistScreen.EVENT_PREP) && u.subject?.type != AssistEntityType.EVENT ->
                TextKey.ASSIST_NAV_NEEDS_EVENT to ScreenLink.of(AssistScreen.EVENTS)
            else -> null
        }
        if (special != null) return TurnOut(listOf(text(uiText(special.first), u.wire, listOf(special.second))))
        val links = u.screens.map { linkFor(it, u.subject) }
        return TurnOut(listOf(text(opener(u) + uiText(TextKey.ASSIST_NAV, quoted(links.first().label)), u.wire, links).copy(subjectId = u.subject?.id)))
    }

    private suspend fun controlReply(u: AssistUnderstanding): TurnOut = when (u.intent) {
        AssistIntent.HISTORY -> TurnOut(listOf(text(uiText(TextKey.ASSIST_HISTORY_OPEN), u.wire)), panel = AssistPanel.HISTORY, countTopic = false)
        AssistIntent.FORGET_ALL -> TurnOut(listOf(text(uiText(TextKey.ASSIST_FORGET_OPEN), u.wire)), panel = AssistPanel.MEMORY, countTopic = false)
        AssistIntent.MEMORY -> {
            val items = AssistantMemory(deps).list(ctx).items
            val t = if (items.isEmpty()) uiText(TextKey.ASSIST_MEMORY_EMPTY) else uiText(TextKey.ASSIST_MEMORY_SUMMARY, assistList(items.take(4).map { it.text }))
            TurnOut(listOf(text(t, u.wire)), panel = AssistPanel.MEMORY, countTopic = false)
        }
        AssistIntent.LEARNING_SWITCH -> {
            val on = u.learningOn ?: !chat.learning.isOn()
            val msg = bot(AssistMessageKind.CHOICE, uiText(if (on) TextKey.ASSIST_LEARNING_ON_CONFIRM else TextKey.ASSIST_LEARNING_OFF_CONFIRM), u.wire)
                .copy(options = yesNo(), choice = AssistChoiceKind.CONFIRM_LEARNING, state = CardState.PENDING, payload = mapOf("on" to on.toString()))
            TurnOut(listOf(msg), countTopic = false)
        }
        else -> TurnOut(listOf(text(uiText(TextKey.ASSIST_NEW_CONVERSATION_HINT), u.wire)), countTopic = false, startNew = true)
    }

    private fun smallTalk(u: AssistUnderstanding): AssistMessage {
        fun withOffer(first: String, key: String): AssistMessage {
            val (offer, _) = vary(AssistVariants.OFFER)
            return text("$first $offer", u.wire, opener = key)
        }
        return when (u.intent) {
            AssistIntent.GREETING -> when (detectGreeting(u.signals)) {
                GreetingKind.SALAM -> withOffer(uiText(TextKey.ASSIST_SALAM_REPLY), "GREET")
                GreetingKind.MORNING -> withOffer(uiText(TextKey.ASSIST_MORNING_REPLY), "GREET")
                GreetingKind.EVENING -> withOffer(uiText(TextKey.ASSIST_EVENING_REPLY), "GREET")
                else -> withOffer(greetingLine(ctx.hour, null) + ".", "GREET")
            }
            AssistIntent.HOW_ARE_YOU -> vary(AssistVariants.HOW_ARE_YOU).let { (t, k) -> withOffer(t, k) }
            AssistIntent.THANKS -> vary(AssistVariants.THANKS).let { (t, k) -> text(t, u.wire, opener = k) }
            AssistIntent.PRAISE -> vary(AssistVariants.PRAISE).let { (t, k) -> text(t, u.wire, opener = k) }
            AssistIntent.BYE -> if (isLateNight(ctx.hour)) text(uiText(TextKey.ASSIST_BYE_NIGHT), u.wire) else vary(AssistVariants.BYE).let { (t, k) -> text(t, u.wire, opener = k) }
            AssistIntent.WHO_ARE_YOU -> text(uiText(TextKey.ASSIST_WHO_ARE_YOU), u.wire)
            AssistIntent.HELP -> text(uiText(TextKey.ASSIST_HELP), u.wire)
            AssistIntent.ADVICE_REQUEST -> text(
                uiText(TextKey.ASSIST_ADVICE_BOUNDARY), u.wire,
                listOf(ScreenLink.of(AssistScreen.INVESTMENT), ScreenLink.of(AssistScreen.SAVINGS_CALCULATOR), ScreenLink.of(AssistScreen.ADVISOR)),
            )
            else -> text(uiText(TextKey.ASSIST_FRUSTRATION, exampleQuestion()), u.wire)
        }
    }

    /** مثال من اقتراحات الصفحة (أول اقتراح مش صوت). */
    fun exampleQuestion(): String = TAB_CHIPS[ctx.tab].orEmpty().firstOrNull { !it.voice }?.let { chipLabel(it, lex) } ?: uiText(TextKey.ASSIST_Q_SPEND_TOTAL)

    /** «مش فاهم»: جملة صريحة + لحد ٣ شاشات قريبة، والسؤال بيتحفظ في سجل الأسئلة (لو التعلم شغال). عمره ما بيدّعي إجابة. */
    private suspend fun unknownReply(u: AssistUnderstanding): TurnOut {
        val zakat = chat.zakatShown()
        val screens = nearestScreens(u.signals.normalized, ctx.tab, { s -> zakat || (s != AssistScreen.ZAKAT && s != AssistScreen.ZAKAT_PAY) })
        if (chat.learning.isOn()) {
            val (saved, drop) = recordUnknown(deps.stores.unknown.listAll(), u.signals.raw, ctx.nowIso, ctx.tab.wire, ctx.space.id)
            deps.stores.unknown.save(saved)
            if (drop.isNotEmpty()) deps.stores.unknown.remove(drop)
        }
        val (t, key) = vary(AssistVariants.UNKNOWN, clipText(u.signals.raw.trim(), 60))
        val msg = bot(AssistMessageKind.LINKS, t, ASSIST_UNKNOWN_TOPIC).copy(links = screens.map { ScreenLink.of(it) }, openerKey = key)
        return TurnOut(listOf(msg), countTopic = false)
    }

    fun yesNo() = listOf(AssistOption(OPT_YES, uiText(TextKey.ASSIST_OPT_YES)), AssistOption(OPT_NO, uiText(TextKey.ASSIST_OPT_NO)))
}

internal const val OPT_YES = "yes"
internal const val OPT_NO = "no"

/** رابط شاشة بالاسم الأساسي (معرّفه بالاسم اللي الشاشة مستنياه). */
internal fun linkFor(screen: AssistScreen, subject: AssistEntity?): ScreenLink {
    val arg = when (subject?.type) {
        AssistEntityType.PERSON -> "personId"
        AssistEntityType.MERCHANT -> "merchantId"
        AssistEntityType.WALLET -> "walletId"
        AssistEntityType.CATEGORY -> "categoryId"
        AssistEntityType.GOAL -> "goalId"
        AssistEntityType.EVENT -> "eventId"
        AssistEntityType.PROJECT -> "projectId"
        AssistEntityType.RECURRING -> "recurringId"
        AssistEntityType.ASSET -> "assetId"
        AssistEntityType.ROSCA -> "roscaId"
        AssistEntityType.PLAN -> "planId"
        null -> null
    }
    return if (arg != null && screen.needsEntity && subject != null) ScreenLink.of(screen, arg to subject.id) else ScreenLink.of(screen)
}

/** التحية بالساعة المحلية (والاسم لو موجود). */
fun greetingLine(hour: Int, name: String?): String {
    val morning = hour in 4..11
    return when {
        name.isNullOrBlank() -> uiText(if (morning) TextKey.ASSIST_GREETING_MORNING else TextKey.ASSIST_GREETING_EVENING)
        else -> uiText(if (morning) TextKey.ASSIST_GREETING_MORNING_NAMED else TextKey.ASSIST_GREETING_EVENING_NAMED, name)
    }
}

/** سطر البداية: قبل الراتب ⇒ «الراتب بعد …»؛ أول الشهر ⇒ «بدأ شهرك الجديد»؛ غير كده صيغة من التلاتة. */
internal fun introLine(ctx: AssistContext, msgs: List<AssistMessage>): String {
    if (msgs.isNotEmpty()) return ""
    val v = pickVariant(AssistVariants.INTRO_DEFAULT, null, variantDay(ctx.today), 0) ?: AssistVariants.INTRO_DEFAULT.first()
    return uiText(v.text)
}
