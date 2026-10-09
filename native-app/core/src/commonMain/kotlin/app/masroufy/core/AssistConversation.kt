package app.masroufy.core

/**
 * محادثة المساعد وسجلها (OVERRIDES §78 + ردود المالك 2026-10-09 على فرع التصميم):
 * - **المحادثة بتخلص بعد ساعة من غير كلام** ([CONVERSATION_IDLE_MS]): أول فتحة بعدها بتبدأ محادثة جديدة، واللي فاتت في السجل. زرار
 *   «محادثة جديدة» بيعمل نفس الحكاية في أي وقت.
 * - **السجل مع الحساب ويتزامن** (قرار المالك §78 والرد ٤ على فرع التصميم) — مجموعة [ASSISTANT_CONVERSATIONS_GROUP].
 * - أنواع الرسالة: نص · كارت عملية للتأكيد · كارت تقسيم · سؤال المحفظة الأساسية · اختيار (شخصين بنفس الاسم · نضيف شخص؟) · روابط شاشات.
 *   **مفيش رسالة «ذكّره»** (رد المالك ٣: أي تذكير = إشعار في الجرس).
 */
const val ASSISTANT_CONVERSATIONS_GROUP = "assistantConversations"

/** ساعة سكوت ⇒ محادثة جديدة (رد المالك ١ — 2026-10-09). */
const val CONVERSATION_IDLE_MS: Long = 60L * 60 * 1000

/** حدود الحفظ (اختيار Claude — المالك يقدر يغيّرها): آخر ١٠٠ محادثة، وآخر ٢٠٠ رسالة في المحادثة (المستند الواحد في فايربيز لحد ١ ميجا). */
const val MAX_CONVERSATIONS = 100
const val MAX_MESSAGES_PER_CONVERSATION = 200

enum class AssistSpeaker(val wire: String) { ME("me"), BOT("bot");

    companion object {
        fun fromWire(wire: String): AssistSpeaker? = entries.firstOrNull { it.wire == wire }
    }
}

/** حالة الكارت: مستني · اتسجل · اتعدّل تحت (كارت جديد مكانه) · اتلغى. */
enum class CardState(val wire: String) { PENDING("pending"), SAVED("saved"), DROPPED("dropped"), CANCELLED("cancelled");

    companion object {
        fun fromWire(wire: String): CardState? = entries.firstOrNull { it.wire == wire }
    }
}

/** خيار في رسالة اختيار: [id] بيرجع للمساعد لما المستخدم يدوس. */
data class AssistOption(val id: String, val label: String)

/** نوع الاختيار — بيقول للمساعد يعمل إيه لما المستخدم يختار. */
enum class AssistChoiceKind(val wire: String) {
    /** «بتصرف عادةً منين؟» — بزرارين (حساب البنك الأساسي والكاش) — رد المالك ٢. */
    MAIN_WALLET("main_wallet"),

    /** اسمين بنفس الاسم ⇒ «تقصد مين؟». */
    PICK_PERSON("pick_person"),

    /** اسم مش في الأشخاص ⇒ «أضيف «X» إلى الأشخاص؟». */
    ADD_PERSON("add_person"),

    /** عملية بنفس المبلغ النهارده ⇒ «أقسّمها هي ولا أسجّل جديدة؟». */
    SPLIT_EXISTING("split_existing"),

    /** تأكيد قبل فعل (المحفظة الأساسية من الكلام · إيقاف التعلّم). */
    CONFIRM_ACTION("confirm_action"),
    ;

    companion object {
        fun fromWire(wire: String): AssistChoiceKind? = entries.firstOrNull { it.wire == wire }
    }
}

data class AssistChoice(
    val kind: AssistChoiceKind,
    val options: List<AssistOption>,
    /** اللي المساعد محتاجه يكمّل بعد الاختيار (معرّف كارت · نص الطلب الأصلي …). */
    val payload: Map<String, String> = emptyMap(),
    val picked: String? = null,
)

data class AssistMessage(
    val id: String,
    val from: AssistSpeaker,
    /** وقت ISO كامل. */
    val at: String,
    val text: String,
    /** النية اللي اتفهمت (رسائل المستخدم) — للسجل والتعلّم. */
    val intent: String? = null,
    val links: List<ScreenLink> = emptyList(),
    val txn: TxnDraft? = null,
    val split: SplitDraft? = null,
    val choice: AssistChoice? = null,
    val state: CardState? = null,
    /** العملية اللي اتسجلت من الكارت. */
    val savedTransactionId: Id? = null,
) {
    val pending: Boolean get() = state == CardState.PENDING
}

