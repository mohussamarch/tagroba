package app.masroufy.core

/**
 * «اللي اتعلمته عنك» والاقتراحات (§78-٤ و§78-٥ + مطابقة النموذج على فرع التصميم) — كله **على الحساب ويتزامن وفي النسخة الشاملة** (قرار
 * المالك §78 «الذاكرة والأسئلة»). نوعين، وكل واحد بيتمسح لوحده:
 * - **حاجات أكدتها بنفسك** (يوم الراتب ومصدره · المحل الأكتر · اللي بتقسّم معاهم · الخطة اللي عليها النجمة) — بتتحسب من بياناتك
 *   المؤكدة كل مرة، **ومش بتتخزن**؛ المتخزن بس «امسح دي» ([ForgottenFact]).
 * - **المواضيع اللي بتسأل عنها كتير** بعدد المرات ([AssistTopic]) — مرتين أو أكتر ⇒ اقتراح أول ما المحادثة تفتح تحت «بتسأل عنها كتير».
 * مفتاح «يتعلّم من أسئلتي» ([AssistPrefs.learning]): واقف ⇒ ولا موضوع بيتعدّ، والاقتراحات حسب الصفحة بس.
 */
const val ASSISTANT_TOPICS_GROUP = "assistantTopics"
const val ASSISTANT_PREFS_GROUP = "assistantPrefs"
const val ASSISTANT_FORGOTTEN_GROUP = "assistantForgotten"
const val ASSISTANT_UNKNOWN_GROUP = "assistantUnknown"

/** معرّف مستند الإعدادات (مستند واحد للحساب). */
const val ASSISTANT_PREFS_ID = "main"

/** موضوع بيتسأل عنه: [key] = النية + الاسم الأساسي («data.spend.category:category:cat-…»). */
data class AssistTopic(
    val key: String,
    val intent: String,
    val entityType: AssistEntityType? = null,
    val entityId: Id? = null,
    /** اسم الاسم الأساسي ساعة السؤال (للاقتراح «كم صرفت على «القهوة»؟»). */
    val entityName: String? = null,
    val count: Int,
    val lastAt: String,
)

data class AssistPrefs(val learning: Boolean = true, val updatedAt: String = "")

/** «امسح دي» على حاجة من اللي اتعلمها — المفتاح فيه قيمة الحاجة (المحل الأكتر لو اتغير بيرجع يظهر لأنه حاجة جديدة). */
data class ForgottenFact(val key: String, val at: String)

/**
 * سؤال ما اتفهمش (§78-٧ «السؤال يتحفظ عشان نعلّمه بعدين»). النص زي ما اتكتب بعد قص أي رقم طويل (CLAUDE.md #11)، وعدد المرات.
 * المعرّف = بصمة الكلام بعد التوحيد (نفس السؤال بصيغتين كتابة = سطر واحد).
 */
data class UnknownQuestion(val id: String, val text: String, val count: Int, val firstAt: String, val lastAt: String, val tab: AssistTab? = null)

/** الموضوع بيظهر في «بتسأل عنها كتير» من مرتين (§78-٥ والنموذج: `topics[t] >= 2`). */
const val FREQUENT_TOPIC_MIN = 2

/** حد اقتراحات الأسئلة المتكررة أول المحادثة ٣، وكل الاقتراحات تحت مستطيل الكتابة ٦ (نفس النموذج). */
const val MAX_LEARNED_CHIPS = 3
const val MAX_CHIPS = 6

/** الحاجة اللي اتعلمها في كارت «اللي اتعلمته عنك». */
enum class MemoryItemKind { FACT, TOPIC }

data class MemoryItem(
    /** بيتبعت لـ«امسح دي». */
    val key: String,
    val kind: MemoryItemKind,
    val text: String,
    /** «من مصادر الدخل» · «سألت ٣ مرات» … */
    val source: String,
)

/** المواضيع المتكررة بالترتيب: الأكتر سؤالًا، وبعده الأحدث. */
fun frequentTopics(topics: List<AssistTopic>): List<AssistTopic> =
    topics.filter { it.count >= FREQUENT_TOPIC_MIN }.sortedWith(compareByDescending<AssistTopic> { it.count }.thenByDescending { it.lastAt })

/** مفتاح «امسح دي» للموضوع. */
fun topicMemoryKey(topicKey: String) = "topic:$topicKey"

