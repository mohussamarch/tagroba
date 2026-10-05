package app.masroufy.core

/**
 * حاسبة الورث — المدخلات والنتيجة (OVERRIDES §69). **القانون بالبلد تلقائيًا، مش اختيار:**
 * - السعودية = نظام الأحوال الشخصية 1443هـ (مرسوم ملكي م/73) — المواد 197–244 (الإرث) و179–190 (الوصية).
 *   https://laws.boe.gov.sa/BoeLaws/Laws/LawDetails/4d72d829-947b-45d5-b9b5-ae5800d6bac2/1
 * - مصر = قانون المواريث 77 لسنة 1943 + قانون الوصية 71 لسنة 1946 — **من نسخ غير رسمية** (qadaya.net) ⇒ تتراجع على نسخة رسمية.
 *
 * حالة النص ساكت فيها ⇒ [InheritanceResult.NoText] («لا نص — اسأل المحكمة» — م251 السعودي) ومن غير تخمين.
 * حالة النسخة دي ما بتحسبهاش (ذوو الأرحام · الحمل · المفقود · المناسخات · التخارج) ⇒ [InheritanceResult.Unsupported].
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
    ;

    val label: String get() = uiText(nameKey)
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
 * المسألة. [heirs] الورثة **بالعدد** (زوجة 1 · ابن 2 …). [names] أسامي اختيارية بنفس الترتيب.
 * [distantRelatives] هل فيه أقارب من ذوي الأرحام (أولاد البنات · الخال · العمة …)؟ بيتسأل بس لما الزوج/الزوجة لوحدهم أو مفيش ورثة —
 * لأن الإجابة بتغيّر القسمة (السعودية م231/2 و234 · مصر م30–31). null = ما اتسألش.
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
enum class ShareBasis { FARD, RESIDUARY, FARD_AND_RESIDUARY, RADD, BLOCKED, NOTHING_LEFT }

/** سبب «لا نص». */
enum class NoTextReason(val textKey: TextKey) {
    GRANDFATHER_MIXED_SIBLINGS(TextKey.INHERIT_NO_TEXT_GF_MIXED),
}

/** سبب «غير مدعوم في النسخة دي». */
enum class UnsupportedReason(val textKey: TextKey) {
    COUNTRY(TextKey.INHERIT_UNSUPPORTED_COUNTRY),
    DISTANT_RELATIVES(TextKey.INHERIT_UNSUPPORTED_DISTANT),
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
}
