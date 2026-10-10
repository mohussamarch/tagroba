package app.masroufy.core

/**
 * عمر المحادثة وسجلها (OVERRIDES §78 + ردود المالك 2026-10-09 على فرع التصميم):
 * - **المحادثة بتخلص بعد ساعة من غير رسايل** ([CONVERSATION_IDLE_MS]): أول فتحة بعدها بتبدأ محادثة جديدة، واللي فاتت في السجل. زرار
 *   «محادثة جديدة» بيعمل نفس الحكاية في أي وقت. الساعة بتتحسب من آخر رسالة **متخزنة** ⇒ شغالة بين الجوالين.
 * - **السجل مع الحساب ويتزامن** (قرار المالك §78 والرد ٤) — بيفضل لحد ما المستخدم يمسحه بنفسه (المقترح — سؤال المالك ٥ لسه مفتوح).
 */
const val CONVERSATION_IDLE_MS: Long = 60L * 60 * 1000

/** المحادثة لسه شغالة؟ آخر رسالة من أقل من ساعة. وقت مش مقروء ⇒ خلصت (الأأمن: محادثة جديدة بدل ما نكمّل على حاجة قديمة). */
fun conversationAlive(c: AssistConversation, nowIso: String): Boolean {
    val now = isoInstantMillis(nowIso) ?: return false
    val last = isoInstantMillis(c.lastMessageAt) ?: return false
    return now - last < CONVERSATION_IDLE_MS
}

/**
 * المحادثة الحالية: الأحدث لو لسه شغالة، وإلا null (⇒ تبدأ جديدة، والقديمة في السجل). نفس اللحظة ⇒ الأحدث بداية، وبعدها الفاضية
 * («محادثة جديدة» اتداست بعد آخر رسالة في نفس الثانية).
 */
fun currentConversation(all: List<AssistConversation>, nowIso: String): AssistConversation? =
    all.maxWithOrNull(
        compareBy<AssistConversation> { isoInstantMillis(it.lastMessageAt) ?: Long.MIN_VALUE }
            .thenBy { isoInstantMillis(it.createdAt) ?: Long.MIN_VALUE }.thenBy { it.messageCount == 0 }.thenBy { it.id },
    )?.takeIf { conversationAlive(it, nowIso) }

/** مجموعات السجل: النهارده · امبارح · الأسبوع ده (من السبت — رد المالك في النافذة التالتة) · أقدم. */
enum class HistoryGroup { TODAY, YESTERDAY, THIS_WEEK, OLDER }

fun historyGroupOf(lastDay: IsoDate, today: IsoDate): HistoryGroup = when {
    lastDay >= today -> HistoryGroup.TODAY
    lastDay == dayBefore(today) -> HistoryGroup.YESTERDAY
    lastDay >= weekStartSaturday(today) -> HistoryGroup.THIS_WEEK
    else -> HistoryGroup.OLDER
}

/** رأس المحادثة من رسايلها (العنوان = أول كلام للمستخدم · أول رد · العدد · آخر وقت). */
fun conversationHeader(id: String, spaceId: String, messages: List<AssistMessage>, fallbackAt: String): AssistConversation {
    val sorted = messages.sortedWith(compareBy<AssistMessage> { isoInstantMillis(it.createdAt) ?: Long.MIN_VALUE }.thenBy { it.id })
    val title = sorted.firstOrNull { it.from == AssistSpeaker.ME }?.text?.let { clipText(it, CONVERSATION_TITLE_MAX) } ?: ""
    val first = sorted.firstOrNull { it.from == AssistSpeaker.BOT }?.text?.let { clipText(it, CONVERSATION_FIRST_REPLY_MAX) } ?: ""
    return AssistConversation(id, spaceId, sorted.firstOrNull()?.createdAt ?: fallbackAt, sorted.lastOrNull()?.createdAt ?: fallbackAt, title, first, sorted.size)
}

/** الرسايل بترتيبها (الوقت ثم المعرّف — المعرّفات متسلسلة). */
fun List<AssistMessage>.inOrder(): List<AssistMessage> = sortedWith(compareBy<AssistMessage> { isoInstantMillis(it.createdAt) ?: Long.MIN_VALUE }.thenBy { it.id })

/** الكارت المستني (آخر واحد) — اللي «أيوه/لا/خليها ٢٠» بيتكلموا عنه. */
fun List<AssistMessage>.pendingCard(): AssistMessage? =
    inOrder().lastOrNull { it.pending && (it.kind == AssistMessageKind.TXN_CARD || it.kind == AssistMessageKind.SPLIT_CARD) }

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
