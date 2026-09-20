package app.masroufy.core

/**
 * مصادر الدخل — المكان بس (OVERRIDES §48). الميزة والشاشات بعدين.
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
    ;

    companion object {
        fun fromWire(wire: String?): IncomeSourceKind = entries.firstOrNull { it.wire == wire } ?: OTHER
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
)

const val INCOME_SOURCE_NAME_MAX = 80

class IncomeSourceError(message: String) : IllegalArgumentException(message)

/** الاسم بعد التنضيف + المطبّع، زي المشاريع. */
fun checkIncomeSource(
    name: String,
    startedAt: IsoDate,
    endedAt: IsoDate?,
    expectedDayOfMonth: Int?,
    expectedMinor: Halalas?,
    sources: List<IncomeSource>,
    selfId: Id? = null,
): CheckedName {
    val clean = JsText.collapseWhitespace(JsText.trim(name))
    if (clean.isEmpty() || clean.length > INCOME_SOURCE_NAME_MAX) {
        throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_NAME_LENGTH, INCOME_SOURCE_NAME_MAX.toString()))
    }
    val normalized = normalizeText(clean)
    if (sources.any { it.id != selfId && it.normalizedName == normalized }) {
        throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_DUPLICATE))
    }
    if (!isValidIsoDate(startedAt)) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_START))
    if (endedAt != null) {
        if (!isValidIsoDate(endedAt) || endedAt < startedAt) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_END))
    }
    if (expectedDayOfMonth != null && (expectedDayOfMonth < 1 || expectedDayOfMonth > 31)) {
        throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_DAY))
    }
    if (expectedMinor != null && expectedMinor <= 0) throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_BAD_AMOUNT))
    return CheckedName(clean, normalized)
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
