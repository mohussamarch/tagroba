package app.masroufy.core

/**
 * الكيانات — نقل `src/domain/entities/types.ts` بنفس الحقول ونفس أسماء التخزين في فايربيز.
 * أنواع بس من غير سلوك. المبالغ `Long` بالهللة و**موجبة دايمًا** (الاتجاه في `observedDirection`).
 */
typealias Id = String

enum class ReviewState(val wire: String) {
    CONFIRMED("confirmed"), SUGGESTED("suggested"), NEEDS_REVIEW("needs_review");

    companion object {
        fun fromWire(wire: String): ReviewState = entries.first { it.wire == wire }
    }
}

data class Transaction(
    val id: Id,
    val occurredAt: IsoDate,
    /** "day" أو "minute" — بعض المصادر بتدي اليوم من غير ساعة. */
    val datePrecision: String,
    val sourceOrder: Int,
    val economicKind: EconomicKind,
    /** المستخدم أكد النوع بنفسه ⇒ ما يتكتبش فوقه آليًا. */
    val economicKindConfirmed: Boolean,
    /** اتجاه السيولة في المصدر — حقيقة بنكية، ثابت مهما النوع اتغير. */
    val observedDirection: Direction,
    val amountMinor: Halalas,
    val currency: Currency,
    val categoryConfirmed: Boolean,
    val excludedFromBudget: Boolean,
    val reviewState: ReviewState,
    val isCashTagged: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val sourceTime: String? = null,
    /** المبلغ الأصلي من المصدر لو المستخدم عدّل المبلغ (OVERRIDES §32). */
    val originalAmountMinor: Halalas? = null,
    val merchantId: Id? = null,
    val categoryId: Id? = null,
    val note: String? = null,
    val walletId: Id? = null,
    /** الطرف التاني في التحويل الداخلي (ARCHITECTURE §17). */
    val transferToWalletId: Id? = null,
    /** الرصيد اللي أعلنه المصدر بعد الحركة (OVERRIDES §7-ب). */
    val statedBalanceMinor: Halalas? = null,
    val rawDescription: String? = null,
    val rawMerchantName: String? = null,
    val sourceCategory: String? = null,
    val sourceOperationType: String? = null,
    /**
     * نوع **مقترح** مستني تأكيد المالك — النوع نفسه ([economicKind]) بيفضل «غير محدد» لحد ما يأكد (§75-6 · §77-D):
     * `REFUND_RECEIVED` = «ده استرداد؟» · `INTERNAL_TRANSFER` = «دي العملية اللي رجعت؟ نلغي الاتنين؟». حقل منفصل عن قصد: الاقتراح
     * ما بيلمسش `economicKind` (التطبيق القديم بيرفض نسخة فيها «استرداد» مؤكد — §55). لحد ما يتجاوب **ما بيتحسبش بأي نوع** حتى في
     * المجاميع التقريبية (`withEstimatedKinds` — مش «دخل» ولا «مصروف»)، وأي نوع المالك يحدده بإيده بيشيله.
     */
    val suggestedKind: EconomicKind? = null,
    /**
     * §77-D: العملية دي **رجوع** عملية تانية ([reversalOfId]) والاتنين اتلغوا (تحويل داخلي مؤكد) — أو العملية دي **الأصلية** اللي رجعت
     * ([reversedById]). آخر الاسم `Id` عن قصد: قص أرقام الحسابات وقت الكتابة (`storeForm` هنا و`sanitize` في التطبيق القديم) ما بيلمسش
     * الحقل اللي آخره `Id` — المعرّف فيه أرقام كتير ورا بعض وكان هيتقص فيتكسر الربط.
     */
    val reversalOfId: Id? = null,
    val reversedById: Id? = null,
    /** §75-12: المبلغ الأجنبي بالوحدة الصغرى **بتاعة [foreignCurrency]** (`currencyDecimals` — الدولار 2 · الدينار الكويتي 3 · الين 0). */
    val foreignAmountMinor: Long? = null,
    /** §75-12: العملة الأجنبية (ISO 4217) — [amountMinor] بعملة المحفظة (المبلغ المحلي اللي المالك كتبه). */
    val foreignCurrency: String? = null,
    /**
     * §77-D: النوع اللي **المالك كان أكده** على الأصلية قبل ما تتلغي مع اللي رجعت (بيتكتب بس لو كان مؤكد) — التراجع والتصليح بيرجّعوه هو
     * بالظبط بدل «غير محددة». الأصلية اللي نوعها ما كانش مؤكد (الإلغاء لوحده) ⇒ null وبترجع «غير محددة».
     */
    val kindBeforeReversal: EconomicKind? = null,
    /**
     * §6.5-14 «عمليات تابعة»: الفلوس الداخلة دي **تابعة** للمصروف ده ⇒ ما بتتحسبش دخل ولا بتنقّص المصروف في يومها — المصروف نفسه بيتحسب
     * بالصافي ([linkedInflowMinor]). رصيد المحفظة ما بيتغيرش (الفلوس دخلت فعلًا). آخر الاسم `Id` عن قصد (زي [reversalOfId]).
     */
    val linkedExpenseId: Id? = null,
    /** §6.5-14: على المصروف — مجموع العمليات التابعة ليه (بيتحسب من جديد مع كل ربط وفك) ⇒ المصروف = المبلغ − ده (`netAfterLinks`). */
    val linkedInflowMinor: Halalas? = null,
    /**
     * §82-13 (مراجعة 0.3): علامة صريحة لسؤال «سلفة ولا دعم؟» على «دعم» قديم — true = السؤال مفتوح (بتتكتب أول ما يتلاقي) · false = المالك
     * جاوب · null = لسه ما اتفحصش (الحد بآخر تعديل [LEGACY_AUTO_SUPPORT_UNTIL]). بتتكتب لو موجودة بس ⇒ المستندات والنسخ القديمة زي ما هي.
     * من غيرها أي تعديل بعد كده (زي «طبّق القواعد على كل السجل») كان بيحرّك آخر تعديل فالسؤال يختفي.
     */
    val supportAskPending: Boolean? = null,
    /**
     * A2/F3 (r05 — R5 · N2): المالك فك ربط العملية دي بشخص، أو مسح الدين اللي اتعمل منها، أو غيّر معناها بإيده ⇒ قواعد الحسابات المربوطة
     * («سجّل تحويلاته لوحده» · «مستحقات دايمًا») عمرها ما ترجع تربطها لوحدها — مهما الحساب اتقرر أو اتنسى أو اتعاد بعد كده (العلامة على
     * العملية نفسها، مش على قرار الحساب). بيتكتب لو true بس.
     */
    val personRulesOff: Boolean? = null,
    /**
     * «حدّث الرصيد» (`BalanceCheckpoint.kt`): العملية دي **جزء المالك فصله من «فرق مش معروف»** للنقطة دي (قسّمه على تصنيف حقيقي) ⇒ بتتحسب
     * قبل النقطة مباشرة، فبتشرح الفرق ومش بتغيّر الرصيد اللي المالك أكده. بيتكتب لو موجود بس، وآخره `Id` ⇒ قص الأرقام ما بيلمسوش.
     * النقطة لو اتمسحت ⇒ الحقل بيتشال والعملية بتفضل عادية.
     */
    val explainsCheckpointId: Id? = null,
    /**
     * إجابة المالك 2026-10-15 (OVERRIDES §91-2): رسالة بنك يومها جوه أيام كشف اتستورد للمحفظة، ومبلغها **مش في الكشف** ⇒ بتتسجل وعليها العلامة
     * دي «مش في الكشف» (`markStatementCovered`). true بس — بتتكتب لو موجودة بس ⇒ المستندات والنسخ القديمة زي ما هي.
     */
    val notInStatement: Boolean? = null,
    /**
     * r051 (تقسيط المشتريات — `BnplCharges`): المالك ردّ على لوحة «ادفع بعدين» للخصم ده («تخطّي» · «مش قسط — عملية عادية») ⇒ ما يتسألش
     * تاني. true بس — بتتكتب لو موجودة بس ⇒ المستندات القديمة زي ما هي.
     */
    val bnplDecided: Boolean? = null,
    /**
     * PLAN-0.6 §7: المالك علّم المشترى ده «مستني استرداد» (مصاريف شغل هتترجع) ⇒ **ما بيتحسبش من مصروفه** ([personalShareOf]) لحد ما
     * الاسترداد يتربط بيه (`LinkInflows` — العلامة بتتشال، والمصروف بيتحسب بالصافي). true بس — بتتكتب لو موجودة بس.
     */
    val awaitingRefund: Boolean? = null,
    /**
     * r06-1b3 (PLAN-0.6 §7 — «استرداد مصاريف» لأكتر من مشترى): على الداخلة اللي اتوزعت على أكتر من مصروف — «معرّف-المصروف=المبلغ بالهللة»
     * لكل مصروف ([linkedSharesOf]). [linkedExpenseId] = أول واحد. null = الداخلة كلها لمصروف واحد (الشكل القديم). بيتكتب لو موجود بس.
     */
    val linkedSplit: List<String>? = null,
    /**
     * r06-darahim (PLAN-0.6 §12-3): «برّه التقارير» — المجاميع وأعلى صرف والتوزيع ما بتحسبهاش. منفصلة عن «برّه الميزانية»
     * ([excludedFromBudget]). null = **زي القديم**: العلامة القديمة الواحدة بتشيلها من الاتنين ([isExcludedFrom]). بتتكتب لو موجودة بس.
     */
    val excludedFromReports: Boolean? = null,
    /** r06-darahim (§12-2): وجبة المطعم (علامة مش تصنيف) — مقترحة من ساعة العملية، والمالك يغيّرها أو يشيلها. null = مفيش. */
    val meal: Meal? = null,
    /**
     * r06-darahim (§12-5): سؤال «مكرر؟» — true = مفتوح (خصم تاني من نفس المحل في خلال دقيقة) · false = المالك قال «الاتنين صح».
     * null = ما اتسألش. عمرها ما بتمسح حاجة لوحدها. بتتكتب لو موجودة بس.
     */
    val duplicateAskPending: Boolean? = null,
    /**
     * r06-delete (OVERRIDES §118-1): المصدر اللي العملية كانت تابعة له اتمسح بـ«امسحه وسيب العمليات» ⇒ «النوع:الاسم» (مثلًا «wallet:حساب»).
     * العملية نفسها بتفضل زي ما هي (المجاميع ما بتتغيرش). null = مفيش. بتتكتب لو موجودة بس.
     */
    val sourceRemoved: String? = null,
)

