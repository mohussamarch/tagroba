package app.masroufy.core

/**
 * كيانات الزكاة المتخزنة (OVERRIDES §62) — **وقائع** يدخلها المستخدم، وسنين الزكاة، ودفعاتها.
 * الوقائع **كيان جنب الأصل والدين** مش حقول جواهم (زي `DebtTerms`) — عشان الأصل والدين يفضلوا مطابقين للتطبيق الحالي ونسخته بالحرف.
 */
enum class ZakatPurpose(val wire: String) {
    /** للبس (شبكة · حلق · خاتم). */
    WEAR("wear"),
    /** ادخار (سبايك · جنيهات). */
    SAVING("saving");

    companion object {
        fun fromWire(wire: String): ZakatPurpose = entries.first { it.wire == wire }
    }
}

enum class ZakatShareHolding(val wire: String) {
    TRADING("trading"), LONG_TERM("long_term");

    companion object {
        fun fromWire(wire: String): ZakatShareHolding = entries.first { it.wire == wire }
    }
}

enum class ZakatCollectability(val wire: String) {
    /** هيرجع — قادر وناوي. */
    STRONG("strong"),
    /** مشكوك فيه — بيماطل أو مش قادر. */
    DOUBTFUL("doubtful");

    companion object {
        fun fromWire(wire: String): ZakatCollectability = entries.first { it.wire == wire }
    }
}

enum class ZakatSubject(val wire: String) {
    ASSET("asset"), OBLIGATION("obligation");

    companion object {
        fun fromWire(wire: String): ZakatSubject = entries.first { it.wire == wire }
    }
}

/**
 * وقائع أصل أو دين ليك. المعرّف = معرّف الأصل أو الدين. `null` = لسه ما اتسألش (مش «لأ» — قاعدة 10).
 * [karat] للدهب (1–24) · [fineness] للفضة في الألف (925 مثلًا).
 * [saudiCompany] للسهم والصندوق: الشركة (أو الصندوق) سعودية؟ — بيتسأل في السعودية بس، للطويل الأجل (§62: الإعفاء في الدليل
 * مربوط بالشركات المساهمة في المملكة، والأجنبية الدليل ساكت عنها).
 */
data class ZakatFact(
    val subjectId: Id,
    val subject: ZakatSubject,
    val purpose: ZakatPurpose? = null,
    val holding: ZakatShareHolding? = null,
    val collectability: ZakatCollectability? = null,
    val karat: Int? = null,
    val fineness: Int? = null,
    val updatedAt: String,
    val saudiCompany: Boolean? = null,
)

/** سطور الدفع — بالترتيب ده بتتعرض وبيتوزع عليها المدفوع. */
enum class ZakatLineKind(val wire: String, val labelKey: TextKey) {
    CASH("cash", TextKey.ZAKAT_LINE_CASH),
    GOLD("gold", TextKey.ZAKAT_LINE_GOLD),
    SILVER("silver", TextKey.ZAKAT_LINE_SILVER),
    STOCKS("stocks", TextKey.ZAKAT_LINE_STOCKS),
    FUNDS("funds", TextKey.ZAKAT_LINE_FUNDS),
    RECEIVABLES("receivables", TextKey.ZAKAT_LINE_RECEIVABLES),
    /** ديون ليك اتحصّلت في السنة دي — 2.5% مرة واحدة على اللي اتحصّل (مصر 4399 · السعودية المشكوك فيه §3.4). */
    COLLECTED_RECEIVABLES("collected_receivables", TextKey.ZAKAT_LINE_COLLECTED_RECEIVABLES),
    ROSCA("rosca", TextKey.ZAKAT_LINE_ROSCA);

    val label: String get() = uiText(labelKey)

    companion object {
        fun fromWire(wire: String): ZakatLineKind = entries.first { it.wire == wire }
    }
}

/** سطر سنة متثبّتة: اللي عليه زكاة والمطلوب منه (2.5% مقرّبة على السطر نفسه). */
data class ZakatYearLine(val kind: ZakatLineKind, val zakatableMinor: Halalas, val dueMinor: Halalas)

/**
 * سنة زكاة: من [hawlStart] لحد [dueAt] (نفس اليوم الهجري السنة الجاية). المعرّف = [dueAt].
 * مفتوحة لحد ما المستخدم **يثبّت** الحساب ([closedAt]) — ساعتها السطور والنصاب بيتحفظوا زي ما هما عشان سعر بكرة ما يغيّرش زكاة امبارح.
 */
data class ZakatYear(
    val id: Id,
    val hawlStart: IsoDate,
    val dueAt: IsoDate,
    val currency: Currency,
    val confirmedAt: String,
    val closedAt: String? = null,
    val nisabMinor: Halalas? = null,
    val lines: List<ZakatYearLine> = emptyList(),
) {
    val closed: Boolean get() = closedAt != null
    val dueMinor: Halalas get() = sumMoney(lines.map { it.dueMinor })
}

/**
 * دفعة زكاة على سطر أو أكتر من سنة. [transactionId] = العملية من الكشف (التصنيف «زكاة») — `null` = اتدفعت كاش من غير عملية.
 * عملية واحدة = دفعة واحدة؛ الدفع على كذا عملية = كذا دفعة بنفس السطور.
 */
data class ZakatPayment(
    val id: Id,
    val yearId: Id,
    val transactionId: Id?,
    val amountMinor: Halalas,
    val lines: List<ZakatLineKind>,
    val paidAt: IsoDate,
    val createdAt: String,
)

data class ZakatLineStatus(val kind: ZakatLineKind, val dueMinor: Halalas, val paidMinor: Halalas, val remainingMinor: Halalas) {
    val paid: Boolean get() = remainingMinor == 0L
}

/** المطلوب · اللي اتدفع منه · الباقي · و«صدقة زيادة» (اللي اتدفع فوق المطلوب — **ما بيتحسبش من السنة الجاية**). */
data class ZakatYearStatus(
    val dueMinor: Halalas,
    val paidMinor: Halalas,
    val remainingMinor: Halalas,
    val extraCharityMinor: Halalas,
    val lines: List<ZakatLineStatus>,
)

/**
 * الدفعات بالترتيب (تاريخ الدفع ثم وقت التسجيل ثم المعرّف)، وكل دفعة بتتوزع على سطورها **بترتيب السطور**
 * لحد اللي فاضل في كل سطر؛ الزيادة «صدقة زيادة». اختيار Claude — المالك يقدر يغيّره.
 */
fun zakatYearStatus(year: ZakatYear, payments: List<ZakatPayment>): ZakatYearStatus {
    val left = LinkedHashMap<ZakatLineKind, Halalas>().apply { year.lines.forEach { put(it.kind, it.dueMinor) } }
    var extra = 0L
    val ordered = payments.filter { it.yearId == year.id }.sortedWith(compareBy({ it.paidAt }, { it.createdAt }, { it.id }))
    for (p in ordered) {
        var rest = p.amountMinor
        for (kind in ZakatLineKind.entries) {
            if (kind !in p.lines || rest == 0L) continue
            val room = left[kind] ?: continue
            val take = minOf(room, rest)
            left[kind] = subtractMoney(room, take)
            rest = subtractMoney(rest, take)
        }
        extra = addMoney(extra, rest)
    }
    val lines = year.lines.map { ZakatLineStatus(it.kind, it.dueMinor, subtractMoney(it.dueMinor, left.getValue(it.kind)), left.getValue(it.kind)) }
    val due = year.dueMinor
    val remaining = sumMoney(lines.map { it.remainingMinor })
    return ZakatYearStatus(due, subtractMoney(due, remaining), remaining, extra, lines)
}