/**
 * أسئلة المتابعة بعد أول سؤال (النموذج: `NEXT` — «القهوة بتأخّر هدف السفر مثلًا»): النية ⇒ نوايا قريبة منها. الاسم الأساسي بيتنقل لو النية
 * التانية بتاخد نفس النوع (التصنيف ⇒ سقف نفس التصنيف).
 */
val FOLLOW_UPS: Map<AssistIntent, List<AssistIntent>> = mapOf(
    AssistIntent.SPEND_TOTAL to listOf(AssistIntent.SPEND_BIGGEST, AssistIntent.REMAINING),
    AssistIntent.SPEND_CATEGORY to listOf(AssistIntent.CATEGORY_BUDGET, AssistIntent.SPEND_COMPARE),
    AssistIntent.SPEND_MERCHANT to listOf(AssistIntent.LAST_AT_MERCHANT, AssistIntent.SPEND_TOTAL),
    AssistIntent.SPEND_PERSON to listOf(AssistIntent.PERSON_BALANCE, AssistIntent.OWED_TO_ME),
    AssistIntent.SPEND_BIGGEST to listOf(AssistIntent.SPEND_COMPARE, AssistIntent.BUDGET_STATUS),
    AssistIntent.SPEND_COMPARE to listOf(AssistIntent.SPEND_BIGGEST, AssistIntent.FORECAST),
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
    AssistIntent.INCOME to listOf(AssistIntent.SPEND_TOTAL, AssistIntent.NEXT_SALARY),
    AssistIntent.LAST_AT_MERCHANT to listOf(AssistIntent.SPEND_MERCHANT, AssistIntent.SPEND_TOTAL),
    AssistIntent.GOAL_PROGRESS to listOf(AssistIntent.REMAINING, AssistIntent.SPEND_BIGGEST),
    AssistIntent.DUES_UPCOMING to listOf(AssistIntent.NEXT_SALARY, AssistIntent.BILLS),
    AssistIntent.BILLS to listOf(AssistIntent.DUES_UPCOMING, AssistIntent.REMAINING),
    AssistIntent.QUICK_ADD to listOf(AssistIntent.SPEND_TOTAL, AssistIntent.REMAINING),
    AssistIntent.SPLIT to listOf(AssistIntent.OWED_TO_ME, AssistIntent.DEBTS_OVERDUE),
)

/** اقتراحات كل صفحة (النموذج: `BY_CTX`) — «VOICE» = زرار «أضف عملية بصوتي». */
enum class ChipKind { ASK, VOICE }

data class PageChip(val kind: ChipKind, val intent: AssistIntent? = null, val screen: AssistScreen? = null)

val TAB_CHIPS: Map<AssistTab, List<PageChip>> = mapOf(
    AssistTab.HOME to listOf(PageChip(ChipKind.ASK, AssistIntent.SPEND_TOTAL), PageChip(ChipKind.VOICE), PageChip(ChipKind.ASK, AssistIntent.NEXT_SALARY), PageChip(ChipKind.ASK, AssistIntent.SPLIT), PageChip(ChipKind.ASK, AssistIntent.BUDGET_STATUS)),
    AssistTab.OPERATIONS to listOf(PageChip(ChipKind.VOICE), PageChip(ChipKind.ASK, AssistIntent.SPEND_TOTAL), PageChip(ChipKind.ASK, AssistIntent.PENDING_REVIEW), PageChip(ChipKind.ASK, AssistIntent.SPLIT)),
    AssistTab.PEOPLE to listOf(PageChip(ChipKind.ASK, AssistIntent.OWED_TO_ME), PageChip(ChipKind.ASK, AssistIntent.SPLIT), PageChip(ChipKind.VOICE)),
    AssistTab.INVESTMENT to listOf(PageChip(ChipKind.ASK, AssistIntent.BUDGET_STATUS), PageChip(ChipKind.ASK, AssistIntent.NAV, AssistScreen.ZAKAT), PageChip(ChipKind.ASK, AssistIntent.SPEND_TOTAL), PageChip(ChipKind.VOICE)),
    AssistTab.MORE to listOf(PageChip(ChipKind.ASK, AssistIntent.NAV, AssistScreen.LOCK), PageChip(ChipKind.ASK, AssistIntent.NAV, AssistScreen.ZAKAT), PageChip(ChipKind.VOICE), PageChip(ChipKind.ASK, AssistIntent.SPLIT)),
)
