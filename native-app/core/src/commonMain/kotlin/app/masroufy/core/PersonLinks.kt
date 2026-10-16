package app.masroufy.core

/**
 * ربط عملية بشخص (جولة r05 — شكوى المالك 2026-10-16: «علّمت كتير على كذا عملية بيني وبين شخص إنه دين مستحق… ولما دخلت على الشخص الصفحة
 * فاضية»). أي طريق بيقول «العملية دي مع الشخص ده» بيكتب [PersonLink] (سطر لكل عملية — المعرّف = معرّف العملية) ومعاه أثرها في الدفتر حسب
 * المعنى: دين جديد · سداد دين مفتوح · نصيب هدية · أو «مع الشخص ده بس» من غير دين. صفحة الشخص بتقرا الروابط دي.
 * المبالغ بالهللة (Long) زي كل الدفتر. الأنواع هنا من غير سلوك تخزين.
 */
enum class PersonMeaning(val wire: String, val direction: Direction?) {
    /** فلوس طلعت منك: سلفة منك أو دفعت عنه ⇒ دين **لك عنده**. */
    LENT("lent", Direction.OUT),

    /** فلوس طلعت منك: سداد دين **عليك له** ⇒ تسوية على ديونه المفتوحة (الأقدم الأول). */
    REPAID("repaid", Direction.OUT),

    /** فلوس طلعت منك: هدية أو دعم ⇒ مفيش دين. */
    GAVE("gave", Direction.OUT),

    /** فلوس دخلتلك: سلفة أخدتها منه ⇒ دين **عليك له**. */
    BORROWED("borrowed", Direction.IN),

    /** فلوس دخلتلك: رجّعلك فلوسك ⇒ تسوية على ديونك المفتوحة عنده (الأقدم الأول). */
    COLLECTED("collected", Direction.IN),

    /** فلوس دخلتلك: هدية منه. */
    RECEIVED("received", Direction.IN),

    /** فلوس دخلتلك: دعم منه. */
    SUPPORTED("supported", Direction.IN),

    /** «مع الشخص ده بس» — العملية بتبان في سجله من غير دين ولا تغيير نوع. */
    WITH("with", null);

    companion object {
        fun fromWire(wire: String): PersonMeaning =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("unknown person meaning: $wire")
    }
}

/** مجموعة ربط العمليات بالأشخاص في النسخة الشاملة (`Backup.kt`): مستند لكل عملية — مش في نسخ التطبيق الحالي، وبتتكتب بس لو فيها حاجة. */
const val PERSON_LINKS_GROUP = "personLinks"

/** المعاني اللي تنفع لعملية في الاتجاه ده — الدين الأول، وبعده الهدية، و«مع الشخص ده بس» آخر واحد. */
fun meaningsFor(direction: Direction): List<PersonMeaning> = PersonMeaning.entries.filter { it.direction == null || it.direction == direction }

/** الشراء (فلوس طالعة ومش تحويل): «سلفة» معناها «دفعت عنه» ونوع العملية بيفضل شراء (المصروف بيقل بنصيبه — spec/02). */
fun isPurchaseShaped(t: Transaction): Boolean =
    t.observedDirection == Direction.OUT && !isTransferLike(t) && (t.economicKind == EconomicKind.PURCHASE || t.economicKind == EconomicKind.UNCLASSIFIED)

/** النوع اللي المعنى بيحطه على العملية (مؤكد) — null = النوع ما بيتغيرش. */
fun kindForMeaning(meaning: PersonMeaning, purchase: Boolean): EconomicKind? = when (meaning) {
    PersonMeaning.LENT -> if (purchase) null else EconomicKind.LOAN_GRANTED
    PersonMeaning.REPAID -> EconomicKind.DEBT_REPAID
    PersonMeaning.GAVE -> if (purchase) null else EconomicKind.SUPPORT_GIFT
    PersonMeaning.BORROWED -> EconomicKind.LOAN_RECEIVED
    PersonMeaning.COLLECTED -> EconomicKind.DEBT_COLLECTED
    PersonMeaning.RECEIVED -> EconomicKind.GIFT_RECEIVED
    PersonMeaning.SUPPORTED -> EconomicKind.SUPPORT_RECEIVED
    PersonMeaning.WITH -> null
}

/**
 * رابط عملية بشخص. [obligationId] · [allocationId] · [settlementIds] = اللي الربط نفسه كتبه في الدفتر (عشان «فك الربط» و«تراجع» يشيلوه هو
 * بس). [kindBefore] وأخواتها = نوع العملية قبل الربط لو الربط غيّره (بيرجع زي ما كان). [auto] = اتسجلت لوحدها من حساب مربوط بالشخص.
 */
