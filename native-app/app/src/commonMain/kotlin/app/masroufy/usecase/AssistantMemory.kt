package app.masroufy.usecase

import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistMessage
import app.masroufy.core.ChipRef
import app.masroufy.core.Direction
import app.masroufy.core.FactKeys
import app.masroufy.core.ForgottenMark
import app.masroufy.core.HistoryGroup
import app.masroufy.core.IsoDate
import app.masroufy.core.LearnedItem
import app.masroufy.core.LearnedKind
import app.masroufy.core.MAX_LEARNED_TOPICS
import app.masroufy.core.SPLIT_PARTNERS_MAX
import app.masroufy.core.SPLIT_PARTNER_MIN
import app.masroufy.core.TOP_STORE_DAYS
import app.masroufy.core.TOP_STORE_MIN
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.UnknownQuestion
import app.masroufy.core.shiftDays
import app.masroufy.core.frequentTopics
import app.masroufy.core.historyGroupOf
import app.masroufy.core.inOrder
import app.masroufy.core.isoInstantMillis
import app.masroufy.core.sourcesActiveOn
import app.masroufy.core.unknownAsText

/** «اللي اتعلمته عنك»: العناصر بنوعيها + حالة مفتاح التعلم. */
data class MemoryView(val items: List<LearnedItem>, val learningOn: Boolean)

/**
 * «اللي اتعلمته عنك» (§78-٤ + النموذج): نوعين، كل واحد بيتمسح لوحده و«امسح الكل» ومفتاح تشغيل/إيقاف:
 * - **حاجات أكدتها بنفسك** — بتتحسب كل مرة من بياناتك المؤكدة (يوم الراتب ومصدره من مصادر الدخل · أكتر محل في عمليات مؤكدة ٩٠ يوم ·
 *   اللي بتقسّم معاهم · الخطة اللي عليها النجمة). **مش من أسئلتك أبدًا**؛ المتخزن بس علامة «امسح».
 * - **مواضيع بتسأل عنها كتير** بعدد المرات (مرتين أو أكتر).
 */
class AssistantMemory(private val deps: AssistantDeps) {
    private val st = deps.stores
    private val learning = AssistantLearning(st.settings, deps.clock)

    suspend fun list(ctx: AssistContext): MemoryView {
        val forgotten = st.forgotten.listAll().map { it.factKey }.toSet()
        val facts = facts(ctx.today).filter { it.key !in forgotten }
        val lex = deps.lexicon.load()
        val topics = frequentTopics(st.topics.listAll()).take(MAX_LEARNED_TOPICS).map { t ->
            LearnedItem(FactKeys.topic(t.key), LearnedKind.TOPIC, chipLabel(ChipRef(t.topic, t.subjectId), lex), uiText(TextKey.ASSIST_FACT_ASKED, assistTimes(t.askCount)), t.askCount)
        }
        return MemoryView(facts + topics, learning.isOn())
    }

    /** الحاجات المؤكدة من البيانات (بمفاتيح فيها القيمة — لو اتغيرت بترجع تظهر). */
    suspend fun facts(today: IsoDate): List<LearnedItem> {
        val out = mutableListOf<LearnedItem>()
        val src = deps.sources
        sourcesActiveOn(src.incomeSources?.list().orEmpty(), today).firstOrNull { isWage(it) && it.expectedDayOfMonth != null }?.let { s ->
            out += LearnedItem(FactKeys.salary(s.id, s.expectedDayOfMonth!!), LearnedKind.FACT, uiText(TextKey.ASSIST_FACT_SALARY, s.expectedDayOfMonth.toString(), s.name), uiText(TextKey.ASSIST_FACT_FROM_INCOME))
        }
        val lex = deps.lexicon.load()
        val rows = src.txns.listByDateRange(shiftDays(today, -TOP_STORE_DAYS), today)
        rows.filter { it.observedDirection == Direction.OUT && it.economicKindConfirmed && it.merchantId != null }.groupingBy { it.merchantId!! }.eachCount()
            .filter { it.value >= TOP_STORE_MIN }.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.let { (id, n) ->
                lex.merchants.firstOrNull { it.id == id }?.let { m ->
                    out += LearnedItem(FactKeys.store(id), LearnedKind.FACT, uiText(TextKey.ASSIST_FACT_STORE, m.displayName, assistTimes(n)), uiText(TextKey.ASSIST_FACT_FROM_TXNS))
                }
            }
        deps.allocations?.listByTransactionIds(rows.map { it.id })?.groupingBy { it.personId }?.eachCount()?.filter { it.value >= SPLIT_PARTNER_MIN }
            ?.entries?.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.take(SPLIT_PARTNERS_MAX)?.forEach { (id, _) ->
                lex.people.firstOrNull { it.id == id }?.let { p ->
                    out += LearnedItem(FactKeys.partner(id), LearnedKind.FACT, uiText(TextKey.ASSIST_FACT_PARTNER, p.name), uiText(TextKey.ASSIST_FACT_FROM_SPLITS))
                }
            }
        src.goals?.load(today)?.firstOrNull { it.goal.starred }?.let { g ->
            out += LearnedItem(FactKeys.goal(g.goal.id), LearnedKind.FACT, uiText(TextKey.ASSIST_FACT_GOAL, g.goal.name), uiText(TextKey.ASSIST_FACT_FROM_GOALS))
        }
        return out
    }

