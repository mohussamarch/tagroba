package app.masroufy.core

/**
 * حزمة البلد — OVERRIDES §40. **المكان بس اتجهز دلوقتي**، ومحتوى أي بلد جديدة بيتكتب
 * لما يبقى في بيانات حقيقية منها (رسايل بنوكها وكشوفها)، مش بالتخمين.
 *
 * اللي بيختلف من بلد للتانية:
 * - **العملة** واللغة المبدئية.
 * - **شجرة التصنيفات المبدئية** (مصر فيها «باركنج» و«سايس»، والسعودية مفيهاش).
 * - **قارئ رسايل البنك**: أنماط العملة وكلمات الصادر والوارد بتختلف، وقراية الرسايل أصلًا
 *   على أندرويد بس.
 * - **مخططات ملفات الكشوف** المتاحة.
 * - **اسم محفظة البنك المبدئية** (اسم المستخدم لمحفظته بيفضل بتاعه، ده الاسم الأول بس).
 *
 * اللي **ما بيختلفش**: كل الحساب المالي (spec/02) — الهللة والأنواع الاقتصادية والمطابقة.
 */
data class CountryPack(
    /** كود البلد ISO 3166-1 alpha-2. */
    val code: String,
    val currency: Currency,
    val defaultLanguage: Language,
    /** اسم ملف شجرة التصنيفات المبدئية جوه موارد التطبيق. */
    val categoryTreeAsset: String,
    /** قارئ رسايل البنك — `null` يعني القراية لسه مش متاحة في البلد دي. */
    val smsReader: BankSmsReader?,
    /** مخططات ملفات الكشوف اللي التطبيق بيعرف يقراها في البلد دي. */
    val statementSchemas: List<SchemaId>,
    /** الاسم المبدئي لمحفظة البنك في حساب جديد — المستخدم بيغيره. */
    val bankWalletName: String,
    val cashWalletName: String,
    /** منطقة الوقت (IANA) — حدود اليوم في قراية رسايل البنك. التطبيق الحالي كان مثبّت الرياض لكل البلاد. */
    val timeZone: String = "Asia/Riyadh",
    /** فروق شجرة التصنيفات عن السعودية (§64 — [buildCountryCategoryTree]). السعودية من غير فروق. */
    val categoryDelta: List<CategoryDeltaEdit> = emptyList(),
    /** نسخة العربي في البلد دي (OVERRIDES §66): فصحى مختصرة إلا لو اتحدد غير كده (مصر ⇒ المصري الحالي بالحرف). */
    val arabicVariant: ArabicVariant = ArabicVariant.MSA,
    /** أسماء بذور التصنيفات بالفصحى (§66 — [SAUDI_MSA_SEED_NAMES]) — **المعرّف ما بيتغيرش**. فاضية = أسماء الشجرة زي ما هي. */
    val seedNames: List<SeedNameChange> = emptyList(),
    /**
     * اللي لسه ناقص في الحزمة دي بالاسم (للمطور) — الحزمة الجاهزة قايمتها فاضية.
     * **ممنوع** تسيبها فاضية وحاجة ناقصة: ده بيخلي التطبيق يدّعي إنه بيدعم بلد وهو لأ (CLAUDE.md #15).
     */
    val gaps: List<String> = emptyList(),
) {
    val ready: Boolean get() = gaps.isEmpty()
}

/**
 * قارئ رسايل البنك. بلد جديدة = تنفيذ جديد، من غير ما أي شاشة تتغير.
 * التنفيذ لازم يرفض اللي مش عملية (رمز تحقق، عرض، عملية مرفوضة) بسبب مكتوب.
 */
interface BankSmsReader {
    /** معرّف ثابت بيتخزن مع العملية — ما يتغيرش. */
    val id: String

    fun parse(message: BankSmsMessage, lineNumber: Int): SmsParseResult
}

/** قارئ الرسايل السعودي — هو نفسه `parseBankSms` الموجود، من غير أي تغيير في السلوك. */
object SaudiBankSmsReader : BankSmsReader {
    override val id: String = "sa"

    override fun parse(message: BankSmsMessage, lineNumber: Int): SmsParseResult =
        parseBankSms(message, lineNumber)
}

/** السعودية — ده اللي التطبيق شغال بيه فعلًا دلوقتي. */
val SAUDI_PACK = CountryPack(
    code = "SA",
    currency = Currency.SAR,
    defaultLanguage = Language.AR,
    categoryTreeAsset = "categoryTree.json",
    smsReader = SaudiBankSmsReader,
    statementSchemas = listOf(SchemaId.PREVIEW, SchemaId.LEGACY, SchemaId.ALRAJHI_PDF, SchemaId.SMS),
    bankWalletName = "البنك",
    // فصحى (§66 — اختيار Claude، المالك يقدر يغيّره). مصر بتفضل «كاش».
    cashWalletName = "كاش",
    arabicVariant = ArabicVariant.MSA,
    seedNames = SAUDI_MSA_SEED_NAMES,
)

/**
 * مصر: قارئ الرسايل من عينات QNB (OVERRIDES §40.3) وقارئ كشف QNB (§40.4)، والشجرة = **شجرة السعودية + فروق بسيطة**
 * ([EGYPT_CATEGORY_DELTA] — رد المالك §64-٣؛ الفروق اختيار Claude والمالك بيزود أو يشيل).
 * ⚠️ سعر الدهب والفضة بالجنيه لسه مش في ملف الأسعار ⇒ نصاب الزكاة في مصر «غير متاح» (فجوة الزكاة §31.10، مش فجوة الحزمة).
 */
val EGYPT_PACK = CountryPack(
    code = "EG",
    currency = Currency.EGP,
    defaultLanguage = Language.AR,
    // نفس ملف شجرة السعودية + فروق مصر (`categoryDelta`)
    categoryTreeAsset = "categoryTree.json",
    smsReader = EgyptBankSmsReader,
    statementSchemas = listOf(SchemaId.PREVIEW, SchemaId.LEGACY, SchemaId.QNB_PDF, SchemaId.SMS),
    bankWalletName = "البنك",
    cashWalletName = "كاش",
    timeZone = "Africa/Cairo",
    categoryDelta = EGYPT_CATEGORY_DELTA,
    // نص مصر = النص المصري الحالي بالحرف، وبذورها ما اتغيرتش (§66)
    arabicVariant = ArabicVariant.EGYPTIAN,
)

/** البلاد اللي التطبيق يعرف عنها حاجة. اللي مش جاهزة بتقول ناقصها إيه في `gaps`. */
val COUNTRY_PACKS: Map<String, CountryPack> = mapOf(SAUDI_PACK.code to SAUDI_PACK, EGYPT_PACK.code to EGYPT_PACK)

val DEFAULT_COUNTRY_PACK: CountryPack = SAUDI_PACK

/** بيرجع حزمة البلد، والافتراضية لو البلد لسه مش مدعومة. */
fun countryPack(code: String?): CountryPack =
    COUNTRY_PACKS[code?.uppercase()] ?: DEFAULT_COUNTRY_PACK