data class AssistConversation(
    val id: String,
    /** البلد اللي المحادثة بدأت فيها (الأرقام بعملتها). */
    val spaceId: String,
    val startedAt: String,
    val lastAt: String,
    val messages: List<AssistMessage>,
) {
    /** العنوان في السجل = أول رسالة من المستخدم (مقصوصة). */
    val title: String get() = messages.firstOrNull { it.from == AssistSpeaker.ME }?.text?.let { clip(it, 60) } ?: ""

    /** أول سطر في السجل = أول رد. */
    val firstLine: String get() = messages.firstOrNull { it.from == AssistSpeaker.BOT }?.text?.let { clip(it, 120) } ?: ""

    /** الكارت المستني (آخر واحد) — اللي «أيوه/لا/خليها ٢٠» بيتكلموا عنه. */
    val pendingCard: AssistMessage? get() = messages.lastOrNull { it.pending && (it.txn != null || it.split != null) }
}

private fun clip(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1) + "…"

/** المحادثة لسه شغالة؟ آخر كلام من أقل من ساعة. وقت مش مقروء ⇒ خلصت (الأأمن: محادثة جديدة بدل ما نكمّل على حاجة قديمة). */
fun conversationAlive(c: AssistConversation, nowIso: String): Boolean {
    val now = isoInstantMillis(nowIso) ?: return false
    val last = isoInstantMillis(c.lastAt) ?: return false
    return now - last <= CONVERSATION_IDLE_MS
}

/** المحادثة الحالية: الأحدث لو لسه شغالة، وإلا null (⇒ تبدأ جديدة، والقديمة في السجل). */
fun currentConversation(all: List<AssistConversation>, nowIso: String): AssistConversation? =
    all.maxByOrNull { isoInstantMillis(it.lastAt) ?: Long.MIN_VALUE }?.takeIf { conversationAlive(it, nowIso) }

/** مجموعات السجل: النهارده · امبارح · الأسبوع ده (من السبت) · أقدم — بتاريخ البلد ([localDate] بيحوّل الوقت لتاريخ محلي). */
enum class HistoryGroup { TODAY, YESTERDAY, THIS_WEEK, OLDER }

fun historyGroupOf(lastDay: IsoDate, today: IsoDate): HistoryGroup = when {
    lastDay >= today -> HistoryGroup.TODAY
    lastDay == dayBefore(today) -> HistoryGroup.YESTERDAY
    lastDay >= weekStartSaturday(today) -> HistoryGroup.THIS_WEEK
    else -> HistoryGroup.OLDER
}

/** قص المحادثة لآخر [MAX_MESSAGES_PER_CONVERSATION] رسالة (الأقدم بيتشال). */
fun AssistConversation.trimmed(): AssistConversation =
    if (messages.size <= MAX_MESSAGES_PER_CONVERSATION) this else copy(messages = messages.takeLast(MAX_MESSAGES_PER_CONVERSATION))

private val INSTANT = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3})\d*)?)?(Z|[+-]\d{2}:\d{2})""")

/** وقت ISO بالمللي (زي `Date.parse` لشكل `toISOString` والمناطق الزمنية) — غير كده null. */
fun isoInstantMillis(iso: String): Long? {
    val m = INSTANT.matchEntire(iso) ?: return null
    val g = m.groupValues
    val day = runCatching { toDayNumber(DateParts(g[1].toInt(), g[2].toInt(), g[3].toInt())).toLong() }.getOrNull() ?: return null
    val millis = g[7].ifEmpty { "0" }.padEnd(3, '0').toLong()
    val offset = if (g[8] == "Z") 0 else (g[8].substring(1, 3).toInt() * 60 + g[8].substring(4, 6).toInt()) * (if (g[8][0] == '-') -1 else 1)
    val seconds = day * 86_400 + g[4].toInt() * 3_600 + g[5].toInt() * 60 + g[6].ifEmpty { "0" }.toInt() - offset * 60L
    return seconds * 1_000 + millis
}
