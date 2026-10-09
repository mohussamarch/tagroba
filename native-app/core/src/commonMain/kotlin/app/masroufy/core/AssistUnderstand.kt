package app.masroufy.core

/**
 * فهم رسالة واحدة للمساعد — **قواعد على الجوال، من غير ذكاء اصطناعي** (OVERRIDES §78). الترتيب نفس النموذج التفاعلي (مطابقة §78 على فرع
 * التصميم): توحيد الكلام ⇒ تعديل الكارت المستني ⇒ تقسيم ⇒ مصروف بمبلغ ⇒ موضوع معروف (تحكم · محفظة أساسية · فاتورة · أسئلة البيانات) ⇒
 * اسم شاشة ⇒ «مش فاهم». الفهم **ما بيحسبش ولا رقم** — بيقول «إيه المطلوب» بس، والرقم من حالات الاستخدام في طبقة الاستخدامات.
 */
enum class AssistNote {
    /** المبلغ بعملة غير عملة البلد ⇒ سؤال بصراحة من غير كارت. */
    FOREIGN_CURRENCY,

    /** مبلغين مختلفين في نفس الكلام ⇒ «أنهي فيهم؟». */
    TWO_AMOUNTS,

    /** يوم في المستقبل («بكرة») ⇒ ما بيتسجلش مصروف لسه ما حصلش. */
    FUTURE_DATE,

    /** الاسم ملتبس (شخصين بنفس الاسم) ⇒ اختيار، ما بنخمّنش. */
    AMBIGUOUS,

    /** رقم مكتوب كمبلغ ومش صالح (أكتر من خانتين كسر · كبير جدًا) ⇒ سؤال بصراحة. */
    INVALID_AMOUNT,
}

data class AssistUnderstanding(
    val intent: AssistIntent,
    val signals: AssistSignals,
    /** للتنقل: الشاشات بالترتيب (لحد ٣). */
    val screens: List<AssistScreen> = emptyList(),
    /** الاسم الأساسي في السؤال (التصنيف · المحل · الشخص …) — null لو مفيش. */
    val subject: AssistEntity? = null,
    val note: AssistNote? = null,
    /** تحية جوه سؤال ⇒ أول الرد. */
    val greeting: GreetingKind? = null,
    /** للتحكم في التعلّم: يشتغل (true) ولا يقف (false). */
    val learningOn: Boolean? = null,
) {
    /** المعرّف اللي بيتخزن ويتعدّ («nav.zakat» للتنقل — نفس قايمة التصميم). */
    val wire: String get() = if (intent == AssistIntent.NAV) screens.firstOrNull()?.navWire ?: intent.wire else intent.wire

    /** مفتاح الموضوع في «بتسأل عنها كتير»: النية + الاسم الأساسي (القهوة غير البقالة). */
    val topicKey: String get() = subject?.let { "$wire:${it.type.name.lowercase()}:${it.id}" } ?: wire
}

/** اللي الفهم محتاجه من برّه الرسالة: أسامي المستخدم · فيه كارت مستني؟ · الشخص اللي شاشته مفتوحة · الشاشات الظاهرة. */
data class AssistUnderstandContext(
    val lexicon: AssistLexicon = AssistLexicon(),
    val pendingCard: Boolean = false,
    val subjectPersonId: Id? = null,
    val visible: (AssistScreen) -> Boolean = { true },
)

