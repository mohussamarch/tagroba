package app.masroufy.core

/**
 * أسامي اختبار المساعد — **كلها مخترعة** (المستودع عام): التصنيفات من **شجرة التطبيق الحقيقية** (حساب سعودي جديد)، والباقي أشخاص ومحلات
 * ومحافظ وأهداف وأحداث ومشاريع وفواتير وأصول وجمعيات وأقساط من غير أي بيانات حقيقية.
 */
internal object AssistTestLexicon {
    val saudiCategories: List<Category> by lazy { buildCountryCategoryTree(RealSeeds.tree, SAUDI_PACK).categories + GiftCategories.defaults() }

    val merchants = listOf(
        Merchant("m-marsa", "مقهى المرسى", normalizeText("مقهى المرسى"), aliases = listOf(normalizeText("كافيه المرسى"), normalizeText("Al Marsa"))),
        Merchant("m-noor", "أسواق النور", normalizeText("أسواق النور")),
    )
    val people = listOf(Person("p-ahmed", "أحمد"), Person("p-sara", "سارة"), Person("p-khaled", "خالد"), Person("p-fahd", "فهد"))
    val wallets = listOf(
        Wallet("w-bank", "الراجحي", Currency.SAR, "bank", 0, "2026-01-01"),
        Wallet("w-cash", "كاش", Currency.SAR, "cash", 0, "2026-01-01"),
    )
    val recurring = listOf(
        RecurringItem("r-elec", "فاتورة الكهرباء", "name:كهرباء", "bill", 1, 38_000, Currency.SAR, "2026-10-03", true, true),
        RecurringItem("r-rent", "الإيجار", "name:ايجار", "bill", 1, 250_000, Currency.SAR, "2026-11-01", true, true),
        RecurringItem("r-net", "فاتورة النت", "name:نت", "bill", 1, 23_000, Currency.SAR, "2026-10-12", true, true),
    )

    fun lexicon(people: List<Person> = this.people): AssistLexicon = AssistLexicon(
        categories = saudiCategories,
        merchants = merchants,
        people = people,
        wallets = wallets,
        goals = listOf(LexItem("g-travel", listOf("السفر"))),
        events = listOf(LexItem("e-wedding", listOf("الفرح")), LexItem("e-aqiqa", listOf("العقيقة")), LexItem("e-marriage", listOf("الزواج"))),
        projects = listOf(LexItem("pr-home", listOf("مشروع البيت")), LexItem("pr-finish", listOf("مشروع التشطيب"))),
        recurring = recurring,
        assets = listOf(LexItem("a-gold", listOf("الذهب")), LexItem("a-silver", listOf("الفضة"))),
        roscas = listOf(LexItem("ro-work", listOf("جمعية الشغل"))),
        plans = listOf(LexItem("pl-car", listOf("تمويل السيارة"))),
    )
}

/** صف اختبار: الكلام ⇒ النية (معرّف التصميم) + اختياري: الاسم الأساسي · الفترة · المبلغ · فيه كارت مستني. */
internal data class AssistCase(
    val text: String,
    val intent: String,
    val pending: Boolean = false,
    val subject: String? = null,
    val period: AssistPeriodKind? = null,
    val amount: Long? = null,
)
