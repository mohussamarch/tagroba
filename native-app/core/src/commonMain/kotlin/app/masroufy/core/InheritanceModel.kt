package app.masroufy.core

/**
 * حاسبة الورث — المدخلات والنتيجة (OVERRIDES §69). **القانون بالبلد تلقائيًا، مش اختيار:**
 * - السعودية = نظام الأحوال الشخصية 1443هـ (مرسوم ملكي م/73) — المواد 197–244 (الإرث) و179–190 (الوصية).
 *   https://laws.boe.gov.sa/BoeLaws/Laws/LawDetails/4d72d829-947b-45d5-b9b5-ae5800d6bac2/1
 * - مصر = قانون المواريث 77 لسنة 1943 (الوقائع المصرية عدد 92، 12 أغسطس 1943 — https://manshurat.org/node/12494) + قانون الوصية
 *   71 لسنة 1946 (الوقائع المصرية عدد 65، 1 يوليو 1946 — https://manshurat.org/node/12495). **اتراجع على صورة الجريدة الرسمية نفسها**
 *   (ثقة A− — الموقع اللي شايل الصور مش حكومي؛ م76 اتأكدت حرف بحرف من حكم الدستورية العليا 2021). جدول المراجعة في OVERRIDES §69.4.
 *
 * حالة النص ساكت فيها ⇒ [InheritanceResult.NoText] («لا نص — اسأل المحكمة» — م251 السعودي) ومن غير تخمين.
 * حالة النسخة دي ما بتحسبهاش (الحمل · المفقود · المناسخات · التخارج · ذوو أرحام أبعد من جيل واحد) ⇒ [InheritanceResult.Unsupported].
 */
enum class InheritanceLaw(val code: String) {
    SA("SA"), EG("EG");

    companion object {
        /** بلد المساحة (§41) ⇒ قانونها. بلد مالهاش قانون هنا ⇒ null (الحاسبة مش متاحة — مش بنخمّن). */
        fun of(countryCode: String?): InheritanceLaw? = entries.firstOrNull { it.code == countryCode?.uppercase() }
    }
}

/** النص القانوني اللي المادة منه. */
enum class LawSource(val nameKey: TextKey) {
    SA_PERSONAL_STATUS(TextKey.INHERIT_LAW_SA),
    EG_INHERITANCE(TextKey.INHERIT_LAW_EG_77),
    EG_BEQUEST(TextKey.INHERIT_LAW_EG_71),
}

/** «المادة كذا من كذا». [article] زي ما هو مكتوب («213/3» = الفقرة 3). */
data class Citation(val source: LawSource, val article: String) {
    val text: String get() = uiText(TextKey.INHERIT_CITATION, article, uiText(source.nameKey))
}

/**
 * الورثة اللي النسخة دي بتحسبهم (بالعدد). «الجد» = أبو الأب (الجد الصحيح). جيل واحد بس من أولاد الابن (ابن ابن · بنت ابن)
 * — الأنزل منهم مش في النسخة دي. [male] للقسمة «للذكر مثل حظ الأنثيين».
 * **ذوو الأرحام** (§69.4): 25 نوع في آخر القايمة (جيل واحد) — الخال والخالة والعمة بقوة القرابة (شقيق · لأب · لأم) لأن مصر م35
 * بتقدّم الأقوى. الأبعد (بنت العم · أولاد الخال · الجد أبو أم الأب …) مش في النسخة دي.
 */