fun understandAssist(text: String, ctx: AssistUnderstandContext = AssistUnderstandContext()): AssistUnderstanding {
    val s = AssistSignals(text, ctx.lexicon)
    val greeting = detectGreeting(s)
    fun done(intent: AssistIntent, subject: AssistEntity? = null, screens: List<AssistScreen> = emptyList(), note: AssistNote? = null, learning: Boolean? = null) =
        AssistUnderstanding(intent, s, screens, subject, note, if (intent.kind == AssistIntentKind.SMALLTALK) null else greeting, learning)

    // ١. الكارت المستني: تأكيد · إلغاء · تعديل (مبلغ · محفظة · يوم · «التصنيف …»)، من غير كلمة مصروف جديدة ومش سؤال
    if (ctx.pendingCard) {
        if (isConfirm(s)) return done(AssistIntent.CONFIRM_PENDING)
        if (isCancel(s)) return done(AssistIntent.CANCEL_PENDING)
        if (isPendingEdit(s)) return done(AssistIntent.EDIT_PENDING, s.entities.ofType(AssistEntityType.CATEGORY).firstOrNull())
    }
    // ٢. تقسيم
    if (s.has(AssistWords.SPLIT) && !s.has(AssistTalkWords.HISTORY)) {
        val note = if (s.money.distinct.size > 1) AssistNote.TWO_AMOUNTS else null
        return done(AssistIntent.SPLIT, s.entities.ofType(AssistEntityType.PERSON).firstOrNull(), note = note)
    }
    // ٣. مصروف بمبلغ (مش سؤال) — والرقم اللي مكتوب كمبلغ ومش صالح («١٥٫٥٥٥») ⇒ سؤال بصراحة من غير كارت
    if ((s.hasAmount || (s.money.invalid && !s.question)) && !s.question && controlOf(s) == null) {
        val note = when {
            !s.hasAmount -> AssistNote.INVALID_AMOUNT
            s.money.distinct.size > 1 -> AssistNote.TWO_AMOUNTS
            s.dayOffset == -1 -> AssistNote.FUTURE_DATE
            else -> null
        }
        val subject = s.specific(AssistEntityType.RECURRING) ?: s.specific(AssistEntityType.MERCHANT) ?: s.entity(AssistEntityType.CATEGORY)
        return done(AssistIntent.QUICK_ADD, subject, note = note)
    }
    // ٤. موضوع معروف: التحكم · المحفظة الأساسية · تسجيل فاتورة · طلب نصيحة · أسئلة البيانات
    controlOf(s)?.let { (intent, on) -> return done(intent, learning = on) }
    s.entity(AssistEntityType.WALLET)?.takeIf { s.has(AssistWords.MAIN) || s.has(AssistWords.USUALLY_FROM) }?.let {
        val note = if (s.specific(AssistEntityType.WALLET) == null && s.entities.ambiguous(AssistEntityType.WALLET)) AssistNote.AMBIGUOUS else null
        return done(AssistIntent.SET_MAIN_WALLET, it, note = note)
    }
    if (s.has(AssistWords.RECORD) && !s.question && (s.specific(AssistEntityType.RECURRING) != null || s.has(AssistWords.BILLS))) {
        return done(AssistIntent.RECORD_DUE_BILL, s.specific(AssistEntityType.RECURRING) ?: s.entity(AssistEntityType.CATEGORY))
    }
    if (s.has(AssistTalkWords.ADVICE)) return done(AssistIntent.ADVICE_REQUEST)
    // «فين/وين/وريني/افتح …» من غير «كام/إمتى» ⇒ إجابة التنقل للموضوع (التصميم: سؤال «فين» ⇒ الشاشة)، ولو ما فيش شاشة ⇒ البيانات
    val navMode = (s.has(AssistWords.WHERE) || s.has(AssistWords.NAV_VERB)) && !s.has(AssistWords.HOW_MUCH) && !s.has(AssistWords.WHEN)
    if (!navMode) dataTopic(s, ctx)?.let { return done(it.first, it.second, note = it.third) }
    // ٥. اسم شاشة: اسم من أسامي المستخدم (شخص · محفظة · تصنيف بسقف …) ثم كلمات الشاشات
    entityScreen(s)?.let { (screen, entity) ->
        val note = if (s.entities.ambiguous(entity.type)) AssistNote.AMBIGUOUS else null
        return done(AssistIntent.NAV, entity, listOf(screen), note)
    }
    val named = screensNamedIn(s.tokens, ctx.visible)
    if (named.isNotEmpty()) return done(AssistIntent.NAV, screens = named)
    if (navMode) dataTopic(s, ctx)?.let { return done(it.first, it.second, note = it.third) }
    smallTalkOf(s)?.let { return done(it) }
    // ٦. مش فاهم (الأقرب بيتحسب في طبقة الاستخدامات بالتبويب والشاشات الظاهرة)
    return done(AssistIntent.UNKNOWN)
}

