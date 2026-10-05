package app.masroufy.core

/**
 * نتيجة حاسبة الورث (OVERRIDES §69). **تحت كل نتيجة** ([disclaimer]): «حساب تقريبي للتخطيط، والقسمة الرسمية بصك حصر الورثة من المحكمة».
 */
sealed interface InheritanceResult {
    val disclaimer: String get() = uiText(TextKey.INHERIT_DISCLAIMER)

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
        fun heir(kind: HeirKind): HeirShare? = heirs.firstOrNull { it.kind == kind }
    }

    /** النص ساكت ⇒ «لا نص — اسأل المحكمة» (السعودية م251 · مصر: المادة اللي في [citation]). */
    data class NoText(val reason: NoTextReason, val citation: Citation) : InheritanceResult {
        val text: String get() = uiText(TextKey.INHERIT_NO_TEXT, uiText(reason.textKey))
    }

    /** النسخة دي ما بتحسبش الحالة دي — من غير تخمين. */
    data class Unsupported(val reason: UnsupportedReason) : InheritanceResult {
        val text: String get() = uiText(reason.textKey)
    }

    /** المدخلات غلط (5 زوجات · زوج وزوجة مع بعض · مبلغ بالسالب …). [args] قيم النص. */
    data class Invalid(val reason: InvalidReason, val args: List<String> = emptyList()) : InheritanceResult {
        val text: String get() = uiText(reason.textKey, *args.toTypedArray())
    }
}

/**
 * نصيب نوع وارث. [share] نصيبهم كلهم من نصيب الورثة · [perPerson] نصيب الواحد · [amountsMinor] مبلغ كل واحد بالترتيب
 * (ممكن يفرق هللة بين اتنين متساويين — الهللة ما بتتقسمش) · [names] الأسامي لو اتكتبت.
 */
data class HeirShare(
    val kind: HeirKind,
    val count: Int,
    val basis: ShareBasis,
    val share: Frac,
    val perPerson: Frac,
    val amountsMinor: List<Halalas>,
    val names: List<String?>,
) {
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
