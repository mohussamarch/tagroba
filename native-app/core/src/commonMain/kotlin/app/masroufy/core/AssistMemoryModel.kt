package app.masroufy.core

/**
 * «اللي اتعلمته عنك» والاقتراحات تحت مستطيل الكتابة (§78-٤ و§78-٥ + مطابقة النموذج على فرع التصميم). نوعين في «اللي اتعلمته عنك»،
 * وكل واحد بيتمسح لوحده بـ«×» و«امسح اللي اتعلمه» بيمسح الكل:
 * - **حاجات أكدتها بنفسك** في شاشاتها (يوم الراتب ومصدره · المحل الأكتر · اللي بتقسّم معاهم · الخطة اللي عليها النجمة) — بتتحسب من
 *   بياناتك كل مرة **ومش بتتخزن**؛ المتخزن بس علامة «امسح» ([ForgottenMark]).
 * - **المواضيع اللي بتسأل عنها كتير** بعدد المرات ([AssistTopic]).
 * مفتاح «يتعلّم من أسئلتي» واقف ⇒ ولا موضوع بيتعدّ، ومفيش اقتراحات متعلّمة (اقتراحات الصفحة بس).
 */

/** الموضوع بيبقى اقتراح من مرتين (§78-٥ والنموذج: `topics[t] >= 2`). */
const val FREQUENT_TOPIC_MIN = 2

/** أقصى اقتراحات متعلّمة ٣، وكل الاقتراحات تحت مستطيل الكتابة ٦ (نفس النموذج). */
const val MAX_LEARNED_CHIPS = 3
const val MAX_CHIPS = 6

/** المواضيع المتكررة بالترتيب: الأكتر سؤالًا، وبعده الأحدث. */
fun frequentTopics(topics: List<AssistTopic>): List<AssistTopic> =
    topics.filter { it.askCount >= FREQUENT_TOPIC_MIN }
        .sortedWith(compareByDescending<AssistTopic> { it.askCount }.thenByDescending { isoInstantMillis(it.lastAskedAt) ?: Long.MIN_VALUE }.thenBy { it.key })

/** الموضوع اتسأل تاني ⇒ العدد +١. */
fun countTopic(existing: AssistTopic?, topic: String, subjectId: Id?, nowIso: String): AssistTopic =
    existing?.copy(askCount = existing.askCount + 1, lastAskedAt = nowIso) ?: AssistTopic(topic, subjectId, 1, nowIso)

/**
 * أسئلة المتابعة بعد أول سؤال (النموذج: `NEXT` — اتنين لكل موضوع، التاني قريب منه). الاسم الأساسي بيتنقل لو النية التانية بتاخد نفس
 * النوع (التصنيف ⇒ سقف نفس التصنيف).
 */
val FOLLOW_UPS: Map<AssistIntent, List<AssistIntent>> = mapOf(
    AssistIntent.SPEND_TOTAL to listOf(AssistIntent.SPEND_BIGGEST, AssistIntent.REMAINING),
    AssistIntent.SPEND_CATEGORY to listOf(AssistIntent.CATEGORY_BUDGET, AssistIntent.SPEND_COMPARE),
    AssistIntent.SPEND_MERCHANT to listOf(AssistIntent.LAST_AT_MERCHANT, AssistIntent.SPEND_TOTAL),
    AssistIntent.SPEND_PERSON to listOf(AssistIntent.PERSON_BALANCE, AssistIntent.OWED_TO_ME),
    AssistIntent.SPEND_BIGGEST to listOf(AssistIntent.SPEND_COMPARE, AssistIntent.BUDGET_STATUS),
    AssistIntent.SPEND_COMPARE to listOf(AssistIntent.SPEND_BIGGEST, AssistIntent.FORECAST),
    AssistIntent.INCOME to listOf(AssistIntent.SPEND_TOTAL, AssistIntent.NEXT_SALARY),
    AssistIntent.REMAINING to listOf(AssistIntent.DAILY_ALLOWANCE, AssistIntent.BUDGET_STATUS),
    AssistIntent.DAILY_ALLOWANCE to listOf(AssistIntent.REMAINING, AssistIntent.FORECAST),
    AssistIntent.FORECAST to listOf(AssistIntent.REMAINING, AssistIntent.SPEND_BIGGEST),
    AssistIntent.BUDGET_STATUS to listOf(AssistIntent.REMAINING, AssistIntent.SPEND_BIGGEST),
    AssistIntent.CATEGORY_BUDGET to listOf(AssistIntent.SPEND_CATEGORY, AssistIntent.BUDGET_STATUS),
    AssistIntent.CASH_ON_HAND to listOf(AssistIntent.ON_HAND, AssistIntent.SPEND_TOTAL),
    AssistIntent.ON_HAND to listOf(AssistIntent.CASH_ON_HAND, AssistIntent.REMAINING),
    AssistIntent.OWED_TO_ME to listOf(AssistIntent.DEBTS_OVERDUE, AssistIntent.I_OWE),
    AssistIntent.I_OWE to listOf(AssistIntent.OWED_TO_ME, AssistIntent.DUES_UPCOMING),
    AssistIntent.PERSON_BALANCE to listOf(AssistIntent.OWED_TO_ME, AssistIntent.DEBTS_OVERDUE),
    AssistIntent.DEBTS_OVERDUE to listOf(AssistIntent.OWED_TO_ME, AssistIntent.I_OWE),
    AssistIntent.NEXT_SALARY to listOf(AssistIntent.DUES_UPCOMING, AssistIntent.REMAINING),
    AssistIntent.SALARY_AMOUNT to listOf(AssistIntent.NEXT_SALARY, AssistIntent.INCOME),
    AssistIntent.LAST_AT_MERCHANT to listOf(AssistIntent.SPEND_MERCHANT, AssistIntent.SPEND_TOTAL),
    AssistIntent.GOAL_PROGRESS to listOf(AssistIntent.REMAINING, AssistIntent.SPEND_BIGGEST),
    AssistIntent.DUES_UPCOMING to listOf(AssistIntent.NEXT_SALARY, AssistIntent.BILLS),
    AssistIntent.BILLS to listOf(AssistIntent.DUES_UPCOMING, AssistIntent.REMAINING),
    AssistIntent.PENDING_REVIEW to listOf(AssistIntent.SPEND_TOTAL, AssistIntent.REMAINING),
    AssistIntent.ASSETS to listOf(AssistIntent.ZAKAT, AssistIntent.GOAL_PROGRESS),
    AssistIntent.ZAKAT to listOf(AssistIntent.ASSETS, AssistIntent.DUES_UPCOMING),
    AssistIntent.QUICK_ADD to listOf(AssistIntent.SPEND_TOTAL, AssistIntent.REMAINING),
    AssistIntent.SPLIT to listOf(AssistIntent.OWED_TO_ME, AssistIntent.DEBTS_OVERDUE),
)