/** r06-darahim (§12-2): وجبة على عملية مطعم — علامة للعرض، مش تصنيف. */
enum class Meal(val wire: String) {
    BREAKFAST("breakfast"), LUNCH("lunch"), DINNER("dinner"),

    /** المالك شال الوجبة بإيده ⇒ الاقتراح ما يرجعش. */
    NONE("none");

    companion object {
        fun fromWire(wire: String): Meal = entries.first { it.wire == wire }
    }
}

/** مكان العلامة (§12-3): الميزانيات بتقرا «برّه الميزانية» · التقارير والمجاميع وأعلى صرف بتقرا «برّه التقارير». */
enum class ExclusionScope { BUDGET, REPORTS }

/** العلامة القديمة الواحدة ([Transaction.excludedFromBudget] من غير [Transaction.excludedFromReports]) = برّه الاتنين. */
fun Transaction.isExcludedFrom(scope: ExclusionScope): Boolean = when (scope) {
    ExclusionScope.BUDGET -> excludedFromBudget
    ExclusionScope.REPORTS -> excludedFromReports ?: excludedFromBudget
}

enum class AllocationKind(val wire: String) {
    RECEIVABLE("receivable"), GIFT("gift");

    companion object {
        fun fromWire(wire: String): AllocationKind = entries.first { it.wire == wire }
    }
}