data class PersonLink(
    val transactionId: Id,
    val personId: Id,
    val meaning: PersonMeaning,
    val amountMinor: Halalas,
    val createdAt: String,
    val obligationId: Id? = null,
    val allocationId: Id? = null,
    val settlementIds: List<Id> = emptyList(),
    val kindBefore: EconomicKind? = null,
    val kindConfirmedBefore: Boolean? = null,
    val reviewBefore: ReviewState? = null,
    /** النوع المقترح اللي الربط شاله (زي «ده استرداد؟») — بيرجع مع «فك الربط». */
    val suggestedBefore: EconomicKind? = null,
    val auto: Boolean = false,
)

/** نوع عملية جديدة «مع الشخص ده»: المعنى بيحدد النوع والنوع بيحدد الاتجاه (`AddTransaction`). «مع الشخص ده بس» ⇒ null (لازم معنى). */
fun operationKindFor(meaning: PersonMeaning): EconomicKind? = if (meaning == PersonMeaning.WITH) null else kindForMeaning(meaning, purchase = false)

/** الدين اللي المعنى بيعمله (null = مفيش دين جديد — سداد أو هدية أو «مع الشخص ده بس»). */
fun obligationKindFor(meaning: PersonMeaning): ObligationKind? = when (meaning) {
    PersonMeaning.LENT -> ObligationKind.RECEIVABLE
    PersonMeaning.BORROWED -> ObligationKind.LOAN_PAYABLE
    else -> null
}

/**
 * التصنيفات اللي معناها «فلوس مع شخص» ومعاها فروعها (رد المالك 2026-10-16 — OVERRIDES §103 · A1): **سلف · ديون · قروض · مستحقات · تحويل
 * لأشخاص** بس. المزروعة بمعرّفها ([PERSON_SEED_CATEGORY_IDS])، واللي المالك عملها بنفسه بكلمة كاملة من [PERSON_CATEGORY_WORDS] — عمرها ما
 * تبقى «العناية الشخصية» ولا أي تصنيف مزروع تاني. فروع «المستحقات» اللي النظام بيحطها لوحده (جمعيات · تمويل · تقسيط مشتريات — مع جهة مش
 * شخص) برّه (اختيار Claude — HANDOVER §6).
 */
fun personCategoryIds(categories: List<Category>): Set<Id> {
    val roots = categories.filter(::isPersonCategory).map { it.id }.toSet()
    return roots + categories.filter { it.parentId in roots && it.id !in DUES_SYSTEM_SUBS }.map { it.id }
}

/** «تحويلات › تحويل لأشخاص» (البذرة) و«المستحقات» (`DuesCategories`). */
val PERSON_SEED_CATEGORY_IDS: Set<Id> = setOf(seedCategoryId("تحويلات", "تحويل لأشخاص"), DuesCategories.ROOT)

private val DUES_SYSTEM_SUBS: Set<Id> = setOf(DuesCategories.ROSCAS, DuesCategories.FINANCING, DuesCategories.PURCHASE_PLANS)

/**
 * الكلمات (بعد التطبيع وشيل «ال» و«و» و«ل» و«ب») اللي بتخلّي تصنيف عمله المالك «فلوس مع شخص» — كلمة كاملة بالظبط، مش جزء من كلمة:
 * سلف · ديون · قروض · مستحقات (A1). «شخص/أشخاص» لوحدها **مش** كفاية (زي «هدايا لأشخاص») — لازم معاها «تحويل» ([PERSON_TRANSFER_WORDS]).
 */
val PERSON_CATEGORY_WORDS: Set<String> =
    setOf("سلف", "سلفه", "سلفيات", "دين", "ديون", "قرض", "قروض", "اقتراض", "استلاف", "مستحق", "مستحقات", "مستحقه")

/** «تحويل لأشخاص» اللي المالك عمله بنفسه: كلمة أولها «تحويل» + كلمة من دول. */
val PERSON_TRANSFER_WORDS: Set<String> = setOf("شخص", "اشخاص", "ناس")

/** المعاني اللي تنفع لشراء (نصيب شخص فيه): «دفعت عنه» · «هدية له» · «مع الشخص ده بس». */
val PURCHASE_MEANINGS: List<PersonMeaning> = listOf(PersonMeaning.LENT, PersonMeaning.GAVE, PersonMeaning.WITH)

/** قواعد الحساب ده اتمنعت على العملية دي؟ (المالك فك ربطها أو غيّر معناها — A2). */
fun TransferParty.skips(transactionId: Id): Boolean = transactionId in autoSkip