    /** «×» على عنصر: الموضوع بيتشال (عدّه بيبدأ من الأول)؛ الحاجة المؤكدة بتتعلّم «اتمسحت» (البيانات نفسها ما بتتلمسش). */
    suspend fun forget(key: String) {
        if (key.startsWith("topic:")) st.topics.remove(listOf(key.removePrefix("topic:"))) else st.forgotten.save(ForgottenMark(key, deps.clock.nowIso()))
    }

    /** «امسح اللي اتعلمه»: كل المواضيع + علامة على كل حاجة مؤكدة ظاهرة دلوقتي (علامات كروت البداية ما بتتلمسش). */
    suspend fun clearAll(today: IsoDate) {
        st.topics.remove(st.topics.listAll().map { it.key })
        facts(today).forEach { st.forgotten.save(ForgottenMark(it.key, deps.clock.nowIso())) }
    }

    suspend fun setLearning(on: Boolean) = learning.set(on)
}

/** سطر في السجل: المحادثة + مجموعتها (النهارده · امبارح · الأسبوع ده · أقدم). */
data class HistoryRow(val conversation: AssistConversation, val group: HistoryGroup)

/** سجل المحادثات (على الحساب ويتزامن — رد المالك ٤). المحادثة الفاضية («محادثة جديدة» من غير رسايل) ما بتظهرش. */
class AssistantHistory(private val deps: AssistantDeps) {
    private val st = deps.stores

    suspend fun list(today: IsoDate): List<HistoryRow> = st.conversations.listAll().filter { it.messageCount > 0 }
        .sortedWith(compareByDescending<AssistConversation> { isoInstantMillis(it.lastMessageAt) ?: Long.MIN_VALUE }.thenBy { it.id })
        .map { HistoryRow(it, historyGroupOf(it.lastMessageAt.take(10), today)) }

    suspend fun messages(conversationId: String): List<AssistMessage> = st.messages.listByConversation(conversationId).inOrder()

    suspend fun delete(conversationId: String) {
        st.messages.removeByConversations(listOf(conversationId))
        st.conversations.remove(listOf(conversationId))
    }

    suspend fun clear() {
        val ids = st.conversations.listAll().map { it.id }
        st.messages.removeByConversations(ids)
        st.conversations.remove(ids)
    }
}

/** الأسئلة اللي ما اتفهمتش (§78-٧): للمالك يشوفها وينسخها بنفسه — مفيش حاجة بتتبعت لوحدها. */
class AssistantUnknownLog(private val deps: AssistantDeps) {
    suspend fun list(): List<UnknownQuestion> = deps.stores.unknown.listAll().sortedByDescending { isoInstantMillis(it.lastAskedAt) ?: Long.MIN_VALUE }

    /** «أسئلة لم أفهمها بعد (n)» (§79.2-10): آخر [limit] بس، والعدد كله من [list]. */
    suspend fun recent(limit: Int = UNKNOWN_SHOWN): List<UnknownQuestion> = list().take(limit)

    suspend fun asText(): String = unknownAsText(deps.stores.unknown.listAll())

    /** «×» على سؤال — بيرجّع السؤال عشان «تراجع» (٤ ثواني في الشاشة) يرجّعه زي ما كان. */
    suspend fun remove(id: String): UnknownQuestion? {
        val q = deps.stores.unknown.listAll().firstOrNull { it.id == id } ?: return null
        deps.stores.unknown.remove(listOf(id))
        return q
    }

    suspend fun restore(question: UnknownQuestion) = deps.stores.unknown.save(question)

    suspend fun clear() = deps.stores.unknown.remove(deps.stores.unknown.listAll().map { it.id })
}

/** عدد الأسئلة اللي ما اتفهمتش الظاهرة في «اللي اتعلمته عنك» (المالك: آخر ٥). */
const val UNKNOWN_SHOWN = 5
