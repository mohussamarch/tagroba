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
    cashWalletName = "كاش",
)

/**
 * مصر — **ناقصة لسه**: قارئ الرسايل خلص من عينات QNB اللي بعتها المالك (OVERRIDES §40.3)،
 * لكن شجرة التصنيفات المصرية (باركنج، سايس…) وقارئ كشف QNB لسه ما اتعملوش.
 */
val EGYPT_PACK = CountryPack(
    code = "EG",
    currency = Currency.EGP,
    defaultLanguage = Language.AR,
    // ⚠️ دي شجرة السعودية لحد ما تتعمل شجرة مصر
    categoryTreeAsset = "categoryTree.json",
    smsReader = EgyptBankSmsReader,
    statementSchemas = listOf(SchemaId.PREVIEW, SchemaId.LEGACY, SchemaId.SMS),
    bankWalletName = "البنك",
    cashWalletName = "كاش",
    gaps = listOf("categoryTree", "statementReader"),
)

/** البلاد اللي التطبيق يعرف عنها حاجة. اللي مش جاهزة بتقول ناقصها إيه في `gaps`. */
val COUNTRY_PACKS: Map<String, CountryPack> = mapOf(SAUDI_PACK.code to SAUDI_PACK, EGYPT_PACK.code to EGYPT_PACK)

val DEFAULT_COUNTRY_PACK: CountryPack = SAUDI_PACK

/** بيرجع حزمة البلد، والافتراضية لو البلد لسه مش مدعومة. */
fun countryPack(code: String?): CountryPack =
    COUNTRY_PACKS[code?.uppercase()] ?: DEFAULT_COUNTRY_PACK
