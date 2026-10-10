package app.masroufy.core

/**
 * شكل رسايل المساعد «مصروفي» (OVERRIDES §78 + ردود المالك 2026-10-09 على فرع التصميم) — زي ما بتتخزن بالظبط:
 * - **المحادثة** ([AssistConversation]) رأس بس (البلد · أول وآخر رسالة · العنوان · أول رد · العدد)، و**كل رسالة مستند لوحدها**
 *   ([AssistMessage]) في مجموعة مسطحة بمعرّف المحادثة — جوالين في نفس الوقت ما بيكتبوش فوق بعض، والنسخة الشاملة تفضل مسطحة.
 * - الرد بيتخزن **زي ما اتعرض** (النص والمبالغ ساعتها): السجل عمره ما بيعيد حساب رقم ولا بيتكتب تاني لو اللغة اتغيرت.
 * - أنواع الرسالة: نص · روابط شاشات · كارت عملية للتأكيد · كارت تقسيم · سؤال «بتصرف عادةً منين؟» · اختيار. **مفيش رسالة «ذكّره»**
 *   (رد المالك ٣: أي تذكير = إشعار في الجرس).
 */
enum class AssistSpeaker(val wire: String) { ME("me"), BOT("bot");

    companion object {
        fun fromWire(wire: String): AssistSpeaker? = entries.firstOrNull { it.wire == wire }
    }
}

enum class AssistMessageKind(val wire: String) {
    TEXT("text"),

    /** رد التنقل ورد «مش فاهم»: جملة + زراير شاشات. */
    LINKS("links"),
    TXN_CARD("txnCard"),
    SPLIT_CARD("splitCard"),

    /** «بتصرف عادةً منين؟» بزرارين (رد المالك ٢) — الكارت المستني جواها لحد ما يختار. */
    WALLET_PICK("walletPick"),
    CHOICE("choice"),
    ;

