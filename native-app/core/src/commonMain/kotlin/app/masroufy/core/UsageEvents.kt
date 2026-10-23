package app.masroufy.core

/**
 * بيانات الاستخدام (OVERRIDES §114 · PLAN-0.6 §16): **قايمة أحداث ثابتة**. كل معامل = ثابت من الكود أو عدد صغير أو فئة — **مفيش نص حر**.
 * ممنوع: مبالغ · أرقام حسابات · نص رسايل · أسامي ناس أو محلات · تواريخ عمليات · مكان.
 *
 * **الضمان بالنوع:** [UsageEvent] و[UsageValue] مالهمش constructor مفتوح برّه `core` — الطريق الوحيد هو دوال [UsageEvents] و[UsageValue.Companion]،
 * واللي بياخد نص منهم ([UsageValue.screen] · [UsageValue.sender] · [UsageValue.ruleId]) بيقبل شكل ثابت بس وإلا بيبعت «other».
 * فاحص السجلات (`NoSecretsInLogsTest`) بيتأكد إن كل نداء `Usage.log` في الكود بيبدأ بـ`UsageEvents.`.
 */
enum class UsageEventName(val wire: String) {
    ONBOARDING_STEP("onboarding_step"),
    PERMISSION_RESULT("permission_result"),
    SCREEN_OPENED("screen_opened"),
    CATEGORY_SET("category_set"),
    BUILTIN_RULE_OVERRIDDEN("builtin_rule_overridden"),
    SMS_PARSE("sms_parse"),
    SMS_NEW_SHAPE_WAITING("sms_new_shape_waiting"),
    BACKGROUND_GAVE_UP("background_gave_up"),
    PLUS_DISMISSED("plus_dismissed"),
    FAQ_NO_RESULT("faq_no_result"),
    FAQ_NOT_ANSWERED("faq_not_answered"),
}

enum class UsageParam(val wire: String) {
    STEP("step"), PHASE("phase"), PERMISSION("permission"), GRANTED("granted"), SCREEN("screen"),
    PREVIOUS("previous"), RULE("rule"), SENDER("sender"), PARSED("parsed"), FAILED("failed"), COUNT("count"),
}

/** خصائص المستخدم (user properties) — حالة مش حدث: فئات بس. */
enum class UsageProperty(val wire: String) {
    ACCOUNTS("accounts"), COUNTRIES("countries"), DARK_MODE("dark_mode"),
    INSTALLMENTS("f_installments"), BNPL("f_bnpl"), ROSCA("f_rosca"), ZAKAT("f_zakat"), SAVINGS("f_savings"),
}

/** مرحلة الخطوة في رحلة البداية. */
enum class UsagePhase { REACHED, COMPLETED }

enum class UsagePermission { SMS, NOTIFICATIONS }

/** التصنيف قبل ما المستخدم يختار: لوحده (مقترح) · مفيش · كان مختار قبل كده. */
enum class UsageCategoryBefore { AUTO, NONE, USER }

/** قيمة معامل: نص من شكل ثابت **أو** عدد صغير. */
class UsageValue private constructor(val text: String?, val number: Long?) {
    override fun equals(other: Any?): Boolean = other is UsageValue && other.text == text && other.number == number

    override fun hashCode(): Int = (text?.hashCode() ?: 0) * 31 + (number?.hashCode() ?: 0)

    override fun toString(): String = text ?: number.toString()

