package app.masroufy.core

/**
 * أنواع الفهم في المساعد (OVERRIDES §78 — قايمة النوايا اللي اتصممت مع الجلسة): تنقّل · سؤال عن بياناتك · فعل · تحكم · كلام عادي · مش فاهم.
 * المعرّف ([wire]) هو نفسه اللي في التصميم («data.spend.total» …) — بيتخزن في سجل المحادثة وعدّاد المواضيع، فما يتغيرش.
 */
enum class AssistIntentKind { NAVIGATION, DATA, ACTION, CONTROL, SMALLTALK, FALLBACK }

enum class AssistIntent(val wire: String, val kind: AssistIntentKind) {
    NAV("nav.screen", AssistIntentKind.NAVIGATION),

    SPEND_TOTAL("data.spend.total", AssistIntentKind.DATA),
    SPEND_CATEGORY("data.spend.category", AssistIntentKind.DATA),
    SPEND_MERCHANT("data.spend.merchant", AssistIntentKind.DATA),
    SPEND_PERSON("data.spend.person", AssistIntentKind.DATA),
    SPEND_BIGGEST("data.spend.biggest", AssistIntentKind.DATA),
    SPEND_COMPARE("data.spend.compare", AssistIntentKind.DATA),
    INCOME("data.income", AssistIntentKind.DATA),
    REMAINING("data.remaining", AssistIntentKind.DATA),
    DAILY_ALLOWANCE("data.daily_allowance", AssistIntentKind.DATA),
    FORECAST("data.forecast", AssistIntentKind.DATA),
    BUDGET_STATUS("data.budget_status", AssistIntentKind.DATA),
    CATEGORY_BUDGET("data.category_budget", AssistIntentKind.DATA),
    CASH_ON_HAND("data.cash_on_hand", AssistIntentKind.DATA),
    ON_HAND("data.on_hand", AssistIntentKind.DATA),
    OWED_TO_ME("data.owed_to_me", AssistIntentKind.DATA),
    I_OWE("data.i_owe", AssistIntentKind.DATA),
    PERSON_BALANCE("data.person_balance", AssistIntentKind.DATA),
    DEBTS_OVERDUE("data.debts_overdue", AssistIntentKind.DATA),
    NEXT_SALARY("data.next_salary", AssistIntentKind.DATA),
    SALARY_AMOUNT("data.salary_amount", AssistIntentKind.DATA),
    LAST_AT_MERCHANT("data.last_at_merchant", AssistIntentKind.DATA),
    GOAL_PROGRESS("data.goal_progress", AssistIntentKind.DATA),
    ZAKAT("data.zakat", AssistIntentKind.DATA),
    DUES_UPCOMING("data.dues_upcoming", AssistIntentKind.DATA),
    BILLS("data.bills", AssistIntentKind.DATA),
    PENDING_REVIEW("data.pending_review", AssistIntentKind.DATA),
    ASSETS("data.assets", AssistIntentKind.DATA),
    EVENT_SPEND("data.event_spend", AssistIntentKind.DATA),
    PROJECT_SPEND("data.project_spend", AssistIntentKind.DATA),
    ROSCA("data.rosca", AssistIntentKind.DATA),
    INSTALLMENTS("data.installments", AssistIntentKind.DATA),
    OCCASIONS("data.occasions", AssistIntentKind.DATA),

    QUICK_ADD("action.quick_add", AssistIntentKind.ACTION),
    EDIT_PENDING("action.edit_pending", AssistIntentKind.ACTION),
    CONFIRM_PENDING("action.confirm_pending", AssistIntentKind.ACTION),
    CANCEL_PENDING("action.cancel_pending", AssistIntentKind.ACTION),
    SPLIT("action.split", AssistIntentKind.ACTION),
    SET_MAIN_WALLET("action.set_main_wallet", AssistIntentKind.ACTION),
    RECORD_DUE_BILL("action.record_due_bill", AssistIntentKind.ACTION),

    NEW_CONVERSATION("control.new_conversation", AssistIntentKind.CONTROL),
    HISTORY("control.history", AssistIntentKind.CONTROL),
    MEMORY("control.memory", AssistIntentKind.CONTROL),
    LEARNING_SWITCH("control.learning_switch", AssistIntentKind.CONTROL),
    FORGET_ALL("control.forget_all", AssistIntentKind.CONTROL),

    GREETING("smalltalk.greeting", AssistIntentKind.SMALLTALK),
    HOW_ARE_YOU("smalltalk.how_are_you", AssistIntentKind.SMALLTALK),
    THANKS("smalltalk.thanks", AssistIntentKind.SMALLTALK),
    BYE("smalltalk.bye", AssistIntentKind.SMALLTALK),
    WHO_ARE_YOU("smalltalk.who_are_you", AssistIntentKind.SMALLTALK),
    HELP("smalltalk.help", AssistIntentKind.SMALLTALK),
    PRAISE("smalltalk.praise", AssistIntentKind.SMALLTALK),
    FRUSTRATION("smalltalk.frustration", AssistIntentKind.SMALLTALK),
    ADVICE_REQUEST("smalltalk.advice_request", AssistIntentKind.SMALLTALK),

    UNKNOWN("fallback.unknown", AssistIntentKind.FALLBACK),
    ;

    /** بيتعدّ في «بتسأل عنها كتير»: أسئلة البيانات والتنقل والأفعال اللي بتتكرر — مش الكلام العادي ولا «مش فاهم» ولا الرد على كارت. */
    val learnable: Boolean
        get() = kind == AssistIntentKind.DATA || kind == AssistIntentKind.NAVIGATION || this == SPLIT || this == RECORD_DUE_BILL

    companion object {
        fun fromWire(wire: String): AssistIntent? = entries.firstOrNull { it.wire == wire }
    }
}

/** التبويب اللي المساعد اتفتح منه (§67 · §74: أربع تبويبات + «المزيد» ورا الترس) — للاقتراحات وأقرب الشاشات. */
enum class AssistTab(val wire: String) { HOME("home"), OPERATIONS("operations"), PEOPLE("people"), INVESTMENT("investment"), MORE("more");

    companion object {
        fun fromWire(wire: String?): AssistTab? = entries.firstOrNull { it.wire == wire }
    }
}

/** الجهاز: قراية رسايل البنك أندرويد بس (أبل ما بتسمحش) ⇒ على الآيفون «رسائل البنك» بتوديك «الصق رسالة». */
enum class AssistPlatform { ANDROID, IOS }