data class PersonAllocation(
    val id: Id,
    val transactionId: Id,
    val personId: Id,
    val allocationKind: AllocationKind,
    val amountMinor: Halalas,
    val currency: Currency,
    /**
     * §88: «دعم» ([AllocationKind.GIFT]) جوه عملية نوعها مش مصروف (الزيادة على سداد دين عليك — §82-19) ⇒ **بيتحسب مصروف** (`givenAwayShareOf`).
     * بيتخزن بس لو true ⇒ أي تخصيص قديم بيتكتب هو هو، والتطبيق القديم بيقرا الحقل الزيادة وبيسيبه.
     */
    val countedAsSpend: Boolean = false,
)

enum class ObligationKind(val wire: String) {
    RECEIVABLE("receivable"), LOAN_PAYABLE("loan_payable"), CUSTODY_PAYABLE("custody_payable");

    companion object {
        fun fromWire(wire: String): ObligationKind = entries.first { it.wire == wire }
    }
}

data class Obligation(
    val id: Id,
    val personId: Id,
    /** null = دين قديم من كاش قبل التطبيق (OVERRIDES §27). */
    val originTransactionId: Id?,
    val kind: ObligationKind,
    val originalMinor: Halalas,
    val currency: Currency,
    /**
     * §82-19 «أمانة ليك عنده»: دين **ليك** ([ObligationKind.RECEIVABLE]) أصله زيادة دفعتها فوق اللي عليك وهو شايلها لك. بيتخزن بنفس نوع
     * «لك عنده» (التطبيق القديم ونسخته الشاملة بيقبلوا الأنواع التلاتة بس، وبيقروه دين ليك عادي) + الحقل ده **لو true بس**.
     */
    val custodyHeld: Boolean = false,
    /** r06-debts — «السبب» اللي اتكتب في «ضيف دين» (اختياري، 80 حرف بالكتير). بيتكتب لو موجود بس ⇒ أي دين قديم ونسخته هي هي. */
    val note: String? = null,
    /** فحص 0.5.3 — الدين اتعمل من «ضيف دين» بـ«نقدًا» (عملية كاش اتعملت معاه) ⇒ السطر «دين نقدًا» مش «من عملية». بيتكتب لو true بس. */
    val cashOrigin: Boolean = false,
)

/** [fx] = اتسدد بعملة البلد التانية (§111-4): المدفوع بعملته والسعر — [amountMinor] دايمًا بعملة الدين بعد التحويل. */
data class Settlement(val id: Id, val transactionId: Id, val obligationId: Id, val amountMinor: Halalas, val fx: SettlementFx? = null)