/** سؤال البيانات + ملاحظة «الاسم ملتبس» لو الشخص ليه اتنين بنفس الاسم. */
private fun dataTopic(s: AssistSignals, ctx: AssistUnderstandContext): Triple<AssistIntent, AssistEntity?, AssistNote?>? {
    val (intent, subject) = dataTopicOf(s, ctx) ?: return null
    val note = if (subject != null && subject.start >= 0 && s.entities.ambiguous(subject.type)) AssistNote.AMBIGUOUS else null
    return Triple(intent, subject, note)
}

/** تعديل الكارت: مبلغ أو محفظة أو يوم، أو «التصنيف …» صريح — ومن غير كلمة مصروف جديدة (تصنيف/محل) ومش سؤال. */
private fun isPendingEdit(s: AssistSignals): Boolean {
    if (s.question) return false
    val explicitCategory = s.has(AssistWords.CATEGORY_WORD) && s.entity(AssistEntityType.CATEGORY) != null
    if (explicitCategory) return true
    val newExpense = s.entity(AssistEntityType.CATEGORY) != null || s.specific(AssistEntityType.MERCHANT) != null || s.has(AssistWords.SPLIT)
    if (newExpense) return false
    return s.hasAmount || s.entity(AssistEntityType.WALLET) != null || (s.dayOffset != null && s.dayOffset >= 0)
}

/**
 * اسم من أسامي المستخدم ⇒ شاشة تفاصيله (لو مفيش سؤال بيانات اتعرف قبلها): «افتح أحمد» · «محفظة الكاش» · «ميزانية الأكل» · «صفحة المرسى».
 * التصنيف بيفتح سقفه بس لو فيه كلمة ميزانية/سقف (غير كده كلمة «قهوة» لوحدها ما بتفتحش حاجة).
 */
private fun entityScreen(s: AssistSignals): Pair<AssistScreen, AssistEntity>? {
    val open = s.has(AssistWords.OPEN) || s.tokens.size <= 3
    s.entity(AssistEntityType.CATEGORY)?.takeIf { s.has(AssistWords.BUDGET) }?.let { return AssistScreen.CATEGORY_BUDGET to it }
    if (!open) return null
    val order = listOf(
        AssistEntityType.PERSON to AssistScreen.PERSON_PROFILE, AssistEntityType.MERCHANT to AssistScreen.MERCHANT,
        AssistEntityType.GOAL to AssistScreen.GOAL_DETAIL, AssistEntityType.EVENT to AssistScreen.EVENT_DETAIL, AssistEntityType.PROJECT to AssistScreen.PROJECT_DETAIL,
        AssistEntityType.ROSCA to AssistScreen.ROSCA_DETAIL, AssistEntityType.PLAN to AssistScreen.INSTALLMENT_DETAIL,
        AssistEntityType.RECURRING to AssistScreen.SUBSCRIPTION_DETAIL, AssistEntityType.ASSET to AssistScreen.ASSET_DETAIL,
    )
    for ((type, screen) in order) s.specific(type)?.let { return screen to it }
    // المحفظة: اسمها، أو «محفظة الكاش» / «تفاصيل حساب البنك» (الكلمة العامة مع كلمة محفظة/حساب)
    s.specific(AssistEntityType.WALLET)?.let { return AssistScreen.WALLET_DETAIL to it }
    if (s.has(vocab("محفظه", "حساب", "wallet", fuzzy = false))) s.entity(AssistEntityType.WALLET)?.let { return AssistScreen.WALLET_DETAIL to it }
    return null
}
