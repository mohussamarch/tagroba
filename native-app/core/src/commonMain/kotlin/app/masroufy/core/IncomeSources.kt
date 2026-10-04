package app.masroufy.core

/**
 * مصادر الدخل (OVERRIDES §48 · §64). المنطق هنا وفي `IncomeChanges.kt` (تغيير الشغل وأسئلته) و`IncomeSignals.kt`
 * (مين بيحوّل المرتب · المرتب المتأخر) و`IncomeComparison.kt` (قبل وبعد المصدر الجديد). الشاشات مستنية تصميم المالك.
 *
 * **المستخدم هو اللي بيقول**: اشتغلت فين ومن إمتى وسبت إمتى. البرنامج **ما يستنتجش**
 * إن المرتب زاد لما المبلغ يزيد — نفس التغيير ممكن يكون زيادة أو خصم أو إجازة أو سلفة،
 * والفرق بينهم مش في الكشف (قرار المالك، وده بيصحّح اقتراح Claude الأول).
 *
 * اللي البرنامج يقدر يستنتجه: **مين اللي حوّل** عشان يبطّل يسأل عن كل إيداع.
 */
enum class IncomeSourceKind(val wire: String) {
    /** وظيفة بمرتب. */
    JOB("job"),
    PART_TIME("part_time"),
    CLIENT("client"),
    RENT("rent"),
    INVESTMENT("investment"),
    OTHER("other"),
    /** معاش (رد المالك §65 — نوع جديد؛ بيتحسب «بمرتب» في «فاضلك تقريبًا»). */
    PENSION("pension"),
    ;

    companion object {
        fun fromWire(wire: String?): IncomeSourceKind = entries.firstOrNull { it.wire == wire } ?: OTHER
    }
}

/**
 * دورية القبض (رد المالك §65 عن البارت تايم: «ممكن اسبوعي»). **شهري هو الافتراضي** — والمصدر القديم من غير الحقل = شهري.
 * الشهري يومه [IncomeSource.expectedDayOfMonth]، والأسبوعي يومه [IncomeSource.payWeekday].
 */
enum class PayFrequency(val wire: String) {
    MONTHLY("monthly"), WEEKLY("weekly");

    companion object {
        fun fromWire(wire: String?): PayFrequency = entries.firstOrNull { it.wire == wire } ?: MONTHLY
    }
}

data class IncomeSource(
    val id: Id,
    /** اسم الشركة أو العميل. */
    val name: String,
    val normalizedName: String,
    val kind: IncomeSourceKind,
    val currency: Currency,
    val startedAt: IsoDate,
    /** null = لسه شغال فيه. */
    val endedAt: IsoDate? = null,
    /** اليوم المتوقع في الشهر — للتنبيه لما المعتاد ما ييجيش. */
    val expectedDayOfMonth: Int? = null,
    /** المبلغ المتوقع لو المستخدم كتبه. **اختياري** — مفيش فورم إجباري. */
    val expectedMinor: Halalas? = null,
    val createdAt: String = "",
    /**
     * مفاتيح أطراف التحويل (الاسم + آخر 4 — زي `TransferParty.key`) اللي المستخدم **أكد** إنها بتحوّل مرتب المصدر ده
     * («ده مرتب من …؟» ⇒ أيوه). الإيداع الجاي منها بيتنسب للمصدر لوحده من غير سؤال (§48 · §64).
     */
    val payerKeys: List<String> = emptyList(),
    /** أطراف قال عليها «لأ، مش مرتب من المصدر ده» ⇒ ما يتسألش عنها تاني للمصدر ده. */
    val declinedPayerKeys: List<String> = emptyList(),
    /** شهري (الافتراضي) أو أسبوعي — §65. */
    val payFrequency: PayFrequency = PayFrequency.MONTHLY,
    /** يوم القبض في الأسبوع للأسبوعي بس: 1 = الاتنين … 7 = الحد (ISO). null = لسه ما اتقالش. */
    val payWeekday: Int? = null,
)

/** الأنواع اللي ليها «مرتب» من شغل (يوم مرتب · مرتب متوقع · «هتروح بيها الشغل؟» · «ده مرتب من …؟»). */
val SALARIED_KINDS: Set<IncomeSourceKind> = setOf(IncomeSourceKind.JOB, IncomeSourceKind.PART_TIME)

const val INCOME_SOURCE_NAME_MAX = 80