/** المعنى اللي بيتحط لوحده على تحويلات حساب مربوط بشخص («سجّل تحويلاته لوحده»): مستحقات · سلفة · هدية · دعم · حاجة تانية. */
enum class AutoMeaning(val wire: String) {
    DUES("dues"), LOAN("loan"), GIFT("gift"), SUPPORT("support"), OTHER("other");

    companion object {
        fun fromWire(wire: String): AutoMeaning? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * [AutoMeaning] ⇒ معنى حسب اتجاه التحويل: «مستحقات» = سداد (الداخل منه بيسدد اللي ليك عنده، والطالع ليه بيسدد اللي عليك) · «سلفة» = دين جديد
 * · «هدية»/«دعم» = من غير دين · «حاجة تانية» = مع الشخص ده بس.
 */
fun meaningForAuto(auto: AutoMeaning, direction: Direction): PersonMeaning {
    val incoming = direction == Direction.IN
    return when (auto) {
        AutoMeaning.DUES -> if (incoming) PersonMeaning.COLLECTED else PersonMeaning.REPAID
        AutoMeaning.LOAN -> if (incoming) PersonMeaning.BORROWED else PersonMeaning.LENT
        AutoMeaning.GIFT -> if (incoming) PersonMeaning.RECEIVED else PersonMeaning.GAVE
        AutoMeaning.SUPPORT -> if (incoming) PersonMeaning.SUPPORTED else PersonMeaning.GAVE
        AutoMeaning.OTHER -> PersonMeaning.WITH
    }
}

/** مفتاح حساب ضافه المالك بإيده (آخر 4 أرقام بس — قاعدة 11) — نفس شكل مفتاح طرف من غير اسم (`TransferParties.kt`). */
fun manualPartyKey(last4: String): String = "#$last4"

/** آخر 4 أرقام زي ما المالك كتبها (العربي والفارسي بيتحولوا لاتيني) — null لو مش 4 أرقام بالظبط. */
fun typedLast4(text: String): String? {
    val digits = text.trim().map { c ->
        when (c) {
            in '٠'..'٩' -> '0' + (c - '٠')
            in '۰'..'۹' -> '0' + (c - '۰')
            else -> c
        }
    }.joinToString("")
    return digits.takeIf(::isLast4)
}

/** 4 أرقام لاتيني بالظبط (§79.1). */
fun isLast4(text: String): Boolean = text.length == 4 && text.all { it in '0'..'9' }

/**
 * الطرف اللي اتقري من العملية هو الحساب المربوط ده؟ بالمفتاح، أو — لحساب مكتوب بالإيد ([manualPartyKey]) — بآخر 4 أرقام لوحدها
 * (الاسم اللي جه في الرسالة ممكن يختلف).
 */
fun partyMatches(party: TransferParty, ref: TransferPartyRef): Boolean =
    ref.key == party.key || (party.key.startsWith("#") && party.last4 != null && ref.last4 == party.last4)

private val ARTICLE_PREFIXES = listOf("وال", "بال", "لل", "ال", "و", "ل", "ب")

/**
 * التصنيف ده معناه «فلوس مع شخص»؟ المزروع بمعرّفه بس ([PERSON_SEED_CATEGORY_IDS]) — الاسم ما بيتبصلوش (عشان «العناية الشخصية»). اللي المالك
 * عمله: كلمة كاملة من [PERSON_CATEGORY_WORDS] بعد التطبيع، أو «تحويل» ومعاها كلمة من [PERSON_TRANSFER_WORDS] (زي «تحويل لأشخاص»).
 */
fun isPersonCategory(category: Category): Boolean {
    if (category.id in PERSON_SEED_CATEGORY_IDS) return true
    if (category.id.startsWith(SEED_CATEGORY_PREFIX)) return false
    val words = normalizeText(category.name).split(' ').filter { it.isNotEmpty() }
    val bare = words.flatMap { word -> listOfNotNull(word, ARTICLE_PREFIXES.firstOrNull { word.startsWith(it) && word.length - it.length >= 3 }?.let { word.removePrefix(it) }) }
    if (bare.any { it in PERSON_CATEGORY_WORDS }) return true
    return words.any { it.startsWith("تحويل") || it.startsWith("التحويل") } && bare.any { it in PERSON_TRANSFER_WORDS }
}

/** معرّفات التصنيفات المزروعة (`seedCategoryId` · المستحقات · الدخل · رسوم البنك) — اللي المالك بيعمله `category-…`. */
private const val SEED_CATEGORY_PREFIX = "cat-"
