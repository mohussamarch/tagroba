package app.masroufy.core

/**
 * محرك التنبيهات على الجوال — الكتالوج (OVERRIDES §61): كل نوع تنبيه ومجموعته وإلحاحه، وسلّم التصعيد.
 * المحرك نفسه (القرار لكل تنبيه) في `AlertEngine.kt`، ونص شاشة القفل في `AlertTexts.kt`.
 *
 * **«الجوال الأول»:** الأنواع اللي محتاجة سيرفر ([AlertKind.needsServer]) متعرّفة هنا بس عشان الإعدادات تعرفها —
 * **ولا حاجة على الجوال بتولّدها** لحد ما السيرفر يتقرر.
 */
enum class AlertGroup(val wire: String) {
    DUES("dues"), BUDGET("budget"), QUESTIONS("questions"), ZAKAT("zakat"), BALANCE("balance"), PROFILE("profile"),
    OCCASIONS("occasions"), SECURITY("security"), LINKED_ACCOUNTS("linked_accounts"), EMAIL("email"),
    // مصادر الدخل (§48 · §64) — مجموعة لوحدها عشان تتقفل لوحدها
    INCOME("income"),
    ;

    companion object {
        fun fromWire(wire: String): AlertGroup? = entries.firstOrNull { it.wire == wire }
    }
}

/** الإلحاح الأساسي — الترتيب مهم (الأقل الأول). */
enum class AlertUrgency { NONE, LOW, MEDIUM, HIGH }

/** درجة في سلّم التصعيد: قرّب ⇒ النهارده ⇒ فات. */
enum class AlertStage(val wire: String) { SOON("soon"), TODAY("today"), OVERDUE("overdue") }

