package app.masroufy.core

/**
 * الكلمة العامة ⇒ تصنيف البذرة («قهوة» ⇒ «مطاعم وقهوة › قهوة ومشروبات») — لما المستخدم ما كتبش اسم تصنيفه بالظبط. المعرّف بنفس قاعدة
 * الشجرة (`cat-` + الاسم القديم بعد `normalizeText` — §66 المعرّف ما بيتغيرش لما الاسم يتغير)، وأكتر من معرّف = أول واحد موجود عند
 * المستخدم (مصر: «جوال» ⇒ «موبايل» · «مواقف وسايس» ⇒ «باركنج»). **التصنيف لازم يبقى موجود في شجرة المستخدم** — غير كده مفيش تصنيف.
 */
private fun slugOf(text: String) = normalizeText(text).replace(' ', '-').lowercase()

fun seedCategoryId(main: String, sub: String? = null): Id = "cat-" + slugOf(main) + (sub?.let { "--" + slugOf(it) } ?: "")

private fun ids(vararg pairs: Pair<String, String?>): List<Id> = pairs.map { seedCategoryId(it.first, it.second) }

private val SEED_WORDS: List<Pair<List<String>, List<Id>>> = listOf(
    listOf("قهوه", "قهوة", "كافي", "كوفي", "كابتشينو", "لاتيه", "اسبريسو", "coffee", "latte") to ids("مطاعم وقهوة" to "قهوة ومشروبات"),
    listOf("مطعم", "مطاعم", "غدا", "غداء", "عشا", "عشاء", "فطور", "فطار", "اكل", "وجبه", "شاورما", "برجر", "بيتزا", "كبسه", "مندي", "restaurant", "lunch", "dinner", "breakfast", "food")
        to ids("مطاعم وقهوة" to "مطاعم"),
    listOf("حلويات", "حلا", "كيك", "مخبز", "مخبوزات", "دونات", "bakery", "dessert") to ids("مطاعم وقهوة" to "حلويات ومخبوزات"),
    listOf("بقاله", "سوبرماركت", "ماركت", "مقاضي", "تموين", "grocery", "groceries", "supermarket") to ids("بقالة وسوبرماركت" to null),
    listOf("خضار", "خضروات", "فواكه", "فاكهه", "vegetables", "fruit") to ids("بقالة وسوبرماركت" to "خضار وفواكه"),
    listOf("لحم", "لحوم", "فراخ", "دجاج", "سمك", "meat", "chicken") to ids("بقالة وسوبرماركت" to "لحوم ودواجن وأسماك"),
    listOf("كهربا", "كهرباء", "electricity") to ids("المنزل" to "كهرباء"),
    listOf("مياه", "مويه", "water") to ids("المنزل" to "مياه"),
    listOf("غاز", "انبوبه", "gas") to ids("المنزل" to "غاز"),
    listOf("ايجار", "اجار", "rent") to ids("المنزل" to "إيجار"),
    listOf("صيانه", "سباك", "سباكه", "كهربائي", "plumber") to ids("المنزل" to "صيانة وسباكة"),
    listOf("تنظيف", "منظفات", "cleaning") to ids("المنزل" to "تنظيف"),
    // «عاملة» مش هنا عن قصد («الميزانية عاملة إيه» = حالها إيه)
    listOf("شغاله", "خادمه", "عماله", "maid") to ids("المنزل" to "عمالة منزلية"),
    listOf("جوال", "موبايل", "رصيد", "باقه", "mobile") to ids("اتصالات" to "جوال", "اتصالات" to "موبايل"),
    listOf("نت", "انترنت", "واي", "wifi", "internet") to ids("اتصالات" to "إنترنت منزلي"),
    listOf("نتفلكس", "شاهد", "netflix", "osn") to ids("اشتراكات رقمية" to "بث وأفلام"),
    listOf("سبوتيفاي", "انغامي", "spotify", "anghami") to ids("اشتراكات رقمية" to "موسيقى"),
    listOf("مخالفه", "مخالفات", "ساهر") to ids("السيارة" to "مخالفات السيارة"),
    listOf("مدرسه", "مدارس", "school") to ids("الأسرة والأطفال" to "مدارس ورسوم دراسية"),
    listOf("حضانه", "nursery") to ids("الأسرة والأطفال" to "حضانة"),
    listOf("حفاضات", "بامبرز", "diapers") to ids("الأسرة والأطفال" to "مستلزمات أطفال"),
    listOf("بنزين", "بانزين", "وقود", "fuel", "petrol") to ids("السيارة" to "وقود"),
    listOf("موقف", "مواقف", "باركنج", "parking") to ids("السيارة" to "مواقف وسايس", "السيارة" to "باركنج"),
    listOf("سايس") to ids("السيارة" to "سايس", "السيارة" to "مواقف وسايس"),
    listOf("ميكانيكي", "زيت", "غسيل", "mechanic") to ids("السيارة" to "صيانة السيارة"),
    listOf("تاكسي", "اوبر", "كريم", "uber", "careem", "taxi") to ids("السيارة" to "تاكسي وتطبيقات"),
    listOf("مترو", "باص", "اتوبيس", "مواصلات", "metro", "bus") to ids("السيارة" to "مواصلات عامة"),
    listOf("ملابس", "هدوم", "لبس", "clothes") to ids("العناية الشخصية" to "ملابس"),
    listOf("جزمه", "حذاء", "شوز", "shoes") to ids("العناية الشخصية" to "أحذية"),
    listOf("عطر", "عطور", "perfume") to ids("العناية الشخصية" to "عطور"),
    listOf("حلاق", "حلاقه", "barber") to ids("العناية الشخصية" to "حلاقة"),
    listOf("صالون", "كوافير", "تجميل", "salon") to ids("العناية الشخصية" to "تجميل"),
    listOf("صيدليه", "دواء", "ادويه", "علاج", "pharmacy", "medicine") to ids("صحة وصيدليات" to "صيدلية"),
    listOf("دكتور", "طبيب", "مستشفى", "عياده", "doctor", "hospital", "clinic") to ids("صحة وصيدليات" to "مستشفى وعيادة"),
    listOf("اسنان", "dentist") to ids("صحة وصيدليات" to "أسنان"),
    listOf("سينما", "cinema", "movie") to ids("ترفيه" to "سينما"),
    listOf("العاب", "بلايستيشن", "games") to ids("ترفيه" to "ألعاب"),
    listOf("جيم", "نادي", "gym") to ids("ترفيه" to "رياضة ونوادي"),
    listOf("طيران", "flight") to ids("سفر" to "طيران"),
    listOf("فندق", "hotel") to ids("سفر" to "فنادق وسكن"),
    listOf("كتاب", "كتب", "books") to ids("تعليم وتدريب" to "كتب"),
    listOf("كورس", "دوره", "course") to ids("تعليم وتدريب" to "دورات أونلاين"),
    listOf("صدقه", "تبرع", "charity") to ids("تبرعات" to "صدقة"),
    listOf("هديه", "هدايا", "gift") to listOf(GiftCategories.ROOT),
    listOf("تسوق", "شوبنج", "امازون", "shopping", "amazon") to ids("التسوق" to "تسوق إلكتروني"),
    listOf("الكترونيات", "شاحن", "سماعه", "electronics", "charger") to ids("التسوق" to "إلكترونيات وأجهزة"),
)