enum class HeirKind(val nameKey: TextKey, val male: Boolean) {
    HUSBAND(TextKey.INHERIT_HEIR_HUSBAND, true),
    WIFE(TextKey.INHERIT_HEIR_WIFE, false),
    SON(TextKey.INHERIT_HEIR_SON, true),
    DAUGHTER(TextKey.INHERIT_HEIR_DAUGHTER, false),
    FATHER(TextKey.INHERIT_HEIR_FATHER, true),
    MOTHER(TextKey.INHERIT_HEIR_MOTHER, false),
    GRANDFATHER(TextKey.INHERIT_HEIR_GRANDFATHER, true),
    PATERNAL_GRANDMOTHER(TextKey.INHERIT_HEIR_PATERNAL_GRANDMOTHER, false),
    MATERNAL_GRANDMOTHER(TextKey.INHERIT_HEIR_MATERNAL_GRANDMOTHER, false),
    SON_SON(TextKey.INHERIT_HEIR_SON_SON, true),
    SON_DAUGHTER(TextKey.INHERIT_HEIR_SON_DAUGHTER, false),
    FULL_BROTHER(TextKey.INHERIT_HEIR_FULL_BROTHER, true),
    FULL_SISTER(TextKey.INHERIT_HEIR_FULL_SISTER, false),
    PATERNAL_BROTHER(TextKey.INHERIT_HEIR_PATERNAL_BROTHER, true),
    PATERNAL_SISTER(TextKey.INHERIT_HEIR_PATERNAL_SISTER, false),
    MATERNAL_BROTHER(TextKey.INHERIT_HEIR_MATERNAL_BROTHER, true),
    MATERNAL_SISTER(TextKey.INHERIT_HEIR_MATERNAL_SISTER, false),
    FULL_NEPHEW(TextKey.INHERIT_HEIR_FULL_NEPHEW, true),
    PATERNAL_NEPHEW(TextKey.INHERIT_HEIR_PATERNAL_NEPHEW, true),
    FULL_UNCLE(TextKey.INHERIT_HEIR_FULL_UNCLE, true),
    PATERNAL_UNCLE(TextKey.INHERIT_HEIR_PATERNAL_UNCLE, true),
    FULL_COUSIN(TextKey.INHERIT_HEIR_FULL_COUSIN, true),
    PATERNAL_COUSIN(TextKey.INHERIT_HEIR_PATERNAL_COUSIN, true),

    // ذوو الأرحام — جيل واحد (`InheritanceDistant.kt`): السعودية م232–236 · مصر م31–38. بيورثوا بس لو مفيش صاحب فرض ولا عاصب غير الزوجين
    DAUGHTER_SON(TextKey.INHERIT_HEIR_DAUGHTER_SON, true),
    DAUGHTER_DAUGHTER(TextKey.INHERIT_HEIR_DAUGHTER_DAUGHTER, false),
    SON_DAUGHTER_SON(TextKey.INHERIT_HEIR_SON_DAUGHTER_SON, true),
    SON_DAUGHTER_DAUGHTER(TextKey.INHERIT_HEIR_SON_DAUGHTER_DAUGHTER, false),
    FULL_SISTER_SON(TextKey.INHERIT_HEIR_FULL_SISTER_SON, true),
    FULL_SISTER_DAUGHTER(TextKey.INHERIT_HEIR_FULL_SISTER_DAUGHTER, false),
    PATERNAL_SISTER_SON(TextKey.INHERIT_HEIR_PATERNAL_SISTER_SON, true),
    PATERNAL_SISTER_DAUGHTER(TextKey.INHERIT_HEIR_PATERNAL_SISTER_DAUGHTER, false),
    FULL_BROTHER_DAUGHTER(TextKey.INHERIT_HEIR_FULL_BROTHER_DAUGHTER, false),
    PATERNAL_BROTHER_DAUGHTER(TextKey.INHERIT_HEIR_PATERNAL_BROTHER_DAUGHTER, false),
    MATERNAL_BROTHER_SON(TextKey.INHERIT_HEIR_MATERNAL_BROTHER_SON, true),
    MATERNAL_BROTHER_DAUGHTER(TextKey.INHERIT_HEIR_MATERNAL_BROTHER_DAUGHTER, false),
    MATERNAL_SISTER_SON(TextKey.INHERIT_HEIR_MATERNAL_SISTER_SON, true),
    MATERNAL_SISTER_DAUGHTER(TextKey.INHERIT_HEIR_MATERNAL_SISTER_DAUGHTER, false),
    MATERNAL_GRANDFATHER(TextKey.INHERIT_HEIR_MATERNAL_GRANDFATHER, true),
    MATERNAL_UNCLE_FULL(TextKey.INHERIT_HEIR_MATERNAL_UNCLE_FULL, true),
    MATERNAL_UNCLE_PATERNAL(TextKey.INHERIT_HEIR_MATERNAL_UNCLE_PATERNAL, true),
    MATERNAL_UNCLE_MATERNAL(TextKey.INHERIT_HEIR_MATERNAL_UNCLE_MATERNAL, true),
    MATERNAL_AUNT_FULL(TextKey.INHERIT_HEIR_MATERNAL_AUNT_FULL, false),
    MATERNAL_AUNT_PATERNAL(TextKey.INHERIT_HEIR_MATERNAL_AUNT_PATERNAL, false),
    MATERNAL_AUNT_MATERNAL(TextKey.INHERIT_HEIR_MATERNAL_AUNT_MATERNAL, false),
    PATERNAL_AUNT_FULL(TextKey.INHERIT_HEIR_PATERNAL_AUNT_FULL, false),
    PATERNAL_AUNT_PATERNAL(TextKey.INHERIT_HEIR_PATERNAL_AUNT_PATERNAL, false),
    PATERNAL_AUNT_MATERNAL(TextKey.INHERIT_HEIR_PATERNAL_AUNT_MATERNAL, false),
    MATERNAL_HALF_UNCLE(TextKey.INHERIT_HEIR_MATERNAL_HALF_UNCLE, true),
    ;