enum class AlertKind(
    val wire: String,
    val group: AlertGroup,
    val urgency: AlertUrgency,
    /** محتاج قرار من المستخدم (مش مجرد معلومة) ⇒ ما بيتدفنش في ملخص. */
    val needsDecision: Boolean = false,
    /** «متأخر»: شريط الإشعارات + نافذة جوه التطبيق لما يتفتح (§61). */
    val inAppWindow: Boolean = false,
    /** محتاج سيرفر (إيميل · حسابات مربوطة · دخول من جهاز جديد) ⇒ **مش بيتولد دلوقتي**. */
    val needsServer: Boolean = false,
) {
    DUE_SOON("due_soon", AlertGroup.DUES, AlertUrgency.MEDIUM),
    DUE_TODAY("due_today", AlertGroup.DUES, AlertUrgency.HIGH),
    DUE_OVERDUE("due_overdue", AlertGroup.DUES, AlertUrgency.HIGH, inAppWindow = true),
    BUDGET_THRESHOLD("budget_threshold", AlertGroup.BUDGET, AlertUrgency.LOW),
    BUDGET_EXCEEDED("budget_exceeded", AlertGroup.BUDGET, AlertUrgency.MEDIUM),
    TRANSFER_QUESTION("transfer_question", AlertGroup.QUESTIONS, AlertUrgency.LOW, needsDecision = true),
    ZAKAT_SOON("zakat_soon", AlertGroup.ZAKAT, AlertUrgency.MEDIUM),
    ZAKAT_TODAY("zakat_today", AlertGroup.ZAKAT, AlertUrgency.HIGH),
    ZAKAT_OVERDUE("zakat_overdue", AlertGroup.ZAKAT, AlertUrgency.MEDIUM, inAppWindow = true),
    BALANCE_MISMATCH("balance_mismatch", AlertGroup.BALANCE, AlertUrgency.LOW, needsDecision = true),
    PROFILE_INCOMPLETE("profile_incomplete", AlertGroup.PROFILE, AlertUrgency.NONE),
    // مناسبات الشخص (§64): متوسط الاتنين ⇒ وقتك المعتاد، ومفيش «عدّى» (المناسبة اللي عدّت بتستنى السنة الجاية)
    OCCASION_SOON("occasion_soon", AlertGroup.OCCASIONS, AlertUrgency.MEDIUM),
    OCCASION_TODAY("occasion_today", AlertGroup.OCCASIONS, AlertUrgency.MEDIUM),
    // المرتب المتأخر (§64): هادي — وقتك المعتاد، من غير «عدّى» ولا نافذة (اختيار Claude)
    INCOME_LATE("income_late", AlertGroup.INCOME, AlertUrgency.MEDIUM),
    NEW_DEVICE_LOGIN("new_device_login", AlertGroup.SECURITY, AlertUrgency.HIGH, needsServer = true),
    LINKED_ACCOUNT_ACTIVITY("linked_account_activity", AlertGroup.LINKED_ACCOUNTS, AlertUrgency.MEDIUM, needsServer = true),
    MONTHLY_EMAIL("monthly_email", AlertGroup.EMAIL, AlertUrgency.NONE, needsServer = true),
    ;

    companion object {
        fun fromWire(wire: String): AlertKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** تذكير الزكاة قبل الميعاد بـ10 أيام (§62 — قرار تنفيذ). «قرّب» في المستحقات = [DUE_SOON_DAYS]. */
const val ZAKAT_REMINDER_DAYS = 10

/** الدرجة النهارده لميعاد [dueAt]، أو null لو لسه بعيد (أبعد من [soonDays]). */
fun alertStage(dueAt: IsoDate, today: IsoDate, soonDays: Int): AlertStage? {
    val days = daysBetween(today, dueAt)
    return when {
        days < 0 -> AlertStage.OVERDUE
        days == 0 -> AlertStage.TODAY
        days <= soonDays -> AlertStage.SOON
        else -> null
    }
}

fun dueKindFor(stage: AlertStage): AlertKind = when (stage) {
    AlertStage.SOON -> AlertKind.DUE_SOON
    AlertStage.TODAY -> AlertKind.DUE_TODAY
    AlertStage.OVERDUE -> AlertKind.DUE_OVERDUE
}

fun zakatKindFor(stage: AlertStage): AlertKind = when (stage) {
    AlertStage.SOON -> AlertKind.ZAKAT_SOON
    AlertStage.TODAY -> AlertKind.ZAKAT_TODAY
    AlertStage.OVERDUE -> AlertKind.ZAKAT_OVERDUE
}

/**
 * تنبيه مرشّح النهارده. [threadKey] = **الموضوع** (القسط ده بميعاده · ميزانية الفترة دي …) — كل درجات التصعيد بتاعته
 * ليها نفس الموضوع، فالجديدة بتحل محل القديمة في الصفحة. [eventKey] = الموضوع + النوع ⇒ كل درجة بتتبعت **مرة واحدة**.
 *
 * [title] و[body] **التفاصيل جوه التطبيق بس** (فيها مبالغ وأسامي) — عمرها ما بتروح لشريط الإشعارات؛
 * الشريط بياخد نص عام من النوع لوحده ([systemNoticeFor]).
 */
data class AlertCandidate(
    val kind: AlertKind,
    val threadKey: String,
    val title: String,
    val body: String,
    /** اتجاه الفلوس لتنبيهات المستحقات (هتدفع/هتستلم) — بيغيّر النص العام بس. */
    val flow: DueFlow = DueFlow.PAY,
    /** المبلغ وحجم الشهر (سقف الميزانية) **بنفس العملة** — للحكم على «كبير بالنسبة لشهرك». null = مش معروف ⇒ مفيش حكم. */
    val amountMinor: Halalas? = null,
    val monthScaleMinor: Halalas? = null,
    /** البلد اللي التنبيه منها (§64) — السعودية افتراضي. */
    val spaceId: String = DEFAULT_SPACE_ID,
    /** اسم البلد **جوه التطبيق بس** (مش على شاشة القفل) — `null` = حساب ببلد واحدة أو تنبيه على مستوى الحساب. */
    val spaceLabel: String? = null,
) {
    val eventKey: String get() = "$threadKey|${kind.wire}"
}

/**
 * تنبيه من بلد (§64): موضوع البلاد غير السعودية بيبدأ بمعرّفها (`eg:`) عشان قسط في مصر وقسط في السعودية بنفس المعرّف ما يبقوش موضوع واحد.
 * **موضوع السعودية ما بيتغيرش** — الإيصالات القديمة («اتبعت») بتفضل شغالة. [labelled] = عنده أكتر من بلد ⇒ اسم البلد بيظهر جوه التطبيق.
 */
fun AlertCandidate.inSpace(space: Space, labelled: Boolean): AlertCandidate = copy(
    threadKey = if (space.id == DEFAULT_SPACE_ID) threadKey else "${space.id}:$threadKey",
    spaceId = space.id,
    spaceLabel = if (labelled) space.name else null,
)

internal fun addDaysIso(date: IsoDate, days: Int): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(date)) + days)