/** الفترتين [aStart..aEnd] و[bStart..bEnd] بيتقابلوا (شاملين؛ null = لسه شغال). */
fun periodsOverlap(aStart: IsoDate, aEnd: IsoDate?, bStart: IsoDate, bEnd: IsoDate?): Boolean =
    (bEnd == null || aStart <= bEnd) && (aEnd == null || bStart <= aEnd)

class IncomeSourceError(message: String) : IllegalArgumentException(message)

/**
 * الاسم بعد التنضيف + المطبّع، زي المشاريع. **نفس الاسم مسموح لو الفترات ما بتتقابلش** (رد المالك §64: الرجوع لشركة قديمة =
 * فترة جديدة بنفس الاسم، والقديمة بتفضل بتاريخها) — المرفوض بس فترتين بنفس الاسم في نفس الوقت.
 * الأسبوعي يومه في الأسبوع (1–7) ومن غير يوم في الشهر، والشهري العكس. **الوظيفة ينفع تكون أسبوعي** زي البارت تايم (رد المالك
 * §64-٤ — بيلغي «الوظيفة شهري بس»)؛ الوظيفة الشهري يومها يوم المرتب في الملف (واحد للحساب — §64).
 */
fun checkIncomeSource(
    name: String,
    startedAt: IsoDate,
    endedAt: IsoDate?,
    expectedDayOfMonth: Int?,
    expectedMinor: Halalas?,
    sources: List<IncomeSource>,
    selfId: Id? = null,
    kind: IncomeSourceKind = IncomeSourceKind.JOB,
    payFrequency: PayFrequency = PayFrequency.MONTHLY,
    payWeekday: Int? = null,
): CheckedName {
    val clean = JsText.collapseWhitespace(JsText.trim(name))
    if (clean.isEmpty() || clean.length > INCOME_SOURCE_NAME_MAX) {
        throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_NAME_LENGTH, INCOME_SOURCE_NAME_MAX.toString()))
    }
    val normalized = normalizeText(clean)
    if (!isValidIsoDate(startedAt)) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_START))
    if (endedAt != null) {
        if (!isValidIsoDate(endedAt) || endedAt < startedAt) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_END))
    }
    if (sources.any { it.id != selfId && it.normalizedName == normalized && periodsOverlap(it.startedAt, it.endedAt, startedAt, endedAt) }) {
        throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_DUPLICATE))
    }
    if (expectedDayOfMonth != null && (expectedDayOfMonth < 1 || expectedDayOfMonth > 31)) {
        throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_DAY))
    }
    if (payWeekday != null && payWeekday !in 1..7) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_WEEKDAY))
    val mismatch = when (payFrequency) {
        PayFrequency.MONTHLY -> payWeekday != null
        PayFrequency.WEEKLY -> expectedDayOfMonth != null
    }
    if (mismatch) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_FREQUENCY_MISMATCH))
    if (expectedMinor != null && expectedMinor <= 0) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_AMOUNT))
    return CheckedName(clean, normalized)
}

/** المصدر عنده ميعاد قبض معروف (يوم في الشهر للشهري · يوم في الأسبوع للأسبوعي). */
fun hasKnownPayDay(s: IncomeSource): Boolean = when (s.payFrequency) {
    PayFrequency.MONTHLY -> s.expectedDayOfMonth != null
    PayFrequency.WEEKLY -> s.payWeekday != null
}

/** المصادر الشغالة في يوم معين — التوقع ما يفضلش يفترض شغل المستخدم سابه. */
fun sourcesActiveOn(sources: List<IncomeSource>, day: IsoDate): List<IncomeSource> =
    sources.filter { it.startedAt <= day && (it.endedAt == null || it.endedAt >= day) }

/**
 * نسبة أكبر مصدر من إجمالي الدخل بالعُشر من المية (855 = 85.5٪)، أو null لو مفيش دخل.
 * «٩٢٪ من دخلك من مصدر واحد» — جملة مستشار مالي، ومحتاجة البيانات دي عشان تتقال.
 */
fun topSourceShareTenthPercent(amountBySource: Map<Id, Halalas>): Long? {
    val total = sumMoney(amountBySource.values.toList())
    if (total <= 0) return null
    val top = amountBySource.values.maxOrNull() ?: return null
    // نفس حساب النسبة في التوزيع: العُشر من المية بقسمة صحيحة، من غير كسور عائمة
    return shareOfAmount(1000, top, total)
}