    val label: String get() = uiText(nameKey)

    /** الاسم المخزن في الحسبة المحفوظة (§69.3) — ثابت حتى لو ترتيب الأنواع اتغير. */
    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(wire: String): HeirKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** حاجة من التركة (اسم + قيمة تقريبية بالوحدة الصغرى). */
data class EstateItem(val name: String, val valueMinor: Halalas)

/**
 * الوصية الاختيارية. [toHeir] = لواحد من الورثة. [heirsConsent] = **كل** الورثة وافقوا بعد الوفاة — بيغطي الوصية لوارث
 * (السعودية م179) والزيادة عن الثلث (السعودية م190 · مصر 71/1946 م37). موافقة بعض الورثة بس مش في النسخة دي.
 */
data class Bequest(val amountMinor: Halalas, val toHeir: Boolean = false, val heirsConsent: Boolean = false)

/**
 * ابن أو بنت **مات قبل المتوفى** (أو معاه) وساب أولاد — للوصية الواجبة في مصر بس (71/1946 م76–79).
 * [sons]/[daughters] أولاده (جيل واحد). [givenInLifeMinor] اللي المتوفى أداه لهم في حياته من غير مقابل (بيتخصم — م76).
 */
data class PredeceasedChild(val isSon: Boolean, val sons: Int, val daughters: Int, val givenInLifeMinor: Halalas = 0)

/** ظروف النسخة دي ما بتحسبهاش — كل واحد بيرجّع «غير مدعوم» بسببه. */
enum class SpecialCircumstance {
    /** فيه حمل (جنين) ممكن يورث — السعودية م240 · مصر م42–44. */
    PREGNANCY,
    /** وارث مفقود — السعودية م238 · مصر م45. */
    MISSING_HEIR,
    /** وارث مات بعد المتوفى وقبل القسمة (المناسخات). */
    SUCCESSIVE_DEATHS,
    /** اتفاق بين الورثة إن حد يسيب نصيبه بمقابل (التخارج) — السعودية م243–245 · مصر م48. */
    TAKHARUJ,
}

/**
 * أولاد **شخص واحد** من ذوي الأرحام بيوصلوا بيه (بنت · بنت ابن · أخت · أخ · أخ أو أخت لأم). [parent] نوع الشخص ده (مثلًا [HeirKind.DAUGHTER]
 * = بنت واحدة من بنات المتوفى ماتت قبله). لازم في **السعودية** بس: بالتنزيل كل ولد بياخد نصيب أبوه/أمه هو بالذات (م235)، فلو أولاد البنات
 * من أكتر من بنت، القسمة بتفرق. في مصر القسمة على الأشخاص (م38) فالتوزيع ده مش بيغيّر حاجة. أولاد الأخ الشقيق/لأب **بنات بس** (ابنه عاصب).
 */
data class DistantBranch(val parent: HeirKind, val sons: Int, val daughters: Int)

/**
 * المسألة. [heirs] الورثة **بالعدد** (زوجة 1 · ابن 2 …). [names] أسامي اختيارية بنفس الترتيب.
 * [distantRelatives] هل فيه أقارب من ذوي الأرحام (أولاد البنات · الخال · العمة …)؟ بيتسأل بس لما الزوج/الزوجة لوحدهم أو مفيش ورثة —
 * لأن الإجابة بتغيّر القسمة (السعودية م231/2 و234 · مصر م30–31). null = ما اتسألش. لو أنواع ذوي الأرحام مكتوبة في [heirs] السؤال ده ما بيلزمش.
 * [distantBranches] أولاد كل شخص لوحده ([DistantBranch]) — بيتطلب في السعودية بس لما أولاد نفس النوع ممكن يكونوا من أكتر من شخص.
 */
data class InheritanceCase(
    val countryCode: String,
    val heirs: Map<HeirKind, Int>,
    val items: List<EstateItem>,
    val funeralMinor: Halalas = 0,
    val debtsMinor: Halalas = 0,
    val bequest: Bequest? = null,
    val predeceasedChildren: List<PredeceasedChild> = emptyList(),
    val distantRelatives: Boolean? = null,
    val special: Set<SpecialCircumstance> = emptySet(),
    val names: Map<HeirKind, List<String>> = emptyMap(),
    val distantBranches: List<DistantBranch> = emptyList(),
)

/** نوع الملاحظة اللي بتطلع مع النتيجة — كل واحدة بنصها ومادتها. */
enum class InheritanceNoteKind(val textKey: TextKey) {
    ORDER(TextKey.INHERIT_NOTE_ORDER),
    BLOCKED(TextKey.INHERIT_NOTE_BLOCKED),
    AWL(TextKey.INHERIT_NOTE_AWL),
    RADD(TextKey.INHERIT_NOTE_RADD),
    RADD_TO_SPOUSE(TextKey.INHERIT_NOTE_RADD_SPOUSE),
    UMARIYYA(TextKey.INHERIT_NOTE_UMARIYYA),
    GRANDFATHER_MOTHER_THIRD(TextKey.INHERIT_NOTE_GF_MOTHER_THIRD),
    TAKMILA(TextKey.INHERIT_NOTE_TAKMILA),
    WITH_DAUGHTERS(TextKey.INHERIT_NOTE_WITH_DAUGHTERS),
    RESIDUE_NONE(TextKey.INHERIT_NOTE_RESIDUE_NONE),
    MUSHTARAKA_SHARED(TextKey.INHERIT_NOTE_MUSHTARAKA_SHARED),
    MUSHTARAKA_DROPPED(TextKey.INHERIT_NOTE_MUSHTARAKA_DROPPED),
    GRANDFATHER_BLOCKS_SIBLINGS(TextKey.INHERIT_NOTE_GF_BLOCKS_SIBLINGS),
    GRANDFATHER_SHARES(TextKey.INHERIT_NOTE_GF_SHARES),
    GRANDFATHER_RESIDUE(TextKey.INHERIT_NOTE_GF_RESIDUE),
    GRANDFATHER_SIXTH(TextKey.INHERIT_NOTE_GF_SIXTH),
    DEBTS_EXCEED(TextKey.INHERIT_NOTE_DEBTS_EXCEED),
    BEQUEST_CAPPED(TextKey.INHERIT_NOTE_BEQUEST_CAPPED),
    BEQUEST_EXCESS_CONSENTED(TextKey.INHERIT_NOTE_BEQUEST_EXCESS_CONSENTED),
    BEQUEST_HEIR_VOID(TextKey.INHERIT_NOTE_BEQUEST_HEIR_VOID),
    BEQUEST_HEIR_CONSENTED(TextKey.INHERIT_NOTE_BEQUEST_HEIR_CONSENTED),
    BEQUEST_HEIR_VALID(TextKey.INHERIT_NOTE_BEQUEST_HEIR_VALID),
    WAJIBA(TextKey.INHERIT_NOTE_WAJIBA),
    WAJIBA_CAPPED(TextKey.INHERIT_NOTE_WAJIBA_CAPPED),
    WAJIBA_GIFT(TextKey.INHERIT_NOTE_WAJIBA_GIFT),
    NO_WAJIBA(TextKey.INHERIT_NOTE_NO_WAJIBA),
    /** ذوو الأرحام مع صاحب فرض أو عاصب ⇒ ما بيورثوش (السعودية م234 · مصر م31). */
    DISTANT_EXCLUDED(TextKey.INHERIT_NOTE_DISTANT_EXCLUDED),
    /** السعودية: {0} بياخد نصيب {1} (التنزيل — م235). */
    DISTANT_TANZIL(TextKey.INHERIT_NOTE_DISTANT_TANZIL),
    /** مصر: الترتيب بالصنف ثم الدرجة ثم القوة، وللذكر ضعف الأنثى (م31–38). */
    DISTANT_EGYPT_ORDER(TextKey.INHERIT_NOTE_DISTANT_EGYPT_ORDER),
    /** مصر: التلتين لقرابة الأب والتلت لقرابة الأم (م35). */
    DISTANT_SIDES(TextKey.INHERIT_NOTE_DISTANT_SIDES),
    /** الزوج/الزوجة بياخد نصيبه بس، والباقي لذوي الأرحام قبل الرد عليه (السعودية م231/2 و234/2 · مصر م30). */
    DISTANT_BEFORE_SPOUSE_RADD(TextKey.INHERIT_NOTE_DISTANT_SPOUSE),
    /** النص مش واضح ⇒ الحساب ممكن يختلف عن حكم المحكمة (رد المالك §69.3 — مسائل مصر الغامضة). */
    UNCLEAR_TEXT(TextKey.INHERIT_NOTE_UNCLEAR),
}

/**
 * ملاحظة على النتيجة: نوعها · المادة (null = النظام ما فيهوش نص بالمعنى ده، زي «مفيش وصية واجبة») · الورثة اللي فيها ثم القيم.
 * أسامي الورثة بتتقرا وقت العرض (عشان تتغير مع اللغة) — `{0}` و`{1}` للورثة الأول وبعدهم القيم.
 */
data class InheritanceNote(
    val kind: InheritanceNoteKind,
    val citation: Citation?,
    val heirs: List<HeirKind> = emptyList(),
    val values: List<String> = emptyList(),
) {
    val text: String get() = uiText(kind.textKey, *(heirs.map { it.label } + values).toTypedArray())
}

/** الوارث ورث بإيه. */
enum class ShareBasis { FARD, RESIDUARY, FARD_AND_RESIDUARY, RADD, BLOCKED, NOTHING_LEFT, DISTANT }

/** سبب «لا نص». */
enum class NoTextReason(val textKey: TextKey) {
    GRANDFATHER_MIXED_SIBLINGS(TextKey.INHERIT_NO_TEXT_GF_MIXED),
    /** السعودية م236: «الأقرب للميت» من نفس الجهة — وطرق العدّ المعروفة بتختلف في الحالة دي. */
    DISTANT_NEARNESS(TextKey.INHERIT_NO_TEXT_DISTANT_NEARNESS),
    /** السعودية م235: أكتر من قريب بصلات مختلفة في مكان نفس الشخص (خال شقيق وخال لأب …) — المادة ما بتقولش نصيبه يتقسم بينهم إزاي. */
    DISTANT_SAME_PLACE(TextKey.INHERIT_NO_TEXT_DISTANT_SAME_PLACE),
}

/** سبب «غير مدعوم في النسخة دي». */
enum class UnsupportedReason(val textKey: TextKey) {
    COUNTRY(TextKey.INHERIT_UNSUPPORTED_COUNTRY),
    /** «فيه أقارب تانيين» ومحدش منهم اتكتب في القايمة. */
    DISTANT_RELATIVES(TextKey.INHERIT_UNSUPPORTED_DISTANT_UNLISTED),
    /** السعودية: أولاد نفس النوع وممكن يكونوا من أكتر من شخص ⇒ لازم [InheritanceCase.distantBranches] (`{0}` = الشخص). */
    ASK_DISTANT_BRANCHES(TextKey.INHERIT_UNSUPPORTED_ASK_BRANCHES),
    /** مصر: ابن/بنت مات قبله وذوو الأرحام هم اللي بيورثوا — الواجبة معاهم مش في النسخة دي. */
    WAJIBA_WITH_DISTANT(TextKey.INHERIT_UNSUPPORTED_WAJIBA_DISTANT),
    ASK_DISTANT_RELATIVES(TextKey.INHERIT_UNSUPPORTED_ASK_DISTANT),
    NO_HEIRS(TextKey.INHERIT_UNSUPPORTED_NO_HEIRS),
    PREGNANCY(TextKey.INHERIT_UNSUPPORTED_PREGNANCY),
    MISSING_HEIR(TextKey.INHERIT_UNSUPPORTED_MISSING),
    SUCCESSIVE_DEATHS(TextKey.INHERIT_UNSUPPORTED_SUCCESSIVE),
    TAKHARUJ(TextKey.INHERIT_UNSUPPORTED_TAKHARUJ),
    PREDECEASED_SON_GRANDCHILDREN_INHERIT(TextKey.INHERIT_UNSUPPORTED_PREDECEASED_SON),
}

/** سبب «المدخلات غلط». */
enum class InvalidReason(val textKey: TextKey) {
    COUNT_RANGE(TextKey.INHERIT_INVALID_COUNT),
    WIVES_MAX(TextKey.INHERIT_INVALID_WIVES),
    HUSBAND_AND_WIFE(TextKey.INHERIT_INVALID_HUSBAND_WIFE),
    ONLY_ONE(TextKey.INHERIT_INVALID_ONLY_ONE),
    NO_ITEMS(TextKey.INHERIT_INVALID_NO_ITEMS),
    ITEM_NAME(TextKey.INHERIT_INVALID_ITEM_NAME),
    NEGATIVE_AMOUNT(TextKey.INHERIT_INVALID_NEGATIVE),
    TOO_LARGE(TextKey.INHERIT_INVALID_TOO_LARGE),
    PREDECEASED_NO_CHILDREN(TextKey.INHERIT_INVALID_PREDECEASED),
    /** أولاد كل شخص ([DistantBranch]) مش مطابقين للأعداد المكتوبة (`{0}` = الشخص). */
    DISTANT_BRANCHES(TextKey.INHERIT_INVALID_BRANCHES),
}