/** الكلمة (بعد التوحيد ومن غير «ال») ⇒ معرّفات البذرة المرشحة بالترتيب. */
internal val SEED_WORD_INDEX: Map<String, List<Id>> = buildMap {
    for ((words, targets) in SEED_WORDS) for (w in words) put(assistNormalize(w).removePrefix("ال").ifEmpty { assistNormalize(w) }, targets)
}

/** كلمات المحفظة العامة: «كاش» ⇒ محفظة الكاش · «البنك/البطاقة/مدى» ⇒ حساب البنك. */
enum class WalletWord { CASH, BANK }

private fun normSet(vararg w: String) = w.map(::assistNormalize).toSet()

internal val ASSIST_CASH_WORDS = normSet("كاش", "نقد", "نقدا", "نقدي", "نقدًا", "cash", "كاشا", "بالكاش")
internal val ASSIST_BANK_WORDS = normSet(
    "بنك", "البنك", "بطاقه", "البطاقه", "كارت", "الكارت", "الفيزا", "فيزا", "مدى", "مدي", "card", "bank", "visa", "mada", "ابل باي", "apple pay",
)

/** كلمات عامة في أسامي المحلات والمحافظ والأهداف — الاسم المميز هو اللي بعدها («مقهى المرسى» ⇒ «المرسى»). */
internal val GENERIC_NAME_WORDS = normSet(
    "مقهى", "كافيه", "كوفي", "مطعم", "محل", "محلات", "سوبرماركت", "بقاله", "شركه", "مؤسسه", "صيدليه", "مخبز", "cafe", "coffee", "restaurant", "store",
    "shop", "market", "مصرف", "بنك", "البنك", "حساب", "محفظه", "bank", "account", "wallet", "هدف", "خطه", "مشروع", "فاتوره", "اشتراك", "جمعيه", "قسط",
    "تمويل", "حدث", "مناسبه", "اصل", "the", "al",
)