    companion object {
        const val MAX_COUNT = 999
        const val OTHER = "other"
        private val SCREEN_NAME = Regex("[A-Za-z][A-Za-z0-9_]{0,39}")
        private val BUILTIN_RULE_ID = Regex("rule-[0-9]{4}")

        /** اسم ثابت من الكود (enum). */
        fun of(code: Enum<*>): UsageValue = UsageValue(code.name.lowercase(), null)

        fun flag(on: Boolean): UsageValue = UsageValue(if (on) "yes" else "no", null)

        /** عدد صغير — بيتقص على 0..[MAX_COUNT]. */
        fun count(n: Int): UsageValue = UsageValue(null, n.coerceIn(0, MAX_COUNT).toLong())

        /** عدد الحسابات بفئات PLAN §16: 0 · 1 · 2-3 · 4+. */
        fun accountsBucket(n: Int): UsageValue = UsageValue(
            when {
                n <= 0 -> "0"
                n == 1 -> "1"
                n <= 3 -> "2-3"
                else -> "4+"
            },
            null,
        )

        /** اسم الشاشة (`Route.name` — ثابت من الكود). أي حاجة مش بشكل اسم كود ⇒ «other». */
        fun screen(route: String): UsageValue = UsageValue(if (SCREEN_NAME.matches(route)) route else OTHER, null)

        /** رقم المرسل **العام** بتاع بنك في الدليل بس (زي ما هو مكتوب في الدليل) — غير كده «other» (ممكن يبقى رقم شخص). */
        fun sender(raw: String): UsageValue = UsageValue(knownSender(raw) ?: OTHER, null)

        /** معرّف قاعدة جاهزة (`rule-0001`…) بس — قواعد المستخدم معرّفاتها عشوائية ⇒ «other». */
        fun ruleId(id: String): UsageValue = UsageValue(if (BUILTIN_RULE_ID.matches(id)) id else OTHER, null)

        private fun key(s: String): String = normalizeText(s).replace(" ", "")

        private fun knownSender(raw: String): String? {
            val k = key(raw)
            if (k.isEmpty()) return null
            return BANKS.asSequence().flatMap { it.senders.asSequence() }.firstOrNull { key(it) == k }
        }
    }
}

class UsageEvent internal constructor(val name: UsageEventName, val params: Map<UsageParam, UsageValue>)

/** الأحداث كلها — الطريق الوحيد لعمل [UsageEvent]. */
object UsageEvents {
    private fun e(name: UsageEventName, vararg p: Pair<UsageParam, UsageValue>) = UsageEvent(name, linkedMapOf(*p))

    fun onboardingStep(step: Enum<*>, phase: UsagePhase) =
        e(UsageEventName.ONBOARDING_STEP, UsageParam.STEP to UsageValue.of(step), UsageParam.PHASE to UsageValue.of(phase))

    fun permission(which: UsagePermission, granted: Boolean) =
        e(UsageEventName.PERMISSION_RESULT, UsageParam.PERMISSION to UsageValue.of(which), UsageParam.GRANTED to UsageValue.flag(granted))

    fun screenOpened(route: String) = e(UsageEventName.SCREEN_OPENED, UsageParam.SCREEN to UsageValue.screen(route))

    /** المستخدم اختار تصنيف — [before] = كان لوحده (اتغيّر) ولا فاضي ولا اختيار قديم. */
    fun categorySet(before: UsageCategoryBefore) = e(UsageEventName.CATEGORY_SET, UsageParam.PREVIOUS to UsageValue.of(before))

    fun builtInRuleOverridden(ruleId: String) = e(UsageEventName.BUILTIN_RULE_OVERRIDDEN, UsageParam.RULE to UsageValue.ruleId(ruleId))

    /** لقطة صندوق رسايل البنك لمرسل واحد: كام اتقرت وكام ما اتقريتش. */
    fun smsParse(sender: String, parsed: Int, failed: Int) = e(
        UsageEventName.SMS_PARSE,
        UsageParam.SENDER to UsageValue.sender(sender), UsageParam.PARSED to UsageValue.count(parsed), UsageParam.FAILED to UsageValue.count(failed),
    )

    fun smsNewShapeWaiting(sender: String, count: Int) =
        e(UsageEventName.SMS_NEW_SHAPE_WAITING, UsageParam.SENDER to UsageValue.sender(sender), UsageParam.COUNT to UsageValue.count(count))

    fun backgroundGaveUp() = e(UsageEventName.BACKGROUND_GAVE_UP)

    fun plusDismissed() = e(UsageEventName.PLUS_DISMISSED)

    /** بحث في الأسئلة من غير نتيجة — **العدد بس**، النص عمره ما بيتبعت. */
    fun faqNoResult() = e(UsageEventName.FAQ_NO_RESULT)

    fun faqNotAnswered() = e(UsageEventName.FAQ_NOT_ANSWERED)
}
