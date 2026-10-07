package app.masroufy.core

/**
 * نتيجة حاسبة الورث (OVERRIDES §69). **تحت كل نتيجة** ([disclaimer]) نص المالك بالبلد (§69.3):
 * السعودية «… بصك حصر الورثة من المحكمة» · مصر «… بإعلام الوراثة من المحكمة». بلد مش معروفة ⇒ النص السعودي (الأصل).
 */
sealed interface InheritanceResult {
    /** قانون النتيجة — null لو البلد مالهاش قانون هنا. */
    val resultLaw: InheritanceLaw?

    val disclaimer: String get() = inheritanceDisclaimer(resultLaw)

    /**
     * اتحسبت. المبالغ بالوحدة الصغرى وكل قسمة مجموعها **بالظبط** قد الكل:
     * [grossMinor] = [funeralMinor] + [debtsMinor] + [wajibaMinor] + [bequestMinor] + [heirsMinor] (+ اللي ما اتدفعش لو الديون أكبر — بيبان في الملاحظات).
     * [heirs] لكل نوع وارث اتكتب (حتى المحجوب — بنصيب صفر وسببه في الملاحظات). أنصبتهم **من نصيب الورثة** ([heirsMinor]).
     * [items] قسمة كل حاجة لوحدها على الكل (التجهيز · الديون · الوصية الواجبة · الوصية · كل وارث).
     */
    data class Computed(
        val law: InheritanceLaw,
        val grossMinor: Halalas,
        val funeralMinor: Halalas,
        val debtsMinor: Halalas,
        val wajibaMinor: Halalas,
        val bequestMinor: Halalas,
        val heirsMinor: Halalas,
        val heirs: List<HeirShare>,
        val wajiba: List<WajibaShare>,
        val items: List<ItemSplit>,
        val notes: List<InheritanceNote>,
    ) : InheritanceResult {
        override val resultLaw: InheritanceLaw get() = law

        fun heir(kind: HeirKind): HeirShare? = heirs.firstOrNull { it.kind == kind }
    }

    /** النص ساكت ⇒ «لا نص — اسأل المحكمة» (السعودية م251 · مصر: المادة اللي في [citation]). */
    data class NoText(val reason: NoTextReason, val citation: Citation) : InheritanceResult {
        override val resultLaw: InheritanceLaw get() = if (citation.source == LawSource.SA_PERSONAL_STATUS) InheritanceLaw.SA else InheritanceLaw.EG

        val text: String get() = uiText(TextKey.INHERIT_NO_TEXT, uiText(reason.textKey))
    }

    /** النسخة دي ما بتحسبش الحالة دي — من غير تخمين. [args] قيم النص (الشخص في [UnsupportedReason.ASK_DISTANT_BRANCHES]). */
    data class Unsupported(val reason: UnsupportedReason, val law: InheritanceLaw? = null, val args: List<String> = emptyList()) : InheritanceResult {
        override val resultLaw: InheritanceLaw? get() = law

        val text: String get() = uiText(reason.textKey, *args.toTypedArray())
    }

    /** المدخلات غلط (5 زوجات · زوج وزوجة مع بعض · مبلغ بالسالب …). [args] قيم النص. */
    data class Invalid(val reason: InvalidReason, val args: List<String> = emptyList(), val law: InheritanceLaw? = null) : InheritanceResult {
        override val resultLaw: InheritanceLaw? get() = law

        val text: String get() = uiText(reason.textKey, *args.toTypedArray())
    }
}

/** التنبيه تحت النتيجة بالبلد (رد المالك §69.3): مصر «إعلام الوراثة» · السعودية وأي بلد تانية «صك حصر الورثة». */
fun inheritanceDisclaimer(law: InheritanceLaw?): String =
    uiText(if (law == InheritanceLaw.EG) TextKey.INHERIT_DISCLAIMER_EG else TextKey.INHERIT_DISCLAIMER)

/** نفس النتيجة بقانون البلد (عشان التنبيه تحتها يبقى بتاع بلدها). المحسوبة و«لا نص» قانونهم معروف أصلًا. */
internal fun InheritanceResult.withLaw(law: InheritanceLaw?): InheritanceResult = when (this) {
    is InheritanceResult.Unsupported -> if (this.law == null) copy(law = law) else this
    is InheritanceResult.Invalid -> if (this.law == null) copy(law = law) else this
    else -> this
}

/**
 * نصيب نوع وارث. [share] نصيبهم كلهم من نصيب الورثة · [personShares] نصيب كل واحد بالترتيب · [perPerson] نصيب الواحد لو كلهم
 * متساويين (في السعودية أولاد ذوي الأرحام من أشخاص مختلفين ممكن يختلفوا — م235 — ⇒ null) · [amountsMinor] مبلغ كل واحد بالترتيب
 * (ممكن يفرق هللة بين اتنين متساويين — الهللة ما بتتقسمش) · [names] الأسامي لو اتكتبت.
 */
data class HeirShare(
    val kind: HeirKind,
    val count: Int,
    val basis: ShareBasis,
    val share: Frac,
    val personShares: List<Frac>,
    val amountsMinor: List<Halalas>,
    val names: List<String?>,
) {
    val perPerson: Frac? get() = personShares.distinct().singleOrNull()

    val totalMinor: Halalas get() = amountsMinor.sum()
}

/**
 * الوصية الواجبة لأولاد ابن/بنت مات قبل المتوفى (مصر). [share] نصيبهم من التركة بعد التجهيز والديون (قبل الخصم لو اتدّوا في حياته)
 * · [sonsMinor]/[daughtersMinor] مبلغ كل ولد وكل بنت (للذكر مثل حظ الأنثيين — م76).
 */
data class WajibaShare(
    val child: PredeceasedChild,
    val share: Frac,
    val totalMinor: Halalas,
    val sonsMinor: List<Halalas>,
    val daughtersMinor: List<Halalas>,
)

/** مين بياخد من حاجة. */
sealed interface Claimant {
    data object Funeral : Claimant
    data object Debts : Claimant
    data object Bequest : Claimant
    /** حفيد من [childIndex] (ترتيبه في `predeceasedChildren`) — [son] ولد ولا بنت، و[index] ترتيبه بينهم. */
    data class Wajiba(val childIndex: Int, val son: Boolean, val index: Int) : Claimant
    data class Heir(val kind: HeirKind, val index: Int) : Claimant
}

/** اسم المستحق للعرض (بيتقرا وقت العرض عشان يتغير مع اللغة). */
val Claimant.label: String
    get() = when (this) {
        Claimant.Funeral -> uiText(TextKey.INHERIT_CLAIMANT_FUNERAL)
        Claimant.Debts -> uiText(TextKey.INHERIT_CLAIMANT_DEBTS)
        Claimant.Bequest -> uiText(TextKey.INHERIT_CLAIMANT_BEQUEST)
        is Claimant.Wajiba -> uiText(TextKey.INHERIT_CLAIMANT_WAJIBA)
        is Claimant.Heir -> kind.label
    }

data class ClaimantAmount(val claimant: Claimant, val amountMinor: Halalas)

/** قسمة حاجة واحدة: مجموع [parts] = [valueMinor] بالظبط. اللي نصيبه صفر في الحاجة دي ما بيتكتبش. */
data class ItemSplit(val name: String, val valueMinor: Halalas, val parts: List<ClaimantAmount>) {
    fun of(claimant: Claimant): Halalas = parts.filter { it.claimant == claimant }.sumOf { it.amountMinor }
}