    companion object {
        fun fromWire(wire: String): AssistMessageKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** حالة الكارت: مستني · اتعمل · اتعدّل تحت (كارت جديد مكانه) · اتلغى. */
enum class CardState(val wire: String) { PENDING("pending"), DONE("done"), DROPPED("dropped"), CANCELLED("cancelled");

    companion object {
        fun fromWire(wire: String): CardState? = entries.firstOrNull { it.wire == wire }
    }
}

/** نوع الاختيار — بيقول للمساعد يكمّل إزاي لما المستخدم يدوس. */
enum class AssistChoiceKind(val wire: String) {
    /** اسمين بنفس الاسم (شخصين · محفظتين) ⇒ «تقصد مين؟» — ما بنخمّنش. */
    PICK_SUBJECT("pickSubject"),

    /** اسم مش في الأشخاص (في التقسيم) ⇒ «أضيف «X» إلى الأشخاص؟». */
    ADD_PERSON("addPerson"),

    /** عملية بنفس المبلغ اتسجلت النهارده ⇒ «أقسّمها هي ولا أسجّل جديدة؟». */
    SPLIT_EXISTING("splitExisting"),

    /** «خلّي الكاش هي الأساسية» من الكلام ⇒ تأكيد قبل الحفظ. */
    CONFIRM_MAIN_WALLET("confirmMainWallet"),

    /** «بطّل تتعلم من أسئلتي» ⇒ تأكيد قبل ما المفتاح يتغير. */
    CONFIRM_LEARNING("confirmLearning"),
    ;

    companion object {
        fun fromWire(wire: String): AssistChoiceKind? = entries.firstOrNull { it.wire == wire }
    }
}

data class AssistOption(val id: String, val label: String)

/** كارت عملية (إضافة بالكتابة §78-٣). الأسامي زي ما اتعرضت. `transactionId` بيتملى بعد «احفظ». */
data class TxnDraft(
    val title: String,
    /** موجب دايمًا، بالوحدة الصغرى. */
    val amountMinor: Halalas,
    val currency: Currency,
    val occurredOn: IsoDate,
    /** null = لسه مفيش محفظة أساسية ⇒ «بتصرف عادةً منين؟» الأول. */
    val walletId: Id?,
    val walletName: String?,
    val categoryId: Id?,
    val categoryName: String?,
    val merchantId: Id? = null,
    val merchantName: String? = null,
    /** شراء (الافتراضي) أو رسوم («رسوم التحويل ١٥»). */
    val kind: EconomicKind = EconomicKind.PURCHASE,
    /** الفاتورة الدورية اللي الكلام بيتكلم عنها. */
    val recurringItemId: Id? = null,
    /** عملية شبهها اتسجلت نفس اليوم ⇒ تحذير على الكارت بس (سؤال المالك ٨ — المقترح). */
    val similarTransactionId: Id? = null,
    /** المستخدم غيّر التصنيف على الكارت ⇒ بيتحفظ للمحل لما يأكد (§75-16). */
    val categoryChanged: Boolean = false,
    val transactionId: Id? = null,
    /** السلفة بالكتابة (§79.2-7): الشخص اللي سلّفته أو سلّفك. */
    val personId: Id? = null,
    val personName: String? = null,
    /** التحويل بين محافظك (§79.2-7): المحفظة اللي الفلوس راحتلها. */
    val toWalletId: Id? = null,
    val toWalletName: String? = null,
) {
    /** نوع الكارت من النوع الاقتصادي (§79.2-7) — الشاشة بتعرض الإشارة واللون منه بس. */
    val cardType: CardType
        get() = when (kind) {
            EconomicKind.LOAN_GRANTED -> CardType.LENT
            EconomicKind.LOAN_RECEIVED -> CardType.BORROWED
            EconomicKind.INTERNAL_TRANSFER -> CardType.MOVE
            in CARD_INCOME_KINDS -> CardType.INCOME
            else -> CardType.EXPENSE
        }
}

/**
 * كروت الإضافة بالكتابة (§78-٣ + §79.2-7): مصروف «−» · دخل «+» · سلفة إديتها «−» (بتظهر في «لك») · سلفة خدتها «+» (في «عليك») · تحويل بين
 * محافظك من غير إشارة.
 */
enum class CardType(val sign: String) { EXPENSE("−"), INCOME("+"), LENT("−"), BORROWED("+"), MOVE("") }

/** أنواع الدخل اللي كارت «قبضت …» بيسجّلها. */
val CARD_INCOME_KINDS: Set<EconomicKind> = setOf(
    EconomicKind.SALARY, EconomicKind.BONUS, EconomicKind.COMMISSION, EconomicKind.OVERTIME, EconomicKind.FREELANCE, EconomicKind.PERSONAL_SALE,
    EconomicKind.GIFT_RECEIVED, EconomicKind.SUPPORT_RECEIVED, EconomicKind.BENEFIT_RECEIVED, EconomicKind.INVESTMENT_INCOME,
)

data class SplitShare(val personId: Id?, val name: String, val amountMinor: Halalas, val isMe: Boolean = false)

data class SplitDraft(
    val title: String,
    val totalMinor: Halalas,
    val currency: Currency,
    val occurredOn: IsoDate,
    val walletId: Id?,
    val walletName: String?,
    val categoryId: Id?,
    val categoryName: String?,
    val shares: List<SplitShare>,
    /** عملية موجودة هتتقسم بدل ما تتسجل جديدة. */
    val existingTransactionId: Id? = null,
    val transactionId: Id? = null,
)

/** رابط شاشة في رد: الشاشة + معرّفات بتتفتح بيها + الاسم زي ما اتعرض. الشاشات بتتبني في مكان تاني — هنا «فين» بس. */
data class ScreenLink(val screen: AssistScreen, val args: Map<String, String> = emptyMap(), val label: String = uiText(screen.label)) {
    val board: String get() = screen.board

    companion object {
        fun of(screen: AssistScreen, vararg args: Pair<String, String>): ScreenLink =
            ScreenLink(screen, linkedMapOf(*args).apply { screen.section?.let { put("section", it) } })
    }
}

data class AssistMessage(
    val id: String,
    val conversationId: String,
    /** وقت ISO كامل. */
    val createdAt: String,
    val from: AssistSpeaker,
    val kind: AssistMessageKind,
    val text: String,
    /** الموضوع اللي اتفهم (نفس معرّف التصميم: «data.spend.category» · «nav.zakat» · «fallback.unknown»). */
    val topic: String? = null,
    /** الاسم الأساسي في السؤال (تصنيف · شخص · محل …). */
    val subjectId: Id? = null,
    /** الصيغة اللي اتختارت من الردود المتنوعة — عشان نفس الصيغة ما تتكررش ورا بعض. */
    val openerKey: String? = null,
    val state: CardState? = null,
    val card: TxnDraft? = null,
    val split: SplitDraft? = null,
    val options: List<AssistOption> = emptyList(),
    val picked: String? = null,
    val links: List<ScreenLink> = emptyList(),
    val choice: AssistChoiceKind? = null,
    /** اللي المساعد محتاجه يكمّل بعد الاختيار (الكلام الأصلي · الاسم …) — نصوص بس. */
    val payload: Map<String, String> = emptyMap(),
    /** الرقم من مصدر قال «تقريبي». */
    val approximate: Boolean = false,
) {
    val pending: Boolean get() = state == CardState.PENDING

    /** شكل الرد للشاشة (نفس أنواع النموذج التفاعلي). */
    val reply: AssistantReply?
        get() = if (from == AssistSpeaker.ME) null else when (kind) {
            AssistMessageKind.TEXT -> AssistantReply.Text(text, links, approximate)
            AssistMessageKind.LINKS -> if (topic == ASSIST_UNKNOWN_TOPIC) AssistantReply.Unknown(text, links) else AssistantReply.Text(text, links, approximate)
            AssistMessageKind.TXN_CARD -> card?.let { AssistantReply.TxnCard(text, it, state ?: CardState.PENDING) }
            AssistMessageKind.SPLIT_CARD -> split?.let { AssistantReply.SplitCard(text, it, state ?: CardState.PENDING) }
            AssistMessageKind.WALLET_PICK -> AssistantReply.WalletPick(text, options, picked)
            AssistMessageKind.CHOICE -> AssistantReply.Choice(text, options, picked)
        }
}

const val ASSIST_UNKNOWN_TOPIC = "fallback.unknown"

/** أنواع الرد زي النموذج التفاعلي — **مفيش نوع «تذكير لشخص»** (رد المالك ٣). */
sealed interface AssistantReply {
    data class Text(val text: String, val links: List<ScreenLink>, val approximate: Boolean) : AssistantReply

    data class TxnCard(val text: String, val draft: TxnDraft, val state: CardState) : AssistantReply

    data class SplitCard(val text: String, val draft: SplitDraft, val state: CardState) : AssistantReply

    data class WalletPick(val text: String, val options: List<AssistOption>, val picked: String?) : AssistantReply

    data class Choice(val text: String, val options: List<AssistOption>, val picked: String?) : AssistantReply

    data class Unknown(val text: String, val links: List<ScreenLink>) : AssistantReply
}

/** رأس المحادثة في السجل. [messageCount] بيتحسب تاني من الرسايل وقت الفتح (جوالين في نفس الوقت) — مش بيتصدّق. */
data class AssistConversation(
    val id: String,
    /** البلد اللي المحادثة بدأت فيها (الأرقام بعملتها). */
    val spaceId: String,
    val createdAt: String,
    val lastMessageAt: String,
    /** أول كلام للمستخدم (لحد ٨٠ حرف). */
    val title: String,
    /** أول رد (لحد ١٢٠ حرف). */
    val firstReply: String,
    val messageCount: Int,
)

const val CONVERSATION_TITLE_MAX = 80
const val CONVERSATION_FIRST_REPLY_MAX = 120

fun clipText(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1) + "…"