/** اقتراح: موضوع (نية + اسم أساسي) أو زرار «أضف عملية بصوتي». الشاشة بتبعته زي ما هو لـ`AssistantChat.ask`. */
data class ChipRef(val topic: String, val subjectId: Id? = null, val voice: Boolean = false) {
    companion object {
        val VOICE = ChipRef("voice", voice = true)

        fun of(intent: AssistIntent, subjectId: Id? = null) = ChipRef(intent.wire, subjectId)

        fun nav(screen: AssistScreen) = ChipRef(screen.navWire)
    }
}

data class Chip(val ref: ChipRef, val label: String, val learned: Boolean)

/** شريط الاقتراحات: العنوان («بتسأل عنها كتير» · «اقتراحات من أسئلتك» · «اقتراحات لصفحة «X»») والاقتراحات. */
data class ChipBar(val label: String?, val chips: List<Chip>)

/** اقتراحات كل صفحة (النموذج: `BY_CTX`) — الزكاة بتتشال لو المحتوى الإسلامي مخفي. */
val TAB_CHIPS: Map<AssistTab, List<ChipRef>> = mapOf(
    AssistTab.HOME to listOf(ChipRef.of(AssistIntent.SPEND_TOTAL), ChipRef.VOICE, ChipRef.of(AssistIntent.NEXT_SALARY), ChipRef.of(AssistIntent.REMAINING), ChipRef.of(AssistIntent.BUDGET_STATUS)),
    AssistTab.OPERATIONS to listOf(ChipRef.VOICE, ChipRef.of(AssistIntent.SPEND_TOTAL), ChipRef.of(AssistIntent.PENDING_REVIEW), ChipRef.of(AssistIntent.SPEND_BIGGEST)),
    AssistTab.PEOPLE to listOf(ChipRef.of(AssistIntent.OWED_TO_ME), ChipRef.of(AssistIntent.I_OWE), ChipRef.VOICE, ChipRef.of(AssistIntent.OCCASIONS)),
    AssistTab.INVESTMENT to listOf(ChipRef.of(AssistIntent.BUDGET_STATUS), ChipRef.nav(AssistScreen.ZAKAT), ChipRef.of(AssistIntent.GOAL_PROGRESS), ChipRef.VOICE),
    AssistTab.MORE to listOf(ChipRef.nav(AssistScreen.LOCK), ChipRef.nav(AssistScreen.ZAKAT), ChipRef.VOICE, ChipRef.nav(AssistScreen.BACKUP)),
)

/** نوع «اللي اتعلمته عنك»: حاجة أكدتها بنفسك · موضوع بتسأل عنه. */
enum class LearnedKind { FACT, TOPIC }

data class LearnedItem(
    /** بيتبعت لـ«امسح دي». */
    val key: String,
    val kind: LearnedKind,
    val text: String,
    /** «من مصادر الدخل» · «سألت ٣ مرات» … */
    val sourceLabel: String,
    val askCount: Int? = null,
)

/** مفاتيح الحاجات اللي أكدتها (فيها قيمة الحاجة — لو اتغيرت بترجع تظهر). */
object FactKeys {
    fun salary(sourceId: Id, day: Int) = "fact:salary:$sourceId:$day"

    fun store(merchantId: Id) = "fact:store:$merchantId"

    fun partner(personId: Id) = "fact:partner:$personId"

    fun goal(goalId: Id) = "fact:goal:$goalId"

    fun topic(key: String) = "topic:$key"

    fun card(key: String) = "card:$key"
}

/** المحل الأكتر: أكتر محل في عمليات مصروف **مؤكدة** آخر ٩٠ يوم، ٣ مرات على الأقل (التصميم). */
const val TOP_STORE_DAYS = 90
const val TOP_STORE_MIN = 3

/** اللي بتقسّم معاهم: شخص ليه تخصيصين أو أكتر آخر ٩٠ يوم — أول اتنين. */
const val SPLIT_PARTNER_MIN = 2
const val SPLIT_PARTNERS_MAX = 2

/** أقصى مواضيع في «اللي اتعلمته عنك». */
const val MAX_LEARNED_TOPICS = 3
